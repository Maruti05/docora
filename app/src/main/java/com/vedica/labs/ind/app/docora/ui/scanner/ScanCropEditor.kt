package com.vedica.labs.ind.app.docora.ui.scanner

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.imaging.ScanImageMath
import com.vedica.labs.ind.app.docora.core.imaging.ScanImageProcessor
import com.vedica.labs.ind.app.docora.core.model.Point2F
import com.vedica.labs.ind.app.docora.core.model.QuadCorners
import com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * Manual crop / perspective editor for one captured page.
 *
 * The live detector is good, but a glossy receipt, a page on a dark desk or a curled book can leave
 * a corner a few percent off. This editor shows the full captured image with the detected quad and
 * four draggable handles, so the user can make every saved page exactly right. It works in the
 * page's displayed orientation and converts back to the unrotated crop space on Apply, which keeps
 * the page pipeline unchanged.
 */
@Composable
fun ScanCropEditor(
    page: ScanPageUi,
    onAutoDetect: suspend () -> QuadCorners?,
    onApply: (QuadCorners) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = LocalDocoraSpacing.current
    val scope = rememberCoroutineScope()
    val accent = MaterialTheme.colorScheme.primary
    val initial = remember(page.id, page.rotationDegrees) {
        ScanImageMath.rotateQuad(page.corners, page.rotationDegrees)
    }
    var bitmap by remember(page.id) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(page.id) { mutableStateOf(true) }
    var detecting by remember(page.id) { mutableStateOf(false) }
    var autoFailed by remember(page.id) { mutableStateOf(false) }
    var quad by remember(page.id, page.rotationDegrees) { mutableStateOf(initial) }
    val autoFailedText = stringResource(R.string.scan_crop_auto_failed)

    LaunchedEffect(page.id, page.rotationDegrees) {
        loading = true
        bitmap = withContext(Dispatchers.IO) {
            val base = ScanImageProcessor.decode(page.file, EDITOR_IMAGE_LONG_EDGE)
                ?: return@withContext null
            if (page.rotationDegrees == 0) {
                base
            } else {
                ScanImageProcessor.rotate(base, page.rotationDegrees)
            }
        }
        loading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                        tint = Color.White,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.scan_crop_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Text(
                        text = stringResource(R.string.scan_crop_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (detecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    TextButton(
                        onClick = {
                            scope.launch {
                                detecting = true
                                autoFailed = false
                                val detected = runCatching { onAutoDetect() }.getOrNull()
                                if (detected != null) quad = detected else autoFailed = true
                                detecting = false
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.scan_crop_auto))
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = spacing.large, vertical = spacing.medium),
                contentAlignment = Alignment.Center,
            ) {
                val image = bitmap
                when {
                    loading -> CircularProgressIndicator(color = Color.White)
                    image == null -> Text(
                        text = stringResource(R.string.scan_crop_load_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    else -> {
                        val aspect = image.width.toFloat() / image.height.toFloat()
                        val editorWidth = min(maxWidth.value, maxHeight.value * aspect).dp
                        Box(
                            modifier = Modifier
                                .width(editorWidth)
                                .aspectRatio(aspect),
                        ) {
                            Image(
                                bitmap = image.asImageBitmap(),
                                contentDescription = stringResource(R.string.scan_page_label, 1),
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier.fillMaxSize(),
                            )
                            CropCanvas(
                                quad = quad,
                                accent = accent,
                                onQuadChange = { quad = it },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }

            if (autoFailed) {
                Text(
                    text = autoFailedText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = spacing.large),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.large, vertical = spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                TextButton(
                    onClick = {
                        quad = initial
                        autoFailed = false
                    },
                    enabled = !detecting,
                ) {
                    Text(stringResource(R.string.action_reset), color = Color.White)
                }
                OutlinedButton(
                    onClick = {
                        quad = QuadCorners.fullFrame()
                        autoFailed = false
                    },
                    enabled = !detecting,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Crop,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.scan_crop_full_page))
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        val inverse = (360 - page.rotationDegrees) % 360
                        onApply(ScanImageMath.rotateQuad(quad, inverse))
                    },
                    enabled = !detecting,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_apply))
                }
            }
        }
    }
}

/** Image-space crop overlay: dims everything outside the quad and hosts the four handles. */
@Composable
private fun CropCanvas(
    quad: QuadCorners,
    accent: Color,
    onQuadChange: (QuadCorners) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val handleRadius = with(density) { 11.dp.toPx() }
    val touchRadius = with(density) { 32.dp.toPx() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var activeCorner by remember { mutableIntStateOf(-1) }
    val currentQuad by rememberUpdatedState(quad)
    val currentOnChange by rememberUpdatedState(onQuadChange)

    androidx.compose.foundation.Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { position ->
                        val width = canvasSize.width.toFloat()
                        val height = canvasSize.height.toFloat()
                        if (width <= 0f || height <= 0f) {
                            activeCorner = -1
                            return@detectDragGestures
                        }
                        var best = -1
                        var bestDistance = touchRadius * touchRadius
                        currentQuad.toPointList().forEachIndexed { index, point ->
                            val dx = point.x * width - position.x
                            val dy = point.y * height - position.y
                            val distance = dx * dx + dy * dy
                            if (distance <= bestDistance) {
                                bestDistance = distance
                                best = index
                            }
                        }
                        activeCorner = best
                    },
                    onDrag = { change, dragAmount ->
                        if (activeCorner < 0) return@detectDragGestures
                        val width = canvasSize.width.toFloat()
                        val height = canvasSize.height.toFloat()
                        if (width <= 0f || height <= 0f) return@detectDragGestures
                        change.consume()
                        currentOnChange(
                            currentQuad.movedCorner(
                                index = activeCorner,
                                dx = dragAmount.x / width,
                                dy = dragAmount.y / height,
                            ),
                        )
                    },
                    onDragEnd = { activeCorner = -1 },
                    onDragCancel = { activeCorner = -1 },
                )
            },
    ) {
        val width = size.width
        val height = size.height
        val points = quad.toPointList().map { Offset(it.x * width, it.y * height) }

        val outside = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, width, height))
            moveTo(points[0].x, points[0].y)
            for (index in 1 until points.size) lineTo(points[index].x, points[index].y)
            close()
        }
        drawPath(outside, color = Color.Black.copy(alpha = 0.55f))

        val border = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (index in 1 until points.size) lineTo(points[index].x, points[index].y)
            close()
        }
        drawPath(
            path = border,
            color = Color.White,
            style = Stroke(width = 2.dp.toPx()),
        )

        // Thirds inside the selection, the same guide a camera grid uses.
        for (step in 1..2) {
            val t = step / 3f
            val top = lerp(points[0], points[1], t)
            val bottom = lerp(points[3], points[2], t)
            val left = lerp(points[0], points[3], t)
            val right = lerp(points[1], points[2], t)
            drawLine(Color.White.copy(alpha = 0.35f), top, bottom, strokeWidth = 1.dp.toPx())
            drawLine(Color.White.copy(alpha = 0.35f), left, right, strokeWidth = 1.dp.toPx())
        }

        points.forEachIndexed { index, point ->
            val radius = if (index == activeCorner) handleRadius * 1.15f else handleRadius
            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = radius + 1.dp.toPx(),
                center = point,
            )
            drawCircle(color = Color.White, radius = radius, center = point)
            drawCircle(color = accent, radius = radius * 0.42f, center = point)
        }
    }
}

private fun lerp(from: Offset, to: Offset, t: Float): Offset =
    Offset(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)

/**
 * Moves one corner while keeping the four points in their clockwise order.
 *
 * Only adjacent corners are constrained: the top-left stays left of the top-right and above the
 * bottom-left, and so on. That is enough to prevent the edge from folding through itself and does
 * not force the quad to be axis-aligned, so a genuine trapezoid is still editable.
 */
private fun QuadCorners.movedCorner(index: Int, dx: Float, dy: Float): QuadCorners {
    val points = toPointList().toMutableList()
    val point = points[index]
    var x = (point.x + dx).coerceIn(0f, 1f)
    var y = (point.y + dy).coerceIn(0f, 1f)
    val gap = 0.03f

    when (index) {
        0 -> {
            x = x.coerceAtMost((points[1].x - gap).coerceAtLeast(0f))
            y = y.coerceAtMost((points[3].y - gap).coerceAtLeast(0f))
        }
        1 -> {
            x = x.coerceAtLeast((points[0].x + gap).coerceAtMost(1f))
            y = y.coerceAtMost((points[2].y - gap).coerceAtLeast(0f))
        }
        2 -> {
            x = x.coerceAtLeast((points[3].x + gap).coerceAtMost(1f))
            y = y.coerceAtLeast((points[1].y + gap).coerceAtMost(1f))
        }
        3 -> {
            x = x.coerceAtMost((points[2].x - gap).coerceAtLeast(0f))
            y = y.coerceAtLeast((points[0].y + gap).coerceAtMost(1f))
        }
    }
    points[index] = Point2F(x, y)
    return QuadCorners.fromPoints(points)
}

private val EditorBackground = Color(0xFF0C0F0D)

private const val EDITOR_IMAGE_LONG_EDGE = 1600
