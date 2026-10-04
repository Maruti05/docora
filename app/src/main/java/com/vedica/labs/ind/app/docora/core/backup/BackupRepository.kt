package com.vedica.labs.ind.app.docora.core.backup

import android.net.Uri
import com.vedica.labs.ind.app.docora.BuildConfig
import com.vedica.labs.ind.app.docora.core.common.DocoraError
import com.vedica.labs.ind.app.docora.core.common.DocoraResult
import com.vedica.labs.ind.app.docora.core.common.DispatcherProvider
import com.vedica.labs.ind.app.docora.core.common.docoraRunCatching
import com.vedica.labs.ind.app.docora.core.model.Folder
import com.vedica.labs.ind.app.docora.core.model.SmartCollection
import com.vedica.labs.ind.app.docora.core.model.Tag
import com.vedica.labs.ind.app.docora.core.repository.DocumentRepository
import com.vedica.labs.ind.app.docora.core.repository.FolderRepository
import com.vedica.labs.ind.app.docora.core.repository.SearchRepository
import com.vedica.labs.ind.app.docora.core.repository.TagRepository
import com.vedica.labs.ind.app.docora.core.storage.SafGateway
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Metadata backup and restore (PRD §50).
 *
 * The user picks the file through the Storage Access Framework, so Docora never needs a storage
 * permission for this: the one file the user explicitly chose is the only one that is read or
 * written. Both directions are safe to repeat. Export overwrites nothing else, and restore *merges*:
 * a folder or tag that already exists is reused, and a document annotation is only applied to a
 * document the library still knows about.
 */
interface BackupRepository {

    /** Writes a JSON snapshot of folders, tags and document metadata to [uri]. */
    suspend fun exportTo(uri: Uri): DocoraResult<BackupSummary>

    /** Merges a snapshot written by [exportTo] back into the library. */
    suspend fun restoreFrom(uri: Uri): DocoraResult<RestoreSummary>
}

/** How many folders/tags a merge reused or created. */
private data class MergeResult(
    val ids: Map<String, String>,
    val created: Int,
)

@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val safGateway: SafGateway,
    private val documentRepository: DocumentRepository,
    private val folderRepository: FolderRepository,
    private val tagRepository: TagRepository,
    private val searchRepository: SearchRepository,
    private val dispatchers: DispatcherProvider,
) : BackupRepository {

    override suspend fun exportTo(uri: Uri): DocoraResult<BackupSummary> =
        withContext(dispatchers.io) {
            docoraRunCatching {
                val folders = folderRepository.observeAllFolders().first().map { it.folder }
                val tags = tagRepository.observeTags().first().map { it.tag }
                val documents = documentRepository
                    .observeCollection(SmartCollection.ALL, limit = MAX_BACKUP_DOCUMENTS)
                    .first()

                val payload = BackupFile(
                    exportedAt = System.currentTimeMillis(),
                    appVersion = BuildConfig.VERSION_NAME,
                    folders = folders.map(::toBackupFolder),
                    tags = tags.map(::toBackupTag),
                    documents = documents.map { document ->
                        BackupDocument(
                            uri = document.uri,
                            displayName = document.displayName,
                            mimeType = document.mimeType,
                            sizeBytes = document.sizeBytes,
                            folderId = document.folderId,
                            isFavorite = document.isFavorite,
                            category = document.category?.name,
                            tagIds = tagRepository.getTagsForDocument(document.id).map { it.id },
                        )
                    },
                )

                val stream = safGateway.openOutputStream(uri)
                    ?: throw DocoraError.StorageUnavailable()
                stream.bufferedWriter().use { it.write(JSON.encodeToString(payload)) }

                BackupSummary(
                    folders = payload.folders.size,
                    tags = payload.tags.size,
                    documents = payload.documents.size,
                )
            }
        }

    private fun toBackupFolder(folder: Folder) = BackupFolder(
        id = folder.id,
        name = folder.name,
        parentId = folder.parentId,
        treeUri = folder.treeUri,
        createdAt = folder.createdAt,
    )

    private fun toBackupTag(tag: Tag) = BackupTag(
        id = tag.id,
        name = tag.name,
        colorHex = tag.colorHex,
        createdAt = tag.createdAt,
    )

    override suspend fun restoreFrom(uri: Uri): DocoraResult<RestoreSummary> =
        withContext(dispatchers.io) {
            // Specific DocoraErrors thrown below are preserved; everything else is wrapped, so the
            // screen can tell "not a Docora backup" apart from a storage failure.
            docoraRunCatching(errorFactory = { throwable ->
                (throwable as? DocoraError) ?: DocoraError.Unexpected(throwable)
            }) {
                val raw = safGateway.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw DocoraError.StorageUnavailable()
                val payload = runCatching { JSON.decodeFromString<BackupFile>(raw) }
                    .getOrElse { throw DocoraError.UnsupportedFormat(BackupFile.MIME_TYPE) }
                if (payload.format != BackupFile.FORMAT || payload.version > BackupFile.VERSION) {
                    throw DocoraError.UnsupportedFormat(payload.format)
                }

                val folders = mergeFolders(payload.folders)
                val tags = mergeTags(payload.tags)
                val documents = mergeDocuments(payload.documents, folders.ids, tags.ids)

                RestoreSummary(
                    folders = folders.created,
                    tags = tags.created,
                    documents = documents,
                )
            }
        }

    /**
     * Recreates the folder tree.
     *
     * Parents are processed before children (a backup is a flat list), an existing folder with the
     * same name under the same parent is reused instead of duplicated, and a child whose parent is
     * missing from the backup is restored at the root rather than dropped.
     */
    private suspend fun mergeFolders(entries: List<BackupFolder>): MergeResult {
        val known = folderRepository.observeAllFolders().first()
            .map { it.folder }
            .associateBy { folderKey(it.parentId, it.name) }
            .toMutableMap()
        val ids = mutableMapOf<String, String>()
        var created = 0

        orderParentsFirst(entries).forEach { entry ->
            val name = entry.name.trim()
            if (name.isEmpty()) return@forEach
            val parentId = entry.parentId?.let { ids[it] }
            known[folderKey(parentId, name)]?.let { match ->
                ids[entry.id] = match.id
                return@forEach
            }
            val folder = folderRepository
                .createFolder(name = name, parentId = parentId, treeUri = entry.treeUri)
                .getOrNull()
                ?: return@forEach
            ids[entry.id] = folder.id
            known[folderKey(folder.parentId, folder.name)] = folder
            created++
        }
        return MergeResult(ids, created)
    }

    /** Tag names are unique case-insensitively, matching how the tag picker behaves. */
    private suspend fun mergeTags(entries: List<BackupTag>): MergeResult {
        val known = tagRepository.observeTags().first()
            .map { it.tag }
            .associateBy { it.name.lowercase() }
            .toMutableMap()
        val ids = mutableMapOf<String, String>()
        var created = 0

        entries.forEach { entry ->
            val name = entry.name.trim()
            if (name.isEmpty()) return@forEach
            known[name.lowercase()]?.let { match ->
                ids[entry.id] = match.id
                return@forEach
            }
            val tag = tagRepository.createTag(name).getOrNull() ?: return@forEach
            ids[entry.id] = tag.id
            known[tag.name.lowercase()] = tag
            created++
        }
        return MergeResult(ids, created)
    }

    /**
     * Re-applies folder, favourite and tag annotations.
     *
     * A document that is no longer in the library is skipped: its URI does not resolve, and
     * importing metadata for a file that is not there would only create a broken row.
     */
    private suspend fun mergeDocuments(
        entries: List<BackupDocument>,
        folderIds: Map<String, String>,
        tagIds: Map<String, String>,
    ): Int {
        var applied = 0
        entries.forEach { entry ->
            val document = documentRepository.getDocumentByUri(entry.uri) ?: return@forEach
            val folderId = entry.folderId?.let { folderIds[it] }
            var touched = false

            if (folderId != null && folderId != document.folderId) {
                documentRepository.moveToFolder(listOf(document.id), folderId)
                touched = true
            }
            if (entry.isFavorite && !document.isFavorite) {
                documentRepository.setFavorite(listOf(document.id), true)
                touched = true
            }
            val restoredTagIds = entry.tagIds.mapNotNull { tagIds[it] }
            if (restoredTagIds.isNotEmpty()) {
                tagRepository.setDocumentTags(document.id, restoredTagIds)
                touched = true
            }
            if (touched) {
                applied++
                // The index stores tag names next to the document, so it has to be refreshed too.
                documentRepository.getDocument(document.id)?.let { refreshed ->
                    runCatching { searchRepository.indexDocument(refreshed) }
                }
            }
        }
        return applied
    }

    /** Depth-first ordering so a parent always exists before its children are recreated. */
    private fun orderParentsFirst(entries: List<BackupFolder>): List<BackupFolder> {
        val byId = entries.associateBy { it.id }
        fun depth(entry: BackupFolder): Int {
            var steps = 0
            var parent = entry.parentId
            val seen = mutableSetOf<String>()
            while (parent != null && seen.add(parent)) {
                steps++
                parent = byId[parent]?.parentId
            }
            return steps
        }
        return entries.sortedBy { depth(it) }
    }

    /** Identity of a folder inside its parent, used to match a backup entry to a live folder. */
    private fun folderKey(parentId: String?, name: String): String =
        "${parentId.orEmpty()}\u0000${name.trim()}"

    private companion object {
        val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }

        /** A snapshot is bounded so a runaway library cannot exhaust memory mid-export. */
        const val MAX_BACKUP_DOCUMENTS = 20_000
    }
}
