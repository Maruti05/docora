package com.vedica.labs.ind.app.docora.core.handler

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import com.vedica.labs.ind.app.docora.core.common.DocoraError
import com.vedica.labs.ind.app.docora.core.common.DocoraResult
import com.vedica.labs.ind.app.docora.core.common.docoraRunCatching
import com.vedica.labs.ind.app.docora.core.model.DocumentType
import com.vedica.labs.ind.app.docora.core.storage.SafGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Plain text / Markdown document handler (PRD §26, §46).
 */
class TextDocumentHandler(
    private val safGateway: SafGateway,
) : DocumentHandler {

    override val supportedType: DocumentType = DocumentType.TEXT
    override val capabilities: HandlerCapabilities = HandlerCapabilities(
        canRenderPreview = true,
        canExtractText = true,
        canCountPages = true,
        canSearchInline = true,
        canExportPdf = true,
    )

    override fun canHandle(mimeType: String): Boolean =
        mimeType.startsWith("text/", ignoreCase = true) ||
            mimeType == "application/json" ||
            mimeType == "application/xml"

    override suspend fun getPageCount(uri: Uri): DocoraResult<Int> =
        DocoraResult.Success(1)

    override suspend fun renderThumbnail(
        uri: Uri,
        pageIndex: Int,
        targetWidth: Int,
    ): DocoraResult<Bitmap> = withContext(Dispatchers.IO) {
        docoraRunCatching {
            val text = extractText(uri).let {
                if (it is DocoraResult.Success) it.value else ""
            }
            val targetHeight = (targetWidth * 1.4f).toInt()
            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            val paint = Paint().apply {
                color = Color.DKGRAY
                textSize = 18f
                isAntiAlias = true
            }

            val lines = text.lines().take(15)
            var y = 30f
            for (line in lines) {
                val truncated = if (line.length > 25) line.take(25) + "…" else line
                canvas.drawText(truncated, 20f, y, paint)
                y += 24f
            }
            bitmap
        }
    }

    override suspend fun extractText(uri: Uri, pageIndex: Int?): DocoraResult<String> =
        withContext(Dispatchers.IO) {
            docoraRunCatching {
                val stream = safGateway.openInputStream(uri) ?: throw DocoraError.DocumentMissing()
                stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        }
}
