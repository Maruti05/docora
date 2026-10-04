package com.vedica.labs.ind.app.docora.ui.home

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vedica.labs.ind.app.docora.core.model.Document
import com.vedica.labs.ind.app.docora.ui.components.DocumentThumbnail
import com.vedica.labs.ind.app.docora.ui.designsystem.DocoraThemeTokens
import com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraSpacing

/**
 * The dashboard's reusable pieces.
 *
 * Every tile is a fixed-width [Card] so a horizontal rail scrolls predictably, and every accent
 * bubble is drawn from the palette in `HomeTiles` at low alpha: the tint carries the meaning while
 * the theme keeps owning the actual surface colours, which is what keeps the rail legible in both
 * light and dark mode.
 */

/** A shortcut into the library: icon, label and the live count behind it. */
@Composable
fun CollectionTile(
    title: String,
    count: Int,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalDocoraSpacing.current
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.width(152.dp),
    ) {
        Column(
            modifier = Modifier.padding(spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            AccentBubble(icon = icon, accent = accent, size = 44.dp, iconSize = 22.dp)
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** A category tile: same visual language as the shortcut tiles, one size down. */
@Composable
fun CategoryTile(
    title: String,
    count: Int,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalDocoraSpacing.current
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.width(132.dp),
    ) {
        Column(
            modifier = Modifier.padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            AccentBubble(icon = icon, accent = accent, size = 36.dp, iconSize = 19.dp)
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One recent document: a real thumbnail when one can be rendered, the type of the file and when it
 * was last modified, with the favourite state promoted to its own trailing mark.
 */
@Composable
fun RecentDocumentRow(
    document: Document,
    renderPreview: suspend (String, Int) -> Bitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalDocoraSpacing.current
    val extended = DocoraThemeTokens.extendedColors
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DocumentThumbnail(
                uri = document.uri,
                type = document.type,
                renderPreview = renderPreview,
                targetPx = THUMBNAIL_PX,
                size = 52.dp,
            )
            Spacer(Modifier.width(spacing.medium))
            Column(Modifier.weight(1f)) {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    text = documentMeta(document),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (document.isFavorite) {
                Spacer(Modifier.width(spacing.small))
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = extended.favourite,
                    modifier = Modifier.size(20.dp),
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Shared accent bubble: coloured glyph on a faint wash of the same colour. */
@Composable
private fun AccentBubble(
    icon: ImageVector,
    accent: Color,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Thumbnail decode width; small enough to stay cheap, large enough to look sharp at 52dp. */
private const val THUMBNAIL_PX = 168
