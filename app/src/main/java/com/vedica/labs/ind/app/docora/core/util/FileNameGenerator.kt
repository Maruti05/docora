package com.vedica.labs.ind.app.docora.core.util

/**
 * Generates file names that are predictable, filesystem safe and never silently
 * overwrite an existing document (PRD §45).
 *
 * Two inputs are supported:
 *  * a scan/import timestamp, producing `Scan_2026-09-27_0930.pdf`, and
 *  * an optional OCR derived title, producing `Vehicle_Insurance_2026-09-27_0930.pdf`.
 */
object FileNameGenerator {

    private const val MAX_STEM_LENGTH = 60
    private const val MAX_TITLE_WORDS = 8

    /** Characters that break SAF providers, Windows interop or shell usage. */
    private val ILLEGAL = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000')

    fun sanitise(input: String): String {
        var name = input
        ILLEGAL.forEach { name = name.replace(it, '_') }
        name = name.replace(Regex("\\s+"), " ")
        name = name.replace(Regex("_+"), "_")
        name = name.trim().trim('.', ' ', '_')
        if (name.length > MAX_STEM_LENGTH) {
            name = name.substring(0, MAX_STEM_LENGTH).trim()
        }
        return name.ifBlank { "Document" }
    }

    /**
     * Builds a scanned-document name. [recognisedTitle] is the highest confidence line of
     * recognised text when OCR already ran, otherwise null.
     */
    fun forScan(
        timestampMillis: Long,
        extension: String,
        recognisedTitle: String? = null,
        prefix: String = "Scan",
    ): String {
        val stamp = DateFormatter.formatFileStamp(timestampMillis)
        val title = recognisedTitle?.let { titleFromText(it) }
        val stem = if (title.isNullOrBlank()) {
            "${sanitise(prefix)}_$stamp"
        } else {
            "${sanitise(title)}_$stamp"
        }
        return withExtension(stem, extension)
    }

    /** Name for a generated artefact such as a merged or compressed PDF. */
    fun forDerived(sourceName: String, suffix: String, extension: String): String {
        val stem = sourceName.substringBeforeLast('.', sourceName)
        return withExtension("${sanitise(stem)}_${sanitise(suffix)}", extension)
    }

    /**
     * Appends " (1)", " (2)"… until the name is free.
     *
     * [exists] is supplied by the caller because availability is provider specific: a
     * document provider may expose names that the local filesystem cannot see.
     */
    fun uniqueName(desiredName: String, exists: (String) -> Boolean): String {
        if (!exists(desiredName)) return desiredName
        val extension = MimeTypes.extensionOf(desiredName)
        val stem = if (extension.isEmpty()) desiredName else desiredName.dropLast(extension.length + 1)
        var counter = 1
        while (counter < 1000) {
            val candidate = withExtension("$stem ($counter)", extension)
            if (!exists(candidate)) return candidate
            counter++
        }
        return withExtension("${stem}_${System.currentTimeMillis()}", extension)
    }

    /** Extracts a plausible title from recognised text: first meaningful line. */
    fun titleFromText(text: String): String? {
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.length in 4..80 }
            .filter { line ->
                val letters = line.count { it.isLetter() }
                letters >= line.length / 2
            }
            .take(MAX_TITLE_WORDS * 2)
            .toList()
        val candidate = lines.firstOrNull() ?: return null
        return candidate.split(' ').take(MAX_TITLE_WORDS).joinToString(" ")
    }

    private fun withExtension(stem: String, extension: String): String {
        val cleanExtension = extension.trim().trimStart('.')
        return if (cleanExtension.isEmpty()) stem else "$stem.$cleanExtension"
    }
}
