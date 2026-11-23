package com.vedica.labs.ind.app.docora.ui.home

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.model.Document
import com.vedica.labs.ind.app.docora.ui.components.DocumentThumbnail
import com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraSpacing

/**
 * One row of the recent list.
 *
 * Deliberately not a card: the rows sit inside one surface in [HomeScreen], separated by
 * hairlines, which is calmer than eight stacked cards and makes the list read as a single object.
 * Tapping opens the document itself in the viewer - details remain available from the browser.
 */
@Composable
fun RecentDocumentRow(
    document: Document,
    renderPreview: suspend (String, Int) -> Bitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalDocoraSpacing.current
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DocumentThumbnail(
            uri = document.uri,
            type = document.type,
            renderPreview = renderPreview,
            targetPx = THUMBNAIL_PX,
            size = 44.dp,
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
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (document.isFavorite) {
            Spacer(Modifier.width(spacing.small))
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = stringResource(R.string.action_favourite),
                tint = scheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(spacing.extraSmall))
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = scheme.outline,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Thumbnail decode width; small enough to stay cheap, large enough to look sharp at 44dp. */
private const val THUMBNAIL_PX = 160
