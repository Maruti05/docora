package com.vedica.labs.ind.app.docora.core.repository

import com.vedica.labs.ind.app.docora.core.common.DocoraError
import com.vedica.labs.ind.app.docora.core.common.DocoraResult
import com.vedica.labs.ind.app.docora.core.common.DispatcherProvider
import com.vedica.labs.ind.app.docora.core.common.docoraRunCatching
import com.vedica.labs.ind.app.docora.core.database.dao.TagDao
import com.vedica.labs.ind.app.docora.core.database.entity.TagEntity
import com.vedica.labs.ind.app.docora.core.database.mapper.toDomain
import com.vedica.labs.ind.app.docora.core.model.Tag
import com.vedica.labs.ind.app.docora.core.model.TagWithCount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tag operations (PRD §18).
 *
 * Tags are the cross-cutting axis of the library: a document lives in one folder and can carry
 * any number of tags. Names are de-duplicated case-insensitively so "Finance" and "finance"
 * cannot both exist and quietly split the user's documents in two.
 */
interface TagRepository {

    fun observeTags(): Flow<List<TagWithCount>>

    fun observeTagsForDocument(documentId: String): Flow<List<Tag>>

    suspend fun getTagsForDocument(documentId: String): List<Tag>

    /** Creates a tag, or returns the existing one with the same (case-insensitive) name. */
    suspend fun createTag(name: String, colorArgb: Long? = null): DocoraResult<Tag>

    suspend fun renameTag(tagId: String, newName: String): DocoraResult<Tag>

    /** Deletes a tag and removes it from every document. Documents are never deleted. */
    suspend fun deleteTag(tagId: String): DocoraResult<Unit>

    suspend fun assignTag(documentIds: List<String>, tagId: String)

    suspend fun removeTag(documentId: String, tagId: String)

    /** Replaces the tag set of a document; used by the tag picker sheet. */
    suspend fun setDocumentTags(documentId: String, tagIds: List<String>)

    /** Tag names per document, used when building the search index payload. */
    suspend fun tagNamesFor(documentId: String): List<String>
}

@Singleton
class TagRepositoryImpl @Inject constructor(
    private val tagDao: TagDao,
    private val dispatchers: DispatcherProvider,
) : TagRepository {

    override fun observeTags(): Flow<List<TagWithCount>> =
        combine(tagDao.observeAll(), tagDao.observeTagUsage()) { tags, usage ->
            val counts = usage.associate { it.tagId to it.documentCount }
            tags.map { entity ->
                TagWithCount(tag = entity.toDomain(), documentCount = counts[entity.id] ?: 0)
            }
        }

    override fun observeTagsForDocument(documentId: String): Flow<List<Tag>> =
        tagDao.observeTagsForDocument(documentId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getTagsForDocument(documentId: String): List<Tag> =
        withContext(dispatchers.io) { tagDao.getTagsForDocument(documentId).map { it.toDomain() } }

    override suspend fun createTag(name: String, colorArgb: Long?): DocoraResult<Tag> =
        withContext(dispatchers.io) {
            docoraRunCatching {
                val clean = requireName(name)
                findByNormalisedName(clean)?.let { return@docoraRunCatching it.toDomain() }
                val entity = TagEntity(
                    id = UUID.randomUUID().toString(),
                    name = clean,
                    colorAccent = colorArgb,
                    createdAt = System.currentTimeMillis(),
                )
                tagDao.insert(entity)
                entity.toDomain()
            }
        }

    override suspend fun renameTag(tagId: String, newName: String): DocoraResult<Tag> =
        withContext(dispatchers.io) {
            docoraRunCatching {
                val clean = requireName(newName)
                val existing = tagDao.getById(tagId) ?: throw DocoraError.DocumentMissing()
                val clash = findByNormalisedName(clean)
                if (clash != null && clash.id != tagId) {
                    throw DocoraError.Unexpected(IllegalStateException("tag name already in use"))
                }
                tagDao.rename(tagId, clean)
                existing.copy(name = clean).toDomain()
            }
        }

    override suspend fun deleteTag(tagId: String): DocoraResult<Unit> = withContext(dispatchers.io) {
        docoraRunCatching {
            // Assignments are cleared first so an interrupted delete cannot leave orphaned rows.
            tagDao.clearTagAssignments(tagId)
            tagDao.deleteById(tagId)
        }
    }

    override suspend fun assignTag(documentIds: List<String>, tagId: String) =
        withContext(dispatchers.io) {
            documentIds.forEach { documentId -> tagDao.assignTag(documentId, tagId) }
        }

    override suspend fun removeTag(documentId: String, tagId: String) =
        withContext(dispatchers.io) { tagDao.removeTagFromDocument(documentId, tagId) }

    override suspend fun setDocumentTags(documentId: String, tagIds: List<String>) =
        withContext(dispatchers.io) {
            tagDao.clearDocumentTags(documentId)
            tagIds.forEach { tagId -> tagDao.assignTag(documentId, tagId) }
        }

    override suspend fun tagNamesFor(documentId: String): List<String> =
        withContext(dispatchers.io) {
            tagDao.getTagsForDocument(documentId).map { it.name }
        }

    /**
     * SQLite's default collation is binary, so a case-insensitive lookup is done by trying the
     * stored form and the lower-cased form rather than by scanning every tag.
     */
    private suspend fun findByNormalisedName(name: String): TagEntity? =
        tagDao.getByName(name) ?: tagDao.getByName(name.lowercase())

    private fun requireName(name: String): String {
        val clean = name.trim().replace(Regex("\\s+"), " ").take(40)
        if (clean.isEmpty()) throw DocoraError.Unexpected(IllegalArgumentException("empty tag name"))
        return clean
    }
}
