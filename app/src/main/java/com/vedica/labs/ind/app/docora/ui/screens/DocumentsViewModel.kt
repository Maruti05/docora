package com.vedica.labs.ind.app.docora.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.datastore.PreferencesManager
import com.vedica.labs.ind.app.docora.core.handler.DocumentHandlerRegistry
import com.vedica.labs.ind.app.docora.core.model.Document
import com.vedica.labs.ind.app.docora.core.model.DocumentFilter
import com.vedica.labs.ind.app.docora.core.model.DocumentImportRequest
import com.vedica.labs.ind.app.docora.core.model.DocumentSource
import com.vedica.labs.ind.app.docora.core.model.DocumentType
import com.vedica.labs.ind.app.docora.core.model.SmartCollection
import com.vedica.labs.ind.app.docora.core.model.ViewMode
import com.vedica.labs.ind.app.docora.core.repository.DocumentRepository
import com.vedica.labs.ind.app.docora.core.storage.DeviceDocument
import com.vedica.labs.ind.app.docora.core.storage.DeviceDocumentsSource
import com.vedica.labs.ind.app.docora.core.storage.SafGateway
import com.vedica.labs.ind.app.docora.core.util.MimeTypes
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
    val viewMode: ViewMode = ViewMode.default,
    val documents: List<Document> = emptyList(),
    val deviceDocuments: List<DeviceDocument> = emptyList(),
    val hasStoragePermission: Boolean = false,
    val deviceLoading: Boolean = false,
    val isLoading: Boolean = true,
    /** Ids currently selected via long-press (PRD §28). */
    val selection: Set<String> = emptySet(),
)

/**
 * Browser logic for the Documents screen.
 *
 * The library window is a database flow (collection + filter + name search, debounced so a
 * keystroke does not fire a query per character); the "On this device" section is a bounded
 * MediaStore snapshot that only loads once the storage permission has been granted. Device
 * files are imported into the library on tap - an import is a metadata insert, the original
 * file is never copied or moved.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class DocumentsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentRepository: DocumentRepository,
    private val safGateway: SafGateway,
    private val deviceDocumentsSource: DeviceDocumentsSource,
    private val handlerRegistry: DocumentHandlerRegistry,
    private val preferencesManager: PreferencesManager,
) : ViewModel() {

    private data class Controls(val query: String, val filter: DocumentFilter)

    private val controls = MutableStateFlow(Controls("", DocumentFilter.ALL))
    private val viewModeFlow = MutableStateFlow(ViewMode.default)
    private val permissionFlow = MutableStateFlow(deviceDocumentsSource.hasPermission())
    private val deviceFlow = MutableStateFlow<List<DeviceDocument>>(emptyList())
    private val deviceLoadingFlow = MutableStateFlow(false)
    private val selectionFlow = MutableStateFlow<Set<String>>(emptySet())
    private val messageFlow = MutableStateFlow<String?>(null)

    private val libraryFlow = controls
        .debounce(200)
        .flatMapLatest { c ->
            documentRepository.observeCollection(
                collection = SmartCollection.ALL,
                filter = c.filter,
                searchQuery = c.query.trim().ifBlank { null },
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
    ) { documents, c, meta ->
        val query = c.query.trim()
        DocumentsUiState(
            query = c.query,
            filter = c.filter,
            viewMode = meta.viewMode,
            documents = documents,
            deviceDocuments = if (query.isEmpty()) {
                meta.device
            } else {
                meta.device.filter { it.displayName.contains(query, ignoreCase = true) }
            },
            hasStoragePermission = meta.permission,
            deviceLoading = meta.deviceLoading,
            isLoading = false,
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
            viewModeFlow.value = preferencesManager.settings.first().defaultViewMode
            if (deviceDocumentsSource.hasPermission()) refreshDeviceDocuments()
        }
    }

    // ------------------------------------------------------------------ controls

    fun setQuery(query: String) {
        controls.value = controls.value.copy(query = query)
    }

    fun setFilter(filter: DocumentFilter) {
        controls.value = controls.value.copy(filter = filter)
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

    fun onStoragePermissionResult() {
        val granted = deviceDocumentsSource.hasPermission()
        permissionFlow.value = granted
        if (granted) {
            refreshDeviceDocuments()
        } else {
            deviceFlow.value = emptyList()
        }
    }

    fun refreshDeviceDocuments() {
        viewModelScope.launch {
            deviceLoadingFlow.value = true
            deviceFlow.value = deviceDocumentsSource.query()
            deviceLoadingFlow.value = false
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
}

