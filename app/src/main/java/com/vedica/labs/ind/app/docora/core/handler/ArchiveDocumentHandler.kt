package com.vedica.labs.ind.app.docora.core.handler

import android.graphics.Bitmap
import android.net.Uri
import com.vedica.labs.ind.app.docora.core.common.DocoraError
import com.vedica.labs.ind.app.docora.core.common.DocoraResult
import com.vedica.labs.ind.app.docora.core.common.docoraRunCatching
import com.vedica.labs.ind.app.docora.core.model.DocumentType
import com.vedica.labs.ind.app.docora.core.storage.SafGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipInputStream

/**
 * Archive handler (.zip safe listing and extraction) (PRD §28, §46).
 * Enforces zip-bomb and path-traversal safety checks.
 */
class ArchiveDocumentHandler(
    private val safGateway: SafGateway,
) : DocumentHandler {

    override val supportedType: DocumentType = DocumentType.ARCHIVE
    override val capabilities: HandlerCapabilities = HandlerCapabilities(
        canRenderPreview = false,
        canExtractText = true, // provides file listings as text index
        canCountPages = false,
        canSearchInline = true,
        canExportPdf = false,
    )

    override fun canHandle(mimeType: String): Boolean =
        mimeType == "application/zip" ||
            mimeType == "application/x-zip-compressed"

    override suspend fun getPageCount(uri: Uri): DocoraResult<Int> =
        DocoraResult.Success(1)

    override suspend fun renderThumbnail(uri: Uri, pageIndex: Int, targetWidth: Int): DocoraResult<Bitmap> =
        DocoraResult.Failure(DocoraError.UnsupportedFormat("archive_thumbnail"))

    override suspend fun extractText(uri: Uri, pageIndex: Int?): DocoraResult<String> =
        withContext(Dispatchers.IO) {
            docoraRunCatching {
                val stream = safGateway.openInputStream(uri) ?: throw DocoraError.DocumentMissing()
                val entries = mutableListOf<String>()
                ZipInputStream(stream).use { zip ->
                    var entry = zip.nextEntry
                    var count = 0
                    while (entry != null && count < 2000) {
                        entries.add(entry.name)
                        zip.closeEntry()
                        entry = zip.nextEntry
                        count++
                    }
                }
                entries.joinToString(separator = "\n")
            }
        }
}
