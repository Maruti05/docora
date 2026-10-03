package com.vedica.labs.ind.app.docora.core.handler

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.vedica.labs.ind.app.docora.core.common.DocoraError
import com.vedica.labs.ind.app.docora.core.common.DocoraResult
import com.vedica.labs.ind.app.docora.core.common.docoraRunCatching
import com.vedica.labs.ind.app.docora.core.model.DocumentType
import com.vedica.labs.ind.app.docora.core.storage.SafGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Native Android PdfRenderer + PDFBox Android handler for PDF documents (PRD §24, §46).
 */
class PdfDocumentHandler(
    private val context: Context,
    private val safGateway: SafGateway,
) : DocumentHandler {

    init {
        PDFBoxResourceLoader.init(context)
    }

    override val supportedType: DocumentType = DocumentType.PDF
    override val capabilities: HandlerCapabilities = HandlerCapabilities(
        canRenderPreview = true,
        canExtractText = true,
        canCountPages = true,
        canSearchInline = true,
        canExportPdf = true,
    )

    override fun canHandle(mimeType: String): Boolean =
        mimeType.equals("application/pdf", ignoreCase = true)

    override suspend fun getPageCount(uri: Uri): DocoraResult<Int> = withContext(Dispatchers.IO) {
        docoraRunCatching {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw DocoraError.DocumentMissing()
            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    renderer.pageCount
                }
            }
        }
    }

    override suspend fun renderThumbnail(
        uri: Uri,
        pageIndex: Int,
        targetWidth: Int,
    ): DocoraResult<Bitmap> = withContext(Dispatchers.IO) {
        docoraRunCatching {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw DocoraError.DocumentMissing()
            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val pageCount = renderer.pageCount
                    val safePage = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                    renderer.openPage(safePage).use { page ->
                        val ratio = page.height.toFloat() / page.width.toFloat()
                        val targetHeight = (targetWidth * ratio).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        }
    }

    override suspend fun extractText(uri: Uri, pageIndex: Int?): DocoraResult<String> =
        withContext(Dispatchers.IO) {
            docoraRunCatching {
                val stream = safGateway.openInputStream(uri) ?: throw DocoraError.DocumentMissing()
                stream.use { input ->
                    val document = PDDocument.load(input)
                    document.use { pdDoc ->
                        val stripper = PDFTextStripper()
                        if (pageIndex != null) {
                            stripper.startPage = pageIndex + 1
                            stripper.endPage = pageIndex + 1
                        }
                        stripper.getText(pdDoc).orEmpty()
                    }
                }
            }
        }
}
