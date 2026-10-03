package com.vedica.labs.ind.app.docora.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Extracted text and layout per page (PRD §28, §44).
 *
 * `encrypted_text` stores the ciphertext produced by KeystoreCipher, ensuring OCR
 * output is protected at rest. For FTS search, a separate unencrypted search index
 * holds tokens.
 */
@Entity(
    tableName = "document_pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["document_id", "page_number"], unique = true),
    ],
)
data class DocumentPageEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "document_id")
    val documentId: String,
    @ColumnInfo(name = "page_number")
    val pageNumber: Int,
    @ColumnInfo(name = "width")
    val width: Int,
    @ColumnInfo(name = "height")
    val height: Int,
    @ColumnInfo(name = "encrypted_text")
    val encryptedText: ByteArray,
    @ColumnInfo(name = "plain_text_searchable")
    val plainTextSearchable: String, // sanitized plain text for local FTS searching
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DocumentPageEntity
        return id == other.id && documentId == other.documentId && pageNumber == other.pageNumber
    }

    override fun hashCode(): Int = id.hashCode()
}
