package com.vedica.labs.ind.app.docora.core.imaging

import com.vedica.labs.ind.app.docora.core.common.DocoraLog
import org.opencv.android.OpenCVLoader

/**
 * One-time OpenCV initialisation.
 *
 * The native library is loaded lazily by the process, and every OpenCV-backed operation checks
 * [isReady] first. If a device's ABI or ROM cannot load it, the scanner keeps working through the
 * pure-Kotlin detector and color-matrix filters instead of crashing.
 */
object OpenCvRuntime {

    @Volatile
    private var ready = false

    val isReady: Boolean get() = ready

    fun initialize(): Boolean {
        if (ready) return true
        synchronized(this) {
            if (ready) return true
            ready = runCatching { OpenCVLoader.initLocal() }.getOrDefault(false)
            if (ready) {
                DocoraLog.i("OpenCV", "initialised")
            } else {
                DocoraLog.w("OpenCV", "initialisation_failed")
            }
        }
        return ready
    }
}
