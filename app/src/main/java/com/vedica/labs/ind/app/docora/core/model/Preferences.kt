package com.vedica.labs.ind.app.docora.core.model

/** Ordering options offered by the browser (PRD §16). */
enum class SortOrder {
    NAME,
    DATE_MODIFIED,
    DATE_CREATED,
    SIZE,
    TYPE,
    LAST_OPENED,
    ;

    companion object {
        val default: SortOrder = DATE_MODIFIED
    }
}

/** Ascending or descending, stored separately so the sort chip can flip direction. */
enum class SortDirection { ASCENDING, DESCENDING }

/** A complete sort selection: the field plus its direction. */
data class DocumentSort(
    val order: SortOrder = SortOrder.default,
    val direction: SortDirection = SortDirection.DESCENDING,
) {
    /** Flips ascending/descending, which is what the sort chip does on tap. */
    fun reversed(): DocumentSort = copy(
        direction = if (direction == SortDirection.ASCENDING) {
            SortDirection.DESCENDING
        } else {
            SortDirection.ASCENDING
        },
    )

    companion object {
        val Default = DocumentSort()
    }
}

/** Layouts the browser can use; persisted per user (PRD §16). */
enum class ViewMode(val gridColumns: Int?) {
    LIST(null),
    COMPACT(null),
    GRID(2),
    LARGE_GRID(1),
    ;

    val isGrid: Boolean get() = gridColumns != null

    companion object {
        val default: ViewMode = GRID
    }
}

/** Filter chips on the documents screen. */
enum class DocumentFilter(val titleKey: String) {
    ALL("documents_filter_all"),
    PDF("documents_filter_pdf"),
    IMAGES("documents_filter_images"),
    DOCUMENTS("documents_filter_documents"),
    ARCHIVES("documents_filter_archives"),
    FAVORITES("documents_filter_favourites"),
    NEEDS_OCR("documents_filter_ocr_pending"),
    ;

    companion object {
        val default: DocumentFilter = ALL
    }
}

/** Theme choice (PRD §39). */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Auto-lock delay for App Lock (PRD §33). */
enum class AutoLockTimeout(val millis: Long?) {
    IMMEDIATELY(0L),
    ONE_MINUTE(60_000L),
    FIVE_MINUTES(300_000L),
    FIFTEEN_MINUTES(900_000L),
    NEVER(null),
}

/** How long trashed documents are kept before the cleanup worker removes them. */
enum class TrashRetention(val days: Int?) {
    SEVEN_DAYS(7),
    THIRTY_DAYS(30),
    NINETY_DAYS(90),
    FOREVER(null),
}

/** Compression presets for the PDF toolkit (PRD §46). */
enum class CompressionPreset(val jpegQuality: Int, val maxImageEdgePx: Int) {
    ORIGINAL(95, 3200),
    HIGH(85, 2400),
    BALANCED(70, 1800),
    SMALL(45, 1200),
    ;

    companion object {
        val default: CompressionPreset = BALANCED
    }
}

/** Page sizes offered when creating a PDF from images. */
enum class PdfPageSize(val widthPt: Float, val heightPt: Float) {
    A4(595f, 842f),
    LETTER(612f, 792f),
    FIT_TO_IMAGE(0f, 0f),
    ;

    companion object {
        val default: PdfPageSize = A4
    }
}

enum class PdfOrientation { PORTRAIT, LANDSCAPE, FIT }

/** Scanner image quality, mapped to a target long edge in pixels. */
enum class ScannerQuality(val targetLongEdgePx: Int, val jpegQuality: Int) {
    STANDARD(1600, 80),
    HIGH(2400, 90),
    MAXIMUM(3200, 95),
    ;

    companion object {
        val default: ScannerQuality = HIGH
    }
}

/** Scanner colour processing modes (PRD §12). */
enum class ScanFilter {
    ORIGINAL,
    ENHANCE,
    GRAYSCALE,
    BLACK_AND_WHITE,
    MAGIC_COLOUR,
    ;

    companion object {
        val default: ScanFilter = ENHANCE
    }
}

/** Scanner capture modes tune detection and enhancement defaults. */
enum class ScanMode(val defaultFilter: ScanFilter, val prefersHighContrast: Boolean) {
    AUTO(ScanFilter.ENHANCE, false),
    DOCUMENT(ScanFilter.ENHANCE, false),
    ID_CARD(ScanFilter.MAGIC_COLOUR, false),
    RECEIPT(ScanFilter.BLACK_AND_WHITE, true),
    WHITEBOARD(ScanFilter.ENHANCE, true),
    PHOTO(ScanFilter.ORIGINAL, false),
    ;

    companion object {
        val default: ScanMode = AUTO
    }
}

/** Torch state requested by the scanner UI. */
enum class FlashMode { AUTO, ON, OFF }
