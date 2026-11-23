package com.vedica.labs.ind.app.docora.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.model.Document
import com.vedica.labs.ind.app.docora.core.pdf.PdfCompressionPreset
import com.vedica.labs.ind.app.docora.core.pdf.PdfImageOptions
import com.vedica.labs.ind.app.docora.core.pdf.PdfMetadata
import com.vedica.labs.ind.app.docora.core.pdf.PdfOrientation
import com.vedica.labs.ind.app.docora.core.pdf.PdfPageSize
import com.vedica.labs.ind.app.docora.core.util.FileSizeFormatter


/** The tools exposed by the hub (PRD §11, §46). */
private enum class PdfTool(
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector,
) {
    ORGANIZE(
        R.string.toolkit_reorder,
        R.string.toolkit_organize_summary,
        Icons.Filled.ViewModule,
    ),
    EXTRACT(
        R.string.toolkit_extract,
        R.string.toolkit_extract_summary,
        Icons.Filled.FileCopy,
    ),
    SPLIT(
        R.string.toolkit_split,
        R.string.toolkit_split_summary,
        Icons.Filled.ContentCut,
    ),
    MERGE(
        R.string.toolkit_merge,
        R.string.toolkit_merge_summary,
        Icons.Filled.MergeType,
    ),
    COMPRESS(
        R.string.toolkit_compress,
        R.string.toolkit_compress_summary,
        Icons.Filled.Archive,
    ),
    METADATA(
        R.string.toolkit_metadata,
        R.string.toolkit_metadata_summary,
        Icons.Filled.Edit,
    ),
    IMAGES(
        R.string.toolkit_images_to_pdf,
        R.string.toolkit_images_summary,
        Icons.Filled.PhotoLibrary,
    ),
    EXPORT_TEXT(
        R.string.toolkit_export_text,
        R.string.toolkit_export_text_summary,
        Icons.Filled.Description,
    ),
}

/**
 * PDF toolkit hub (PRD §11, §46).
 *
 * Each tool opens in a bottom sheet over the same screen so an operation never loses the
 * selected document. Results are new library documents written by [PdfToolsViewModel]; the
 * original is never modified.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    controller: NavHostController,
    documentId: String? = null,
    vm: PdfToolsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snacks = remember { SnackbarHostState() }
    var activeTool by remember { mutableStateOf<PdfTool?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is PdfToolsEvent.Message -> snacks.showSnackbar(event.text)
                is PdfToolsEvent.Open -> controller.navigate(Screen.viewerRoute(event.documentId))
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.toolkit_title)) },
                navigationIcon = {
                    IconButton(onClick = { controller.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snacks) },
    ) { padding ->
        val document = state.document
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.errorRes != null || document == null -> ToolsError(
                messageRes = state.errorRes ?: R.string.error_document_missing,
                onBack = { controller.popBackStack() },
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { ToolsHero(document = document, pageCount = state.pageCount) }
                item { SectionLabel(stringResource(R.string.toolkit_section_pages)) }
                listItems(
                    listOf(
                        PdfTool.ORGANIZE,
                        PdfTool.EXTRACT,
                        PdfTool.SPLIT,
                        PdfTool.MERGE,
                    ),
                    key = { it.name },
                ) { tool ->
                    ToolCard(tool = tool, onClick = { activeTool = tool })
                }
                item { SectionLabel(stringResource(R.string.toolkit_section_convert)) }
                listItems(
                    listOf(PdfTool.COMPRESS, PdfTool.METADATA, PdfTool.IMAGES, PdfTool.EXPORT_TEXT),
                    key = { it.name },
                ) { tool ->
                    ToolCard(tool = tool, onClick = { activeTool = tool })
                }
            }
        }
    }

    val tool = activeTool
    if (tool != null && state.document != null) {
        ModalBottomSheet(
            onDismissRequest = { if (!state.busy) activeTool = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            when (tool) {
                PdfTool.ORGANIZE -> OrganizeSheet(vm, state, onDismiss = { activeTool = null })
                PdfTool.EXTRACT -> ExtractSheet(vm, state, onDismiss = { activeTool = null })
                PdfTool.SPLIT -> SplitSheet(vm, state, onDismiss = { activeTool = null })
                PdfTool.MERGE -> MergeSheet(vm, state, onDismiss = { activeTool = null })
                PdfTool.COMPRESS -> CompressSheet(vm, state)
                PdfTool.METADATA -> MetadataSheet(vm, state)
                PdfTool.IMAGES -> ImagesToPdfSheet(vm, state)
                PdfTool.EXPORT_TEXT -> ExportTextSheet(
                    vm = vm,
                    state = state,
                    onDismiss = { activeTool = null },
                )
            }
        }
    }
}

@Composable
private fun ToolsHero(document: Document, pageCount: Int) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Archive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    document.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.toolkit_pages_and_size,
                        pluralStringResource(R.plurals.plural_pages, pageCount, pageCount),
                        FileSizeFormatter.format(document.sizeBytes),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun ToolCard(tool: PdfTool, onClick: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.shapes.medium,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(tool.titleRes), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(tool.subtitleRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrganizeSheet(
    vm: PdfToolsViewModel,
    state: PdfToolsUiState,
    onDismiss: () -> Unit,
) {
    var outputName by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .padding(horizontal = 20.dp),
    ) {
        Text(stringResource(R.string.toolkit_reorder), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.toolkit_organize_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                pluralStringResource(R.plurals.plural_pages, state.pages.size, state.pages.size),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { vm.rotateAll(-90) },
                enabled = !state.busy,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Filled.RotateLeft,
                    contentDescription = stringResource(R.string.toolkit_rotate_all_left),
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = { vm.rotateAll(90) },
                enabled = !state.busy,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Filled.RotateRight,
                    contentDescription = stringResource(R.string.toolkit_rotate_all_right),
                    modifier = Modifier.size(18.dp),
                )
            }
            TextButton(onClick = vm::resetPages, enabled = !state.busy) {
                Text(stringResource(R.string.action_reset))
            }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            gridItems(state.pages, key = { it.id }) { page ->
                EditablePageCard(vm = vm, page = page, enabled = !state.busy)
            }
        }
        OutlinedTextField(
            value = outputName,
            onValueChange = { outputName = it },
            label = { Text(stringResource(R.string.toolkit_output_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SheetActions(
            busy = state.busy,
            enabled = state.pages.isNotEmpty(),
            onDismiss = onDismiss,
            onConfirm = { vm.saveOrganized(outputName) },
        )
    }
}

@Composable
private fun EditablePageCard(
    vm: PdfToolsViewModel,
    page: PdfToolPage,
    enabled: Boolean,
) {
    var bitmap by remember(page.id, page.rotationDegrees) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(page.id, page.rotationDegrees) {
        bitmap = vm.pageThumbnail(page)
    }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.74f)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                val current = bitmap
                if (current != null) {
                    Image(
                        bitmap = current.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                ) {
                    Text(
                        text = (page.sourceIndex + 1).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (page.rotationDegrees != 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    ) {
                        Text(
                            text = "${page.rotationDegrees}°",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PageAction(Icons.Filled.RotateLeft, R.string.toolkit_rotate_left, enabled) {
                    vm.rotatePage(page.id, -90)
                }
                PageAction(Icons.Filled.RotateRight, R.string.toolkit_rotate_right, enabled) {
                    vm.rotatePage(page.id, 90)
                }
                PageAction(Icons.Filled.ArrowUpward, R.string.scan_move_earlier, enabled) {
                    vm.movePage(page.id, -1)
                }
                PageAction(Icons.Filled.ArrowDownward, R.string.scan_move_later, enabled) {
                    vm.movePage(page.id, 1)
                }
                PageAction(Icons.Filled.Delete, R.string.scan_delete_page, enabled) {
                    vm.deletePage(page.id)
                }
            }
        }
    }
}

@Composable
private fun PageAction(
    icon: ImageVector,
    descriptionRes: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(32.dp)) {
        Icon(
            icon,
            contentDescription = stringResource(descriptionRes),
            modifier = Modifier.size(17.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtractSheet(
    vm: PdfToolsViewModel,
    state: PdfToolsUiState,
    onDismiss: () -> Unit,
) {
    RangeSheetContent(
        titleRes = R.string.toolkit_extract,
        hintRes = R.string.toolkit_extract_hint,
        busy = state.busy,
        onDismiss = onDismiss,
        onConfirm = vm::extractPages,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SplitSheet(
    vm: PdfToolsViewModel,
    state: PdfToolsUiState,
    onDismiss: () -> Unit,
) {
    var byRanges by remember { mutableStateOf(true) }
    var ranges by remember { mutableStateOf("") }
    var everyPages by remember { mutableStateOf("10") }
    var outputName by remember { mutableStateOf("") }
    val chunk = everyPages.toIntOrNull()
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(stringResource(R.string.toolkit_split), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.toolkit_split_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = byRanges,
                onClick = { byRanges = true },
                label = { Text(stringResource(R.string.toolkit_split_by_ranges)) },
            )
            FilterChip(
                selected = !byRanges,
                onClick = { byRanges = false },
                label = { Text(stringResource(R.string.toolkit_split_every)) },
            )
        }
        Spacer(Modifier.height(10.dp))
        if (byRanges) {
            OutlinedTextField(
                value = ranges,
                onValueChange = { ranges = it },
                label = { Text(stringResource(R.string.toolkit_pages_hint)) },
                placeholder = { Text("1-3, 5, 8-10") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                value = everyPages,
                onValueChange = { input ->
                    everyPages = input.filter { it.isDigit() }.take(4)
                },
                label = { Text(stringResource(R.string.toolkit_split_every)) },
                placeholder = { Text("10") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = outputName,
            onValueChange = { outputName = it },
            label = { Text(stringResource(R.string.toolkit_output_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
        SheetActions(
            busy = state.busy,
            enabled = if (byRanges) ranges.isNotBlank() else (chunk ?: 0) > 0,
            onDismiss = onDismiss,
            onConfirm = {
                if (byRanges) {
                    vm.split(ranges, outputName)
                } else {
                    vm.splitEvery(chunk ?: 1, outputName)
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeSheetContent(
    titleRes: Int,
    hintRes: Int,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var ranges by remember { mutableStateOf("") }
    var outputName by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(stringResource(titleRes), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(hintRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = ranges,
            onValueChange = { ranges = it },
            label = { Text(stringResource(R.string.toolkit_pages_hint)) },
            placeholder = { Text("1-3, 5, 8-10") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = outputName,
            onValueChange = { outputName = it },
            label = { Text(stringResource(R.string.toolkit_output_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
        SheetActions(
            busy = busy,
            enabled = ranges.isNotBlank(),
            onDismiss = onDismiss,
            onConfirm = { onConfirm(ranges, outputName) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MergeSheet(
    vm: PdfToolsViewModel,
    state: PdfToolsUiState,
    onDismiss: () -> Unit,
) {
    var outputName by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> if (uris.isNotEmpty()) vm.importExternalPdfs(uris) }
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .padding(horizontal = 20.dp),
    ) {
        Text(stringResource(R.string.toolkit_merge), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.toolkit_merge_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val selectedPdfs = state.mergeSelection.mapNotNull { id ->
            state.libraryPdfs.firstOrNull { it.id == id }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(
                    R.string.toolkit_merge_total,
                    selectedPdfs.size,
                    selectedPdfs.sumOf { it.pageCount ?: 0 },
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = vm::selectAllMerge, enabled = !state.busy) {
                Text(stringResource(R.string.action_select_all))
            }
            TextButton(
                onClick = vm::clearMerge,
                enabled = !state.busy && state.mergeSelection.isNotEmpty(),
            ) {
                Text(stringResource(R.string.action_clear_selection))
            }
        }
        TextButton(
            onClick = { picker.launch(arrayOf("application/pdf")) },
            enabled = !state.busy,
            modifier = Modifier.align(Alignment.Start),
        ) {
            Text(stringResource(R.string.toolkit_add_from_device))
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.libraryPdfs.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.toolkit_merge_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 4.dp)) {
                listItems(state.libraryPdfs, key = { it.id }) { pdf ->
                    val position = state.mergeSelection.indexOf(pdf.id)
                    val selected = position >= 0
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !state.busy) { vm.toggleMerge(pdf.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { vm.toggleMerge(pdf.id) },
                            enabled = !state.busy,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                pdf.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                FileSizeFormatter.format(pdf.sizeBytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (selected) {
                            Text(
                                "#${position + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            IconButton(
                                onClick = { vm.moveMerge(pdf.id, -1) },
                                enabled = !state.busy,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Filled.ArrowUpward,
                                    contentDescription = stringResource(R.string.scan_move_earlier),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(
                                onClick = { vm.moveMerge(pdf.id, 1) },
                                enabled = !state.busy,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Filled.ArrowDownward,
                                    contentDescription = stringResource(R.string.scan_move_later),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
        OutlinedTextField(
            value = outputName,
            onValueChange = { outputName = it },
            label = { Text(stringResource(R.string.toolkit_output_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SheetActions(
            busy = state.busy,
            enabled = state.mergeSelection.size >= 2,
            onDismiss = onDismiss,
            onConfirm = { vm.merge(outputName) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompressSheet(
    vm: PdfToolsViewModel,
    state: PdfToolsUiState,
) {
    val document = state.document ?: return
    var preset by remember { mutableStateOf(PdfCompressionPreset.BALANCED) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(stringResource(R.string.toolkit_compress), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.toolkit_compress_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(
                R.string.toolkit_current_size,
                FileSizeFormatter.format(document.sizeBytes),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(
                R.string.toolkit_estimated_size,
                FileSizeFormatter.format((document.sizeBytes * preset.estimatedRatio).toLong()),
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        PdfCompressionPreset.entries.forEach { option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.busy) { preset = option }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = preset == option,
                    onClick = { preset = option },
                    enabled = !state.busy,
                )
                Text(compressionLabel(option), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(onClick = { vm.compress(preset) }, enabled = !state.busy) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetadataSheet(
    vm: PdfToolsViewModel,
    state: PdfToolsUiState,
) {
    var title by remember(state.metadata) { mutableStateOf(state.metadata.title) }
    var author by remember(state.metadata) { mutableStateOf(state.metadata.author) }
    var subject by remember(state.metadata) { mutableStateOf(state.metadata.subject) }
    var keywords by remember(state.metadata) { mutableStateOf(state.metadata.keywords) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(stringResource(R.string.toolkit_metadata), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.toolkit_metadata_title)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = author,
            onValueChange = { author = it },
            label = { Text(stringResource(R.string.toolkit_metadata_author)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = subject,
            onValueChange = { subject = it },
            label = { Text(stringResource(R.string.toolkit_metadata_subject)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = keywords,
            onValueChange = { keywords = it },
            label = { Text(stringResource(R.string.toolkit_metadata_keywords)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = {
                    vm.saveMetadata(
                        PdfMetadata(
                            title = title,
                            author = author,
                            subject = subject,
                            keywords = keywords,
                        ),
                    )
                },
                enabled = !state.busy,
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImagesToPdfSheet(
    vm: PdfToolsViewModel,
    state: PdfToolsUiState,
) {
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pageSize by remember { mutableStateOf(PdfPageSize.A4) }
    var orientation by remember { mutableStateOf(PdfOrientation.AUTO) }
    var quality by remember { mutableStateOf(85f) }
    var outputName by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { picked -> images = picked }

    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .padding(horizontal = 20.dp),
    ) {
        Text(
            stringResource(R.string.toolkit_images_to_pdf),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            stringResource(R.string.toolkit_images_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { picker.launch(arrayOf("image/*")) },
                enabled = !state.busy,
            ) { Text(stringResource(R.string.toolkit_add_images)) }
            Spacer(Modifier.width(12.dp))
            Text(
                if (images.isEmpty()) {
                    stringResource(R.string.toolkit_no_images)
                } else {
                    pluralStringResource(R.plurals.plural_pages, images.size, images.size)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        Column(Modifier.weight(1f).fillMaxWidth()) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.toolkit_page_size), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PdfPageSize.entries.forEach { option ->
                    FilterChip(
                        selected = pageSize == option,
                        onClick = { pageSize = option },
                        label = { Text(pageSizeLabel(option)) },
                        enabled = !state.busy,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.toolkit_orientation), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PdfOrientation.entries.forEach { option ->
                    FilterChip(
                        selected = orientation == option,
                        onClick = { orientation = option },
                        label = { Text(orientationLabel(option)) },
                        enabled = !state.busy,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.toolkit_quality) + " · ${quality.toInt()}",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = quality,
                onValueChange = { quality = it },
                valueRange = 30f..100f,
                enabled = !state.busy,
            )
        }
        OutlinedTextField(
            value = outputName,
            onValueChange = { outputName = it },
            label = { Text(stringResource(R.string.toolkit_output_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = {
                    vm.imagesToPdf(
                        images = images,
                        options = PdfImageOptions(
                            pageSize = pageSize,
                            orientation = orientation,
                            jpegQuality = quality.toInt(),
                        ),
                        outputName = outputName,
                    )
                },
                enabled = images.isNotEmpty() && !state.busy,
            ) { Text(stringResource(R.string.action_create)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportTextSheet(
    vm: PdfToolsViewModel,
    state: PdfToolsUiState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.loadExportText() }
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .padding(horizontal = 20.dp),
    ) {
        Text(
            stringResource(R.string.toolkit_export_text),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            stringResource(R.string.toolkit_export_text_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        if (state.exportTextLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        val text = state.exportText
        if (text.isNullOrBlank()) {
            Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (!state.exportTextLoading) {
                    Text(
                        stringResource(R.string.toolkit_export_text_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 1.dp,
            ) {
                SelectionContainer {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = { copyText(context, text) },
                enabled = !text.isNullOrBlank(),
            ) { Text(stringResource(R.string.pdf_editor_copy)) }
            TextButton(
                onClick = { shareText(context, text) },
                enabled = !text.isNullOrBlank(),
            ) { Text(stringResource(R.string.action_share)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
        }
    }
}

private fun copyText(context: Context, text: String?) {
    if (text.isNullOrBlank()) return
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(
        ClipData.newPlainText(context.getString(R.string.pdf_editor_clip_label), text),
    )
}

private fun shareText(context: Context, text: String?) {
    if (text.isNullOrBlank()) return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

@Composable
private fun SheetActions(
    busy: Boolean,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onDismiss, enabled = !busy) {
            Text(stringResource(R.string.action_cancel))
        }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onConfirm, enabled = enabled && !busy) {
            Text(stringResource(R.string.action_save))
        }
    }
}

@Composable
private fun ToolsError(
    messageRes: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
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
private fun compressionLabel(preset: PdfCompressionPreset): String = when (preset) {
    PdfCompressionPreset.ORIGINAL -> stringResource(R.string.toolkit_compression_original)
    PdfCompressionPreset.HIGH -> stringResource(R.string.toolkit_compression_high)
    PdfCompressionPreset.BALANCED -> stringResource(R.string.toolkit_compression_balanced)
    PdfCompressionPreset.SMALL -> stringResource(R.string.toolkit_compression_small)
}

@Composable
private fun pageSizeLabel(size: PdfPageSize): String = when (size) {
    PdfPageSize.A4 -> stringResource(R.string.toolkit_page_size_a4)
    PdfPageSize.LETTER -> stringResource(R.string.toolkit_page_size_letter)
    PdfPageSize.FIT -> stringResource(R.string.toolkit_page_size_fit)
}

@Composable
private fun orientationLabel(orientation: PdfOrientation): String = when (orientation) {
    PdfOrientation.PORTRAIT -> stringResource(R.string.toolkit_orientation_portrait)
    PdfOrientation.LANDSCAPE -> stringResource(R.string.toolkit_orientation_landscape)
    PdfOrientation.AUTO -> stringResource(R.string.toolkit_orientation_auto)
}
