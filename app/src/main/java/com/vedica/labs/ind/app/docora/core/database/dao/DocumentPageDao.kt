package com.vedica.labs.ind.app.docora.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vedica.labs.ind.app.docora.core.database.entity.DocumentPageEntity
import kotlinx.coroutines.flow.Flow

/**
 * Access to per-page OCR extractions and search tokens.
 */
@Dao
interface DocumentPageDao {

    @Query("SELECT * FROM document_pages WHERE document_id = :documentId ORDER BY page_number ASC")
    fun observePagesForDocument(documentId: String): Flow<List<DocumentPageEntity>>

    @Query("SELECT * FROM document_pages WHERE document_id = :documentId ORDER BY page_number ASC")
    suspend fun getPagesForDocument(documentId: String): List<DocumentPageEntity>

    @Query("SELECT * FROM document_pages WHERE document_id = :documentId AND page_number = :pageNumber LIMIT 1")
    suspend fun getPage(documentId: String, pageNumber: Int): DocumentPageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(page: DocumentPageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(pages: List<DocumentPageEntity>)

    @Query("DELETE FROM document_pages WHERE document_id = :documentId")
    suspend fun deleteForDocument(documentId: String)

    @Query(
        """
        SELECT document_id FROM document_pages
        WHERE plain_text_searchable LIKE '%' || :query || '%'
        LIMIT :limit
        """,
    )
    suspend fun searchPageText(query: String, limit: Int = 50): List<String>

    @Query("SELECT COUNT(*) FROM document_pages WHERE document_id = :documentId")
    suspend fun pageCount(documentId: String): Int

    /** Drops pages beyond [keepCount] after a document shrinks (re-import of a shorter file). */
    @Query("DELETE FROM document_pages WHERE document_id = :documentId AND page_number >= :keepCount")
    suspend fun deletePagesFrom(documentId: String, keepCount: Int)

    /**
     * Page text search scoped to the documents the user can see. Joined in SQL so the caller
     * receives document ids directly instead of re-querying per page.
     */
    @Query(
        """
        SELECT DISTINCT p.document_id FROM document_pages p
        INNER JOIN documents d ON d.id = p.document_id
        WHERE d.is_trash = 0 AND p.plain_text_searchable LIKE '%' || :query || '%'
        LIMIT :limit
        """,
    )
    suspend fun searchVisiblePageText(query: String, limit: Int = 50): List<String>

    @Query("SELECT COALESCE(SUM(LENGTH(plain_text_searchable)), 0) FROM document_pages")
    suspend fun totalIndexedTextCharacters(): Long
}

