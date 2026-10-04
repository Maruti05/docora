package com.vedica.labs.ind.app.docora.core.imaging

import com.vedica.labs.ind.app.docora.core.model.DocumentDetection
import com.vedica.labs.ind.app.docora.core.model.Point2F
import com.vedica.labs.ind.app.docora.core.model.QuadCorners
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Finds the page in a camera frame and reports it as a normalised quadrilateral.
 *
 * The approach mirrors the classic scan pipeline (Canny-style gradients, projection profiles, then a
 * convex quad): edges are found from the luminance gradient, the four boundaries are read from
 * edge-weighted projection profiles, and each boundary is measured twice - once per half of its
 * perpendicular axis - which is what lets the result be a trapezoid rather than a plain box, so a
 * page photographed at an angle is followed instead of forced into a rectangle.
 *
 * Everything runs on the coarse analysis grid (a few thousand pixels), not the preview frame, which
 * is what keeps it within a fraction of a frame's worth of work.
 *
 * The detector is stateful on purpose. Raw per-frame results jitter by a pixel or two, and a quad
 * that twitches while the user holds still looks broken, so corners are eased toward the new
 * measurement and, when the page leaves the frame, the last quad is held briefly and then faded out
 * rather than snapping to nothing.
 *
 * It is pure Kotlin: no Android types, so the whole thing is unit tested on the JVM.
 */
class DocumentEdgeDetector(
    /** Weight of each new measurement when easing corners; 1 means "no smoothing". */
    private val smoothing: Float = 0.4f,
    /** Frames a quad survives after the page disappears, so a wobble does not blank the outline. */
    private val releaseAfterMissedFrames: Int = 8,
    /** Confidence decay per missed frame, so the outline fades instead of blinking off. */
    private val confidenceDecay: Float = 0.6f,
) {

    private var smoothed: QuadCorners? = null
    private var smoothedConfidence: Float = 0f
    private var missedFrames: Int = 0

    fun reset() {
        smoothed = null
        smoothedConfidence = 0f
        missedFrames = 0
    }

    /** Latest eased quad, or null when nothing has been found recently. */
    val currentCorners: QuadCorners? get() = smoothed

    /**
     * Consumes one greyscale frame and returns the document outline.
     *
     * [gray] is row-major, [width] x [height]; [Detection.thresholdFactor] and friends are fixed at
     * construction so the tuning is covered by tests rather than sprinkled through the code.
     */
    fun detect(gray: IntArray, width: Int, height: Int): DocumentDetection {
        val measurement = measure(gray, width, height)
        if (measurement == null) {
            missedFrames++
            val held = smoothed
            if (held == null || missedFrames >= releaseAfterMissedFrames) {
                smoothed = null
                smoothedConfidence = 0f
                missedFrames = 0
                return DocumentDetection.None
            }
            // While holding, only the frame count decides when the outline goes; the decaying
            // confidence is what the overlay fades with, so the two never fight over the release.
            smoothedConfidence *= confidenceDecay
            return DocumentDetection(held, smoothedConfidence, quadCoverage(held))
        }

        missedFrames = 0
        val previous = smoothed
        val eased = if (previous == null) measurement.corners else ease(previous, measurement.corners)
        smoothed = eased
        smoothedConfidence = if (previous == null) {
            measurement.confidence
        } else {
            smoothedConfidence + (measurement.confidence - smoothedConfidence) * smoothing
        }
        return DocumentDetection(eased, smoothedConfidence, quadCoverage(eased))
    }

    // ------------------------------------------------------------------ measurement

    /** A single frame's raw finding, before smoothing. */
    private data class Measurement(val corners: QuadCorners, val confidence: Float)

    private fun measure(gray: IntArray, width: Int, height: Int): Measurement? {
        if (width < MIN_EDGE || height < MIN_EDGE || gray.size < width * height) return null

        val magnitude = gradientMagnitude(gray, width, height)
        var peak = 0f
        var sum = 0f
        for (value in magnitude) {
            if (value > peak) peak = value
            sum += value
        }
        val interior = max(1, (width - 2) * (height - 2))
        val mean = sum / interior
        // A flat frame (a wall, a dark room) has no meaningful gradient and must not produce a quad.
        if (peak < MIN_PEAK || mean < MIN_MEAN_GRADIENT) return null

        val threshold = max(mean * edgeThresholdFactor, peak * peakRatio)

        val middleX = width / 2
        val middleY = height / 2

        // Each boundary is measured on its own half so the quad can be a trapezoid.
        val leftTop = bounds(columnProfile(magnitude, width, height, threshold, 0, middleY), strongRatio)
        val leftBottom = bounds(columnProfile(magnitude, width, height, threshold, middleY, height), strongRatio)
        val rightTop = bounds(columnProfile(magnitude, width, height, threshold, 0, middleY), strongRatio)
        val rightBottom = bounds(columnProfile(magnitude, width, height, threshold, middleY, height), strongRatio)
        val topLeft = bounds(rowProfile(magnitude, width, height, threshold, 0, middleX), strongRatio)
        val topRight = bounds(rowProfile(magnitude, width, height, threshold, middleX, width), strongRatio)
        val bottomLeft = bounds(rowProfile(magnitude, width, height, threshold, 0, middleX), strongRatio)
        val bottomRight = bounds(rowProfile(magnitude, width, height, threshold, middleX, width), strongRatio)

        // The axis-aligned extremes double as the fallback whenever a half-profile is too weak.
        val left = leftTop?.first ?: leftBottom?.first ?: return null
        val right = rightTop?.last ?: rightBottom?.last ?: return null
        val top = topLeft?.first ?: topRight?.first ?: return null
        val bottom = bottomLeft?.last ?: bottomRight?.last ?: return null

        if (right - left < MIN_SIDE_FRACTION * width) return null
        if (bottom - top < MIN_SIDE_FRACTION * height) return null

        val cornerPoints = listOf(
            Point2F((leftTop?.first ?: left).toFloat() / width, (topLeft?.first ?: top).toFloat() / height),
            Point2F((rightTop?.last ?: right).toFloat() / width, (topRight?.first ?: top).toFloat() / height),
            Point2F((rightBottom?.last ?: right).toFloat() / width, (bottomRight?.last ?: bottom).toFloat() / height),
            Point2F((leftBottom?.first ?: left).toFloat() / width, (bottomLeft?.last ?: bottom).toFloat() / height),
        ).map { point ->
            Point2F(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f))
        }

        val corners = normalise(QuadCorners.fromPoints(cornerPoints)) ?: return null
        val coverage = quadCoverage(corners)
        if (coverage < MIN_COVERAGE || coverage > MAX_COVERAGE) return null

        val confidence = perimeterConfidence(magnitude, width, height, corners, threshold)
        if (confidence < DocumentDetection.MIN_CONFIDENCE) return null

        return Measurement(corners, confidence)
    }

    /**
     * Sobel gradient magnitude, summed over both axes.
     *
     * `|gx| + |gy|` rather than the Euclidean norm: it is a monotonic stand-in for magnitude, and it
     * avoids a square root per pixel across the whole grid.
     */
    private fun gradientMagnitude(gray: IntArray, width: Int, height: Int): FloatArray {
        val magnitude = FloatArray(width * height)
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val index = row + x
                val topLeft = gray[index - width - 1].toFloat()
                val top = gray[index - width].toFloat()
                val topRight = gray[index - width + 1].toFloat()
                val left = gray[index - 1].toFloat()
                val right = gray[index + 1].toFloat()
                val bottomLeft = gray[index + width - 1].toFloat()
                val bottom = gray[index + width].toFloat()
                val bottomRight = gray[index + width + 1].toFloat()
                val gx = (topRight + 2f * right + bottomRight) - (topLeft + 2f * left + bottomLeft)
                val gy = (bottomLeft + 2f * bottom + bottomRight) - (topLeft + 2f * top + topRight)
                magnitude[index] = abs(gx) + abs(gy)
            }
        }
        return magnitude
    }

    /** Edge weight per column, counting only rows in `[from, to)`. */
    private fun columnProfile(
        magnitude: FloatArray,
        width: Int,
        height: Int,
        threshold: Float,
        from: Int,
        to: Int,
    ): FloatArray {
        val profile = FloatArray(width)
        for (y in max(1, from) until minOf(height - 1, to)) {
            val row = y * width
            for (x in 1 until width - 1) {
                val value = magnitude[row + x]
                if (value >= threshold) profile[x] += value
            }
        }
        return profile
    }

    /** Edge weight per row, counting only columns in `[from, to)`. */
    private fun rowProfile(
        magnitude: FloatArray,
        width: Int,
        height: Int,
        threshold: Float,
        from: Int,
        to: Int,
    ): FloatArray {
        val profile = FloatArray(height)
        for (x in max(1, from) until minOf(width - 1, to)) {
            for (y in 1 until height - 1) {
                val value = magnitude[y * width + x]
                if (value >= threshold) profile[y] += value
            }
        }
        return profile
    }

    /**
     * First and last index whose profile reaches [ratio] of the peak, or null when nothing does.
     *
     * A crisp edge lights up a single column, so `first == last` is a perfectly good answer and must
     * not be treated as degenerate; only "no index reached the cut" is a failure.
     */
    private fun bounds(profile: FloatArray, ratio: Float): IntRange? {
        var peak = 0f
        for (value in profile) if (value > peak) peak = value
        if (peak <= 0f) return null
        val cut = peak * ratio
        var first = -1
        var last = -1
        for (index in profile.indices) {
            if (profile[index] >= cut) {
                if (first < 0) first = index
                last = index
            }
        }
        return if (first < 0) null else first..last
    }

    /** Fraction of perimeter samples that actually sit on an edge; 0..1 is a readable confidence. */
    private fun perimeterConfidence(
        magnitude: FloatArray,
        width: Int,
        height: Int,
        corners: QuadCorners,
        threshold: Float,
    ): Float {
        val points = corners.toPointList()
        var hits = 0
        var total = 0
        for (side in points.indices) {
            val from = points[side]
            val to = points[(side + 1) % points.size]
            for (step in 0..PERIMETER_SAMPLES) {
                val t = step.toFloat() / PERIMETER_SAMPLES
                val x = ((from.x + (to.x - from.x) * t) * width).roundToInt().coerceIn(0, width - 1)
                val y = ((from.y + (to.y - from.y) * t) * height).roundToInt().coerceIn(0, height - 1)
                // Sample a 3x3 neighbourhood: on a coarse grid the true edge is often one pixel over.
                var best = 0f
                for (dy in -1..1) {
                    val sy = (y + dy).coerceIn(0, height - 1)
                    for (dx in -1..1) {
                        val sx = (x + dx).coerceIn(0, width - 1)
                        val value = magnitude[sy * width + sx]
                        if (value > best) best = value
                    }
                }
                if (best >= threshold) hits++
                total++
            }
        }
        return if (total == 0) 0f else hits.toFloat() / total
    }

    // ------------------------------------------------------------------ geometry

    /** Repairs a quad whose corners crossed over; null when it cannot be salvaged. */
    private fun normalise(corners: QuadCorners): QuadCorners? {
        val points = corners.toPointList()
        points.forEach { if (it.x.isNaN() || it.y.isNaN()) return null }
        val ordered = listOf(
            Point2F(minOf(points[0].x, points[1].x), minOf(points[0].y, points[3].y)),
            Point2F(maxOf(points[0].x, points[1].x), minOf(points[1].y, points[2].y)),
            Point2F(maxOf(points[2].x, points[3].x), maxOf(points[1].y, points[2].y)),
            Point2F(minOf(points[2].x, points[3].x), maxOf(points[0].y, points[3].y)),
        )
        if (ordered[1].x <= ordered[0].x || ordered[3].y <= ordered[0].y) return null
        return QuadCorners.fromPoints(ordered)
    }

    private fun ease(from: QuadCorners, to: QuadCorners): QuadCorners {
        val a = from.toPointList()
        val b = to.toPointList()
        return QuadCorners.fromPoints(
            a.indices.map { index ->
                Point2F(
                    x = a[index].x + (b[index].x - a[index].x) * smoothing,
                    y = a[index].y + (b[index].y - a[index].y) * smoothing,
                )
            },
        )
    }

    /** Fraction of the frame the quad covers. */
    private fun quadCoverage(corners: QuadCorners): Float {
        val points = corners.toPointList()
        var area = 0f
        for (index in points.indices) {
            val next = points[(index + 1) % points.size]
            area += points[index].x * next.y - next.x * points[index].y
        }
        return (abs(area) / 2f).coerceIn(0f, 1f)
    }

    private companion object {
        /** Below this the grid is too small for the profiles to mean anything. */
        const val MIN_EDGE = 8

        /** Gradient floor: an empty scene is never accepted, however uniform its noise. */
        const val MIN_PEAK = 30f
        const val MIN_MEAN_GRADIENT = 2f

        /** Column/row counts as an edge at this fraction of the strongest one. */
        const val strongRatio = 0.45f

        /** Absolute floor for "is this pixel an edge", as a share of the strongest gradient. */
        const val peakRatio = 0.2f

        /** A page smaller than this share of the frame is treated as noise, not a document. */
        const val MIN_SIDE_FRACTION = 0.16f

        /** Coverage window; above [MAX_COVERAGE] the frame is essentially all page. */
        const val MIN_COVERAGE = 0.06f
        const val MAX_COVERAGE = 0.995f

        const val PERIMETER_SAMPLES = 40

        /** Relative to the mean gradient, so dim and bright scenes are treated alike. */
        const val edgeThresholdFactor = 1.6f
    }
}
