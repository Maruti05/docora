package com.vedica.labs.ind.app.docora.core.backup

import kotlinx.serialization.Serializable

/**
 * The portable metadata backup (PRD §50).
 *
 * A backup deliberately holds *metadata only*: the files themselves stay where they are, either
 * with their SAF provider or in the app sandbox. That keeps a backup small enough to email, and it
 * means a restore can never duplicate gigabytes of documents. Every entry is keyed by the stable
 * identifiers the app already uses - the document URI and the folder/tag ids - so a restore can be
 * merged into a library that has moved on since the backup was taken.
 *
 * [format] is checked on import so a random JSON file is rejected with a clear message instead of
 * producing an empty library.
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: Long = 0L,
    val appVersion: String = "",
    val folders: List<BackupFolder> = emptyList(),
    val tags: List<BackupTag> = emptyList(),
    val documents: List<BackupDocument> = emptyList(),
) {
    companion object {
        const val FORMAT = "docora.backup"
        const val VERSION = 1
        const val MIME_TYPE = "application/json"
        const val FILE_EXTENSION = "json"
    }
}

/** One virtual folder. [parentId] refers to another entry in the same backup. */
@Serializable
data class BackupFolder(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val treeUri: String? = null,
    val createdAt: Long = 0L,
)

/** One tag. Names are matched case-insensitively on restore, which is how tags are de-duplicated. */
@Serializable
data class BackupTag(
    val id: String,
    val name: String,
    val colorHex: String? = null,
    val createdAt: Long = 0L,
)

/**
 * One document's metadata plus the tags it carried.
 *
 * [uri] is the identity: a restore only applies folder, favourite and tag changes to documents the
 * library already knows about, because a URI that no longer resolves cannot be re-imported without
 * the file itself. Annotations that cannot be applied are simply skipped.
 */
@Serializable
data class BackupDocument(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long = 0L,
    val folderId: String? = null,
    val isFavorite: Boolean = false,
    val category: String? = null,
    val tagIds: List<String> = emptyList(),
)

/** What an export wrote, used for the confirmation message. */
data class BackupSummary(
    val folders: Int,
    val tags: Int,
    val documents: Int,
)

/** What a restore merged. Documents counts the entries whose annotations were applied. */
data class RestoreSummary(
    val folders: Int,
    val tags: Int,
    val documents: Int,
) {
    val isEmpty: Boolean get() = folders == 0 && tags == 0 && documents == 0
}
