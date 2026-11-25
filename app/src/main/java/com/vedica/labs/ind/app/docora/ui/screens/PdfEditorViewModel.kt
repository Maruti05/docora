package com.vedica.labs.ind.app.docora.ui.screens

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.common.DispatcherProvider
import com.vedica.labs.ind.app.docora.core.common.DocoraError
import com.vedica.labs.ind.app.docora.core.common.DocoraResult
import com.vedica.labs.ind.app.docora.core.model.Document
import com.vedica.labs.ind.app.docora.core.model.DocumentType
import com.vedica.labs.ind.app.docora.core.pdf.PdfMark
import com.vedica.labs.ind.app.docora.core.pdf.PdfMarkKind
import com.vedica.labs.ind.app.docora.core.pdf.PdfPoint
import com.vedica.labs.ind.app.docora.core.pdf.PdfRenderRepository
import com.vedica.labs.ind.app.docora.core.pdf.PdfTextExtractor
import com.vedica.labs.ind.app.docora.core.pdf.PdfTextRun
import com.vedica.labs.ind.app.docora.core.pdf.PdfToolkit
import com.vedica.labs.ind.app.docora.core.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** The tools available in the PDF editor. Order here is the order of the scrollable dock. */
enum class PdfEditTool {
    SELECT,
    HIGHLIGHT,
    UNDERLINE,
    STRIKEOUT,
    SQUIGGLY,
    NOTE,
    TEXT,
    INK,
    SIGNATURE,
    RECTANGLE,
    ELLIPSE,
    LINE,
    ARROW,
    REDACT,
    ERASER,
}

/** Highlight yellow, shared by every text-markup tool. */
private val DefaultHighlightColor: Int = 0xFFFFEB3B.toInt()

/** Charcoal used by pens, shapes and text boxes when the current colour is a highlighter tone. */
private val DefaultInkColor: Int = 0xFF212121.toInt()

/** Opaque blackout colour. */
private val DefaultRedactColor: Int = 0xFF111111.toInt()

/** Text being typed for a sticky note or a free text box. */
data class PdfTextDraft(
    val position: PdfPoint,
    val pageIndex: Int,
    val kind: PdfMarkKind,
    val text: String = "",
)

/** Immutable editor state. */
data class PdfEditorUiState(
    val document: Document? = null,
    /** The page the user is looking at; tools and page actions apply to it. */
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    /** Rendered pages, keyed by index. Filled lazily as pages scroll into view. */
    val pageBitmaps: Map<Int, Bitmap> = emptyMap(),
    /** Height / width per rendered page, so placeholders and loaded pages share one aspect. */
    val pageAspects: Map<Int, Float> = emptyMap(),
    val textRuns: List<PdfTextRun> = emptyList(),
    val marks: List<PdfMark> = emptyList(),
    val redo: List<PdfMark> = emptyList(),
    val selection: IntRange? = null,
    val selectionAnchor: Int? = null,
    val tool: PdfEditTool = PdfEditTool.SELECT,
    val colorArgb: Int = DefaultHighlightColor,
    val strokeWidth: Float = 2.4f,
    val fontSize: Float = 12f,
    val isLoading: Boolean = true,
    val saving: Boolean = false,
    val textDraft: PdfTextDraft? = null,
    val errorRes: Int? = null,
) {
    val currentPageMarks: List<PdfMark>
        get() = marks.filter { it.pageIndex == pageIndex }

    val hasSelection: Boolean get() = selection != null
    val canUndo: Boolean get() = marks.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    /**
     * Page aspect as height / width.
     *
     * Before the first page has rendered there is no measurement, so A4 portrait is used. Once any
     * page has loaded, its aspect is borrowed for the placeholders too, which keeps the scroll
     * position from jumping for the common case of a uniformly sized document.
     */
    fun pageAspect(index: Int): Float =
        pageAspects[index] ?: pageAspects.values.firstOrNull() ?: DEFAULT_PAGE_ASPECT

    companion object {
        const val DEFAULT_PAGE_ASPECT = 1.414f
    }
}

/** One-shot editor results. */
sealed interface PdfEditorEvent {
    data class Open(val documentId: String) : PdfEditorEvent
    data class Message(val text: String) : PdfEditorEvent
}

/**
 * Multi-page editor logic (PRD §10, §11).
 *
 * Every page is rendered lazily and shown in one continuous, scrollable column, so the user can
 * read through the document and mark up any page without stepping through it with arrows. Marks
 * carry their own page index and are written in one pass by [PdfToolkit.applyAnnotations]; nothing
 * touches the original file.
 */
@HiltViewModel
class PdfEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val documentRepository: DocumentRepository,
    private val pdfRenderRepository: PdfRenderRepository,
    private val pdfTextExtractor: PdfTextExtractor,
    private val pdfToolkit: PdfToolkit,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val documentId: String? = savedStateHandle[Screen.ARG_DOCUMENT_ID]
    private val initialPage: Int = savedStateHandle.get<Int>(Screen.ARG_PAGE) ?: 0

    private val _state = MutableStateFlow(PdfEditorUiState())
    val state: StateFlow<PdfEditorUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PdfEditorEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PdfEditorEvent> = _events.asSharedFlow()

    private val renderJobs = HashMap<Int, Job>()
    private var textRunsJob: Job? = null

    /** A tap that arrived before the page's text layer had finished loading. */
    private var pendingSelection: Pair<Int, PdfPoint>? = null

    init {
        load()
    }

    private fun load() {
        val id = documentId
        if (id == null) {
            _state.value = PdfEditorUiState(isLoading = false, errorRes = R.string.error_document_missing)
            return
        }
        viewModelScope.launch {
            val document = documentRepository.getDocument(id)
            if (document == null || document.type != DocumentType.PDF) {
                _state.value = PdfEditorUiState(
                    isLoading = false,
                    errorRes = R.string.error_document_missing,
                )
                return@launch
            }
            val info = runCatching { pdfRenderRepository.open(document.uri) }.getOrNull()
            if (info == null) {
                _state.value = PdfEditorUiState(
                    document = document,
                    isLoading = false,
                    errorRes = R.string.viewer_render_failed,
                )
                return@launch
            }
            val page = initialPage.coerceIn(0, (info.pageCount - 1).coerceAtLeast(0))
            _state.value = PdfEditorUiState(
                document = document,
                pageIndex = page,
                pageCount = info.pageCount,
                isLoading = false,
            )
            requestPage(page)
            prefetchAround(page)
            loadTextRuns(page)
        }
    }

    // ------------------------------------------------------------------ pages

    /**
     * Tracks the page currently in front of the user.
     *
     * Scrolling calls this constantly, so it only reacts when the index actually changes: the
     * selection is cleared, the page's text layer is fetched and neighbouring pages are warmed.
     */
    fun setActivePage(index: Int) {
        val current = _state.value
        if (index !in 0 until current.pageCount) return
        if (index == current.pageIndex) {
            requestPage(index)
            return
        }
        if (pendingSelection?.first != index) pendingSelection = null
        _state.value = current.copy(
            pageIndex = index,
            selection = null,
            selectionAnchor = null,
            textRuns = emptyList(),
        )
        requestPage(index)
        prefetchAround(index)
        loadTextRuns(index)
    }

    private fun requestPage(index: Int) {
        val current = _state.value
        if (index !in 0 until current.pageCount) return
        if (current.pageBitmaps.containsKey(index)) return
        if (renderJobs[index]?.isActive == true) return
        val document = current.document ?: return
        renderJobs[index] = viewModelScope.launch {
            val rendered = withContext(dispatchers.io) {
                runCatching {
                    pdfRenderRepository.renderPage(
                        uri = document.uri,
                        pageIndex = index,
                        targetWidthPx = EDITOR_RENDER_WIDTH_PX,
                        rotationDegrees = 0,
                    )
                }.getOrNull()
            }
            if (rendered != null) {
                _state.update { state ->
                    if (state.pageCount == 0) return@update state
                    // Keep only the pages nearest the visible one. The render repository owns its
                    // own byte-bounded cache, so dropping a reference here is safe and cheap.
                    val bitmaps = (state.pageBitmaps + (index to rendered.bitmap)).let { all ->
                        if (all.size <= MAX_RENDERED_PAGES) {
                            all
                        } else {
                            val keep = all.keys
                                .sortedBy { abs(it - state.pageIndex) }
                                .take(MAX_RENDERED_PAGES)
                                .toSet()
                            all.filterKeys { it in keep }
                        }
                    }
                    state.copy(
                        pageBitmaps = bitmaps,
                        pageAspects = state.pageAspects + (index to rendered.aspect),
                    )
                }
            }
            renderJobs.remove(index)
        }
    }

    private fun prefetchAround(index: Int) {
        requestPage(index - 1)
        requestPage(index + 1)
    }

    /** Asks for one page to be rendered; safe to call from every page card's composition. */
    fun ensurePage(index: Int) {
        requestPage(index)
    }

    private fun loadTextRuns(index: Int) {
        val document = _state.value.document ?: return
        textRunsJob?.cancel()
        textRunsJob = viewModelScope.launch {
            val runs = withContext(dispatchers.io) {
                runCatching { pdfTextExtractor.pageRuns(document.uri, index) }
                    .getOrDefault(emptyList())
            }
            if (_state.value.pageIndex != index) return@launch
            _state.value = _state.value.copy(textRuns = runs)
            pendingSelection?.takeIf { it.first == index }?.let { (_, point) ->
                pendingSelection = null
                applySelectStart(point)
            }
        }
    }

    // ------------------------------------------------------------------ tools

    fun setTool(tool: PdfEditTool) {
        val current = _state.value
        val color = when (tool) {
            PdfEditTool.HIGHLIGHT,
            PdfEditTool.UNDERLINE,
            PdfEditTool.STRIKEOUT,
            PdfEditTool.SQUIGGLY,
            -> if (current.colorArgb == DefaultInkColor) DefaultHighlightColor else current.colorArgb
            PdfEditTool.INK,
            PdfEditTool.SIGNATURE,
            PdfEditTool.TEXT,
            PdfEditTool.RECTANGLE,
            PdfEditTool.ELLIPSE,
            PdfEditTool.LINE,
            PdfEditTool.ARROW,
            -> if (current.colorArgb == DefaultHighlightColor) DefaultInkColor else current.colorArgb
            PdfEditTool.REDACT -> DefaultRedactColor
            else -> current.colorArgb
        }
        val stroke = when (tool) {
            PdfEditTool.SIGNATURE -> 3.4f
            PdfEditTool.INK -> if (current.tool == PdfEditTool.SIGNATURE) 2.4f else current.strokeWidth
            else -> current.strokeWidth
        }
        _state.value = current.copy(
            tool = tool,
            colorArgb = color,
            strokeWidth = stroke,
            selection = if (tool == PdfEditTool.SELECT) current.selection else null,
            selectionAnchor = if (tool == PdfEditTool.SELECT) current.selectionAnchor else null,
            textDraft = null,
        )
    }

    fun setColor(colorArgb: Int) {
        _state.value = _state.value.copy(colorArgb = colorArgb)
    }

    fun setStrokeWidth(width: Float) {
        _state.value = _state.value.copy(strokeWidth = width.coerceIn(0.8f, 14f))
    }

    fun setFontSize(size: Float) {
        _state.value = _state.value.copy(fontSize = size.coerceIn(8f, 28f))
    }

    // ------------------------------------------------------------------ selection

    fun selectStart(pageIndex: Int, position: PdfPoint) {
        if (pageIndex != _state.value.pageIndex) {
            // The text layer belongs to the active page only. Remember the tap and replay it once
            // this page's words have arrived, so the first tap on a page is not swallowed.
            pendingSelection = pageIndex to position
            setActivePage(pageIndex)
            return
        }
        applySelectStart(position)
    }

    private fun applySelectStart(position: PdfPoint) {
        val index = findRun(position, nearest = false)
        val current = _state.value
        _state.value = if (index == null) {
            current.copy(selection = null, selectionAnchor = null)
        } else {
            current.copy(selection = index..index, selectionAnchor = index)
        }
    }

    fun selectExtend(pageIndex: Int, position: PdfPoint) {
        if (pageIndex != _state.value.pageIndex) return
        val current = _state.value
        val anchor = current.selectionAnchor ?: return applySelectStart(position)
        val index = findRun(position, nearest = true) ?: return
        _state.value = current.copy(selection = min(anchor, index)..max(anchor, index))
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selection = null, selectionAnchor = null)
    }

    fun selectedText(): String {
        val current = _state.value
        val range = current.selection ?: return ""
        val runs = current.textRuns.subList(range.first, range.last + 1)
        val builder = StringBuilder()
        runs.forEachIndexed { index, run ->
            if (index > 0) {
                val previous = runs[index - 1]
                val sameLine = abs(run.centerY - previous.centerY) <
                    max(run.height, previous.height) * 0.6f
                builder.append(if (sameLine) ' ' else '\n')
            }
            builder.append(run.text)
        }
        return builder.toString().trim()
    }

    /** Turns the current text selection into one markup mark per line. */
    fun markSelection(kind: PdfMarkKind) {
        val current = _state.value
        val range = current.selection ?: return
        val runs = current.textRuns.subList(range.first, range.last + 1)
        if (runs.isEmpty()) return
        val lines = ArrayList<MutableList<PdfTextRun>>()
        runs.forEach { run ->
            val last = lines.lastOrNull()
            val sameLine = last != null &&
                abs(run.centerY - last.last().centerY) <
                max(run.height, last.last().height) * 0.6f
            if (sameLine) last.add(run) else lines.add(mutableListOf(run))
        }
        lines.forEach { line ->
            val left = line.minOf { it.left }
            val top = line.minOf { it.top }
            val right = line.maxOf { it.right }
            val bottom = line.maxOf { it.bottom }
            addMark(
                PdfMark(
                    kind = kind,
                    pageIndex = current.pageIndex,
                    colorArgb = current.colorArgb,
                    start = PdfPoint(left, top),
                    end = PdfPoint(right, bottom),
                ),
            )
        }
    }

    private fun findRun(position: PdfPoint, nearest: Boolean): Int? {
        val runs = _state.value.textRuns
        if (runs.isEmpty()) return null
        val direct = runs.indices
            .filter { runs[it].contains(position.x, position.y) }
            .minByOrNull { distanceToCenter(runs[it], position) }
        if (direct != null || !nearest) return direct
        return runs.indices
            .filter { distanceToCenter(runs[it], position) < 0.06f }
            .minByOrNull { distanceToCenter(runs[it], position) }
    }

    private fun distanceToCenter(run: PdfTextRun, point: PdfPoint): Float {
        val dx = (run.left + run.right) / 2f - point.x
        val dy = run.centerY - point.y
        return dx * dx + dy * dy
    }

    // ------------------------------------------------------------------ marks

    fun addRectMark(pageIndex: Int, start: PdfPoint, end: PdfPoint, kind: PdfMarkKind) {
        val left = min(start.x, end.x)
        val top = min(start.y, end.y)
        val right = max(start.x, end.x)
        val bottom = max(start.y, end.y)
        if (right - left < 0.004f || bottom - top < 0.004f) return
        addMark(
            PdfMark(
                kind = kind,
                pageIndex = pageIndex,
                colorArgb = _state.value.colorArgb,
                start = PdfPoint(left, top),
                end = PdfPoint(right, bottom),
            ),
        )
    }

    fun addInk(pageIndex: Int, points: List<PdfPoint>) {
        if (points.size < 2) return
        addMark(
            PdfMark(
                kind = PdfMarkKind.INK,
                pageIndex = pageIndex,
                colorArgb = _state.value.colorArgb,
                points = points,
                strokeWidth = _state.value.strokeWidth,
            ),
        )
    }

    fun beginDraft(pageIndex: Int, position: PdfPoint, kind: PdfMarkKind) {
        _state.value = _state.value.copy(
            textDraft = PdfTextDraft(position = position, pageIndex = pageIndex, kind = kind),
        )
    }

    fun updateDraft(text: String) {
        val draft = _state.value.textDraft ?: return
        _state.value = _state.value.copy(textDraft = draft.copy(text = text))
    }

    fun commitDraft() {
        val current = _state.value
        val draft = current.textDraft ?: return
        val text = draft.text.trim()
        if (text.isNotEmpty()) {
            addMark(
                PdfMark(
                    kind = draft.kind,
                    pageIndex = draft.pageIndex,
                    colorArgb = current.colorArgb,
                    start = draft.position,
                    note = text,
                    fontSize = current.fontSize,
                ),
            )
        }
        _state.value = _state.value.copy(textDraft = null)
    }

    fun dismissDraft() {
        _state.value = _state.value.copy(textDraft = null)
    }

    /**
     * Removes the topmost mark under [point] on [pageIndex].
     *
     * The eraser works on whole marks rather than pixels: marks are vectors, so deleting one is
     * undoable and leaves no half-erased residue.
     */
    fun eraseAt(pageIndex: Int, point: PdfPoint) {
        val current = _state.value
        val victim = current.marks.lastOrNull { it.pageIndex == pageIndex && it.hitTest(point) }
            ?: return
        _state.value = current.copy(
            marks = current.marks.filterNot { it === victim },
            redo = listOf(victim) + current.redo,
        )
    }

    private fun addMark(mark: PdfMark) {
        _state.value = _state.value.copy(
            marks = _state.value.marks + mark,
            redo = emptyList(),
        )
    }

    fun undo() {
        val current = _state.value
        val last = current.marks.lastOrNull() ?: return
        _state.value = current.copy(
            marks = current.marks.dropLast(1),
            redo = listOf(last) + current.redo,
        )
    }

    fun redo() {
        val current = _state.value
        val next = current.redo.firstOrNull() ?: return
        _state.value = current.copy(
            marks = current.marks + next,
            redo = current.redo.drop(1),
        )
    }

    fun clearPage() {
        val current = _state.value
        val pageMarks = current.marks.filter { it.pageIndex == current.pageIndex }
        if (pageMarks.isEmpty()) return
        _state.value = current.copy(
            marks = current.marks.filterNot { it.pageIndex == current.pageIndex },
            redo = pageMarks.reversed() + current.redo,
        )
    }

    // ------------------------------------------------------------------ save

    fun save() {
        val current = _state.value
        if (current.saving) return
        val document = current.document ?: return
        if (current.marks.isEmpty()) {
            _events.tryEmit(PdfEditorEvent.Message(context.getString(R.string.pdf_editor_no_marks)))
            return
        }
        _state.value = current.copy(saving = true)
        viewModelScope.launch {
            val result = runCatching { pdfToolkit.applyAnnotations(document, current.marks) }
                .getOrNull()
            _state.value = _state.value.copy(saving = false)
            when (result) {
                is DocoraResult.Success -> _events.tryEmit(PdfEditorEvent.Open(result.value.id))
                is DocoraResult.Failure -> _events.tryEmit(
                    PdfEditorEvent.Message(context.getString(errorMessage(result.error))),
                )
                null -> _events.tryEmit(
                    PdfEditorEvent.Message(context.getString(R.string.error_generic_title)),
                )
            }
        }
    }

    private fun errorMessage(error: DocoraError): Int = when (error) {
        is DocoraError.CorruptedDocument -> R.string.error_pdf_corrupted
        is DocoraError.DocumentMissing,
        is DocoraError.PermissionRevoked -> R.string.error_document_missing
        is DocoraError.InsufficientStorage -> R.string.error_not_enough_storage
        is DocoraError.StorageUnavailable -> R.string.error_storage_unavailable
        else -> R.string.error_generic_title
    }

    override fun onCleared() {
        textRunsJob?.cancel()
        renderJobs.values.forEach { it.cancel() }
        // Do not close the shared render session here: the viewer that opens the saved copy may
        // already be using it. The repository swaps sessions when a different document is requested.
        super.onCleared()
    }

    private companion object {
        const val EDITOR_RENDER_WIDTH_PX = 1200

        /**
         * Rendered pages kept in state. Each is a few megabytes, and the render repository already
         * keeps its own byte-bounded cache, so six is enough to scroll smoothly without pressure.
         */
        const val MAX_RENDERED_PAGES = 6
    }
}

/** True when a mark is close enough to [point] for the eraser to pick it up. */
private fun PdfMark.hitTest(point: PdfPoint): Boolean {
    val tolerance = 0.012f
    return when (kind) {
        PdfMarkKind.NOTE -> start?.let { distance(it, point) < 0.035f } ?: false
        PdfMarkKind.TEXT -> {
            val origin = start ?: return false
            point.x in (origin.x - 0.01f)..(origin.x + 0.45f) &&
                point.y in (origin.y - 0.08f)..(origin.y + 0.03f)
        }
        PdfMarkKind.INK -> points.any { distance(it, point) < 0.025f }
        PdfMarkKind.LINE, PdfMarkKind.ARROW -> {
            val from = start ?: return false
            val to = end ?: return false
            segmentDistance(point, from, to) < 0.025f
        }
        PdfMarkKind.ELLIPSE -> {
            val from = start ?: return false
            val to = end ?: return false
            val left = min(from.x, to.x)
            val right = max(from.x, to.x)
            val top = min(from.y, to.y)
            val bottom = max(from.y, to.y)
            val cx = (left + right) / 2f
            val cy = (top + bottom) / 2f
            val rx = (right - left) / 2f + tolerance
            val ry = (bottom - top) / 2f + tolerance
            if (rx <= 0f || ry <= 0f) return false
            val dx = (point.x - cx) / rx
            val dy = (point.y - cy) / ry
            dx * dx + dy * dy <= 1.25f
        }
        else -> {
            val from = start ?: return false
            val to = end ?: return false
            point.x in (min(from.x, to.x) - tolerance)..(max(from.x, to.x) + tolerance) &&
                point.y in (min(from.y, to.y) - tolerance)..(max(from.y, to.y) + tolerance)
        }
    }
}

private fun distance(a: PdfPoint, b: PdfPoint): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}

private fun segmentDistance(point: PdfPoint, from: PdfPoint, to: PdfPoint): Float {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared <= 0.0000001f) return distance(from, point)
    val t = (((point.x - from.x) * dx + (point.y - from.y) * dy) / lengthSquared)
        .coerceIn(0f, 1f)
    return distance(point, PdfPoint(from.x + t * dx, from.y + t * dy))
}
