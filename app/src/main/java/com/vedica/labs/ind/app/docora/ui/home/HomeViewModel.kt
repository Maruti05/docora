package com.vedica.labs.ind.app.docora.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedica.labs.ind.app.docora.core.model.DashboardStats
import com.vedica.labs.ind.app.docora.core.model.Document
import com.vedica.labs.ind.app.docora.core.model.DocumentCategory
import com.vedica.labs.ind.app.docora.core.model.IndexingProgress
import com.vedica.labs.ind.app.docora.core.model.SmartCollection
import com.vedica.labs.ind.app.docora.core.repository.DocumentRepository
import com.vedica.labs.ind.app.docora.core.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val stats: DashboardStats = DashboardStats.Empty,
    val recentDocuments: List<Document> = emptyList(),
    val indexing: IndexingProgress = IndexingProgress(0, 0),
    val categoryCounts: Map<DocumentCategory, Int> = emptyMap(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    documentRepository: DocumentRepository,
    searchRepository: SearchRepository,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        documentRepository.observeDashboard(),
        documentRepository.observeCollection(SmartCollection.RECENT, limit = 10),
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
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )
}
