package com.vedica.labs.ind.app.docora.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.common.DispatcherProvider
import com.vedica.labs.ind.app.docora.core.handler.DocumentHandlerRegistry
import com.vedica.labs.ind.app.docora.core.model.Document
import com.vedica.labs.ind.app.docora.core.model.DocumentType
import com.vedica.labs.ind.app.docora.core.model.TextOrigin
import com.vedica.labs.ind.app.docora.core.pdf.PdfBookmarkStore
import com.vedica.labs.ind.app.docora.core.pdf.PdfRenderRepository
import com.vedica.labs.ind.app.docora.core.pdf.PdfSearchHit
import com.vedica.labs.ind.app.docora.core.pdf.PdfTextExtractor
import com.vedica.labs.ind.app.docora.core.pdf.PdfUnlockResult
import com.vedica.labs.ind.app.docora.core.pdf.PdfUnlockStore
import com.vedica.labs.ind.app.docora.core.repository.DocumentRepository
import com.vedica.labs.ind.app.docora.core.util.MimeTypes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    /** True when a PDF is encrypted and the password has not been supplied yet. */
    val passwordRequired: Boolean = false,
    /** True while a supplied password is being verified/decrypted. */
    val passwordSubmitting: Boolean = false,
    /** Wrong-password/unlock failure surfaced inside the password dialog. */
    val passwordErrorRes: Int? = null,
    /** Extra rotation applied on top of the page's own `/Rotate`, in 90° steps. */
    val rotation: Int = 0,
    /** Inverts rendered colours for low-light reading. */
    val inverted: Boolean = false,
    /** Zero-based page indexes bookmarked for this document. */
    val bookmarks: Set<Int> = emptySet(),
    val searchQuery: String = "",
    val searchHits: List<PdfSearchHit> = emptyList(),
    val searchLoading: Boolean = false,
)

/**
 * Viewer logic (PRD §10).
 *
 * PDF pages are rasterised through [PdfRenderRepository], which keeps a single PDFium
 * session and a byte-bounded LRU cache for the document on screen. A page slot asks for
 * its bitmap only while composed, so a 1,000-page PDF holds only the visible pages plus
 * the cache window (PRD §35, §36).
 *
 * Text search runs through [PdfTextExtractor] because PDFium's renderer exposes no text
 * layer; the extractor memoises the parsed pages so typing does not re-parse the file on
 * every keystroke. Bookmarks are a reading aid and stay out of the PDF itself
 * ([PdfBookmarkStore]).
 */
@HiltViewModel
class ViewerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentRepository: DocumentRepository,
    private val handlerRegistry: DocumentHandlerRegistry,
    private val pdfRenderRepository: PdfRenderRepository,
    private val pdfTextExtractor: PdfTextExtractor,
    private val pdfBookmarkStore: PdfBookmarkStore,
    private val pdfUnlockStore: PdfUnlockStore,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(ViewerUiState())
    val state: StateFlow<ViewerUiState> = _state.asStateFlow()

    private var loadedId: String? = null
    private var bookmarkJob: Job? = null
    private var searchJob: Job? = null
    private var textJob: Job? = null

    fun load(documentId: String?) {
        if (documentId == null || documentId == loadedId) return
        loadedId = documentId
        bookmarkJob?.cancel()
        searchJob?.cancel()
        textJob?.cancel()
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
            var passwordRequired = false
            if (mode == ViewerMode.PDF) {
                // Opening here means the first page slot never pays the parse cost twice and
                // a broken file is reported before the pager is composed.
                val opened = runCatching { pdfRenderRepository.open(document.uri) }
                opened.onSuccess { pageCount = it.pageCount }
                opened.onFailure {
                    // PdfRenderer cannot read encrypted files. PDFBox can tell us whether the
                    // failure is a password gate (fixable by the user) or real corruption.
                    passwordRequired = runCatching {
                        pdfUnlockStore.isPasswordProtected(document.uri)
                    }.getOrDefault(false)
                    if (!passwordRequired) error = R.string.viewer_render_failed
                }
            }
            _state.value = ViewerUiState(
                document = document,
                mode = mode,
                pageCount = pageCount,
                error = error,
                isLoading = false,
                passwordRequired = passwordRequired,
            )
            if (mode == ViewerMode.PDF) observeBookmarks(document)
            if (mode == ViewerMode.TEXT) requestText()
        }
    }

    /** Verifies a password, decrypts the PDF into the session cache and opens it. */
    fun unlock(password: String) {
        val current = _state.value
        val document = current.document ?: return
        if (current.passwordSubmitting || password.isEmpty()) return
        _state.value = current.copy(passwordSubmitting = true, passwordErrorRes = null)
        viewModelScope.launch {
            when (val result = pdfUnlockStore.unlock(document.uri, password)) {
                is PdfUnlockResult.Unlocked -> {
                    // Drop the failed session and any empty text cached while locked.
                    runCatching { pdfRenderRepository.close() }
                    val opened = runCatching { pdfRenderRepository.open(document.uri) }
                    if (opened.isSuccess) {
                        runCatching { pdfTextExtractor.clear() }
                        _state.value = _state.value.copy(
                            passwordRequired = false,
                            passwordSubmitting = false,
                            passwordErrorRes = null,
                            error = null,
                            pageCount = opened.getOrThrow().pageCount,
                        )
                    } else {
                        _state.value = _state.value.copy(
                            passwordSubmitting = false,
                            passwordErrorRes = R.string.viewer_password_failed,
                        )
                    }
                }
                PdfUnlockResult.WrongPassword -> _state.value = _state.value.copy(
                    passwordSubmitting = false,
                    passwordErrorRes = R.string.viewer_password_incorrect,
                )
                is PdfUnlockResult.Failed -> _state.value = _state.value.copy(
                    passwordSubmitting = false,
                    passwordErrorRes = R.string.viewer_password_failed,
                )
            }
        }
    }

    /** Clears the inline error as soon as the user edits the password field. */
    fun clearPasswordError() {
        if (_state.value.passwordErrorRes != null) {
            _state.value = _state.value.copy(passwordErrorRes = null)
        }
    }

    fun retry() {
        val id = loadedId
        loadedId = null
        load(id)
    }

    private fun observeBookmarks(document: Document) {
        bookmarkJob?.cancel()
        bookmarkJob = viewModelScope.launch {
            pdfBookmarkStore.observe(document.id).collect { pages ->
                _state.value = _state.value.copy(bookmarks = pages)
            }
        }
    }

    private fun resolveHandler(document: Document) =
        handlerRegistry.getHandlerForMime(document.mimeType)
            ?: handlerRegistry.getHandlerForType(document.type)
            ?: error("no handler for ${document.type}")

    /** Renders one page on demand through the shared session and cache. */
    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): RenderedPage {
        val current = _state.value
        val document = current.document ?: return RenderedPage(pageIndex, null, null, true)
        return withContext(dispatchers.io) {
            when (current.mode) {
                ViewerMode.PDF -> {
                    val rendered = runCatching {
                        pdfRenderRepository.renderPage(
                            uri = document.uri,
                            pageIndex = pageIndex,
                            targetWidthPx = targetWidthPx,
                            rotationDegrees = current.rotation,
                        )
                    }.getOrNull()
                    if (rendered == null) {
                        RenderedPage(pageIndex, null, null, true)
                    } else {
                        RenderedPage(pageIndex, rendered.bitmap, rendered.aspect)
                    }
                }
                ViewerMode.IMAGE -> {
                    val bitmap = runCatching {
                        handlerRegistry.getHandlerForType(DocumentType.IMAGE)
                            ?.renderThumbnail(Uri.parse(document.uri), 0, targetWidthPx)
                            ?.getOrNull()
                    }.getOrNull()
                    if (bitmap == null) {
                        RenderedPage(pageIndex, null, null, true)
                    } else {
                        RenderedPage(pageIndex, bitmap, bitmap.height.toFloat() / bitmap.width)
                    }
                }
                else -> RenderedPage(pageIndex, null, null, true)
            }
        }
    }

    /** Thumbnail for the page rail and the page editor. */
    suspend fun renderPageThumbnail(pageIndex: Int): Bitmap? {
        val current = _state.value
        val document = current.document ?: return null
        return withContext(dispatchers.io) {
            runCatching {
                if (current.mode == ViewerMode.PDF) {
                    pdfRenderRepository.renderThumbnail(document.uri, pageIndex)
                } else {
                    handlerRegistry.getHandlerForType(document.type)
                        ?.renderThumbnail(Uri.parse(document.uri), pageIndex, 160)
                        ?.getOrNull()
                }
            }.getOrNull()
        }
    }

    fun rotate() {
        _state.value = _state.value.copy(rotation = (_state.value.rotation + 90) % 360)
    }

    fun toggleInverted() {
        _state.value = _state.value.copy(inverted = !_state.value.inverted)
    }

    fun toggleBookmark(pageIndex: Int) {
        val document = _state.value.document ?: return
        if (_state.value.mode != ViewerMode.PDF) return
        viewModelScope.launch {
            runCatching { pdfBookmarkStore.toggle(document.id, pageIndex) }
        }
    }

    /** Debounced in-document search over the extracted text layer. */
    fun search(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        searchJob?.cancel()
        val document = _state.value.document ?: return
        val needle = query.trim()
        if (needle.length < MIN_SEARCH_CHARS || _state.value.mode != ViewerMode.PDF) {
            _state.value = _state.value.copy(searchHits = emptyList(), searchLoading = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            _state.value = _state.value.copy(searchLoading = true)
            val hits = runCatching {
                pdfTextExtractor.search(document.uri, needle)
            }.getOrDefault(emptyList())
            _state.value = _state.value.copy(searchHits = hits, searchLoading = false)
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _state.value = _state.value.copy(
            searchQuery = "",
            searchHits = emptyList(),
            searchLoading = false,
        )
    }

    /** Loads extracted/recognised text for the text panel (PRD §10, §13). */
    fun requestText() {
        val current = _state.value
        val document = current.document ?: return
        if (current.textLoading || current.textContent != null) return
        _state.value = current.copy(textLoading = true)
        textJob = viewModelScope.launch {
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

    fun pageLabel(page: Int): String =
        context.getString(R.string.viewer_page_of, page + 1, _state.value.pageCount)

    override fun onCleared() {
        bookmarkJob?.cancel()
        searchJob?.cancel()
        textJob?.cancel()
        // The shared render repository stays open: closing it here would race a viewer opened
        // from the same flow. It drops the previous session when a different PDF is opened.
        super.onCleared()
    }

    private companion object {
        /** The text panel is for reading and finding, not for loading 100 MB into RAM. */
        const val MAX_TEXT_CHARS = 400_000

        const val MIN_SEARCH_CHARS = 2
        const val SEARCH_DEBOUNCE_MILLIS = 250L
    }
}
