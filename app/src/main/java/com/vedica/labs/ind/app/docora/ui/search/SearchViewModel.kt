package com.vedica.labs.ind.app.docora.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedica.labs.ind.app.docora.core.model.Document
import com.vedica.labs.ind.app.docora.core.model.IndexingProgress
import com.vedica.labs.ind.app.docora.core.model.SearchResultItem
import com.vedica.labs.ind.app.docora.core.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Search screen state (PRD §25): suggestions while typing, ranked results once committed. */
data class SearchUiState(
    val query: String = "",
    val results: List<SearchResultItem> = emptyList(),
    val suggestions: List<Document> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val indexing: IndexingProgress = IndexingProgress(0, 0),
    val isSearching: Boolean = false,
)

/**
 * Global search logic.
 *
 * The query is debounced, then fanned out to the instant-suggestion query and the ranked
 * full-text query; both answer from the persisted FTS index, so results stay in the
 * low-millisecond range even on a library of thousands of documents (PRD §8, §35).
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val committedFlow = MutableStateFlow("")

    private val resultsFlow = committedFlow
        .debounce(150)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(emptyList<SearchResultItem>())
            } else {
                flow { emit(searchRepository.search(query)) }
            }
        }

    private val suggestionsFlow = queryFlow
        .debounce(120)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(emptyList<Document>())
            } else {
                flow { emit(searchRepository.suggestions(query)) }
            }
        }

    val state: StateFlow<SearchUiState> = combine(
        queryFlow,
        resultsFlow,
        suggestionsFlow,
        searchRepository.observeRecentSearches(),
        searchRepository.observeIndexingProgress(),
    ) { query, results, suggestions, recent, indexing ->
        SearchUiState(
            query = query,
            results = results,
            suggestions = suggestions,
            recentSearches = recent,
            indexing = indexing,
            isSearching = query.isNotBlank() && results.isEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState(),
    )

    fun onQueryChange(value: String) {
        queryFlow.value = value
        committedFlow.value = value.trim()
    }

    fun onSearchCommitted() {
        val query = queryFlow.value.trim()
        if (query.isBlank()) return
        committedFlow.value = query
        viewModelScope.launch { searchRepository.recordSearch(query) }
    }

    fun onRecentSelected(term: String) {
        onQueryChange(term)
        onSearchCommitted()
    }

    fun clearRecentSearches() {
        viewModelScope.launch { searchRepository.clearRecentSearches() }
    }
}
