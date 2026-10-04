package com.vedica.labs.ind.app.docora.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.model.DocumentCategory
import com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraSpacing

/**
 * The dashboard's section furniture.
 *
 * Both rails are `LazyRow`s, so only the tiles actually on screen are composed and measured: with
 * ten categories and five shortcuts the difference is invisible on a flagship and very visible on
 * a low-end phone, which is exactly where a home screen has to stay smooth.
 */

/** A section title with an optional trailing action ("See all"). */
@Composable
fun HomeSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onAction),
            )
        }
    }
}

/** The shortcut rail: one facet of the library per tile. */
@Composable
fun HomeCollectionsRow(
    state: HomeUiState,
    onOpenCollection: (HomeCollection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalDocoraSpacing.current
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing.gridGap),
    ) {
        items(items = HomeCollection.entries, key = { it.name }) { collection ->
            CollectionTile(
                title = stringResource(collection.titleRes),
                count = collectionCount(collection, state),
                icon = collection.icon,
                accent = collectionAccent(collection),
                onClick = { onOpenCollection(collection) },
            )
        }
    }
}

/** The category rail: the same rule-based categories the browser filters by (PRD §19). */
@Composable
fun HomeCategoriesRow(
    state: HomeUiState,
    onOpenCategory: (DocumentCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalDocoraSpacing.current
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing.gridGap),
    ) {
        items(items = DocumentCategory.dashboard, key = { it.name }) { category ->
            CategoryTile(
                title = stringResource(categoryNameRes(category)),
                count = state.categoryCounts[category] ?: 0,
                icon = categoryIcon(category),
                accent = categoryAccent(category),
                onClick = { onOpenCategory(category) },
            )
        }
    }
}
