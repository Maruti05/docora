package com.vedica.labs.ind.app.docora.core.common

import android.util.Log

/**
 * Privacy-safe logging.
 *
 * Docora documents can contain passports, medical records and bank statements, so the
 * logger is intentionally *not* a thin wrapper over [Log]:
 *
 *  * nothing is logged in release builds at all,
 *  * only class/tag + a short event name + a small set of explicitly non-sensitive
 *    primitive values may be logged,
 *  * document names, URIs, OCR text and extracted text must never be passed here.
 *
 * Call sites therefore pass structured tags such as `"import"`/`"ocr"` rather than
 * interpolated strings.
 */
object DocoraLog {

    private const val MAX_TAG = 23

    fun d(tag: String, event: String, vararg keyValues: Pair<String, Any?>) {
        if (!isEnabled()) return
        Log.d(tag.take(MAX_TAG), render(event, keyValues))
    }

    fun i(tag: String, event: String, vararg keyValues: Pair<String, Any?>) {
        if (!isEnabled()) return
        Log.i(tag.take(MAX_TAG), render(event, keyValues))
    }

    fun w(tag: String, event: String, throwable: Throwable? = null, vararg keyValues: Pair<String, Any?>) {
        if (!isEnabled()) return
        Log.w(tag.take(MAX_TAG), render(event, keyValues), throwable)
    }

    fun e(tag: String, event: String, throwable: Throwable? = null, vararg keyValues: Pair<String, Any?>) {
        if (!isEnabled()) return
        Log.e(tag.take(MAX_TAG), render(event, keyValues), throwable)
    }

    private fun isEnabled(): Boolean = try {
        Class.forName("com.vedica.labs.ind.app.docora.BuildConfig")
            .getField("DEBUG")
            .getBoolean(null)
    } catch (_: Throwable) {
        false
    }

    private fun render(event: String, keyValues: Array<out Pair<String, Any?>>): String {
        if (keyValues.isEmpty()) return event
        return buildString(event.length + keyValues.size * 16) {
            append(event)
            keyValues.forEach { (key, value) ->
                append(' ')
                append(key)
                append('=')
                append(sanitise(value))
            }
        }
    }

    /** Only primitives, enum names and booleans survive sanitisation. */
    private fun sanitise(value: Any?): String = when (value) {
        null -> "null"
        is Boolean, is Number -> value.toString()
        is Enum<*> -> value.name
        else -> "<redacted:${value.javaClass.simpleName}>"
    }
}
