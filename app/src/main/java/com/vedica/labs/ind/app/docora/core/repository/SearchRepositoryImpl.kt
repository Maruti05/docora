package com.vedica.labs.ind.app.docora.core.repository

import com.vedica.labs.ind.app.docora.core.common.DispatcherProvider
import com.vedica.labs.ind.app.docora.core.database.dao.DocumentDao
import com.vedica.labs.ind.app.docora.core.database.dao.SearchDao
import com.vedica.labs.ind.app.docora.core.database.dao.TagDao
import com.vedica.labs.ind.app.docora.core.database.mapper.buildSearchIndexPayload
import com.vedica.labs.ind.app.docora.core.database.mapper.toDomain
import com.vedica.labs.ind.app.docora.core.datastore.PreferencesManager
import com.vedica.labs.ind.app.docora.core.model.Document
import com.vedica.labs.ind.app.docora.core.model.DocumentType
import com.vedica.labs.ind.app.docora.core.model.IndexingProgress
import com.vedica.labs.ind.app.docora.core.model.SearchFilters
import com.vedica.labs.ind.app.docora.core.model.SearchResultItem
import com.vedica.labs.ind.app.docora.core.search.SearchQuery
import com.vedica.labs.ind.app.docora.core.search.SearchRanking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Full-text search implementation (PRD §8, §25).
 *
 * Read path: MATCH against the FTS4 index with every filter pushed into SQL, then ranking and
 * snippet extraction in Kotlin (see [SearchRanking] for why). Type filters are widened to "every
 * type" when the user has not chosen one, which keeps the statement free of an empty `IN ()` - a
 * construct SQLite rejects at parse time.
 *
 * Write path: [indexDocument] always replaces the row (delete + insert in one transaction), so the
 * index cannot accumulate duplicates for a document that was re-imported or renamed.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val searchDao: SearchDao,
    private val documentDao: DocumentDao,
    private val tagDao: TagDao,
    private val preferencesManager: PreferencesManager,
    private val dispatchers: DispatcherProvider,
) : SearchRepository {

    private suspend fun <T> io(block: suspend () -> T): T = withContext(dispatchers.io) { block() }

    override suspend fun search(
        query: String,
        filters: SearchFilters,
        limit: Int,
    ): List<SearchResultItem> = io {
        val expression = SearchQuery.toMatchExpression(query)
        val terms = SearchQuery.terms(query)
        val types = filters.types.ifEmpty { DocumentType.entries.toSet() }.toList()
        val rows = if (expression == null) {
            emptyList()
        } else {
            runCatching {
                searchDao.search(
                    matchQuery = expression,
                    types = types,
                    trashFlag = if (filters.includeTrashed) 1 else 0,
                    favoritesOnly = if (filters.favoritesOnly) 1 else 0,
                    modifiedAfter = filters.dateRange.lowerBoundMillis(System.currentTimeMillis()) ?: 0L,
                    useFolderFilter = if (filters.folderIds.isEmpty()) 0 else 1,
                    // A sentinel value keeps the IN list non-empty when the filter is off.
                    folderIds = filters.folderIds.toList().ifEmpty { listOf(NO_FILTER_SENTINEL) },
                    useTagFilter = if (filters.tagIds.isEmpty()) 0 else 1,
                    tagIds = filters.tagIds.toList().ifEmpty { listOf(NO_FILTER_SENTINEL) },
                    limit = limit,
                )
            }.getOrElse {
                // The user can type something the FTS grammar cannot parse (a lone quote, for
                // instance). Falling back to a LIKE scan keeps the screen useful.
                searchDao.searchFallback(
                    query = query.trim(),
                    types = types,
                    trashFlag = if (filters.includeTrashed) 1 else 0,
                    limit = limit,
                )
            }
        }

        val now = System.currentTimeMillis()
        rows.mapNotNull { row ->
            val entity = row.document
            val tagNames = tagDao.getTagsForDocument(entity.id).joinToString(separator = " ") { it.name }
            val score = SearchRanking.score(
                title = entity.displayName,
                tags = tagNames,
                notes = entity.notes,
                body = row.searchBody,
                category = entity.category?.name.orEmpty(),
                terms = terms,
                modifiedAt = entity.modifiedAt,
                now = now,
            )
            if (score.rank <= 0.0) {
                null
            } else {
                SearchResultItem(
                    document = entity.toDomain(),
                    snippet = SearchRanking.snippet(row.searchBody, entity.notes, terms),
                    rank = score.rank,
                )
            }
        }.sortedWith(
            compareByDescending<SearchResultItem> { it.rank }
                .thenByDescending { it.document.modifiedAt },
        )
    }

    override suspend fun suggestions(query: String, limit: Int): List<Document> =
        search(query = query, limit = limit).map { it.document }

    override fun observeIndexingProgress(): Flow<IndexingProgress> =
        documentDao.observeDashboardStats().mapLatest { stats ->
            val total = (stats.totalDocuments + stats.pendingIndexing).coerceAtLeast(1)
            IndexingProgress(
                total = total,
                processed = (total - stats.pendingIndexing).coerceAtLeast(0),
            )
        }

    override fun observeRecentSearches(): Flow<List<String>> =
        preferencesManager.settings.mapLatest { it.recentSearches }

    override suspend fun recordSearch(query: String) {
        val clean = query.trim()
        if (clean.length < MIN_RECORDED_QUERY_LENGTH) return
        preferencesManager.update { settings ->
            val updated = (listOf(clean) + settings.recentSearches.filterNot {
                it.equals(clean, ignoreCase = true)
            }).take(SearchRepository.MAX_RECENT_SEARCHES)
            settings.copy(recentSearches = updated)
        }
    }

    override suspend fun clearRecentSearches() {
        preferencesManager.update { it.copy(recentSearches = emptyList()) }
    }

    override suspend fun indexDocument(
        document: Document,
        extractedText: String,
        recognisedText: String,
    ) = io {
        val entity = documentDao.getById(document.id) ?: return@io
        val tagNames = tagDao.getTagsForDocument(entity.id).map { it.name }
        val payload = buildSearchIndexPayload(entity, extractedText, recognisedText, tagNames)
        searchDao.upsertIndexRow(
            docId = payload.docId,
            title = payload.title.take(MAX_INDEXED_TITLE_CHARS),
            body = payload.body.take(MAX_INDEXED_BODY_CHARS),
            notes = payload.notes.take(MAX_INDEXED_NOTES_CHARS),
            tags = payload.tags,
            category = payload.category,
        )
    }

    override suspend fun removeFromIndex(documentId: String) =
        io { searchDao.deleteIndexRow(documentId) }

    override suspend fun clearIndex() = io { searchDao.clearIndex() }

    private companion object {
        /** Never matches a real id; only used to keep a filter `IN` list syntactically valid. */
        const val NO_FILTER_SENTINEL = "__docora_no_filter__"
        const val MIN_RECORDED_QUERY_LENGTH = 2
        const val MAX_INDEXED_TITLE_CHARS = 300
        const val MAX_INDEXED_NOTES_CHARS = 4_000

        /**
         * The index keeps a window of the extracted text. Indexing an entire 500 page book would
         * multiply database size for a shrinking chance that a phrase at the very end is ever
         * searched for, so long documents are truncated rather than allowed to bloat the database
         * (PRD §36: documents must degrade gracefully).
         */
        const val MAX_INDEXED_BODY_CHARS = 200_000
    }
}
