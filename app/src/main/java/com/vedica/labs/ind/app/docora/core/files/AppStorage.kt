package com.vedica.labs.ind.app.docora.core.files

import android.content.Context
import androidx.core.content.FileProvider
import com.vedica.labs.ind.app.docora.core.common.DocoraError
import com.vedica.labs.ind.app.docora.core.common.docoraRunCatching
import com.vedica.labs.ind.app.docora.core.common.DocoraResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app sandbox layout.
 *
 * Docora keeps three things on disk and nothing else: generated documents (scans, merged or
 * compressed PDFs), thumbnails, and export/backup artefacts. Everything here lives under
 * `filesDir`/`cacheDir`, so it is removed when the user clears app data or uninstalls, and it is
 * covered by the FileProvider configuration (see `res/xml/file_paths.xml`) when a file has to be
 * handed to another app.
 *
 * Directory creation is lazy and idempotent: an import must never fail because a directory was
 * missing, and no work happens during application start (PRD §35).
 */
@Singleton
class AppStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Documents Docora created itself (scans, PDF toolkit output). */
    val documentsDir: File get() = ensure(File(context.filesDir, DOCUMENTS))

    /** Generated thumbnails; safe to delete at any time, they are rebuilt on demand. */
    val thumbnailsDir: File get() = ensure(File(context.filesDir, THUMBNAILS))

    /** User requested exports (metadata backup, extracted text). */
    val exportsDir: File get() = ensure(File(context.filesDir, EXPORTS))

    /** Scratch space; cleared whenever the app is brought to the foreground. */
    private val sharedDir: File get() = ensure(File(context.cacheDir, SHARED))

    /**
     * Pages captured by the scanner that have not been saved into the library yet.
     *
     * Staging lives in the cache so an abandoned scan is reclaimed by the platform if the app is
     * killed, and it is separate from [documentsDir] so a half-finished scan can never be mistaken
     * for a document the user owns. The directory is exposed through the FileProvider (see
     * `res/xml/file_paths.xml`) because the rest of the app reaches every file through a URI.
     */
    val scanStagingDir: File get() = ensure(File(context.cacheDir, SCAN_STAGING))

    /** A fresh staging file for one captured page, guaranteed not to collide. */
    fun newStagingFile(fileName: String): File = uniqueFile(scanStagingDir, fileName)

    /** Removes staged pages; called after a scan is saved and when a scan is discarded. */
    fun clearScanStaging(): Int {
        val files = scanStagingDir.listFiles().orEmpty()
        var removed = 0
        files.forEach { file -> if (file.delete()) removed++ }
        return removed
    }

    /** Content URI for a sandbox file, so the handler layer can read it like any other document. */
    fun uriFor(file: File): String = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    ).toString()

    private fun ensure(dir: File): File {
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Absolute path for a new sandbox document, guaranteed not to collide. */
    fun newDocumentPath(fileName: String): File = uniqueFile(documentsDir, fileName)

    fun newExportPath(fileName: String): File = uniqueFile(exportsDir, fileName)

    fun thumbnailFile(documentId: String): File = File(thumbnailsDir, "$documentId.jpg")

    /**
     * A file inside `cacheDir/shared` and its FileProvider URI.
     *
     * Sharing from cache (not from `filesDir`) means a document handed to another app leaves no
     * permanent copy behind, and the URI is scoped to this app's provider authority.
     */
    fun prepareSharedFile(fileName: String): Pair<File, String> {
        val file = uniqueFile(sharedDir, fileName)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return file to uri.toString()
    }

    /** Total size of the thumbnail cache, used by the storage analyser (PRD §42). */
    fun thumbnailCacheBytes(): Long = thumbnailsDir.listFiles()?.sumOf { it.length() } ?: 0L

    fun clearThumbnails(): Int {
        val files = thumbnailsDir.listFiles().orEmpty()
        var removed = 0
        files.forEach { file -> if (file.delete()) removed++ }
        return removed
    }

    /** Removes scratch files; called on foreground so temp data never accumulates. */
    fun clearSharedScratch(): Int {
        val files = sharedDir.listFiles().orEmpty()
        var removed = 0
        files.forEach { file -> if (file.delete()) removed++ }
        return removed
    }

    /** Deletes the sandbox copy of a document Docora owns. Missing files are not an error. */
    fun deleteOwnedDocument(path: String): DocoraResult<Boolean> = docoraRunCatching {
        val file = File(path)
        if (!file.exists()) return@docoraRunCatching false
        if (!file.delete()) throw DocoraError.StorageUnavailable()
        true
    }

    /** Free space check used before creating a large export (PRD §41). */
    fun availableBytes(): Long = context.filesDir.usableSpace

    private fun uniqueFile(dir: File, fileName: String): File {
        val safeName = fileName.replace(Regex("[/\\\\]"), "_").ifBlank { "document" }
        var candidate = File(dir, safeName)
        if (!candidate.exists()) return candidate
        val extension = safeName.substringAfterLast('.', "")
        val stem = if (extension.isEmpty()) safeName else safeName.dropLast(extension.length + 1)
        var counter = 1
        while (counter < 1000) {
            val name = if (extension.isEmpty()) "$stem ($counter)" else "$stem ($counter).$extension"
            candidate = File(dir, name)
            if (!candidate.exists()) return candidate
            counter++
        }
        return File(dir, "$stem-${System.currentTimeMillis()}.$extension")
    }

    private companion object {
        const val DOCUMENTS = "documents"
        const val THUMBNAILS = "thumbnails"
        const val EXPORTS = "exports"
        const val SHARED = "shared"
        const val SCAN_STAGING = "scans"
    }
}
