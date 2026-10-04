package com.vedica.labs.ind.app.docora.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.vedica.labs.ind.app.docora.core.database.entity.DocumentEntity
import com.vedica.labs.ind.app.docora.core.database.model.CategoryCountRow
import com.vedica.labs.ind.app.docora.core.database.model.DashboardStatsRow
import com.vedica.labs.ind.app.docora.core.database.model.DuplicateGroupRow
import com.vedica.labs.ind.app.docora.core.database.model.StorageBreakdownRow
import com.vedica.labs.ind.app.docora.core.model.DocumentCategory
import com.vedica.labs.ind.app.docora.core.model.DocumentSource
import com.vedica.labs.ind.app.docora.core.model.DocumentType
import kotlinx.coroutines.flow.Flow
import com.vedica.labs.ind.app.docora.core.model.OcrStatus
import com.vedica.labs.ind.app.docora.core.model.TextIndexStatus
import com.vedica.labs.ind.app.docora.core.model.TextOrigin

/**
 * Persisted document metadata access.
 *
 * Every listening query is exposed as a `Flow` so the UI follows the database instead of
 * polling it, and every query that can be pushed into SQLite is pushed there: filtering,
 * sorting, counting and aggregating in Kotlin would not survive a library with thousands
 * of documents (PRD §35, §36).
 */
@Dao
interface DocumentDao {

    // ------------------------------------------------------------------ single rows

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): DocumentEntity?

    /**
     * Every URI the library already knows about.
     *
     * Trashed and archived rows are included on purpose: the device sync uses this set to decide
     * what is "new", so a file the user deleted inside Docora is never silently re-imported just
     * because it still exists on the device.
     */
    @Query("SELECT uri FROM documents")
    suspend fun allUris(): List<String>

    @Query("SELECT uri FROM documents")
    fun observeAllUris(): Flow<List<String>>

    @Query("SELECT * FROM documents WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE checksum_sha256 = :checksum AND is_trash = 0")
    suspend fun getByChecksum(checksum: String): List<DocumentEntity>

    // ------------------------------------------------------------------ collections

    @Query(
        "SELECT * FROM documents WHERE is_trash = 0 AND is_archived = 0 " +
            "ORDER BY is_pinned DESC, modified_at DESC",
    )
    fun observeAllActive(): Flow<List<DocumentEntity>>

    @Query(
        "SELECT * FROM documents WHERE is_trash = 0 AND is_archived = 0 AND source = :source " +
            "ORDER BY modified_at DESC LIMIT :limit",
    )
    fun observeBySource(source: DocumentSource, limit: Int = 200): Flow<List<DocumentEntity>>

    @Query(
        "SELECT * FROM documents WHERE is_trash = 0 AND is_archived = 0 " +
            "ORDER BY size_bytes DESC LIMIT :limit",
    )
    fun observeLargestFiles(limit: Int = 100): Flow<List<DocumentEntity>>

    @Query(
        "SELECT * FROM documents WHERE is_trash = 0 AND is_archived = 0 AND category = :category " +
            "ORDER BY modified_at DESC LIMIT :limit",
    )
    fun observeByCategory(category: DocumentCategory, limit: Int = 200): Flow<List<DocumentEntity>>

    // --------------------------------------------------- raw, paged and sorted reads


    @RawQuery(observedEntities = [DocumentEntity::class])
    fun observeRawDocuments(query: SupportSQLiteQuery): Flow<List<DocumentEntity>>

    @RawQuery
    suspend fun rawDocuments(query: SupportSQLiteQuery): List<DocumentEntity>

    // ------------------------------------------------------------------ aggregates

    /**
     * Single-statement dashboard counters.
     *
     * Correlated subqueries keep this to one result row and one table scan set, so the
     * dashboard does not issue ten queries per database change (PRD §35).
     */
    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM documents WHERE is_trash = 0 AND is_archived = 0) AS totalDocuments,
            (SELECT COUNT(*) FROM documents WHERE is_trash = 0 AND is_archived = 0 AND source = 'SCANNED') AS scans,
            (SELECT COUNT(*) FROM documents WHERE is_trash = 0 AND is_favorite = 1) AS favorites,
            (SELECT COUNT(*) FROM documents WHERE is_trash = 0 AND is_archived = 0 AND document_type = 'PDF') AS pdfs,
            (SELECT COUNT(*) FROM documents WHERE is_trash = 0 AND is_archived = 0 AND document_type = 'IMAGE') AS images,
            (SELECT COUNT(*) FROM documents WHERE is_trash = 0 AND ocr_state IN ('NOT_PROCESSED', 'PROCESSING') AND document_type IN ('PDF', 'IMAGE')) AS needsOcr,
            (SELECT COUNT(*) FROM documents WHERE is_trash = 1) AS trashed,
            (SELECT COUNT(*) FROM documents WHERE is_trash = 0 AND (
                thumbnail_path IS NULL OR checksum_sha256 IS NULL OR
                text_state IN ('NOT_PROCESSED', 'PROCESSING') OR
                ocr_state IN ('NOT_PROCESSED', 'PROCESSING')
            )) AS pendingIndexing,
            (SELECT COALESCE(SUM(size_bytes), 0) FROM documents WHERE is_trash = 0) AS totalBytes,
            (SELECT COALESCE(SUM(size_bytes), 0) FROM documents WHERE is_trash = 1) AS trashBytes
        """,
    )
    fun observeDashboardStats(): Flow<DashboardStatsRow>

    @Query(
        """
        SELECT
            (SELECT COALESCE(SUM(size_bytes), 0) FROM documents WHERE is_trash = 0) AS totalBytes,
            (SELECT COALESCE(SUM(size_bytes), 0) FROM documents WHERE is_trash = 0 AND document_type = 'PDF') AS pdfBytes,
            (SELECT COALESCE(SUM(size_bytes), 0) FROM documents WHERE is_trash = 0 AND document_type = 'IMAGE') AS imageBytes,
            (SELECT COALESCE(SUM(size_bytes), 0) FROM documents WHERE is_trash = 0 AND document_type NOT IN ('PDF', 'IMAGE')) AS otherBytes,
            (SELECT COALESCE(SUM(size_bytes), 0) FROM documents WHERE is_trash = 1) AS trashBytes,
            (SELECT COUNT(*) FROM documents WHERE is_trash = 0) AS documentCount
        """,
    )
    fun observeStorageBreakdown(): Flow<StorageBreakdownRow>

    @Query(
        "SELECT category AS category, COUNT(*) AS document_count FROM documents " +
            "WHERE is_trash = 0 AND is_archived = 0 AND category IS NOT NULL GROUP BY category",
    )
    fun observeCategoryCounts(): Flow<List<CategoryCountRow>>

    @Query("SELECT COUNT(*) FROM documents WHERE is_trash = 0 AND is_archived = 0")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM documents WHERE folder_id = :folderId")
    suspend fun countInFolder(folderId: String): Int

    @Query("SELECT COALESCE(SUM(size_bytes), 0) FROM documents WHERE is_trash = 0")
    fun observeTotalStorageBytes(): Flow<Long>

    @Query("SELECT COALESCE(SUM(size_bytes), 0) FROM documents WHERE is_trash = 1")
    fun observeTrashBytes(): Flow<Long>

    // ------------------------------------------------------------------ duplicates

    /**
     * Duplicate candidates. Grouping happens in SQLite on an indexed checksum column, so a
     * library with thousands of documents resolves in one pass (PRD §20).
     */
    @Query(
        """
        SELECT checksum_sha256 AS checksum, size_bytes AS size_bytes, COUNT(*) AS copy_count
        FROM documents
        WHERE is_trash = 0 AND checksum_sha256 IS NOT NULL
        GROUP BY checksum_sha256
        HAVING COUNT(*) > 1
        ORDER BY size_bytes DESC
        """,
    )
    suspend fun findDuplicateGroups(): List<DuplicateGroupRow>

    @Query("SELECT * FROM documents WHERE checksum_sha256 IN (:checksums) AND is_trash = 0")
    suspend fun getDuplicatesForChecksums(checksums: List<String>): List<DocumentEntity>

    // ------------------------------------------------------------------ mutations

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(documents: List<DocumentEntity>)

    @Update
    suspend fun update(document: DocumentEntity)

    @Query("UPDATE documents SET display_name = :newName, extension = :extension, modified_at = :modifiedAt WHERE id = :id")
    suspend fun rename(id: String, newName: String, extension: String, modifiedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET is_favorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE documents SET is_favorite = :isFavorite WHERE id IN (:ids)")
    suspend fun setFavoriteForIds(ids: List<String>, isFavorite: Boolean)

    @Query("UPDATE documents SET is_pinned = :isPinned WHERE id = :id")
    suspend fun setPinned(id: String, isPinned: Boolean)

    @Query("UPDATE documents SET is_archived = :isArchived WHERE id IN (:ids)")
    suspend fun setArchived(ids: List<String>, isArchived: Boolean)

    @Query("UPDATE documents SET is_trash = 1, trashed_at = :trashedAt WHERE id IN (:ids)")
    suspend fun moveToTrash(ids: List<String>, trashedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET is_trash = 0, trashed_at = NULL WHERE id IN (:ids)")
    suspend fun restoreFromTrash(ids: List<String>)

    @Query("SELECT * FROM documents WHERE is_trash = 1 AND trashed_at IS NOT NULL AND trashed_at < :cutoff")
    suspend fun getTrashedBefore(cutoff: Long): List<DocumentEntity>

    @Query("UPDATE documents SET last_opened_at = :openedAt WHERE id = :id")
    suspend fun updateLastOpened(id: String, openedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: String, notes: String)

    @Query("UPDATE documents SET folder_id = :folderId WHERE id IN (:ids)")
    suspend fun moveToFolder(ids: List<String>, folderId: String?)

    @Query("UPDATE documents SET folder_id = NULL WHERE folder_id = :folderId")
    suspend fun detachFromFolder(folderId: String)

    // ------------------------------------------------------- background work state

    @Query(
        "SELECT * FROM documents WHERE is_trash = 0 AND " +
            "(thumbnail_path IS NULL OR checksum_sha256 IS NULL) LIMIT :limit",
    )
    suspend fun getPendingEnrichment(limit: Int = 8): List<DocumentEntity>

    @Query(
        "SELECT * FROM documents WHERE is_trash = 0 AND text_state IN ('NOT_PROCESSED', 'FAILED') " +
            "AND document_type IN ('PDF', 'TEXT', 'WORD_DOCUMENT', 'SPREADSHEET', 'PRESENTATION') " +
            "ORDER BY modified_at DESC LIMIT :limit",
    )
    suspend fun getPendingTextExtraction(limit: Int = 5): List<DocumentEntity>

    @Query(
        "SELECT * FROM documents WHERE is_trash = 0 AND ocr_state IN ('NOT_PROCESSED', 'FAILED') " +
            "AND document_type IN ('PDF', 'IMAGE') ORDER BY modified_at DESC LIMIT :limit",
    )
    suspend fun getPendingTextRecognition(limit: Int = 5): List<DocumentEntity>

    @Query("UPDATE documents SET text_state = :state, text_origin = :origin, indexed_at = :indexedAt WHERE id = :id")
    suspend fun updateTextState(id: String, state: TextIndexStatus, origin: TextOrigin, indexedAt: Long?)

    @Query("UPDATE documents SET ocr_state = :state, ocr_completed_at = :completedAt WHERE id = :id")
    suspend fun updateOcrState(id: String, state: OcrStatus, completedAt: Long? = null)

    @Query("UPDATE documents SET thumbnail_path = :path WHERE id = :id")
    suspend fun setThumbnailPath(id: String, path: String?)

    @Query("UPDATE documents SET checksum_sha256 = :checksum WHERE id = :id")
    suspend fun setChecksum(id: String, checksum: String)

    @Query("UPDATE documents SET page_count = :pageCount, width = :width, height = :height WHERE id = :id")
    suspend fun setGeometry(id: String, pageCount: Int?, width: Int?, height: Int?)

    @Query("UPDATE documents SET category = :category WHERE id = :id")
    suspend fun setCategory(id: String, category: DocumentCategory?)

    @Query("UPDATE documents SET size_bytes = :sizeBytes, modified_at = :modifiedAt WHERE id = :id")
    suspend fun updateFileFacts(id: String, sizeBytes: Long, modifiedAt: Long)

    // ------------------------------------------------------------------ deletion

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM documents WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM documents WHERE is_trash = 1")
    suspend fun emptyTrash()
}


