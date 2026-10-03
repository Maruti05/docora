package com.vedica.labs.ind.app.docora.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vedica.labs.ind.app.docora.core.model.DocumentCategory
import com.vedica.labs.ind.app.docora.core.model.DocumentSource
import com.vedica.labs.ind.app.docora.core.model.DocumentType
import com.vedica.labs.ind.app.docora.core.model.OcrStatus
import com.vedica.labs.ind.app.docora.core.model.TextIndexStatus
import com.vedica.labs.ind.app.docora.core.model.TextOrigin

/**
 * Persisted document metadata (PRD §7, §44).
 *
 * Nothing but metadata lives here: the document content stays with its SAF provider (or in
 * the app sandbox for documents Docora created), so the database stays small and quick to
 * back up.
 *
 * `id` is a stable, app-generated identifier (a hash of the URI) rather than the implicit
 * SQLite rowid, because rows are re-inserted when a provider changes a document and the
 * identifier must survive that.
 *
 * Indexed on:
 * - uri (unique) - authoritative identifier from SAF.
 * - folder_id - folder contents listing.
 * - document_type - type filters.
 * - category - smart categories on the dashboard.
 * - is_favorite / is_trash / is_pinned / is_archived - collection queries.
 * - modified_at / created_at / size_bytes / last_opened_at - every supported sort order.
 * - checksum_sha256 - duplicate detection.
 */
@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["uri"], unique = true),
        Index(value = ["folder_id"]),
        Index(value = ["document_type"]),
        Index(value = ["category"]),
        Index(value = ["is_favorite"]),
        Index(value = ["is_pinned"]),
        Index(value = ["is_archived"]),
        Index(value = ["is_trash"]),
        Index(value = ["modified_at"]),
        Index(value = ["created_at"]),
        Index(value = ["size_bytes"]),
        Index(value = ["last_opened_at"]),
        Index(value = ["parent_tree_uri"]),
        Index(value = ["checksum_sha256"]),
        Index(value = ["ocr_state"]),
    ],
)
data class DocumentEntity(
    @PrimaryKey
    val id: String,
    val uri: String,
    @ColumnInfo(name = "parent_tree_uri")
    val parentTreeUri: String?,
    @ColumnInfo(name = "provider_authority")
    val providerAuthority: String?,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "extension")
    val extension: String,
    @ColumnInfo(name = "folder_id")
    val folderId: String?,
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    @ColumnInfo(name = "document_type")
    val documentType: DocumentType,
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "modified_at")
    val modifiedAt: Long,
    @ColumnInfo(name = "indexed_at")
    val indexedAt: Long?,
    @ColumnInfo(name = "last_opened_at")
    val lastOpenedAt: Long?,
    @ColumnInfo(name = "page_count")
    val pageCount: Int?,
    @ColumnInfo(name = "width")
    val width: Int?,
    @ColumnInfo(name = "height")
    val height: Int?,
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String?,
    @ColumnInfo(name = "source")
    val source: DocumentSource = DocumentSource.IMPORTED,
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,
    @ColumnInfo(name = "is_pinned")
    val isPinned: Boolean = false,
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,
    @ColumnInfo(name = "is_trash")
    val isTrash: Boolean = false,
    @ColumnInfo(name = "trashed_at")
    val trashedAt: Long? = null,
    @ColumnInfo(name = "is_encrypted")
    val isEncrypted: Boolean = false,
    @ColumnInfo(name = "category")
    val category: DocumentCategory? = null,
    @ColumnInfo(name = "checksum_sha256")
    val checksumSha256: String? = null,
    @ColumnInfo(name = "text_state")
    val textState: TextIndexStatus = TextIndexStatus.NOT_PROCESSED,
    @ColumnInfo(name = "text_origin")
    val textOrigin: TextOrigin = TextOrigin.NONE,
    @ColumnInfo(name = "ocr_state")
    val ocrState: OcrStatus = OcrStatus.NOT_PROCESSED,
    @ColumnInfo(name = "ocr_completed_at")
    val ocrCompletedAt: Long? = null,
    @ColumnInfo(name = "notes")
    val notes: String = "",
)

