package com.vedica.labs.ind.app.docora.core.model

/**
 * The file families Docora understands.
 *
 * A type is only listed here when the app can actually do something useful with it
 * (render, index, list or hand over to another app) - see the handler table in
 * `data/handlers` for the concrete capability of each type.
 */
enum class DocumentType(val label: String) {
    PDF("PDF"),
    IMAGE("Image"),
    TEXT("Text"),
    SPREADSHEET("Spreadsheet"),
    PRESENTATION("Presentation"),
    WORD_DOCUMENT("Document"),
    ARCHIVE("Archive"),
    UNKNOWN("Unknown"),
    ;

    val isPdf: Boolean get() = this == PDF
    val isImage: Boolean get() = this == IMAGE

    /** Types Docora can render natively. */
    val hasNativePreview: Boolean
        get() = when (this) {
            PDF, IMAGE, TEXT -> true
            else -> false
        }

    /** Types whose text content can be extracted for full-text search. */
    val hasExtractableText: Boolean
        get() = when (this) {
            PDF, TEXT, WORD_DOCUMENT, SPREADSHEET, PRESENTATION -> true
            else -> false
        }

    /**
     * Types on-device text recognition can run over.
     *
     * Text based documents already carry their own text, so running OCR over them would only
     * duplicate work and inflate the index.
     */
    val supportsTextRecognition: Boolean
        get() = this == PDF || this == IMAGE

    companion object {
        fun fromMimeType(mimeType: String?): DocumentType = when {
            mimeType == null -> UNKNOWN
            mimeType == "application/pdf" -> PDF
            mimeType.startsWith("image/") -> IMAGE
            mimeType == "text/plain" || mimeType == "text/csv" || mimeType.startsWith("text/") -> TEXT
            mimeType.contains("spreadsheet") || mimeType.contains("excel") -> SPREADSHEET
            mimeType.contains("presentation") || mimeType.contains("powerpoint") -> PRESENTATION
            mimeType.contains("word") || mimeType.contains("wordprocessing") -> WORD_DOCUMENT
            mimeType.contains("zip") || mimeType.contains("compressed") -> ARCHIVE
            else -> UNKNOWN
        }

        /**
         * Resolves a type from the MIME type reported by the provider, falling back to the
         * file extension because many document providers report `application/octet-stream`.
         */
        fun from(mimeType: String?, extension: String?): DocumentType {
            val byMime = fromMimeType(mimeType)
            if (byMime != UNKNOWN) return byMime
            return fromExtension(extension)
        }

        fun fromExtension(extension: String?): DocumentType = when (extension?.lowercase()) {
            "pdf" -> PDF
            "jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "tif", "tiff" -> IMAGE
            "txt", "csv", "md", "log", "json", "xml" -> TEXT
            "xls", "xlsx", "ods", "numbers" -> SPREADSHEET
            "ppt", "pptx", "odp", "key" -> PRESENTATION
            "doc", "docx", "odt", "rtf", "pages" -> WORD_DOCUMENT
            "zip" -> ARCHIVE
            else -> UNKNOWN
        }
    }
}
