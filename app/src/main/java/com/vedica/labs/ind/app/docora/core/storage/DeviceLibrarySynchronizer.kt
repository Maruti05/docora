package com.vedica.labs.ind.app.docora.core.storage

import com.vedica.labs.ind.app.docora.core.repository.DocumentRepository
import com.vedica.labs.ind.app.docora.core.util.DocumentLimits
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What one automatic device scan did.
 *
 * [skipped] counts files that already live in the library (imported earlier, or deleted by the
 * user and therefore deliberately not resurrected), [failed] counts files that could not be
 * imported - usually because they exceed [DocumentLimits.MAX_OPENABLE_FILE_BYTES].
 */
data class DeviceSyncOutcome(
    val permissionGranted: Boolean,
    val discovered: Int,
    val imported: Int,
    val skipped: Int,
    val failed: Int,
) {
    /** Nothing changed, so no feedback needs to interrupt the user. */
    val isEmpty: Boolean get() = imported == 0 && failed == 0

    companion object {
        val NoPermission = DeviceSyncOutcome(
            permissionGranted = false,
            discovered = 0,
            imported = 0,
            skipped = 0,
            failed = 0,
        )
    }
}

/**
 * Imports the documents that already live on the device into the Docora library.
 *
 * The documents browser used to surface device files as a manual, tap-to-import list, which
 * meant a fresh install showed an empty library until the user imported files one by one. This
 * synchroniser closes that gap: it runs the bounded MediaStore discovery of
 * [DeviceDocumentsSource] and inserts every file that is not already known, so the library -
 * including search, categories and the dashboard counters - reflects the device as soon as the
 * app starts.
 *
 * Guarantees:
 *  * the source file is never copied, moved or modified: the row keeps the MediaStore
 *    `content://` URI and importing is a metadata insert only (PRD §43);
 *  * an import writes the full-text index row too, so a scanned file is searchable immediately;
 *  * a file the user deleted inside Docora is not silently re-imported, because known URIs
 *    (trashed rows included) are filtered out before inserting;
 *  * concurrent callers (dashboard plus browser, or a re-scan racing the startup scan) collapse
 *    into a single pass thanks to the mutex.
 */
@Singleton
class DeviceLibrarySynchronizer @Inject constructor(
    private val source: DeviceDocumentsSource,
    private val documentRepository: DocumentRepository,
) {

    private val mutex = Mutex()

    /** Scans the device and imports everything that is not in the library yet. */
    suspend fun sync(
        limit: Int = SYNC_LIMIT,
        maxAgeDays: Int = SYNC_MAX_AGE_DAYS,
    ): DeviceSyncOutcome = mutex.withLock {
        if (!source.hasPermission()) return@withLock DeviceSyncOutcome.NoPermission

        val discovered = source.query(limit = limit, maxAgeDays = maxAgeDays)
        if (discovered.isEmpty()) {
            return@withLock DeviceSyncOutcome(
                permissionGranted = true,
                discovered = 0,
                imported = 0,
                skipped = 0,
                failed = 0,
            )
        }

        val known = documentRepository.knownUris()
        // Files Docora can never open are dropped up front: `import` refuses them, and letting
        // them through would abort the batch for a reason the user cannot act on.
        val pending = discovered.filter { it.uri !in known && !DocumentLimits.isTooLargeToOpen(it.sizeBytes) }

        var imported = 0
        var failed = 0
        pending.forEach { device ->
            // One bad file must not cost the whole scan, so each insert is isolated.
            runCatching { documentRepository.import(device.toImportRequest()) }
                .onSuccess { imported++ }
                .onFailure { failed++ }
        }

        DeviceSyncOutcome(
            permissionGranted = true,
            discovered = discovered.size,
            imported = imported,
            skipped = discovered.size - pending.size,
            failed = failed,
        )
    }

    companion object {
        /**
         * Automatic scan window. Wider than the interactive "On this device" list because the
         * point of the sync is to make the whole document set reachable, not just the newest
         * page of it.
         */
        const val SYNC_LIMIT = 500
        const val SYNC_MAX_AGE_DAYS = 1825
    }
}
