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
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Microsoft Office document handler (.docx text extraction without Apache POI) (PRD §27, §46).
 * Extracts text directly from word/document.xml in the DOCX package using standard XML parsing.
 */
class OfficeDocumentHandler(
    private val safGateway: SafGateway,
) : DocumentHandler {

    override val supportedType: DocumentType = DocumentType.WORD_DOCUMENT
    override val capabilities: HandlerCapabilities = HandlerCapabilities(
        canRenderPreview = false,
        canExtractText = true,
        canCountPages = false,
        canSearchInline = false,
        canExportPdf = false,
    )

    override fun canHandle(mimeType: String): Boolean =
        mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
            mimeType == "application/msword"

    override suspend fun getPageCount(uri: Uri): DocoraResult<Int> =
        DocoraResult.Success(1)

    override suspend fun renderThumbnail(uri: Uri, pageIndex: Int, targetWidth: Int): DocoraResult<Bitmap> =
        DocoraResult.Failure(DocoraError.UnsupportedFormat("office_thumbnail"))

    override suspend fun extractText(uri: Uri, pageIndex: Int?): DocoraResult<String> =
        withContext(Dispatchers.IO) {
            docoraRunCatching {
                val stream: InputStream = safGateway.openInputStream(uri) ?: throw DocoraError.DocumentMissing()
                val textBuilder = StringBuilder()
                ZipInputStream(stream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == "word/document.xml") {
                            val xmlContent = zip.bufferedReader(Charsets.UTF_8).readText()
                            // Strip XML tags cleanly: <w:p> to newline, other tags removed
                            val text = xmlContent
                                .replace(Regex("<w:p[ >]"), "\n")
                                .replace(Regex("<[^>]+>"), "")
                                .replace("&amp;", "&")
                                .replace("&lt;", "<")
                                .replace("&gt;", ">")
                                .replace("&quot;", "\"")
                                .replace("&apos;", "'")
                                .trim()
                            textBuilder.append(text)
                            break
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
                textBuilder.toString()
            }
        }
}
