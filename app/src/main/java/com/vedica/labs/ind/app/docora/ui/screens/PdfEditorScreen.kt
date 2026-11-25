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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Title
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.pdf.PdfMark
import com.vedica.labs.ind.app.docora.core.pdf.PdfMarkKind
import com.vedica.labs.ind.app.docora.core.pdf.PdfPoint
import com.vedica.labs.ind.app.docora.ui.designsystem.SystemBarAppearance
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Multi-page PDF editor (PRD §10, §11).
 *
 * Pages are a continuous, vertically scrollable column - the way a PDF reads - instead of a single
 * page stepped through with arrows. Every page is rendered on demand, every mark carries the page
 * it belongs to, and the dock is a horizontally scrollable instrument so the growing toolset never
 * squeezes the page or wraps a label.
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
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

    state.textDraft?.let { draft ->
        val isTextBox = draft.kind == PdfMarkKind.TEXT
        AlertDialog(
            onDismissRequest = vm::dismissDraft,
            title = {
                Text(
                    stringResource(
                        if (isTextBox) R.string.pdf_text_title else R.string.pdf_note_title,
                    ),
                )
            },
            text = {
                OutlinedTextField(
                    value = draft.text,
                    onValueChange = vm::updateDraft,
                    label = {
                        Text(
                            stringResource(
                                if (isTextBox) R.string.pdf_text_hint else R.string.pdf_note_hint,
                            ),
                        )
                    },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = vm::commitDraft,
                    enabled = draft.text.isNotBlank(),
                ) { Text(stringResource(R.string.pdf_note_add)) }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissDraft) {
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

/**
 * One page of the document.
 *
 * The card is measured from the page's own aspect ratio. It starts as an A4-shaped placeholder
 * and swaps in the render when it arrives, so fast scrolling never collapses the column.
 */
@Composable
private fun EditorPageCard(
    index: Int,
    state: PdfEditorUiState,
    vm: PdfEditorViewModel,
) {
    val aspect = state.pageAspect(index)
    val bitmap = state.pageBitmaps[index]
    val marks = remember(state.marks, index) { state.marks.filter { it.pageIndex == index } }
    val scheme = MaterialTheme.colorScheme

    Column(Modifier.fillMaxWidth()) {
        LaunchedEffect(index) { vm.ensurePage(index) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = scheme.surfaceContainerHigh,
            ) {
                Text(
                    text = stringResource(R.string.pdf_editor_page_badge, index + 1),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (marks.isNotEmpty()) {
                Text(
                    text = pluralStringResource(
                        R.plurals.pdf_editor_marks_count,
                        marks.size,
                        marks.size,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color.White,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / aspect),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val pageWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
                val pageHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
                if (bitmap != null) {
                    val image = remember(bitmap) { bitmap.asImageBitmap() }
                    Image(
                        bitmap = image,
                        contentDescription = stringResource(
                            R.string.a11y_page_thumbnail,
                            index + 1,
                        ),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds,
                    )
                } else {
                    PagePlaceholder()
                }
                TextMarksLayer(
                    pageIndex = index,
                    marks = marks,
                    pageWidthPx = pageWidthPx,
                    pageHeightPx = pageHeightPx,
                )
                PageInteraction(
                    index = index,
                    state = state,
                    vm = vm,
                    marks = marks,
                )
            }
        }
    }
}

@Composable
private fun PagePlaceholder() {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(26.dp),
            strokeWidth = 2.dp,
            color = scheme.primary.copy(alpha = 0.55f),
        )
    }
}

/** Rendered text boxes are real text, so their size tracks the page width at 72 dpi. */
@Composable
private fun TextMarksLayer(
    pageIndex: Int,
    marks: List<PdfMark>,
    pageWidthPx: Float,
    pageHeightPx: Float,
) {
    val density = LocalDensity.current
    marks.forEach { mark ->
        if (mark.kind != PdfMarkKind.TEXT) return@forEach
        val start = mark.start ?: return@forEach
        val text = mark.note.orEmpty()
        if (text.isBlank()) return@forEach
        Text(
            text = text,
            color = Color(mark.colorArgb),
            fontSize = with(density) {
                (pageWidthPx * mark.fontSize / 595f).coerceIn(6f, pageWidthPx / 4f).toSp()
            },
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (start.x * pageWidthPx).roundToInt(),
                        y = (start.y * pageHeightPx).roundToInt(),
                    )
                }
                .widthIn(max = with(density) { (pageWidthPx * 0.9f).toDp() }),
        )
    }
}

/**
 * Gesture surface for one page.
 *
 * With [PdfEditTool.SELECT] a plain drag is left unconsumed so the list scrolls; a tap selects a
 * word and a long-press drag extends the selection. Drawing tools consume their drags, which is
 * what makes a page directly editable anywhere in the document.
 */
@Composable
private fun PageInteraction(
    index: Int,
    state: PdfEditorUiState,
    vm: PdfEditorViewModel,
    marks: List<PdfMark>,
) {
    var previewStart by remember(index, state.tool) { mutableStateOf<PdfPoint?>(null) }
    var previewEnd by remember(index, state.tool) { mutableStateOf<PdfPoint?>(null) }
    var inkPoints by remember(index, state.tool) { mutableStateOf<List<PdfPoint>>(emptyList()) }

    val gestureModifier = when (state.tool) {
        PdfEditTool.SELECT -> Modifier
            .pointerInput(index) {
                // A tap selects a word, but the down event is deliberately not consumed: a drag
                // must still reach the LazyColumn so the document can be scrolled.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                    vm.selectStart(index, normalisedPoint(up.position))
                    up.consume()
                }
            }
            .pointerInput(index) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset -> vm.selectStart(index, normalisedPoint(offset)) },
                    onDrag = { change, _ ->
                        vm.selectExtend(index, normalisedPoint(change.position))
                        change.consume()
                    },
                    onDragEnd = {},
                    onDragCancel = {},
                )
            }
        PdfEditTool.NOTE -> Modifier.pointerInput(index) {
            detectTapGestures { offset ->
                vm.setActivePage(index)
                vm.beginDraft(index, normalisedPoint(offset), PdfMarkKind.NOTE)
            }
        }
        PdfEditTool.TEXT -> Modifier.pointerInput(index) {
            detectTapGestures { offset ->
                vm.setActivePage(index)
                vm.beginDraft(index, normalisedPoint(offset), PdfMarkKind.TEXT)
            }
        }
        PdfEditTool.ERASER -> Modifier.pointerInput(index) {
            detectTapGestures { offset ->
                vm.setActivePage(index)
                vm.eraseAt(index, normalisedPoint(offset))
            }
        }
        PdfEditTool.INK, PdfEditTool.SIGNATURE -> Modifier.pointerInput(index, state.tool) {
            detectDragGestures(
                onDragStart = { offset ->
                    vm.setActivePage(index)
                    inkPoints = listOf(normalisedPoint(offset))
                },
                onDrag = { change, _ ->
                    inkPoints = inkPoints + normalisedPoint(change.position)
                    change.consume()
                },
                onDragEnd = {
                    vm.addInk(index, inkPoints)
                    inkPoints = emptyList()
                },
                onDragCancel = { inkPoints = emptyList() },
            )
        }
        else -> {
            val kind = state.tool.markKind()
            Modifier.pointerInput(index, state.tool) {
                detectDragGestures(
                    onDragStart = { offset ->
                        vm.setActivePage(index)
                        val point = normalisedPoint(offset)
                        previewStart = point
                        previewEnd = point
                    },
                    onDrag = { change, _ ->
                        previewEnd = normalisedPoint(change.position)
                        change.consume()
                    },
                    onDragEnd = {
                        val start = previewStart
                        val end = previewEnd
                        if (start != null && end != null && kind != null) {
                            vm.addRectMark(index, start, end, kind)
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
        }
    }

    Box(Modifier.fillMaxSize().then(gestureModifier)) {
        MarkLayer(
            state = state,
            pageIndex = index,
            marks = marks,
            previewStart = previewStart,
            previewEnd = previewEnd,
            inkPoints = inkPoints,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun MarkLayer(
    state: PdfEditorUiState,
    pageIndex: Int,
    marks: List<PdfMark>,
    previewStart: PdfPoint?,
    previewEnd: PdfPoint?,
    inkPoints: List<PdfPoint>,
    modifier: Modifier = Modifier,
) {
    val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    Canvas(modifier) {
        val w = size.width
        val h = size.height

        marks.forEach { mark ->
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
                PdfMarkKind.STRIKEOUT -> {
                    val start = mark.start
                    val end = mark.end
                    if (start != null && end != null) {
                        val y = (start.y + end.y) / 2f * h
                        drawLine(
                            color = color,
                            start = Offset(min(start.x, end.x) * w, y),
                            end = Offset(max(start.x, end.x) * w, y),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
                PdfMarkKind.SQUIGGLY -> {
                    val start = mark.start
                    val end = mark.end
                    if (start != null && end != null) {
                        drawSquiggle(
                            color = color,
                            left = min(start.x, end.x) * w,
                            right = max(start.x, end.x) * w,
                            y = max(start.y, end.y) * h,
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
                PdfMarkKind.RECTANGLE -> drawRectMark(mark, w, h, color, filled = false)
                PdfMarkKind.REDACT -> drawRectMark(mark, w, h, color, filled = true)
                PdfMarkKind.ELLIPSE -> drawEllipseMark(mark, w, h, color)
                PdfMarkKind.LINE -> drawLineMark(mark, w, h, color, arrow = false)
                PdfMarkKind.ARROW -> drawLineMark(mark, w, h, color, arrow = true)
                PdfMarkKind.TEXT -> Unit
            }
        }

        if (pageIndex == state.pageIndex) {
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
        }

        val start = previewStart
        val end = previewEnd
        if (start != null && end != null) {
            val color = Color(state.colorArgb)
            when (state.tool) {
                PdfEditTool.HIGHLIGHT -> drawRect(
                    color = color.copy(alpha = 0.34f),
                    topLeft = Offset(min(start.x, end.x) * w, min(start.y, end.y) * h),
                    size = Size(abs(end.x - start.x) * w, abs(end.y - start.y) * h),
                )
                PdfEditTool.UNDERLINE -> drawLine(
                    color = color,
                    start = Offset(min(start.x, end.x) * w, max(start.y, end.y) * h),
                    end = Offset(max(start.x, end.x) * w, max(start.y, end.y) * h),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                PdfEditTool.STRIKEOUT -> drawLine(
                    color = color,
                    start = Offset(min(start.x, end.x) * w, (start.y + end.y) / 2f * h),
                    end = Offset(max(start.x, end.x) * w, (start.y + end.y) / 2f * h),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                PdfEditTool.SQUIGGLY -> drawSquiggle(
                    color = color,
                    left = min(start.x, end.x) * w,
                    right = max(start.x, end.x) * w,
                    y = max(start.y, end.y) * h,
                )
                PdfEditTool.RECTANGLE -> drawPreviewRect(start, end, w, h, color, filled = false)
                PdfEditTool.REDACT -> drawPreviewRect(start, end, w, h, color, filled = true)
                PdfEditTool.ELLIPSE -> drawOval(
                    color = color,
                    topLeft = Offset(min(start.x, end.x) * w, min(start.y, end.y) * h),
                    size = Size(abs(end.x - start.x) * w, abs(end.y - start.y) * h),
                    style = Stroke(width = state.strokeWidth.dp.toPx()),
                )
                PdfEditTool.LINE, PdfEditTool.ARROW -> {
                    drawLine(
                        color = color,
                        start = Offset(start.x * w, start.y * h),
                        end = Offset(end.x * w, end.y * h),
                        strokeWidth = state.strokeWidth.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    if (state.tool == PdfEditTool.ARROW) {
                        drawArrowHead(
                            start = Offset(start.x * w, start.y * h),
                            end = Offset(end.x * w, end.y * h),
                            color = color,
                            strokeWidth = state.strokeWidth.dp.toPx(),
                        )
                    }
                }
                else -> Unit
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPreviewRect(
    start: PdfPoint,
    end: PdfPoint,
    w: Float,
    h: Float,
    color: Color,
    filled: Boolean,
) {
    drawRect(
        color = color,
        topLeft = Offset(min(start.x, end.x) * w, min(start.y, end.y) * h),
        size = Size(abs(end.x - start.x) * w, abs(end.y - start.y) * h),
        style = if (filled) {
            androidx.compose.ui.graphics.drawscope.Fill
        } else {
            Stroke(2.dp.toPx())
        },
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRectMark(
    mark: PdfMark,
    w: Float,
    h: Float,
    color: Color,
    filled: Boolean,
) {
    val start = mark.start ?: return
    val end = mark.end ?: return
    drawRect(
        color = color,
        topLeft = Offset(min(start.x, end.x) * w, min(start.y, end.y) * h),
        size = Size(abs(end.x - start.x) * w, abs(end.y - start.y) * h),
        style = if (filled) {
            androidx.compose.ui.graphics.drawscope.Fill
        } else {
            Stroke(mark.strokeWidth.dp.toPx())
        },
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEllipseMark(
    mark: PdfMark,
    w: Float,
    h: Float,
    color: Color,
) {
    val start = mark.start ?: return
    val end = mark.end ?: return
    drawOval(
        color = color,
        topLeft = Offset(min(start.x, end.x) * w, min(start.y, end.y) * h),
        size = Size(abs(end.x - start.x) * w, abs(end.y - start.y) * h),
        style = Stroke(mark.strokeWidth.dp.toPx()),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLineMark(
    mark: PdfMark,
    w: Float,
    h: Float,
    color: Color,
    arrow: Boolean,
) {
    val start = mark.start ?: return
    val end = mark.end ?: return
    val from = Offset(start.x * w, start.y * h)
    val to = Offset(end.x * w, end.y * h)
    drawLine(
        color = color,
        start = from,
        end = to,
        strokeWidth = mark.strokeWidth.dp.toPx(),
        cap = StrokeCap.Round,
    )
    if (arrow) drawArrowHead(from, to, color, mark.strokeWidth.dp.toPx())
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrowHead(
    start: Offset,
    end: Offset,
    color: Color,
    strokeWidth: Float,
) {
    val angle = kotlin.math.atan2(end.y - start.y, end.x - start.x)
    val head = (strokeWidth * 4f).coerceAtLeast(10.dp.toPx())
    val spread = Math.toRadians(24.0).toFloat()
    val first = Offset(
        end.x - head * kotlin.math.cos(angle - spread),
        end.y - head * kotlin.math.sin(angle - spread),
    )
    val second = Offset(
        end.x - head * kotlin.math.cos(angle + spread),
        end.y - head * kotlin.math.sin(angle + spread),
    )
    drawLine(color, end, first, strokeWidth, StrokeCap.Round)
    drawLine(color, end, second, strokeWidth, StrokeCap.Round)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSquiggle(
    color: Color,
    left: Float,
    right: Float,
    y: Float,
) {
    if (right - left < 1f) return
    val amplitude = 2.5.dp.toPx()
    val step = 8.dp.toPx()
    val path = Path()
    var x = left
    var up = true
    path.moveTo(left, y)
    while (x < right) {
        x = (x + step).coerceAtMost(right)
        path.lineTo(x, y + if (up) -amplitude else amplitude)
        up = !up
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
    )
}

/** Tool metadata for the scrollable dock. */
private data class EditorToolSpec(
    val tool: PdfEditTool,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
)

private val EditorTools = listOf(
    EditorToolSpec(PdfEditTool.SELECT, R.string.pdf_tool_select, Icons.Filled.TextFields),
    EditorToolSpec(PdfEditTool.HIGHLIGHT, R.string.pdf_tool_highlight, Icons.Filled.Highlight),
    EditorToolSpec(PdfEditTool.UNDERLINE, R.string.pdf_tool_underline, Icons.Filled.FormatUnderlined),
    EditorToolSpec(PdfEditTool.STRIKEOUT, R.string.pdf_tool_strikethrough, Icons.Filled.FormatStrikethrough),
    EditorToolSpec(PdfEditTool.SQUIGGLY, R.string.pdf_tool_squiggly, Icons.Filled.Gesture),
    EditorToolSpec(PdfEditTool.NOTE, R.string.pdf_tool_note, Icons.AutoMirrored.Filled.StickyNote2),
    EditorToolSpec(PdfEditTool.TEXT, R.string.pdf_tool_text, Icons.Filled.Title),
    EditorToolSpec(PdfEditTool.INK, R.string.pdf_tool_draw, Icons.Filled.Brush),
    EditorToolSpec(PdfEditTool.SIGNATURE, R.string.pdf_tool_signature, Icons.Filled.Draw),
    EditorToolSpec(PdfEditTool.RECTANGLE, R.string.pdf_tool_rectangle, Icons.Filled.CropSquare),
    EditorToolSpec(PdfEditTool.ELLIPSE, R.string.pdf_tool_ellipse, Icons.Filled.Circle),
    EditorToolSpec(PdfEditTool.LINE, R.string.pdf_tool_line, Icons.Filled.Remove),
    EditorToolSpec(PdfEditTool.ARROW, R.string.pdf_tool_arrow, Icons.Filled.NorthEast),
    EditorToolSpec(PdfEditTool.REDACT, R.string.pdf_tool_redact, Icons.Filled.Block),
    EditorToolSpec(PdfEditTool.ERASER, R.string.pdf_tool_eraser, Icons.Filled.AutoFixHigh),
)

private fun PdfEditTool.markKind(): PdfMarkKind? = when (this) {
    PdfEditTool.HIGHLIGHT -> PdfMarkKind.HIGHLIGHT
    PdfEditTool.UNDERLINE -> PdfMarkKind.UNDERLINE
    PdfEditTool.STRIKEOUT -> PdfMarkKind.STRIKEOUT
    PdfEditTool.SQUIGGLY -> PdfMarkKind.SQUIGGLY
    PdfEditTool.RECTANGLE -> PdfMarkKind.RECTANGLE
    PdfEditTool.ELLIPSE -> PdfMarkKind.ELLIPSE
    PdfEditTool.LINE -> PdfMarkKind.LINE
    PdfEditTool.ARROW -> PdfMarkKind.ARROW
    PdfEditTool.REDACT -> PdfMarkKind.REDACT
    else -> null
}

@Composable
private fun EditorBody(
    state: PdfEditorUiState,
    vm: PdfEditorViewModel,
    onCopy: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val railState = rememberLazyListState()

    // Jump to the page the viewer was on, and follow the scroll position afterwards.
    LaunchedEffect(state.pageCount) {
        if (state.pageCount > 0) listState.scrollToItem(state.pageIndex)
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.mostVisiblePageIndex() }
            .distinctUntilChanged()
            .collect { index -> if (index != null) vm.setActivePage(index) }
    }
    LaunchedEffect(state.pageIndex) {
        if (railState.firstVisibleItemIndex != state.pageIndex) {
            railState.animateScrollToItem((state.pageIndex - 1).coerceAtLeast(0))
        }
    }

    var showJumpDialog by remember { mutableStateOf(false) }

    fun jumpToPage(index: Int) {
        val target = index.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
        scope.launch { listState.animateScrollToItem(target) }
        vm.setActivePage(target)
    }

    if (showJumpDialog) {
        var input by remember { mutableStateOf((state.pageIndex + 1).toString()) }
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text(stringResource(R.string.pdf_editor_jump_title)) },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { value ->
                        input = value.filter { it.isDigit() }.take(6)
                    },
                    label = { Text(stringResource(R.string.pdf_editor_jump_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showJumpDialog = false
                        jumpToPage((input.toIntOrNull() ?: 1) - 1)
                    },
                ) { Text(stringResource(R.string.pdf_editor_jump_go)) }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(count = state.pageCount, key = { it }) { index ->
                EditorPageCard(index = index, state = state, vm = vm)
            }
        }
        EditorDock(
            state = state,
            vm = vm,
            railState = railState,
            onJumpToPage = ::jumpToPage,
            onOpenJump = { showJumpDialog = true },
            onToolSelect = { tool ->
                // Tapping the active tool again leaves it, the way a segmented control
                // deselects. It is the quickest route back to scrolling and selection.
                vm.setTool(if (state.tool == tool) PdfEditTool.SELECT else tool)
                if (state.tool == tool) vm.clearSelection()
            },
            onClearPage = vm::clearPage,
            onCopy = onCopy,
            onDone = {
                vm.clearSelection()
                vm.setTool(PdfEditTool.SELECT)
            },
        )
    }
}

/**
 * The bottom instrument: a page rail for jumping, a horizontally scrollable tool strip and one
 * line of guidance. The strip scrolls so a growing toolset never squeezes the page or wraps labels.
 */
@Composable
private fun EditorDock(
    state: PdfEditorUiState,
    vm: PdfEditorViewModel,
    railState: androidx.compose.foundation.lazy.LazyListState,
    onJumpToPage: (Int) -> Unit,
    onOpenJump: () -> Unit,
    onToolSelect: (PdfEditTool) -> Unit,
    onClearPage: () -> Unit,
    onCopy: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val showOptions = state.tool != PdfEditTool.SELECT || state.hasSelection
    Box(
        modifier = modifier
            .fillMaxWidth()
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
                        .padding(start = 2.dp, end = 4.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onOpenJump) {
                        Icon(
                            Icons.Filled.FindInPage,
                            contentDescription = stringResource(R.string.pdf_editor_jump_open),
                        )
                    }
                    LazyRow(
                        state = railState,
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                    ) {
                        items(count = state.pageCount, key = { it }) { index ->
                            PageChip(
                                index = index,
                                selected = index == state.pageIndex,
                                onClick = { onJumpToPage(index) },
                            )
                        }
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

                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(1.dp)
                        .background(scheme.outlineVariant.copy(alpha = 0.5f)),
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(EditorTools, key = { it.tool }) { spec ->
                        EditorToolItem(
                            spec = spec,
                            selected = state.tool == spec.tool,
                            onClick = { onToolSelect(spec.tool) },
                            modifier = Modifier.width(66.dp),
                        )
                    }
                }

                // Contextual options sit inside the dock, not on top of the page: the user can
                // always see the area they are about to mark.
                AnimatedVisibility(
                    visible = showOptions,
                    enter = fadeIn(tween(140)) + androidx.compose.animation.expandVertically(tween(180)),
                    exit = fadeOut(tween(100)) + androidx.compose.animation.shrinkVertically(tween(140)),
                ) {
                    ToolOptionsRow(
                        state = state,
                        vm = vm,
                        onCopy = onCopy,
                        onClearPage = onClearPage,
                        onDone = onDone,
                    )
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
private fun PageChip(
    index: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (selected) scheme.primaryContainer else scheme.surfaceContainerHighest,
        animationSpec = tween(160),
        label = "pageChipContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        animationSpec = tween(160),
        label = "pageChipContent",
    )
    val pageDescription = stringResource(R.string.pdf_editor_page_badge, index + 1)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        color = container,
        modifier = Modifier.semantics {
            contentDescription = pageDescription
        },
    ) {
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelMedium,
            color = content,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun editorHint(state: PdfEditorUiState): String = when (state.tool) {
    PdfEditTool.SELECT -> when {
        state.hasSelection -> stringResource(R.string.pdf_editor_selection_hint)
        state.textRuns.isEmpty() -> stringResource(R.string.pdf_editor_no_text_scroll)
        else -> stringResource(R.string.pdf_editor_select_hint)
    }
    PdfEditTool.HIGHLIGHT -> stringResource(R.string.pdf_editor_hint_highlight)
    PdfEditTool.UNDERLINE -> stringResource(R.string.pdf_editor_hint_underline)
    PdfEditTool.STRIKEOUT -> stringResource(R.string.pdf_editor_hint_strikethrough)
    PdfEditTool.SQUIGGLY -> stringResource(R.string.pdf_editor_hint_squiggly)
    PdfEditTool.NOTE -> stringResource(R.string.pdf_editor_hint_note)
    PdfEditTool.TEXT -> stringResource(R.string.pdf_editor_hint_text)
    PdfEditTool.INK -> stringResource(R.string.pdf_editor_hint_draw)
    PdfEditTool.SIGNATURE -> stringResource(R.string.pdf_editor_hint_signature)
    PdfEditTool.RECTANGLE -> stringResource(R.string.pdf_editor_hint_rectangle)
    PdfEditTool.ELLIPSE -> stringResource(R.string.pdf_editor_hint_ellipse)
    PdfEditTool.LINE -> stringResource(R.string.pdf_editor_hint_line)
    PdfEditTool.ARROW -> stringResource(R.string.pdf_editor_hint_arrow)
    PdfEditTool.REDACT -> stringResource(R.string.pdf_editor_hint_redact)
    PdfEditTool.ERASER -> stringResource(R.string.pdf_editor_hint_eraser)
}

@Composable
private fun EditorToolItem(
    spec: EditorToolSpec,
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
            .padding(top = 4.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .width(50.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = spec.icon,
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
            text = stringResource(spec.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Compact, horizontally scrollable options for the active tool.
 *
 * The row lives inside the dock rather than floating over the page, so choosing a colour never
 * hides the area the user is about to mark. It scrolls for the same reason the tool strip does:
 * the toolset and its options are allowed to grow.
 */
private data class EditorColorChoice(
    val argb: Int,
    @StringRes val nameRes: Int,
)

/** One palette for every tool: the bright tones read as highlighter, the dark ones as ink. */
private val EditorPalette = listOf(
    EditorColorChoice(0xFFFFD54F.toInt(), R.string.pdf_color_yellow),
    EditorColorChoice(0xFF8BC34A.toInt(), R.string.pdf_color_green),
    EditorColorChoice(0xFF42A5F5.toInt(), R.string.pdf_color_blue),
    EditorColorChoice(0xFF9C6ADE.toInt(), R.string.pdf_color_purple),
    EditorColorChoice(0xFFFF7043.toInt(), R.string.pdf_color_orange),
    EditorColorChoice(0xFFE53935.toInt(), R.string.pdf_color_red),
    EditorColorChoice(0xFF212121.toInt(), R.string.pdf_color_charcoal),
    EditorColorChoice(0xFFFFFFFF.toInt(), R.string.pdf_color_white),
)

@Composable
private fun ToolOptionsRow(
    state: PdfEditorUiState,
    vm: PdfEditorViewModel,
    onCopy: () -> Unit,
    onClearPage: () -> Unit,
    onDone: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state.tool) {
                PdfEditTool.SELECT -> {
                    item {
                        ActionPill(
                            icon = Icons.Filled.ContentCopy,
                            label = stringResource(R.string.pdf_editor_copy),
                            onClick = onCopy,
                        )
                    }
                    item {
                        ActionPill(
                            icon = Icons.Filled.Highlight,
                            label = stringResource(R.string.pdf_editor_highlight_selection),
                            onClick = {
                                vm.markSelection(PdfMarkKind.HIGHLIGHT)
                                vm.clearSelection()
                            },
                        )
                    }
                    item {
                        ActionPill(
                            icon = Icons.Filled.FormatUnderlined,
                            label = stringResource(R.string.pdf_editor_underline_selection),
                            onClick = {
                                vm.markSelection(PdfMarkKind.UNDERLINE)
                                vm.clearSelection()
                            },
                        )
                    }
                    item {
                        ActionPill(
                            icon = Icons.Filled.FormatStrikethrough,
                            label = stringResource(R.string.pdf_editor_strike_selection),
                            onClick = {
                                vm.markSelection(PdfMarkKind.STRIKEOUT)
                                vm.clearSelection()
                            },
                        )
                    }
                }
                PdfEditTool.ERASER -> {
                    item {
                        Text(
                            text = stringResource(R.string.pdf_editor_eraser_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 2,
                            modifier = Modifier.widthIn(max = 220.dp),
                        )
                    }
                    item {
                        ActionPill(
                            icon = Icons.Filled.DeleteSweep,
                            label = stringResource(R.string.pdf_editor_clear_page),
                            onClick = onClearPage,
                        )
                    }
                }
                else -> {
                    items(EditorPalette, key = { it.argb }) { choice ->
                        ColorDot(
                            choice = choice,
                            selected = choice.argb == state.colorArgb,
                            onClick = { vm.setColor(choice.argb) },
                        )
                    }
                    if (state.tool.supportsWidth()) {
                        item { CompactDivider() }
                        item {
                            WidthChip(
                                label = stringResource(R.string.pdf_editor_thin),
                                selected = state.strokeWidth <= 1.6f,
                                onClick = { vm.setStrokeWidth(1.4f) },
                            )
                        }
                        item {
                            WidthChip(
                                label = stringResource(R.string.pdf_editor_medium),
                                selected = state.strokeWidth in 1.7f..4f,
                                onClick = { vm.setStrokeWidth(2.6f) },
                            )
                        }
                        item {
                            WidthChip(
                                label = stringResource(R.string.pdf_editor_thick),
                                selected = state.strokeWidth > 4f,
                                onClick = { vm.setStrokeWidth(6f) },
                            )
                        }
                    }
                    if (state.tool == PdfEditTool.TEXT) {
                        item { CompactDivider() }
                        item {
                            WidthChip(
                                label = stringResource(R.string.pdf_editor_small),
                                selected = state.fontSize <= 10f,
                                onClick = { vm.setFontSize(10f) },
                            )
                        }
                        item {
                            WidthChip(
                                label = stringResource(R.string.pdf_editor_medium),
                                selected = state.fontSize in 10.1f..18f,
                                onClick = { vm.setFontSize(14f) },
                            )
                        }
                        item {
                            WidthChip(
                                label = stringResource(R.string.pdf_editor_large),
                                selected = state.fontSize > 18f,
                                onClick = { vm.setFontSize(24f) },
                            )
                        }
                    }
                }
            }
        }
        if (state.tool != PdfEditTool.ERASER) {
            IconButton(onClick = onDone) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.pdf_editor_done),
                )
            }
        }
    }
}

private fun PdfEditTool.supportsWidth(): Boolean = when (this) {
    PdfEditTool.INK,
    PdfEditTool.SIGNATURE,
    PdfEditTool.RECTANGLE,
    PdfEditTool.ELLIPSE,
    PdfEditTool.LINE,
    PdfEditTool.ARROW,
    -> true
    else -> false
}

@Composable
private fun ActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = scheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = scheme.onSecondaryContainer,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSecondaryContainer,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ColorDot(
    choice: EditorColorChoice,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val swatch = Color(choice.argb)
    val label = stringResource(choice.nameRes)
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
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
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun WidthChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainerHighest,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun CompactDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(22.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
    )
}

@Composable
private fun editorToolLabel(tool: PdfEditTool): String = stringResource(
    when (tool) {
        PdfEditTool.SELECT -> R.string.pdf_tool_select
        PdfEditTool.HIGHLIGHT -> R.string.pdf_tool_highlight
        PdfEditTool.UNDERLINE -> R.string.pdf_tool_underline
        PdfEditTool.STRIKEOUT -> R.string.pdf_tool_strikethrough
        PdfEditTool.SQUIGGLY -> R.string.pdf_tool_squiggly
        PdfEditTool.NOTE -> R.string.pdf_tool_note
        PdfEditTool.TEXT -> R.string.pdf_tool_text
        PdfEditTool.INK -> R.string.pdf_tool_draw
        PdfEditTool.SIGNATURE -> R.string.pdf_tool_signature
        PdfEditTool.RECTANGLE -> R.string.pdf_tool_rectangle
        PdfEditTool.ELLIPSE -> R.string.pdf_tool_ellipse
        PdfEditTool.LINE -> R.string.pdf_tool_line
        PdfEditTool.ARROW -> R.string.pdf_tool_arrow
        PdfEditTool.REDACT -> R.string.pdf_tool_redact
        PdfEditTool.ERASER -> R.string.pdf_tool_eraser
    },
)

private fun PdfMark.path(width: Float, height: Float): Path =
    points.toPath(width, height)

/** Converts a pointer position into the page's normalised 0..1 space. */
private fun PointerInputScope.normalisedPoint(offset: Offset): PdfPoint = PdfPoint(
    x = (offset.x / size.width).coerceIn(0f, 1f),
    y = (offset.y / size.height).coerceIn(0f, 1f),
)

/**
 * The page that occupies most of the viewport.
 *
 * Using the first visible item would keep the previous page "current" while only a sliver of it
 * remains on screen; the page under the user's eye is the one with the largest visible height.
 */
private fun LazyListState.mostVisiblePageIndex(): Int? {
    val info = layoutInfo
    if (info.visibleItemsInfo.isEmpty()) return null
    val viewportStart = info.viewportStartOffset
    val viewportEnd = info.viewportEndOffset
    return info.visibleItemsInfo.maxByOrNull { item ->
        (min(item.offset + item.size, viewportEnd) - max(item.offset, viewportStart))
            .coerceAtLeast(0)
    }?.index
}

private fun List<PdfPoint>.toPath(width: Float, height: Float): Path {
    val path = Path()
    forEachIndexed { index, point ->
        val x = point.x * width
        val y = point.y * height
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
}
