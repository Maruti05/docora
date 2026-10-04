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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.model.DocumentCategory
import com.vedica.labs.ind.app.docora.core.model.DocumentFilter
import com.vedica.labs.ind.app.docora.core.model.DocumentSort
import com.vedica.labs.ind.app.docora.core.model.SortDirection
import com.vedica.labs.ind.app.docora.core.model.SortOrder
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snacks = remember { SnackbarHostState() }
    var tagTarget by remember { mutableStateOf<com.vedica.labs.ind.app.docora.core.model.Document?>(null) }
    var showSortSheet by remember { mutableStateOf(false) }
    // Hoisted so show/hide animations are not restarted by recomposition, and so a sort tap can
    // hide the sheet before the sort (and its list reshuffle) is applied.
    val sortSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sortSheetScope = rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> vm.importUris(uris.toList()) { } }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { vm.onStoragePermissionResult() }

    // "All files access" (Android 11+) is granted on a system screen, so the grant only becomes
    // visible when the app returns to the foreground: re-check on resume and pull the documents in.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.onStoragePermissionResult()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // One entry point for "let Docora see this device": the system settings page where the platform
    // requires it (Android 11+), the runtime dialog otherwise.
    val requestStorageAccess: () -> Unit = {
        if (vm.requiresAllFilesAccess()) vm.openAllFilesAccessSettings()
        else permissionLauncher.launch(vm.storagePermissions())
    }

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
                    isRefreshing = state.syncing,
                    onSortClick = { showSortSheet = true },
                    onToggleView = vm::toggleViewMode,
                    onRefresh = {
                        if (vm.hasStoragePermission()) {
                            vm.refreshDeviceDocuments(reportWhenUnchanged = true)
                        } else {
                            requestStorageAccess()
                        }
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
                    onAllow = requestStorageAccess,
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
    if (showSortSheet) {
        SortBottomSheet(
            current = state.sort,
            sheetState = sortSheetState,
            onSelect = { sort ->
                // Hide first, then sort: applying the sort while the sheet is still visible
                // reshuffles the list behind it and makes the dismiss animation stutter/jump.
                sortSheetScope.launch {
                    runCatching { sortSheetState.hide() }.getOrDefault(false)
                    showSortSheet = false
                    vm.setSort(sort)
                }
            },
            onDismiss = { showSortSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentsTopBar(
    isGrid: Boolean,
    isRefreshing: Boolean,
    onSortClick: () -> Unit,
    onToggleView: () -> Unit,
    onRefresh: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.documents_title)) },
        actions = {
            IconButton(onClick = onSortClick) {
                Icon(
                    Icons.AutoMirrored.Filled.Sort,
                    contentDescription = stringResource(R.string.action_sort),
                )
            }
            IconButton(onClick = onToggleView) {
                Icon(
                    imageVector = if (isGrid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                    contentDescription = stringResource(R.string.action_view_mode),
                )
            }
            // Refresh re-indexes the device: it replaces the old import shortcut, because a device
            // document that is picked up by the scan appears in the library without a second step.
            IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.action_refresh),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
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

/** Sort picker for the Documents screen: one tap picks a field + direction preset (PRD §16). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortBottomSheet(
    current: DocumentSort,
    sheetState: androidx.compose.material3.SheetState,
    onSelect: (DocumentSort) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Wrap content: no fillMaxHeight/fillMaxSize — the sheet sizes to its content and
        // never stretches up to the status bar. The list is capped (heightIn) so it only
        // scrolls when the screen is too short, instead of forcing a near-fullscreen sheet.
        dragHandle = null,
    ) {
        // One scroll container owned by the sheet: the drag gesture scrolls this list, and
        // flinging past the top dismisses the sheet — no nested scrollables to fight it.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            // Fixed drag handle + title: they never scroll away, so the dismiss target is
            // always visible and the sheet never remeasures while dragging.
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(4.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                )
                Text(
                    text = stringResource(R.string.documents_sort_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
            // LazyColumn (not Column+verticalScroll): ModalBottomSheet wires a LazyColumn into
            // its nested-scroll connection, so a swipe-down at the top of the list dismisses the
            // sheet in one smooth motion instead of bouncing between two competing scrollers.
            // heightIn cap: on tall screens everything fits and the sheet wraps; on short
            // screens only the list scrolls while the handle + title stay fixed.
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
            item(key = "header_recent") {
            Text(
                text = stringResource(R.string.documents_sort_recent),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
            )
            }
            item(key = "sort_newest") {
            SortOptionRow(
                label = stringResource(R.string.documents_sort_newest),
                supporting = stringResource(R.string.sort_date_modified),
                icon = Icons.Filled.Schedule,
                selected = current == DocumentSort(SortOrder.DATE_MODIFIED, SortDirection.DESCENDING),
                onClick = { onSelect(DocumentSort(SortOrder.DATE_MODIFIED, SortDirection.DESCENDING)) },
            )
            }
            item(key = "sort_oldest") {
            SortOptionRow(
                label = stringResource(R.string.documents_sort_oldest),
                supporting = stringResource(R.string.sort_date_modified),
                icon = Icons.Filled.History,
                selected = current == DocumentSort(SortOrder.DATE_MODIFIED, SortDirection.ASCENDING),
                onClick = { onSelect(DocumentSort(SortOrder.DATE_MODIFIED, SortDirection.ASCENDING)) },
            )
            }
            item(key = "sort_recent") {
            SortOptionRow(
                label = stringResource(R.string.documents_sort_recently_opened),
                supporting = stringResource(R.string.sort_last_opened),
                icon = Icons.Filled.Today,
                selected = current == DocumentSort(SortOrder.LAST_OPENED, SortDirection.DESCENDING),
                onClick = { onSelect(DocumentSort(SortOrder.LAST_OPENED, SortDirection.DESCENDING)) },
            )
            }
            item(key = "div1") {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            item(key = "header_name") {
            Text(
                text = stringResource(R.string.documents_sort_name),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 2.dp),
            )
            }
            item(key = "sort_az") {
            SortOptionRow(
                label = stringResource(R.string.documents_sort_a_to_z),
                supporting = stringResource(R.string.sort_name),
                icon = Icons.Filled.SortByAlpha,
                selected = current == DocumentSort(SortOrder.NAME, SortDirection.ASCENDING),
                onClick = { onSelect(DocumentSort(SortOrder.NAME, SortDirection.ASCENDING)) },
            )
            }
            item(key = "sort_za") {
            SortOptionRow(
                label = stringResource(R.string.documents_sort_z_to_a),
                supporting = stringResource(R.string.sort_name),
                icon = Icons.Filled.SortByAlpha,
                selected = current == DocumentSort(SortOrder.NAME, SortDirection.DESCENDING),
                onClick = { onSelect(DocumentSort(SortOrder.NAME, SortDirection.DESCENDING)) },
            )
            }
            item(key = "div2") {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            item(key = "header_size") {
            Text(
                text = stringResource(R.string.documents_sort_size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 2.dp),
            )
            }
            item(key = "sort_small") {
            SortOptionRow(
                label = stringResource(R.string.documents_sort_smallest),
                supporting = stringResource(R.string.sort_size),
                icon = Icons.Filled.Storage,
                selected = current == DocumentSort(SortOrder.SIZE, SortDirection.ASCENDING),
                onClick = { onSelect(DocumentSort(SortOrder.SIZE, SortDirection.ASCENDING)) },
            )
            }
            item(key = "sort_large") {
            SortOptionRow(
                label = stringResource(R.string.documents_sort_largest),
                supporting = stringResource(R.string.sort_size),
                icon = Icons.Filled.Storage,
                selected = current == DocumentSort(SortOrder.SIZE, SortDirection.DESCENDING),
                onClick = { onSelect(DocumentSort(SortOrder.SIZE, SortDirection.DESCENDING)) },
            )
            }
            item(key = "div3") {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            item(key = "header_adv") {
            Text(
                text = stringResource(R.string.documents_sort_advanced),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 2.dp),
            )
            }
            item(key = "sort_created") {
            SortOptionRow(
                label = stringResource(R.string.sort_date_created),
                supporting = stringResource(R.string.documents_sort_created_hint),
                icon = Icons.Filled.Today,
                selected = current.order == SortOrder.DATE_CREATED,
                onClick = {
                    val direction = if (current.order == SortOrder.DATE_CREATED) {
                        current.reversed().direction
                    } else {
                        SortDirection.DESCENDING
                    }
                    onSelect(DocumentSort(SortOrder.DATE_CREATED, direction))
                },
                trailingDirection = current.order == SortOrder.DATE_CREATED,
            )
            }
            item(key = "sort_type") {
            SortOptionRow(
                label = stringResource(R.string.sort_type),
                supporting = stringResource(R.string.documents_sort_type_hint),
                icon = Icons.Filled.Description,
                selected = current.order == SortOrder.TYPE,
                onClick = {
                    val direction = if (current.order == SortOrder.TYPE) {
                        current.reversed().direction
                    } else {
                        SortDirection.ASCENDING
                    }
                    onSelect(DocumentSort(SortOrder.TYPE, direction))
                },
                trailingDirection = current.order == SortOrder.TYPE,
            )
            }
            }
        }
    }
}

@Composable
private fun SortOptionRow(
    label: String,
    supporting: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    trailingDirection: Boolean = false,
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
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailingDirection) {
            Icon(
                Icons.Filled.SwapVert,
                contentDescription = stringResource(R.string.documents_sort_direction),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

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
        // The overlay must cover the *entire* card (thumbnail + title + meta). matchParentSize
        // is only honoured when applied to a direct child of this Box, so it goes on the
        // AnimatedVisibility node itself (the direct child) and the inner Box just fills it.
        // Applying matchParentSize to a Box nested inside AnimatedVisibility's own layout did
        // not size the tint to the parent, which left the meta line (type • size) untinted.
        AnimatedVisibility(
            visible = selected,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(120)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
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
        // Same direct-child rule as SelectableRowWrapper: matchParentSize has to sit on the
        // AnimatedVisibility node so the tile tint spans the whole card instead of stopping
        // short of the file-type / file-size meta line.
        AnimatedVisibility(
            visible = selected,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.96f),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.96f),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
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
