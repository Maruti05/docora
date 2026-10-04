package com.vedica.labs.ind.app.docora.ui.screens

import android.content.Context
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.feature.print.DocumentPrintAdapter
import kotlinx.coroutines.launch

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

/** Dark stage behind PDF pages so page edges stay visible in both themes. */
private val StageColor = Color(0xFF2B2F2C)
private val ChromeColor = Color(0xF0141815)

/**
 * Document viewer (PRD §10): lazily rendered PDF pages, zoomable images, extracted text,
 * find-in-document, share and print.
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
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var railOpen by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }

    LaunchedEffect(documentId) { vm.load(documentId) }

    val currentPage by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }

    if (railOpen && state.mode == ViewerMode.PDF) {
        PageRailSheet(
            vm = vm,
            pageCount = state.pageCount,
            currentPage = currentPage,
            onDismiss = { railOpen = false },
            onJump = { page ->
                railOpen = false
                scope.launch { listState.animateScrollToItem(page) }
            },
        )
    }

    Scaffold(
        containerColor = StageColor,
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
                        if (state.mode == ViewerMode.PDF && state.pageCount > 0) {
                            Text(
                                stringResource(R.string.viewer_page_of, currentPage + 1, state.pageCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { controller.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.mode == ViewerMode.PDF) {
                        IconButton(onClick = { findOpen = !findOpen }) {
                            Icon(Icons.Filled.FindInPage, contentDescription = stringResource(R.string.viewer_search_in_document))
                        }
                    }
                    if (state.mode == ViewerMode.PDF) {
                        IconButton(onClick = { railOpen = !railOpen }) {
                            Icon(Icons.Filled.ViewModule, contentDescription = stringResource(R.string.viewer_thumbnails))
                        }
                    }
                    IconButton(onClick = { vm.share(context) }) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
                    }
                    val document = state.document
                    if (document != null) {
                        IconButton(onClick = {
                            DocumentPrintAdapter.print(
                                context,
                                Uri.parse(document.uri),
                                document.displayName,
                            )
                        }) {
                            Icon(Icons.Filled.Print, contentDescription = stringResource(R.string.action_print))
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
                visible = findOpen && state.mode == ViewerMode.PDF,
                enter = expandVertically(tween(180)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(180)),
            ) {
                FindBar(
                    query = findQuery,
                    text = state.textContent,
                    onQueryChange = { findQuery = it },
                    onDismiss = {
                        findOpen = false
                        findQuery = ""
                    },
                )
            }
            when (state.mode) {
                ViewerMode.PDF -> PdfPager(vm = vm, pageCount = state.pageCount, listState = listState)
                ViewerMode.IMAGE -> ImagePage(vm = vm)
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

/** Lazy vertical pager over PDF pages. */
@Composable
private fun PdfPager(
    vm: ViewerViewModel,
    pageCount: Int,
    listState: LazyListState,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
    ) {
        items(pageCount, key = { it }) { page ->
            ZoomablePdfPage(vm = vm, page = page)
        }
    }
}

/** One PDF page; the bitmap is fetched while composed and dropped when the slot leaves. */
@Composable
private fun ZoomablePdfPage(vm: ViewerViewModel, page: Int) {
    var bitmap by remember(page) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(page) { mutableStateOf(false) }
    var scale by remember(page) { mutableStateOf(1f) }

    LaunchedEffect(page) {
        val result = vm.renderPage(page, 1440)
        bitmap = result.bitmap
        failed = result.failed
    }

    val transformState = rememberTransformableState { zoomChange, _, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(page) {
                detectTapGestures(
                    onDoubleTap = { scale = if (scale > 1.5f) 1f else 2.5f },
                )
            }
            .transformable(transformState)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
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
private fun ImagePage(vm: ViewerViewModel) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scale by remember { mutableStateOf(1f) }
    LaunchedEffect(Unit) {
        bitmap = vm.renderPage(0, 2048).bitmap
    }
    val transformState = rememberTransformableState { zoomChange, _, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .transformable(transformState)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
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

/** Find bar counting matches over the extracted text. */
@Composable
private fun FindBar(
    query: String,
    text: String?,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(color = ChromeColor) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.FindInPage, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        stringResource(R.string.viewer_search_in_document),
                        color = Color.White.copy(alpha = 0.6f),
                    )
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
            )
            val hits = remember(query, text) {
                if (query.length < 2 || text == null) {
                    ""
                } else {
                    Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
                        .findAll(text)
                        .count()
                        .toString()
                }
            }
            Text(
                text = hits,
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_dismiss),
                    tint = Color.White,
                )
            }
        }
    }
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
