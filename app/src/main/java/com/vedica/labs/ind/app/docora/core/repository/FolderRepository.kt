package com.vedica.labs.ind.app.docora.core.repository

import com.vedica.labs.ind.app.docora.core.common.DocoraError
import com.vedica.labs.ind.app.docora.core.common.DocoraResult
import com.vedica.labs.ind.app.docora.core.common.DispatcherProvider
import com.vedica.labs.ind.app.docora.core.common.docoraRunCatching
import com.vedica.labs.ind.app.docora.core.database.dao.DocumentDao
import com.vedica.labs.ind.app.docora.core.database.dao.FolderDao
import com.vedica.labs.ind.app.docora.core.database.entity.FolderEntity
import com.vedica.labs.ind.app.docora.core.database.mapper.toDomain
import com.vedica.labs.ind.app.docora.core.database.query.DocumentQueryBuilder
import com.vedica.labs.ind.app.docora.core.model.DocumentSort
import com.vedica.labs.ind.app.docora.core.model.Folder
import com.vedica.labs.ind.app.docora.core.model.FolderWithCount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Folder operations.
 *
 * Folders are virtual by design (PRD §18): a folder groups documents that may physically live in
 * completely different SAF locations. Only folders created inside a real device location carry a
 * `treeUri`, and only those can move the underlying file.
 */
interface FolderRepository {

    fun observeRootFolders(): Flow<List<FolderWithCount>>

    fun observeAllFolders(): Flow<List<FolderWithCount>>

    fun observeSubfolders(parentId: String): Flow<List<FolderWithCount>>

    fun observeFolder(folderId: String): Flow<Folder?>

    suspend fun getFolder(folderId: String): Folder?

    /** Creates a folder, de-duplicating the name against its siblings instead of failing. */
    suspend fun createFolder(
        name: String,
        parentId: String? = null,
        treeUri: String? = null,
    ): DocoraResult<Folder>

    suspend fun rename(folderId: String, newName: String): DocoraResult<Folder>

    /**
     * Deletes a folder. Documents inside it are detached (never deleted) unless
     * [moveDocumentsToTrash] is requested, and the caller always confirms first.
     */
    suspend fun delete(folderId: String, moveDocumentsToTrash: Boolean = false): DocoraResult<Unit>

    suspend fun setFavorite(folderId: String, isFavorite: Boolean)

    /** Maps a SAF tree the user granted to a folder, creating it the first time. */
    suspend fun findOrCreateForTree(
        displayName: String,
        treeUri: String,
        parentId: String? = null,
    ): DocoraResult<Folder>
}

@Singleton
class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao,
    private val documentDao: DocumentDao,
    private val dispatchers: DispatcherProvider,
) : FolderRepository {

    override fun observeRootFolders(): Flow<List<FolderWithCount>> =
        folderDao.observeRootFolders().withCounts()

    override fun observeAllFolders(): Flow<List<FolderWithCount>> =
        folderDao.observeAll().withCounts()

    override fun observeSubfolders(parentId: String): Flow<List<FolderWithCount>> =
        folderDao.observeSubfolders(parentId).withCounts()

    override fun observeFolder(folderId: String): Flow<Folder?> =
        folderDao.observeById(folderId).map { it?.toDomain() }

    override suspend fun getFolder(folderId: String): Folder? = withContext(dispatchers.io) {
        folderDao.getById(folderId)?.toDomain()
    }

    override suspend fun createFolder(
        name: String,
        parentId: String?,
        treeUri: String?,
    ): DocoraResult<Folder> = withContext(dispatchers.io) {
        docoraRunCatching {
            val cleanName = requireName(name)
            if (treeUri != null) {
                folderDao.findByTreeUri(treeUri)?.let { return@docoraRunCatching it.toDomain() }
            }
            val entity = FolderEntity(
                id = UUID.randomUUID().toString(),
                name = uniqueSiblingName(cleanName, parentId),
                parentId = parentId,
                treeUri = treeUri,
                createdAt = System.currentTimeMillis(),
            )
            folderDao.upsert(entity)
            entity.toDomain()
        }
    }

    override suspend fun rename(folderId: String, newName: String): DocoraResult<Folder> =
        withContext(dispatchers.io) {
            docoraRunCatching {
                val existing = folderDao.getById(folderId) ?: throw DocoraError.DocumentMissing()
                val cleanName = requireName(newName)
                val unique = uniqueSiblingName(cleanName, existing.parentId, excludeId = folderId)
                folderDao.rename(folderId, unique)
                existing.copy(name = unique).toDomain()
            }
        }

    override suspend fun delete(
        folderId: String,
        moveDocumentsToTrash: Boolean,
    ): DocoraResult<Unit> = withContext(dispatchers.io) {
        docoraRunCatching {
            // Detaching and trashing are two explicit branches so the destructive choice stays
            // visible: deleting a folder never deletes documents unless the user asked for it.
            if (moveDocumentsToTrash) {
                val ids = documentDao.rawDocuments(
                    DocumentQueryBuilder.folderQuery(
                        folderId = folderId,
                        sort = DocumentSort.Default,
                        limit = MAX_FOLDER_CLEAR,
                    ),
                ).map { it.id }
                if (ids.isNotEmpty()) documentDao.moveToTrash(ids)
            } else {
                documentDao.detachFromFolder(folderId)
            }
            folderDao.detachChildren(folderId)
            folderDao.deleteById(folderId)
        }
    }

    override suspend fun setFavorite(folderId: String, isFavorite: Boolean) =
        withContext(dispatchers.io) { folderDao.setFavorite(folderId, isFavorite) }

    override suspend fun findOrCreateForTree(
        displayName: String,
        treeUri: String,
        parentId: String?,
    ): DocoraResult<Folder> = withContext(dispatchers.io) {
        docoraRunCatching {
            folderDao.findByTreeUri(treeUri)?.let { return@docoraRunCatching it.toDomain() }
            val fallbackName = displayName.substringAfterLast('/').trim().ifEmpty { "Storage" }
            val entity = FolderEntity(
                id = UUID.randomUUID().toString(),
                name = uniqueSiblingName(requireName(fallbackName), parentId),
                parentId = parentId,
                treeUri = treeUri,
                createdAt = System.currentTimeMillis(),
            )
            folderDao.upsert(entity)
            entity.toDomain()
        }
    }

    /** Folds folders together with their document counts, without a query per row. */
    private fun Flow<List<FolderEntity>>.withCounts(): Flow<List<FolderWithCount>> =
        combine(folderDao.observeDocumentCountsPerFolder()) { folders, counts ->
            val countsById = counts.associate { it.folderId to it.documentCount }
            folders.map { entity ->
                FolderWithCount(
                    folder = entity.toDomain(),
                    documentCount = countsById[entity.id] ?: 0,
                )
            }
        }

    private fun requireName(name: String): String {
        val clean = name.trim().replace(Regex("\\s+"), " ").take(60)
        if (clean.isEmpty()) throw DocoraError.Unexpected(IllegalArgumentException("empty folder name"))
        return clean
    }

    private suspend fun uniqueSiblingName(
        name: String,
        parentId: String?,
        excludeId: String? = null,
    ): String {
        if (excludeId != null) {
            // Renaming to the folder's own name must not create "Name (1)".
            folderDao.getById(excludeId)?.let { if (it.name == name) return name }
        }
        if (folderDao.countSiblingsNamed(name, parentId) == 0) return name
        var counter = 1
        while (counter < MAX_NAME_ATTEMPTS) {
            val candidate = "$name ($counter)"
            if (folderDao.countSiblingsNamed(candidate, parentId) == 0) return candidate
            counter++
        }
        return "$name ${System.currentTimeMillis()}"
    }

    private companion object {
        const val MAX_NAME_ATTEMPTS = 500
        const val MAX_FOLDER_CLEAR = 20_000
    }
}
