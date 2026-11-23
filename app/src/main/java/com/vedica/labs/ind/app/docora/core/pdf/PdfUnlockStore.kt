package com.vedica.labs.ind.app.docora.core.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.vedica.labs.ind.app.docora.core.common.DispatcherProvider
import com.vedica.labs.ind.app.docora.core.storage.SafGateway
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Result of trying to unlock an encrypted PDF. */
sealed interface PdfUnlockResult {
    /** The password was accepted and a decryptable copy is ready for the renderer. */
    data class Unlocked(val pageCount: Int) : PdfUnlockResult

    /** The password was rejected; the user can try again. */
    data object WrongPassword : PdfUnlockResult

    /** The file could not be read or decrypted for a non-password reason. */
    data class Failed(val cause: Throwable? = null) : PdfUnlockResult
}

/**
 * Session-scoped access to encrypted PDFs.
 *
 * Android's [android.graphics.pdf.PdfRenderer] - and therefore the fast page rasteriser - cannot
 * open a PDF that needs a password, while PDFBox can. When the user supplies the password this
 * store uses PDFBox to remove the encryption and writes an **app-private** temporary copy, then
 * resolves the original document URI to that copy. The renderer, text extractor and PDF toolkit
 * all ask this store for the resolved URI, so a password entered once works for reading, text
 * search, the page editor and the PDF tools for the rest of the session.
 *
 * The decrypted copy lives in the app cache and is deleted on the next process start and whenever
 * a newer unlock replaces it. It is never exposed through a content provider or a share intent;
 * the original document URI remains the identity of the document everywhere else.
 */
@Singleton
class PdfUnlockStore @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val safGateway: SafGateway,
    private val dispatchers: DispatcherProvider,
) {

    private val mutex = Mutex()
    private val resolved = LinkedHashMap<String, File>()

    init {
        // A previous process may have been killed while a document was unlocked. The copies are
        // useless without the in-memory mapping, so clear them eagerly.
        runCatching { tempDirectory().listFiles()?.forEach { it.delete() } }
    }

    /** The temporary copy for [uri], or null when the document has not been unlocked. */
    fun resolve(uri: String): String? = synchronized(resolved) {
        resolved[uri]?.takeIf { it.exists() }?.let { Uri.fromFile(it).toString() }
    }

    /**
     * Reports whether [uri] is encrypted with a user password.
     *
     * Called only after the renderer has already refused the file, so this is the branch that
     * decides between "ask for the password" and "this file is broken". An owner-password-only
     * PDF opens with the empty password and is not treated as locked.
     */
    suspend fun isPasswordProtected(uri: String): Boolean = withContext(dispatchers.io) {
        val stream = runCatching { safGateway.openInputStream(Uri.parse(uri)) }.getOrNull()
            ?: return@withContext false
        try {
            stream.use { input ->
                PDDocument.load(input, "", MemoryUsageSetting.setupMixed(MAX_HEAP_BUFFER_BYTES)).use {
                    it.numberOfPages
                }
            }
            false
        } catch (_: InvalidPasswordException) {
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** Decrypts [uri] with [password] and remembers the result for the rest of the session. */
    suspend fun unlock(uri: String, password: String): PdfUnlockResult =
        withContext(dispatchers.io) {
            mutex.withLock {
                val stream = runCatching { safGateway.openInputStream(Uri.parse(uri)) }.getOrNull()
                    ?: return@withLock PdfUnlockResult.Failed()
                val directory = tempDirectory()
                val target = runCatching {
                    File.createTempFile("unlocked-", ".pdf", directory)
                }.getOrNull() ?: return@withLock PdfUnlockResult.Failed()

                try {
                    stream.use { input ->
                        PDDocument.load(
                            input,
                            password,
                            MemoryUsageSetting.setupMixed(MAX_HEAP_BUFFER_BYTES),
                        ).use { document ->
                            document.isAllSecurityToBeRemoved = true
                            document.save(target)
                            replaceLocked(uri, target)
                            PdfUnlockResult.Unlocked(document.numberOfPages)
                        }
                    }
                } catch (_: InvalidPasswordException) {
                    target.delete()
                    PdfUnlockResult.WrongPassword
                } catch (error: Throwable) {
                    target.delete()
                    PdfUnlockResult.Failed(error)
                }
            }
        }

    private fun replaceLocked(uri: String, file: File) {
        synchronized(resolved) {
            resolved.put(uri, file)?.delete()
        }
    }

    private fun tempDirectory(): File {
        val directory = File(context.cacheDir, TEMP_DIRECTORY)
        if (!directory.exists()) directory.mkdirs()
        return directory
    }

    private companion object {
        const val TEMP_DIRECTORY = "pdf-unlocked"
        const val MAX_HEAP_BUFFER_BYTES = 16L * 1024 * 1024
    }
}
