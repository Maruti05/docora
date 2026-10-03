package com.vedica.labs.ind.app.docora.core.files

import android.graphics.Bitmap
import com.vedica.labs.ind.app.docora.core.util.DocumentLimits
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thumbnail persistence (PRD §22).
 *
 * Thumbnails are always written as JPEG at a fixed quality: the grid shows them at ~200dp, so
 * anything better is invisible while costing memory and disk. A single 512px thumbnail is roughly
 * 25-40 KB, which keeps a thousand documents under 40 MB.
 *
 * The store never decodes images itself - callers pass an already down-sampled bitmap - and it
 * never throws: a thumbnail that cannot be written is a cosmetic failure, never a document
 * failure, so the call site can carry on.
 */
@Singleton
class ThumbnailStore @Inject constructor(
    private val appStorage: AppStorage,
) {

    /** Writes [bitmap] and returns the absolute path, or null when it could not be stored. */
    fun save(documentId: String, bitmap: Bitmap): String? {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
        val target = appStorage.thumbnailFile(documentId)
        return runCatching {
            target.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, output)
            }
            target.absolutePath
        }.getOrNull()
    }

    /** Absolute path of an existing thumbnail, or null. */
    fun pathFor(documentId: String): String? =
        appStorage.thumbnailFile(documentId).takeIf { it.exists() }?.absolutePath

    fun exists(documentId: String): Boolean = appStorage.thumbnailFile(documentId).exists()

    fun delete(documentId: String): Boolean = appStorage.thumbnailFile(documentId).delete()

    fun cacheSizeBytes(): Long = appStorage.thumbnailCacheBytes()

    fun clear(): Int = appStorage.clearThumbnails()

    /**
     * Deletes the oldest thumbnails until the cache is back inside [limitBytes].
     *
     * Enforcing the user's cache limit happens here rather than in a settings screen, so the limit
     * is honoured even when the user never opens settings again.
     */
    fun trimToLimit(limitBytes: Long = DEFAULT_LIMIT_BYTES): Int {
        val files = appStorage.thumbnailsDir.listFiles()?.toList().orEmpty()
        var total = files.sumOf { it.length() }
        if (total <= limitBytes) return 0

        var removed = 0
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total <= limitBytes) return removed
            val size = file.length()
            if (file.delete()) {
                total -= size
                removed++
            }
        }
        return removed
    }

    /** True when a thumbnail has to be regenerated for a document of [sizeBytes]. */
    fun isStale(documentId: String, sizeBytes: Long): Boolean {
        val file = appStorage.thumbnailFile(documentId)
        if (!file.exists()) return true
        // A zero byte thumbnail means the writer was interrupted; regenerate rather than show a
        // broken image forever.
        return file.length() <= 0L || (sizeBytes > 0L && file.length() > DocumentLimits.THUMBNAIL_LONG_EDGE_PX * 8L * 1024L)
    }

    companion object {
        /** JPEG quality: 82 is the knee of the curve for document imagery. */
        const val QUALITY = 82

        /** Default ceiling when the user has not chosen one (settings allow up to 512 MB). */
        const val DEFAULT_LIMIT_BYTES: Long = 128L * 1024 * 1024
    }
}
