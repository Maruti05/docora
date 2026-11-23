package com.vedica.labs.ind.app.docora.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.pdf.PdfSearchHit
import com.vedica.labs.ind.app.docora.feature.print.DocumentPrintAdapter
import com.vedica.labs.ind.app.docora.ui.designsystem.SystemBarAppearance
import kotlinx.coroutines.launch

/** Dark stage behind PDF pages so page edges stay visible in both themes. */
private val StageColor = Color(0xFF2B2F2C)
private val ChromeColor = Color(0xF0141815)

/** Inverts a rendered page for a low-light, high-contrast reading mode. */
private val InvertColorMatrix = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

/**
 * Zoom and pan state for a single image document.
 *
 * An image is laid out as one item, so scaling it visually is safe; the PDF reader instead zooms
 * its whole list by width so pages never overlap.
 */
private class PageZoomState {

    var scale by mutableStateOf(MIN_SCALE)
        private set

    var offset by mutableStateOf(Offset.Zero)
        private set

    val isZoomed: Boolean get() = scale > MIN_SCALE

    private var viewport by mutableStateOf(IntSize.Zero)

    fun onViewportChanged(size: IntSize) {
        viewport = size
        if (scale <= MIN_SCALE) {
            offset = Offset.Zero
        } else {
            offset = clamp(offset)
        }
    }

    fun onTransform(zoomChange: Float, panChange: Offset, centroid: Offset) {
        val oldScale = scale
        val newScale = (oldScale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
        if (newScale <= MIN_SCALE) {
            scale = MIN_SCALE
            offset = Offset.Zero
            return
        }
        if (newScale == oldScale && panChange == Offset.Zero) return
        val focal = centroid - center()
        val adjusted = focal + (offset - focal) * (newScale / oldScale)
        scale = newScale
        offset = clamp(adjusted + panChange)
    }

    fun onDrag(drag: Offset) {
        if (scale <= MIN_SCALE) return
        offset = clamp(offset + drag)
    }

    fun onDoubleTap(position: Offset) {
        if (scale > MIN_SCALE + 0.05f) {
            scale = MIN_SCALE
            offset = Offset.Zero
            return
        }
        val oldScale = scale
        val newScale = DOUBLE_TAP_SCALE
        val focal = position - center()
        val adjusted = focal + (offset - focal) * (newScale / oldScale)
        scale = newScale
        offset = clamp(adjusted)
    }

    private fun center(): Offset = Offset(viewport.width / 2f, viewport.height / 2f)

    private fun clamp(value: Offset): Offset {
        if (scale <= MIN_SCALE || viewport.width <= 0 || viewport.height <= 0) {
            return Offset.Zero
        }
        val maxX = viewport.width * (scale - 1f) / 2f
        val maxY = viewport.height * (scale - 1f) / 2f
        return Offset(
            x = value.x.coerceIn(-maxX, maxX),
            y = value.y.coerceIn(-maxY, maxY),
        )
    }

    private companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 5f
        const val DOUBLE_TAP_SCALE = 2.5f
    }
}

/**
 * Pinch-to-zoom, pan-when-zoomed and double-tap zoom for a single image document.
 *
 * The gesture loop deliberately does not consume single-finger movement at 1×. That is the
 * difference between an image that can be scrolled inside its list and one whose zoom modifier
 * swallows every drag before the list ever sees it.
 */
private fun Modifier.pageZoom(state: PageZoomState): Modifier = this
    .onSizeChanged(state::onViewportChanged)
    .pointerInput(state) {
        val touchSlop = viewConfiguration.touchSlop
        val doubleTapSlop = 48.dp.toPx()
        val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
        var lastTapTime = 0L
        var lastTapPosition = Offset.Zero

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var totalDrag = 0f
            var multiTouch = false
            var lastUpTime = 0L
            var lastPosition = down.position

            do {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                when {
                    pressed.size >= 2 -> {
                        multiTouch = true
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val centroid = event.calculateCentroid(useCurrent = true)
                        totalDrag += panChange.getDistance()
                        if (zoomChange != 1f || panChange != Offset.Zero) {
                            state.onTransform(zoomChange, panChange, centroid)
                            event.changes.forEach { it.consume() }
                        }
                    }
                    pressed.size == 1 -> {
                        val change = pressed.first()
                        val drag = change.positionChange()
                        totalDrag += drag.getDistance()
                        if (state.isZoomed && drag != Offset.Zero) {
                            state.onDrag(drag)
                            change.consume()
                        }
                        lastPosition = change.position
                    }
                }
                event.changes.firstOrNull { !it.pressed }?.let { up ->
                    lastUpTime = up.uptimeMillis
                    lastPosition = up.position
                }
            } while (event.changes.any { it.pressed })

            if (!multiTouch && totalDrag <= touchSlop && lastUpTime > 0L) {
                val isDoubleTap = lastUpTime - lastTapTime <= doubleTapTimeout &&
                    (lastPosition - lastTapPosition).getDistance() <= doubleTapSlop
                if (isDoubleTap) {
                    state.onDoubleTap(lastPosition)
                    lastTapTime = 0L
                } else {
                    lastTapTime = lastUpTime
                    lastTapPosition = lastPosition
                }
            } else {
                lastTapTime = 0L
            }
        }
    }
    .graphicsLayer {
        scaleX = state.scale
        scaleY = state.scale
        translationX = state.offset.x
        translationY = state.offset.y
        clip = true
    }

/**
 * Pinch detection for the whole PDF list.
 *
 * Only multi-touch input is consumed, on the initial pass so the scroll containers never see it.
 * A single finger is left completely alone, which keeps vertical page scrolling and horizontal
 * panning of the zoomed document working exactly as they do when the document is at 1×.
 */
private fun Modifier.pinchToZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.count { it.pressed }
            if (pressed >= 2) {
                val zoomChange = event.calculateZoom()
                if (zoomChange.isFinite() && zoomChange != 1f) {
                    onZoom(zoomChange)
                }
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

private const val MIN_PDF_ZOOM = 1f
private const val MAX_PDF_ZOOM = 4f

/**
 * Page thumbnail rail (PRD §10): lazy-rendered page previews with tap-to-jump.
 * Thumbnails render on demand exactly like main pages, so a 500-page document only
 * materialises the ones the user scrolls past.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageRailSheet(
    vm: ViewerViewModel,
    pageCount: Int,
    currentPage: Int,
    bookmarks: Set<Int>,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.viewer_thumbnails),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(pageCount, key = { it }) { page ->
                    RailThumbnail(
                        vm = vm,
                        page = page,
                        selected = page == currentPage,
                        bookmarked = page in bookmarks,
                        onClick = { onJump(page) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RailThumbnail(
    vm: ViewerViewModel,
    page: Int,
    selected: Boolean,
    bookmarked: Boolean,
    onClick: () -> Unit,
) {
    var bitmap by remember(page) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(page) { bitmap = vm.renderPageThumbnail(page) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .width(72.dp)
                .height(96.dp)
                .clip(MaterialTheme.shapes.small)
                .background(Color.White)
                .border(
                    width = if (selected) 2.dp else 0.5.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.White.copy(alpha = 0.4f)
                    },
                    shape = MaterialTheme.shapes.small,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            val current = bitmap
            if (current != null) {
                Image(
                    bitmap = current.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillWidth,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.width(36.dp),
                    color = Color.Gray,
                    trackColor = Color.LightGray.copy(alpha = 0.4f),
                )
            }
            if (bookmarked) {
                Icon(
                    Icons.Filled.Bookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(14.dp),
                )
            }
        }
        Text(
            text = (page + 1).toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Document viewer (PRD §10): lazily rendered PDF pages, zoomable images, extracted text,
 * find-in-document, bookmarks, rotation, share and print.
 *
 * Rendering is demand-driven: each page slot asks the [ViewerViewModel] for its bitmap only
 * while composed, so a 1,000-page PDF holds only the visible pages plus the LRU window
 * (PRD §35, §36).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    controller: NavHostController,
    documentId: String? = null,
    vm: ViewerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    // The reader always paints near-black chrome, even in light mode, so force light system icons.
    SystemBarAppearance(useDarkIcons = false)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var railOpen by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var jumpOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var passwordDialogOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.passwordRequired) {
        if (state.passwordRequired) passwordDialogOpen = true
    }

    LaunchedEffect(documentId) { vm.load(documentId) }

    val currentPage by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }
    val bookmarked = currentPage in state.bookmarks
    val document = state.document

    if (railOpen && state.mode == ViewerMode.PDF) {
        PageRailSheet(
            vm = vm,
            pageCount = state.pageCount,
            currentPage = currentPage,
            bookmarks = state.bookmarks,
            onDismiss = { railOpen = false },
            onJump = { page ->
                railOpen = false
                scope.launch { listState.animateScrollToItem(page) }
            },
        )
    }

    if (findOpen && state.mode == ViewerMode.PDF) {
        FindSheet(
            vm = vm,
            onDismiss = { findOpen = false },
            onJump = { hit ->
                findOpen = false
                scope.launch { listState.animateScrollToItem(hit.pageIndex) }
            },
        )
    }

    if (jumpOpen && state.mode == ViewerMode.PDF) {
        JumpToPageDialog(
            pageCount = state.pageCount,
            onDismiss = { jumpOpen = false },
            onJump = { page ->
                jumpOpen = false
                scope.launch { listState.animateScrollToItem(page) }
            },
        )
    }

    Scaffold(
        containerColor = StageColor,
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.clickable(
                            enabled = state.mode == ViewerMode.PDF,
                        ) { jumpOpen = true },
                    ) {
                        Text(
                            document?.title.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (state.mode == ViewerMode.PDF && state.pageCount > 0 && !state.passwordRequired) {
                            Text(
                                stringResource(
                                    R.string.viewer_page_of,
                                    currentPage + 1,
                                    state.pageCount,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                        }
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
                    if (state.mode == ViewerMode.PDF) {
                        IconButton(onClick = { findOpen = true }) {
                            Icon(
                                Icons.Filled.FindInPage,
                                contentDescription = stringResource(R.string.viewer_search_in_document),
                            )
                        }
                        IconButton(onClick = { vm.toggleBookmark(currentPage) }) {
                            Icon(
                                imageVector = if (bookmarked) {
                                    Icons.Filled.Bookmark
                                } else {
                                    Icons.Filled.BookmarkBorder
                                },
                                contentDescription = stringResource(R.string.viewer_bookmark),
                                tint = if (bookmarked) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.White
                                },
                            )
                        }
                        if (document != null) {
                            IconButton(
                                onClick = {
                                    controller.navigate(
                                        Screen.pdfEditorRoute(document.id, currentPage),
                                    )
                                },
                            ) {
                                Icon(
                                    Icons.Filled.EditNote,
                                    contentDescription = stringResource(R.string.viewer_edit_page),
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.action_more),
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            if (state.mode == ViewerMode.PDF && document != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.toolkit_title)) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                                    },
                                    onClick = {
                                        menuOpen = false
                                        controller.navigate(Screen.pdfToolsRoute(document.id))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.viewer_jump_to_page)) },
                                    onClick = {
                                        menuOpen = false
                                        jumpOpen = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.viewer_thumbnails)) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.ViewModule, contentDescription = null)
                                    },
                                    onClick = {
                                        menuOpen = false
                                        railOpen = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.viewer_rotate)) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.RotateRight, contentDescription = null)
                                    },
                                    onClick = {
                                        menuOpen = false
                                        vm.rotate()
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                if (state.inverted) {
                                                    R.string.viewer_night_mode_off
                                                } else {
                                                    R.string.viewer_night_mode
                                                },
                                            ),
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.InvertColors, contentDescription = null)
                                    },
                                    onClick = {
                                        menuOpen = false
                                        vm.toggleInverted()
                                    },
                                )
                                HorizontalDivider()
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_share)) },
                                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    vm.share(context)
                                },
                            )
                            if (document != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_print)) },
                                    leadingIcon = { Icon(Icons.Filled.Print, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        DocumentPrintAdapter.print(
                                            context,
                                            Uri.parse(document.uri),
                                            document.displayName,
                                        )
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ChromeColor,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
        snackbarHost = { SnackbarHost(remember { SnackbarHostState() }) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            AnimatedVisibility(
                visible = state.mode == ViewerMode.PDF && state.inverted,
                enter = expandVertically(tween(180)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(180)),
            ) {
                SuggestionBanner(stringResource(R.string.viewer_night_mode_active))
            }
            when {
                state.isLoading -> ViewerLoadingState()
                else -> when (state.mode) {
                    ViewerMode.PDF -> if (state.passwordRequired) {
                        LockedPdfState(
                            title = document?.displayName.orEmpty(),
                            onUnlock = { passwordDialogOpen = true },
                            onBack = { controller.popBackStack() },
                        )
                    } else {
                        PdfPager(
                            vm = vm,
                            pageCount = state.pageCount,
                            listState = listState,
                            rotation = state.rotation,
                            inverted = state.inverted,
                        )
                    }
                    ViewerMode.IMAGE -> ImagePage(vm = vm, inverted = state.inverted)
                    ViewerMode.TEXT -> TextPage(state = state, onTextRequested = vm::requestText)
                    ViewerMode.EXTERNAL -> ExternalPrompt(
                        title = state.document?.displayName.orEmpty(),
                        onOpen = { vm.openExternally(context) },
                        onBack = { controller.popBackStack() },
                    )
                    ViewerMode.UNAVAILABLE -> UnavailableState(
                        messageRes = state.error,
                        onRetry = vm::retry,
                        onBack = { controller.popBackStack() },
                    )
                }
            }
        }
    }

    if (state.passwordRequired && passwordDialogOpen) {
        PdfPasswordDialog(
            submitting = state.passwordSubmitting,
            errorRes = state.passwordErrorRes,
            onDismiss = { if (!state.passwordSubmitting) passwordDialogOpen = false },
            onCancel = { passwordDialogOpen = false },
            onSubmit = vm::unlock,
            onInputChanged = vm::clearPasswordError,
        )
    }
}

/** Dark-stage state shown behind the password dialog for an encrypted PDF. */
@Composable
private fun LockedPdfState(
    title: String,
    onUnlock: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.viewer_password_locked_title),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onUnlock) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.viewer_password_unlock))
            }
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.action_back), color = Color.White)
            }
        }
    }
}

/**
 * Password prompt for an encrypted PDF.
 *
 * The password stays in this dialog and is handed once to the view model; it is never written to
 * preferences or logs. Incorrect passwords leave the dialog open with an inline error so the user
 * can correct a typo without reopening the document.
 */
@Composable
private fun PdfPasswordDialog(
    submitting: Boolean,
    errorRes: Int?,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    onSubmit: (String) -> Unit,
    onInputChanged: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.viewer_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.viewer_password_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        onInputChanged()
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.viewer_password_hint)) },
                    visualTransformation = if (visible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                imageVector = if (visible) {
                                    Icons.Filled.VisibilityOff
                                } else {
                                    Icons.Filled.Visibility
                                },
                                contentDescription = stringResource(
                                    if (visible) {
                                        R.string.viewer_password_hide
                                    } else {
                                        R.string.viewer_password_show
                                    },
                                ),
                            )
                        }
                    },
                    isError = errorRes != null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (password.isNotEmpty() && !submitting) onSubmit(password)
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (errorRes != null) {
                    Text(
                        text = stringResource(errorRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(password) },
                enabled = password.isNotEmpty() && !submitting,
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.viewer_password_unlock))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !submitting) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun ViewerLoadingState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.viewer_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun SuggestionBanner(text: String) {
    Surface(color = ChromeColor) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

/**
 * Lazy vertical reader with one zoom level for the whole document.
 *
 * The zoom is expressed as the width of the whole list rather than a transform on one page:
 * every page is re-laid-out at the same larger width, so pages remain in order, never overlap,
 * and ordinary vertical scrolling keeps working. Horizontal overflow is scrollable while zoomed.
 */
@Composable
private fun PdfPager(
    vm: ViewerViewModel,
    pageCount: Int,
    listState: LazyListState,
    rotation: Int,
    inverted: Boolean,
) {
    var scale by remember(rotation) { mutableFloatStateOf(MIN_PDF_ZOOM) }
    val horizontal = rememberScrollState()
    val scope = rememberCoroutineScope()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val contentWidth = maxWidth * scale
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pinchToZoom { factor ->
                    scale = (scale * factor).coerceIn(MIN_PDF_ZOOM, MAX_PDF_ZOOM)
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontal),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .width(contentWidth)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    items(pageCount, key = { it }) { page ->
                        ZoomablePdfPage(
                            vm = vm,
                            page = page,
                            rotation = rotation,
                            inverted = inverted,
                        )
                    }
                }
            }
            if (scale > MIN_PDF_ZOOM + 0.01f) {
                AssistChip(
                    onClick = {
                        scale = MIN_PDF_ZOOM
                        scope.launch { horizontal.scrollTo(0) }
                    },
                    label = { Text(stringResource(R.string.viewer_zoom_reset)) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                )
            }
        }
    }
}

/** One PDF page; the bitmap is fetched while composed and dropped when the slot leaves. */
@Composable
private fun ZoomablePdfPage(
    vm: ViewerViewModel,
    page: Int,
    rotation: Int,
    inverted: Boolean,
) {
    var bitmap by remember(page, rotation) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(page, rotation) { mutableStateOf(false) }

    LaunchedEffect(page, rotation) {
        val result = vm.renderPage(page, 1440)
        bitmap = result.bitmap
        failed = result.failed
    }

    Box(Modifier.fillMaxWidth()) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = stringResource(R.string.a11y_page_thumbnail, page + 1),
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
                colorFilter = if (inverted) {
                    ColorFilter.colorMatrix(InvertColorMatrix)
                } else {
                    null
                },
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.707f),
                contentAlignment = Alignment.Center,
            ) {
                if (failed) {
                    Text(
                        stringResource(R.string.viewer_render_failed),
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.width(120.dp),
                        color = Color.White.copy(alpha = 0.7f),
                        trackColor = Color.White.copy(alpha = 0.15f),
                    )
                }
            }
        }
    }
}

/** Full-bleed zoomable image document. */
@Composable
private fun ImagePage(vm: ViewerViewModel, inverted: Boolean) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    val zoomState = remember { PageZoomState() }
    LaunchedEffect(Unit) {
        bitmap = vm.renderPage(0, 2048).bitmap
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pageZoom(zoomState),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = if (inverted) {
                    ColorFilter.colorMatrix(InvertColorMatrix)
                } else {
                    null
                },
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.width(120.dp), color = Color.White)
        }
    }
}

/** Reading view for text documents. */
@Composable
private fun TextPage(state: ViewerUiState, onTextRequested: () -> Unit) {
    LaunchedEffect(Unit) { onTextRequested() }
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.surface),
    ) {
        if (state.textLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        val text = state.textContent
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
            )
        } else {
            Text(
                text = stringResource(R.string.viewer_loading),
                modifier = Modifier.align(Alignment.Center),
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** Bottom sheet listing every page that contains the query. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FindSheet(
    vm: ViewerViewModel,
    onDismiss: () -> Unit,
    onJump: (PdfSearchHit) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.78f)
                .padding(horizontal = 20.dp),
        ) {
            Text(
                stringResource(R.string.viewer_search_in_document),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = vm::search,
                placeholder = { Text(stringResource(R.string.viewer_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            if (state.searchLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                val query = state.searchQuery.trim()
                when {
                    query.length < 2 -> Text(
                        stringResource(R.string.viewer_search_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.searchHits.isEmpty() -> Text(
                        stringResource(R.string.viewer_no_matches),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> Text(
                        stringResource(R.string.viewer_search_page_count, state.searchHits.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.fillMaxWidth()) {
                items(state.searchHits, key = { it.pageIndex }) { hit ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onJump(hit) }
                            .padding(vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.viewer_page_number, hit.pageIndex + 1),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.viewer_search_occurrences, hit.occurrences),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            hit.snippet,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

/** Numeric jump-to-page dialog. */
@Composable
private fun JumpToPageDialog(
    pageCount: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    val parsed = value.toIntOrNull()
    val valid = parsed != null && parsed in 1..pageCount
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.viewer_jump_to_page)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { input -> value = input.filter { it.isDigit() }.take(6) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                placeholder = { Text("1 – $pageCount") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onJump) },
                enabled = valid,
            ) { Text(stringResource(R.string.action_open)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Shown when Docora cannot render the format (PRD §5, §41). */
@Composable
private fun ExternalPrompt(
    title: String,
    onOpen: () -> Unit,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(40.dp),
            )
            Text(stringResource(R.string.error_unsupported_format), style = MaterialTheme.typography.titleMedium)
            Text(title, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AssistChip(onClick = onOpen, label = { Text(stringResource(R.string.action_open_with)) })
                AssistChip(onClick = onBack, label = { Text(stringResource(R.string.action_back)) })
            }
        }
    }
}

/** Error state with retry (PRD §41). */
@Composable
private fun UnavailableState(
    messageRes: Int?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = null,
                tint = scheme.error,
                modifier = Modifier.size(40.dp),
            )
            Text(
                stringResource(messageRes ?: R.string.error_generic_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AssistChip(onClick = onRetry, label = { Text(stringResource(R.string.action_retry)) })
                AssistChip(onClick = onBack, label = { Text(stringResource(R.string.action_back)) })
            }
        }
    }
}
