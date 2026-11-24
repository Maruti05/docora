package com.vedica.labs.ind.app.docora.core.imaging

import android.graphics.Bitmap
import com.vedica.labs.ind.app.docora.core.common.DocoraLog
import com.vedica.labs.ind.app.docora.core.model.DocumentDetection
import com.vedica.labs.ind.app.docora.core.model.Point2F
import com.vedica.labs.ind.app.docora.core.model.QuadCorners
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.RotatedRect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * OpenCV contour detector for document edges.
 *
 * The pipeline is the one used by desktop scanners: blur, find edges, close small gaps, then look
 * for the largest rectangle-ish contour. Two details make it reliable with a phone camera:
 *
 *  * **Rounded and fold-damaged pages** rarely approximate to exactly four points, so a contour
 *    that fails the four-point test is retried with its minimum-area rotated rectangle. That is
 *    still an honest quad for a rectangular sheet.
 *  * **Candidates are scored, not just taken in area order.** A bright table or a lamp-lit wall
 *    produces a huge, very rectangular contour that would otherwise win over the page. The score
 *    combines coverage, how completely the contour fills its fitted rectangle, and the aspect
 *    ratio, and anything covering almost the whole frame is discarded: that is the camera looking
 *    at nothing but a surface, not a framed page.
 *
 * If Canny misses a low-contrast page - a white receipt on a white desk is the classic case - it
 * retries with an adaptive threshold, which recovers the boundary from the interior/background
 * split instead of relying on a strong edge.
 *
 * Live frames use a small 320x240 mat, so a full pass costs well under a preview frame. The
 * detector smooths live corners exactly like the Kotlin fallback, so the outline does not twitch
 * while the user holds the phone.
 */
class OpenCvDocumentDetector(
    private val smoothing: Float = 0.45f,
    private val releaseAfterMissedFrames: Int = 8,
    private val confidenceDecay: Float = 0.6f,
) {

    private var smoothed: QuadCorners? = null
    private var smoothedConfidence = 0f
    private var missedFrames = 0

    fun reset() {
        smoothed = null
        smoothedConfidence = 0f
        missedFrames = 0
    }

    /** Live detection over a row-major 8-bit grayscale frame. */
    fun detect(gray: ByteArray, width: Int, height: Int): DocumentDetection {
        if (!OpenCvRuntime.isReady || width < MIN_EDGE || height < MIN_EDGE) {
            return DocumentDetection.None
        }
        if (gray.size < width * height) return DocumentDetection.None

        val found = measure(gray, width, height)
        if (found == null) {
            missedFrames++
            val held = smoothed
            if (held == null || missedFrames >= releaseAfterMissedFrames) {
                reset()
                return DocumentDetection.None
            }
            smoothedConfidence *= confidenceDecay
            return DocumentDetection(held, smoothedConfidence, polygonArea(held))
        }

        missedFrames = 0
        val previous = smoothed
        val corners = if (previous == null) {
            found.corners
        } else {
            ease(previous, found.corners)
        }
        smoothed = corners
        smoothedConfidence = if (previous == null) {
            found.confidence
        } else {
            smoothedConfidence + (found.confidence - smoothedConfidence) * smoothing
        }
        return DocumentDetection(corners, smoothedConfidence, polygonArea(corners))
    }

    /** One-shot detection for a captured page. Returns corners in the bitmap's orientation. */
    fun detect(bitmap: Bitmap): QuadCorners? {
        if (!OpenCvRuntime.isReady || bitmap.width < MIN_EDGE || bitmap.height < MIN_EDGE) return null
        return runCatching {
            val rgba = Mat()
            val gray = Mat()
            try {
                Utils.bitmapToMat(bitmap, rgba)
                Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
                findQuad(gray)?.corners
            } finally {
                rgba.release()
                gray.release()
            }
        }.getOrNull()
    }

    // ------------------------------------------------------------------ internals

    private data class Found(
        val corners: QuadCorners,
        val confidence: Float,
        val coverage: Float,
    )

    private fun measure(gray: ByteArray, width: Int, height: Int): Found? = runCatching {
        val mat = Mat(height, width, CvType.CV_8UC1)
        try {
            mat.put(0, 0, gray)
            findQuad(mat)
        } finally {
            mat.release()
        }
    }.getOrNull()

    private fun findQuad(gray: Mat): Found? {
        val blurred = Mat()
        val edges = Mat()
        val binary = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        return try {
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 50.0, 150.0)
            Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernel)
            bestQuad(edges)?.let {
                DocoraLog.d(TAG, "quad_canny", "coverage" to it.coverage, "confidence" to it.confidence)
                return it
            }

            Imgproc.adaptiveThreshold(
                blurred,
                binary,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                31,
                12.0,
            )
            Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_CLOSE, kernel)
            bestQuad(binary)?.let {
                DocoraLog.d(TAG, "quad_threshold", "coverage" to it.coverage, "confidence" to it.confidence)
                return it
            }

            Core.bitwise_not(binary, binary)
            bestQuad(binary)?.let {
                DocoraLog.d(TAG, "quad_inverse", "coverage" to it.coverage, "confidence" to it.confidence)
                return it
            }
            null
        } catch (_: Throwable) {
            null
        } finally {
            blurred.release()
            edges.release()
            binary.release()
            kernel.release()
        }
    }

    private fun bestQuad(mask: Mat): Found? {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        try {
            Imgproc.findContours(
                mask,
                contours,
                hierarchy,
                Imgproc.RETR_LIST,
                Imgproc.CHAIN_APPROX_SIMPLE,
            )
            val width = mask.cols().toFloat()
            val height = mask.rows().toFloat()
            val total = (width * height).coerceAtLeast(1f)

            var best: Found? = null
            var bestScore = 0f

            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area <= 0.0) continue
                val coverage = (area / total).toFloat()
                if (coverage < MIN_COVERAGE || coverage > MAX_COVERAGE) continue

                val points = quadPoints(contour) ?: continue
                val ordered = orderPoints(points)
                val aspect = aspectRatio(ordered)
                if (aspect < MIN_ASPECT || aspect > MAX_ASPECT) continue

                // How much of the fitted rotated rectangle the contour actually fills. A page is
                // close to 1; a ragged background blob is not, and must not outrank it.
                val fit = runCatching { Imgproc.minAreaRect(MatOfPoint2f(*contour.toArray())) }.getOrNull()
                    ?.let { rectangleArea(it) }
                    ?: continue
                if (fit <= 0.0) continue
                val rectangularity = (area / fit).coerceIn(0.0, 1.0).toFloat()
                if (rectangularity < MIN_RECTANGULARITY) continue

                val score = coverage * rectangularity
                if (score <= bestScore) continue
                bestScore = score

                val normalized = ordered.map { point ->
                    Point2F(
                        x = (point.x / width).toFloat().coerceIn(0f, 1f),
                        y = (point.y / height).toFloat().coerceIn(0f, 1f),
                    )
                }
                val confidence = (
                    0.62f +
                        0.22f * rectangularity +
                        0.15f * (coverage / 0.7f).coerceIn(0f, 1f)
                    ).coerceAtMost(0.97f)
                best = Found(
                    corners = QuadCorners.fromPoints(normalized),
                    confidence = confidence,
                    coverage = coverage,
                )
            }
            return best
        } catch (_: Throwable) {
            return null
        } finally {
            contours.forEach { runCatching { it.release() } }
            hierarchy.release()
        }
    }

    /**
     * Four corners for a contour: the approximation when it is already a convex quad, otherwise the
     * minimum-area rotated rectangle, which handles rounded corners and a slightly wavy page edge.
     */
    private fun quadPoints(contour: MatOfPoint): Array<Point>? {
        val curve = MatOfPoint2f(*contour.toArray())
        val approximate = MatOfPoint2f()
        try {
            val perimeter = Imgproc.arcLength(curve, true)
            if (perimeter <= 0.0) return null
            Imgproc.approxPolyDP(curve, approximate, perimeter * 0.02, true)
            val points = approximate.toArray()
            if (points.size == 4 && Imgproc.isContourConvex(MatOfPoint(*points))) return points
            return rectPoints(Imgproc.minAreaRect(curve))
        } finally {
            curve.release()
            approximate.release()
        }
    }

    /** Corners of a rotated rectangle, in a stable clockwise-ish order. */
    private fun rectPoints(rect: RotatedRect): Array<Point> {
        val angle = Math.toRadians(rect.angle.toDouble())
        val cos = kotlin.math.cos(angle)
        val sin = kotlin.math.sin(angle)
        val halfWidth = rect.size.width / 2.0
        val halfHeight = rect.size.height / 2.0
        val cx = rect.center.x
        val cy = rect.center.y
        fun corner(sx: Double, sy: Double) = Point(
            cx + sx * halfWidth * cos - sy * halfHeight * sin,
            cy + sx * halfWidth * sin + sy * halfHeight * cos,
        )
        return arrayOf(
            corner(-1.0, -1.0),
            corner(1.0, -1.0),
            corner(1.0, 1.0),
            corner(-1.0, 1.0),
        )
    }

    /** Clockwise order: top-left, top-right, bottom-right, bottom-left. */
    private fun orderPoints(points: Array<Point>): Array<Point> {
        val topLeft = points.minBy { it.x + it.y }
        val bottomRight = points.maxBy { it.x + it.y }
        val topRight = points.maxBy { it.x - it.y }
        val bottomLeft = points.minBy { it.x - it.y }
        return arrayOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    /** Long side over short side of the ordered quad; 1 is a square, 1.4 an A4 page. */
    private fun aspectRatio(points: Array<Point>): Float {
        fun distance(a: Point, b: Point): Float = hypot(a.x - b.x, a.y - b.y).toFloat()
        val width = max(
            distance(points[0], points[1]).toDouble(),
            distance(points[3], points[2]).toDouble(),
        )
        val height = max(
            distance(points[0], points[3]).toDouble(),
            distance(points[1], points[2]).toDouble(),
        )
        val long = max(width, height)
        val short = min(width, height)
        if (short <= 0.5) return Float.MAX_VALUE
        return (long / short).toFloat()
    }

    private fun rectangleArea(rectangle: RotatedRect): Double =
        rectangle.size.width * rectangle.size.height

    private fun ease(from: QuadCorners, to: QuadCorners): QuadCorners {
        val first = from.toPointList()
        val second = to.toPointList()
        return QuadCorners.fromPoints(
            first.indices.map { index ->
                Point2F(
                    x = first[index].x + (second[index].x - first[index].x) * smoothing,
                    y = first[index].y + (second[index].y - first[index].y) * smoothing,
                )
            },
        )
    }

    private fun polygonArea(corners: QuadCorners): Float {
        val points = corners.toPointList()
        var area = 0f
        for (index in points.indices) {
            val next = points[(index + 1) % points.size]
            area += points[index].x * next.y - next.x * points[index].y
        }
        return (abs(area) / 2f).coerceIn(0f, 1f)
    }

    private companion object {
        const val TAG = "OpenCvDocDetector"
        const val MIN_EDGE = 20

        /** A page smaller than this share of the frame is a logo, not a document. */
        const val MIN_COVERAGE = 0.055f

        /**
         * Above this the "document" is the whole frame - a plain desk or wall. Cropping to it would
         * gain nothing, and it must not win over the actual sheet inside the frame.
         */
        const val MAX_COVERAGE = 0.94f

        /** Contour area over fitted-rectangle area; a real sheet is close to 1. */
        const val MIN_RECTANGULARITY = 0.55f

        /** Long/short side limits. Wide receipts and A4 pages both fit comfortably. */
        const val MIN_ASPECT = 0.24f
        const val MAX_ASPECT = 4.2f
    }
}
