package com.vedica.labs.ind.app.docora.ui.scanner

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.imaging.ScanImageProcessor
import com.vedica.labs.ind.app.docora.core.model.ScanFilter
import com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The screen shown immediately after the shutter fires.
 *
 * This is the CamScanner-style step: one capture, then a deliberate review with the crop/filter
 * controls, then either retake it or keep it as a page. The camera is not running behind this
 * screen, so a held page can never produce another capture. The next page starts only when the
 * user returns to the viewfinder and taps the shutter again.
 */
@Composable
fun ScanCaptureReview(
    page: ScanPageUi,
    pageNumber: Int,
    filter: ScanFilter,
    onFilter: (ScanFilter) -> Unit,
    onRotate: () -> Unit,
    onAdjustEdges: () -> Unit,
    onRetake: () -> Unit,
    onSave: () -> Unit,
) {
    val spacing = LocalDocoraSpacing.current
    var bitmap by remember(page.id) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(page.id) { mutableStateOf(true) }

    LaunchedEffect(page.id, filter, page.rotationDegrees, page.corners) {
        loading = true
        bitmap = withContext(Dispatchers.IO) {
            ScanImageProcessor.preview(
                file = page.file,
                maxLongEdge = REVIEW_LONG_EDGE,
                filter = filter,
                rotationDegrees = page.rotationDegrees,
                corners = page.corners,
            )
        }
        loading = false
    }

    BackHandler { onSave() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ReviewBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = spacing.large),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onSave) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = Color.White,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.scan_capture_review_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Text(
                        text = stringResource(R.string.scan_capture_review_subtitle, pageNumber),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = spacing.large, vertical = spacing.small),
                contentAlignment = Alignment.Center,
            ) {
                val image = bitmap
                when {
                    image == null && loading -> CircularProgressIndicator(color = Color.White)
                    image == null -> Text(
                        text = stringResource(R.string.scan_crop_load_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                    )
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val ratio = image.width.toFloat() / image.height.coerceAtLeast(1)
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White,
                            shadowElevation = 10.dp,
                            // Wrap the sheet at its own aspect instead of filling the box: a cropped
                            // ID card or receipt should look like the card it is, not like a page
                            // with white bars above and below it.
                            modifier = Modifier.aspectRatio(ratio),
                        ) {
                            Image(
                                bitmap = image.asImageBitmap(),
                                contentDescription = stringResource(R.string.scan_page_label, pageNumber),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(18.dp)),
                            )
                        }
                        // Keep the previous frame visible while a new filter renders, so moving
                        // along the strip feels immediate instead of flashing an empty card.
                        if (loading) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Black.copy(alpha = 0.55f),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.large),
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onAdjustEdges,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Crop,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.scan_crop_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onRotate,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.RotateRight,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.scan_rotate_page), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            ScanFilterStrip(
                filters = ScanFilter.entries,
                selected = filter,
                onSelect = onFilter,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = spacing.large,
                    vertical = spacing.small,
                ),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.large, vertical = spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onRetake) {
                    Text(
                        text = stringResource(R.string.scan_retake),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = onSave, enabled = !loading) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.scan_save_page))
                }
            }
        }
    }
}

private val ReviewBackground = Color(0xFF0C0F0D)
private const val REVIEW_LONG_EDGE = 1800
