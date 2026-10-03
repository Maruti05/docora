package com.vedica.labs.ind.app.docora.core.imaging

import com.vedica.labs.ind.app.docora.core.model.Point2F
import com.vedica.labs.ind.app.docora.core.model.QuadCorners
import kotlin.math.max
import kotlin.math.min

/**
 * Pure, dependency-free image maths used by the scanner.
 *
 * Everything that can be decided from numbers alone lives here rather than in
 * [ScanImageProcessor]: threshold selection, quad geometry and luminance statistics are the parts
 * that are easy to get subtly wrong, and keeping them free of `android.graphics` means they are
 * covered by fast JVM tests instead of instrumentation runs.
 */
object ScanImageMath {

    /** Rec. 601 luma, the same weighting the platform uses when it converts to greyscale. */
    fun luminance(argb: Int): Int {
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        return (red * 299 + green * 587 + blue * 114) / 1000
    }

    fun graysOf(pixels: IntArray): IntArray = IntArray(pixels.size) { luminance(pixels[it]) }

    /**
     * Otsu's method: picks the threshold that maximises between-class variance.
     *
     * A fixed threshold fails in practice - a page photographed under a warm lamp and the same page
     * scanned under office lighting need different cut-offs, and a document with a grey background
     * would come out either solid black or solid white. Otsu adapts per page with one histogram
     * pass, which is cheap enough to run on a capture.
     */
    fun otsuThreshold(gray: IntArray): Int {
        if (gray.isEmpty()) return 128
        val histogram = IntArray(HISTOGRAM_BUCKETS)
        for (value in gray) {
            histogram[value.coerceIn(0, HISTOGRAM_BUCKETS - 1)]++
        }
        val total = gray.size
        var sum = 0.0
        for (level in 0 until HISTOGRAM_BUCKETS) sum += level * histogram[level]

        var sumBackground = 0.0
        var weightBackground = 0
        var bestVariance = -1.0
        var threshold = 128
        for (level in 0 until HISTOGRAM_BUCKETS) {
            weightBackground += histogram[level]
            if (weightBackground == 0) continue
            val weightForeground = total - weightBackground
            if (weightForeground == 0) break

            sumBackground += level * histogram[level]
            val meanBackground = sumBackground / weightBackground
            val meanForeground = (sum - sumBackground) / weightForeground
            val delta = meanBackground - meanForeground
            val variance = weightBackground.toDouble() * weightForeground * delta * delta
            if (variance > bestVariance) {
                bestVariance = variance
                threshold = level
            }
        }
        // Guard against a page that is entirely one tone: the "threshold" would then be an
        // extreme value and produce a blank result.
        return threshold.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
    }

    /** Mean luminance, used for the auto-exposure style checks in the auto-capture heuristic. */
    fun meanLuminance(gray: IntArray): Float {
        if (gray.isEmpty()) return 0f
        var sum = 0L
        for (value in gray) sum += value
        return sum.toFloat() / gray.size
    }

    /** Standard deviation of luminance: near-zero means a flat frame with nothing to capture. */
    fun standardDeviation(gray: IntArray, mean: Float): Float {
        if (gray.size < 2) return 0f
        var accumulator = 0.0
        for (value in gray) {
            val delta = value - mean
            accumulator += delta * delta
        }
        return kotlin.math.sqrt(accumulator / gray.size).toFloat()
    }

    /** Mean absolute luminance difference between two equally shaped frames. */
    fun meanAbsoluteDifference(first: IntArray, second: IntArray): Float {
        val size = min(first.size, second.size)
        if (size == 0) return Float.MAX_VALUE
        var sum = 0L
        for (index in 0 until size) sum += kotlin.math.abs(first[index] - second[index])
        return sum.toFloat() / size
    }

    /** Turns a normalised quad into pixel coordinates inside a [width] x [height] frame. */
    fun quadToPixels(corners: QuadCorners, width: Int, height: Int): List<Point2F> =
        corners.toPointList().map { point ->
            Point2F(
                x = (point.x * width).coerceIn(0f, width.toFloat()),
                y = (point.y * height).coerceIn(0f, height.toFloat()),
            )
        }

    /**
     * Axis-aligned bounding box of a quad as `[left, top, right, bottom]`, clamped to the frame and
     * guaranteed to be at least one pixel wide and tall so it can always be drawn.
     */
    fun boundingBox(corners: QuadCorners, width: Int, height: Int): IntArray {
        val points = quadToPixels(corners, width, height)
        val left = points.minOf { it.x }.toInt().coerceIn(0, max(0, width - 1))
        val top = points.minOf { it.y }.toInt().coerceIn(0, max(0, height - 1))
        val right = points.maxOf { it.x }.toInt().coerceIn(left + 1, width)
        val bottom = points.maxOf { it.y }.toInt().coerceIn(top + 1, height)
        return intArrayOf(left, top, right, bottom)
    }

    /** Sample size that keeps the long edge at or above [targetLongEdge]; 1 means "no downscale". */
    fun sampleSizeFor(width: Int, height: Int, targetLongEdge: Int): Int {
        if (targetLongEdge <= 0 || width <= 0 || height <= 0) return 1
        var sample = 1
        var longEdge = max(width, height)
        while (longEdge / 2 >= targetLongEdge) {
            longEdge /= 2
            sample *= 2
        }
        return sample
    }

    private const val HISTOGRAM_BUCKETS = 256
    private const val MIN_THRESHOLD = 32
    private const val MAX_THRESHOLD = 224
}
