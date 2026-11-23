package com.vedica.labs.ind.app.docora.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.common.DocoraError
import com.vedica.labs.ind.app.docora.core.common.DocoraResult
import com.vedica.labs.ind.app.docora.core.model.Document
import com.vedica.labs.ind.app.docora.core.model.DocumentImportRequest
import com.vedica.labs.ind.app.docora.core.model.DocumentSource
import com.vedica.labs.ind.app.docora.core.model.DocumentType
import com.vedica.labs.ind.app.docora.core.model.SmartCollection
import com.vedica.labs.ind.app.docora.core.pdf.PdfCompressionPreset
import com.vedica.labs.ind.app.docora.core.pdf.PdfImageOptions
import com.vedica.labs.ind.app.docora.core.pdf.PdfMetadata
import com.vedica.labs.ind.app.docora.core.pdf.PdfPageSpec
import com.vedica.labs.ind.app.docora.core.pdf.PdfRenderRepository
import com.vedica.labs.ind.app.docora.core.pdf.PdfTextExtractor
import com.vedica.labs.ind.app.docora.core.pdf.PdfToolkit
import com.vedica.labs.ind.app.docora.core.processing.DocumentEnricher
import com.vedica.labs.ind.app.docora.core.repository.DocumentRepository
import com.vedica.labs.ind.app.docora.core.storage.SafGateway
import com.vedica.labs.ind.app.docora.core.util.MimeTypes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One page in the page editor; [id] is stable while the order changes. */
data class PdfToolPage(
    val id: Long,
    val sourceIndex: Int,
    val rotationDegrees: Int = 0,
)

/** Immutable state for the PDF toolkit hub. */
data class PdfToolsUiState(
    val document: Document? = null,
    val pageCount: Int = 0,
    val pages: List<PdfToolPage> = emptyList(),
    val metadata: PdfMetadata = PdfMetadata(),
    val libraryPdfs: List<Document> = emptyList(),
    val mergeSelection: List<String> = emptyList(),
    val exportText: String? = null,
    val exportTextLoading: Boolean = false,
    val isLoading: Boolean = true,
    val busy: Boolean = false,
    val errorRes: Int? = null,
)

/** One-shot results so the screen can navigate or show a snackbar exactly once. */
sealed interface PdfToolsEvent {
    data class Message(val text: String) : PdfToolsEvent
    data class Open(val documentId: String) : PdfToolsEvent
}

/**
 * PDF toolkit logic (PRD §11, §46).
 *
 * Every operation is delegated to [PdfToolkit], which writes a new sandbox file and registers
 * it in the library instead of modifying the original. The screen therefore never owns file
 * handles and can be dismissed while an operation is running.
 */
@HiltViewModel
class PdfToolsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val documentRepository: DocumentRepository,
    private val pdfToolkit: PdfToolkit,
    private val pdfRenderRepository: PdfRenderRepository,
    private val pdfTextExtractor: PdfTextExtractor,
    private val safGateway: SafGateway,
    private val documentEnricher: DocumentEnricher,
) : ViewModel() {

    private val documentId: String? = savedStateHandle[Screen.ARG_DOCUMENT_ID]

    private val _state = MutableStateFlow(PdfToolsUiState())
    val state: StateFlow<PdfToolsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PdfToolsEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PdfToolsEvent> = _events.asSharedFlow()

    private var pageIdSeed = 0L

    init {
        load()
        observeLibraryPdfs()
    }

    private fun load() {
        val id = documentId
        if (id == null) {
            _state.value = PdfToolsUiState(
                isLoading = false,
                errorRes = R.string.error_document_missing,
            )
            return
        }
        viewModelScope.launch {
            val document = documentRepository.getDocument(id)
            if (document == null || document.type != DocumentType.PDF) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorRes = R.string.error_document_missing,
                )
                return@launch
            }
            val info = pdfToolkit.readInfo(document.uri).getOrNull()
            val pageCount = info?.pageCount ?: document.pageCount ?: 0
            _state.value = PdfToolsUiState(
                document = document,
                pageCount = pageCount,
                pages = buildPages(pageCount),
                metadata = info?.metadata ?: PdfMetadata(),
                isLoading = false,
            )
        }
    }

    private fun observeLibraryPdfs() {
        viewModelScope.launch {
            documentRepository.observeCollection(
                collection = SmartCollection.PDFS,
                limit = MAX_LIBRARY_PDFS,
            ).collect { pdfs ->
                _state.value = _state.value.copy(libraryPdfs = pdfs)
            }
        }
    }

    private fun buildPages(count: Int): List<PdfToolPage> =
        List(count.coerceAtLeast(0)) { index ->
            PdfToolPage(id = nextPageId(), sourceIndex = index)
        }

    private fun nextPageId(): Long = ++pageIdSeed

    /** Renders an editor thumbnail at the current rotation for this spec. */
    suspend fun pageThumbnail(page: PdfToolPage): Bitmap? {
        val document = _state.value.document ?: return null
        return runCatching {
            pdfRenderRepository.renderPage(
                uri = document.uri,
                pageIndex = page.sourceIndex,
                targetWidthPx = EDITOR_THUMBNAIL_WIDTH_PX,
                rotationDegrees = page.rotationDegrees,
            )?.bitmap
        }.getOrNull()
    }

    // ------------------------------------------------------------------ page edits

    fun rotatePage(id: Long, deltaDegrees: Int) {
        updatePages { pages ->
            pages.map { page ->
                if (page.id == id) {
                    page.copy(rotationDegrees = ((page.rotationDegrees + deltaDegrees) % 360 + 360) % 360)
                } else {
                    page
                }
            }
        }
    }

    fun rotateAll(deltaDegrees: Int) {
        updatePages { pages ->
            pages.map { page ->
                page.copy(
                    rotationDegrees = ((page.rotationDegrees + deltaDegrees) % 360 + 360) % 360,
                )
            }
        }
    }

    fun movePage(id: Long, offset: Int) {
        updatePages { pages ->
            val from = pages.indexOfFirst { it.id == id }
            if (from < 0) return@updatePages pages
            val to = (from + offset).coerceIn(0, pages.lastIndex)
            if (to == from) return@updatePages pages
            pages.toMutableList().apply {
                add(to, removeAt(from))
            }
        }
    }

    fun duplicatePage(id: Long) {
        updatePages { pages ->
            val from = pages.indexOfFirst { it.id == id }
            if (from < 0) return@updatePages pages
            pages.toMutableList().apply {
                add(from + 1, pages[from].copy(id = nextPageId()))
            }
        }
    }

    fun deletePage(id: Long) {
        val pages = _state.value.pages
        if (pages.size <= 1) {
            emitMessage(context.getString(R.string.toolkit_keep_one_page))
            return
        }
        _state.value = _state.value.copy(pages = pages.filterNot { it.id == id })
    }

    fun resetPages() {
        _state.value = _state.value.copy(pages = buildPages(_state.value.pageCount))
    }

    private fun updatePages(transform: (List<PdfToolPage>) -> List<PdfToolPage>) {
        _state.value = _state.value.copy(pages = transform(_state.value.pages))
    }

    // ------------------------------------------------------------------ operations

    fun saveOrganized(outputName: String) {
        startOperation {
            val document = _state.value.document ?: return@startOperation
            val specs = _state.value.pages.map { PdfPageSpec(it.sourceIndex, it.rotationDegrees) }
            handleSingle(
                pdfToolkit.organize(
                    document = document,
                    pages = specs,
                    suffix = "organized",
                    outputName = normalizedName(outputName),
                ),
            )
        }
    }

    fun extractPages(rangesText: String, outputName: String) {
        startOperation {
            val document = _state.value.document ?: return@startOperation
            val ranges = parsePageRanges(rangesText, _state.value.pageCount)
            if (ranges.isEmpty()) {
                emitMessage(context.getString(R.string.toolkit_invalid_range))
                return@startOperation
            }
            val specs = ranges.flatMap { it.toList() }
                .distinct()
                .map { PdfPageSpec(it - 1) }
            handleSingle(
                pdfToolkit.organize(
                    document = document,
                    pages = specs,
                    suffix = "extracted",
                    outputName = normalizedName(outputName),
                ),
            )
        }
    }

    fun split(rangesText: String, outputName: String) {
        startOperation {
            val document = _state.value.document ?: return@startOperation
            val ranges = parsePageRanges(rangesText, _state.value.pageCount)
            if (ranges.isEmpty()) {
                emitMessage(context.getString(R.string.toolkit_invalid_range))
                return@startOperation
            }
            runSplit(document, ranges, outputName)
        }
    }

    /** Splits into fixed-size chunks: 1-10, 11-20, ... (the common "one file per N pages"). */
    fun splitEvery(pagesPerFile: Int, outputName: String) {
        startOperation {
            val document = _state.value.document ?: return@startOperation
            val pageCount = _state.value.pageCount
            val step = pagesPerFile.coerceAtLeast(1)
            if (pageCount <= 0) {
                emitMessage(context.getString(R.string.toolkit_invalid_range))
                return@startOperation
            }
            val ranges = (1..pageCount step step).map { start ->
                start..(start + step - 1).coerceAtMost(pageCount)
            }
            runSplit(document, ranges, outputName)
        }
    }

    private suspend fun runSplit(
        document: Document,
        ranges: List<IntRange>,
        outputName: String,
    ) {
        when (val result = pdfToolkit.split(document, ranges, normalizedName(outputName))) {
            is DocoraResult.Success -> {
                val outputs = result.value
                if (outputs.size == 1) {
                    emitOpen(outputs.first())
                } else {
                    emitMessage(
                        context.getString(R.string.toolkit_result_multiple, outputs.size),
                    )
                }
            }
            is DocoraResult.Failure -> emitFailure(result.error)
        }
    }

    fun compress(preset: PdfCompressionPreset) {
        startOperation {
            val document = _state.value.document ?: return@startOperation
            handleSingle(pdfToolkit.compress(document, preset))
        }
    }

    fun saveMetadata(metadata: PdfMetadata) {
        startOperation {
            val document = _state.value.document ?: return@startOperation
            _state.value = _state.value.copy(metadata = metadata)
            handleSingle(pdfToolkit.updateMetadata(document, metadata))
        }
    }

    fun toggleMerge(id: String) {
        val current = _state.value.mergeSelection
        _state.value = _state.value.copy(
            mergeSelection = if (id in current) {
                current - id
            } else {
                current + id
            },
        )
    }

    fun selectAllMerge() {
        _state.value = _state.value.copy(
            mergeSelection = _state.value.libraryPdfs.map { it.id },
        )
    }

    fun clearMerge() {
        _state.value = _state.value.copy(mergeSelection = emptyList())
    }

    fun moveMerge(id: String, offset: Int) {
        val current = _state.value.mergeSelection
        val from = current.indexOf(id)
        if (from < 0) return
        val to = (from + offset).coerceIn(0, current.lastIndex)
        if (to == from) return
        _state.value = _state.value.copy(
            mergeSelection = current.toMutableList().apply {
                add(to, removeAt(from))
            },
        )
    }

    fun merge(outputName: String) {
        startOperation {
            val state = _state.value
            val selected = state.mergeSelection.mapNotNull { id ->
                state.libraryPdfs.firstOrNull { it.id == id }
            }
            if (selected.size < 2) {
                emitMessage(context.getString(R.string.toolkit_merge_needs_two))
                return@startOperation
            }
            handleSingle(pdfToolkit.merge(selected, normalizedName(outputName)))
        }
    }

    fun imagesToPdf(
        images: List<Uri>,
        options: PdfImageOptions,
        outputName: String,
    ) {
        startOperation {
            if (images.isEmpty()) {
                emitMessage(context.getString(R.string.toolkit_pick_images))
                return@startOperation
            }
            handleSingle(pdfToolkit.imagesToPdf(images, options, normalizedName(outputName)))
        }
    }

    /** Extracts the text layer for the export sheet; the result is cached by the extractor. */
    fun loadExportText() {
        val current = _state.value
        if (current.exportTextLoading || current.exportText != null) return
        val document = current.document ?: return
        _state.value = current.copy(exportTextLoading = true)
        viewModelScope.launch {
            val pages = runCatching { pdfTextExtractor.pageTexts(document.uri) }
                .getOrDefault(emptyList())
            val text = pages.mapIndexedNotNull { index, page ->
                if (page.isBlank()) null else "Page ${index + 1}\n$page"
            }.joinToString(separator = "\n\n")
            _state.value = _state.value.copy(
                exportText = text,
                exportTextLoading = false,
            )
        }
    }

    fun clearExportText() {
        _state.value = _state.value.copy(exportText = null, exportTextLoading = false)
    }

    /** Imports PDFs picked from the device and appends them to the merge order. */
    fun importExternalPdfs(uris: List<Uri>) {
        if (uris.isEmpty()) return
        startOperation {
            val added = ArrayList<String>(uris.size)
            var imported = 0
            uris.forEach { uri ->
                runCatching {
                    safGateway.takePersistablePermission(uri)
                    val meta = safGateway.getMetadata(uri).getOrNull() ?: return@forEach
                    val mimeType = MimeTypes.resolve(meta.mimeType, meta.displayName)
                    val extension = MimeTypes.extensionOf(meta.displayName)
                    if (DocumentType.from(mimeType, extension) != DocumentType.PDF) {
                        return@forEach
                    }
                    val document = documentRepository.import(
                        DocumentImportRequest(
                            uri = uri.toString(),
                            displayName = meta.displayName,
                            mimeType = mimeType,
                            extension = extension,
                            sizeBytes = meta.sizeBytes,
                            type = DocumentType.PDF,
                            source = DocumentSource.IMPORTED,
                            modifiedAt = meta.modifiedAt,
                            providerAuthority = uri.authority,
                        ),
                    )
                    imported++
                    added.add(document.id)
                }
            }
            if (added.isNotEmpty()) {
                _state.value = _state.value.copy(
                    mergeSelection = (_state.value.mergeSelection + added).distinct(),
                )
                documentEnricher.start()
            }
            emitMessage(
                if (imported > 0) {
                    context.getString(R.string.toolkit_imported_pdfs, imported)
                } else {
                    context.getString(R.string.toolkit_import_failed)
                },
            )
        }
    }

    private fun startOperation(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true)
        viewModelScope.launch {
            try {
                block()
            } finally {
                _state.value = _state.value.copy(busy = false)
            }
        }
    }

    private suspend fun handleSingle(result: DocoraResult<Document>) {
        when (result) {
            is DocoraResult.Success -> emitOpen(result.value)
            is DocoraResult.Failure -> emitFailure(result.error)
        }
    }

    private fun emitOpen(document: Document) {
        _events.tryEmit(PdfToolsEvent.Open(document.id))
    }

    private fun emitMessage(text: String) {
        _events.tryEmit(PdfToolsEvent.Message(text))
    }

    private fun emitFailure(error: DocoraError) {
        val messageRes = when (error) {
            is DocoraError.CorruptedDocument -> R.string.error_pdf_corrupted
            is DocoraError.DocumentMissing,
            is DocoraError.PermissionRevoked -> R.string.error_document_missing
            is DocoraError.InsufficientStorage -> R.string.error_not_enough_storage
            is DocoraError.StorageUnavailable -> R.string.error_storage_unavailable
            is DocoraError.UnsupportedFormat -> R.string.error_unsupported_format
            else -> R.string.error_generic_title
        }
        emitMessage(context.getString(messageRes))
    }

    private fun normalizedName(raw: String): String? = raw.trim().ifBlank { null }

    private companion object {
        const val MAX_LIBRARY_PDFS = 200
        const val EDITOR_THUMBNAIL_WIDTH_PX = 240
    }
}

/**
 * Parses the compact range syntax used by the split and extract tools: `1-3, 5, 8-10`.
 *
 * Tokens are clamped to the document's page count and invalid tokens are ignored, so a typo
 * never produces an out-of-bounds page request.
 */
internal fun parsePageRanges(input: String, pageCount: Int): List<IntRange> {
    if (pageCount <= 0) return emptyList()
    val ranges = ArrayList<IntRange>()
    input.split(',', ';', '\n', ' ')
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { token ->
            val parts = token.split('-', '–', '—')
            when (parts.size) {
                1 -> {
                    val page = parts[0].toIntOrNull() ?: return@forEach
                    if (page in 1..pageCount) ranges.add(page..page)
                }
                2 -> {
                    val first = parts[0].toIntOrNull() ?: return@forEach
                    val second = parts[1].toIntOrNull() ?: return@forEach
                    val start = minOf(first, second).coerceIn(1, pageCount)
                    val end = maxOf(first, second).coerceIn(1, pageCount)
                    ranges.add(start..end)
                }
            }
        }
    return ranges
}
