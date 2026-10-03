package com.vedica.labs.ind.app.docora.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vedica.labs.ind.app.docora.core.database.entity.FolderEntity
import com.vedica.labs.ind.app.docora.core.database.model.FolderCountRow
import kotlinx.coroutines.flow.Flow

/**
 * Access to persisted folder structures.
 */
@Dao
interface FolderDao {

    @Query("SELECT * FROM folders WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<FolderEntity?>

    @Query("SELECT * FROM folders WHERE tree_uri = :treeUri LIMIT 1")
    suspend fun getByTreeUri(treeUri: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE parent_id IS NULL ORDER BY name ASC")
    fun observeRootFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE parent_id = :parentId ORDER BY name ASC")
    fun observeSubfolders(parentId: String): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE is_favorite = 1 ORDER BY name ASC")
    fun observeFavorites(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: FolderEntity)

    @Update
    suspend fun update(folder: FolderEntity)

    @Query("UPDATE folders SET name = :newName WHERE id = :id")
    suspend fun rename(id: String, newName: String)

    @Query("UPDATE folders SET is_favorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Counts direct children so the folder browser can show them without a per-row query. */
    @Query(
        """
        SELECT folder_id AS folder_id, COUNT(*) AS document_count FROM documents
        WHERE is_trash = 0 AND is_archived = 0 AND folder_id IS NOT NULL
        GROUP BY folder_id
        """,
    )
    fun observeDocumentCountsPerFolder(): Flow<List<FolderCountRow>>

    /** Resolves a virtual folder from the SAF tree it mirrors, if any. */
    @Query("SELECT * FROM folders WHERE tree_uri = :treeUri LIMIT 1")
    suspend fun findByTreeUri(treeUri: String): FolderEntity?

    @Query("SELECT COUNT(*) FROM folders WHERE parent_id IS :parentId AND name = :name")
    suspend fun countSiblingsNamed(name: String, parentId: String?): Int

    @Query("UPDATE folders SET parent_id = NULL WHERE parent_id = :parentId")
    suspend fun detachChildren(parentId: String)
}

