package com.vedica.labs.ind.app.docora.core.handler

import android.graphics.Bitmap
import android.net.Uri
import com.vedica.labs.ind.app.docora.core.common.DocoraResult
import com.vedica.labs.ind.app.docora.core.model.DocumentType

/**
 * Capability descriptor for a specific document format (PRD §46).
 */
data class HandlerCapabilities(
    val canRenderPreview: Boolean,
    val canExtractText: Boolean,
    val canCountPages: Boolean,
    val canSearchInline: Boolean,
    val canExportPdf: Boolean,
)

/**
 * Common abstraction for handling different file types.
 */
interface DocumentHandler {
    val supportedType: DocumentType
    val capabilities: HandlerCapabilities

    fun canHandle(mimeType: String): Boolean

    suspend fun getPageCount(uri: Uri): DocoraResult<Int>

    suspend fun renderThumbnail(uri: Uri, pageIndex: Int = 0, targetWidth: Int = 300): DocoraResult<Bitmap>

    suspend fun extractText(uri: Uri, pageIndex: Int? = null): DocoraResult<String>
}
