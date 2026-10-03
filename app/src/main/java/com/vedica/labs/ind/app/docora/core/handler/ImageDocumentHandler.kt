package com.vedica.labs.ind.app.docora.core.handler

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.vedica.labs.ind.app.docora.core.common.DocoraError
import com.vedica.labs.ind.app.docora.core.common.DocoraResult
import com.vedica.labs.ind.app.docora.core.common.docoraRunCatching
import com.vedica.labs.ind.app.docora.core.model.DocumentType
import com.vedica.labs.ind.app.docora.core.storage.SafGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Image document handler (JPEG, PNG, WEBP, GIF, HEIC) (PRD §25, §46).
 */
class ImageDocumentHandler(
    private val safGateway: SafGateway,
) : DocumentHandler {

    override val supportedType: DocumentType = DocumentType.IMAGE
    override val capabilities: HandlerCapabilities = HandlerCapabilities(
        canRenderPreview = true,
        canExtractText = false, // OCR runs via ML Kit if enabled
        canCountPages = true,
        canSearchInline = false,
        canExportPdf = true,
    )

    override fun canHandle(mimeType: String): Boolean =
        mimeType.startsWith("image/", ignoreCase = true)

    override suspend fun getPageCount(uri: Uri): DocoraResult<Int> =
        DocoraResult.Success(1)

    override suspend fun renderThumbnail(
        uri: Uri,
        pageIndex: Int,
        targetWidth: Int,
    ): DocoraResult<Bitmap> = withContext(Dispatchers.IO) {
        docoraRunCatching {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            safGateway.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            } ?: throw DocoraError.DocumentMissing()

            var sampleSize = 1
            if (boundsOptions.outWidth > targetWidth) {
                sampleSize = boundsOptions.outWidth / targetWidth
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize.coerceAtLeast(1)
            }

            safGateway.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: throw DocoraError.DocumentMissing()
        }
    }

    override suspend fun extractText(uri: Uri, pageIndex: Int?): DocoraResult<String> =
        DocoraResult.Success("") // Image text handled by OCR engine
}
