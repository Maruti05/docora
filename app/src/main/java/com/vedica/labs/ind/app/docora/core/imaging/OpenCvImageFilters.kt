package com.vedica.labs.ind.app.docora.core.imaging

import android.graphics.Bitmap
import com.vedica.labs.ind.app.docora.core.model.ScanFilter
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Document enhancement filters backed by OpenCV.
 *
 * These replace the flat colour-matrix treatment for the filters that benefit from local
 * processing:
 *
 *  * **Enhance / Magic colour** - CLAHE on the Lab luminance channel evens out shadows and lamp
 *    falloff across a page, then an unsharp mask restores text crispness. Magic colour also lifts
 *    saturation for stamps and IDs.
 *  * **Grayscale** - CLAHE keeps faint pencil text visible where a plain desaturation loses it.
 *  * **Black & white** - an adaptive threshold handles uneven lighting far better than a global
 *    Otsu cut, then a median blur removes speckle from camera noise.
 *
 * Every operation returns a new bitmap and releases its intermediate Mats. If any native step
 * fails, the caller falls back to the pure-Kotlin filters.
 */
object OpenCvImageFilters {

    fun apply(source: Bitmap, filter: ScanFilter): Bitmap? {
        if (!OpenCvRuntime.isReady || filter == ScanFilter.ORIGINAL) return null
        return runCatching {
            val rgba = Mat()
            Utils.bitmapToMat(source, rgba)
            val output = try {
                when (filter) {
                    ScanFilter.ORIGINAL -> rgba
                    ScanFilter.ENHANCE -> enhance(rgba, magic = false)
                    ScanFilter.MAGIC_COLOUR -> enhance(rgba, magic = true)
                    ScanFilter.GRAYSCALE -> grayscale(rgba)
                    ScanFilter.BLACK_AND_WHITE -> blackAndWhite(rgba)
                }
            } catch (throwable: Throwable) {
                rgba.release()
                throw throwable
            }
            try {
                val bitmap = Bitmap.createBitmap(
                    output.cols(),
                    output.rows(),
                    Bitmap.Config.ARGB_8888,
                )
                Utils.matToBitmap(output, bitmap)
                bitmap
            } finally {
                if (output !== rgba) output.release()
                rgba.release()
            }
        }.getOrNull()
    }

    private fun enhance(rgba: Mat, magic: Boolean): Mat {
        val rgb = Mat()
        val lab = Mat()
        val enhanced = Mat()
        val blurred = Mat()
        val sharp = Mat()
        val result = Mat()
        try {
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgb, lab, Imgproc.COLOR_RGB2Lab)

            val channels = ArrayList<Mat>(3)
            Core.split(lab, channels)
            try {
                val clahe = Imgproc.createCLAHE(2.4, Size(8.0, 8.0))
                clahe.apply(channels[0], channels[0])
                Core.merge(channels, lab)
            } finally {
                channels.forEach { it.release() }
            }

            Imgproc.cvtColor(lab, enhanced, Imgproc.COLOR_Lab2RGB)
            Imgproc.GaussianBlur(enhanced, blurred, Size(0.0, 0.0), 3.0)
            Core.addWeighted(enhanced, 1.35, blurred, -0.35, 0.0, sharp)

            val color = if (magic) boostSaturation(sharp) else sharp
            try {
                Imgproc.cvtColor(color, result, Imgproc.COLOR_RGB2RGBA)
            } finally {
                if (color !== sharp) color.release()
            }
            return result
        } finally {
            rgb.release()
            lab.release()
            enhanced.release()
            blurred.release()
            sharp.release()
        }
    }

    private fun boostSaturation(rgb: Mat): Mat {
        val hsv = Mat()
        val boosted = Mat()
        val result = Mat()
        try {
            Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)
            val channels = ArrayList<Mat>(3)
            Core.split(hsv, channels)
            try {
                channels[1].convertTo(channels[1], CvType.CV_8UC1, 1.35, 0.0)
                Core.merge(channels, boosted)
            } finally {
                channels.forEach { it.release() }
            }
            Imgproc.cvtColor(boosted, result, Imgproc.COLOR_HSV2RGB)
            return result
        } finally {
            hsv.release()
            boosted.release()
        }
    }

    private fun grayscale(rgba: Mat): Mat {
        val gray = Mat()
        val blurred = Mat()
        val equalised = Mat()
        val rgbaOut = Mat()
        try {
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            // A small blur keeps the CLAHE from amplifying sensor grain.
            Imgproc.GaussianBlur(gray, blurred, Size(3.0, 3.0), 0.0)
            val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            clahe.apply(blurred, equalised)
            Imgproc.cvtColor(equalised, rgbaOut, Imgproc.COLOR_GRAY2RGBA)
            return rgbaOut
        } finally {
            gray.release()
            blurred.release()
            equalised.release()
        }
    }

    private fun blackAndWhite(rgba: Mat): Mat {
        val gray = Mat()
        val blurred = Mat()
        val thresholded = Mat()
        val cleaned = Mat()
        val rgbaOut = Mat()
        try {
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(3.0, 3.0), 0.0)
            Imgproc.adaptiveThreshold(
                blurred,
                thresholded,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                27,
                10.0,
            )
            Imgproc.medianBlur(thresholded, cleaned, 3)
            Imgproc.cvtColor(cleaned, rgbaOut, Imgproc.COLOR_GRAY2RGBA)
            return rgbaOut
        } finally {
            gray.release()
            blurred.release()
            thresholded.release()
            cleaned.release()
        }
    }
}
