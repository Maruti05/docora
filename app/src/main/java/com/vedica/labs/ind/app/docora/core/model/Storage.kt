package com.vedica.labs.ind.app.docora.core.model

/** Aggregate storage usage for the storage analyser (PRD §42). */
data class StorageBreakdown(
    val totalBytes: Long,
    val pdfBytes: Long,
    val imageBytes: Long,
    val otherBytes: Long,
    val trashBytes: Long,
    val thumbnailCacheBytes: Long,
    val documentCount: Int,
) {
    /** Everything Docora knows about, including the trash and the thumbnail cache. */
    val accountedBytes: Long get() = pdfBytes + imageBytes + otherBytes + trashBytes + thumbnailCacheBytes

    companion object {
        val Empty = StorageBreakdown(0, 0, 0, 0, 0, 0, 0)
    }
}

/** One entry of the "largest files" list. */
data class LargestFileItem(
    val documentId: String,
    val displayName: String,
    val sizeBytes: Long,
    val type: DocumentType,
)

/** A set of documents whose content is identical (PRD §20). */
data class DuplicateGroup(
    val checksum: String,
    val sizeBytes: Long,
    val documents: List<Document>,
) {
    val wastedBytes: Long get() = sizeBytes * (documents.size - 1).coerceAtLeast(0).toLong()
}

/** Result of a duplicate scan. */
data class DuplicateScanResult(
    val groups: List<DuplicateGroup>,
) {
    val duplicateCount: Int get() = groups.sumOf { it.documents.size - 1 }
    val reclaimableBytes: Long get() = groups.sumOf { it.wastedBytes }
}
