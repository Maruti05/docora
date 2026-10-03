package com.vedica.labs.ind.app.docora.core.search

/**
 * Translates what the user typed into a safe FTS4 MATCH expression and a list of highlight terms.
 *
 * The input comes straight from a text field, so it can contain FTS operators (`OR`, `NEAR`, `^`,
 * `"`, `*`, `-`) that would either change the meaning of the query or make SQLite reject it
 * outright. Everything is therefore tokenised from scratch and rebuilt:
 *
 *  * operators are dropped, never passed through,
 *  * Unicode letters and digits are kept, punctuation becomes a separator (so "invoice-2026.pdf"
 *    searches as `invoice* 2026* pdf*`),
 *  * tokens of two or more characters get a `*` suffix, which is what turns "ins" into a match
 *    for "insurance" using the FTS prefix indexes.
 *
 * The result is pure and deterministic, which is why it is unit tested rather than trusted.
 */
object SearchQuery {

    /** Characters that terminate a token; everything else is treated as part of a word. */
    private val SEPARATORS = Regex("[^\\p{L}\\p{N}]+")

    private const val MIN_PREFIX_LENGTH = 2
    private const val MAX_TOKENS = 8

    /** User-facing words, used for highlighting and for the snippet window. */
    fun terms(input: String): List<String> = tokenise(input).map { it.text }

    /**
     * FTS4 MATCH expression, or null when nothing searchable was typed (the caller then shows an
     * empty state instead of running a query that would match every row).
     */
    fun toMatchExpression(input: String): String? {
        val tokens = tokenise(input)
        if (tokens.isEmpty()) return null
        return tokens.joinToString(separator = " ") { token ->
            if (token.prefix) "${token.text}*" else token.text
        }
    }

    /**
     * Column-scoped expression used to score title matches without a second index table.
     * FTS4 supports `column:term`, and the column names mirror the index table.
     */
    fun toColumnMatch(column: String, input: String): String? =
        toMatchExpression(input)?.let { expression ->
            expression.split(' ').joinToString(separator = " ") { "$column:$it" }
        }

    /** True when the raw input contains a character the FTS grammar treats specially. */
    fun containsFtsSyntax(input: String): Boolean =
        input.any { it in FTS_SPECIALS }

    private data class Token(val text: String, val prefix: Boolean)

    private fun tokenise(input: String): List<Token> =
        input.trim()
            .split(SEPARATORS)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_TOKENS)
            .map { word ->
                val normalised = word.lowercase()
                Token(
                    text = normalised,
                    prefix = normalised.length >= MIN_PREFIX_LENGTH,
                )
            }
            .toList()

    private val FTS_SPECIALS = charArrayOf('"', '*', '^', '-', ':', '(', ')', '~')
}
