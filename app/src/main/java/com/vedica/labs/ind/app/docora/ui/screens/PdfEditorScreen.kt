package com.vedica.labs.ind.app.docora.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.pdf.PdfMark
import com.vedica.labs.ind.app.docora.core.pdf.PdfMarkKind
import com.vedica.labs.ind.app.docora.core.pdf.PdfPoint
import com.vedica.labs.ind.app.docora.ui.designsystem.SystemBarAppearance
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Single-page PDF editor (PRD §10, §11).
 *
 * The page is shown exactly as it will be annotated: the bitmap, the text layer and every mark use
 * the same normalised coordinates, so what the user selects is what [PdfToolkit] writes back.
 * Editing is intentionally page-scoped (with arrows for page changes) instead of happening inside
 * the vertical reader, where a drag could mean "select text", "draw" or "scroll".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfEditorScreen(
    controller: NavHostController,
    vm: PdfEditorViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    // The editor chrome follows the app theme, so it declares its own system-bar icon colour
    // instead of inheriting the reader's dark-stage override.
    SystemBarAppearance(useDarkIcons = MaterialTheme.colorScheme.surface.luminance() > 0.5f)
    val snacks = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is PdfEditorEvent.Open -> {
                    // Replace the original reader as well: the annotated copy is the new document
                    // the user asked for, and backing out should return to Details, not the old file.
                    controller.popBackStack(Screen.Viewer.route, inclusive = true)
                    controller.navigate(Screen.viewerRoute(event.documentId))
                }
                is PdfEditorEvent.Message -> snacks.showSnackbar(event.text)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.document?.title.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(
                                R.string.pdf_editor_page,
                                state.pageIndex + 1,
                                state.pageCount.coerceAtLeast(1),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { controller.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = vm::undo, enabled = state.canUndo) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = stringResource(R.string.action_undo),
                        )
                    }
                    IconButton(onClick = vm::redo, enabled = state.canRedo) {
                        Icon(
                            Icons.AutoMirrored.Filled.Redo,
                            contentDescription = stringResource(R.string.action_redo),
                        )
                    }
                    if (state.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp).padding(end = 10.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        FilledTonalIconButton(
                            onClick = vm::save,
                            enabled = state.canUndo,
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            Icon(
                                Icons.Filled.Save,
                                contentDescription = stringResource(R.string.action_save),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snacks) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.errorRes != null -> EditorError(
                    messageRes = state.errorRes ?: R.string.error_generic_title,
                    onBack = { controller.popBackStack() },
                )
                else -> EditorBody(
                    state = state,
                    vm = vm,
                    onCopy = {
                        val text = vm.selectedText()
                        if (text.isNotBlank()) {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(
                                ClipData.newPlainText(
                                    context.getString(R.string.pdf_editor_clip_label),
                                    text,
                                ),
                            )
                            scope.launch {
                                snacks.showSnackbar(
                                    context.getString(R.string.pdf_editor_text_copied),
                                )
                            }
                        }
                    },
                )
            }
            if (state.saving) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }

    state.noteDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = vm::dismissNote,
            title = { Text(stringResource(R.string.pdf_note_title)) },
            text = {
                OutlinedTextField(
                    value = draft.text,
                    onValueChange = vm::updateNote,
                    label = { Text(stringResource(R.string.pdf_note_hint)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = vm::commitNote,
                    enabled = draft.text.isNotBlank(),
                ) { Text(stringResource(R.string.pdf_note_add)) }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissNote) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun EditorError(messageRes: Int, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                stringResource(messageRes),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
        }
    }
}

@Composable
private fun EditorPage(
    state: PdfEditorUiState,
    vm: PdfEditorViewModel,
) {
    val bitmap = state.bitmap
    if (bitmap == null) {
        CircularProgressIndicator()
        return
    }
    val aspect = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val pageWidth = minOf(maxWidth, maxHeight * aspect)
        val pageHeight = pageWidth / aspect
        var previewStart by remember(state.pageIndex, state.tool) { mutableStateOf<PdfPoint?>(null) }
        var previewEnd by remember(state.pageIndex, state.tool) { mutableStateOf<PdfPoint?>(null) }
        var inkPoints by remember(state.pageIndex, state.tool) { mutableStateOf<List<PdfPoint>>(emptyList()) }

        Box(
            modifier = Modifier
                .size(pageWidth, pageHeight)
                .shadow(elevation = 10.dp, shape = RoundedCornerShape(3.dp))
                .background(Color.White),
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.a11y_page_thumbnail, state.pageIndex + 1),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
            MarkLayer(state = state, previewStart = previewStart, previewEnd = previewEnd, inkPoints = inkPoints)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(state.tool, state.pageIndex) {
                        fun normalised(offset: Offset): PdfPoint = PdfPoint(
                            x = (offset.x / size.width.toFloat()).coerceIn(0f, 1f),
                            y = (offset.y / size.height.toFloat()).coerceIn(0f, 1f),
                        )
                        when (state.tool) {
                            PdfEditTool.SELECT -> awaitEachGesture {
                                val down = awaitFirstDown()
                                vm.selectStart(normalised(down.position))
                                down.consume()
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.pressed } ?: break
                                    vm.selectExtend(normalised(change.position))
                                    change.consume()
                                } while (event.changes.any { it.pressed })
                            }
                            PdfEditTool.HIGHLIGHT, PdfEditTool.UNDERLINE -> {
                                val kind = if (state.tool == PdfEditTool.HIGHLIGHT) {
                                    PdfMarkKind.HIGHLIGHT
                                } else {
                                    PdfMarkKind.UNDERLINE
                                }
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val point = normalised(offset)
                                        previewStart = point
                                        previewEnd = point
                                    },
                                    onDrag = { change, _ ->
                                        previewEnd = normalised(change.position)
                                        change.consume()
                                    },
                                    onDragEnd = {
                                        val start = previewStart
                                        val end = previewEnd
                                        if (start != null && end != null) {
                                            vm.addRectMark(start, end, kind)
                                        }
                                        previewStart = null
                                        previewEnd = null
                                    },
                                    onDragCancel = {
                                        previewStart = null
                                        previewEnd = null
                                    },
                                )
                            }
                            PdfEditTool.INK -> detectDragGestures(
                                onDragStart = { offset -> inkPoints = listOf(normalised(offset)) },
                                onDrag = { change, _ ->
                                    inkPoints = inkPoints + normalised(change.position)
                                    change.consume()
                                },
                                onDragEnd = {
                                    vm.addInk(inkPoints)
                                    inkPoints = emptyList()
                                },
                                onDragCancel = { inkPoints = emptyList() },
                            )
                            PdfEditTool.NOTE -> detectTapGestures { offset ->
                                vm.beginNote(normalised(offset))
                            }
                        }
                    },
            )
        }
    }
}

@Composable
private fun MarkLayer(
    state: PdfEditorUiState,
    previewStart: PdfPoint?,
    previewEnd: PdfPoint?,
    inkPoints: List<PdfPoint>,
) {
    val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        state.currentPageMarks.forEach { mark ->
            val color = Color(mark.colorArgb)
            when (mark.kind) {
                PdfMarkKind.HIGHLIGHT -> {
                    val start = mark.start
                    val end = mark.end
                    if (start != null && end != null) {
                        drawRect(
                            color = color.copy(alpha = 0.34f),
                            topLeft = Offset(min(start.x, end.x) * w, min(start.y, end.y) * h),
                            size = Size(abs(end.x - start.x) * w, abs(end.y - start.y) * h),
                        )
                    }
                }
                PdfMarkKind.UNDERLINE -> {
                    val start = mark.start
                    val end = mark.end
                    if (start != null && end != null) {
                        val y = max(start.y, end.y) * h
                        drawLine(
                            color = color,
                            start = Offset(min(start.x, end.x) * w, y),
                            end = Offset(max(start.x, end.x) * w, y),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
                PdfMarkKind.NOTE -> {
                    val start = mark.start
                    if (start != null) {
                        drawCircle(
                            color = color,
                            radius = 9.dp.toPx(),
                            center = Offset(start.x * w, start.y * h),
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 3.5.dp.toPx(),
                            center = Offset(start.x * w, start.y * h),
                        )
                    }
                }
                PdfMarkKind.INK -> drawPath(
                    path = mark.path(w, h),
                    color = color,
                    style = Stroke(
                        width = mark.strokeWidth.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }

        state.selection?.let { range ->
            for (index in range) {
                val run = state.textRuns.getOrNull(index) ?: continue
                drawRect(
                    color = selectionColor,
                    topLeft = Offset(run.left * w, run.top * h),
                    size = Size(run.width * w, run.height * h),
                )
            }
        }

        val start = previewStart
        val end = previewEnd
        if (start != null && end != null) {
            val color = Color(state.colorArgb)
            if (state.tool == PdfEditTool.HIGHLIGHT) {
                drawRect(
                    color = color.copy(alpha = 0.34f),
                    topLeft = Offset(min(start.x, end.x) * w, min(start.y, end.y) * h),
                    size = Size(abs(end.x - start.x) * w, abs(end.y - start.y) * h),
                )
            } else {
                val y = max(start.y, end.y) * h
                drawLine(
                    color = color,
                    start = Offset(min(start.x, end.x) * w, y),
                    end = Offset(max(start.x, end.x) * w, y),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        if (inkPoints.size > 1) {
            drawPath(
                path = inkPoints.toPath(w, h),
                color = Color(state.colorArgb),
                style = Stroke(
                    width = state.strokeWidth.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}

/** Tools in dock order; the options notch is positioned with the same order. */
private val EditorToolOrder = listOf(
    PdfEditTool.SELECT,
    PdfEditTool.HIGHLIGHT,
    PdfEditTool.UNDERLINE,
    PdfEditTool.NOTE,
    PdfEditTool.INK,
)

/**
 * Editor surface: the page above, the dock below, and the tool options floating between them.
 *
 * The layout is deliberately stable. Tool settings used to live in extra rows inside the dock, so
 * picking the pen made the whole toolbar jump and bury the page under two rows of chips. Now the
 * dock keeps one fixed height and the settings for the active tool arrive as a small card that
 * floats just above it, pointing at the tool that opened it. That is both calmer to look at and
 * less destructive to the space available for the page.
 */
@Composable
private fun EditorBody(
    state: PdfEditorUiState,
    vm: PdfEditorViewModel,
    onCopy: () -> Unit,
) {
    var dockHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val activeIndex = EditorToolOrder.indexOf(state.tool).coerceAtLeast(0)
    val showOptions = state.tool != PdfEditTool.SELECT || state.hasSelection

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                EditorPage(state = state, vm = vm)
            }
            EditorDock(
                state = state,
                onToolSelect = { tool ->
                    // Tapping the active tool again leaves it, the way a segmented control
                    // deselects. It is the quickest route back to plain text selection.
                    vm.setTool(if (state.tool == tool) PdfEditTool.SELECT else tool)
                    if (state.tool == tool) vm.clearSelection()
                },
                onPreviousPage = { vm.goToPage(-1) },
                onNextPage = { vm.goToPage(1) },
                onClearPage = vm::clearPage,
                modifier = Modifier.onSizeChanged { dockHeightPx = it.height },
            )
        }

        ToolOptionsOverlay(
            state = state,
            vm = vm,
            onCopy = onCopy,
            onDone = {
                vm.clearSelection()
                vm.setTool(PdfEditTool.SELECT)
            },
            visible = showOptions,
            anchorFraction = (activeIndex + 0.5f) / EditorToolOrder.size.toFloat(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp)
                .padding(bottom = with(density) { dockHeightPx.toDp() } + 8.dp),
        )
    }
}

/**
 * The permanent bottom chrome: page stepping, the five tools and one line of guidance.
 *
 * It is a floating rounded card rather than an edge-to-edge panel so it reads as an instrument,
 * not as another screen surface, and so the page keeps a little breathing room around it.
 */
@Composable
private fun EditorDock(
    state: PdfEditorUiState,
    onToolSelect: (PdfEditTool) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onClearPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Scaffold already reserves the system navigation inset for its content.
            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = scheme.surfaceContainerLow,
            tonalElevation = 2.dp,
            shadowElevation = 10.dp,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, end = 6.dp, top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onPreviousPage, enabled = state.pageIndex > 0) {
                        Icon(
                            Icons.Filled.ChevronLeft,
                            contentDescription = stringResource(R.string.pdf_editor_previous_page),
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.pdf_editor_page,
                            state.pageIndex + 1,
                            state.pageCount.coerceAtLeast(1),
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    IconButton(
                        onClick = onNextPage,
                        enabled = state.pageIndex < state.pageCount - 1,
                    ) {
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = stringResource(R.string.pdf_editor_next_page),
                        )
                    }
                    IconButton(
                        onClick = onClearPage,
                        enabled = state.currentPageMarks.isNotEmpty(),
                    ) {
                        Icon(
                            Icons.Filled.DeleteSweep,
                            contentDescription = stringResource(R.string.pdf_editor_clear_page),
                        )
                    }
                }

                Row(Modifier.fillMaxWidth()) {
                    EditorToolOrder.forEach { tool ->
                        EditorToolItem(
                            tool = tool,
                            selected = state.tool == tool,
                            onClick = { onToolSelect(tool) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                val hint = editorHint(state)
                AnimatedContent(
                    targetState = hint,
                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(90)) },
                    label = "editorHint",
                ) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun editorHint(state: PdfEditorUiState): String = when (state.tool) {
    PdfEditTool.SELECT -> when {
        state.hasSelection -> stringResource(R.string.pdf_editor_selection_hint)
        state.textRuns.isEmpty() -> stringResource(R.string.pdf_editor_no_text)
        else -> stringResource(R.string.pdf_editor_select_hint)
    }
    PdfEditTool.HIGHLIGHT -> stringResource(R.string.pdf_editor_hint_highlight)
    PdfEditTool.UNDERLINE -> stringResource(R.string.pdf_editor_hint_underline)
    PdfEditTool.NOTE -> stringResource(R.string.pdf_editor_hint_note)
    PdfEditTool.INK -> stringResource(R.string.pdf_editor_hint_draw)
}

@Composable
private fun EditorToolItem(
    tool: PdfEditTool,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "editorToolScale",
    )
    val container by animateColorAsState(
        targetValue = if (selected) scheme.primaryContainer else Color.Transparent,
        animationSpec = tween(180),
        label = "editorToolContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        animationSpec = tween(180),
        label = "editorToolContent",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                interactionSource = interaction,
                // No square ripple: the capsule behind the icon is the only selection feedback.
                indication = null,
                role = Role.Tab,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            )
            .padding(top = 2.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .width(54.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = editorToolIcon(tool),
                // The visible label names the tool; the icon adds no extra meaning.
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }
        Text(
            text = editorToolLabel(tool),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/**
 * The tooltip above the dock.
 *
 * It is deliberately flat-coloured and anchored with a small notch so it is unmistakably attached
 * to the tool that opened it, and it animates from the bottom edge of the dock rather than from
 * the screen edge.
 */
@Composable
private fun ToolOptionsOverlay(
    state: PdfEditorUiState,
    vm: PdfEditorViewModel,
    onCopy: () -> Unit,
    onDone: () -> Unit,
    visible: Boolean,
    anchorFraction: Float,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxWidth(),
        enter = fadeIn(tween(160)) +
            scaleIn(
                animationSpec = tween(220),
                initialScale = 0.94f,
                transformOrigin = TransformOrigin(0.5f, 1f),
            ) +
            slideInVertically(animationSpec = tween(220)) { it / 3 },
        exit = fadeOut(tween(120)) +
            scaleOut(
                animationSpec = tween(140),
                targetScale = 0.97f,
                transformOrigin = TransformOrigin(0.5f, 1f),
            ) +
            slideOutVertically(tween(140)) { it / 4 },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ToolOptionsCard(state = state, vm = vm, onCopy = onCopy, onDone = onDone)
            OptionsNotch(color = scheme.surfaceContainerHigh, fraction = anchorFraction)
        }
    }
}

@Composable
private fun ToolOptionsCard(
    state: PdfEditorUiState,
    vm: PdfEditorViewModel,
    onCopy: () -> Unit,
    onDone: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = scheme.surfaceContainerHigh,
        // No tonal tint: the notch below is painted with the same token, and both must match.
        tonalElevation = 0.dp,
        shadowElevation = 14.dp,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val accent = if (state.tool == PdfEditTool.SELECT) {
                    scheme.primary
                } else {
                    Color(state.colorArgb)
                }
                Box(Modifier.size(9.dp).background(accent, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = editorToolLabel(state.tool),
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onDone,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.pdf_editor_done),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            AnimatedContent(
                targetState = state.tool,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(90)) },
                label = "editorToolOptions",
            ) { tool ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when (tool) {
                        PdfEditTool.SELECT -> SelectionOptions(
                            onCopy = onCopy,
                            onHighlight = {
                                vm.markSelection(PdfMarkKind.HIGHLIGHT)
                                // Close the popover on apply so the marked text is visible again.
                                vm.clearSelection()
                            },
                            onUnderline = {
                                vm.markSelection(PdfMarkKind.UNDERLINE)
                                vm.clearSelection()
                            },
                        )
                        else -> {
                            EditorColorSection(selected = state.colorArgb, onSelect = vm::setColor)
                            if (tool == PdfEditTool.INK) {
                                EditorBrushSection(
                                    strokeWidth = state.strokeWidth,
                                    onSelect = vm::setStrokeWidth,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Small triangle that ties the options card to the active tool. */
@Composable
private fun OptionsNotch(color: Color, fraction: Float) {
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val cx = size.width * fraction.coerceIn(0.1f, 0.9f)
        val half = 8.dp.toPx()
        val path = Path().apply {
            moveTo(cx - half, 0f)
            lineTo(cx + half, 0f)
            lineTo(cx, size.height)
            close()
        }
        drawPath(path, color = color)
    }
}

/** Text-selection actions live in the same popover, so the dock never grows a second row. */
@Composable
private fun SelectionOptions(
    onCopy: () -> Unit,
    onHighlight: () -> Unit,
    onUnderline: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SelectionPill(
            icon = Icons.Filled.ContentCopy,
            label = stringResource(R.string.pdf_editor_copy),
            onClick = onCopy,
            modifier = Modifier.weight(1f),
        )
        SelectionPill(
            icon = Icons.Filled.Highlight,
            label = stringResource(R.string.pdf_editor_highlight_selection),
            onClick = onHighlight,
            modifier = Modifier.weight(1f),
        )
        SelectionPill(
            icon = Icons.Filled.FormatUnderlined,
            label = stringResource(R.string.pdf_editor_underline_selection),
            onClick = onUnderline,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SelectionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = scheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = scheme.onSecondaryContainer,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class EditorColorChoice(
    val argb: Int,
    @StringRes val nameRes: Int,
)

/** One palette for every tool: the bright tones read as highlighter, the dark ones as ink. */
private val EditorPalette = listOf(
    EditorColorChoice(0xFFFFD54F.toInt(), R.string.pdf_color_yellow),
    EditorColorChoice(0xFF8BC34A.toInt(), R.string.pdf_color_green),
    EditorColorChoice(0xFF42A5F5.toInt(), R.string.pdf_color_blue),
    EditorColorChoice(0xFFFF7043.toInt(), R.string.pdf_color_orange),
    EditorColorChoice(0xFFE53935.toInt(), R.string.pdf_color_red),
    EditorColorChoice(0xFF212121.toInt(), R.string.pdf_color_charcoal),
)

@Composable
private fun EditorColorSection(selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.pdf_editor_color),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            EditorPalette.forEach { choice ->
                ColorSwatch(
                    choice = choice,
                    selected = choice.argb == selected,
                    onClick = { onSelect(choice.argb) },
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    choice: EditorColorChoice,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "swatchScale",
    )
    val swatch = Color(choice.argb)
    val label = stringResource(choice.nameRes)

    Box(
        modifier = Modifier
            .size(34.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .then(
                if (selected) {
                    Modifier.border(2.dp, scheme.onSurface, CircleShape)
                } else {
                    Modifier.border(1.dp, scheme.outlineVariant.copy(alpha = 0.6f), CircleShape)
                },
            )
            .padding(4.dp)
            .background(swatch, CircleShape)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = if (swatch.luminance() > 0.55f) Color(0xFF1B1B1B) else Color.White,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/** Brush presets with a live line preview instead of three abstract chips. */
@Composable
private fun EditorBrushSection(strokeWidth: Float, onSelect: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.pdf_editor_brush_size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BrushChoice(
                width = 1.4f,
                labelRes = R.string.pdf_editor_thin,
                selected = strokeWidth <= 1.6f,
                onClick = { onSelect(1.4f) },
                modifier = Modifier.weight(1f),
            )
            BrushChoice(
                width = 2.6f,
                labelRes = R.string.pdf_editor_medium,
                selected = strokeWidth in 1.7f..4f,
                onClick = { onSelect(2.6f) },
                modifier = Modifier.weight(1f),
            )
            BrushChoice(
                width = 6f,
                labelRes = R.string.pdf_editor_thick,
                selected = strokeWidth > 4f,
                onClick = { onSelect(6f) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BrushChoice(
    width: Float,
    @StringRes labelRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (selected) scheme.primaryContainer else scheme.surfaceContainerLowest,
        animationSpec = tween(160),
        label = "brushContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        animationSpec = tween(160),
        label = "brushContent",
    )

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = container,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                drawLine(
                    color = contentColor,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = width.dp.toPx().coerceIn(1.2f, 8f),
                    cap = StrokeCap.Round,
                )
            }
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

private fun editorToolIcon(tool: PdfEditTool): ImageVector = when (tool) {
    PdfEditTool.SELECT -> Icons.Filled.TextFields
    PdfEditTool.HIGHLIGHT -> Icons.Filled.Highlight
    PdfEditTool.UNDERLINE -> Icons.Filled.FormatUnderlined
    PdfEditTool.NOTE -> Icons.AutoMirrored.Filled.StickyNote2
    PdfEditTool.INK -> Icons.Filled.Brush
}

@Composable
private fun editorToolLabel(tool: PdfEditTool): String = stringResource(
    when (tool) {
        PdfEditTool.SELECT -> R.string.pdf_tool_select
        PdfEditTool.HIGHLIGHT -> R.string.pdf_tool_highlight
        PdfEditTool.UNDERLINE -> R.string.pdf_tool_underline
        PdfEditTool.NOTE -> R.string.pdf_tool_note
        PdfEditTool.INK -> R.string.pdf_tool_draw
    },
)

private fun PdfMark.path(width: Float, height: Float): Path =
    points.toPath(width, height)

private fun List<PdfPoint>.toPath(width: Float, height: Float): Path {
    val path = Path()
    forEachIndexed { index, point ->
        val x = point.x * width
        val y = point.y * height
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
}
