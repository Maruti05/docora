package com.vedica.labs.ind.app.docora.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedica.labs.ind.app.docora.core.handler.DocumentHandlerRegistry
import com.vedica.labs.ind.app.docora.core.model.DashboardStats
import com.vedica.labs.ind.app.docora.core.model.Document
import com.vedica.labs.ind.app.docora.core.model.DocumentCategory
import com.vedica.labs.ind.app.docora.core.model.DocumentType
import com.vedica.labs.ind.app.docora.core.model.IndexingProgress
import com.vedica.labs.ind.app.docora.core.model.SmartCollection
import com.vedica.labs.ind.app.docora.core.repository.DocumentRepository
import com.vedica.labs.ind.app.docora.core.repository.SearchRepository
import com.vedica.labs.ind.app.docora.core.util.MimeTypes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class HomeUiState(
    val stats: DashboardStats = DashboardStats.Empty,
    val recentDocuments: List<Document> = emptyList(),
    val indexing: IndexingProgress = IndexingProgress(0, 0),
    val categoryCounts: Map<DocumentCategory, Int> = emptyMap(),
    val isLoading: Boolean = true,
)

/**
 * The dashboard's single source of state.
 *
 * Four database flows are folded into one state object and published as a `StateFlow`. Two details
 * matter for how the screen feels: the upstream flows are already Room-backed (so a change pushes a
 * new value instead of the screen polling), and `distinctUntilChanged` collapses emissions that
 * carry the same values - a document being re-indexed, for instance, notifies several tables but
 * changes nothing on the dashboard, and without this the whole list would recompose for nothing.
 *
 * The collection is capped at [RECENT_LIMIT] because the dashboard shows a preview, not the
 * library; the full list lives on the documents screen.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    documentRepository: DocumentRepository,
    searchRepository: SearchRepository,
    private val handlerRegistry: DocumentHandlerRegistry,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        documentRepository.observeDashboard(),
        documentRepository.observeCollection(SmartCollection.RECENT, limit = RECENT_LIMIT),
        searchRepository.observeIndexingProgress(),
        documentRepository.observeCategoryCounts(),
    ) { stats, recent, indexing, categories ->
        HomeUiState(
            stats = stats,
            recentDocuments = recent,
            indexing = indexing,
            categoryCounts = categories,
            isLoading = false,
        )
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    /**
     * Renders a row thumbnail through the handler layer, exactly as the documents browser does.
     *
     * The MIME type is read back from the provider (falling back to the URI extension) because a
     * document's stored type can be coarser than what the handler needs, and a failure returns null
     * so the row falls back to its type glyph instead of showing a broken image.
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

    private companion object {
        /** How many recent documents the dashboard previews. */
        const val RECENT_LIMIT = 8
    }
}
