package com.vedica.labs.ind.app.docora.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.model.DocumentFilter
import com.vedica.labs.ind.app.docora.core.model.DocumentCategory
import com.vedica.labs.ind.app.docora.core.storage.DeviceDocument
import com.vedica.labs.ind.app.docora.core.util.MimeTypes
import com.vedica.labs.ind.app.docora.ui.components.DocumentGridTile
import com.vedica.labs.ind.app.docora.ui.components.DocumentRow
import com.vedica.labs.ind.app.docora.ui.components.DocumentSectionHeader
import com.vedica.labs.ind.app.docora.ui.components.DeviceDocumentRow
import com.vedica.labs.ind.app.docora.ui.home.categoryIcon
import com.vedica.labs.ind.app.docora.ui.home.categoryNameRes
import com.vedica.labs.ind.app.docora.ui.components.StoragePermissionCard
import com.vedica.labs.ind.app.docora.ui.designsystem.DocoraThemeTokens
import com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraExtendedColors
import com.vedica.labs.ind.app.docora.ui.designsystem.gridColumnsFor
import com.vedica.labs.ind.app.docora.ui.designsystem.rememberDocoraWidthClass

/**
 * The library browser (PRD §16, §23, §28).
 *
 * Two sources are shown: the Docora library (Room, instant, filterable) and, when storage
 * permission is granted, a bounded MediaStore discovery section whose files can be imported
 * with a tap. Long-press enters multi-select with bulk favourite/trash actions (PRD §28).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    controller: NavHostController,
    vm: DocumentsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snacks = remember { SnackbarHostState() }
    var tagTarget by remember { mutableStateOf<com.vedica.labs.ind.app.docora.core.model.Document?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> vm.importUris(uris.toList()) { } }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { vm.onStoragePermissionResult() }

    LaunchedEffect(message) {
        message?.let {
            snacks.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (state.selection.isEmpty()) {
                DocumentsTopBar(
                    isGrid = state.viewMode.isGrid,
                    onToggleView = vm::toggleViewMode,
                    onImport = {
                        importLauncher.launch(MimeTypes.importableMimeTypes)
                    },
                )
            } else {
                SelectionTopBar(
                    count = state.selection.size,
                    onClear = vm::clearSelection,
                    onFavorite = { vm.setBulkFavorite(true) },
                    onUnfavorite = { vm.setBulkFavorite(false) },
                    onDelete = vm::bulkTrash,
                )
            }
        },
        snackbarHost = { SnackbarHost(snacks) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SearchField(query = state.query, onQueryChange = vm::setQuery)
            FilterStrip(
                selected = state.filter,
                onSelect = vm::setFilter,
            )
            if (!state.hasStoragePermission) {
                StoragePermissionCard(
                    onAllow = { permissionLauncher.launch(vm.storagePermissions()) },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            LibraryBody(
                state = state,
                vm = vm,
                controller = controller,
                onTag = { tagTarget = it },
                onImportDevice = {
                    importLauncher.launch(MimeTypes.importableMimeTypes)
                },
            )
        }
    }
    tagTarget?.let { target ->
        TagPickerDialog(
            document = target,
            onDismiss = { tagTarget = null },
            onSelect = { category ->
                vm.setCategory(target, category)
                tagTarget = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentsTopBar(
    isGrid: Boolean,
    onToggleView: () -> Unit,
    onImport: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.documents_title)) },
        actions = {
            IconButton(onClick = onToggleView) {
                Icon(
                    imageVector = if (isGrid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                    contentDescription = stringResource(R.string.action_view_mode),
                )
            }
            IconButton(onClick = onImport) {
                Icon(
                    Icons.Filled.FileDownload,
                    contentDescription = stringResource(R.string.action_import_files),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

/** Selection context bar (PRD §28): count plus the bulk actions that are safe here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    onClear: () -> Unit,
    onFavorite: () -> Unit,
    onUnfavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = { Text(pluralDocuments(count)) },
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear_selection))
            }
        },
        actions = {
            IconButton(onClick = onFavorite) {
                Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.action_favourite))
            }
            IconButton(onClick = onUnfavorite) {
                Icon(Icons.Filled.StarBorder, contentDescription = stringResource(R.string.action_unfavourite))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
            }
        },
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
private fun pluralDocuments(count: Int): String = stringResource(R.string.documents_selected, count)

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.home_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

@Composable
private fun FilterStrip(selected: DocumentFilter, onSelect: (DocumentFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 6.dp),
    ) {
        items(DocumentFilter.entries) { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(filterLabel(filter)) },
            )
        }
    }
}

@Composable
private fun filterLabel(filter: DocumentFilter): String =
    stringResource(filterTitleRes(filter))

private fun filterTitleRes(filter: DocumentFilter): Int = when (filter) {
    DocumentFilter.ALL -> R.string.documents_filter_all
    DocumentFilter.PDF -> R.string.documents_filter_pdf
    DocumentFilter.IMAGES -> R.string.documents_filter_images
    DocumentFilter.DOCUMENTS -> R.string.documents_filter_documents
    DocumentFilter.ARCHIVES -> R.string.documents_filter_archives
    DocumentFilter.FAVORITES -> R.string.documents_filter_favourites
    DocumentFilter.NEEDS_OCR -> R.string.documents_filter_ocr_pending
}

/** Routes between the grid/list bodies and their empty states (PRD §64). */
@Composable
private fun LibraryBody(
    state: DocumentsUiState,
    vm: DocumentsViewModel,
    controller: NavHostController,
    onTag: (com.vedica.labs.ind.app.docora.core.model.Document) -> Unit,
    onImportDevice: () -> Unit,
) {
    val widthClass = rememberDocoraWidthClass()
    val columns = gridColumnsFor(state.viewMode, widthClass)

    if (state.documents.isEmpty() && state.deviceDocuments.isEmpty()) {
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            EmptyLibrary(
                hasQuery = state.query.isNotBlank(),
                onImport = onImportDevice,
                onScan = { controller.navigate(Screen.Scanner.route) },
            )
        }
        return
    }

    if (state.viewMode.isGrid) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = rememberLazyGridState(),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.documents, key = { it.id }) { document ->
                val menuItems = rowMenu(vm, document, controller, onTag = { onTag(document) })
                SelectableTile(
                    selected = state.selection.contains(document.id),
                    onLongPress = { vm.enterSelection(document.id) },
                    onClick = { openDocument(vm, controller, document.id, state.selection.isNotEmpty()) },
                ) {
                    DocumentGridTile(
                        document = document,
                        onClick = { openDocument(vm, controller, document.id, state.selection.isNotEmpty()) },
                        renderPreview = { uri, target -> vm.renderPreview(uri, target) },
                        menuItems = menuItems,
                    )
                }
            }
            if (state.deviceDocuments.isNotEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(columns) }) {
                    DeviceSection(
                        state = state,
                        vm = vm,
                        controller = controller,
                    )
                }
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.documents, key = { it.id }) { document ->
                SelectableRowWrapper(
                    selected = state.selection.contains(document.id),
                    onLongPress = { vm.enterSelection(document.id) },
                    onClick = { openDocument(vm, controller, document.id, state.selection.isNotEmpty()) },
                ) {
                    DocumentRow(
                        document = document,
                        onClick = { openDocument(vm, controller, document.id, state.selection.isNotEmpty()) },
                        renderPreview = { uri, target -> vm.renderPreview(uri, target) },
                        menuItems = rowMenu(vm, document, controller, onTag = { onTag(document) }),
                    )
                }
            }
            if (state.deviceDocuments.isNotEmpty()) {
                item(key = "device-section") {
                    DeviceSection(
                        state = state,
                        vm = vm,
                        controller = controller,
                    )
                }
            }
        }
    }
}

private fun openDocument(
    vm: DocumentsViewModel,
    controller: NavHostController,
    documentId: String,
    selecting: Boolean,
) {
    if (selecting) {
        vm.toggleSelection(documentId)
    } else {
        vm.markOpened(documentId)
        controller.navigate(Screen.viewerRoute(documentId))
    }
}

/** Long-press selection wrapper with the animated selection overlay (PRD §28). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SelectableRowWrapper(
    selected: Boolean,
    onLongPress: () -> Unit,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val extended = LocalDocoraExtendedColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        content()
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(120)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .background(extended.selectionOverlay, MaterialTheme.shapes.large),
            )
        }
    }
}

/** Grid variant of the selection wrapper. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SelectableTile(
    selected: Boolean,
    onLongPress: () -> Unit,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val extended = LocalDocoraExtendedColors.current
    Box(
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        content()
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.96f),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.96f),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(extended.selectionOverlay, MaterialTheme.shapes.large),
            )
        }
    }
}

/** Empty state with the two primary entry points (PRD §64). */
@Composable
private fun EmptyLibrary(
    hasQuery: Boolean,
    onImport: () -> Unit,
    onScan: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = scheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(
            stringResource(if (hasQuery) R.string.empty_search_title else R.string.empty_library_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            stringResource(if (hasQuery) R.string.empty_search_message else R.string.empty_library_message),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.material3.Button(onClick = onScan) {
                Text(stringResource(R.string.action_scan_document))
            }
            androidx.compose.material3.OutlinedButton(onClick = onImport) {
                Text(stringResource(R.string.action_import_files))
            }
        }
    }
}

/**
 * Overflow menu per row (PRD §27): open, tag, favourite and trash.
 *
 * [onTag] is hoisted rather than handled here because the picker is a dialog owned by the screen:
 * a menu item must not try to open a second popup from inside the menu's own composition.
 */
@Composable
private fun rowMenu(
    vm: DocumentsViewModel,
    document: com.vedica.labs.ind.app.docora.core.model.Document,
    controller: NavHostController,
    onTag: () -> Unit,
): List<com.vedica.labs.ind.app.docora.ui.components.DocumentMenuAction> = listOf(
    com.vedica.labs.ind.app.docora.ui.components.DocumentMenuAction(
        labelRes = R.string.action_details,
        icon = Icons.Filled.Description,
        onClick = { controller.navigate(Screen.detailsRoute(document.id)) },
    ),
    com.vedica.labs.ind.app.docora.ui.components.DocumentMenuAction(
        labelRes = R.string.action_tags,
        icon = Icons.AutoMirrored.Filled.Label,
        onClick = onTag,
    ),
    com.vedica.labs.ind.app.docora.ui.components.DocumentMenuAction(
        labelRes = if (document.isFavorite) R.string.action_unfavourite else R.string.action_favourite,
        icon = if (document.isFavorite) Icons.Filled.StarBorder else Icons.Filled.Star,
        onClick = { vm.toggleFavorite(document) },
    ),
    com.vedica.labs.ind.app.docora.ui.components.DocumentMenuAction(
        labelRes = R.string.action_delete,
        icon = Icons.Filled.Delete,
        tint = MaterialTheme.colorScheme.error,
        onClick = { vm.moveToTrash(document) },
    ),
)

/**
 * Category picker for one document (PRD §19).
 *
 * The list mirrors the dashboard categories, so tagging from the browser files a document under
 * exactly the same buckets the home screen counts - Work, Finance, Personal, Medical, Vehicle and
 * so on - plus an explicit "no tag" entry that undoes the assignment.
 */
@Composable
private fun TagPickerDialog(
    document: com.vedica.labs.ind.app.docora.core.model.Document,
    onDismiss: () -> Unit,
    onSelect: (DocumentCategory?) -> Unit,
) {
    val choices: List<DocumentCategory?> = DocumentCategory.dashboard + DocumentCategory.OTHER
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tag_document_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                TagOption(
                    label = stringResource(R.string.tag_document_none),
                    selected = document.category == null,
                    icon = Icons.AutoMirrored.Filled.Label,
                    onClick = { onSelect(null) },
                )
                choices.forEach { category ->
                    if (category == null) return@forEach
                    TagOption(
                        label = stringResource(categoryNameRes(category)),
                        selected = document.category == category,
                        icon = categoryIcon(category),
                        onClick = { onSelect(category) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun TagOption(
    label: String,
    selected: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        RadioButton(selected = selected, onClick = onClick)
    }
}

/** Discovery section listing files already on the device; tap imports then opens (PRD §43). */
@Composable
private fun DeviceSection(
    state: DocumentsUiState,
    vm: DocumentsViewModel,
    controller: NavHostController,
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DocumentSectionHeader(
            title = stringResource(R.string.documents_on_device),
            subtitle = stringResource(R.string.documents_on_device_hint),
        )
        if (state.deviceLoading) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
        state.deviceDocuments.take(6).forEach { device ->
            DeviceDocumentRow(
                device = device,
                onClick = { vm.openDeviceDocument(device) { controller.navigate(Screen.viewerRoute(it)) } },
                renderPreview = { uri, target -> vm.renderPreview(uri, target) },
            )
        }
    }
}
