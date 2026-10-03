package com.vedica.labs.ind.app.docora.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.vedica.labs.ind.app.docora.core.database.model.SearchRow
import com.vedica.labs.ind.app.docora.core.model.DocumentType

/**
 * Full-text search over the `documents_fts` index (PRD §8, §25).
 *
 * The index is a standalone FTS4 table, so every write is explicit: [upsertIndexRow] replaces
 * the row for a document inside one transaction. That guarantees the index can never hold two
 * rows for the same document and can never lag behind an edited title or note.
 *
 * Query shape notes:
 *  * `:types` is always non-empty. The caller substitutes every [DocumentType] when the user
 *    has not filtered by type, which keeps the statement free of an empty `IN ()` list - a
 *    construct SQLite rejects.
 *  * `:folderIds`/`:tagIds` follow the same rule and are additionally guarded by
 *    `:useFolderFilter`/`:useTagFilter`, so an unfiltered search never pays for the subquery.
 *  * Snippets are built in Kotlin from the stored body because FTS4's `snippet()` output is not
 *    highlighter friendly; the body is selected alongside the row so no second query is needed
 *    per result.
 */
@Dao
interface SearchDao {

    @Query(
        """
        INSERT INTO documents_fts(doc_id, title, body, notes, tags, category)
        VALUES (:docId, :title, :body, :notes, :tags, :category)
        """,
    )
    suspend fun insertIndexRow(
        docId: String,
        title: String,
        body: String,
        notes: String,
        tags: String,
        category: String,
    )

    @Query("DELETE FROM documents_fts WHERE doc_id = :docId")
    suspend fun deleteIndexRow(docId: String)

    /** Replaces the index row of one document atomically. */
    @Transaction
    suspend fun upsertIndexRow(
        docId: String,
        title: String,
        body: String,
        notes: String,
        tags: String,
        category: String,
    ) {
        deleteIndexRow(docId)
        insertIndexRow(docId, title, body, notes, tags, category)
    }

    @Query("DELETE FROM documents_fts")
    suspend fun clearIndex()

    @Query("SELECT COUNT(*) FROM documents_fts")
    suspend fun indexedRowCount(): Int

    @Query("SELECT COUNT(*) FROM documents WHERE is_trash = 0")
    suspend fun activeDocumentCount(): Int

    @Query(
        """
        SELECT d.*, f.body AS search_body
        FROM documents_fts f
        JOIN documents d ON d.id = f.doc_id
        WHERE documents_fts MATCH :matchQuery
          AND d.is_trash = :trashFlag
          AND d.document_type IN (:types)
          AND (:favoritesOnly = 0 OR d.is_favorite = 1)
          AND (:modifiedAfter = 0 OR d.modified_at >= :modifiedAfter)
          AND (:useFolderFilter = 0 OR d.folder_id IN (:folderIds))
          AND (:useTagFilter = 0 OR EXISTS (
                SELECT 1 FROM document_tag_cross_ref r
                WHERE r.document_id = d.id AND r.tag_id IN (:tagIds)
          ))
        ORDER BY d.is_pinned DESC, d.modified_at DESC
        LIMIT :limit
        """,
    )
    suspend fun search(
        matchQuery: String,
        types: List<DocumentType>,
        trashFlag: Int,
        favoritesOnly: Int,
        modifiedAfter: Long,
        useFolderFilter: Int,
        folderIds: List<String>,
        useTagFilter: Int,
        tagIds: List<String>,
        limit: Int,
    ): List<SearchRow>

    /**
     * Fallback for input the FTS grammar cannot express (a lone `"` or `*`, for example). It is
     * a LIKE scan, so it is only ever a second attempt - never the primary path.
     */
    @Query(
        """
        SELECT d.*, '' AS search_body FROM documents d
        WHERE (d.display_name LIKE '%' || :query || '%' OR d.notes LIKE '%' || :query || '%')
          AND d.is_trash = :trashFlag
          AND d.document_type IN (:types)
        ORDER BY d.modified_at DESC
        LIMIT :limit
        """,
    )
    suspend fun searchFallback(
        query: String,
        types: List<DocumentType>,
        trashFlag: Int,
        limit: Int,
    ): List<SearchRow>

    @Query("SELECT body FROM documents_fts WHERE doc_id = :docId LIMIT 1")
    suspend fun indexedBody(docId: String): String?
}
