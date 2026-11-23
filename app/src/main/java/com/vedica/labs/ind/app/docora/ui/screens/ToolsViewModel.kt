package com.vedica.labs.ind.app.docora.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedica.labs.ind.app.docora.core.model.Document
import com.vedica.labs.ind.app.docora.core.model.SmartCollection
import com.vedica.labs.ind.app.docora.core.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The Tools tab's only state: the PDFs that can be opened in the toolkit. */
data class ToolsUiState(
    val pdfs: List<Document> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * Backs the top-level Tools tab.
 *
 * The toolkit itself is document-scoped, so the tab's job is to keep the PDF list live and hand a
 * chosen document to [PdfToolsScreen]. Room pushes changes, which means a newly created or deleted
 * PDF appears here without a manual refresh.
 */
@HiltViewModel
class ToolsViewModel @Inject constructor(
    documentRepository: DocumentRepository,
) : ViewModel() {

    val state: StateFlow<ToolsUiState> = documentRepository
        .observeCollection(
            collection = SmartCollection.PDFS,
            limit = MAX_PDFS,
        )
        .map { pdfs -> ToolsUiState(pdfs = pdfs, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ToolsUiState(),
        )

    private companion object {
        /** The tab is a picker, not the full library; a cap keeps the list cheap to compose. */
        const val MAX_PDFS = 200
    }
}
