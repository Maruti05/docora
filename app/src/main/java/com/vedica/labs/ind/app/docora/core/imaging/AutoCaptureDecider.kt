package com.vedica.labs.ind.app.docora.core.imaging

/**
 * Frame-by-frame decision that answers "is the camera looking at a document that has stopped
 * moving?" without any heavy CV dependency.
 *
 * The heuristic has two independent gates, and both have to hold for [stableFramesRequired]
 * consecutive frames:
 *
 *  * **Texture** - the frame has to contain structure (luminance spread and luminance range above
 *    a floor). A blank wall or a closed laptop lid is flat, and photographing it is never what the
 *    user wants.
 *  * **Stability** - the frame-to-frame luminance difference has to fall below a small tolerance,
 *    which is what "the user has stopped moving the phone" looks like numerically.
 *
 * Both gates only ever *suggest* a capture: the caller decides whether to honour it, which keeps a
 * false positive harmless (the user simply gets a page they can delete).
 *
 * The class is pure Kotlin and holds no Android types, so the tuning is unit tested directly.
 */
class AutoCaptureDecider(
    private val minDeviation: Float = 16f,
    private val minRange: Int = 70,
    private val maxFrameDifference: Float = 4.5f,
    private val stableFramesRequired: Int = 6,
) {

    /** What the decider concluded about the latest frame. */
    data class Verdict(
        val documentVisible: Boolean,
        val steady: Boolean,
        val stableStreak: Int,
        val shouldCapture: Boolean,
    )

    private var previousFrame: IntArray? = null
    private var streak: Int = 0

    fun reset() {
        previousFrame = null
        streak = 0
    }

    /**
     * Feeds one downsampled greyscale frame.
     *
     * [gray] is expected to be small (a few thousand pixels) - it is produced from the camera's
     * Y plane at analysis resolution, never from the full sensor frame.
     */
    fun submit(gray: IntArray): Verdict {
        if (gray.isEmpty()) return Verdict(documentVisible = false, steady = false, stableStreak = 0, shouldCapture = false)

        val mean = ScanImageMath.meanLuminance(gray)
        val deviation = ScanImageMath.standardDeviation(gray, mean)
        val minimum = gray.min()
        val maximum = gray.max()
        val documentVisible = deviation >= minDeviation && (maximum - minimum) >= minRange

        val previous = previousFrame
        previousFrame = gray.copyOf()

        val difference = if (previous == null || previous.size != gray.size) {
            Float.MAX_VALUE
        } else {
            ScanImageMath.meanAbsoluteDifference(gray, previous)
        }
        val steady = difference <= maxFrameDifference

        streak = when {
            !documentVisible -> 0
            steady -> streak + 1
            else -> 0
        }

        return Verdict(
            documentVisible = documentVisible,
            steady = steady,
            stableStreak = streak,
            shouldCapture = documentVisible && steady && streak >= stableFramesRequired,
        )
    }

    /** True while the auto-capture gate is configured for something inside a document. */
    val isConfiguredForDocuments: Boolean get() = minRange > 0
}
