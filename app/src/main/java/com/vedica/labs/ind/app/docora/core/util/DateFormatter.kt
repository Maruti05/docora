package com.vedica.labs.ind.app.docora.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Locale aware date formatting plus the relative labels Docora uses in lists
 * ("Today", "Yesterday", "3 days ago").
 */
object DateFormatter {

    private val dayFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

    private val dayMonthFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

    private val fileStampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm", Locale.US)

    private fun zone(): ZoneId = ZoneId.systemDefault()

    /** e.g. "27 Sep 2026". */
    fun formatDate(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone()).format(dayFormatter)

    /** e.g. "27 Sep 2026, 09:30". */
    fun formatDateTime(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone())
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))

    /** Timestamp used in generated file names: `2026-09-27_0930`. */
    fun formatFileStamp(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone()).format(fileStampFormatter)

    /**
     * Relative label for lists: today/yesterday/"N days ago" for the first week, then a
     * short date.
     */
    fun formatRelative(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone()).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone()).toLocalDate()
        val days = ChronoUnit.DAYS.between(date, today)
        return when {
            days < 0L -> date.format(dayMonthFormatter)
            days == 0L -> "Today"
            days == 1L -> "Yesterday"
            days < 7L -> "$days days ago"
            date.year == today.year -> date.format(dayMonthFormatter)
            else -> date.format(dayFormatter)
        }
    }

    /** Start of the day containing [epochMillis], used by date filters. */
    fun startOfDay(epochMillis: Long): Long {
        val date: LocalDate = Instant.ofEpochMilli(epochMillis).atZone(zone()).toLocalDate()
        return date.atStartOfDay(zone()).toInstant().toEpochMilli()
    }

    /** Milliseconds for a `yyyy-MM-dd` date, or null when the text is not a date. */
    fun parseIsoDate(text: String): Long? = runCatching {
        LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE)
            .atStartOfDay(zone())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}
