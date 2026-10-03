package com.vedica.labs.ind.app.docora.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.vedica.labs.ind.app.docora.core.database.entity.DocumentEntity

/**
 * Single-row aggregate used by the home dashboard and the processing banner.
 *
 * Everything is computed in one SQL statement with correlated subqueries so the dashboard
 * never runs a query per counter: with ten counters on screen, ten round trips per
 * database change would be the difference between a fluid and a janky screen (PRD §35).
 */
data class DashboardStatsRow(
    @ColumnInfo(name = "totalDocuments") val totalDocuments: Int,
    @ColumnInfo(name = "scans") val scans: Int,
    @ColumnInfo(name = "favorites") val favorites: Int,
    @ColumnInfo(name = "pdfs") val pdfs: Int,
    @ColumnInfo(name = "images") val images: Int,
    @ColumnInfo(name = "needsOcr") val needsOcr: Int,
    @ColumnInfo(name = "trashed") val trashed: Int,
    @ColumnInfo(name = "pendingIndexing") val pendingIndexing: Int,
    @ColumnInfo(name = "totalBytes") val totalBytes: Long,
    @ColumnInfo(name = "trashBytes") val trashBytes: Long,
)

/** Storage analyser input (PRD §42). */
data class StorageBreakdownRow(
    @ColumnInfo(name = "totalBytes") val totalBytes: Long,
    @ColumnInfo(name = "pdfBytes") val pdfBytes: Long,
    @ColumnInfo(name = "imageBytes") val imageBytes: Long,
    @ColumnInfo(name = "otherBytes") val otherBytes: Long,
    @ColumnInfo(name = "trashBytes") val trashBytes: Long,
    @ColumnInfo(name = "documentCount") val documentCount: Int,
)

/** One checksum collision group (PRD §20). */
data class DuplicateGroupRow(
    @ColumnInfo(name = "checksum") val checksum: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "copy_count") val copyCount: Int,
)

/** One indexed key/value pair of the per-category counters. */
data class CategoryCountRow(
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "document_count") val documentCount: Int,
)

/** A folder or tag with its document count, resolved in SQL instead of in a loop. */
data class FolderCountRow(
    @ColumnInfo(name = "folder_id") val folderId: String,
    @ColumnInfo(name = "document_count") val documentCount: Int,
)

data class TagCountRow(
    @ColumnInfo(name = "tag_id") val tagId: String,
    @ColumnInfo(name = "document_count") val documentCount: Int,
)

/**
 * Full-text search projection: the hydrated document plus the indexed body used to build
 * the result snippet without a second query.
 */
data class SearchRow(
    @Embedded val document: DocumentEntity,
    @ColumnInfo(name = "search_body") val searchBody: String,
)
