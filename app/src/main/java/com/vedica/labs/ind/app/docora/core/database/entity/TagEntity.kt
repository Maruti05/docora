package com.vedica.labs.ind.app.docora.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * User-applied tags for cross-cutting document organization.
 */
@Entity(
    tableName = "tags",
    indices = [
        Index(value = ["name"], unique = true),
    ],
)
data class TagEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "color_accent")
    val colorAccent: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

/**
 * Many-to-many junction between documents and tags.
 */
@Entity(
    tableName = "document_tag_cross_ref",
    primaryKeys = ["document_id", "tag_id"],
    indices = [
        Index(value = ["tag_id"]),
    ],
)
data class DocumentTagCrossRef(
    @ColumnInfo(name = "document_id")
    val documentId: String,
    @ColumnInfo(name = "tag_id")
    val tagId: String,
)
