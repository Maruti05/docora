package com.vedica.labs.ind.app.docora.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.common.DispatcherProvider
import com.vedica.labs.ind.app.docora.core.handler.DocumentHandlerRegistry
import com.vedica.labs.ind.app.docora.core.model.Document
import com.vedica.labs.ind.app.docora.core.model.DocumentType
import com.vedica.labs.ind.app.docora.core.model.TextOrigin
import com.vedica.labs.ind.app.docora.core.repository.DocumentRepository
import com.vedica.labs.ind.app.docora.core.util.MimeTypes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.LruCache
import javax.inject.Inject

/** How a document is presented in the viewer (PRD §10, §15). */
enum class ViewerMode { PDF, IMAGE, TEXT, EXTERNAL, UNAVAILABLE }

/** A page bitmap plus its aspect ratio, or an error marker. */
data class RenderedPage(
    val pageIndex: Int,
    val bitmap: Bitmap?,
    val aspect: Float?,
    val failed: Boolean = false,
)

/** Immutable viewer state; bitmaps are delivered per page, never held in this object. */
data class ViewerUiState(
    val document: Document? = null,
    val mode: ViewerMode = ViewerMode.UNAVAILABLE,
    val pageCount: Int = 1,
    val textContent: String? = null,
    val textLoading: Boolean = false,
    val textOrigin: TextOrigin? = null,
    val error: Int? = null,
    val isLoading: Boolean = true,
)

/**
 * Viewer logic (PRD §10).
 *
 * Pages are rendered lazily through an [LruCache] so a 1,000-page PDF never materialises
 * more than a handful of bitmaps (PRD §35, §36). Everything heavy runs on IO; the composable
 * only asks for one page at a time and cancels implicitly when it leaves composition.
 */
@HiltViewModel
class ViewerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentRepository: DocumentRepository,
    private val handlerRegistry: DocumentHandlerRegistry,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(ViewerUiState())
    val state: StateFlow<ViewerUiState> = _state.asStateFlow()

    private var loadedId: String? = null
    private val renderMutex = Mutex()

    /** Bitmap cache: ~28 MB ceiling at 1080p pages, far below an OOM budget (PRD §36). */
    private val pageCache = object : LruCache<String, Bitmap>(6) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    fun load(documentId: String?) {
        if (documentId == null || documentId == loadedId) return
        loadedId = documentId
        _state.value = ViewerUiState()
        viewModelScope.launch {
            val document = documentRepository.getDocument(documentId)
            if (document == null) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = R.string.error_document_missing,
                )
                return@launch
            }
            documentRepository.markOpened(document.id)
            val mode = when {
                document.type == DocumentType.PDF -> ViewerMode.PDF
                document.type == DocumentType.IMAGE -> ViewerMode.IMAGE
                document.type == DocumentType.TEXT -> ViewerMode.TEXT
                document.type.hasNativePreview -> ViewerMode.PDF // not reached today
                else -> ViewerMode.EXTERNAL
            }
            var pageCount = 1
            var error: Int? = null
            if (mode == ViewerMode.PDF) {
                when (val counted = resolveHandler(document).getPageCount(Uri.parse(document.uri))) {
                    is com.vedica.labs.ind.app.docora.core.common.DocoraResult.Success -> pageCount = counted.value
                    else -> error = R.string.viewer_render_failed
                }
            }
            _state.value = ViewerUiState(
                document = document,
                mode = mode,
                pageCount = pageCount,
                error = error,
                isLoading = false,
            )
            if (mode == ViewerMode.TEXT) requestText()
        }
    }

    fun retry() {
        val id = loadedId
        loadedId = null
        load(id)
    }

    private fun resolveHandler(document: Document) =
        handlerRegistry.getHandlerForMime(document.mimeType)
            ?: handlerRegistry.getHandlerForType(document.type)
            ?: error("no handler for ${document.type}")

    /**
     * Renders one page on demand. Returns a cached bitmap when possible; concurrent calls
     * for the same page coalesce through the mutex.
     */
    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): RenderedPage {
        val document = _state.value.document ?: return RenderedPage(pageIndex, null, null, true)
        val cacheKey = "${document.id}:$pageIndex:$targetWidthPx"
        pageCache.get(cacheKey)?.let { return RenderedPage(pageIndex, it, it.height.toFloat() / it.width) }
        return withContext(dispatchers.io) {
            renderMutex.withLock {
                pageCache.get(cacheKey)?.let { return@withContext RenderedPage(pageIndex, it, it.height.toFloat() / it.width) }
                val rendered = runCatching {
                    when (_state.value.mode) {
                        ViewerMode.PDF -> renderPdfPage(Uri.parse(document.uri), pageIndex, targetWidthPx)
                        ViewerMode.IMAGE -> renderImagePage(Uri.parse(document.uri), targetWidthPx)
                        else -> null
                    }
                }.getOrNull()
                if (rendered == null) {
                    RenderedPage(pageIndex, null, null, failed = true)
                } else {
                    pageCache.put(cacheKey, rendered)
                    RenderedPage(pageIndex, rendered, rendered.height.toFloat() / rendered.width)
                }
            }
        }
    }

    private fun renderPdfPage(uri: Uri, pageIndex: Int, targetWidthPx: Int): Bitmap? {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val safePage = pageIndex.coerceIn(0, renderer.pageCount - 1)
                renderer.openPage(safePage).use { page ->
                    val scale = (targetWidthPx.toFloat() / page.width).coerceIn(0.5f, 3f)
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bitmap
                }
            }
        }
    }

    private suspend fun renderImagePage(uri: Uri, targetWidthPx: Int): Bitmap? =
        handlerRegistry.getHandlerForType(DocumentType.IMAGE)
            ?.renderThumbnail(uri, 0, targetWidthPx)
            ?.getOrNull()

    /** Thumbnail for the page strip; rendered small and cached by the same LRU. */
    suspend fun renderPageThumbnail(pageIndex: Int): Bitmap? {
        val document = _state.value.document ?: return null
        val cacheKey = "${document.id}:thumb:$pageIndex"
        pageCache.get(cacheKey)?.let { return it }
        return withContext(dispatchers.io) {
            val handler = runCatching { resolveHandler(document) }.getOrNull() ?: return@withContext null
            val bitmap = handler.renderThumbnail(Uri.parse(document.uri), pageIndex, 160).getOrNull()
                ?: return@withContext null
            pageCache.put(cacheKey, bitmap)
            bitmap
        }
    }

    /** Loads extracted/recognised text for the text panel and find-in-document (PRD §10, §13). */
    fun requestText() {
        val current = _state.value
        val document = current.document ?: return
        if (current.textLoading || current.textContent != null) return
        _state.value = current.copy(textLoading = true)
        viewModelScope.launch {
            val handler = runCatching { resolveHandler(document) }.getOrNull()
            val text = handler?.extractText(Uri.parse(document.uri))?.getOrNull()
            _state.value = _state.value.copy(
                textLoading = false,
                textContent = text?.take(MAX_TEXT_CHARS),
                textOrigin = when {
                    !text.isNullOrBlank() && document.type == DocumentType.PDF -> TextOrigin.EMBEDDED
                    !text.isNullOrBlank() -> TextOrigin.RECOGNISED
                    else -> TextOrigin.NONE
                },
            )
        }
    }

    /** Shares the document through the system sheet (PRD §47); URIs are content:// only. */
    fun share(context: Context) {
        val document = _state.value.document ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = document.mimeType.ifBlank { MimeTypes.OCTET_STREAM }
            putExtra(Intent.EXTRA_STREAM, Uri.parse(document.uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    /** Delegates to an external app when Docora cannot render the format (PRD §5). */
    fun openExternally(context: Context) {
        val document = _state.value.document ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(document.uri), document.mimeType.ifBlank { MimeTypes.OCTET_STREAM })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
    }

    fun pageLabel(context: Context, page: Int): String =
        context.getString(R.string.viewer_page_of, page + 1, _state.value.pageCount)

    override fun onCleared() {
        pageCache.evictAll()
        super.onCleared()
    }

    private companion object {
        /** The text panel is for reading and finding, not for loading 100 MB into RAM. */
        const val MAX_TEXT_CHARS = 400_000
    }
}
