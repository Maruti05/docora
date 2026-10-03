package com.vedica.labs.ind.app.docora.core.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import com.vedica.labs.ind.app.docora.core.model.QuadCorners
import com.vedica.labs.ind.app.docora.core.model.ScanFilter
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Raster post-processing for captured pages.
 *
 * Every function is a pure transformation: the input bitmap is never mutated, and a new bitmap is
 * returned only when there is real work to do (otherwise the input is handed straight back, which
 * lets callers cheaply skip allocation for `ORIGINAL` scans).
 *
 * Processing is deliberately ordered crop -> rotate -> filter: rotating first would make the
 * normalised crop corners meaningless, and filtering first would quantise colour before the
 * perspective warp resamples it.
 */
object ScanImageProcessor {

    // ------------------------------------------------------------------ decode

    /**
     * Decodes a captured page, normalising its orientation.
     *
     * CameraX writes JPEGs with the rotation recorded in EXIF rather than baked into the pixels, and
     * `BitmapFactory` ignores EXIF entirely; without this step a portrait capture can be displayed
     * sideways. A [maxLongEdge] greater than zero downsamples during decode, so building a 300 px
     * thumbnail from a 12 MP page costs a fraction of the memory.
     */
    fun decode(file: File, maxLongEdge: Int = 0): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = ScanImageMath.sampleSizeFor(bounds.outWidth, bounds.outHeight, maxLongEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        val rotation = exifRotationDegrees(file)
        return if (rotation == 0) decoded else rotate(decoded, rotation)
    }

    /** Orientation stored in the file's EXIF header, normalised to 0/90/180/270. */
    fun exifRotationDegrees(file: File): Int = runCatching {
        val exif = ExifInterface(file.absolutePath)
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)

    // ------------------------------------------------------------------ geometry

    /** Rotates by a multiple of 90 degrees, clockwise. */
    fun rotate(source: Bitmap, degrees: Int): Bitmap {
        val normalised = ((degrees % 360) + 360) % 360
        if (normalised == 0) return source
        val matrix = Matrix().apply { postRotate(normalised.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Perspective-corrects a page from its detected corners.
     *
     * The four corners are mapped onto the corners of a new bitmap with `setPolyToPoly`, which
     * performs a full projective transform: a page photographed at an angle comes out rectangular,
     * which is what makes a phone scan readable. The output is sized to the quad's bounding box, and
     * anything falling outside the source is filled with paper white rather than transparency so the
     * page still looks like a page once it is embedded in a PDF.
     */
    fun crop(source: Bitmap, corners: QuadCorners): Bitmap {
        if (corners.isFullFrame) return source
        val box = ScanImageMath.boundingBox(corners, source.width, source.height)
        val width = (box[2] - box[0]).coerceAtLeast(1)
        val height = (box[3] - box[1]).coerceAtLeast(1)
        val points = ScanImageMath.quadToPixels(corners, source.width, source.height)

        val sourcePoints = floatArrayOf(
            points[0].x, points[0].y,
            points[1].x, points[1].y,
            points[2].x, points[2].y,
            points[3].x, points[3].y,
        )
        val destinationPoints = floatArrayOf(
            0f, 0f,
            width.toFloat(), 0f,
            width.toFloat(), height.toFloat(),
            0f, height.toFloat(),
        )
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(sourcePoints, 0, destinationPoints, 0, 4)) return source

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(
            source,
            matrix,
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )
        return output
    }

    /** Scales down so the long edge is at most [targetLongEdge]; never upscales. */
    fun scaleToLongEdge(source: Bitmap, targetLongEdge: Int): Bitmap {
        if (targetLongEdge <= 0) return source
        val longEdge = max(source.width, source.height)
        if (longEdge <= targetLongEdge) return source
        val ratio = targetLongEdge.toFloat() / longEdge
        val width = (source.width * ratio).roundToInt().coerceAtLeast(1)
        val height = (source.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    // ------------------------------------------------------------------ filters

    fun applyFilter(source: Bitmap, filter: ScanFilter): Bitmap = when (filter) {
        ScanFilter.ORIGINAL -> source
        ScanFilter.ENHANCE -> tone(source, saturation = 1.12f, contrast = 1.20f, brightness = 6f)
        ScanFilter.GRAYSCALE -> tone(source, saturation = 0f, contrast = 1.14f, brightness = 2f)
        ScanFilter.MAGIC_COLOUR -> tone(source, saturation = 1.55f, contrast = 1.28f, brightness = 8f)
        ScanFilter.BLACK_AND_WHITE -> blackAndWhite(source)
    }

    /**
     * Contrast, saturation and brightness applied as a single colour matrix.
     *
     * Doing this through a [ColorMatrixColorFilter] is roughly an order of magnitude faster than a
     * per-pixel loop, which matters because the same filter is re-applied to every page thumbnail
     * while the user moves along the filter strip.
     */
    fun tone(source: Bitmap, saturation: Float, contrast: Float, brightness: Float): Bitmap {
        val matrix = ColorMatrix().apply { setSaturation(saturation) }
        val scale = contrast
        val translate = 128f * (1f - scale) + brightness
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(matrix)
            isFilterBitmap = true
        }
        Canvas(output).drawBitmap(source, 0f, 0f, paint)
        return output
    }

    /** True black-and-white: greyscale followed by an Otsu threshold, in a single pixel pass. */
    fun blackAndWhite(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val thresholded = threshold(ScanImageMath.graysOf(pixels))
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(thresholded, 0, width, 0, 0, width, height)
        return output
    }

    /** Pure greyscale-to-bitonal conversion; kept public so it can be tested in isolation. */
    fun threshold(gray: IntArray): IntArray {
        val threshold = ScanImageMath.otsuThreshold(gray)
        return IntArray(gray.size) { index ->
            if (gray[index] <= threshold) INK else PAPER
        }
    }

    // ------------------------------------------------------------------ pipelines

    /**
     * Full pipeline used when saving a page: decode at the requested quality, then crop, rotate and
     * filter in that order. Returns null when the file can no longer be decoded, which is a
     * recoverable condition the caller reports instead of crashing on.
     */
    fun processPage(
        file: File,
        targetLongEdge: Int,
        filter: ScanFilter,
        rotationDegrees: Int,
        corners: QuadCorners = QuadCorners.fullFrame(),
    ): Bitmap? {
        val decoded = decode(file, targetLongEdge) ?: return null
        val crooked = crop(decoded, corners)
        val rotated = rotate(crooked, rotationDegrees)
        return applyFilter(rotated, filter)
    }

    /** Small preview used by the page strip and the review panel. */
    fun preview(file: File, maxLongEdge: Int, filter: ScanFilter, rotationDegrees: Int): Bitmap? {
        val decoded = decode(file, maxLongEdge) ?: return null
        val rotated = rotate(decoded, rotationDegrees)
        return applyFilter(rotated, filter)
    }

    /**
     * JPEG quality that pairs with [filter].
     *
     * A bitonal page has no chroma to preserve, so spending quality bits on it only inflates the
     * file; colour pages keep the configured value.
     */
    fun jpegQualityFor(filter: ScanFilter, configured: Int): Int = when (filter) {
        ScanFilter.BLACK_AND_WHITE -> configured.coerceAtMost(BITONAL_JPEG_QUALITY)
        else -> configured
    }

    /** ARGB value for a fully saturated black pixel in a bitonal page. */
    private const val INK = 0xFF000000.toInt()

    /** ARGB value for a fully saturated white pixel in a bitonal page. */
    private const val PAPER = 0xFFFFFFFF.toInt()

    /** Quality ceiling used for bitonal output. */
    private const val BITONAL_JPEG_QUALITY = 82
}
