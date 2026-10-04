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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
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
import com.vedica.labs.ind.app.docora.core.imaging.AutoCaptureDecider
import com.vedica.labs.ind.app.docora.core.model.ScanFilter
import com.vedica.labs.ind.app.docora.core.model.ScanMode
import com.vedica.labs.ind.app.docora.core.model.ScannerQuality
import com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraGradients
import com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraMotion
import com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraSpacing
import com.vedica.labs.ind.app.docora.ui.scanner.CaptureFlashOverlay
import com.vedica.labs.ind.app.docora.ui.scanner.ScanCaptureButton
import com.vedica.labs.ind.app.docora.ui.scanner.ScanFilterStrip
import com.vedica.labs.ind.app.docora.ui.scanner.ScanGuideFrame
import com.vedica.labs.ind.app.docora.ui.scanner.ScanModeStrip
import com.vedica.labs.ind.app.docora.ui.scanner.ScanPageStrip
import com.vedica.labs.ind.app.docora.ui.scanner.ScanReadinessPill
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
    private val decider = AutoCaptureDecider()

    var isCapturing by mutableStateOf(false)
        private set

    /** Binds (or rebinds) the camera for the current capture settings. */
    fun bind(
        owner: LifecycleOwner,
        config: CameraSessionConfig,
        onVerdict: (AutoCaptureDecider.Verdict) -> Unit,
    ) {
        decider.reset()
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
        controller.setEnabledUseCases(
            CameraController.IMAGE_CAPTURE or
                if (config.autoCapture) CameraController.IMAGE_ANALYSIS else 0,
        )
        if (config.autoCapture) {
            controller.setImageAnalysisTargetSize(
                CameraController.OutputSize(Size(GRID_EDGE * 10, GRID_EDGE * 8)),
            )
            controller.setImageAnalysisImageQueueDepth(1)
            controller.setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            controller.setImageAnalysisAnalyzer(analysisExecutor) { proxy ->
                analyzeFrame(proxy, onVerdict)
            }
        } else {
            controller.clearImageAnalysisAnalyzer()
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
     * Reads a coarse luminance grid out of the camera frame.
     *
     * Only the Y plane is touched and only a few hundred samples are taken, so analysis costs far less
     * than the preview: that is what keeps auto capture from competing with the frames the user sees.
     */
    private fun analyzeFrame(proxy: ImageProxy, onVerdict: (AutoCaptureDecider.Verdict) -> Unit) {
        try {
            val plane = proxy.planes.firstOrNull()
            if (plane == null || proxy.width <= 0 || proxy.height <= 0) return
            val buffer = plane.buffer
            val grid = IntArray(GRID_EDGE * GRID_EDGE)
            for (row in 0 until GRID_EDGE) {
                val y = (row * proxy.height) / GRID_EDGE
                for (column in 0 until GRID_EDGE) {
                    val x = (column * proxy.width) / GRID_EDGE
                    val index = y * plane.rowStride + x * plane.pixelStride
                    grid[row * GRID_EDGE + column] = if (index < buffer.limit()) {
                        buffer.get(index).toInt() and 0xFF
                    } else {
                        0
                    }
                }
            }
            onVerdict(decider.submit(grid))
        } finally {
            proxy.close()
        }
    }

    fun release() {
        runCatching { controller.unbind() }
        captureExecutor.shutdown()
        analysisExecutor.shutdown()
    }

    /** Resolution matching the configured quality, in portrait orientation. */
    private fun targetSizeFor(quality: ScannerQuality): Size {
        val longEdge = quality.targetLongEdgePx
        return Size((longEdge * 3) / 4, longEdge)
    }

    private companion object {
        /** Side of the luminance grid fed to the auto-capture heuristic. */
        const val GRID_EDGE = 32
    }
}

/** The subset of settings a camera binding depends on, so rebinding only happens when one changes. */
private data class CameraSessionConfig(
    val useFrontCamera: Boolean,
    val quality: ScannerQuality,
    val autoCapture: Boolean,
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val snacks = remember { SnackbarHostState() }

    var hasPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    var flashTrigger by remember { mutableStateOf(0) }
    var showReview by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (!granted) vm.onPermissionDenied()
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val session = remember { CameraSession(context) }
    DisposableEffect(session) {
        onDispose { session.release() }
    }

    // Rebind only when something the camera actually cares about changes.
    val sessionConfig = CameraSessionConfig(
        useFrontCamera = state.useFrontCamera,
        quality = state.quality,
        autoCapture = state.autoCapture,
    )
    LaunchedEffect(hasPermission, sessionConfig) {
        if (hasPermission) {
            session.bind(lifecycleOwner, sessionConfig, vm::onAutoCaptureVerdict)
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
            onCaptured = vm::onPageCaptured,
            onFailed = vm::onCaptureFailed,
        )
    }

    // Auto capture: the analysis loop decides, the screen only obeys.
    LaunchedEffect(session) {
        vm.captureRequests.collect { shoot() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            ScannerBody(
                state = state,
                session = session,
                onClose = { controller.popBackStack() },
                onShoot = { shoot() },
                onReview = { showReview = true },
                onSave = { vm.save { ids -> ids.firstOrNull()?.let { openViewer(controller, it) } } },
                onCycleFlash = vm::cycleFlashMode,
                onTorch = vm::setTorch,
                onAutoCapture = vm::setAutoCapture,
                onFlipCamera = vm::flipCamera,
                onMode = vm::setMode,
                onFilter = vm::setFilter,
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
}

/** Opens the saved document in the viewer, replacing the scanner in the back stack. */
private fun openViewer(controller: NavHostController, documentId: String) {
    controller.navigate(Screen.viewerRoute(documentId)) {
        popUpTo(Screen.Scanner.route) { inclusive = true }
        launchSingleTop = true
    }
}

/** Camera preview plus the page guidance frame. */
@Composable
private fun PreviewLayer(
    controller: CameraController,
    ready: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(Color.Black)) {
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
        ScanGuideFrame(
            locked = ready,
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .aspectRatio(0.72f)
                .align(Alignment.Center),
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
    onClose: () -> Unit,
    onShoot: () -> Unit,
    onReview: () -> Unit,
    onSave: () -> Unit,
    onCycleFlash: () -> Unit,
    onTorch: (Boolean) -> Unit,
    onAutoCapture: (Boolean) -> Unit,
    onFlipCamera: () -> Unit,
    onMode: (ScanMode) -> Unit,
    onFilter: (ScanFilter) -> Unit,
    onRotatePage: (Long) -> Unit,
    onRemovePage: (Long) -> Unit,
) {
    val spacing = LocalDocoraSpacing.current
    val gradients = LocalDocoraGradients.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val topBar: @Composable (Modifier) -> Unit = { barModifier ->
            ScanTopBar(
                flashMode = state.flashMode,
                torchOn = state.torchOn,
                autoCapture = state.autoCapture,
                onBack = onClose,
                onCycleFlash = onCycleFlash,
                onToggleTorch = { onTorch(!state.torchOn) },
                onToggleAutoCapture = { onAutoCapture(!state.autoCapture) },
                onFlipCamera = onFlipCamera,
                modifier = barModifier,
            )
        }
        val dock: @Composable (Modifier) -> Unit = { dockModifier ->
            ScannerDock(
                state = state,
                busy = session.isCapturing,
                onShoot = onShoot,
                onReview = onReview,
                onSave = onSave,
                onMode = onMode,
                onFilter = onFilter,
                onRotatePage = onRotatePage,
                onRemovePage = onRemovePage,
                modifier = dockModifier,
            )
        }
        val barChrome = Modifier
            .background(gradients.scannerTopScrim)
            .statusBarsPadding()
            .padding(vertical = spacing.small)

        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    PreviewLayer(
                        controller = session.controller,
                        ready = state.pageLooksReady,
                        modifier = Modifier.fillMaxSize(),
                    )
                    topBar(Modifier.align(Alignment.TopCenter).then(barChrome))
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
                PreviewLayer(
                    controller = session.controller,
                    ready = state.pageLooksReady,
                    modifier = Modifier.fillMaxSize(),
                )
                topBar(Modifier.align(Alignment.TopCenter).then(barChrome))
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
 * Capture controls: readiness, the page strip, the filter strip, the mode strip and the shutter.
 *
 * Ordering matters - the things the user looks at most often (readiness, pages, shutter) are nearest
 * the thumb, and the two scrolling strips sit above them.
 */
@Composable
private fun ScannerDock(
    state: ScannerUiState,
    busy: Boolean,
    onShoot: () -> Unit,
    onReview: () -> Unit,
    onSave: () -> Unit,
    onMode: (ScanMode) -> Unit,
    onFilter: (ScanFilter) -> Unit,
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
        ScanReadinessPill(
            ready = state.pageLooksReady,
            autoCapture = state.autoCapture,
            pageCount = state.pageCount,
            modifier = Modifier.padding(horizontal = spacing.large),
        )
        ScanFilterStrip(
            filters = ScanFilter.entries,
            selected = state.filter,
            onSelect = onFilter,
        )
        ScanModeStrip(
            modes = ScanMode.entries,
            selected = state.mode,
            onSelect = onMode,
        )
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
                    FilledTonalButton(onClick = onReview) {
                        Icon(
                            Icons.Filled.DoneAll,
                            contentDescription = null,
                            modifier = Modifier.width(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.scan_review))
                    }
                }
            }
            ScanCaptureButton(
                enabled = !state.isBusy && !busy,
                busy = busy || state.isBusy,
                ready = state.pageLooksReady && state.autoCapture,
                onClick = onShoot,
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.canSave,
                    enter = fadeIn(tween(motion.quick)) + scaleIn(motion.quickSpec()),
                    exit = fadeOut(tween(motion.quick)) + scaleOut(motion.quickSpec()),
                ) {
                    FilledTonalButton(onClick = onSave) {
                        Icon(
                            Icons.Filled.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.width(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.scan_save))
                    }
                }
            }
        }
    }
}
