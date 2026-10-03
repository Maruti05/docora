package com.vedica.labs.ind.app.docora.core.model

/**
 * Everything Docora needs to persist a newly imported, scanned or generated document.
 *
 * The UI/domain hands over plain data - never an `InputStream`, never a `File` - so the
 * repository can build the Row and the SAF side effects in one place under the IO dispatcher.
 * [uri] is always a `content://` URI: a SAF grant for imported files, or a FileProvider URI for
 * files Docora created inside its own sandbox.
 */
data class DocumentImportRequest(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val extension: String,
    val sizeBytes: Long,
    val type: DocumentType,
    val source: DocumentSource = DocumentSource.IMPORTED,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val folderId: String? = null,
    val parentTreeUri: String? = null,
    val providerAuthority: String? = null,
    val pageCount: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
) {
    /** Stable identifier derived from the URI, so re-importing a file updates its row. */
    val stableId: String get() = DocumentIds.fromUri(uri)
}

/** Identifier helpers shared by the importer, the scanner and the backup importer. */
object DocumentIds {

    /**
     * Derives the primary key from the document URI.
     *
     * URI hash codes are avoided (`String.hashCode` is not stable across processes for every
     * input in practice, and collisions are silent data loss), so a short SHA-256 prefix is
     * used instead: stable, collision-resistant, and cheap to compute on import only.
     */
    fun fromUri(uri: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(uri.toByteArray(Charsets.UTF_8))
        val builder = StringBuilder(32)
        for (index in 0 until 16) {
            val value = bytes[index].toInt() and 0xFF
            if (value < 0x10) builder.append('0')
            builder.append(Integer.toHexString(value))
        }
        return builder.toString()
    }
}
