package com.vedica.labs.ind.app.docora.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.vedica.labs.ind.app.docora.core.common.DocoraError
import com.vedica.labs.ind.app.docora.core.util.DocumentLimits
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable

/**
 * A single open [PdfRenderer] for one PDF.
 *
 * The old viewer opened a fresh [PdfRenderer] for every page, which threw away PDFium's
 * parsed document and re-read the file on every scroll. Keeping one renderer alive is the
 * single biggest performance win in the reader: page changes become bitmap rasterisation
 * only.
 *
 * [PdfRenderer] is not thread-safe, so every call goes through [lock]. The renderer also
 * owns a file descriptor, which is why [close] is mandatory and is called by the owning
 * repository when the document changes or the screen goes away.
 */
class PdfRenderSession internal constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {

    internal val lock = Mutex()

    val pageCount: Int = renderer.pageCount

    /** Page width/height in points, used for placeholder aspect ratios and annotation math. */
    fun pageInfo(index: Int): PdfPageInfo? {
        if (index !in 0 until pageCount) return null
        return runCatching {
            renderer.openPage(index).use { page ->
                PdfPageInfo(
                    index = index,
                    widthPoints = page.width.toFloat(),
                    heightPoints = page.height.toFloat(),
                    rotation = 0,
                )
            }
        }.getOrNull()
    }

    /**
     * Rasterises [pageIndex].
     *
     * Rendering happens at [targetWidthPx] so the bitmap is sized for the viewport rather
     * than the PDF's native 72 dpi. That is what keeps a 1,000-page A0 drawing from
     * allocating hundreds of megabytes (PRD A�35, A�36).
     */
    fun renderPage(pageIndex: Int, targetWidthPx: Int, rotationDegrees: Int = 0): Bitmap? {
        if (pageIndex !in 0 until pageCount) return null
        val safeWidth = targetWidthPx.coerceIn(240, MAX_TARGET_WIDTH_PX)
        val base = runCatching {
            renderer.openPage(pageIndex).use { page ->
                val scale = (safeWidth.toFloat() / page.width).coerceIn(0.35f, 4f)
                var width = (page.width * scale).toInt().coerceAtLeast(1)
                var height = (page.height * scale).toInt().coerceAtLeast(1)
                val pixels = width.toLong() * height.toLong()
                if (pixels > MAX_PAGE_PIXELS) {
                    val shrink = kotlin.math.sqrt(MAX_PAGE_PIXELS.toDouble() / pixels.toDouble()).toFloat()
                    width = (width * shrink).toInt().coerceAtLeast(1)
                    height = (height * shrink).toInt().coerceAtLeast(1)
                }
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                val matrix = Matrix().apply { setScale(width.toFloat() / page.width, height.toFloat() / page.height) }
                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }.getOrNull() ?: return null

        val normalised = ((rotationDegrees % 360) + 360) % 360
        if (normalised == 0) return base
        if (normalised % 90 != 0) return base
        return runCatching {
            val matrix = Matrix().apply { postRotate(normalised.toFloat()) }
            val rotated = Bitmap.createBitmap(base, 0, 0, base.width, base.height, matrix, true)
            if (rotated !== base) base.recycle()
            rotated
        }.getOrElse {
            base
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        /** No single bitmap may exceed ~12 MP even on a tablet held far from the eye. */
        private const val MAX_PAGE_PIXELS = 12_000_000L
        private const val MAX_TARGET_WIDTH_PX = 2400

        /** Opens a content URI and fails with a domain error when the file is unavailable. */
        fun open(context: Context, uri: Uri): PdfRenderSession {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw DocoraError.DocumentMissing()
            return try {
                val renderer = PdfRenderer(descriptor)
                if (renderer.pageCount <= 0) {
                    renderer.close()
                    descriptor.close()
                    throw DocoraError.CorruptedDocument()
                }
                PdfRenderSession(descriptor, renderer)
            } catch (error: Throwable) {
                runCatching { descriptor.close() }
                if (error is DocoraError) throw error
                throw DocoraError.CorruptedDocument(error)
            }
        }
    }
}

/**
 * Process-wide owner of the active PDF render session and its bitmap cache.
 *
 * One session at a time is intentional: Docora is a phone app, only one PDF is on screen,
 * and PDFium's memory use is dominated by the open document. The cache is bounded by
 * [DocumentLimits.PDF_PAGE_CACHE_BYTES] in *bytes*, not entries, so a page rendered at
 * 2,400 px cannot quietly evict twice as many small ones as expected.
 */
@javax.inject.Singleton
class PdfRenderRepository @javax.inject.Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val dispatchers: com.vedica.labs.ind.app.docora.core.common.DispatcherProvider,
    private val unlockStore: PdfUnlockStore,
) {

    private val mutex = Mutex()
    private var session: PdfRenderSession? = null
    private var currentUri: String? = null
    private var pageCount: Int = 0

    private val cache = object : android.util.LruCache<String, Bitmap>(
        DocumentLimits.PDF_PAGE_CACHE_BYTES.toInt(),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            value.allocationByteCount.coerceAtLeast(1)
    }

    suspend fun open(uri: String): PdfDocumentInfo = withContextIo {
        mutex.withLock {
            ensureOpenLocked(uri)
            PdfDocumentInfo(pageCount = pageCount)
        }
    }

    suspend fun pageCount(uri: String): Int = open(uri).pageCount

    suspend fun renderPage(
        uri: String,
        pageIndex: Int,
        targetWidthPx: Int,
        rotationDegrees: Int = 0,
    ): RenderedPdfPage? = withContextIo {
        mutex.withLock {
            val active = ensureOpenLocked(uri)
            val key = cacheKey(uri, pageIndex, targetWidthPx, rotationDegrees)
            cache.get(key)?.let { bitmap ->
                return@withLock RenderedPdfPage(bitmap, bitmap.height.toFloat() / bitmap.width)
            }
            val bitmap = active.renderPage(pageIndex, targetWidthPx, rotationDegrees) ?: return@withLock null
            cache.put(key, bitmap)
            RenderedPdfPage(bitmap, bitmap.height.toFloat() / bitmap.width)
        }
    }

    suspend fun renderThumbnail(uri: String, pageIndex: Int): Bitmap? = withContextIo {
        mutex.withLock {
            val active = ensureOpenLocked(uri)
            val key = "thumb:$uri:$pageIndex"
            cache.get(key)?.let { return@withLock it }
            val bitmap = active.renderPage(pageIndex, THUMBNAIL_WIDTH_PX) ?: return@withLock null
            cache.put(key, bitmap)
            bitmap
        }
    }

    suspend fun close() = withContextIo {
        mutex.withLock { closeLocked() }
    }

    private suspend fun ensureOpenLocked(uri: String): PdfRenderSession {
        val existing = session
        if (existing != null && currentUri == uri) return existing
        closeLocked()
        // An encrypted document may have been unlocked earlier in this session: the renderer
        // cannot read the original, so it opens the app-private decrypted copy instead.
        val source = unlockStore.resolve(uri) ?: uri
        val opened = PdfRenderSession.open(context, Uri.parse(source))
        session = opened
        currentUri = uri
        pageCount = opened.pageCount
        return opened
    }

    private fun closeLocked() {
        runCatching { session?.close() }
        session = null
        currentUri = null
        pageCount = 0
        cache.evictAll()
    }

    private fun cacheKey(uri: String, pageIndex: Int, width: Int, rotation: Int): String {
        // Round the width to 120 px buckets: a drag-resize or a foldable hinge does not
        // need a brand new bitmap for every two pixels of width change.
        val bucket = (width + 119) / 120 * 120
        return "page:$uri:$pageIndex:$bucket:${((rotation % 360) + 360) % 360}"
    }

    private suspend fun <T> withContextIo(block: suspend () -> T): T =
        kotlinx.coroutines.withContext(dispatchers.io) { block() }

    private companion object {
        const val THUMBNAIL_WIDTH_PX = 200
    }
}

/** A rendered bitmap plus its aspect ratio (height / width). */
data class RenderedPdfPage(
    val bitmap: Bitmap,
    val aspect: Float,
)
