package com.vedica.labs.ind.app.docora.core.storage

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.vedica.labs.ind.app.docora.core.common.DocoraError
import com.vedica.labs.ind.app.docora.core.common.DocoraResult
import com.vedica.labs.ind.app.docora.core.common.docoraRunCatching
import com.vedica.labs.ind.app.docora.core.util.MimeTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

data class SafMetadata(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val isDirectory: Boolean,
)

/**
 * Single gateway for all Storage Access Framework interactions (PRD §43).
 */
class SafGateway(
    private val context: Context,
    private val contentResolver: ContentResolver = context.contentResolver,
) {
    fun takePersistablePermission(uri: Uri, isWriteRequired: Boolean = false): DocoraResult<Unit> =
        docoraRunCatching {
            var flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (isWriteRequired) {
                flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            }
            try {
                contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: SecurityException) {}
        }

    fun releasePersistablePermission(uri: Uri): DocoraResult<Unit> =
        docoraRunCatching {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                contentResolver.releasePersistableUriPermission(uri, flags)
            } catch (_: SecurityException) {}
        }

    fun getPersistedUriPermissions(): List<Uri> =
        contentResolver.persistedUriPermissions.map { it.uri }

    suspend fun getMetadata(uri: Uri): DocoraResult<SafMetadata> = withContext(Dispatchers.IO) {
        docoraRunCatching {
            val docFile = DocumentFile.fromSingleUri(context, uri)
                ?: DocumentFile.fromTreeUri(context, uri)
                ?: throw DocoraError.DocumentMissing()

            val name = docFile.name ?: "Unnamed"
            val type = docFile.type ?: MimeTypes.resolve(null, name)
            val size = docFile.length()
            val modified = docFile.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()
            val isDir = docFile.isDirectory

            SafMetadata(uri, name, type, size, modified, isDir)
        }
    }

    fun openInputStream(uri: Uri): InputStream? = contentResolver.openInputStream(uri)

    fun openOutputStream(uri: Uri, mode: String = "wt"): OutputStream? =
        contentResolver.openOutputStream(uri, mode)

    suspend fun listChildren(treeUri: Uri): DocoraResult<List<SafMetadata>> = withContext(Dispatchers.IO) {
        docoraRunCatching {
            val treeFile = DocumentFile.fromTreeUri(context, treeUri)
                ?: throw DocoraError.StorageUnavailable()

            if (!treeFile.canRead()) {
                throw DocoraError.PermissionRevoked()
            }

            treeFile.listFiles().map { file ->
                val name = file.name ?: "Unnamed"
                val type = file.type ?: MimeTypes.resolve(null, name)
                SafMetadata(
                    uri = file.uri,
                    displayName = name,
                    mimeType = type,
                    sizeBytes = file.length(),
                    modifiedAt = file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis(),
                    isDirectory = file.isDirectory,
                )
            }
        }
    }

    suspend fun createDocument(
        treeUri: Uri,
        mimeType: String,
        displayName: String,
    ): DocoraResult<Uri> = withContext(Dispatchers.IO) {
        docoraRunCatching {
            val dir = DocumentFile.fromTreeUri(context, treeUri)
                ?: throw DocoraError.StorageUnavailable()
            val newFile = dir.createFile(mimeType, displayName)
                ?: throw DocoraError.ReadOnlyLocation()
            newFile.uri
        }
    }

    suspend fun deleteDocument(uri: Uri): DocoraResult<Boolean> = withContext(Dispatchers.IO) {
        docoraRunCatching {
            val docFile = DocumentFile.fromSingleUri(context, uri)
                ?: DocumentFile.fromTreeUri(context, uri)
                ?: throw DocoraError.DocumentMissing()
            docFile.delete()
        }
    }

    suspend fun renameDocument(uri: Uri, newName: String): DocoraResult<Boolean> = withContext(Dispatchers.IO) {
        docoraRunCatching {
            val docFile = DocumentFile.fromSingleUri(context, uri)
                ?: DocumentFile.fromTreeUri(context, uri)
                ?: throw DocoraError.DocumentMissing()
            docFile.renameTo(newName)
        }
    }

    fun isUriAccessible(uri: Uri): Boolean =
        try {
            contentResolver.query(uri, null, null, null, null)?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
}
