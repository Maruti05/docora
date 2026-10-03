package com.vedica.labs.ind.app.docora.core.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.vedica.labs.ind.app.docora.core.common.DocoraError
import com.vedica.labs.ind.app.docora.core.common.DocoraResult
import com.vedica.labs.ind.app.docora.core.common.docoraRunCatching
import com.vedica.labs.ind.app.docora.core.files.AppStorage
import com.vedica.labs.ind.app.docora.core.imaging.ScanImageProcessor
import com.vedica.labs.ind.app.docora.core.model.DocumentImportRequest
import com.vedica.labs.ind.app.docora.core.model.DocumentSource
import com.vedica.labs.ind.app.docora.core.model.DocumentType
import com.vedica.labs.ind.app.docora.core.model.ScanFilter
import com.vedica.labs.ind.app.docora.core.model.ScannerQuality
import com.vedica.labs.ind.app.docora.core.util.DocumentLimits
import com.vedica.labs.ind.app.docora.core.util.FileNameGenerator
import com.vedica.labs.ind.app.docora.core.util.MimeTypes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns processed scan pages into documents the library can own.
 *
 * The writer is the only place in the scanner that touches the filesystem, and it returns
 * [DocumentImportRequest]s rather than importing anything itself: persisting is the repository's
 * job, which keeps "write a file" and "register a document" independently testable and means the
 * scanner never needs to know how the database is shaped.
 *
 * [android.graphics.pdf.PdfDocument] builds the multi-page PDF instead of a writing library: it is
 * part of the platform, it is fast for image-only pages, and the app already ships PDF *reading*
 * through PDFBox, so a second PDF engine for writing would be dead weight.
 */
@Singleton
class ScanDocumentWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appStorage: AppStorage,
) {

    /**
     * Writes every page into a single multi-page PDF.
     *
     * Each page keeps the pixel dimensions of its processed bitmap, so a page the user rotated keeps
     * its orientation and a page captured in landscape stays landscape - the file is a faithful
     * record of what was reviewed, not a re-layout.
     */
    suspend fun writePdf(
        pages: List<Bitmap>,
        timestamp: Long = System.currentTimeMillis(),
        recognisedTitle: String? = null,
    ): DocoraResult<DocumentImportRequest> = withContext(Dispatchers.IO) {
        docoraRunCatching({ DocoraError.InsufficientStorage(it) }) {
            require(pages.isNotEmpty()) { "A scan needs at least one page" }
            val fileName = FileNameGenerator.forScan(timestamp, PDF_EXTENSION, recognisedTitle)
            val file = appStorage.newDocumentPath(fileName)
            val document = PdfDocument()
            try {
                pages.forEachIndexed { index, page ->
                    val info = PdfDocument.PageInfo
                        .Builder(page.width, page.height, index + 1)
                        .create()
                    val pdfPage = document.startPage(info)
                    pdfPage.canvas.drawBitmap(page, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                    document.finishPage(pdfPage)
                }
                FileOutputStream(file).use { stream -> document.writeTo(stream) }
            } finally {
                document.close()
            }
            val size = file.length()
            check(size <= DocumentLimits.MAX_OPENABLE_FILE_BYTES) {
                "Generated PDF exceeds the maximum openable size"
            }
            DocumentImportRequest(
                uri = appStorage.uriFor(file),
                displayName = fileName,
                mimeType = MimeTypes.PDF,
                extension = PDF_EXTENSION,
                sizeBytes = size,
                type = DocumentType.PDF,
                source = DocumentSource.SCANNED,
                createdAt = timestamp,
                modifiedAt = timestamp,
                pageCount = pages.size,
                width = pages.first().width,
                height = pages.first().height,
            )
        }
    }

    /**
     * Writes every page as its own JPEG, numbered in page order.
     *
     * Pages are named from the same stamp a PDF would use, with a page suffix, so saving the same
     * scan twice produces `Scan_2026-09-28_0930_page-1 (1).jpg` from [FileNameGenerator] rather than
     * a silent overwrite.
     */
    suspend fun writeImages(
        pages: List<Bitmap>,
        quality: ScannerQuality,
        filter: ScanFilter,
        timestamp: Long = System.currentTimeMillis(),
        recognisedTitle: String? = null,
    ): DocoraResult<List<DocumentImportRequest>> = withContext(Dispatchers.IO) {
        docoraRunCatching({ DocoraError.InsufficientStorage(it) }) {
            require(pages.isNotEmpty()) { "A scan needs at least one page" }
            val baseName = FileNameGenerator.forScan(timestamp, JPEG_EXTENSION, recognisedTitle)
            val jpegQuality = ScanImageProcessor.jpegQualityFor(filter, quality.jpegQuality)
            pages.mapIndexed { index, page ->
                val fileName = if (pages.size == 1) {
                    baseName
                } else {
                    FileNameGenerator.forDerived(
                        sourceName = baseName,
                        suffix = "page-${index + 1}",
                        extension = JPEG_EXTENSION,
                    )
                }
                val file = appStorage.newDocumentPath(fileName)
                FileOutputStream(file).use { stream ->
                    page.compress(Bitmap.CompressFormat.JPEG, jpegQuality, stream)
                }
                DocumentImportRequest(
                    uri = appStorage.uriFor(file),
                    displayName = fileName,
                    mimeType = JPEG_MIME,
                    extension = JPEG_EXTENSION,
                    sizeBytes = file.length(),
                    type = DocumentType.IMAGE,
                    source = DocumentSource.SCANNED,
                    createdAt = timestamp + index,
                    modifiedAt = timestamp + index,
                    pageCount = 1,
                    width = page.width,
                    height = page.height,
                )
            }
        }
    }

    /** Measured JPEG quality the scanner should use for a filter, exposed for the UI hint. */
    fun jpegQualityFor(filter: ScanFilter, quality: ScannerQuality): Int =
        ScanImageProcessor.jpegQualityFor(filter, quality.jpegQuality)

    /** Releases processed pages; called once the save pipeline is finished with them. */
    fun recycle(pages: List<Bitmap>) {
        pages.forEach { page -> if (!page.isRecycled) page.recycle() }
    }

    /** Application context, kept so the writer can be used from a worker without an activity. */
    val applicationContext: Context get() = context

    private companion object {
        const val PDF_EXTENSION = "pdf"
        const val JPEG_EXTENSION = "jpg"
        const val JPEG_MIME = "image/jpeg"
    }
}
