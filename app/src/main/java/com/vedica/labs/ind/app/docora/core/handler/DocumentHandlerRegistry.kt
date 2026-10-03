package com.vedica.labs.ind.app.docora.core.handler

import com.vedica.labs.ind.app.docora.core.model.DocumentType

/**
 * Registry resolving the appropriate [DocumentHandler] for a given MIME type or DocumentType.
 */
class DocumentHandlerRegistry(
    private val handlers: List<DocumentHandler>,
) {
    fun getHandlerForMime(mimeType: String): DocumentHandler? =
        handlers.firstOrNull { it.canHandle(mimeType) }

    fun getHandlerForType(type: DocumentType): DocumentHandler? =
        handlers.firstOrNull { it.supportedType == type }
}
