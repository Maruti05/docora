package com.vedica.labs.ind.app.docora.core.pdf

/**
 * Value types shared by the PDF viewer and the PDF editing tools (PRD A�10, A�11).
 *
 * Everything here is plain data so it can cross the ViewModel boundary without pulling
 * PDFBox or renderer types into Compose. Coordinates used by annotations are *normalised*
 * (0..1) against the page exactly as the user sees it, including the viewer rotation that
 * was active while the mark was drawn. Keeping that fact in the model is what lets the
 * writer undo the rotation and produce correct PDF-space coordinates later.
 */

/** Geometry of one page as reported by the PDF renderer / PDFBox. */
data class PdfPageInfo(
    val index: Int,
    val widthPoints: Float,
    val heightPoints: Float,
    val rotation: Int,
)

/** Page count plus document metadata, read once when a document is opened. */
data class PdfDocumentInfo(
    val pageCount: Int,
    val pages: List<PdfPageInfo> = emptyList(),
    val metadata: PdfMetadata = PdfMetadata(),
)

/** Editable standard document information dictionary. */
data class PdfMetadata(
    val title: String = "",
    val author: String = "",
    val subject: String = "",
    val keywords: String = "",
)

/** Compression presets (PRD A�46). */
enum class PdfCompressionPreset {
    /** Re-writes the file and drops unused objects without touching image resolution. */
    ORIGINAL,

    /** Keeps detail; suitable for text-heavy documents and light photographs. */
    HIGH,

    /** Default balance between legibility and size. */
    BALANCED,

    /** Smallest output; best for sharing scans over slow links. */
    SMALL,
    ;

    /** Longest edge an embedded image is allowed to keep, or 0 to leave it untouched. */
    val maxImageEdgePx: Int
        get() = when (this) {
            ORIGINAL -> 0
            HIGH -> 2200
            BALANCED -> 1600
            SMALL -> 1100
        }

    /** JPEG quality used when recompressing photographic images. */
    val jpegQuality: Float
        get() = when (this) {
            ORIGINAL -> 0f
            HIGH -> 0.85f
            BALANCED -> 0.72f
            SMALL -> 0.55f
        }

    /** Rough multiplier used for the "estimated output" hint before processing. */
    val estimatedRatio: Float
        get() = when (this) {
            ORIGINAL -> 0.98f
            HIGH -> 0.78f
            BALANCED -> 0.58f
            SMALL -> 0.38f
        }
}

/** Page size used when turning images into a PDF. */
enum class PdfPageSize { A4, LETTER, FIT }

/** Orientation used when turning images into a PDF. */
enum class PdfOrientation { PORTRAIT, LANDSCAPE, AUTO }

/** Options for the images-to-PDF builder (PRD A�11). */
data class PdfImageOptions(
    val pageSize: PdfPageSize = PdfPageSize.A4,
    val orientation: PdfOrientation = PdfOrientation.AUTO,
    val marginPoints: Float = 24f,
    val jpegQuality: Int = 85,
    val maxImageEdgePx: Int = 2200,
)

/** One page of a rebuilt PDF: where it comes from and how it is rotated. */
data class PdfPageSpec(
    val sourceIndex: Int,
    val rotationDegrees: Int = 0,
)

/** Kinds of mark the viewer can add (PRD A�10). */
enum class PdfMarkKind {
    HIGHLIGHT,
    UNDERLINE,
    STRIKEOUT,
    SQUIGGLY,
    INK,
    NOTE,
    TEXT,
    RECTANGLE,
    ELLIPSE,
    LINE,
    ARROW,
    REDACT,
}

/** A point in normalised, displayed page space (0..1, top-left origin). */
data class PdfPoint(val x: Float, val y: Float)

/**
 * A mark drawn in the viewer and not yet written to a PDF.
 *
 * [displayRotation] is the viewer rotation at drawing time (in addition to the page's own
 * `/Rotate`). The writer uses it together with the page media box to map [start], [end] and
 * [points] back into unrotated PDF user space.
 */
data class PdfMark(
    val kind: PdfMarkKind,
    val pageIndex: Int,
    val colorArgb: Int,
    val displayRotation: Int = 0,
    val start: PdfPoint? = null,
    val end: PdfPoint? = null,
    val points: List<PdfPoint> = emptyList(),
    val note: String? = null,
    val strokeWidth: Float = 2.4f,
    /** Font size in PDF points for [PdfMarkKind.TEXT]; ignored by every other kind. */
    val fontSize: Float = 12f,
)

/** One in-document search hit, grouped by page so the UI can show a result list. */
data class PdfSearchHit(
    val pageIndex: Int,
    val snippet: String,
    val occurrences: Int,
)

/**
 * A word-sized run from the PDF text layer.
 *
 * Coordinates are normalised (0..1) against the page exactly as it is rendered, including the
 * page's own `/Rotate` value. That is the same space [PdfMark] uses, so a selection can become a
 * highlight without another coordinate conversion.
 */
data class PdfTextRun(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float, tolerance: Float = 0.004f): Boolean =
        x >= left - tolerance &&
            x <= right + tolerance &&
            y >= top - tolerance &&
            y <= bottom + tolerance
}
