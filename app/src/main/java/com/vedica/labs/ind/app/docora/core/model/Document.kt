package com.vedica.labs.ind.app.docora.core.model

/**
 * How a document first entered Docora. Used by the "Scans" collection and by the
 * file-name generator for scans.
 */
enum class DocumentSource {
    IMPORTED,
    SCANNED,
    CREATED,
    SHARED_IN,
}

/** Progress of the text-extraction pipeline for a single document. */
enum class TextIndexStatus {
    NOT_PROCESSED,
    PROCESSING,
    COMPLETED,
    FAILED,
    SKIPPED,
}

/** Progress of the on-device text recognition (OCR) pipeline. */
enum class OcrStatus {
    NOT_PROCESSED,
    PROCESSING,
    COMPLETED,
    FAILED,
    NOT_APPLICABLE,
}

/** Where extracted text came from, so the UI can explain it in the details screen. */
enum class TextOrigin {
    EMBEDDED,
    RECOGNISED,
    NONE,
}

/**
 * A document as the rest of the app sees it.
 *
 * This is an immutable domain model: Room entities never leak into the UI layer, which
 * keeps Compose recomposition stable and lets the persistence layer change freely.
 *
 * [uri] is always a content URI obtained through the Storage Access Framework. Docora
 * never assumes a filesystem path.
 */
data class Document(
    val id: String,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val extension: String,
    val sizeBytes: Long,
    val type: DocumentType,
    val createdAt: Long,
    val modifiedAt: Long,
    val lastOpenedAt: Long?,
    val pageCount: Int?,
    val width: Int?,
    val height: Int?,
    val folderId: String?,
    val isFavorite: Boolean,
    val isTrashed: Boolean,
    val trashedAt: Long?,
    val isEncrypted: Boolean,
    val source: DocumentSource,
    val textIndexStatus: TextIndexStatus,
    val ocrStatus: OcrStatus,
    val textOrigin: TextOrigin,
    val category: DocumentCategory?,
    val checksumSha256: String?,
    val thumbnailPath: String?,
    val providerAuthority: String?,
    val indexedAt: Long?,
) {
    /** Name without extension, used as the title in lists and the viewer. */
    val title: String
        get() = if (extension.isNotEmpty() && displayName.endsWith(".$extension", ignoreCase = true)) {
            displayName.dropLast(extension.length + 1)
        } else {
            displayName
        }

    /** True when Docora can only show metadata and delegate rendering to another app. */
    val requiresExternalApp: Boolean
        get() = !type.hasNativePreview

    /** True when the file lives in the app sandbox (created by the scanner or a PDF tool). */
    val isOwnedByApp: Boolean
        get() = providerAuthority == null
}
