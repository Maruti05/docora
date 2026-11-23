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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The editing tools available on a single PDF page. */
enum class PdfEditTool { SELECT, HIGHLIGHT, UNDERLINE, NOTE, INK }

private val DefaultHighlightColor: Int = 0xFFFFEB3B.toInt()

/** A note being composed before it is written into the PDF. */
data class PdfNoteDraft(
    val position: PdfPoint,
    val text: String = "",
)

/** Immutable page-editor state. */
data class PdfEditorUiState(
    val document: Document? = null,
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    val bitmap: Bitmap? = null,
    val textRuns: List<PdfTextRun> = emptyList(),
    val marks: List<PdfMark> = emptyList(),
    val redo: List<PdfMark> = emptyList(),
    val selection: IntRange? = null,
    val selectionAnchor: Int? = null,
    val tool: PdfEditTool = PdfEditTool.SELECT,
    val colorArgb: Int = DefaultHighlightColor,
    val strokeWidth: Float = 2.4f,
    val isLoading: Boolean = true,
    val saving: Boolean = false,
    val noteDraft: PdfNoteDraft? = null,
    val errorRes: Int? = null,
) {
    val currentPageMarks: List<PdfMark>
        get() = marks.filter { it.pageIndex == pageIndex }

    val hasSelection: Boolean get() = selection != null
    val canUndo: Boolean get() = marks.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()
}

/** One-shot editor results. */
sealed interface PdfEditorEvent {
    data class Open(val documentId: String) : PdfEditorEvent
    data class Message(val text: String) : PdfEditorEvent
}

/**
 * Page editor logic (PRD §10, §11).
 *
 * The editor works on a single rendered page so gestures never fight the vertical reader:
 * selecting text, dragging a highlight, drawing ink and placing a note all use the same
 * normalised page coordinate space that [PdfToolkit] writes back into the PDF.
 *
 * Marks live only in this state until the user saves. Saving delegates to
 * [PdfToolkit.applyAnnotations], which writes a new document and leaves the original untouched.
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

    private var renderJob: Job? = null

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
            loadPage(page)
        }
    }

    fun goToPage(delta: Int) {
        val current = _state.value
        if (current.pageCount <= 0) return
        val target = (current.pageIndex + delta).coerceIn(0, current.pageCount - 1)
        if (target != current.pageIndex) loadPage(target)
    }

    private fun loadPage(index: Int) {
        val current = _state.value
        val document = current.document ?: return
        val safeIndex = index.coerceIn(0, (current.pageCount - 1).coerceAtLeast(0))
        renderJob?.cancel()
        _state.value = current.copy(
            pageIndex = safeIndex,
            bitmap = null,
            textRuns = emptyList(),
            selection = null,
            selectionAnchor = null,
            isLoading = true,
        )
        renderJob = viewModelScope.launch {
            val rendered = withContext(dispatchers.io) {
                val page = runCatching {
                    pdfRenderRepository.renderPage(
                        uri = document.uri,
                        pageIndex = safeIndex,
                        targetWidthPx = EDITOR_RENDER_WIDTH_PX,
                        rotationDegrees = 0,
                    )
                }.getOrNull()
                val runs = runCatching {
                    pdfTextExtractor.pageRuns(document.uri, safeIndex)
                }.getOrDefault(emptyList())
                page?.bitmap to runs
            }
            if (_state.value.pageIndex == safeIndex) {
                _state.value = _state.value.copy(
                    bitmap = rendered.first,
                    textRuns = rendered.second,
                    isLoading = false,
                )
            }
        }
    }

    fun setTool(tool: PdfEditTool) {
        val current = _state.value
        _state.value = current.copy(
            tool = tool,
            selection = if (tool == PdfEditTool.SELECT) current.selection else null,
            selectionAnchor = if (tool == PdfEditTool.SELECT) current.selectionAnchor else null,
            noteDraft = if (tool == PdfEditTool.NOTE) current.noteDraft else null,
        )
    }

    fun setColor(colorArgb: Int) {
        _state.value = _state.value.copy(colorArgb = colorArgb)
    }

    fun setStrokeWidth(width: Float) {
        _state.value = _state.value.copy(strokeWidth = width.coerceIn(0.8f, 10f))
    }

    // ------------------------------------------------------------------ selection

    fun selectStart(position: PdfPoint) {
        val index = findRun(position, nearest = false)
        val current = _state.value
        _state.value = if (index == null) {
            current.copy(selection = null, selectionAnchor = null)
        } else {
            current.copy(selection = index..index, selectionAnchor = index)
        }
    }

    fun selectExtend(position: PdfPoint) {
        val current = _state.value
        val anchor = current.selectionAnchor ?: return selectStart(position)
        val index = findRun(position, nearest = true) ?: return
        _state.value = current.copy(
            selection = min(anchor, index)..max(anchor, index),
        )
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

    /** Turns the current text selection into one highlight/underline mark per line. */
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

    fun addRectMark(start: PdfPoint, end: PdfPoint, kind: PdfMarkKind) {
        val left = min(start.x, end.x)
        val top = min(start.y, end.y)
        val right = max(start.x, end.x)
        val bottom = max(start.y, end.y)
        if (right - left < 0.004f || bottom - top < 0.004f) return
        addMark(
            PdfMark(
                kind = kind,
                pageIndex = _state.value.pageIndex,
                colorArgb = _state.value.colorArgb,
                start = PdfPoint(left, top),
                end = PdfPoint(right, bottom),
            ),
        )
    }

    fun addInk(points: List<PdfPoint>) {
        if (points.size < 2) return
        addMark(
            PdfMark(
                kind = PdfMarkKind.INK,
                pageIndex = _state.value.pageIndex,
                colorArgb = _state.value.colorArgb,
                points = points,
                strokeWidth = _state.value.strokeWidth,
            ),
        )
    }

    fun beginNote(position: PdfPoint) {
        _state.value = _state.value.copy(noteDraft = PdfNoteDraft(position))
    }

    fun updateNote(text: String) {
        val draft = _state.value.noteDraft ?: return
        _state.value = _state.value.copy(noteDraft = draft.copy(text = text))
    }

    fun commitNote() {
        val current = _state.value
        val draft = current.noteDraft ?: return
        val text = draft.text.trim()
        if (text.isNotEmpty()) {
            addMark(
                PdfMark(
                    kind = PdfMarkKind.NOTE,
                    pageIndex = current.pageIndex,
                    colorArgb = current.colorArgb,
                    start = draft.position,
                    note = text,
                ),
            )
        }
        _state.value = _state.value.copy(noteDraft = null)
    }

    fun dismissNote() {
        _state.value = _state.value.copy(noteDraft = null)
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
        renderJob?.cancel()
        // Do not close the shared session here: the viewer that opens the saved copy may already
        // be using it. The repository swaps sessions when a different document is requested.
        super.onCleared()
    }

    private companion object {
        const val EDITOR_RENDER_WIDTH_PX = 1600
    }
}
