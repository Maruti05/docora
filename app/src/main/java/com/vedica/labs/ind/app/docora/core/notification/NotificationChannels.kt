package com.vedica.labs.ind.app.docora.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Notification channels (PRD §67).
 *
 * Docora only ever notifies about work the user started: processing progress that takes
 * long enough to leave the app, and completed batch jobs. Nothing promotional, and the
 * channel can be silenced independently by the user.
 */
object NotificationChannels {

    const val PROCESSING = "docora.processing"

    private const val PROCESSING_NAME = "Document processing"
    private const val PROCESSING_DESCRIPTION =
        "Progress for indexing, text recognition and thumbnail generation"

    /** Creates the channels; safe to call on every process start. */
    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val processing = NotificationChannel(
            PROCESSING,
            PROCESSING_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = PROCESSING_DESCRIPTION
            setShowBadge(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(processing)
    }

    /** Channel ids indexed by purpose, kept as constants so code and settings agree. */
    fun channelFor(kind: Kind): String = when (kind) {
        Kind.PROCESSING -> PROCESSING
    }

    enum class Kind { PROCESSING }
}
