package com.vedica.labs.ind.app.docora.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.datastore.PreferencesManager
import com.vedica.labs.ind.app.docora.core.handler.DocumentHandlerRegistry
import com.vedica.labs.ind.app.docora.core.model.Document
import com.vedica.labs.ind.app.docora.core.model.DocumentCategory
import com.vedica.labs.ind.app.docora.core.model.DocumentFilter
import com.vedica.labs.ind.app.docora.core.model.DocumentImportRequest
import com.vedica.labs.ind.app.docora.core.model.DocumentSort
import com.vedica.labs.ind.app.docora.core.model.DocumentSource
import com.vedica.labs.ind.app.docora.core.model.DocumentType
import com.vedica.labs.ind.app.docora.core.model.SmartCollection
import com.vedica.labs.ind.app.docora.core.model.SortDirection
import com.vedica.labs.ind.app.docora.core.model.SortOrder
import com.vedica.labs.ind.app.docora.core.model.ViewMode
import com.vedica.labs.ind.app.docora.core.repository.DocumentRepository
import com.vedica.labs.ind.app.docora.core.storage.DeviceDocument
import com.vedica.labs.ind.app.docora.core.storage.DeviceDocumentsSource
import com.vedica.labs.ind.app.docora.core.storage.DeviceLibrarySynchronizer
import com.vedica.labs.ind.app.docora.core.storage.SafGateway
import com.vedica.labs.ind.app.docora.core.util.MimeTypes
import com.vedica.labs.ind.app.docora.ui.home.categoryNameRes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Immutable browser state: the library window plus the device discovery section. */
data class DocumentsUiState(
    val query: String = "",
    val filter: DocumentFilter = DocumentFilter.ALL,
    val sort: DocumentSort = DocumentSort.Default,
    val viewMode: ViewMode = ViewMode.default,
    val documents: List<Document> = emptyList(),
    val deviceDocuments: List<DeviceDocument> = emptyList(),
    val hasStoragePermission: Boolean = false,
    val deviceLoading: Boolean = false,
    val isLoading: Boolean = true,
    /** True while a device scan/import pass is running. */
    val syncing: Boolean = false,
    /** True when the library window is full, i.e. more documents can still be pulled in. */
    val hasMore: Boolean = false,
    /** Ids currently selected via long-press (PRD §28). */
    val selection: Set<String> = emptySet(),
)

/**
 * Browser logic for the Documents screen.
 *
 * The library window is a database flow (collection + filter + name search, debounced so a
 * keystroke does not fire a query per character) that grows page by page as the user scrolls;
 * the "On this device" section is the leftover of the automatic device scan - every file Docora
 * has not imported yet - so the section doubles as an inbox for new files.
 *
 * A scan runs on first open (and again whenever the storage permission is granted), which is
 * what makes the library reflect the device without any manual importing.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class DocumentsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentRepository: DocumentRepository,
    private val safGateway: SafGateway,
    private val deviceDocumentsSource: DeviceDocumentsSource,
    private val deviceLibrarySynchronizer: DeviceLibrarySynchronizer,
    private val handlerRegistry: DocumentHandlerRegistry,
    private val preferencesManager: PreferencesManager,
) : ViewModel() {

    private data class Controls(val query: String, val filter: DocumentFilter)

    private val controls = MutableStateFlow(Controls("", DocumentFilter.ALL))
    private val sortFlow = MutableStateFlow(DocumentSort.Default)
    private val viewModeFlow = MutableStateFlow(ViewMode.default)
    private val permissionFlow = MutableStateFlow(deviceDocumentsSource.hasPermission())
    private val deviceFlow = MutableStateFlow<List<DeviceDocument>>(emptyList())
    private val deviceLoadingFlow = MutableStateFlow(false)
    private val selectionFlow = MutableStateFlow<Set<String>>(emptySet())
    private val messageFlow = MutableStateFlow<String?>(null)
    private val syncingFlow = MutableStateFlow(false)

    /** How many documents the library window currently holds; [loadMore] grows it. */
    private val windowFlow = MutableStateFlow(DocumentRepository.DEFAULT_PAGE_SIZE)

    private val libraryFlow = combine(controls.debounce(200), windowFlow, sortFlow) { c, window, sort ->
        Triple(c, window, sort)
    }.flatMapLatest { (c, window, sort) ->
            documentRepository.observeCollection(
                collection = SmartCollection.ALL,
                filter = c.filter,
                sort = sort,
                searchQuery = c.query.trim().ifBlank { null },
                limit = window,
            )
        }

    /** Device section + layout, folded into one value so `combine` stays within its arity. */
    private data class BrowserMeta(
        val device: List<DeviceDocument>,
        val permission: Boolean,
        val deviceLoading: Boolean,
        val viewMode: ViewMode,
        val selection: Set<String>,
    )

    private val metaFlow = combine(
        deviceFlow,
        permissionFlow,
        deviceLoadingFlow,
        viewModeFlow,
        selectionFlow,
    ) { device, permission, deviceLoading, viewMode, selection ->
        BrowserMeta(device, permission, deviceLoading, viewMode, selection)
    }

    val state: StateFlow<DocumentsUiState> = combine(
        libraryFlow,
        controls,
        metaFlow,
        documentRepository.observeKnownUris(),
        syncingFlow,
        sortFlow,
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val documents = args[0] as List<Document>
        val c = args[1] as Controls
        val meta = args[2] as BrowserMeta
        @Suppress("UNCHECKED_CAST")
        val knownUris = args[3] as Set<String>
        val syncing = args[4] as Boolean
        val sort = args[5] as DocumentSort
        val query = c.query.trim()
        // Anything already imported leaves the device inbox, so the two sections never list the
        // same file twice and the inbox shrinks as the scan works through the device.
        val inbox = meta.device.filterNot { it.uri in knownUris }
        val filteredInbox = if (query.isEmpty()) {
            inbox
        } else {
            inbox.filter { it.displayName.contains(query, ignoreCase = true) }
        }
        DocumentsUiState(
            query = c.query,
            filter = c.filter,
            sort = sort,
            viewMode = meta.viewMode,
            documents = documents,
            deviceDocuments = sortDeviceDocuments(filteredInbox, sort),
            hasStoragePermission = meta.permission,
            deviceLoading = meta.deviceLoading,
            isLoading = false,
            syncing = syncing,
            // A full window means SQLite may still be holding rows back.
            hasMore = documents.size >= windowFlow.value && windowFlow.value < MAX_WINDOW,
            selection = meta.selection,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DocumentsUiState(),
    )

    /** One-off feedback messages (snackbars), cleared by the screen after display. */
    val message: StateFlow<String?> = messageFlow

    init {
        viewModelScope.launch {
            val settings = preferencesManager.settings.first()
            viewModeFlow.value = settings.defaultViewMode
            sortFlow.value = DocumentSort(
                order = settings.defaultSortOrder,
                direction = settings.defaultSortDirection,
            )
            if (deviceDocumentsSource.hasPermission()) refreshDeviceDocuments()
        }
    }

    // ------------------------------------------------------------------ controls

    fun setQuery(query: String) {
        // A new query is a new result set, so the paging window restarts at the first page.
        windowFlow.value = DocumentRepository.DEFAULT_PAGE_SIZE
        controls.value = controls.value.copy(query = query)
    }

    fun setFilter(filter: DocumentFilter) {
        windowFlow.value = DocumentRepository.DEFAULT_PAGE_SIZE
        controls.value = controls.value.copy(filter = filter)
    }

    /**
     * Applies a new sort order from the Documents sort sheet (PRD §16).
     *
     * A new sort is a new result set, so the paging window restarts; the choice is also saved
     * as the default sort so the library, folders and restarts keep using it.
     */
    fun setSort(sort: DocumentSort) {
        if (sortFlow.value == sort) return
        windowFlow.value = DocumentRepository.DEFAULT_PAGE_SIZE
        sortFlow.value = sort
        viewModelScope.launch {
            preferencesManager.update {
                it.copy(defaultSortOrder = sort.order, defaultSortDirection = sort.direction)
            }
        }
    }

    /** Grows the library window by one page; called when the list reaches its end. */
    fun loadMore() {
        if (windowFlow.value >= MAX_WINDOW) return
        windowFlow.value += DocumentRepository.DEFAULT_PAGE_SIZE
    }

    fun toggleViewMode() {
        viewModeFlow.value =
            if (viewModeFlow.value.isGrid) ViewMode.LIST else ViewMode.GRID
    }

    fun clearMessage() {
        messageFlow.value = null
    }

    // ------------------------------------------------------------------ selection (PRD §28)

    fun enterSelection(documentId: String) {
        selectionFlow.value = setOf(documentId)
    }

    fun toggleSelection(documentId: String) {
        selectionFlow.value = selectionFlow.value.toMutableSet().apply {
            if (!add(documentId)) remove(documentId)
        }
    }

    fun clearSelection() {
        selectionFlow.value = emptySet()
    }

    fun setBulkFavorite(favorite: Boolean) {
        val ids = selectionFlow.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            documentRepository.setFavorite(ids, favorite)
            clearSelection()
            messageFlow.value = context.getString(
                if (favorite) R.string.snack_favourite_added else R.string.snack_favourite_removed,
            )
        }
    }

    fun bulkTrash() {
        val ids = selectionFlow.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            documentRepository.moveToTrash(ids)
            clearSelection()
            messageFlow.value = context.getString(R.string.snack_moved_to_trash)
        }
    }

    fun markOpened(id: String) {
        viewModelScope.launch { documentRepository.markOpened(id) }
    }

    // ------------------------------------------------------------------ permission

    fun hasStoragePermission(): Boolean = deviceDocumentsSource.hasPermission()

    fun storagePermissions(): Array<String> = deviceDocumentsSource.requiredPermissions()

    /** True when the grant has to be made on the system "All files access" screen (Android 11+). */
    fun requiresAllFilesAccess(): Boolean = deviceDocumentsSource.requiresAllFilesAccess()

    /**
     * Sends the user to the system screen that grants "All files access".
     *
     * Android 11+ has no runtime dialog for that permission, so the app has to open the screen
     * itself; the resulting grant is picked up by the resume check (see the screen's lifecycle
     * observer) which then runs the scan.
     */
    fun openAllFilesAccessSettings() {
        val intent = deviceDocumentsSource.allFilesAccessIntent()
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /**
     * Re-reads the storage grant after a permission dialog and on every resume - the all-files
     * grant is made on a system screen and only becomes visible once the app returns to the
     * foreground. A pass only runs on a *change* in access, so an ordinary resume costs nothing.
     */
    fun onStoragePermissionResult() {
        val granted = deviceDocumentsSource.hasPermission()
        val changed = granted != permissionFlow.value
        permissionFlow.value = granted
        when {
            granted && changed -> refreshDeviceDocuments()
            !granted -> deviceFlow.value = emptyList()
        }
    }

    /**
     * Scans the device: imports every file Docora does not know yet, then refreshes the inbox
     * with whatever is left. This is the automatic ingestion path - it runs when the screen
     * opens and again the moment the storage permission is granted - and it is also exposed as a
     * manual action, so a user who just added files can pull them in without restarting.
     *
     * Overlapping calls are cheap: [DeviceLibrarySynchronizer] serialises them and a second pass
     * simply finds nothing left to import.
     *
     * [reportWhenUnchanged] makes a manual refresh confirm that the library is already current, so
     * the button never feels inert; the automatic path stays silent unless something was imported.
     */
    fun refreshDeviceDocuments(reportWhenUnchanged: Boolean = false) {
        viewModelScope.launch {
            syncingFlow.value = true
            deviceLoadingFlow.value = true

            val outcome = deviceLibrarySynchronizer.sync()
            permissionFlow.value = outcome.permissionGranted
            deviceFlow.value = if (outcome.permissionGranted) {
                deviceDocumentsSource.query()
            } else {
                emptyList()
            }

            deviceLoadingFlow.value = false
            syncingFlow.value = false

            when {
                outcome.imported > 0 -> messageFlow.value =
                    context.getString(R.string.snack_scan_imported, outcome.imported)
                // A manual refresh always answers, so the control never feels inert.
                reportWhenUnchanged -> messageFlow.value =
                    context.getString(R.string.snack_scan_up_to_date)
            }
        }
    }

    // ------------------------------------------------------------------ actions

    /** Imports a discovered device file into the library, then hands back its id. */
    fun openDeviceDocument(device: DeviceDocument, onImported: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                documentRepository.import(device.toImportRequest())
            }.onSuccess { document ->
                onImported(document.id)
            }.onFailure {
                messageFlow.value = context.getString(
                    com.vedica.labs.ind.app.docora.R.string.snack_import_failed,
                    1,
                )
            }
        }
    }

    /** SAF picker import: persists read grants and inserts metadata for every picked URI. */
    fun importUris(uris: List<Uri>, onDone: (Int) -> Unit) {
        if (uris.isEmpty()) {
            onDone(0)
            return
        }
        viewModelScope.launch {
            var imported = 0
            uris.forEach { uri ->
                runCatching {
                    safGateway.takePersistablePermission(uri)
                    val meta = safGateway.getMetadata(uri).getOrNull()
                        ?: error("metadata unavailable")
                    val extension = MimeTypes.extensionOf(meta.displayName)
                    val mimeType = MimeTypes.resolve(meta.mimeType, meta.displayName)
                    documentRepository.import(
                        DocumentImportRequest(
                            uri = uri.toString(),
                            displayName = meta.displayName,
                            mimeType = mimeType,
                            extension = extension,
                            sizeBytes = meta.sizeBytes,
                            type = DocumentType.from(mimeType, extension),
                            source = DocumentSource.IMPORTED,
                            modifiedAt = meta.modifiedAt,
                            providerAuthority = uri.authority,
                        ),
                    )
                    imported++
                }
            }
            messageFlow.value = if (imported > 0) {
                context.getString(
                    com.vedica.labs.ind.app.docora.R.string.snack_imported,
                    imported,
                )
            } else {
                context.getString(
                    com.vedica.labs.ind.app.docora.R.string.snack_import_failed,
                    uris.size,
                )
            }
            onDone(imported)
        }
    }

    fun toggleFavorite(document: Document) {
        viewModelScope.launch {
            documentRepository.setFavorite(listOf(document.id), !document.isFavorite)
            messageFlow.value = context.getString(
                if (document.isFavorite) {
                    com.vedica.labs.ind.app.docora.R.string.snack_favourite_removed
                } else {
                    com.vedica.labs.ind.app.docora.R.string.snack_favourite_added
                },
            )
        }
    }

    fun moveToTrash(document: Document) {
        viewModelScope.launch {
            documentRepository.moveToTrash(listOf(document.id))
            messageFlow.value =
                context.getString(com.vedica.labs.ind.app.docora.R.string.snack_moved_to_trash)
        }
    }

    /**
     * Assigns (or clears) the rule-based category of one document (PRD §19).
     *
     * The category is part of the full-text index payload, so the repository re-writes the index
     * row on the way through - a document becomes findable under its new category immediately.
     */
    fun setCategory(document: Document, category: DocumentCategory?) {
        if (document.category == category) return
        viewModelScope.launch {
            documentRepository.setCategory(document.id, category)
            messageFlow.value = if (category == null) {
                context.getString(R.string.snack_category_cleared)
            } else {
                context.getString(R.string.snack_category_set, context.getString(categoryNameRes(category)))
            }
        }
    }

    // ------------------------------------------------------------------ previews

    /**
     * Renders a list/grid thumbnail through the handler layer. Resolves the MIME type from the
     * provider (falling back to the URI extension) so the correct handler is used for both
     * MediaStore and SAF URIs. Any failure returns null and the caller shows the type glyph.
     */
    suspend fun renderPreview(uri: String, targetWidth: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val parsed = Uri.parse(uri)
                val reported = runCatching { context.contentResolver.getType(parsed) }.getOrNull()
                val mimeType = MimeTypes.resolve(reported, parsed.lastPathSegment)
                val handler = handlerRegistry.getHandlerForMime(mimeType)
                    ?: handlerRegistry.getHandlerForType(
                        DocumentType.from(mimeType, MimeTypes.extensionOf(parsed.lastPathSegment)),
                    )
                    ?: return@runCatching null
                handler.renderThumbnail(parsed, 0, targetWidth).getOrNull()
            }.getOrNull()
        }

    /**
     * Sorts the in-memory device inbox with the same field + direction as the library query,
     * so switching the sort never makes the two sections disagree (PRD §16).
     *
     * The library is sorted by SQLite ([DocumentQueryBuilder]); MediaStore rows are not in that
     * table, so this mirrors its comparator in Kotlin. Pinned documents are a library concept
     * and do not apply here.
     */
    private fun sortDeviceDocuments(
        devices: List<DeviceDocument>,
        sort: DocumentSort,
    ): List<DeviceDocument> {
        val comparator: Comparator<DeviceDocument> = when (sort.order) {
            SortOrder.NAME -> compareBy { it.displayName.lowercase() }
            SortOrder.DATE_MODIFIED -> compareBy { it.modifiedAt }
            SortOrder.DATE_CREATED -> compareBy { it.modifiedAt }
            SortOrder.SIZE -> compareBy { it.sizeBytes }
            SortOrder.TYPE -> compareBy({ it.type.name }, { it.displayName.lowercase() })
            SortOrder.LAST_OPENED -> compareBy { it.modifiedAt }
        }
        val ordered = devices.sortedWith(comparator.thenBy { it.displayName.lowercase() })
        return if (sort.direction == SortDirection.ASCENDING) ordered else ordered.reversed()
    }

    private companion object {
        /**
         * Ceiling for the paging window. It exists so a runaway scroll cannot make the browser
         * hold an unbounded list in memory; reaching it already means thousands of documents.
         */
        const val MAX_WINDOW = 2_400
    }
}

