package com.vedica.labs.ind.app.docora.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vedica.labs.ind.app.docora.core.database.entity.DocumentTagCrossRef
import com.vedica.labs.ind.app.docora.core.database.entity.TagEntity
import com.vedica.labs.ind.app.docora.core.database.model.TagCountRow
import kotlinx.coroutines.flow.Flow

/**
 * Access to user tags and document-tag associations.
 */
@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TagEntity?

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTagToDocument(ref: DocumentTagCrossRef)

    @Query("DELETE FROM document_tag_cross_ref WHERE document_id = :documentId AND tag_id = :tagId")
    suspend fun removeTagFromDocument(documentId: String, tagId: String)

    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN document_tag_cross_ref r ON t.id = r.tag_id
        WHERE r.document_id = :documentId
        ORDER BY t.name ASC
    """)
    fun observeTagsForDocument(documentId: String): Flow<List<TagEntity>>

    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN document_tag_cross_ref r ON t.id = r.tag_id
        WHERE r.document_id = :documentId
        ORDER BY t.name ASC
    """)
    suspend fun getTagsForDocument(documentId: String): List<TagEntity>

    @Query("""
        SELECT document_id FROM document_tag_cross_ref
        WHERE tag_id = :tagId
    """)
    suspend fun getDocumentIdsForTag(tagId: String): List<String>

    /** Tag usage counts resolved in SQL so the tag sheet never loops over documents. */
    @Query(
        """
        SELECT r.tag_id AS tag_id, COUNT(*) AS document_count
        FROM document_tag_cross_ref r
        INNER JOIN documents d ON d.id = r.document_id AND d.is_trash = 0
        GROUP BY r.tag_id
        """,
    )
    fun observeTagUsage(): Flow<List<TagCountRow>>

    @Query("UPDATE tags SET name = :newName WHERE id = :id")
    suspend fun rename(id: String, newName: String)

    @Query("SELECT COUNT(*) FROM document_tag_cross_ref WHERE tag_id = :tagId")
    suspend fun countDocumentsForTag(tagId: String): Int

    @Query("DELETE FROM document_tag_cross_ref WHERE tag_id = :tagId")
    suspend fun clearTagAssignments(tagId: String)

    @Query("DELETE FROM document_tag_cross_ref WHERE document_id = :documentId")
    suspend fun clearDocumentTags(documentId: String)

    @Query("INSERT OR IGNORE INTO document_tag_cross_ref(document_id, tag_id) VALUES (:documentId, :tagId)")
    suspend fun assignTag(documentId: String, tagId: String)
}

