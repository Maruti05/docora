package com.vedica.labs.ind.app.docora.core.repository

import com.vedica.labs.ind.app.docora.core.common.DocoraError
import com.vedica.labs.ind.app.docora.core.common.DocoraResult
import com.vedica.labs.ind.app.docora.core.common.DispatcherProvider
import com.vedica.labs.ind.app.docora.core.common.docoraRunCatching
import com.vedica.labs.ind.app.docora.core.database.dao.DocumentDao
import com.vedica.labs.ind.app.docora.core.database.dao.SearchDao
import com.vedica.labs.ind.app.docora.core.database.dao.TagDao
import com.vedica.labs.ind.app.docora.core.database.entity.DocumentEntity
import com.vedica.labs.ind.app.docora.core.database.mapper.buildSearchIndexPayload
import com.vedica.labs.ind.app.docora.core.database.mapper.toDomain
import com.vedica.labs.ind.app.docora.core.database.model.DashboardStatsRow
import com.vedica.labs.ind.app.docora.core.database.model.StorageBreakdownRow
import com.vedica.labs.ind.app.docora.core.database.query.DocumentQueryBuilder
import com.vedica.labs.ind.app.docora.core.files.ThumbnailStore
import com.vedica.labs.ind.app.docora.core.model.DashboardStats
import com.vedica.labs.ind.app.docora.core.model.Document
import com.vedica.labs.ind.app.docora.core.model.DocumentCategory
import com.vedica.labs.ind.app.docora.core.model.DocumentFilter
import com.vedica.labs.ind.app.docora.core.model.DocumentImportRequest
import com.vedica.labs.ind.app.docora.core.model.DocumentSort
import com.vedica.labs.ind.app.docora.core.model.DuplicateGroup
import com.vedica.labs.ind.app.docora.core.model.DuplicateScanResult
import com.vedica.labs.ind.app.docora.core.model.LargestFileItem
import com.vedica.labs.ind.app.docora.core.model.OcrStatus
import com.vedica.labs.ind.app.docora.core.model.SmartCollection
import com.vedica.labs.ind.app.docora.core.model.SortDirection
import com.vedica.labs.ind.app.docora.core.model.SortOrder
import com.vedica.labs.ind.app.docora.core.model.StorageBreakdown
import com.vedica.labs.ind.app.docora.core.model.TextIndexStatus
import com.vedica.labs.ind.app.docora.core.model.TextOrigin
import com.vedica.labs.ind.app.docora.core.util.DocumentLimits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Document metadata repository.
 *
 * Responsibilities kept here on purpose:
 *  * entity <-> domain mapping, so nothing outside the data layer ever sees a Room row,
 *  * keeping the full-text index honest whenever a field that is part of the index changes
 *    (title, notes, category, tags) - search must never answer from a stale title,
 *  * removing sandbox copies when a document Docora created is deleted permanently, so the app
 *    never leaks its own files.
 */
@Singleton
class DocumentRepositoryImpl @Inject constructor(
    private val documentDao: DocumentDao,
    private val searchDao: SearchDao,
    private val tagDao: TagDao,
    private val thumbnailStore: ThumbnailStore,
    private val dispatchers: DispatcherProvider,
) : DocumentRepository {

    private suspend fun <T> io(block: suspend () -> T): T = withContext(dispatchers.io) { block() }

    // ------------------------------------------------------------------ reads

    override fun observeDocument(id: String): Flow<Document?> =
        documentDao.observeById(id).mapLatest { it?.toDomain() }

    override suspend fun getDocument(id: String): Document? = io {
        documentDao.getById(id)?.toDomain()
    }

    override suspend fun getDocumentByUri(uri: String): Document? = io {
        documentDao.getByUri(uri)?.toDomain()
    }

    override suspend fun getDocuments(ids: List<String>): List<Document> = io {
        if (ids.isEmpty()) emptyList() else documentDao.getByIds(ids).toDomain()
    }

    override fun observeCollection(
        collection: SmartCollection,
        filter: DocumentFilter,
        sort: DocumentSort,
        limit: Int,
        searchQuery: String?,
    ): Flow<List<Document>> =
        documentDao.observeRawDocuments(
            DocumentQueryBuilder.collectionQuery(
                collection = collection,
                filter = filter,
                sort = defaultSortFor(collection, sort),
                limit = limit,
                searchQuery = searchQuery,
            ),
        ).mapLatest { entities -> entities.toDomain() }

    override fun observeFolder(
        folderId: String?,
        sort: DocumentSort,
        limit: Int,
    ): Flow<List<Document>> =
        documentDao.observeRawDocuments(
            DocumentQueryBuilder.folderQuery(folderId = folderId, sort = sort, limit = limit),
        ).mapLatest { entities -> entities.toDomain() }

    override fun observeDashboard(): Flow<DashboardStats> =
        documentDao.observeDashboardStats().mapLatest { it.toDomain() }

    override fun observeStorageBreakdown(): Flow<StorageBreakdown> =
        documentDao.observeStorageBreakdown().mapLatest { row ->
            // The thumbnail cache lives on the file system, so it is measured here rather than in
            // SQL; doing it on the IO dispatcher keeps the collecting thread (main) free.
            row.toDomain(thumbnailCacheBytes = io { thumbnailStore.cacheSizeBytes() })
        }

    override fun observeCategoryCounts(): Flow<Map<DocumentCategory, Int>> =
        documentDao.observeCategoryCounts().mapLatest { rows ->
            rows.associate { DocumentCategory.fromName(it.category) to it.documentCount }
        }

    override suspend fun findDuplicates(): DuplicateScanResult = io {
        val groups = documentDao.findDuplicateGroups()
        if (groups.isEmpty()) {
            DuplicateScanResult(emptyList())
        } else {
            val documentsByChecksum = documentDao
                .getDuplicatesForChecksums(groups.map { it.checksum })
                .groupBy { it.checksumSha256.orEmpty() }
            DuplicateScanResult(
                groups = groups.mapNotNull { row ->
                    val documents = documentsByChecksum[row.checksum].orEmpty().toDomain()
                    if (documents.size < 2) {
                        null
                    } else {
                        DuplicateGroup(
                            checksum = row.checksum,
                            sizeBytes = row.sizeBytes,
                            documents = documents.sortedBy { it.modifiedAt },
                        )
                    }
                },
            )
        }
    }

    override suspend fun getLargestFiles(limit: Int): List<LargestFileItem> = io {
        documentDao.rawDocuments(
            DocumentQueryBuilder.collectionQuery(
                collection = SmartCollection.ALL,
                filter = DocumentFilter.ALL,
                sort = DocumentSort(order = SortOrder.SIZE, direction = SortDirection.DESCENDING),
                limit = limit,
            ),
        ).map { entity ->
            LargestFileItem(
                documentId = entity.id,
                displayName = entity.displayName,
                sizeBytes = entity.sizeBytes,
                type = entity.documentType,
            )
        }
    }

    override suspend fun countInFolder(folderId: String): Int =
        io { documentDao.countInFolder(folderId) }

    // ------------------------------------------------------------------ writes

    override suspend fun import(request: DocumentImportRequest): Document = io {
        require(!DocumentLimits.isTooLargeToOpen(request.sizeBytes)) {
            "Document exceeds the maximum openable size"
        }
        val existing = documentDao.getById(request.stableId)
        val entity = DocumentEntity(
            id = request.stableId,
            uri = request.uri,
            parentTreeUri = request.parentTreeUri ?: existing?.parentTreeUri,
            providerAuthority = request.providerAuthority,
            displayName = request.displayName,
            extension = request.extension,
            folderId = request.folderId ?: existing?.folderId,
            mimeType = request.mimeType,
            documentType = request.type,
            sizeBytes = request.sizeBytes,
            createdAt = existing?.createdAt ?: request.createdAt,
            modifiedAt = request.modifiedAt,
            indexedAt = existing?.indexedAt,
            lastOpenedAt = existing?.lastOpenedAt,
            pageCount = request.pageCount,
            width = request.width,
            height = request.height,
            thumbnailPath = existing?.thumbnailPath,
            source = request.source,
            isFavorite = existing?.isFavorite ?: false,
            isPinned = existing?.isPinned ?: false,
            isArchived = false,
            isTrash = false,
            trashedAt = null,
            checksumSha256 = existing?.checksumSha256,
            textState = existing?.textState ?: TextIndexStatus.NOT_PROCESSED,
            textOrigin = existing?.textOrigin ?: TextOrigin.NONE,
            ocrState = existing?.ocrState ?: OcrStatus.NOT_PROCESSED,
            notes = existing?.notes.orEmpty(),
        )
        documentDao.upsert(entity)
        // The name is searchable the instant the document appears in the list, which is what makes
        // an import feel immediate even though text extraction runs later (PRD §43).
        writeIndexRow(entity)
        entity.toDomain()
    }

    override suspend fun importAll(requests: List<DocumentImportRequest>): List<Document> =
        requests.map { import(it) }

    override suspend fun rename(id: String, newDisplayName: String): DocoraResult<Document> = io {
        docoraRunCatching {
            val current = documentDao.getById(id) ?: throw DocoraError.DocumentMissing()
            val cleanName = newDisplayName.trim()
            require(cleanName.isNotEmpty()) { "File name cannot be empty" }
            val extension = current.extension
            val displayName = if (extension.isEmpty() || cleanName.endsWith(".$extension", true)) {
                cleanName
            } else {
                "$cleanName.$extension"
            }
            documentDao.rename(id, displayName, extension)
            val updated = documentDao.getById(id) ?: throw DocoraError.DocumentMissing()
            writeIndexRow(updated)
            updated.toDomain()
        }
    }

    override suspend fun setFavorite(ids: List<String>, isFavorite: Boolean) =
        io { if (ids.isNotEmpty()) documentDao.setFavoriteForIds(ids, isFavorite) }

    override suspend fun setPinned(id: String, isPinned: Boolean) =
        io { documentDao.setPinned(id, isPinned) }

    override suspend fun setArchived(ids: List<String>, isArchived: Boolean) =
        io { if (ids.isNotEmpty()) documentDao.setArchived(ids, isArchived) }

    override suspend fun moveToTrash(ids: List<String>) = io {
        if (ids.isEmpty()) return@io
        documentDao.moveToTrash(ids)
        // Trashed documents drop out of the index immediately so a search can never resurface
        // something the user just deleted.
        ids.forEach { id -> searchDao.deleteIndexRow(id) }
    }

    override suspend fun restore(ids: List<String>) = io {
        if (ids.isEmpty()) return@io
        documentDao.restoreFromTrash(ids)
        documentDao.getByIds(ids).forEach { entity -> writeIndexRow(entity) }
    }

    override suspend fun deletePermanently(ids: List<String>) = io {
        if (ids.isEmpty()) return@io
        val entities = documentDao.getByIds(ids)
        documentDao.deleteByIds(ids)
        entities.forEach { entity ->
            searchDao.deleteIndexRow(entity.id)
            thumbnailStore.delete(entity.id)
            deleteSandboxCopy(entity)
        }
    }

    override suspend fun emptyTrash() = io {
        val trashed = documentDao.rawDocuments(
            DocumentQueryBuilder.collectionQuery(
                collection = SmartCollection.TRASH,
                filter = DocumentFilter.ALL,
                sort = DocumentSort.Default,
                limit = MAX_TRASH_CLEAR,
            ),
        )
        documentDao.emptyTrash()
        trashed.forEach { entity ->
            searchDao.deleteIndexRow(entity.id)
            thumbnailStore.delete(entity.id)
            deleteSandboxCopy(entity)
        }
    }

    override suspend fun moveToFolder(ids: List<String>, folderId: String?) =
        io { if (ids.isNotEmpty()) documentDao.moveToFolder(ids, folderId) }

    override suspend fun updateNotes(id: String, notes: String) {
        io {
            documentDao.updateNotes(id, notes)
            documentDao.getById(id)?.let { entity -> writeIndexRow(entity) }
        }
    }

    override suspend fun markOpened(id: String, openedAt: Long) {
        io { documentDao.updateLastOpened(id, openedAt) }
    }

    override suspend fun updateFileFacts(id: String, sizeBytes: Long, modifiedAt: Long) {
        io { documentDao.updateFileFacts(id, sizeBytes, modifiedAt) }
    }

    override suspend fun setCategory(id: String, category: DocumentCategory?) {
        io {
            documentDao.setCategory(id, category)
            documentDao.getById(id)?.let { entity -> writeIndexRow(entity) }
        }
    }

    // -------------------------------------------------- background enrichment

    override suspend fun getPendingEnrichment(limit: Int): List<Document> =
        io { documentDao.getPendingEnrichment(limit).toDomain() }

    override suspend fun getPendingTextExtraction(limit: Int): List<Document> =
        io { documentDao.getPendingTextExtraction(limit).toDomain() }

    override suspend fun getPendingTextRecognition(limit: Int): List<Document> =
        io { documentDao.getPendingTextRecognition(limit).toDomain() }

    override suspend fun setThumbnailPath(id: String, path: String?) {
        io { documentDao.setThumbnailPath(id, path) }
    }

    override suspend fun setChecksum(id: String, checksum: String) {
        io { documentDao.setChecksum(id, checksum) }
    }

    override suspend fun setGeometry(id: String, pageCount: Int?, width: Int?, height: Int?) {
        io { documentDao.setGeometry(id, pageCount, width, height) }
    }

    override suspend fun setTextState(id: String, status: TextIndexStatus, origin: TextOrigin) {
        io {
            documentDao.updateTextState(id, status, origin, System.currentTimeMillis())
            documentDao.getById(id)?.let { entity -> writeIndexRow(entity) }
        }
    }

    override suspend fun setOcrState(id: String, status: OcrStatus) {
        io {
            documentDao.updateOcrState(
                id = id,
                state = status,
                completedAt = if (status == OcrStatus.COMPLETED) System.currentTimeMillis() else null,
            )
            documentDao.getById(id)?.let { entity -> writeIndexRow(entity) }
        }
    }

    override suspend fun purgeTrashOlderThan(cutoff: Long): Int = io {
        val stale = documentDao.getTrashedBefore(cutoff)
        if (stale.isEmpty()) {
            0
        } else {
            documentDao.deleteByIds(stale.map { it.id })
            stale.forEach { entity ->
                searchDao.deleteIndexRow(entity.id)
                thumbnailStore.delete(entity.id)
                deleteSandboxCopy(entity)
            }
            stale.size
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Rewrites the index row for a document, preserving whatever text body has already been
     * extracted. Metadata edits (rename, notes, category, tags) must not throw away the expensive
     * part of the index.
     */
    private suspend fun writeIndexRow(entity: DocumentEntity) {
        val tagNames = tagDao.getTagsForDocument(entity.id).map { it.name }
        val payload = buildSearchIndexPayload(
            document = entity,
            extractedText = searchDao.indexedBody(entity.id).orEmpty(),
            recognisedText = "",
            tagNames = tagNames,
        )
        searchDao.upsertIndexRow(
            docId = payload.docId,
            title = payload.title.take(MAX_INDEXED_TITLE_CHARS),
            body = payload.body.take(MAX_INDEXED_BODY_CHARS),
            notes = payload.notes.take(MAX_INDEXED_NOTES_CHARS),
            tags = payload.tags,
            category = payload.category,
        )
    }

    /**
     * Removes the sandbox copy of a document Docora created (scans, toolkit output).
     *
     * Documents that live in a user granted SAF location are never deleted from disk here:
     * Docora cannot know whether the provider can undo that, so the app only ever removes rows it
     * owns (PRD §17). The user's original file stays untouched.
     */
    private fun deleteSandboxCopy(entity: DocumentEntity) {
        if (entity.providerAuthority != null) return
        val path = runCatching { android.net.Uri.parse(entity.uri).path }.getOrNull() ?: return
        runCatching { java.io.File(path).takeIf { it.exists() }?.delete() }
    }

    /** Collections have a natural sort order; the caller's choice wins once the user changes it. */
    private fun defaultSortFor(collection: SmartCollection, requested: DocumentSort): DocumentSort =
        when {
            requested.order != SortOrder.DATE_MODIFIED -> requested
            collection == SmartCollection.LARGE_FILES ->
                DocumentSort(SortOrder.SIZE, SortDirection.DESCENDING)
            collection == SmartCollection.RECENT ->
                DocumentSort(SortOrder.LAST_OPENED, SortDirection.DESCENDING)
            else -> requested
        }

    private fun DashboardStatsRow.toDomain(): DashboardStats = DashboardStats(
        totalDocuments = totalDocuments,
        scans = scans,
        favorites = favorites,
        pdfs = pdfs,
        images = images,
        needsOcr = needsOcr,
        trashed = trashed,
        pendingIndexing = pendingIndexing,
        totalBytes = totalBytes,
        trashBytes = trashBytes,
    )

    private fun StorageBreakdownRow.toDomain(thumbnailCacheBytes: Long): StorageBreakdown =
        StorageBreakdown(
            totalBytes = totalBytes,
            pdfBytes = pdfBytes,
            imageBytes = imageBytes,
            otherBytes = otherBytes,
            trashBytes = trashBytes,
            thumbnailCacheBytes = thumbnailCacheBytes,
            documentCount = documentCount,
        )

    private companion object {
        const val MAX_INDEXED_TITLE_CHARS = 300
        const val MAX_INDEXED_NOTES_CHARS = 4_000

        /** A window of extracted text is indexed; see SearchRepositoryImpl for the rationale. */
        const val MAX_INDEXED_BODY_CHARS = 200_000

        /** Upper bound for a single "empty trash" or "delete folder contents" pass. */
        const val MAX_TRASH_CLEAR = 20_000
    }
}
