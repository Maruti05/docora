package com.vedica.labs.ind.app.docora.core.repository

import com.vedica.labs.ind.app.docora.core.common.DocoraResult
import com.vedica.labs.ind.app.docora.core.model.DashboardStats
import com.vedica.labs.ind.app.docora.core.model.Document
import com.vedica.labs.ind.app.docora.core.model.DocumentCategory
import com.vedica.labs.ind.app.docora.core.model.DocumentFilter
import com.vedica.labs.ind.app.docora.core.model.DocumentImportRequest
import com.vedica.labs.ind.app.docora.core.model.DocumentSort
import com.vedica.labs.ind.app.docora.core.model.DuplicateScanResult
import com.vedica.labs.ind.app.docora.core.model.LargestFileItem
import com.vedica.labs.ind.app.docora.core.model.OcrStatus
import com.vedica.labs.ind.app.docora.core.model.SmartCollection
import com.vedica.labs.ind.app.docora.core.model.StorageBreakdown
import com.vedica.labs.ind.app.docora.core.model.TextIndexStatus
import com.vedica.labs.ind.app.docora.core.model.TextOrigin
import kotlinx.coroutines.flow.Flow

/**
 * The single entry point for document metadata.
 *
 * Everything the UI receives is an immutable [Document]: Room entities never leave this layer, so
 * Compose sees stable types and a schema change can never ripple into a composable. Reads are
 * flows that follow the database, writes are `suspend` and dispatched to IO by the implementation.
 */
interface DocumentRepository {

    // ------------------------------------------------------------------ reads

    fun observeDocument(id: String): Flow<Document?>

    suspend fun getDocument(id: String): Document?

    suspend fun getDocumentByUri(uri: String): Document?

    suspend fun getDocuments(ids: List<String>): List<Document>

    /**
     * A page of one collection. [limit] bounds the window the UI holds in memory; the browser
     * grows it incrementally instead of materialising the whole library (PRD §35).
     */
    fun observeCollection(
        collection: SmartCollection,
        filter: DocumentFilter = DocumentFilter.ALL,
        sort: DocumentSort = DocumentSort.Default,
        limit: Int = DEFAULT_PAGE_SIZE,
        searchQuery: String? = null,
    ): Flow<List<Document>>

    fun observeFolder(
        folderId: String?,
        sort: DocumentSort = DocumentSort.Default,
        limit: Int = DEFAULT_PAGE_SIZE,
    ): Flow<List<Document>>

    fun observeDashboard(): Flow<DashboardStats>

    fun observeStorageBreakdown(): Flow<StorageBreakdown>

    /** Documents per category, used for the dashboard category chips. */
    fun observeCategoryCounts(): Flow<Map<DocumentCategory, Int>>

    suspend fun findDuplicates(): DuplicateScanResult

    suspend fun getLargestFiles(limit: Int = 50): List<LargestFileItem>

    suspend fun countInFolder(folderId: String): Int

    // ------------------------------------------------------------------ writes

    /** Persists a new document. Re-importing the same URI updates the existing row. */
    suspend fun import(request: DocumentImportRequest): Document

    suspend fun importAll(requests: List<DocumentImportRequest>): List<Document>

    suspend fun rename(id: String, newDisplayName: String): DocoraResult<Document>

    suspend fun setFavorite(ids: List<String>, isFavorite: Boolean)

    suspend fun setPinned(id: String, isPinned: Boolean)

    suspend fun setArchived(ids: List<String>, isArchived: Boolean)

    /** Application-level trash: reversible, and the only delete path the browser uses. */
    suspend fun moveToTrash(ids: List<String>)

    suspend fun restore(ids: List<String>)

    suspend fun deletePermanently(ids: List<String>)

    suspend fun emptyTrash()

    suspend fun moveToFolder(ids: List<String>, folderId: String?)

    suspend fun updateNotes(id: String, notes: String)

    suspend fun markOpened(id: String, openedAt: Long = System.currentTimeMillis())

    suspend fun updateFileFacts(id: String, sizeBytes: Long, modifiedAt: Long)

    suspend fun setCategory(id: String, category: DocumentCategory?)

    // -------------------------------------------------- background enrichment

    suspend fun getPendingEnrichment(limit: Int = 8): List<Document>

    suspend fun getPendingTextExtraction(limit: Int = 4): List<Document>

    suspend fun getPendingTextRecognition(limit: Int = 4): List<Document>

    suspend fun setThumbnailPath(id: String, path: String?)

    suspend fun setChecksum(id: String, checksum: String)

    suspend fun setGeometry(id: String, pageCount: Int?, width: Int?, height: Int?)

    suspend fun setTextState(id: String, status: TextIndexStatus, origin: TextOrigin)

    suspend fun setOcrState(id: String, status: OcrStatus)

    /** Permanently deletes trashed documents older than [cutoff]; returns how many were removed. */
    suspend fun purgeTrashOlderThan(cutoff: Long): Int

    companion object {
        /** Documents fetched per page: large enough to fill a tablet grid, small enough to stay
         *  well inside a single frame's memory budget. */
        const val DEFAULT_PAGE_SIZE = 60
    }
}
