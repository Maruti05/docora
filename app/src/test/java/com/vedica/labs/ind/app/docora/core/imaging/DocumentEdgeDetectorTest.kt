package com.vedica.labs.ind.app.docora.core.imaging

import com.google.common.truth.Truth.assertThat
import com.vedica.labs.ind.app.docora.core.model.DocumentDetection
import org.junit.Test

/**
 * Verification for the document detector.
 *
 * Synthetic frames are used on purpose: a page on a background is a bright region on a dark one, so
 * the expected corners are known exactly and the detector can be checked without a camera, a device
 * or a screenshot. Each test states the geometry it draws and then asserts on the outline found.
 */
class DocumentEdgeDetectorTest {

    private val width = 64
    private val height = 48

    /** Bright page on a dark background, with the document bounds given in pixels. */
    private fun frameWith(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        paper: Int = 215,
        background: Int = 35,
    ): IntArray {
        val frame = IntArray(width * height) { background }
        for (y in top until bottom) {
            for (x in left until right) {
                frame[y * width + x] = paper
            }
        }
        return frame
    }

    @Test
    fun `finds a centred page`() {
        val detector = DocumentEdgeDetector()
        val detection = detector.detect(frameWith(left = 16, top = 8, right = 48, bottom = 40), width, height)

        assertThat(detection.hasDocument).isTrue()
        val corners = requireNotNull(detection.corners)
        // Expected normalised bounds: x 0.25..0.75, y 0.17..0.83
        assertThat(corners.topLeftX).isWithin(0.09f).of(16f / width)
        assertThat(corners.topLeftY).isWithin(0.09f).of(8f / height)
        assertThat(corners.bottomRightX).isWithin(0.09f).of(48f / width)
        assertThat(corners.bottomRightY).isWithin(0.09f).of(40f / height)
        // 32x32 of a 64x48 frame.
        assertThat(detection.coverage).isWithin(0.06f).of((32f * 32f) / (width * height))
    }

    @Test
    fun `finds an off-centre page`() {
        val detector = DocumentEdgeDetector()
        val detection = detector.detect(frameWith(left = 26, top = 5, right = 57, bottom = 43), width, height)

        assertThat(detection.hasDocument).isTrue()
        val corners = requireNotNull(detection.corners)
        assertThat(corners.topLeftX).isWithin(0.1f).of(26f / width)
        assertThat(corners.bottomRightX).isWithin(0.1f).of(57f / width)
    }

    @Test
    fun `follows a slanted page edge`() {
        // The left edge drifts right as it goes down, so the outline has to be a trapezoid.
        val frame = IntArray(width * height) { 35 }
        for (y in 6 until 42) {
            val left = 14 + (y * 10) / height
            for (x in left until 50) {
                frame[y * width + x] = 215
            }
        }

        val detector = DocumentEdgeDetector()
        val detection = detector.detect(frame, width, height)

        assertThat(detection.hasDocument).isTrue()
        val corners = requireNotNull(detection.corners)
        assertThat(corners.bottomLeftX).isGreaterThan(corners.topLeftX + 0.04f)
    }

    @Test
    fun `reports nothing for a flat frame`() {
        val detector = DocumentEdgeDetector()
        val detection = detector.detect(IntArray(width * height) { 128 }, width, height)

        assertThat(detection.hasDocument).isFalse()
        assertThat(detection.corners).isNull()
    }

    @Test
    fun `reports nothing for a page smaller than the noise floor`() {
        val detector = DocumentEdgeDetector()
        // A 4x3 speck in the corner is not a document.
        val detection = detector.detect(frameWith(left = 1, top = 1, right = 5, bottom = 4), width, height)

        assertThat(detection.hasDocument).isFalse()
    }

    @Test
    fun `holds the outline briefly then releases it when the page leaves the frame`() {
        val detector = DocumentEdgeDetector(releaseAfterMissedFrames = 4)
        val page = frameWith(left = 16, top = 8, right = 48, bottom = 40)
        val blank = IntArray(width * height) { 128 }

        assertThat(detector.detect(page, width, height).hasDocument).isTrue()

        // The first few blank frames keep the last known outline; beyond the release count it goes.
        assertThat(detector.detect(blank, width, height).corners).isNotNull()
        assertThat(detector.detect(blank, width, height).corners).isNotNull()
        repeat(4) { detector.detect(blank, width, height) }

        val afterRelease = detector.detect(blank, width, height)
        assertThat(afterRelease.hasDocument).isFalse()
        assertThat(afterRelease.corners).isNull()
    }

    @Test
    fun `eases towards a moved page instead of jumping`() {
        val detector = DocumentEdgeDetector(smoothing = 0.5f)
        val first = requireNotNull(
            detector.detect(frameWith(left = 10, top = 8, right = 42, bottom = 40), width, height).corners,
        )
        val second = requireNotNull(
            detector.detect(frameWith(left = 22, top = 8, right = 54, bottom = 40), width, height).corners,
        )

        // The new measurement sits well right of the old one, but the eased result is in between.
        assertThat(second.topLeftX).isGreaterThan(first.topLeftX)
        assertThat(second.topLeftX).isLessThan(22f / width)
    }

    @Test
    fun `ignores a frame that is too small to analyse`() {
        val detection = DocumentEdgeDetector().detect(IntArray(4), 4, 4)
        assertThat(detection).isEqualTo(DocumentDetection.None)
    }
}
