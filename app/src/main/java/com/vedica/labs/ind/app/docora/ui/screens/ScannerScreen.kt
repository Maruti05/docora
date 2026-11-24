package com.vedica.labs.ind.app.docora.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.imaging.DocumentEdgeDetector
import com.vedica.labs.ind.app.docora.core.imaging.OpenCvDocumentDetector
import com.vedica.labs.ind.app.docora.core.imaging.OpenCvRuntime
import com.vedica.labs.ind.app.docora.core.model.DocumentDetection
import com.vedica.labs.ind.app.docora.core.model.Point2F
import com.vedica.labs.ind.app.docora.core.model.ScanFilter
import com.vedica.labs.ind.app.docora.core.model.ScanMode
import com.vedica.labs.ind.app.docora.core.model.QuadCorners
import com.vedica.labs.ind.app.docora.core.model.ScannerQuality
import com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraGradients
import com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraMotion
import com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraSpacing
import com.vedica.labs.ind.app.docora.ui.designsystem.SystemBarAppearance
import com.vedica.labs.ind.app.docora.ui.scanner.CaptureFlashOverlay
import com.vedica.labs.ind.app.docora.ui.scanner.ScanCaptureButton
import com.vedica.labs.ind.app.docora.ui.scanner.ScanCaptureReview
import com.vedica.labs.ind.app.docora.ui.scanner.ScanCropEditor
import com.vedica.labs.ind.app.docora.ui.scanner.ScanDetectionOverlay
import com.vedica.labs.ind.app.docora.ui.scanner.ScanGlassButton
import com.vedica.labs.ind.app.docora.ui.scanner.ScanGuidancePill
import com.vedica.labs.ind.app.docora.ui.scanner.ScanModeStrip
import com.vedica.labs.ind.app.docora.ui.scanner.ScanPageStrip
import com.vedica.labs.ind.app.docora.ui.scanner.ScanReviewContent
import com.vedica.labs.ind.app.docora.ui.scanner.ScanTopBar
import com.vedica.labs.ind.app.docora.ui.scanner.ScannerPermissionGate
import com.vedica.labs.ind.app.docora.ui.scanner.ScannerSavingOverlay
import com.vedica.labs.ind.app.docora.ui.scanner.ScannerUiState
import com.vedica.labs.ind.app.docora.ui.scanner.ScannerViewModel
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** True when the camera permission is already granted, so the gate can be skipped on a revisit. */
private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Owns the CameraX objects for one visit to the scanner.
 *
 * Keeping the binding in a plain remembered class (rather than inline in the composable) means the
 * camera is bound exactly once per configuration change, the analyzer runs on its own executor, and the
 * composable stays a description of the layout. [isCapturing] and [previewReady] are Compose state
 * because they are read during composition; the rest is deliberately not.
 */
private class CameraSession(context: Context) {

    val controller: LifecycleCameraController = LifecycleCameraController(context)

    private val captureExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /** OpenCV is primary; the pure-Kotlin detector is the fallback when native loading fails. */
    private val openCvDetector = OpenCvDocumentDetector()

    /** Held across frames: the detector smooths and releases the outline itself. */
    private val fallbackDetector = DocumentEdgeDetector()

    var isCapturing by mutableStateOf(false)
        private set

    /** Binds (or rebinds) the camera for the current capture settings. */
    fun bind(
        owner: LifecycleOwner,
        config: CameraSessionConfig,
        onFrame: (DocumentDetection) -> Unit,
    ) {
        openCvDetector.reset()
        fallbackDetector.reset()
        controller.unbind()
        controller.cameraSelector = if (config.useFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        controller.setImageCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        controller.setImageCaptureTargetSize(
            CameraController.OutputSize(targetSizeFor(config.quality)),
        )
        controller.setTapToFocusEnabled(true)
        // The live outline is part of the screen rather than an option, so analysis is always enabled:
        // with auto capture switched off the frames still feed the edge detector and the shutter stays
        // manual. Enabling the use case and installing the analyzer are deliberately one unconditional
        // step - a use case masked out of the set never delivers frames, so gating one of them on
        // auto capture would freeze the outline the moment the user turned auto capture off.
        controller.setEnabledUseCases(
            CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS,
        )
        controller.setImageAnalysisTargetSize(
            CameraController.OutputSize(Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT)),
        )
        controller.setImageAnalysisImageQueueDepth(1)
        controller.setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        val viewAspect = config.previewAspect
        controller.setImageAnalysisAnalyzer(analysisExecutor) { proxy ->
            analyzeFrame(proxy, viewAspect, onFrame)
        }
        controller.bindToLifecycle(owner)
    }

    /** Fires the shutter into [file]; the callback reports success or a user-facing failure. */
    fun capture(
        file: File,
        onCaptured: (File) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        if (isCapturing) return
        isCapturing = true
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        runCatching {
            controller.takePicture(
                options,
                captureExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        isCapturing = false
                        onCaptured(file)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        isCapturing = false
                        onFailed(exception)
                    }
                },
            )
        }.onFailure { throwable ->
            isCapturing = false
            onFailed(throwable)
        }
    }

    /**
     * Reads a luminance frame out of the camera image and hands it to the best available detector.
     *
     * Two coordinate corrections happen here, and both are necessary for the crop to be right:
     *
     *  * the sample window is [ImageProxy.getCropRect], because CameraX crops the analysis stream to
     *    the requested ratio and the padding outside that rect is not part of the picture;
     *  * the result is projected from the wide analysis field into the on-screen viewfinder, which
     *    the shutter also crops to. Without this the detector would find a page the capture cannot
     *    see, and every crop would be shifted and stretched.
     */
    private fun analyzeFrame(
        proxy: ImageProxy,
        viewAspect: Float,
        onFrame: (DocumentDetection) -> Unit,
    ) {
        try {
            val plane = proxy.planes.firstOrNull()
            if (plane == null || proxy.width <= 0 || proxy.height <= 0) return
            val buffer = plane.buffer
            val crop = proxy.cropRect
            val left = crop.left.coerceIn(0, proxy.width - 1)
            val top = crop.top.coerceIn(0, proxy.height - 1)
            val right = crop.right.coerceIn(left + 1, proxy.width)
            val bottom = crop.bottom.coerceIn(top + 1, proxy.height)
            val sourceWidth = right - left
            val sourceHeight = bottom - top
            val sourceAspect = sourceWidth.toFloat() / sourceHeight

            if (OpenCvRuntime.isReady) {
                val bytes = ByteArray(DETECT_WIDTH * DETECT_HEIGHT)
                for (row in 0 until DETECT_HEIGHT) {
                    val y = top + (row * sourceHeight) / DETECT_HEIGHT
                    val rowStart = y * plane.rowStride
                    for (column in 0 until DETECT_WIDTH) {
                        val x = left + (column * sourceWidth) / DETECT_WIDTH
                        val index = rowStart + x * plane.pixelStride
                        bytes[row * DETECT_WIDTH + column] = if (index < buffer.limit()) {
                            buffer.get(index)
                        } else {
                            0
                        }
                    }
                }
                val detection = openCvDetector.detect(bytes, DETECT_WIDTH, DETECT_HEIGHT)
                onFrame(detection.projected(sourceAspect, viewAspect))
                return
            }

            val grid = IntArray(GRID_WIDTH * GRID_HEIGHT)
            for (row in 0 until GRID_HEIGHT) {
                val y = top + (row * sourceHeight) / GRID_HEIGHT
                val rowStart = y * plane.rowStride
                for (column in 0 until GRID_WIDTH) {
                    val x = left + (column * sourceWidth) / GRID_WIDTH
                    val index = rowStart + x * plane.pixelStride
                    grid[row * GRID_WIDTH + column] = if (index < buffer.limit()) {
                        buffer.get(index).toInt() and 0xFF
                    } else {
                        0
                    }
                }
            }
            onFrame(fallbackDetector.detect(grid, GRID_WIDTH, GRID_HEIGHT).projected(sourceAspect, viewAspect))
        } finally {
            proxy.close()
        }
    }

    fun release() {
        runCatching { controller.unbind() }
        captureExecutor.shutdown()
        analysisExecutor.shutdown()
    }

    /** Stops the camera while a full-screen editor covers the preview. */
    fun unbind() {
        runCatching { controller.unbind() }
    }

    /** Resolution matching the configured quality, in portrait orientation. */
    private fun targetSizeFor(quality: ScannerQuality): Size {
        val longEdge = quality.targetLongEdgePx
        return Size((longEdge * 3) / 4, longEdge)
    }

    private companion object {
        /**
         * Size of the luminance grid fed to the auto-capture heuristic and the edge detector.
         *
         * Coarse enough to stay cheap, fine enough that a page boundary lands on a distinct column
         * rather than being smeared across two. Sampling a 96x72 grid from the 640x480 analysis
         * stream gives corner precision roughly 1.5x better than the old 64x48 grid at a cost of
         * about seven thousand samples per frame.
         */
        const val GRID_WIDTH = 96
        const val GRID_HEIGHT = 72
        const val ANALYSIS_WIDTH = 640
        const val ANALYSIS_HEIGHT = 480

        /** Grayscale frame handed to OpenCV; contours do not need full analysis resolution. */
        const val DETECT_WIDTH = 320
        const val DETECT_HEIGHT = 240
    }
}

/** The subset of settings a camera binding depends on, so rebinding only happens when one changes. */
private data class CameraSessionConfig(
    val useFrontCamera: Boolean,
    val quality: ScannerQuality,
    /** Visible viewfinder aspect (width / height); zero until the preview has been measured. */
    val previewAspect: Float = 0f,
)

/**
 * Document scanner.
 *
 * The screen is a thin shell around three things: the camera (bound by [CameraSession]), the capture
 * state ([ScannerViewModel]) and the controls. Capture is deliberately one tap - framing, enhancement
 * and page order are all reversible afterwards, so nothing on this screen asks for confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    controller: NavHostController,
    vm: ScannerViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The camera stage is dark in both themes, so the status icons must stay light.
    SystemBarAppearance(useDarkIcons = false)
    val lifecycleOwner = LocalLifecycleOwner.current
    val snacks = remember { SnackbarHostState() }

    var hasPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    var flashTrigger by remember { mutableStateOf(0) }
    var showReview by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    var cropPageId by remember { mutableStateOf<Long?>(null) }
    var reviewAfterCrop by remember { mutableStateOf(false) }
    var captureReviewId by remember { mutableStateOf<Long?>(null) }

    /**
     * Aspect of the visible viewfinder, measured from the composed preview.
     *
     * Detection runs on the camera's analysis stream, which spans a wider field than the cropped
     * preview. Knowing the on-screen aspect is what lets the detector's corners be projected into
     * the same frame the user sees - and the same frame the shutter saves.
     */
    var previewAspect by remember { mutableStateOf(0f) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (!granted) vm.onPermissionDenied()
    }

    // Importing from the gallery goes through the system picker: no storage permission is needed for
    // the files the user selects, and multiple pages can be added in one go.
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        uris.forEach { vm.importPicked(it) }
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val session = remember { CameraSession(context) }
    DisposableEffect(session) {
        onDispose { session.release() }
    }

    // Rebind only when something the camera actually cares about changes. The rounded preview
    // aspect participates because the detector needs it to project corners into viewfinder space.
    val sessionConfig = CameraSessionConfig(
        useFrontCamera = state.useFrontCamera,
        quality = state.quality,
        previewAspect = (previewAspect * 100f).toInt() / 100f,
    )
    LaunchedEffect(hasPermission, sessionConfig, cropPageId, showReview, captureReviewId) {
        if (hasPermission && cropPageId == null && !showReview && captureReviewId == null) {
            session.bind(lifecycleOwner, sessionConfig, vm::onFrameAnalyzed)
        } else {
            session.unbind()
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snacks.showSnackbar(it)
            vm.clearMessage()
        }
    }

    fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun shoot() {
        if (!hasPermission || state.isBusy || session.isCapturing) return
        flashTrigger += 1
        session.capture(
            file = vm.newStagingFile(),
            onCaptured = { file ->
                captureReviewId = vm.onPageCaptured(file)
            },
            onFailed = vm::onCaptureFailed,
        )
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            ScannerBody(
                state = state,
                session = session,
                showGrid = showGrid,
                onToggleGrid = { showGrid = !showGrid },
                onPickGallery = { galleryLauncher.launch(arrayOf("image/*")) },
                onPreviewSize = { size ->
                    val aspect = if (size.height > 0) size.width.toFloat() / size.height else 0f
                    if (kotlin.math.abs(aspect - previewAspect) > 0.01f) previewAspect = aspect
                },
                onClose = { controller.popBackStack() },
                onShoot = { shoot() },
                onReview = { showReview = true },
                onSave = { vm.save { ids -> ids.firstOrNull()?.let { openViewer(controller, it) } } },
                onCycleFlash = vm::cycleFlashMode,
                onTorch = vm::setTorch,
                onFlipCamera = vm::flipCamera,
                onMode = vm::setMode,
                onRotatePage = vm::rotatePage,
                onRemovePage = vm::removePage,
            )
        } else {
            ScannerPermissionGate(
                denied = state.permissionDenied,
                onAllow = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = ::openAppSettings,
            )
        }

        CaptureFlashOverlay(trigger = flashTrigger)

        if (state.isSaving) {
            ScannerSavingOverlay(completed = state.saveCompleted, total = state.saveTotal)
        }

        SnackbarHost(
            hostState = snacks,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 216.dp),
        )
    }

    if (showReview && state.pages.isNotEmpty()) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showReview = false },
            sheetState = sheetState,
        ) {
            ScanReviewContent(
                pages = state.pages,
                previews = state.previews,
                filter = state.filter,
                output = state.output,
                saving = state.isSaving,
                onRotate = vm::rotatePage,
                onMove = vm::movePage,
                onRemove = vm::removePage,
                onEdit = { pageId ->
                    reviewAfterCrop = true
                    showReview = false
                    cropPageId = pageId
                },
                onFilter = vm::setFilter,
                onOutput = vm::setOutput,
                onSave = {
                    showReview = false
                    vm.save { ids -> ids.firstOrNull()?.let { openViewer(controller, it) } }
                },
                onDiscard = {
                    showReview = false
                    vm.discard()
                },
            )
        }
    }
    val capturePage = captureReviewId?.let { id -> state.pages.firstOrNull { it.id == id } }
    if (capturePage != null) {
        ScanCaptureReview(
            page = capturePage,
            pageNumber = state.pages.indexOfFirst { it.id == capturePage.id } + 1,
            filter = capturePage.filter,
            onFilter = { filter -> vm.setPageFilter(capturePage.id, filter) },
            onRotate = { vm.rotatePage(capturePage.id) },
            onAdjustEdges = {
                reviewAfterCrop = false
                cropPageId = capturePage.id
            },
            onRetake = {
                vm.removePage(capturePage.id)
                captureReviewId = null
            },
            onSave = { captureReviewId = null },
        )
    }

    val cropPage = cropPageId?.let { id -> state.pages.firstOrNull { it.id == id } }
    if (cropPage != null) {
        ScanCropEditor(
            page = cropPage,
            onAutoDetect = { vm.detectPageCorners(cropPage.id) },
            onApply = { corners ->
                vm.setPageCorners(cropPage.id, corners)
                cropPageId = null
                if (reviewAfterCrop) {
                    reviewAfterCrop = false
                    showReview = true
                }
            },
            onDismiss = {
                cropPageId = null
                if (reviewAfterCrop) {
                    reviewAfterCrop = false
                    showReview = true
                }
            },
        )
    }
}

/** Opens the saved document in the viewer, replacing the scanner in the back stack. */
private fun openViewer(controller: NavHostController, documentId: String) {
    controller.navigate(Screen.viewerRoute(documentId)) {
        popUpTo(Screen.Scanner.route) { inclusive = true }
        launchSingleTop = true
    }
}

/** Camera preview plus the live document outline the detector is tracking. */
@Composable
private fun PreviewLayer(
    controller: CameraController,
    corners: QuadCorners?,
    confidence: Float,
    stability: Float,
    locked: Boolean,
    showGrid: Boolean,
    onSize: (androidx.compose.ui.unit.IntSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Color.Black)
            .onSizeChanged(onSize),
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    setController(controller)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        ScanDetectionOverlay(
            corners = corners,
            confidence = confidence,
            stability = stability,
            locked = locked,
            showGrid = showGrid,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * The capture HUD.
 *
 * Portrait puts the controls in a dock under the preview (thumb reach); landscape moves the same
 * controls into a side column so the preview can use the full height. Both branches share the same
 * components, so the two orientations cannot drift apart.
 */
@Composable
private fun ScannerBody(
    state: ScannerUiState,
    session: CameraSession,
    showGrid: Boolean,
    onToggleGrid: () -> Unit,
    onPickGallery: () -> Unit,
    onPreviewSize: (androidx.compose.ui.unit.IntSize) -> Unit,
    onClose: () -> Unit,
    onShoot: () -> Unit,
    onReview: () -> Unit,
    onSave: () -> Unit,
    onCycleFlash: () -> Unit,
    onTorch: (Boolean) -> Unit,
    onFlipCamera: () -> Unit,
    onMode: (ScanMode) -> Unit,
    onRotatePage: (Long) -> Unit,
    onRemovePage: (Long) -> Unit,
) {
    val spacing = LocalDocoraSpacing.current
    val gradients = LocalDocoraGradients.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val topChrome: @Composable (Modifier) -> Unit = { barModifier ->
            Column(
                modifier = barModifier.fillMaxWidth()
                    .background(gradients.scannerTopScrim)
                    .statusBarsPadding()
                    .padding(top = spacing.small, bottom = spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                ScanTopBar(
                    flashMode = state.flashMode,
                    torchOn = state.torchOn,
                    onBack = onClose,
                    onCycleFlash = onCycleFlash,
                    onToggleTorch = { onTorch(!state.torchOn) },
                    onFlipCamera = onFlipCamera,
                )
                ScanModeStrip(
                    modes = ScanMode.entries,
                    selected = state.mode,
                    onSelect = onMode,
                )
            }
        }
        val dock: @Composable (Modifier) -> Unit = { dockModifier ->
            ScannerDock(
                state = state,
                busy = session.isCapturing,
                showGrid = showGrid,
                onToggleGrid = onToggleGrid,
                onPickGallery = onPickGallery,
                onShoot = onShoot,
                onReview = onReview,
                onSave = onSave,
                onRotatePage = onRotatePage,
                onRemovePage = onRemovePage,
                modifier = dockModifier,
            )
        }
        val preview: @Composable (Modifier) -> Unit = { previewModifier ->
            PreviewLayer(
                controller = session.controller,
                corners = state.detectedCorners,
                confidence = state.detectionConfidence,
                stability = state.stability,
                locked = state.pageLooksReady,
                showGrid = showGrid,
                onSize = onPreviewSize,
                modifier = previewModifier,
            )
        }

        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    preview(Modifier.fillMaxSize())
                    topChrome(Modifier.align(Alignment.TopCenter))
                }
                dock(
                    Modifier
                        .width(232.dp)
                        .fillMaxHeight()
                        .background(Color.Black.copy(alpha = 0.72f))
                        .verticalScroll(rememberScrollState()),
                )
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                preview(Modifier.fillMaxSize())
                topChrome(Modifier.align(Alignment.TopCenter))
                dock(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .background(gradients.scannerBottomScrim),
                )
            }
        }
    }
}

/**
 * Capture controls, ordered by how close they are to the user's thumb.
 *
 * The shutter is the single primary control: every tap produces exactly one capture and opens the
 * review screen. Gallery import and the composition grid sit above it; the mode chips live in the
 * top chrome, and per-page filters live in the capture review, so nothing covers the shutter.
 */
@Composable
private fun ScannerDock(
    state: ScannerUiState,
    busy: Boolean,
    showGrid: Boolean,
    onToggleGrid: () -> Unit,
    onPickGallery: () -> Unit,
    onShoot: () -> Unit,
    onReview: () -> Unit,
    onSave: () -> Unit,
    onRotatePage: (Long) -> Unit,
    onRemovePage: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalDocoraSpacing.current
    val motion = LocalDocoraMotion.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(vertical = spacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        AnimatedVisibility(
            visible = state.pages.isNotEmpty(),
            enter = fadeIn(tween(motion.standard)) + expandVertically(motion.standardSpec()),
            exit = fadeOut(tween(motion.quick)) + shrinkVertically(motion.quickSpec()),
        ) {
            ScanPageStrip(
                pages = state.pages,
                previews = state.previews,
                onRotate = onRotatePage,
                onRemove = onRemovePage,
            )
        }
        ScanGuidancePill(
            progress = state.stability,
            text = scanGuidanceText(state),
            locked = state.pageLooksReady,
            modifier = Modifier.padding(horizontal = spacing.large),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScanGlassButton(
                contentDescription = stringResource(R.string.scan_gallery_import),
                onClick = onPickGallery,
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            ScanGlassButton(
                contentDescription = stringResource(R.string.scan_grid),
                onClick = onToggleGrid,
                active = showGrid,
            ) {
                Icon(
                    imageVector = Icons.Filled.GridOn,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.extraLarge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.pages.isNotEmpty(),
                    enter = fadeIn(tween(motion.quick)) + scaleIn(motion.quickSpec()),
                    exit = fadeOut(tween(motion.quick)) + scaleOut(motion.quickSpec()),
                ) {
                    FilledTonalButton(
                        onClick = onReview,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 10.dp,
                            vertical = 6.dp,
                        ),
                    ) {
                        Icon(
                            Icons.Filled.DoneAll,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.scan_review),
                            maxLines = 1,
                            softWrap = false,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            ScanCaptureButton(
                enabled = !state.isBusy && !busy,
                busy = busy || state.isBusy,
                ready = state.pageLooksReady,
                onClick = onShoot,
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.canSave,
                    enter = fadeIn(tween(motion.quick)) + scaleIn(motion.quickSpec()),
                    exit = fadeOut(tween(motion.quick)) + scaleOut(motion.quickSpec()),
                ) {
                    FilledTonalButton(
                        onClick = onSave,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 10.dp,
                            vertical = 6.dp,
                        ),
                    ) {
                        Icon(
                            Icons.Filled.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.scan_save),
                            maxLines = 1,
                            softWrap = false,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The one sentence that tells the user what the scanner currently wants from them.
 *
 * Derived from the live detector rather than tracked as its own state, so the hint can never
 * contradict the outline drawn over the preview. Captures are always manual.
 */
@Composable
private fun scanGuidanceText(state: ScannerUiState): String = when {
    state.pageLooksReady -> stringResource(R.string.scan_ready_hint)
    state.detectedCorners != null -> stringResource(R.string.scan_edge_detected)
    state.pageCount > 0 -> stringResource(R.string.scan_captured_count, state.pageCount)
    else -> stringResource(R.string.scan_edge_not_detected)
}

/**
 * Projects detector corners from the analysis frame into viewfinder space.
 *
 * The analysis stream covers the whole sensor, while the preview and the saved photo are cropped to
 * the screen's aspect around the same centre. A page that sits on the analysis frame's edge is
 * therefore outside the picture the shutter takes. Scaling and offsetting the quad here keeps the
 * outline, the crop and the saved page describing the same rectangle.
 */
private fun DocumentDetection.projected(analysisAspect: Float, viewAspect: Float): DocumentDetection {
    val quad = corners ?: return this
    if (analysisAspect <= 0f || viewAspect <= 0f) return this
    val scaleX: Float
    val scaleY: Float
    val offsetX: Float
    val offsetY: Float
    if (viewAspect < analysisAspect) {
        val visible = viewAspect / analysisAspect
        scaleX = 1f / visible
        scaleY = 1f
        offsetX = -(1f - visible) / 2f * scaleX
        offsetY = 0f
    } else {
        val visible = analysisAspect / viewAspect
        scaleX = 1f
        scaleY = 1f / visible
        offsetX = 0f
        offsetY = -(1f - visible) / 2f * scaleY
    }
    val projected = quad.toPointList().map { point ->
        Point2F(
            x = (point.x * scaleX + offsetX).coerceIn(0f, 1f),
            y = (point.y * scaleY + offsetY).coerceIn(0f, 1f),
        )
    }
    return copy(corners = QuadCorners.fromPoints(projected))
}
