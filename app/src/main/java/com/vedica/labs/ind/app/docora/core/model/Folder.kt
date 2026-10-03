package com.vedica.labs.ind.app.docora.core.model

/**
 * A folder in Docora's virtual workspace.
 *
 * Folders are virtual: they group documents that may physically live in different SAF
 * locations. Folders that were created inside a real device location also carry the tree
 * [uri] so that "move to folder" can move the actual file.
 */
data class Folder(
    val id: String,
    val name: String,
    val parentId: String?,
    val treeUri: String?,
    val createdAt: Long,
    val isDefault: Boolean,
) {
    val isRoot: Boolean get() = parentId == null
}

/** A user or automatically assigned label. */
data class Tag(
    val id: String,
    val name: String,
    val colorHex: String?,
    val isAutoAssigned: Boolean,
    val createdAt: Long,
)

/** A tag together with how many documents currently use it. */
data class TagWithCount(
    val tag: Tag,
    val documentCount: Int,
)

/**
 * Aggregate counters shown on the dashboard and in the processing banner.
 *
 * Produced by a single SQL statement (see `DocumentDao.observeDashboardStats`) so the home
 * screen does not fan out into a query per counter.
 */
data class DashboardStats(
    val totalDocuments: Int = 0,
    val scans: Int = 0,
    val favorites: Int = 0,
    val pdfs: Int = 0,
    val images: Int = 0,
    val needsOcr: Int = 0,
    val trashed: Int = 0,
    val pendingIndexing: Int = 0,
    val totalBytes: Long = 0,
    val trashBytes: Long = 0,
) {
    val isEmpty: Boolean get() = totalDocuments == 0
    val isIndexing: Boolean get() = pendingIndexing > 0

    companion object {
        val Empty = DashboardStats()
    }
}

/**
 * A folder together with its direct child counts, used by the folder browser.
 *
 * [documentCount] comes from the same aggregate query as the folder list, so a folder row is
 * never the reason for an extra round trip.
 */
data class FolderWithCount(
    val folder: Folder,
    val documentCount: Int,
)


/**
 * Virtual collections computed from indexed metadata. They never duplicate files
 * (PRD §18): each one maps to a query.
 */
enum class SmartCollection(val titleKey: String) {
    ALL("collection_all"),
    RECENT("collection_recent"),
    FAVORITES("collection_favorites"),
    SCANS("collection_scans"),
    PDFS("collection_pdfs"),
    IMAGES("collection_images"),
    LARGE_FILES("collection_large"),
    RECENTLY_MODIFIED("collection_recently_modified"),
    TRASH("collection_trash"),
    DUPLICATES("collection_duplicates"),
    ;

    companion object {
        /** Collections surfaced on the home dashboard, in display order. */
        val dashboard: List<SmartCollection> = listOf(
            ALL,
            RECENT,
            FAVORITES,
            SCANS,
            PDFS,
            IMAGES,
            LARGE_FILES,
            RECENTLY_MODIFIED,
        )
    }
}

/** Counters shown on the home dashboard. */
data class DashboardCounts(
    val totalDocuments: Int,
    val scans: Int,
    val favorites: Int,
    val pdfs: Int,
    val images: Int,
    val needsOcr: Int,
    val trashed: Int,
) {
    companion object {
        val Empty = DashboardCounts(0, 0, 0, 0, 0, 0, 0)
    }
}
