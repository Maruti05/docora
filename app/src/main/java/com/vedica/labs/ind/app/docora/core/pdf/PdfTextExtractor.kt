package com.vedica.labs.ind.app.docora.core.pdf

import android.content.Context
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.vedica.labs.ind.app.docora.core.common.DispatcherProvider
import com.vedica.labs.ind.app.docora.core.storage.SafGateway
import com.vedica.labs.ind.app.docora.core.util.DocumentLimits
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * On-device text extraction and in-document search for PDFs (PRD A�8, A�10).
 *
 * PDFium (through [android.graphics.pdf.PdfRenderer]) is excellent at pixels but exposes no
 * text layer, so searching text is PDFBox's job. Extraction happens page by page in a single
 * open document and the result is memoised for the document on screen; typing in the find
 * sheet therefore does not re-parse the PDF on every keystroke.
 *
 * The cache is deliberately limited to the most recently opened document. A 400,000
 * character ceiling matches the indexing limit elsewhere in the app and keeps a pathological
 * PDF from pinning megabytes of text for the whole session.
 */
@Singleton
class PdfTextExtractor @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val safGateway: SafGateway,
    private val unlockStore: PdfUnlockStore,
    private val dispatchers: DispatcherProvider,
) {

    private val mutex = Mutex()
    private var cachedUri: String? = null
    private var cachedPages: List<String> = emptyList()
    private var cachedChars: Int = 0

    /** Word runs are a separate cache: the editor asks for one page at a time. */
    private val runsMutex = Mutex()
    private var runsUri: String? = null
    private val cachedRuns = object : LinkedHashMap<Int, List<PdfTextRun>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, List<PdfTextRun>>): Boolean =
            size > MAX_CACHED_RUN_PAGES
    }

    suspend fun pageTexts(uri: String): List<String> = withContext(dispatchers.io) {
        mutex.withLock {
            if (cachedUri == uri) return@withLock cachedPages
            extractLocked(uri)
        }
    }

    suspend fun search(uri: String, query: String, limit: Int = 200): List<PdfSearchHit> =
        withContext(dispatchers.io) {
            val needle = query.trim()
            if (needle.length < 2) return@withContext emptyList()
            val pages = mutex.withLock {
                if (cachedUri == uri) cachedPages else extractLocked(uri)
            }
            val lowerNeedle = needle.lowercase()
            val hits = ArrayList<PdfSearchHit>()
            for ((index, page) in pages.withIndex()) {
                if (page.isBlank()) continue
                val lowerPage = page.lowercase()
                var offset = lowerPage.indexOf(lowerNeedle)
                if (offset < 0) continue
                var occurrences = 0
                val firstOffset = offset
                while (offset >= 0 && occurrences < 10_000) {
                    occurrences++
                    offset = lowerPage.indexOf(lowerNeedle, offset + lowerNeedle.length)
                }
                val start = (firstOffset - SNIPPET_RADIUS).coerceAtLeast(0)
                val end = (firstOffset + needle.length + SNIPPET_RADIUS).coerceAtMost(page.length)
                val raw = page.substring(start, end).replace(Regex("\\s+"), " ").trim()
                val prefix = if (start > 0) "\u2026" else ""
                val suffix = if (end < page.length) "\u2026" else ""
                hits.add(PdfSearchHit(index, prefix + raw + suffix, occurrences))
                if (hits.size >= limit) break
            }
            hits
        }

    /**
     * Word runs for one page, used by text selection and annotation.
     *
     * The cache follows the most recently opened document and holds a handful of pages, so
     * flipping between pages in the editor does not re-parse the file. Callers that need a
     * different document simply ask for it and the old runs are dropped.
     */
    suspend fun pageRuns(uri: String, pageIndex: Int): List<PdfTextRun> = withContext(dispatchers.io) {
        runsMutex.withLock {
            if (runsUri != uri) {
                runsUri = uri
                cachedRuns.clear()
            }
            cachedRuns[pageIndex]?.let { return@withLock it }
            val runs = extractRuns(uri, pageIndex)
            cachedRuns[pageIndex] = runs
            runs
        }
    }

    suspend fun clear() {
        mutex.withLock {
            cachedUri = null
            cachedPages = emptyList()
            cachedChars = 0
        }
        runsMutex.withLock {
            runsUri = null
            cachedRuns.clear()
        }
    }

    private fun extractLocked(uri: String): List<String> {
        cachedUri = uri
        cachedPages = emptyList()
        cachedChars = 0
        val source = unlockStore.resolve(uri) ?: uri
        val stream = safGateway.openInputStream(android.net.Uri.parse(source))
            ?: return emptyList()
        val document = stream.use { input ->
            PDDocument.load(input, MemoryUsageSetting.setupMixed(MAX_HEAP_BUFFER_BYTES))
        }
        return try {
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            val pages = ArrayList<String>(document.numberOfPages)
            var usedChars = 0
            for (page in 1..document.numberOfPages) {
                if (usedChars >= DocumentLimits.MAX_INDEXED_TEXT_CHARS) {
                    pages.add("")
                    continue
                }
                val text = runCatching {
                    stripper.startPage = page
                    stripper.endPage = page
                    stripper.getText(document).orEmpty()
                }.getOrDefault("")
                val clipped = text.take(DocumentLimits.MAX_INDEXED_TEXT_CHARS - usedChars)
                usedChars += clipped.length
                pages.add(clipped)
            }
            cachedPages = pages
            cachedChars = usedChars
            pages
        } finally {
            runCatching { document.close() }
        }
    }

    private fun extractRuns(uri: String, pageIndex: Int): List<PdfTextRun> {
        val source = unlockStore.resolve(uri) ?: uri
        val stream = safGateway.openInputStream(android.net.Uri.parse(source)) ?: return emptyList()
        val document = stream.use { input ->
            PDDocument.load(input, MemoryUsageSetting.setupMixed(MAX_HEAP_BUFFER_BYTES))
        }
        return try {
            if (pageIndex !in 0 until document.numberOfPages) return emptyList()
            val page = document.getPage(pageIndex)
            val rotation = ((page.rotation % 360) + 360) % 360
            val box = page.cropBox
            val pageWidth = if (rotation % 180 == 0) box.width else box.height
            val pageHeight = if (rotation % 180 == 0) box.height else box.width
            if (pageWidth <= 0f || pageHeight <= 0f) return emptyList()
            val stripper = PositionStripper(pageWidth, pageHeight).apply {
                startPage = pageIndex + 1
                endPage = pageIndex + 1
                sortByPosition = true
            }
            stripper.getText(document)
            stripper.runs
        } catch (_: Throwable) {
            emptyList()
        } finally {
            runCatching { document.close() }
        }
    }

    /**
     * Collects the text layer as word-sized rectangles.
     *
     * PDFBox exposes page-rotation adjusted coordinates through [TextPosition.getX]/[TextPosition.getY]
     * (0,0 is the top-left of the rendered page), so normalising by the rotated crop box gives the
     * exact same space the rendered bitmap and [PdfMark] use.
     */
    internal class PositionStripper(
        private val pageWidth: Float,
        private val pageHeight: Float,
    ) : PDFTextStripper() {

        val runs = ArrayList<PdfTextRun>()

        private val word = StringBuilder()
        private var left = Float.MAX_VALUE
        private var top = Float.MAX_VALUE
        private var right = -Float.MAX_VALUE
        private var bottom = -Float.MAX_VALUE

        override fun writeString(text: String, textPositions: List<TextPosition>) {
            textPositions.forEach { position ->
                val unicode = position.unicode.orEmpty()
                if (unicode.isEmpty()) return@forEach
                if (unicode.all { it.isWhitespace() }) {
                    flush()
                    return@forEach
                }
                val x = position.x
                val y = position.y
                val width = position.width
                val height = position.height
                if (width <= 0f || height <= 0f) return@forEach
                left = min(left, x)
                top = min(top, y)
                right = max(right, x + width)
                bottom = max(bottom, y + height)
                word.append(unicode)
            }
            flush()
        }

        private fun flush() {
            if (word.isEmpty()) {
                reset()
                return
            }
            val text = word.toString().trim()
            if (text.isNotEmpty()) {
                val normalisedLeft = (left / pageWidth).coerceIn(0f, 1f)
                val normalisedTop = (top / pageHeight).coerceIn(0f, 1f)
                val normalisedRight = (right / pageWidth).coerceIn(0f, 1f)
                val normalisedBottom = (bottom / pageHeight).coerceIn(0f, 1f)
                if (normalisedRight - normalisedLeft > 0.0005f &&
                    normalisedBottom - normalisedTop > 0.0005f
                ) {
                    runs.add(
                        PdfTextRun(
                            text = text,
                            left = normalisedLeft,
                            top = normalisedTop,
                            right = normalisedRight,
                            bottom = normalisedBottom,
                        ),
                    )
                }
            }
            reset()
        }

        private fun reset() {
            word.clear()
            left = Float.MAX_VALUE
            top = Float.MAX_VALUE
            right = -Float.MAX_VALUE
            bottom = -Float.MAX_VALUE
        }
    }

    private companion object {
        const val SNIPPET_RADIUS = 42
        const val MAX_HEAP_BUFFER_BYTES = 24L * 1024 * 1024
        const val MAX_CACHED_RUN_PAGES = 8
    }
}
