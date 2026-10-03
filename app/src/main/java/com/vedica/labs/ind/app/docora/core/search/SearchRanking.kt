package com.vedica.labs.ind.app.docora.core.search

import com.vedica.labs.ind.app.docora.core.util.TextSnippets

/**
 * Ranks and explains search hits.
 *
 * SQLite's FTS4 build on Android has no `bm25()`, and `matchinfo()`-based scoring would need a
 * custom function registered on the connection. Rather than depend on either, Docora scores in
 * Kotlin with explicit weights: the ordering is deterministic, explainable in the UI ("matched in
 * file name"), and unit testable without a database.
 *
 * Weight order reflects how people actually search a document workspace: they remember a file
 * name first, then a tag, then something they wrote in the notes, and only then a phrase buried
 * inside a PDF.
 */
object SearchRanking {

    private const val WEIGHT_TITLE_PHRASE = 1.0
    private const val WEIGHT_TITLE_ALL_TERMS = 0.92
    private const val WEIGHT_TITLE_PARTIAL = 0.78
    private const val WEIGHT_TAG = 0.66
    private const val WEIGHT_NOTES = 0.5
    private const val WEIGHT_BODY = 0.34
    private const val WEIGHT_CATEGORY = 0.26
    private const val RECENCY_BONUS = 0.04
    private const val RECENCY_WINDOW_MILLIS = 30L * 24 * 60 * 60 * 1000

    /** Field names surfaced in the result row so a hit is never unexplained. */
    const val FIELD_TITLE = "title"
    const val FIELD_TAG = "tag"
    const val FIELD_NOTES = "notes"
    const val FIELD_BODY = "body"
    const val FIELD_CATEGORY = "category"

    data class Score(
        val rank: Double,
        val matchedFields: List<String>,
    ) {
        companion object {
            val None = Score(0.0, emptyList())
        }
    }

    /**
     * Scores one candidate.
     *
     * [terms] are the user's words (already normalised by [SearchQuery]); matching is
     * case-insensitive and prefix aware so "ins" still ranks "insurance".
     */
    fun score(
        title: String,
        tags: String,
        notes: String,
        body: String,
        category: String,
        terms: List<String>,
        modifiedAt: Long,
        now: Long = System.currentTimeMillis(),
    ): Score {
        if (terms.isEmpty()) return Score.None

        val normalisedTitle = title.lowercase()
        val matchedFields = mutableListOf<String>()
        var rank = 0.0

        val phrase = terms.joinToString(separator = " ")
        rank = maxOf(
            rank,
            when {
                normalisedTitle.contains(phrase) -> WEIGHT_TITLE_PHRASE
                terms.all { normalisedTitle.contains(it) } -> WEIGHT_TITLE_ALL_TERMS
                terms.any { normalisedTitle.contains(it) } -> WEIGHT_TITLE_PARTIAL
                else -> 0.0
            },
        )
        if (rank > 0.0) matchedFields += FIELD_TITLE

        if (containsAny(tags, terms)) {
            rank = maxOf(rank, WEIGHT_TAG)
            matchedFields += FIELD_TAG
        }
        if (containsAny(notes, terms)) {
            rank = maxOf(rank, WEIGHT_NOTES)
            matchedFields += FIELD_NOTES
        }
        if (containsAny(category, terms)) {
            rank = maxOf(rank, WEIGHT_CATEGORY)
            matchedFields += FIELD_CATEGORY
        }
        if (containsAny(body, terms)) {
            rank = maxOf(rank, WEIGHT_BODY)
            matchedFields += FIELD_BODY
        }

        if (rank <= 0.0) return Score.None

        val age = (now - modifiedAt).coerceAtLeast(0L)
        if (age < RECENCY_WINDOW_MILLIS) {
            val freshness = 1.0 - (age.toDouble() / RECENCY_WINDOW_MILLIS)
            rank += RECENCY_BONUS * freshness
        }

        return Score(rank = rank.coerceIn(0.0, 1.0), matchedFields = matchedFields)
    }

    /**
     * Context line for a result row: the matched text when the hit is inside the body, otherwise
     * the plain snippet so the row always shows something meaningful.
     */
    fun snippet(body: String, notes: String, terms: List<String>, windowChars: Int = 130): String {
        if (body.isNotBlank() && containsAny(body, terms)) {
            return TextSnippets.snippet(body, terms, windowChars)
        }
        if (notes.isNotBlank() && containsAny(notes, terms)) {
            return TextSnippets.snippet(notes, terms, windowChars)
        }
        return TextSnippets.snippet(body.ifBlank { notes }, terms, windowChars)
    }

    /** True when [haystack] contains any of [terms] at a word start. */
    fun containsAny(haystack: String, terms: List<String>): Boolean {
        if (haystack.isEmpty() || terms.isEmpty()) return false
        val lower = haystack.lowercase()
        return terms.any { term ->
            var index = lower.indexOf(term)
            while (index >= 0) {
                val boundary = index == 0 || !lower[index - 1].isLetterOrDigit()
                if (boundary) return true
                index = lower.indexOf(term, index + 1)
            }
            false
        }
    }
}
