package com.vedica.labs.ind.app.docora.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vedica.labs.ind.app.docora.core.model.DocumentType
import com.vedica.labs.ind.app.docora.ui.designsystem.ThumbnailShape
import com.vedica.labs.ind.app.docora.ui.home.typeBackground
import com.vedica.labs.ind.app.docora.ui.home.typeIcon

/**
 * Renders a document preview thumbnail, falling back to the file-type glyph.
 *
 * Previews go through the handler layer (PRD §46): images, PDFs and text documents produce a
 * real bitmap; anything else - or any render failure - shows the coloured type icon, so a
 * broken thumbnail never breaks the row.
 */
@Composable
fun DocumentThumbnail(
    uri: String,
    type: DocumentType,
    renderPreview: suspend (String, Int) -> Bitmap?,
    targetPx: Int,
    modifier: Modifier = Modifier,
    size: Dp? = null,
) {
    var bitmap by remember(uri, targetPx) { mutableStateOf<Bitmap?>(null) }
    val fallback = typeBackground(type)
    val boxModifier = if (size != null) modifier.size(size) else modifier
    Box(
        modifier = boxModifier
            .clip(ThumbnailShape)
            .background(fallback),
        contentAlignment = Alignment.Center,
    ) {
        LaunchedEffect(uri, targetPx) {
            if (bitmap == null) bitmap = renderPreview(uri, targetPx)
        }
        val preview = bitmap
        if (preview != null) {
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = typeIcon(type),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

/** Section header used by the documents browser ("On this device", "In Docora"). */
@Composable
fun DocumentSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * One action in a document row's overflow menu. [tint] is null for neutral actions.
 */
data class DocumentMenuAction(
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color? = null,
    val onClick: () -> Unit,
)

/**
 * Full-width library row: thumbnail, name, meta line, favourite hint and an overflow menu
 * with contextual actions (PRD §15). Tapping the row opens the document.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentRow(
    document: com.vedica.labs.ind.app.docora.core.model.Document,
    onClick: () -> Unit,
    renderPreview: suspend (String, Int) -> Bitmap?,
    modifier: Modifier = Modifier,
    menuItems: List<DocumentMenuAction> = emptyList(),
    onLongPress: (() -> Unit)? = null,
) {
    val extended = com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraExtendedColors.current
    androidx.compose.material3.Card(
        shape = MaterialTheme.shapes.large,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onLongPress != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongPress)
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DocumentThumbnail(
                uri = document.uri,
                type = document.type,
                renderPreview = renderPreview,
                targetPx = 256,
                size = 52.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    document.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    "${document.type.label} • " +
                        com.vedica.labs.ind.app.docora.core.util.FileSizeFormatter.format(document.sizeBytes) +
                        " • " +
                        com.vedica.labs.ind.app.docora.core.util.DateFormatter.formatRelative(document.modifiedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            if (document.isFavorite) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = extended.favourite,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            if (menuItems.isNotEmpty()) {
                DocumentOverflowMenu(actions = menuItems)
            }
        }
    }
}

/**
 * The three-dot overflow menu of a document.
 *
 * The menu is anchored by wrapping the trigger and the popup in the same [Box]: `DropdownMenu`
 * positions itself against its parent layout node, so as a bare sibling inside the full-width
 * row it used to open at the left edge of the card instead of under the icon. Keeping one child
 * around the trigger makes the popup line up with the dots it was launched from.
 */
@Composable
private fun DocumentOverflowMenu(
    actions: List<DocumentMenuAction>,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        val button: @Composable () -> Unit = {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(com.vedica.labs.ind.app.docora.R.string.action_more),
                    tint = iconTint,
                )
            }
        }
        if (containerColor != null) {
            Surface(shape = CircleShape, color = containerColor) { button() }
        } else {
            button()
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(action.labelRes),
                            color = action.tint ?: MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            action.icon,
                            contentDescription = null,
                            tint = action.tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        action.onClick()
                    },
                )
            }
        }
    }
}

/** Grid tile variant of [DocumentRow]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentGridTile(
    document: com.vedica.labs.ind.app.docora.core.model.Document,
    onClick: () -> Unit,
    renderPreview: suspend (String, Int) -> Bitmap?,
    modifier: Modifier = Modifier,
    thumbnailHeight: Dp = 148.dp,
    menuItems: List<DocumentMenuAction> = emptyList(),
    onLongPress: (() -> Unit)? = null,
) {
    androidx.compose.material3.Card(
        shape = MaterialTheme.shapes.large,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = modifier.then(
            if (onLongPress != null) {
                Modifier.combinedClickable(onClick = onClick, onLongClick = onLongPress)
            } else {
                Modifier
            },
        ),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(thumbnailHeight)) {
                DocumentThumbnail(
                    uri = document.uri,
                    type = document.type,
                    renderPreview = renderPreview,
                    targetPx = 512,
                    modifier = Modifier.fillMaxSize(),
                )
                if (menuItems.isNotEmpty()) {
                    // Same actions as the list row, floated over the thumbnail so grid mode is
                    // not a dead end for tagging, favouriting or trashing a document.
                    DocumentOverflowMenu(
                        actions = menuItems,
                        modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                    )
                }
            }
            Column(Modifier.padding(12.dp)) {
                Text(
                    document.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    minLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${document.type.label} • " +
                        com.vedica.labs.ind.app.docora.core.util.FileSizeFormatter.format(document.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Row for a MediaStore discovery result (not yet part of the library). */
@Composable
fun DeviceDocumentRow(
    device: com.vedica.labs.ind.app.docora.core.storage.DeviceDocument,
    onClick: () -> Unit,
    renderPreview: suspend (String, Int) -> Bitmap?,
    modifier: Modifier = Modifier,
) {
    val openLabel = androidx.compose.ui.res.stringResource(
        com.vedica.labs.ind.app.docora.R.string.action_open,
    )
    androidx.compose.material3.Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DocumentThumbnail(
                uri = device.uri,
                type = device.type,
                renderPreview = renderPreview,
                targetPx = 256,
                size = 52.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    device.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    "${device.type.label} • " +
                        com.vedica.labs.ind.app.docora.core.util.FileSizeFormatter.format(device.sizeBytes) +
                        " • " +
                        com.vedica.labs.ind.app.docora.core.util.DateFormatter.formatRelative(device.modifiedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Text(
                openLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}



/**
 * Storage permission gate shown before MediaStore discovery. Explains why access is needed
 * (PRD §66 rationale) and offers a direct route to system settings once the user has
 * permanently denied the permission.
 */
@Composable
fun StoragePermissionCard(
    onAllow: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    androidx.compose.material3.Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = scheme.primaryContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(scheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    androidx.compose.ui.res.stringResource(
                        com.vedica.labs.ind.app.docora.R.string.permission_storage_title,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                androidx.compose.ui.res.stringResource(
                    com.vedica.labs.ind.app.docora.R.string.permission_storage_message,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(6.dp))
            Row {
                androidx.compose.material3.TextButton(onClick = onAllow) {
                    Text(
                        androidx.compose.ui.res.stringResource(
                            com.vedica.labs.ind.app.docora.R.string.action_allow,
                        ),
                    )
                }
                if (onOpenSettings != null) {
                    androidx.compose.material3.TextButton(onClick = onOpenSettings) {
                        Text(
                            androidx.compose.ui.res.stringResource(
                                com.vedica.labs.ind.app.docora.R.string.action_open_settings,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** Generic empty/error state card with an optional action button. */
@Composable
fun InfoCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    androidx.compose.material3.Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(scheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = scheme.onSecondaryContainer,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                androidx.compose.material3.TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

