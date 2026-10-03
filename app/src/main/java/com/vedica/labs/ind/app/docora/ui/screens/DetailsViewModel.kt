package com.vedica.labs.ind.app.docora.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.handler.DocumentHandlerRegistry
import com.vedica.labs.ind.app.docora.core.model.Document
import com.vedica.labs.ind.app.docora.core.model.Folder
import com.vedica.labs.ind.app.docora.core.model.Tag
import com.vedica.labs.ind.app.docora.core.repository.DocumentRepository
import com.vedica.labs.ind.app.docora.core.repository.FolderRepository
import com.vedica.labs.ind.app.docora.core.repository.TagRepository
import com.vedica.labs.ind.app.docora.core.util.MimeTypes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Immutable details state (PRD §49). */
data class DetailsUiState(
    val document: Document? = null,
    val tags: List<Tag> = emptyList(),
    val folder: Folder? = null,
    val folderPath: String? = null,
    val isLoading: Boolean = true,
)

/** Missing displayable folder name. */
private const val ROOT_LABEL = "Library root"

/**
 * Details logic: one live document, its tags and its folder path, plus the write actions
 * offered on the details screen (PRD §27, §49). The document flow keeps the screen current
 * if a background job renames or re-indexes it while it is open.
 */
@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val documentRepository: DocumentRepository,
    private val tagRepository: TagRepository,
    private val folderRepository: FolderRepository,
    private val handlerRegistry: DocumentHandlerRegistry,
    private val dispatchers: com.vedica.labs.ind.app.docora.core.common.DispatcherProvider,
) : ViewModel() {

    private val documentId: String? = savedStateHandle[Screen.ARG_DOCUMENT_ID]

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val state: StateFlow<DetailsUiState> = combine(
        documentRepository.observeDocument(documentId.orEmpty()),
        tagRepository.observeTagsForDocument(documentId.orEmpty()),
    ) { document, tags -> document to tags }
        .combine(folderRepository.observeAllFolders()) { (document, tags), folders ->
            DetailsUiState(
                document = document,
                tags = tags,
                folder = document?.folderId?.let { id -> folders.firstOrNull { it.folder.id == id } }?.folder,
                folderPath = document?.folderId
                    ?.let { id -> folders.firstOrNull { it.folder.id == id } }
                    ?.let { it.folder.name },
                isLoading = false,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DetailsUiState(),
        )

    fun clearMessage() {
        _message.value = null
    }

    fun rename(newName: String) {
        val id = documentId ?: return
        viewModelScope.launch {
            val result = documentRepository.rename(id, newName.trim())
            _message.value = if (result is com.vedica.labs.ind.app.docora.core.common.DocoraResult.Success) {
                context.getString(R.string.snack_renamed)
            } else {
                context.getString(R.string.snack_rename_failed)
            }
        }
    }

    fun toggleFavorite() {
        val document = state.value.document ?: return
        viewModelScope.launch {
            documentRepository.setFavorite(listOf(document.id), !document.isFavorite)
        }
    }

    fun moveToTrash() {
        val id = documentId ?: return
        viewModelScope.launch {
            documentRepository.moveToTrash(listOf(id))
            _message.value = context.getString(R.string.snack_moved_to_trash)
        }
    }

    /** System share sheet; the URI stays a content:// grant (PRD §47). */
    fun share(context: Context) {
        val document = state.value.document ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = document.mimeType.ifBlank { MimeTypes.OCTET_STREAM }
            putExtra(Intent.EXTRA_STREAM, Uri.parse(document.uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    fun openExternally(context: Context) {
        val document = state.value.document ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(document.uri), document.mimeType.ifBlank { MimeTypes.OCTET_STREAM })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
    }

    fun markOpened() {
        val id = documentId ?: return
        viewModelScope.launch { documentRepository.markOpened(id) }
    }

    /** Renders the hero preview through the handler layer; null falls back to the type glyph. */
    suspend fun renderPreview(targetWidth: Int): Bitmap? = withContext(dispatchers.io) {
        val document = state.value.document ?: return@withContext null
        runCatching {
            handlerRegistry.getHandlerForMime(document.mimeType)
                ?.renderThumbnail(Uri.parse(document.uri), 0, targetWidth)
                ?.getOrNull()
        }.getOrNull()
    }
}
