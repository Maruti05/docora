package com.vedica.labs.ind.app.docora.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.model.DocumentCategory

@Composable
fun HomeCollections(state: HomeUiState, onBrowse: () -> Unit) {
    Text(stringResource(R.string.home_collections), style = MaterialTheme.typography.titleMedium)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(collectionTiles(state)) { tile ->
            CollectionTile(
                title = stringResource(tile.titleRes),
                count = tile.count,
                icon = tile.icon,
                onClick = onBrowse,
            )
        }
    }
}

@Composable
fun HomeCategories(state: HomeUiState, onBrowse: () -> Unit) {
    Text(stringResource(R.string.home_categories), style = MaterialTheme.typography.titleMedium)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(DocumentCategory.dashboard) { category ->
            val count = state.categoryCounts[category] ?: 0
            CategoryChip(
                label = stringResource(categoryNameRes(category)),
                count = count,
                icon = categoryIcon(category),
                onClick = onBrowse,
            )
        }
    }
}

@Composable
fun HomeCategoriesRow(state: HomeUiState, onBrowse: () -> Unit) {
    HomeCategories(state = state, onBrowse = onBrowse)
}
