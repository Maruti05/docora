package com.vedica.labs.ind.app.docora.ui.scanner

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.common.DocoraLog
import com.vedica.labs.ind.app.docora.core.datastore.PreferencesManager
import com.vedica.labs.ind.app.docora.core.files.AppStorage
import com.vedica.labs.ind.app.docora.core.imaging.DocumentEdgeDetector
import com.vedica.labs.ind.app.docora.core.imaging.OpenCvDocumentDetector
import com.vedica.labs.ind.app.docora.core.imaging.OpenCvRuntime
import com.vedica.labs.ind.app.docora.core.imaging.ScanImageMath
import com.vedica.labs.ind.app.docora.core.imaging.ScanImageProcessor
import com.vedica.labs.ind.app.docora.core.model.Document
import com.vedica.labs.ind.app.docora.core.model.DocumentDetection
import com.vedica.labs.ind.app.docora.core.model.FlashMode
import com.vedica.labs.ind.app.docora.core.model.ScanFilter
import com.vedica.labs.ind.app.docora.core.model.ScanMode
import com.vedica.labs.ind.app.docora.core.model.ScanOutput
import com.vedica.labs.ind.app.docora.core.model.QuadCorners
import com.vedica.labs.ind.app.docora.core.model.ScannerQuality
import com.vedica.labs.ind.app.docora.core.processing.DocumentEnricher
import com.vedica.labs.ind.app.docora.core.repository.DocumentRepository
import com.vedica.labs.ind.app.docora.core.scanner.ScanDocumentWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    /**
     * Enhancement applied to this page only.
     *
     * Each capture is reviewed on its own screen, so the filter the user settles on there belongs
     * to that page; a receipt shot in black and white must not restyle the colour photo captured
     * before it.
     */
    val filter: ScanFilter = ScanFilter.default,
    /**
     * The document outline detected when this page was shot, normalised to the photo.
     *
     * Stored per page rather than kept as a single live value because each page is framed
     * individually: page two may be photographed at a different angle, and cropping it with page
     * one's corners would cut off exactly the content the detector had found.
     */
    val corners: QuadCorners = QuadCorners.fullFrame(),
) {
    val file: File get() = File(filePath)
}

/** Immutable scanner state: capture settings, the captured pages, and the save progress. */
data class ScannerUiState(
    val mode: ScanMode = ScanMode.default,
    val filter: ScanFilter = ScanFilter.default,
    val quality: ScannerQuality = ScannerQuality.default,
    val flashMode: FlashMode = FlashMode.AUTO,
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
    /** True while the live detector has a confident page outline. */
    val pageLooksReady: Boolean = false,
    /** Latest detected page outline, or null while nothing is found. Drives the live overlay. */
    val detectedCorners: QuadCorners? = null,
    /** Detector confidence for [detectedCorners]; also fades the outline out when the page leaves. */
    val detectionConfidence: Float = 0f,
    /** Confidence expressed 0..1, used for the guidance ring. */
    val stability: Float = 0f,
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
    private val documentEnricher: DocumentEnricher,
) : ViewModel() {

    private val _state = MutableStateFlow(ScannerUiState())
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var previewJob: Job? = null
    private var pageSequence = 0L

    init {
        viewModelScope.launch {
            val settings = preferencesManager.settings.first()
            _state.update {
                it.copy(
                    mode = settings.scannerMode,
                    filter = settings.scannerFilter,
                    quality = settings.scannerQuality,
                )
            }
        }
    }

    // ------------------------------------------------------------------ capture

    /** A fresh file for CameraX to write the next page into. */
    fun newStagingFile(): File =
        appStorage.newStagingFile("page_${System.currentTimeMillis()}.jpg")

    /**
     * Registers a captured page, detects its edges and renders its preview.
     *
     * The page joins the strip immediately (a capture is never silently lost) and starts as the
     * whole frame, then the detector runs over the page's own pixels and the crop snaps to the
     * document. Detecting on the still rather than reusing the live outline matters: the live frame
     * covers a wider field than the shutter saves, and a large surface behind the page can win the
     * live contour. On the saved photo there is nothing but the page.
     *
     * Imported photos follow the same path - they are simply captures whose shutter was another app.
     */
    fun onPageCaptured(file: File, corners: QuadCorners? = null): Long {
        val page = ScanPageUi(
            id = ++pageSequence,
            filePath = file.absolutePath,
            uri = runCatching { appStorage.uriFor(file) }.getOrDefault(file.absolutePath),
            filter = _state.value.filter,
            corners = corners ?: QuadCorners.fullFrame(),
        )
        _state.update { current ->
            current.copy(pages = current.pages + page, isProcessing = true)
        }
        viewModelScope.launch {
            val detected = if (corners != null) {
                corners
            } else {
                detectCorners(file, rotationDegrees = 0)
            }
            val cropped = if (detected != null && detected != page.corners) {
                val updated = page.copy(corners = detected)
                _state.update { current ->
                    current.copy(pages = current.pages.map { if (it.id == page.id) updated else it })
                }
                DocoraLog.d(
                    TAG,
                    "page_detected",
                    "page" to page.id,
                    "tlX" to detected.topLeftX,
                    "tlY" to detected.topLeftY,
                    "brX" to detected.bottomRightX,
                    "brY" to detected.bottomRightY,
                )
                updated
            } else {
                page
            }
            val preview = withContext(Dispatchers.IO) {
                ScanImageProcessor.preview(
                    file = file,
                    maxLongEdge = PREVIEW_LONG_EDGE,
                    filter = cropped.filter,
                    rotationDegrees = cropped.rotationDegrees,
                    corners = cropped.corners,
                )
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
        return page.id
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

    /**
     * Applies manually adjusted crop corners to one page and refreshes its preview.
     *
     * Corners are stored in the unrotated capture space (the same space the save pipeline crops
     * in), so the review thumbnail immediately reflects the new crop.
     */
    fun setPageCorners(pageId: Long, corners: QuadCorners) {
        val page = _state.value.pages.firstOrNull { it.id == pageId } ?: return
        val updated = page.copy(corners = corners)
        _state.update { current ->
            current.copy(pages = current.pages.map { if (it.id == pageId) updated else it })
        }
        renderPreview(updated)
    }

    /** Runs edge detection on one captured page and returns corners in its displayed orientation. */
    suspend fun detectPageCorners(pageId: Long): QuadCorners? {
        val page = _state.value.pages.firstOrNull { it.id == pageId } ?: return null
        return detectCorners(page.file, page.rotationDegrees)
    }

    /** Throws the session away: staged files are deleted and nothing reaches the library. */
    fun discard() {
        previewJob?.cancel()
        _state.update { it.copy(pages = emptyList(), previews = emptyMap(), isProcessing = false) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { appStorage.clearScanStaging() }
        }
    }

    // ------------------------------------------------------------------ settings

    /** Changing the mode adopts that mode's recommended filter for the pages captured from now on. */
    fun setMode(mode: ScanMode) {
        _state.update { it.copy(mode = mode, filter = mode.defaultFilter) }
    }

    /**
     * Applies one filter to every captured page.
     *
     * This is the batch action in the page review sheet; the capture screen changes a single page
     * through [setPageFilter].
     */
    fun setFilter(filter: ScanFilter) {
        val current = _state.value
        if (current.filter == filter && current.pages.all { it.filter == filter }) return
        _state.update { state ->
            state.copy(
                filter = filter,
                pages = state.pages.map { it.copy(filter = filter) },
            )
        }
        renderAllPreviews()
    }

    /** Applies a filter to one page only, which is what the post-capture review screen does. */
    fun setPageFilter(pageId: Long, filter: ScanFilter) {
        val page = _state.value.pages.firstOrNull { it.id == pageId } ?: return
        if (page.filter == filter) return
        val updated = page.copy(filter = filter)
        _state.update { current ->
            current.copy(pages = current.pages.map { if (it.id == pageId) updated else it })
        }
        renderPreview(updated)
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

    fun setOutput(output: ScanOutput) = _state.update { it.copy(output = output) }

    /** Switches between the back and front camera; only meaningful on devices that have both. */
    fun flipCamera() {
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

    // ------------------------------------------------------------------ live detection

    /**
     * Publishes the latest detected outline for the viewfinder.
     *
     * There is deliberately no automatic shutter here: a capture only happens when the user taps
     * the shutter. Each capture then goes to its own review screen, so holding the phone still can
     * never produce a burst of duplicate pages.
     */
    fun onFrameAnalyzed(detection: DocumentDetection) {
        val current = _state.value
        val ready = detection.hasDocument
        val confidence = detection.confidence.coerceIn(0f, 1f)

        // Analysis runs at the camera frame rate; only publish meaningful movement/confidence
        // changes so the HUD is not recomposed for every identical frame.
        val cornersMoved = quadMoved(current.detectedCorners, detection.corners)
        val confidenceShifted = kotlin.math.abs(current.detectionConfidence - confidence) > 0.02f
        if (ready != current.pageLooksReady || cornersMoved || confidenceShifted) {
            _state.update {
                it.copy(
                    pageLooksReady = ready,
                    stability = confidence,
                    detectedCorners = detection.corners,
                    detectionConfidence = confidence,
                )
            }
        }
    }

    /**
     * True when a newly detected quad differs from the current one by more than a hair.
     *
     * The detector eases its corners every frame, so an exact comparison would never be equal; the
     * threshold is what separates "the outline is moving" from "the outline is holding still and the
     * last decimal changed".
     */
    private fun quadMoved(current: QuadCorners?, next: QuadCorners?): Boolean {
        if (current == null || next == null) return current != next
        val before = current.toPointList()
        val after = next.toPointList()
        return before.indices.any { index ->
            kotlin.math.abs(before[index].x - after[index].x) > QUAD_EPSILON ||
                kotlin.math.abs(before[index].y - after[index].y) > QUAD_EPSILON
        }
    }

    /**
     * Adds a page from the gallery instead of the camera.
     *
     * The picked file is copied into the staging area first: the content URI may point at a
     * provider the app only has temporary access to, and the scan pipeline - previews, rotation,
     * cropping and the final save - all work on files it owns.
     */
    fun importPicked(uri: Uri) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val target = appStorage.newStagingFile("import_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("The picked file could not be opened")
                    target
                }.getOrNull()
            }
            if (file == null) {
                DocoraLog.w(TAG, "import_failed", IllegalStateException(uri.toString()))
                _message.value = context.getString(R.string.scan_import_failed)
                return@launch
            }
            // The page's own edge detection happens in onPageCaptured; imported photos follow the
            // same crop-and-filter path as a camera capture so both behave identically.
            onPageCaptured(file)
        }
    }

    /**
     * Runs edge detection once over a captured file.
     *
     * Detection happens on the displayed (rotation-applied) orientation so the crop editor can use
     * the result directly. OpenCV is tried first for a full-resolution contour pass; the pure-Kotlin
     * detector is the fallback when native loading is unavailable or finds no quad.
     */
    private suspend fun detectCorners(file: File, rotationDegrees: Int): QuadCorners? =
        withContext(Dispatchers.IO) {
            val source = ScanImageProcessor.decode(file, DETECTION_LONG_EDGE)
                ?: return@withContext null
            val display = if (rotationDegrees == 0) {
                source
            } else {
                ScanImageProcessor.rotate(source, rotationDegrees)
            }
            try {
                if (display.width <= 0 || display.height <= 0) return@withContext null
                if (OpenCvRuntime.isReady) {
                    OpenCvDocumentDetector().detect(display)?.let { return@withContext it }
                }
                val pixels = IntArray(display.width * display.height)
                display.getPixels(pixels, 0, display.width, 0, 0, display.width, display.height)
                val gray = ScanImageMath.graysOf(pixels)
                DocumentEdgeDetector(
                    smoothing = 1f,
                    releaseAfterMissedFrames = 1,
                ).detect(gray, display.width, display.height).corners
            } finally {
                if (display !== source) runCatching { source.recycle() }
                runCatching { display.recycle() }
            }
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
                            filter = page.filter,
                            rotationDegrees = page.rotationDegrees,
                            corners = page.corners,
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
        documentEnricher.start()
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

    /** Re-renders one page's preview; used after an edit so the strip updates immediately. */
    private fun renderPreview(page: ScanPageUi) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                ScanImageProcessor.preview(
                    file = page.file,
                    maxLongEdge = PREVIEW_LONG_EDGE,
                    filter = page.filter,
                    rotationDegrees = page.rotationDegrees,
                    corners = page.corners,
                )
            } ?: return@launch
            _state.update { it.copy(previews = it.previews + (page.id to bitmap)) }
        }
    }

    /**
     * Re-renders every preview after a batch filter change.
     *
     * Runs sequentially on IO and cancels any earlier pass, so moving along the filter strip costs a
     * bounded amount of work instead of one parallel decode per page. Each page keeps its own filter
     * rather than all of them being forced to the newest one.
     */
    private fun renderAllPreviews() {
        val pages = _state.value.pages
        if (pages.isEmpty()) return
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val rendered = HashMap<Long, Bitmap>(pages.size)
            pages.forEach { page ->
                val bitmap = withContext(Dispatchers.IO) {
                    ScanImageProcessor.preview(
                        file = page.file,
                        maxLongEdge = PREVIEW_LONG_EDGE,
                        filter = page.filter,
                        rotationDegrees = page.rotationDegrees,
                        corners = page.corners,
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

        /** Long edge used for one-shot edge detection on a captured page. */
        const val DETECTION_LONG_EDGE = 900

        /** Movement, in normalised frame units, below which the outline is considered stationary. */
        const val QUAD_EPSILON = 0.004f
    }
}
