package com.vedica.labs.ind.app.docora.ui.scanner

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.common.DocoraLog
import com.vedica.labs.ind.app.docora.core.datastore.PreferencesManager
import com.vedica.labs.ind.app.docora.core.files.AppStorage
import com.vedica.labs.ind.app.docora.core.imaging.AutoCaptureDecider
import com.vedica.labs.ind.app.docora.core.imaging.ScanImageProcessor
import com.vedica.labs.ind.app.docora.core.model.Document
import com.vedica.labs.ind.app.docora.core.model.FlashMode
import com.vedica.labs.ind.app.docora.core.model.ScanFilter
import com.vedica.labs.ind.app.docora.core.model.ScanMode
import com.vedica.labs.ind.app.docora.core.model.ScanOutput
import com.vedica.labs.ind.app.docora.core.model.ScannerQuality
import com.vedica.labs.ind.app.docora.core.repository.DocumentRepository
import com.vedica.labs.ind.app.docora.core.scanner.ScanDocumentWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** One captured page, as the review strip and the save pipeline see it. */
data class ScanPageUi(
    val id: Long,
    val filePath: String,
    val uri: String,
    val rotationDegrees: Int = 0,
) {
    val file: File get() = File(filePath)
}

/** Immutable scanner state: capture settings, the captured pages, and the save progress. */
data class ScannerUiState(
    val mode: ScanMode = ScanMode.default,
    val filter: ScanFilter = ScanFilter.default,
    val quality: ScannerQuality = ScannerQuality.default,
    val flashMode: FlashMode = FlashMode.AUTO,
    val autoCapture: Boolean = true,
    val torchOn: Boolean = false,
    val useFrontCamera: Boolean = false,
    val output: ScanOutput = ScanOutput.default,
    val pages: List<ScanPageUi> = emptyList(),
    /** Filtered preview bitmaps, keyed by page id. Replaced atomically so the strip never flickers. */
    val previews: Map<Long, Bitmap> = emptyMap(),
    val isProcessing: Boolean = false,
    val isSaving: Boolean = false,
    val saveCompleted: Int = 0,
    val saveTotal: Int = 0,
    /** Auto capture hint: the current frame looks like a document and is holding still. */
    val pageLooksReady: Boolean = false,
    val cameraReady: Boolean = false,
    val permissionDenied: Boolean = false,
) {
    val pageCount: Int get() = pages.size
    val canSave: Boolean get() = pages.isNotEmpty() && !isSaving && !isProcessing
    val isBusy: Boolean get() = isSaving || isProcessing
}

/**
 * Capture logic for the scanner.
 *
 * The view model owns everything that is *state* (pages, filters, progress) and nothing that is
 * *hardware*: binding CameraX, writing the JPEG and reading frames stays in the screen, which keeps
 * this class free of camera types and therefore unit testable. The two ends meet through three narrow
 * seams - [newStagingFile], [onPageCaptured] and [captureRequests].
 */
@HiltViewModel
class ScannerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appStorage: AppStorage,
    private val writer: ScanDocumentWriter,
    private val documentRepository: DocumentRepository,
    private val preferencesManager: PreferencesManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ScannerUiState())
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Auto capture asks the screen to fire the shutter; a shared flow keeps it a one-shot event. */
    private val _captureRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val captureRequests: SharedFlow<Unit> = _captureRequests.asSharedFlow()

    private val decider = AutoCaptureDecider()
    private var previewJob: Job? = null
    private var pageSequence = 0L
    private var lastAutoCaptureAt = 0L

    init {
        viewModelScope.launch {
            val settings = preferencesManager.settings.first()
            _state.update {
                it.copy(
                    mode = settings.scannerMode,
                    filter = settings.scannerFilter,
                    quality = settings.scannerQuality,
                    autoCapture = settings.scannerAutoCapture,
                )
            }
        }
    }

    // ------------------------------------------------------------------ capture

    /** A fresh file for CameraX to write the next page into. */
    fun newStagingFile(): File =
        appStorage.newStagingFile("page_${System.currentTimeMillis()}.jpg")

    /**
     * Registers a captured page and renders its preview with the active filter.
     *
     * The page joins the strip immediately (a capture is never silently lost) and its preview is
     * filled in as soon as it has been decoded, which is why those are two separate updates.
     */
    fun onPageCaptured(file: File) {
        val page = ScanPageUi(
            id = ++pageSequence,
            filePath = file.absolutePath,
            uri = runCatching { appStorage.uriFor(file) }.getOrDefault(file.absolutePath),
        )
        _state.update { current ->
            current.copy(pages = current.pages + page, isProcessing = true)
        }
        viewModelScope.launch {
            val filter = _state.value.filter
            val preview = withContext(Dispatchers.IO) {
                ScanImageProcessor.preview(file, PREVIEW_LONG_EDGE, filter, page.rotationDegrees)
            }
            _state.update { current ->
                current.copy(
                    isProcessing = false,
                    previews = if (preview != null) {
                        current.previews + (page.id to preview)
                    } else {
                        current.previews
                    },
                )
            }
            if (preview == null) {
                DocoraLog.w(TAG, "preview_failed", IllegalStateException(page.filePath))
            }
        }
    }

    /** Drops a page the user removed, so its thumbnail and staged file can be collected. */
    fun removePage(pageId: Long) {
        val page = _state.value.pages.firstOrNull { it.id == pageId }
        _state.update { current ->
            current.copy(
                pages = current.pages.filterNot { it.id == pageId },
                previews = current.previews - pageId,
            )
        }
        page?.let { removed ->
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { removed.file.delete() }
            }
        }
    }

    /** Rotates a page a quarter turn clockwise and re-renders its preview immediately. */
    fun rotatePage(pageId: Long) {
        val page = _state.value.pages.firstOrNull { it.id == pageId } ?: return
        val rotated = page.copy(rotationDegrees = (page.rotationDegrees + 90) % 360)
        _state.update { current ->
            current.copy(pages = current.pages.map { if (it.id == pageId) rotated else it })
        }
        renderPreview(rotated)
    }

    /** Moves a page one position earlier or later in the saved document. */
    fun movePage(pageId: Long, delta: Int) {
        _state.update { current ->
            val index = current.pages.indexOfFirst { it.id == pageId }
            if (index < 0) return@update current
            val target = (index + delta).coerceIn(current.pages.indices)
            if (target == index) return@update current
            val reordered = current.pages.toMutableList().apply { add(target, removeAt(index)) }
            current.copy(pages = reordered)
        }
    }

    /** Throws the session away: staged files are deleted and nothing reaches the library. */
    fun discard() {
        previewJob?.cancel()
        _state.update { it.copy(pages = emptyList(), previews = emptyMap(), isProcessing = false) }
        decider.reset()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { appStorage.clearScanStaging() }
        }
    }

    // ------------------------------------------------------------------ settings

    /** Changing the mode also adopts that mode's recommended filter, which is its whole point. */
    fun setMode(mode: ScanMode) {
        _state.update { it.copy(mode = mode, filter = mode.defaultFilter) }
        renderAllPreviews()
    }

    fun setFilter(filter: ScanFilter) {
        if (_state.value.filter == filter) return
        _state.update { it.copy(filter = filter) }
        renderAllPreviews()
    }

    fun setFlashMode(flashMode: FlashMode) = _state.update { it.copy(flashMode = flashMode) }

    /** Cycles Auto -> On -> Off, which is what a single flash button on a camera app does. */
    fun cycleFlashMode() {
        setFlashMode(
            when (_state.value.flashMode) {
                FlashMode.AUTO -> FlashMode.ON
                FlashMode.ON -> FlashMode.OFF
                FlashMode.OFF -> FlashMode.AUTO
            },
        )
    }

    fun setTorch(on: Boolean) = _state.update { it.copy(torchOn = on) }

    fun setAutoCapture(enabled: Boolean) {
        decider.reset()
        _state.update { it.copy(autoCapture = enabled, pageLooksReady = false) }
    }

    fun setOutput(output: ScanOutput) = _state.update { it.copy(output = output) }

    /** Switches between the back and front camera; only meaningful on devices that have both. */
    fun flipCamera() {
        decider.reset()
        _state.update { it.copy(useFrontCamera = !it.useFrontCamera, pageLooksReady = false) }
    }

    fun onCameraReady() {
        if (!_state.value.cameraReady) _state.update { it.copy(cameraReady = true) }
    }

    fun onCameraUnavailable() {
        if (_state.value.cameraReady) _state.update { it.copy(cameraReady = false) }
    }

    fun onPermissionDenied() {
        if (!_state.value.permissionDenied) _state.update { it.copy(permissionDenied = true) }
    }

    /**
     * Reports a failed shutter release.
     *
     * CameraX failures are almost always transient (the camera is being reconfigured, or another app
     * grabbed it), so this surfaces a message instead of changing any capture state.
     */
    fun onCaptureFailed(cause: Throwable) {
        DocoraLog.w(TAG, "capture_failed", cause)
        _message.value = context.getString(R.string.scan_capture_failed)
    }

    fun clearMessage() {
        _message.value = null
    }

    // ------------------------------------------------------------------ auto capture

    /**
     * Feeds the auto-capture heuristic and honours its verdict.
     *
     * The cooldown is what makes auto capture usable: without it, holding a page still for a second
     * would produce a burst of identical pages, because the frame stays "steady" the entire time.
     */
    fun onAutoCaptureVerdict(verdict: AutoCaptureDecider.Verdict) {
        val ready = verdict.documentVisible && verdict.steady
        if (ready != _state.value.pageLooksReady) {
            _state.update { it.copy(pageLooksReady = ready) }
        }
        val current = _state.value
        if (!verdict.shouldCapture || !current.autoCapture || current.isBusy) return
        val now = System.currentTimeMillis()
        if (now - lastAutoCaptureAt < AUTO_CAPTURE_COOLDOWN_MS) return
        lastAutoCaptureAt = now
        _captureRequests.tryEmit(Unit)
    }

    // ------------------------------------------------------------------ save

    /**
     * Processes every page at the configured quality and writes the result into the library.
     *
     * Processing is a full-resolution pass (decode, rotate, filter, scale) that deliberately happens
     * here rather than at capture time: the user may rotate pages or change the filter while
     * reviewing, so re-running the pipeline once at save is cheaper than re-encoding on every edit.
     */
    fun save(onSaved: (List<String>) -> Unit) {
        val snapshot = _state.value
        if (snapshot.pages.isEmpty() || snapshot.isBusy) return
        viewModelScope.launch {
            _state.update {
                it.copy(isSaving = true, saveCompleted = 0, saveTotal = snapshot.pages.size)
            }
            val processed = mutableListOf<Bitmap>()
            val outcome = runCatching {
                snapshot.pages.forEachIndexed { index, page ->
                    _state.update { it.copy(saveCompleted = index) }
                    val bitmap = withContext(Dispatchers.IO) {
                        ScanImageProcessor.processPage(
                            file = page.file,
                            targetLongEdge = snapshot.quality.targetLongEdgePx,
                            filter = snapshot.filter,
                            rotationDegrees = page.rotationDegrees,
                        )
                    } ?: error("Page ${index + 1} could not be processed")
                    processed += bitmap
                }
                _state.update { it.copy(saveCompleted = snapshot.pages.size) }
                val timestamp = System.currentTimeMillis()
                when (snapshot.output) {
                    ScanOutput.PDF -> {
                        val request = writer.writePdf(processed, timestamp).getOrNull()
                            ?: error("PDF export failed")
                        listOf(documentRepository.import(request).id)
                    }
                    ScanOutput.IMAGES -> {
                        val requests = writer.writeImages(
                            pages = processed,
                            quality = snapshot.quality,
                            filter = snapshot.filter,
                            timestamp = timestamp,
                        ).getOrNull() ?: error("Image export failed")
                        documentRepository.importAll(requests).map { it.id }
                    }
                }
            }
            writer.recycle(processed)
            outcome
                .onSuccess { ids -> onSaveSucceeded(ids, snapshot, onSaved) }
                .onFailure { throwable ->
                    DocoraLog.w(TAG, "save_failed", throwable)
                    _state.update { it.copy(isSaving = false, saveCompleted = 0, saveTotal = 0) }
                    _message.value = context.getString(R.string.scan_save_failed)
                }
        }
    }

    private suspend fun onSaveSucceeded(
        ids: List<String>,
        snapshot: ScannerUiState,
        onSaved: (List<String>) -> Unit,
    ) {
        appStorage.clearScanStaging()
        decider.reset()
        _state.update {
            it.copy(
                isSaving = false,
                saveCompleted = 0,
                saveTotal = 0,
                pages = emptyList(),
                previews = emptyMap(),
                pageLooksReady = false,
            )
        }
        val saved: Document? = ids.firstOrNull()?.let { documentRepository.getDocument(it) }
        _message.value = when {
            snapshot.output != ScanOutput.PDF ->
                context.getString(R.string.scan_saved_images, ids.size)
            saved != null -> context.getString(R.string.scan_saved_pdf, saved.displayName)
            else -> context.getString(R.string.scan_saved_images, ids.size)
        }
        onSaved(ids)
    }

    // ------------------------------------------------------------------ previews

    /** Re-renders one page's preview; used after a rotation so the strip updates immediately. */
    private fun renderPreview(page: ScanPageUi) {
        viewModelScope.launch {
            val filter = _state.value.filter
            val bitmap = withContext(Dispatchers.IO) {
                ScanImageProcessor.preview(page.file, PREVIEW_LONG_EDGE, filter, page.rotationDegrees)
            } ?: return@launch
            _state.update { it.copy(previews = it.previews + (page.id to bitmap)) }
        }
    }

    /**
     * Re-renders every preview after the filter changed.
     *
     * Runs sequentially on IO and cancels any earlier pass, so moving along the filter strip costs a
     * bounded amount of work instead of one parallel decode per page.
     */
    private fun renderAllPreviews() {
        val pages = _state.value.pages
        if (pages.isEmpty()) return
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val filter = _state.value.filter
            val rendered = HashMap<Long, Bitmap>(pages.size)
            pages.forEach { page ->
                val bitmap = withContext(Dispatchers.IO) {
                    ScanImageProcessor.preview(
                        file = page.file,
                        maxLongEdge = PREVIEW_LONG_EDGE,
                        filter = filter,
                        rotationDegrees = page.rotationDegrees,
                    )
                }
                if (bitmap != null) rendered[page.id] = bitmap
            }
            _state.update { it.copy(previews = rendered) }
        }
    }

    private companion object {
        const val TAG = "ScannerViewModel"

        /** Long edge of a strip thumbnail: sharp on a 3x display, about 200 KB in memory. */
        const val PREVIEW_LONG_EDGE = 320

        /** Time auto capture waits before it may fire again, so one page yields exactly one page. */
        const val AUTO_CAPTURE_COOLDOWN_MS = 2_500L
    }
}
