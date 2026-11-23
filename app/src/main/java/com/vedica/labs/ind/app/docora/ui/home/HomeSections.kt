package com.vedica.labs.ind.app.docora.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vedica.labs.ind.app.docora.core.model.DocumentCategory
import com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraSpacing

/**
 * Category filters, rendered as quiet text pills.
 *
 * Only categories that actually contain documents are offered: an empty category is a dead end,
 * and ten grey pills would drown the useful ones. Each pill carries its live count, so the row
 * doubles as a one-line overview of how the library is organised.
 */
@Composable
fun HomeCategoriesRow(
    counts: Map<DocumentCategory, Int>,
    onOpenCategory: (DocumentCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalDocoraSpacing.current
    val visible = DocumentCategory.dashboard.filter { (counts[it] ?: 0) > 0 }
    if (visible.isEmpty()) return

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        items(items = visible, key = { it.name }) { category ->
            CategoryPill(
                title = stringResource(categoryNameRes(category)),
                count = counts[category] ?: 0,
                onClick = { onOpenCategory(category) },
            )
        }
    }
}

@Composable
private fun CategoryPill(
    title: String,
    count: Int,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = scheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
