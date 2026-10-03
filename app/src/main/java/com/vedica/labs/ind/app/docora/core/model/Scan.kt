package com.vedica.labs.ind.app.docora.core.model

/**
 * A page captured by the scanner.
 *
 * [corners] are normalised to the captured bitmap (0f..1f) so that the crop can be
 * re-applied at different resolutions without losing precision.
 */
data class ScanPage(
    val index: Int,
    val originalImagePath: String,
    val corners: QuadCorners = QuadCorners.fullFrame(),
    val rotationDegrees: Int = 0,
    val filter: ScanFilter = ScanFilter.default,
)

/** Four normalised corners in clockwise order starting at the top-left. */
data class QuadCorners(
    val topLeftX: Float,
    val topLeftY: Float,
    val topRightX: Float,
    val topRightY: Float,
    val bottomRightX: Float,
    val bottomRightY: Float,
    val bottomLeftX: Float,
    val bottomLeftY: Float,
) {
    fun toPointList(): List<Point2F> = listOf(
        Point2F(topLeftX, topLeftY),
        Point2F(topRightX, topRightY),
        Point2F(bottomRightX, bottomRightY),
        Point2F(bottomLeftX, bottomLeftY),
    )

    /** True when the quad is close enough to the full frame to skip the correction step. */
    val isFullFrame: Boolean
        get() = topLeftX <= 0.02f && topLeftY <= 0.02f &&
            topRightX >= 0.98f && topRightY <= 0.02f &&
            bottomRightX >= 0.98f && bottomRightY >= 0.98f &&
            bottomLeftX <= 0.02f && bottomLeftY >= 0.98f

    companion object {
        fun fullFrame(): QuadCorners = QuadCorners(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)

        fun fromPoints(points: List<Point2F>): QuadCorners {
            require(points.size == 4) { "A quadrilateral needs exactly four corners" }
            return QuadCorners(
                topLeftX = points[0].x,
                topLeftY = points[0].y,
                topRightX = points[1].x,
                topRightY = points[1].y,
                bottomRightX = points[2].x,
                bottomRightY = points[2].y,
                bottomLeftX = points[3].x,
                bottomLeftY = points[3].y,
            )
        }
    }
}

/** A resolution independent 2D point. */
data class Point2F(val x: Float, val y: Float)

/**
 * A scan session that is still being edited. Persisted so that a fold or process death
 * does not lose captured pages (state restoration, PRD §2).
 */
data class ScanSession(
    val id: Long,
    val createdAt: Long,
    val mode: ScanMode,
    val pages: List<ScanPage>,
) {
    val pageCount: Int get() = pages.size
    val isEmpty: Boolean get() = pages.isEmpty()
}

/**
 * What the scanner produces when a session is saved.
 *
 * A PDF is the default because a scan is conceptually one multi-page document; images are offered
 * because a single photographed page (a receipt, a whiteboard) is more useful as a photo.
 */
enum class ScanOutput {
    PDF,
    IMAGES,
    ;

    companion object {
        val default: ScanOutput = PDF
    }
}

/** Result of running the document-edge detector over a camera frame. */
data class DocumentDetection(
    val corners: QuadCorners?,
    val confidence: Float,
    val coverage: Float,
) {
    val hasDocument: Boolean get() = corners != null && confidence >= MIN_CONFIDENCE

    companion object {
        const val MIN_CONFIDENCE = 0.55f
        val None = DocumentDetection(corners = null, confidence = 0f, coverage = 0f)
    }
}
