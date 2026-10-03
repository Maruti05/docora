package com.vedica.labs.ind.app.docora.core.util

import java.util.Locale
import kotlin.math.abs

/**
 * Human readable file sizes.
 *
 * Uses binary units with a single decimal place, which is what people expect from a file
 * manager, and never returns an empty string so that UI placeholders stay simple.
 */
object FileSizeFormatter {

    private const val KB = 1024.0
    private const val MB = KB * 1024
    private const val GB = MB * 1024
    private const val TB = GB * 1024

    fun format(bytes: Long, locale: Locale = Locale.getDefault()): String {
        if (bytes < 0) return "—"
        return when {
            bytes < 1024 -> String.format(locale, "%d B", bytes)
            bytes < MB -> String.format(locale, "%.1f KB", bytes / KB)
            bytes < GB -> String.format(locale, "%.1f MB", bytes / MB)
            bytes < TB -> String.format(locale, "%.2f GB", bytes / GB)
            else -> String.format(locale, "%.2f TB", bytes / TB)
        }
    }

    /** Rounded form used in aggregates such as the storage analyser. */
    fun formatCompact(bytes: Long, locale: Locale = Locale.getDefault()): String {
        if (bytes < 0) return "—"
        return when {
            bytes < 1024 -> String.format(locale, "%d B", bytes)
            bytes < MB -> String.format(locale, "%d KB", (bytes / KB).toLong())
            bytes < GB -> String.format(locale, "%.0f MB", bytes / MB)
            else -> String.format(locale, "%.1f GB", bytes / GB)
        }
    }

    /** Parses values such as "20 MB" and "1.5 GB" back into bytes. */
    fun parse(text: String): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val match = SIZE_PATTERN.matchEntire(trimmed) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues[2].uppercase(Locale.ROOT)) {
            "B" -> 1.0
            "KB" -> KB
            "MB" -> MB
            "GB" -> GB
            "TB" -> TB
            else -> return null
        }
        return (value * multiplier).toLong()
    }

    /** Percentage of [part] within [total], clamped to 0..1. */
    fun fraction(part: Long, total: Long): Float {
        if (total <= 0L) return 0f
        return (part.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    /** True when [a] and [b] are within [toleranceBytes] of each other. */
    fun approximatelyEqual(a: Long, b: Long, toleranceBytes: Long = 4096L): Boolean =
        abs(a - b) <= toleranceBytes

    private val SIZE_PATTERN = Regex("^([0-9]+(?:\\.[0-9]+)?)\\s*([a-zA-Z]{1,2})$")
}
