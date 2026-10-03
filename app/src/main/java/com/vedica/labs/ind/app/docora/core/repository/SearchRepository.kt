package com.vedica.labs.ind.app.docora.core.repository

import com.vedica.labs.ind.app.docora.core.model.Document
import com.vedica.labs.ind.app.docora.core.model.IndexingProgress
import com.vedica.labs.ind.app.docora.core.model.SearchFilters
import com.vedica.labs.ind.app.docora.core.model.SearchResultItem
import kotlinx.coroutines.flow.Flow

/**
 * Full-text search (PRD §8, §25) including the write side of the index.
 *
 * Reads never touch the file system: search answers purely from the persisted index, so a query
 * on a library of thousands of documents stays in the low-millisecond range. The only code that
 * ever reads document content is the indexing worker.
 */
interface SearchRepository {

    /**
     * Ranked results for [query] with [filters] applied.
     *
     * Ranking prefers title hits over body hits, then falls back to recency - a deterministic
     * order that needs no `bm25()` extension and can be unit tested.
     */
    suspend fun search(
        query: String,
        filters: SearchFilters = SearchFilters.None,
        limit: Int = DEFAULT_RESULT_LIMIT,
    ): List<SearchResultItem>

    /** Lightweight matches used to fill the instant-suggestion list while typing. */
    suspend fun suggestions(query: String, limit: Int = 8): List<Document>

    /** How much of the library the index still covers, shown while searching. */
    fun observeIndexingProgress(): Flow<IndexingProgress>

    fun observeRecentSearches(): Flow<List<String>>

    suspend fun recordSearch(query: String)

    suspend fun clearRecentSearches()

    /** Writes the index row for one document (metadata plus whatever text was extracted). */
    suspend fun indexDocument(
        document: Document,
        extractedText: String = "",
        recognisedText: String = "",
    )

    /** Removes a document from the index; called when a row is deleted permanently. */
    suspend fun removeFromIndex(documentId: String)

    suspend fun clearIndex()

    companion object {
        const val DEFAULT_RESULT_LIMIT = 60
        const val MAX_RECENT_SEARCHES = 8
    }
}
