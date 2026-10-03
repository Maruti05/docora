package com.vedica.labs.ind.app.docora.core.util

/**
 * Safety limits applied to every document Docora opens (PRD §61).
 *
 * Limits exist because a document workspace is exposed to malicious input: corrupted
 * PDFs, ZIP bombs, or simply a 4 GB file that would exhaust memory on a phone.
 */
object DocumentLimits {

    /** Files larger than this are indexed lazily and never fully buffered in memory. */
    const val MAX_FULL_BUFFER_BYTES: Long = 32L * 1024 * 1024

    /** Above this size Docora asks before importing instead of silently accepting. */
    const val WARN_FILE_SIZE_BYTES: Long = 200L * 1024 * 1024

    /** Hard ceiling: opening a larger file is refused with an actionable message. */
    const val MAX_OPENABLE_FILE_BYTES: Long = 2L * 1024 * 1024 * 1024

    /** Maximum pages Docora will render for a single PDF (keeps memory bounded). */
    const val MAX_RENDERED_PAGES: Int = 5_000

    /** Longest edge used when decoding an image for the grid/list thumbnails. */
    const val THUMBNAIL_LONG_EDGE_PX: Int = 512

    /** Memory budget the PDF page cache is allowed to occupy. */
    const val PDF_PAGE_CACHE_BYTES: Long = 96L * 1024 * 1024

    /** Archive extraction: total expanded size ceiling (decompression bomb guard). */
    const val MAX_ARCHIVE_EXPANDED_BYTES: Long = 512L * 1024 * 1024

    /** Archive extraction: entries ceiling. */
    const val MAX_ARCHIVE_ENTRIES: Int = 5_000

    /** Text kept per document for indexing; longer text is windowed, not truncated blindly. */
    const val MAX_INDEXED_TEXT_CHARS: Int = 400_000

    fun isTooLargeToOpen(sizeBytes: Long): Boolean = sizeBytes > MAX_OPENABLE_FILE_BYTES
}
