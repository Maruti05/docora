package com.vedica.labs.ind.app.docora.core.storage

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.vedica.labs.ind.app.docora.core.model.DocumentImportRequest
import com.vedica.labs.ind.app.docora.core.model.DocumentSource
import com.vedica.labs.ind.app.docora.core.model.DocumentType
import com.vedica.labs.ind.app.docora.core.util.MimeTypes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One file discovered through MediaStore, ready to be imported into the library.
 *
 * Discovery never copies data: the row keeps the `content://` MediaStore URI, so importing is a
 * metadata insert and the original file is never touched or moved.
 */
data class DeviceDocument(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val type: DocumentType,
) {
    fun toImportRequest(): DocumentImportRequest {
        val extension = MimeTypes.extensionOf(displayName)
        return DocumentImportRequest(
            uri = uri,
            displayName = displayName,
            mimeType = MimeTypes.resolve(mimeType, displayName),
            extension = extension,
            sizeBytes = sizeBytes,
            type = type,
            source = DocumentSource.IMPORTED,
            createdAt = modifiedAt,
            modifiedAt = modifiedAt,
            // MediaStore URIs are served by the media provider, not by Docora's sandbox, so
            // permanent delete must never try to remove the underlying file.
            providerAuthority = "media",
        )
    }
}

/**
 * Discovers the documents that already live on the device via MediaStore (user requested).
 *
 * This powers the "On this device" section of the documents browser: with the storage/media
 * permission granted, PDFs, office documents, text files, archives and images indexed by
 * MediaStore become visible, openable and viewable inside Docora. SAF import remains the
 * permission-free path for picking individual files.
 *
 * Permission model (PRD §66):
 *  * Android 11+ (API 30+): the "All files access" special permission, checked through
 *    [Environment.isExternalStorageManager]. MediaStore hides non-media documents (PDF, Word,
 *    Excel, text) from apps that only hold the read-media grants, so this is the one grant that
 *    makes the whole document set reachable.
 *  * Android 10 and below: the ordinary `READ_EXTERNAL_STORAGE` runtime grant.
 * [requiresAllFilesAccess] tells the UI which route to take - the system settings page or the
 * runtime dialog - before calling [query].
 */
@Singleton
class DeviceDocumentsSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Runtime permissions to request before [query], or an empty array when the platform uses the
     * "All files access" special permission instead (Android 11+), in which case the caller opens
     * [allFilesAccessIntent] rather than showing a permission dialog.
     */
    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            emptyArray()
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    /**
     * True when Docora may read every document on the device.
     *
     * Android 11+ (API 30+) gates non-media files behind "All files access"; Android 10 and below
     * only need the legacy read grant (the manifest opts into legacy storage so the check is
     * meaningful there). One check drives the discovery, the automatic scan and the permission
     * card, so the three can never disagree.
     */
    fun hasPermission(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        else -> ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** True when the user has to grant access on the system "All files access" screen. */
    fun requiresAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()

    /**
     * Intent for the screen where "All files access" is granted.
     *
     * The app-specific page is preferred so the user lands straight on Docora's toggle; some OEM
     * builds only expose the full list, hence the fallback. Launching is the caller's job (it owns
     * the foreground activity), wrapped in `runCatching` because a handful of ROMs handle neither.
     */
    fun allFilesAccessIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.fromParts("package", context.packageName, null),
        )



    /**
     * MediaStore rows for document-like files, newest first.
     *
     * The result is bounded ([limit] rows, [maxAgeDays] days) so the browser never materialises
     * a whole camera roll; everything else stays reachable through SAF import.
     * Queries without permission return an empty list instead of throwing.
     */
    suspend fun query(
        limit: Int = DEFAULT_LIMIT,
        maxAgeDays: Int = DEFAULT_MAX_AGE_DAYS,
    ): List<DeviceDocument> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()

        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
        )
        val cutoffSeconds = (System.currentTimeMillis() -
            TimeUnit.DAYS.toMillis(maxAgeDays.toLong())) / 1000
        // Media types: image or plain file (PDFs, office docs, archives, text) only - audio and
        // video are not documents. Rows are newest-first and hard capped by SQL LIMIT.
        val selection = "(" +
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} OR " +
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE}" +
            ") AND ${MediaStore.Files.FileColumns.SIZE} > 0 AND " +
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} >= ?"
        val args = arrayOf(cutoffSeconds.toString())
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC LIMIT $limit"

        val results = mutableListOf<DeviceDocument>()
        runCatching {
            context.contentResolver.query(collection, projection, selection, args, sortOrder)
                ?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                    val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                    val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                    val mediaTypeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)

                    while (cursor.moveToNext() && results.size < limit) {
                        val id = cursor.getLong(idIndex)
                        val name = cursor.getString(nameIndex) ?: continue
                        val mime = cursor.getString(mimeIndex)
                        val size = cursor.getLong(sizeIndex)
                        val modified = cursor.getLong(modifiedIndex) * 1000
                        val mediaType = cursor.getInt(mediaTypeIndex)

                        // Images use their own content URI; other files live under "file".
                        val base = if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) {
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        } else {
                            collection
                        }
                        val uri = ContentUris.withAppendedId(base, id).toString()
                        val resolved = MimeTypes.resolve(mime, name)
                        val type = DocumentType.from(resolved, MimeTypes.extensionOf(name))
                        // Now that non-media rows are visible (All files access), the raw result set
                        // also contains apks, binaries, databases and the like. Anything Docora
                        // cannot classify as a document is dropped here, so neither the device inbox
                        // nor the automatic import is ever polluted with files the app cannot open.
                        if (type == DocumentType.UNKNOWN) continue
                        results += DeviceDocument(
                            uri = uri,
                            displayName = name,
                            mimeType = resolved,
                            sizeBytes = size,
                            modifiedAt = modified,
                            type = type,
                        )
                    }
                }
        }
        results
    }

    companion object {
        const val DEFAULT_LIMIT = 300
        const val DEFAULT_MAX_AGE_DAYS = 365
    }
}
