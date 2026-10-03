package com.vedica.labs.ind.app.docora.core.util

/** Text helpers shared by search snippets, the text viewer and OCR results. */
object TextSnippets {

    private val WHITESPACE = Regex("\\s+")

    /** Collapses runs of whitespace so list rows keep a stable height. */
    fun collapseWhitespace(text: String): String = text.replace(WHITESPACE, " ").trim()

    /**
     * Builds the context shown under a search result around the first match of [terms],
     * with the surrounding text kept short enough for a two line row.
     */
    fun snippet(
        text: String,
        terms: List<String>,
        windowChars: Int = 120,
    ): String {
        val collapsed = collapseWhitespace(text)
        if (collapsed.isEmpty()) return ""
        if (terms.isEmpty()) return collapsed.take(windowChars)

        val lower = collapsed.lowercase()
        val matchIndex = terms
            .mapNotNull { term -> lower.indexOf(term.lowercase()).takeIf { it >= 0 } }
            .minOrNull()

        if (matchIndex == null || matchIndex < windowChars / 2) {
            return collapsed.take(windowChars)
        }

        val start = (matchIndex - windowChars / 3).coerceAtLeast(0)
        val end = (start + windowChars).coerceAtMost(collapsed.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < collapsed.length) "…" else ""
        return prefix + collapsed.substring(start, end).trim() + suffix
    }

    /** Counts words in extracted or recognised text for the details screen. */
    fun wordCount(text: String): Int = text.split(WHITESPACE).count { it.isNotBlank() }

    /**
     * Normalises text coming out of OCR: fixes the most common artefacts without
     * destroying legitimate content.
     */
    fun normaliseOcrText(text: String): String = text
        .replace("\r\n", "\n")
        .replace(Regex("[ \t]+\n"), "\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    /** Safe substring used when the clipboard should not receive a whole 100 page book. */
    fun truncateForClipboard(text: String, maxChars: Int = 100_000): String =
        if (text.length <= maxChars) text else text.substring(0, maxChars)
}
