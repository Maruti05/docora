package com.vedica.labs.ind.app.docora.core.util

import android.content.ContentResolver
import android.webkit.MimeTypeMap
import com.vedica.labs.ind.app.docora.core.model.DocumentType

/**
 * MIME type resolution that never trusts the provider blindly.
 *
 * SAF providers frequently report `application/octet-stream` (or nothing at all) for
 * perfectly ordinary files, so the extension is consulted as a fallback and a normalised
 * MIME type is always produced.
 */
object MimeTypes {

    const val OCTET_STREAM = "application/octet-stream"
    const val PDF = "application/pdf"
    const val TEXT_PLAIN = "text/plain"
    const val ZIP = "application/zip"

    /**
     * Ordered (MIME, extension) pairs used when the user picks files, so that every
     * supported type shows up in the system picker without granting broad access.
     */
    val importableMimeTypes: Array<String> = arrayOf(
        PDF,
        "image/*",
        "text/plain",
        "text/csv",
        "text/comma-separated-values",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.oasis.opendocument.text",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.oasis.opendocument.spreadsheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.oasis.opendocument.presentation",
        ZIP,
    )

    /** Extension without the dot, lower case, or an empty string. */
    fun extensionOf(displayName: String?): String {
        if (displayName.isNullOrBlank()) return ""
        val dot = displayName.lastIndexOf('.')
        if (dot <= 0 || dot == displayName.length - 1) return ""
        return displayName.substring(dot + 1).lowercase()
    }

    /**
     * Resolves a usable MIME type from the provider value and/or the file name.
     * Values such as `application/octet-stream` are treated as "unknown".
     */
    fun resolve(reportedMimeType: String?, displayName: String?, resolver: ContentResolver? = null): String {
        val reported = reportedMimeType?.takeIf { it.isNotBlank() && it != OCTET_STREAM }
        if (reported != null) return reported

        val extension = extensionOf(displayName)
        if (extension.isNotEmpty()) {
            val fromName = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            if (fromName != null) return fromName
        }
        if (resolver != null) {
            val fromResolver = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            if (fromResolver != null) return fromResolver
        }
        return mappedFallback(extension)
    }

    /** Human readable label for a MIME type, used in the details screen. */
    fun describe(mimeType: String?): String = when {
        mimeType == null -> "Unknown"
        mimeType == PDF -> "PDF"
        mimeType.startsWith("image/") -> "Image · " + mimeType.removePrefix("image/").uppercase()
        mimeType.startsWith("text/") -> "Text · " + mimeType.removePrefix("text/").uppercase()
        else -> mimeType
    }

    /** MIME type Docora will write for a given [DocumentType]. */
    fun forType(type: DocumentType): String = when (type) {
        DocumentType.PDF -> PDF
        DocumentType.IMAGE -> "image/jpeg"
        DocumentType.TEXT -> TEXT_PLAIN
        DocumentType.ARCHIVE -> ZIP
        else -> OCTET_STREAM
    }

    private fun mappedFallback(extension: String): String = when (extension) {
        "pdf" -> PDF
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heic"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "tif", "tiff" -> "image/tiff"
        "txt", "log", "md" -> TEXT_PLAIN
        "csv" -> "text/csv"
        "zip" -> ZIP
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "odt" -> "application/vnd.oasis.opendocument.text"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ods" -> "application/vnd.oasis.opendocument.spreadsheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "odp" -> "application/vnd.oasis.opendocument.presentation"
        else -> OCTET_STREAM
    }
}
