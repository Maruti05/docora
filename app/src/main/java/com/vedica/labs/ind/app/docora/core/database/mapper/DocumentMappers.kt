package com.vedica.labs.ind.app.docora.core.database.mapper

import com.vedica.labs.ind.app.docora.core.database.entity.DocumentEntity
import com.vedica.labs.ind.app.docora.core.database.entity.DocumentPageEntity
import com.vedica.labs.ind.app.docora.core.database.entity.FolderEntity
import com.vedica.labs.ind.app.docora.core.database.entity.TagEntity
import com.vedica.labs.ind.app.docora.core.model.Document
import com.vedica.labs.ind.app.docora.core.model.DocumentPageText
import com.vedica.labs.ind.app.docora.core.model.DocumentType
import com.vedica.labs.ind.app.docora.core.model.Folder
import com.vedica.labs.ind.app.docora.core.model.Tag
import com.vedica.labs.ind.app.docora.core.model.TextOrigin

/**
 * Persistence <-> domain conversion.
 *
 * Keeping this in one place means the UI never sees a Room entity (so Compose gets stable,
 * immutable models) and the storage schema can change without touching a single composable.
 */

fun DocumentEntity.toDomain(): Document = Document(
    id = id,
    uri = uri,
    displayName = displayName,
    mimeType = mimeType,
    extension = extension,
    sizeBytes = sizeBytes,
    type = documentType,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
    lastOpenedAt = lastOpenedAt,
    pageCount = pageCount,
    width = width,
    height = height,
    folderId = folderId,
    isFavorite = isFavorite,
    isTrashed = isTrash,
    trashedAt = trashedAt,
    isEncrypted = isEncrypted,
    source = source,
    textIndexStatus = textState,
    ocrStatus = ocrState,
    textOrigin = textOrigin,
    category = category,
    checksumSha256 = checksumSha256,
    thumbnailPath = thumbnailPath,
    providerAuthority = providerAuthority,
    indexedAt = indexedAt,
)

@JvmName("documentEntitiesToDomain")
fun List<DocumentEntity>.toDomain(): List<Document> = map { it.toDomain() }

fun FolderEntity.toDomain(): Folder = Folder(
    id = id,
    name = name,
    parentId = parentId,
    treeUri = treeUri,
    createdAt = createdAt,
    isDefault = false,
)

@JvmName("folderEntitiesToDomain")
fun List<FolderEntity>.toDomain(): List<Folder> = map { it.toDomain() }

fun TagEntity.toDomain(): Tag = Tag(
    id = id,
    name = name,
    colorHex = colorAccent?.let { value ->
        // Colours are stored as ARGB ints; the domain uses hex so it can be rendered by
        // Compose without a second conversion at the call site.
        String.format("#%06X", 0xFFFFFF and value.toInt())
    },
    isAutoAssigned = false,
    createdAt = createdAt,
)

@JvmName("tagEntitiesToDomain")
fun List<TagEntity>.toDomain(): List<Tag> = map { it.toDomain() }

fun DocumentPageEntity.toDomain(): DocumentPageText = DocumentPageText(
    documentId = documentId,
    pageIndex = pageNumber,
    content = plainTextSearchable,
    origin = TextOrigin.RECOGNISED,
    updatedAt = 0L,
)

/**
 * The text columns written into the FTS index for one document.
 *
 * A document always has an index row - even before its text has been extracted - so that
 * searching by file name works the moment a document is imported (PRD §43), while the body is
 * filled in later by the indexing worker.
 */
data class SearchIndexPayload(
    val docId: String,
    val title: String,
    val body: String,
    val notes: String,
    val tags: String,
    val category: String,
)

fun buildSearchIndexPayload(
    document: DocumentEntity,
    extractedText: String,
    recognisedText: String,
    tagNames: List<String>,
): SearchIndexPayload = SearchIndexPayload(
    docId = document.id,
    title = document.displayName,
    body = listOf(extractedText, recognisedText)
        .filter { it.isNotBlank() }
        .joinToString(separator = "\n"),
    notes = document.notes,
    tags = tagNames.joinToString(separator = " "),
    category = document.category?.name.orEmpty(),
)

/** Type used to decide which handler must process a document. */
fun DocumentEntity.resolvedTypeOrUnknown(): DocumentType = documentType
