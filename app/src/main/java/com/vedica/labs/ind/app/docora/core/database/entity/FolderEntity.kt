package com.vedica.labs.ind.app.docora.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Logical folders organized by the user or mirrors of SAF directory trees.
 */
@Entity(
    tableName = "folders",
    indices = [
        Index(value = ["parent_id"]),
        Index(value = ["tree_uri"], unique = true),
    ],
)
data class FolderEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "parent_id")
    val parentId: String?,
    @ColumnInfo(name = "tree_uri")
    val treeUri: String?,
    @ColumnInfo(name = "color_accent")
    val colorAccent: Long? = null,
    @ColumnInfo(name = "icon_name")
    val iconName: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,
)
