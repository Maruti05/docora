package com.vedica.labs.ind.app.docora.core.processing

import android.net.Uri
import com.vedica.labs.ind.app.docora.core.common.DispatcherProvider
import com.vedica.labs.ind.app.docora.core.common.DocoraResult
import com.vedica.labs.ind.app.docora.core.datastore.PreferencesManager
import com.vedica.labs.ind.app.docora.core.handler.DocumentHandlerRegistry
import com.vedica.labs.ind.app.docora.core.model.Document
import com.vedica.labs.ind.app.docora.core.model.DocumentType
import com.vedica.labs.ind.app.docora.core.model.OcrStatus
import com.vedica.labs.ind.app.docora.core.model.TextIndexStatus
import com.vedica.labs.ind.app.docora.core.model.TextOrigin
import com.vedica.labs.ind.app.docora.core.ocr.OcrEngine
import com.vedica.labs.ind.app.docora.core.repository.DocumentRepository
import com.vedica.labs.ind.app.docora.core.repository.SearchRepository
import com.vedica.labs.ind.app.docora.core.storage.SafGateway
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield

/**
 * Fills in the derived document facts the library promises: a content checksum, extracted text
 * and, for images, recognised text.
 *
 * Progress is deliberately measured by [DocumentRepository] rows, so the Home banner advances as
 * each state is written instead of relying on a separate in-memory counter that could disagree
 * with the database. The loop stops when a full pass finds nothing pending; a later import calls
 * [start] again.
 *
 * OCR is limited to images. PDFs are searchable through their embedded text layer, and the
 * scanner already runs OCR inline for documents it creates. Marking PDF OCR as
 * `NOT_APPLICABLE` keeps the processing count finite and honest.
 */
@Singleton
class DocumentEnricher @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val searchRepository: SearchRepository,
    private val handlerRegistry: DocumentHandlerRegistry,
    private val safGateway: SafGateway,
    private val ocrEngine: OcrEngine,
    private val preferencesManager: PreferencesManager,
    private val dispatchers: DispatcherProvider,
) {

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private var job: Job? = null

    /** Starts a pass if one is not already running. Safe to call after every import/sync. */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            mutex.withLock {
                val settings = preferencesManager.settings.first()
                while (currentCoroutineContext().isActive) {
                    val worked = processBatch(settings)
                    if (!worked) break
                    yield()
                    delay(BATCH_PAUSE_MILLIS)
                }
            }
        }
    }

    private suspend fun processBatch(settings: com.vedica.labs.ind.app.docora.core.model.AppSettings): Boolean {
        var worked = false

        documentRepository.getPendingEnrichment(limit = CHECKSUM_BATCH).forEach { document ->
            worked = true
            processChecksum(document)
        }

        documentRepository.getPendingTextExtraction(limit = TEXT_BATCH).forEach { document ->
            worked = true
            if (settings.indexDocumentText) {
                processText(document)
            } else {
                documentRepository.setTextState(document.id, TextIndexStatus.SKIPPED, TextOrigin.NONE)
            }
        }

        documentRepository.getPendingTextRecognition(limit = OCR_BATCH).forEach { document ->
            worked = true
            when {
                !settings.indexOcrText -> {
                    documentRepository.setOcrState(document.id, OcrStatus.NOT_APPLICABLE)
                }
                document.type == DocumentType.IMAGE -> processImageOcr(document)
                // PDFs already expose their text layer (or are scanned images the viewer cannot
                // OCR page-by-page yet); leaving them pending forever is what made the banner stick.
                else -> documentRepository.setOcrState(document.id, OcrStatus.NOT_APPLICABLE)
            }
        }

        return worked
    }

    private suspend fun processChecksum(document: Document) {
        val checksum = runCatching { computeChecksum(Uri.parse(document.uri)) }
            .getOrNull()
            ?: fallbackChecksum(document)
        runCatching { documentRepository.setChecksum(document.id, checksum) }
    }

    private suspend fun processText(document: Document) {
        val handler = handlerRegistry.getHandlerForMime(document.mimeType)
            ?: handlerRegistry.getHandlerForType(document.type)
        if (handler == null || !handler.capabilities.canExtractText) {
            documentRepository.setTextState(document.id, TextIndexStatus.SKIPPED, TextOrigin.NONE)
            return
        }
        when (val result = runCatching { handler.extractText(Uri.parse(document.uri)) }.getOrNull()) {
            is DocoraResult.Success -> {
                val text = result.value
                runCatching { searchRepository.indexDocument(document, extractedText = text) }
                documentRepository.setTextState(
                    document.id,
                    TextIndexStatus.COMPLETED,
                    if (text.isBlank()) TextOrigin.NONE else TextOrigin.EMBEDDED,
                )
            }
            else -> documentRepository.setTextState(document.id, TextIndexStatus.FAILED, TextOrigin.NONE)
        }
    }

    private suspend fun processImageOcr(document: Document) {
        when (val result = runCatching { ocrEngine.recognizeTextFromUri(Uri.parse(document.uri)) }.getOrNull()) {
            is DocoraResult.Success -> {
                val text = result.value
                runCatching { searchRepository.indexDocument(document, recognisedText = text) }
                documentRepository.setTextState(
                    document.id,
                    TextIndexStatus.COMPLETED,
                    TextOrigin.RECOGNISED,
                )
                documentRepository.setOcrState(document.id, OcrStatus.COMPLETED)
            }
            else -> documentRepository.setOcrState(document.id, OcrStatus.FAILED)
        }
    }

    private fun computeChecksum(uri: Uri): String? {
        val input = safGateway.openInputStream(uri) ?: return null
        return input.use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().toHex()
        }
    }

    /**
     * Deterministic checksum for a file the provider will not open.
     *
     * It keeps duplicate detection from treating every unavailable file as a copy of every other,
     * and it lets the progress pass finish instead of retrying the same missing URI forever.
     */
    private fun fallbackChecksum(document: Document): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("${document.id}:${document.uri}:${document.sizeBytes}".toByteArray())
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte ->
        "%02x".format(byte.toInt() and 0xFF)
    }

    private companion object {
        const val CHECKSUM_BATCH = 8
        const val TEXT_BATCH = 4
        const val OCR_BATCH = 2

        /** Keeps the pass from monopolising an IO worker between batches. */
        const val BATCH_PAUSE_MILLIS = 40L
    }
}
