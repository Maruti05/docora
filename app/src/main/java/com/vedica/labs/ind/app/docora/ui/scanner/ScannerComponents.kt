package com.vedica.labs.ind.app.docora.ui.scanner

import android.graphics.Bitmap
import com.vedica.labs.ind.app.docora.core.model.ScanOutput

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawWithContent
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.model.FlashMode
import com.vedica.labs.ind.app.docora.core.model.QuadCorners
import com.vedica.labs.ind.app.docora.core.model.ScanFilter
import com.vedica.labs.ind.app.docora.core.model.ScanMode
import com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraExtendedColors
import com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraMotion
import com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraSpacing

/** Label for a capture mode, resolved from resources so the strip stays translatable. */
fun scanModeLabelRes(mode: ScanMode): Int = when (mode) {
    ScanMode.AUTO -> R.string.scan_mode_auto
    ScanMode.DOCUMENT -> R.string.scan_mode_document
    ScanMode.ID_CARD -> R.string.scan_mode_id
    ScanMode.RECEIPT -> R.string.scan_mode_receipt
    ScanMode.WHITEBOARD -> R.string.scan_mode_whiteboard
    ScanMode.PHOTO -> R.string.scan_mode_photo
}

/** Label for an enhancement filter. */
fun scanFilterLabelRes(filter: ScanFilter): Int = when (filter) {
    ScanFilter.ORIGINAL -> R.string.scan_filter_original
    ScanFilter.ENHANCE -> R.string.scan_filter_enhance
    ScanFilter.GRAYSCALE -> R.string.scan_filter_grayscale
    ScanFilter.BLACK_AND_WHITE -> R.string.scan_filter_black_white
    ScanFilter.MAGIC_COLOUR -> R.string.scan_filter_magic
}

/** Glyph that stands for a filter's effect, used on the filter strip. */
fun scanFilterIcon(filter: ScanFilter): androidx.compose.ui.graphics.vector.ImageVector = when (filter) {
    ScanFilter.ORIGINAL -> Icons.Filled.Image
    ScanFilter.ENHANCE -> Icons.Filled.AutoAwesome
    ScanFilter.GRAYSCALE -> Icons.Filled.InvertColors
    ScanFilter.BLACK_AND_WHITE -> Icons.Filled.Contrast
    ScanFilter.MAGIC_COLOUR -> Icons.Filled.Palette
}

/** Label for the flash state; also used as the button's accessibility description. */
fun flashLabelRes(mode: FlashMode): Int = when (mode) {
    FlashMode.AUTO -> R.string.scan_flash_auto
    FlashMode.ON -> R.string.scan_flash_on
    FlashMode.OFF -> R.string.scan_flash_off
}

/**
 * Capture mode selector.
 *
 * Modes are not cosmetic: each one selects the filter and contrast bias that suits its subject
 * (a receipt is high contrast text, an ID card needs saturated colour), so switching mode is the
 * single most useful control on this screen.
 */
@Composable
fun ScanModeStrip(
    modes: List<ScanMode>,
    selected: ScanMode,
    onSelect: (ScanMode) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(LocalDocoraSpacing.current.small),
    ) {
        items(modes, key = { it.name }) { mode ->
            val isSelected = mode == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(mode) },
                label = { Text(stringResource(scanModeLabelRes(mode))) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Black.copy(alpha = 0.38f),
                    labelColor = Color.White,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

/**
 * Enhancement filter strip.
 *
 * Rendered as labelled swatches rather than a dropdown so the current treatment is always visible
 * while framing the page, and so the strip can be scrolled with a thumb without leaving the preview.
 */
@Composable
fun ScanFilterStrip(
    filters: List<ScanFilter>,
    selected: ScanFilter,
    onSelect: (ScanFilter) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    val motion = LocalDocoraMotion.current
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(LocalDocoraSpacing.current.medium),
    ) {
        items(filters, key = { it.name }) { filter ->
            val isSelected = filter == selected
            val container by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Black.copy(alpha = 0.38f)
                },
                animationSpec = tween(motion.quick),
                label = "filter-container",
            )
            val tint by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    Color.White
                },
                animationSpec = tween(motion.quick),
                label = "filter-tint",
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClickLabel = stringResource(scanFilterLabelRes(filter))) {
                        onSelect(filter)
                    }
                    .padding(vertical = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(container),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = scanFilterIcon(filter),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = stringResource(scanFilterLabelRes(filter)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Shutter button.
 *
 * The ring pulses only while the frame is a steady document, so the button itself communicates that
 * the phone is ready to shoot. It is the largest control on the screen (84 dp) because it is the one
 * that has to work with a thumb, one handed, without looking at it.
 */
@Composable
fun ScanCaptureButton(
    enabled: Boolean,
    busy: Boolean,
    ready: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalDocoraMotion.current
    val extended = LocalDocoraExtendedColors.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = motion.selectionSpring(),
        label = "shutter-scale",
    )
    val pulseTransition = rememberInfiniteTransition(label = "shutter-pulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = motion.ambient / 2, easing = motion.standardEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shutter-pulse-alpha",
    )
    val ringColor by animateColorAsState(
        targetValue = if (ready) extended.guideLocked else Color.White,
        animationSpec = tween(motion.standard),
        label = "shutter-ring",
    )
    Box(
        modifier = modifier
            .size(84.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.28f))
            .border(4.dp, ringColor.copy(alpha = if (ready) pulse else 1f), CircleShape)
            .clickable(
                interactionSource = interactions,
                indication = null,
                enabled = enabled,
                onClickLabel = stringResource(R.string.scan_capture),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(34.dp),
                color = Color.White,
                strokeWidth = 3.dp,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(if (enabled) Color.White else Color.White.copy(alpha = 0.5f)),
            )
        }
    }
}

/**
 * Captured page strip.
 *
 * Tapping a page rotates it, which is the correction people need most often; the delete badge is the
 * only destructive control and it sits on the page it removes. A long strip is fine here: it scrolls,
 * and it is naturally bounded by how many pages someone captures in one session.
 */
@Composable
fun ScanPageStrip(
    pages: List<ScanPageUi>,
    previews: Map<Long, Bitmap>,
    onRotate: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(LocalDocoraSpacing.current.small),
    ) {
        itemsIndexed(pages, key = { _, page -> page.id }) { index, page ->
            Column(
                modifier = Modifier.animateItem(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 58.dp, height = 74.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .clickable(onClickLabel = stringResource(R.string.scan_rotate_page)) {
                            onRotate(page.id)
                        },
                ) {
                    val preview = previews[page.id]
                    if (preview != null && !preview.isRecycled) {
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = stringResource(R.string.scan_page_label, index + 1),
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.Center),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable(onClickLabel = stringResource(R.string.scan_delete_page)) {
                                onRemove(page.id)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.scan_page_label, index + 1),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Shutter flash.
 *
 * A camera that gives no feedback between "I pressed it" and "the page appeared" feels broken, and
 * the capture latency on a phone is exactly in the range where that is noticeable. The flash is
 * driven by a counter so repeated captures each get their own animation.
 */
@Composable
fun CaptureFlashOverlay(
    trigger: Int,
    modifier: Modifier = Modifier,
) {
    val alpha = remember { Animatable(0f) }
    val motion = LocalDocoraMotion.current
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        alpha.snapTo(0.8f)
        alpha.animateTo(0f, tween(motion.standard, easing = motion.standardEasing))
    }
    if (alpha.value > 0.01f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = alpha.value)),
        )
    }
}

/**
 * Camera permission gate.
 *
 * Shown instead of the preview, with the rationale inline: the camera is the only permission the
 * scanner needs, and a user who has refused it once is offered the direct route to system settings
 * rather than a button that will silently do nothing.
 */
@Composable
fun ScannerPermissionGate(
    denied: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val spacing = LocalDocoraSpacing.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(spacing.extraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(scheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.CameraAlt,
                    contentDescription = null,
                    tint = scheme.onPrimaryContainer,
                    modifier = Modifier.size(34.dp),
                )
            }
            Text(
                text = stringResource(R.string.permission_camera_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(
                    if (denied) R.string.scan_permission_denied else R.string.scan_permission_rationale,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (denied) {
                Text(
                    text = stringResource(R.string.scan_open_settings_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Button(onClick = if (denied) onOpenSettings else onAllow) {
                Text(
                    stringResource(
                        if (denied) R.string.action_open_settings else R.string.action_allow,
                    ),
                )
            }
        }
    }
}

/**
 * Blocking progress while a scan is written.
 *
 * Saving runs off the main thread and can take a second per page at maximum quality, so the overlay
 * states exactly which page is being written and cannot be dismissed by accident - dismissing it
 * would leave the library in an unknown state.
 */
@Composable
fun ScannerSavingOverlay(
    completed: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val spacing = LocalDocoraSpacing.current
    val fraction = if (total <= 0) 0f else (completed.toFloat() / total).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
            modifier = Modifier.padding(spacing.extraLarge),
        ) {
            Column(
                modifier = Modifier.padding(spacing.extraLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.medium),
            ) {
                CircularProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.size(52.dp),
                    color = scheme.primary,
                )
                Text(
                    text = stringResource(R.string.scan_saving),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.scan_progress_saving, completed + 1, total),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                )
            }
        }
    }
}

/**
 * Review panel: reorder, rotate, delete and choose the output format before saving.
 *
 * The capture screen is intentionally minimal, so everything that needs a decision (page order,
 * output format, discarding the session) lives in this one sheet. It is the last place a mistake can
 * be undone before the scan becomes a document in the library.
 */
@Composable
fun ScanReviewContent(
    pages: List<ScanPageUi>,
    previews: Map<Long, Bitmap>,
    filter: ScanFilter,
    output: ScanOutput,
    saving: Boolean,
    onRotate: (Long) -> Unit,
    onMove: (Long, Int) -> Unit,
    onRemove: (Long) -> Unit,
    onFilter: (ScanFilter) -> Unit,
    onOutput: (ScanOutput) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val spacing = LocalDocoraSpacing.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal)
            .padding(bottom = spacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Text(stringResource(R.string.scan_review_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.scan_review_hint),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        ScanFilterStrip(
            filters = ScanFilter.entries,
            selected = filter,
            onSelect = onFilter,
            contentPadding = PaddingValues(horizontal = 0.dp),
        )
        Text(
            stringResource(R.string.scan_output_label),
            style = MaterialTheme.typography.labelLarge,
            color = scheme.primary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            OutputChip(
                label = stringResource(R.string.action_save_pdf),
                selected = output == ScanOutput.PDF,
                onClick = { onOutput(ScanOutput.PDF) },
            )
            OutputChip(
                label = stringResource(R.string.action_save_image),
                selected = output == ScanOutput.IMAGES,
                onClick = { onOutput(ScanOutput.IMAGES) },
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            pages.forEachIndexed { index, page ->
                ReviewPageRow(
                    index = index,
                    page = page,
                    preview = previews[page.id],
                    lastIndex = pages.lastIndex,
                    onRotate = onRotate,
                    onMove = onMove,
                    onRemove = onRemove,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDiscard, enabled = !saving) {
                Text(text = stringResource(R.string.scan_discard), color = scheme.error)
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onSave, enabled = pages.isNotEmpty() && !saving) {
                Icon(
                    imageVector = if (output == ScanOutput.PDF) {
                        Icons.Filled.PictureAsPdf
                    } else {
                        Icons.Filled.Image
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (output == ScanOutput.PDF) {
                            R.string.action_save_pdf
                        } else {
                            R.string.action_save_image
                        },
                    ),
                )
            }
        }
    }
}

/** Output format chip used inside the review panel. */
@Composable
private fun OutputChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

/** One page in the review panel, with every per-page correction next to it. */
@Composable
private fun ReviewPageRow(
    index: Int,
    page: ScanPageUi,
    preview: Bitmap?,
    lastIndex: Int,
    onRotate: (Long) -> Unit,
    onMove: (Long, Int) -> Unit,
    onRemove: (Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val spacing = LocalDocoraSpacing.current
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = scheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(scheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (preview != null && !preview.isRecycled) {
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Filled.Image,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.width(spacing.medium))
            Text(
                text = stringResource(R.string.scan_page_label, index + 1),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onMove(page.id, -1) }, enabled = index > 0) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.scan_move_earlier),
                )
            }
            IconButton(onClick = { onMove(page.id, 1) }, enabled = index < lastIndex) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.scan_move_later),
                )
            }
            IconButton(onClick = { onRotate(page.id) }) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.scan_rotate_page),
                )
            }
            IconButton(onClick = { onRemove(page.id) }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.scan_delete_page),
                    tint = scheme.error,
                )
            }
        }
    }
}

/**
 * Translucent circular control used across the camera chrome.
 *
 * Controls sit on top of an unpredictable background (a white page, a dark desk), so they are drawn
 * as dark translucent discs with light glyphs: that keeps contrast usable over both extremes without
 * a solid bar that would eat the preview.
 */
@Composable
fun ScanGlassButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    icon: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = LocalDocoraMotion.current
    val container by animateColorAsState(
        targetValue = if (active) activeColor else Color.Black.copy(alpha = 0.42f),
        animationSpec = tween(motion.quick),
        label = "glass-container",
    )
    val content by animateColorAsState(
        targetValue = if (active) scheme.onPrimary else Color.White,
        animationSpec = tween(motion.quick),
        label = "glass-content",
    )
    val scale by animateFloatAsState(
        targetValue = if (active) 1.04f else 1f,
        animationSpec = motion.selectionSpring(),
        label = "glass-scale",
    )
    Box(
        modifier = modifier
            .size(44.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(container)
            .clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides content,
        ) {
            icon()
        }
    }
}

/**
 * Camera chrome: close, flash, torch, auto capture and camera switch.
 *
 * Every control is a single tap with a visible state, because the user is holding the phone in one
 * hand and looking at a page, not at the screen.
 */
@Composable
fun ScanTopBar(
    flashMode: FlashMode,
    torchOn: Boolean,
    autoCapture: Boolean,
    onBack: () -> Unit,
    onCycleFlash: () -> Unit,
    onToggleTorch: () -> Unit,
    onToggleAutoCapture: () -> Unit,
    onFlipCamera: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LocalDocoraSpacing.current.small),
    ) {
        ScanGlassButton(
            contentDescription = stringResource(R.string.action_back),
            onClick = onBack,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.weight(1f))
        ScanGlassButton(
            contentDescription = stringResource(flashLabelRes(flashMode)),
            onClick = onCycleFlash,
            active = flashMode != FlashMode.OFF,
        ) {
            Icon(
                imageVector = when (flashMode) {
                    FlashMode.AUTO -> Icons.Filled.FlashAuto
                    FlashMode.ON -> Icons.Filled.FlashOn
                    FlashMode.OFF -> Icons.Filled.FlashOff
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
        ScanGlassButton(
            contentDescription = stringResource(R.string.scan_torch),
            onClick = onToggleTorch,
            active = torchOn,
        ) {
            Icon(Icons.Filled.Highlight, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        ScanGlassButton(
            contentDescription = stringResource(R.string.scan_auto_capture),
            onClick = onToggleAutoCapture,
            active = autoCapture,
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        ScanGlassButton(
            contentDescription = stringResource(R.string.scan_flip_camera),
            onClick = onFlipCamera,
        ) {
            Icon(Icons.Filled.Cameraswitch, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * The live document outline drawn over the preview.
 *
 * When the detector has found a page the overlay shows what it actually found: everything outside
 * the quad is dimmed, the quad is stroked, its corners get handles, and a bright segment sweeps the
 * perimeter as the frame steadies - the same "about to snap" affordance the platform scanners use,
 * so the auto-capture countdown is visible rather than a mystery.
 *
 * When nothing is found the overlay falls back to four corner brackets in the middle of the frame,
 * which is both a target to aim at and an honest statement that the app is still looking.
 *
 * Everything is drawn in one [Canvas]: the scrim, the stroke, the handles, the progress and the
 * optional rule-of-thirds grid are all paths on a single layer, which is what keeps the overlay from
 * costing a recomposition per frame.
 */
@Composable
fun ScanDetectionOverlay(
    corners: QuadCorners?,
    confidence: Float,
    stability: Float,
    locked: Boolean,
    showGrid: Boolean,
    modifier: Modifier = Modifier,
) {
    val extended = LocalDocoraExtendedColors.current
    val motion = LocalDocoraMotion.current
    val outline by animateColorAsState(
        targetValue = if (locked) extended.guideLocked else Color.White,
        animationSpec = tween(motion.standard),
        label = "detection-outline",
    )
    val scrim by animateColorAsState(
        targetValue = Color.Black.copy(alpha = 0.52f),
        animationSpec = tween(motion.standard),
        label = "detection-scrim",
    )
    val points = corners?.toPointList()
    // Fade the whole overlay with the detector's confidence, so a page leaving the frame dissolves
    // instead of the outline blinking out of existence.
    val weight = confidence.coerceIn(0f, 1f)
    val appear = 0.3f + 0.7f * weight

    Canvas(modifier = modifier) {
        if (showGrid) {
            val line = Color.White.copy(alpha = 0.18f)
            val stroke = 1.dp.toPx()
            for (step in 1..2) {
                val x = size.width * step / 3f
                val y = size.height * step / 3f
                drawLine(line, Offset(x, 0f), Offset(x, size.height), stroke)
                drawLine(line, Offset(0f, y), Offset(size.width, y), stroke)
            }
        }
        if (points == null) {
            drawGuideBrackets(outline.copy(alpha = 0.75f))
            return@Canvas
        }
        val canvasPoints = points.map { Offset(it.x * size.width, it.y * size.height) }
        val quad = Path().apply {
            moveTo(canvasPoints[0].x, canvasPoints[0].y)
            for (index in 1 until canvasPoints.size) lineTo(canvasPoints[index].x, canvasPoints[index].y)
            close()
        }

        // Dim everything that is not the page: an even-odd fill of frame-minus-quad.
        val outside = Path().apply {
            addRect(Rect(Offset.Zero, Size(size.width, size.height)))
            moveTo(canvasPoints[0].x, canvasPoints[0].y)
            for (index in 1 until canvasPoints.size) lineTo(canvasPoints[index].x, canvasPoints[index].y)
            close()
            fillType = PathFillType.EvenOdd
        }
        drawPath(outside, scrim.copy(alpha = scrim.alpha * appear))

        drawPath(
            path = quad,
            color = outline.copy(alpha = appear),
            style = Stroke(width = 2.5.dp.toPx(), join = StrokeJoin.Round),
        )

        // Auto-snap progress: the perimeter lights up proportionally to how steady the frame is.
        if (stability > 0.02f) {
            drawPerimeterProgress(canvasPoints, stability, extended.guideLocked.copy(alpha = appear))
        }

        val handleRadius = 5.dp.toPx()
        canvasPoints.forEach { point ->
            drawCircle(Color.Black.copy(alpha = 0.45f * appear), radius = handleRadius * 1.7f, center = point)
            drawCircle(outline.copy(alpha = appear), radius = handleRadius, center = point)
        }
    }
}

/**
 * Four corner brackets, drawn inside a centred page-shaped target before a document is found.
 *
 * The brackets mark where to aim rather than following the screen edge: an outline pinned to the
 * corners of the preview would suggest the whole frame is already the page.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGuideBrackets(accent: Color) {
    val targetWidth = size.width * 0.86f
    val targetHeight = minOf(size.height * 0.8f, targetWidth / 0.72f)
    val left = (size.width - targetWidth) / 2f
    val top = (size.height - targetHeight) / 2f
    val right = left + targetWidth
    val bottom = top + targetHeight
    val arm = minOf(targetWidth, targetHeight) * 0.16f
    val stroke = 5f

    // Top-left
    drawLine(accent, Offset(left, top + arm), Offset(left, top), stroke, StrokeCap.Round)
    drawLine(accent, Offset(left, top), Offset(left + arm, top), stroke, StrokeCap.Round)
    // Top-right
    drawLine(accent, Offset(right - arm, top), Offset(right, top), stroke, StrokeCap.Round)
    drawLine(accent, Offset(right, top), Offset(right, top + arm), stroke, StrokeCap.Round)
    // Bottom-right
    drawLine(accent, Offset(right, bottom - arm), Offset(right, bottom), stroke, StrokeCap.Round)
    drawLine(accent, Offset(right, bottom), Offset(right - arm, bottom), stroke, StrokeCap.Round)
    // Bottom-left
    drawLine(accent, Offset(left + arm, bottom), Offset(left, bottom), stroke, StrokeCap.Round)
    drawLine(accent, Offset(left, bottom), Offset(left, bottom - arm), stroke, StrokeCap.Round)
}

/** Paints the first [fraction] of the quad's perimeter, walking clockwise from the top-left. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPerimeterProgress(
    points: List<Offset>,
    fraction: Float,
    color: Color,
) {
    var remaining = points.indices.sumOf { index ->
        val from = points[index]
        val to = points[(index + 1) % points.size]
        kotlin.math.hypot((to.x - from.x).toDouble(), (to.y - from.y).toDouble())
    } * fraction.coerceIn(0f, 1f).toDouble()
    val stroke = 5.dp.toPx()
    for (index in points.indices) {
        if (remaining <= 0.0) return
        val from = points[index]
        val to = points[(index + 1) % points.size]
        val length = kotlin.math.hypot((to.x - from.x).toDouble(), (to.y - from.y).toDouble())
        if (length <= 0.0) continue
        val share = (remaining / length).coerceAtMost(1.0).toFloat()
        drawLine(
            color = color,
            start = from,
            end = Offset(from.x + (to.x - from.x) * share, from.y + (to.y - from.y) * share),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        remaining -= length
    }
}

/**
 * Status line above the capture controls.
 *
 * Carries the guidance sentence the detector implies - aiming, holding steady, or about to snap -
 * next to a small progress ring, so the user never has to look anywhere else to know what the app
 * expects of them or whether the shot will be taken for them.
 */
@Composable
fun ScanGuidancePill(
    progress: Float,
    text: String,
    locked: Boolean,
    modifier: Modifier = Modifier,
) {
    val extended = LocalDocoraExtendedColors.current
    val accent by animateColorAsState(
        targetValue = if (locked) extended.guideLocked else Color.White,
        animationSpec = tween(LocalDocoraMotion.current.standard),
        label = "guidance-accent",
    )
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.46f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LocalDocoraSpacing.current.small),
        ) {
            Box(
                modifier = Modifier.size(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.size(16.dp),
                    color = accent,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    strokeWidth = 2.dp,
                )
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
