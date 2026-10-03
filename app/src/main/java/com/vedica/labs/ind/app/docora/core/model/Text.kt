package com.vedica.labs.ind.app.docora.core.model

/** Result of running on-device text recognition over one page. */
data class OcrPageText(
    val pageIndex: Int,
    val text: String,
    val confidence: Float?,
)

/** Result of running text recognition over a whole document. */
data class OcrDocumentText(
    val pages: List<OcrPageText>,
    val languageHint: String?,
    val engine: String,
) {
    val combinedText: String get() = pages.joinToString(separator = "\n\n") { it.text }.trim()

    val wordCount: Int
        get() = combinedText.split(WHITESPACE).count { it.isNotBlank() }

    val isEmpty: Boolean get() = combinedText.isBlank()

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}

/** Extracted text for one page of a document (PDF text layer or OCR result). */
data class DocumentPageText(
    val documentId: String,
    val pageIndex: Int,
    val content: String,
    val origin: TextOrigin,
    val updatedAt: Long,
)

/** Rendered page geometry used by the PDF toolkit and the viewer page indicator. */
data class DocumentPageInfo(
    val pageIndex: Int,
    val widthPt: Float,
    val heightPt: Float,
    val rotationDegrees: Int,
    val textLength: Int,
)

/** User drawn annotation on a page (PRD §10, extended in v2). */
data class Annotation(
    val id: String,
    val documentId: String,
    val pageIndex: Int,
    val type: AnnotationType,
    val colorHex: String,
    val points: List<Point2F>,
    val text: String?,
    val createdAt: Long,
)

enum class AnnotationType {
    HIGHLIGHT,
    UNDERLINE,
    STRIKETHROUGH,
    FREEHAND,
    NOTE,
    RECTANGLE,
}

/** A bookmark inside a document. */
data class Bookmark(
    val id: String,
    val documentId: String,
    val pageIndex: Int,
    val label: String?,
    val createdAt: Long,
)
