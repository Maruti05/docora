package com.vedica.labs.ind.app.docora.feature.print

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Streams a document through the Android print framework (PRD §48).
 *
 * The print spooler calls [onWrite] on the main thread, so the copy is shifted onto a
 * worker thread; the cancellation signal is honoured both before and during the write so
 * a cancelled job never leaves a half-written spool file.
 */
class DocumentPrintAdapter(
    private val context: Context,
    private val uri: Uri,
    private val documentName: String,
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(documentName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()
        callback.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        val worker = Thread {
            if (cancellationSignal?.isCanceled == true) {
                callback.onWriteCancelled()
                return@Thread
            }
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { source ->
                    FileInputStream(source.fileDescriptor).use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                } ?: throw IOException("document unavailable")
                if (cancellationSignal?.isCanceled == true) {
                    callback.onWriteCancelled()
                } else {
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                }
            } catch (error: Exception) {
                callback.onWriteFailed(error.message)
            } finally {
                runCatching { destination.close() }
            }
        }
        cancellationSignal?.setOnCancelListener { worker.interrupt() }
        worker.start()
    }

    companion object {
        /** Opens the system print dialog for [uri]. Safe to call from any screen. */
        fun print(context: Context, uri: Uri, documentName: String) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                ?: return
            printManager.print(
                documentName.take(48),
                DocumentPrintAdapter(context, uri, documentName),
                PrintAttributes.Builder().build(),
            )
        }
    }
}
