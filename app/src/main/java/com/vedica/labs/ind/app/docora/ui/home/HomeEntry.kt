package com.vedica.labs.ind.app.docora.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.model.DocumentCategory
import com.vedica.labs.ind.app.docora.ui.components.DocoraProgressCard
import com.vedica.labs.ind.app.docora.ui.components.DocoraSectionHeader
import com.vedica.labs.ind.app.docora.ui.components.DocoraSkeleton
import com.vedica.labs.ind.app.docora.ui.components.LocalBottomBarInset
import com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraSpacing
import com.vedica.labs.ind.app.docora.ui.screens.Screen

/**
 * The home dashboard.
 *
 * A deliberately short, quiet page: the product name, a three-number overview, the most recent
 * documents and the categories that actually contain something. Everything duplicated from the
 * bottom bar (Documents, Tools, Search, Settings) is gone, and there are no coloured shortcut
 * tiles - navigation is the bar's job, so the dashboard is free to be about *content*.
 *
 * The recent list opens documents in the viewer directly. The old behaviour - tapping a recent
 * file opened its details sheet - added a step to the most common action in the app.
 *
 * Structure is still one `LazyColumn` with stable keys, so the page virtualises and survives a
 * data refresh without losing scroll position.
 */
@Composable
fun HomeScreen(controller: NavHostController, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spacing = LocalDocoraSpacing.current
    val bottomInset = maxOf(spacing.bottomScrollInset, LocalBottomBarInset.current)
    val indexing = state.indexing
    val recent = state.recentDocuments
    val categories = DocumentCategory.dashboard.filter { (state.categoryCounts[it] ?: 0) > 0 }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(
            start = spacing.screenHorizontal,
            end = spacing.screenHorizontal,
            top = spacing.screenVertical,
            bottom = bottomInset,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.large),
    ) {
        item(key = "header", contentType = "header") {
            HomeHeader(onScan = { controller.navigate(Screen.Scanner.route) })
        }

        if (state.isLoading) {
            item(key = "overviewSkeleton", contentType = "skeleton") {
                HomeOverviewSkeleton()
            }
            item(key = "categoriesHeader", contentType = "section") {
                DocoraSectionHeader(
                    title = stringResource(R.string.home_categories),
                )
            }
            item(key = "categoriesSkeleton", contentType = "skeleton") {
                HomeCategoriesSkeleton()
            }
            item(key = "recentHeader", contentType = "section") {
                DocoraSectionHeader(
                    title = stringResource(R.string.home_recent_documents),
                )
            }
            item(key = "recentSkeleton", contentType = "skeleton") {
                HomeRecentSkeleton()
            }
        } else {
            if (indexing.total > 0 && !indexing.isComplete) {
                item(key = "progress", contentType = "progress") {
                    DocoraProgressCard(
                        title = stringResource(R.string.processing_title),
                        subtitle = stringResource(
                            R.string.processing_progress,
                            indexing.processed,
                            indexing.total,
                        ),
                        fraction = indexing.fraction,
                    )
                }
            }

            item(key = "overview", contentType = "overview") {
                HomeOverviewCard(
                    total = state.stats.totalDocuments,
                    favourites = state.stats.favorites,
                    bytes = state.stats.totalBytes,
                )
            }

            if (categories.isNotEmpty()) {
                item(key = "categoriesHeader", contentType = "section") {
                    DocoraSectionHeader(
                        title = stringResource(R.string.home_categories),
                    )
                }
                item(key = "categories", contentType = "categories") {
                    HomeCategoriesRow(
                        counts = state.categoryCounts,
                        onOpenCategory = { category ->
                            controller.navigate(Screen.categoryRoute(category.name))
                        },
                    )
                }
            }

            item(key = "recentHeader", contentType = "section") {
                DocoraSectionHeader(
                    title = stringResource(R.string.home_recent_documents),
                    actionLabel = stringResource(R.string.home_see_all),
                    onAction = { controller.navigate(Screen.Documents.route) },
                )
            }

            if (recent.isEmpty()) {
                item(key = "recentEmpty", contentType = "empty") {
                    EmptyLibraryCard(onScan = { controller.navigate(Screen.Scanner.route) })
                }
            } else {
                item(key = "recent", contentType = "recent") {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            recent.forEachIndexed { index, document ->
                                RecentDocumentRow(
                                    document = document,
                                    renderPreview = { uri, target ->
                                        viewModel.renderPreview(uri, target)
                                    },
                                    onClick = {
                                        controller.navigate(Screen.viewerRoute(document.id))
                                    },
                                )
                                if (index != recent.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 72.dp),
                                        thickness = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Three pulsing number blocks in the same shape as [HomeOverviewCard]. */
@Composable
private fun HomeOverviewSkeleton() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { index ->
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DocoraSkeleton(Modifier.size(58.dp, 22.dp))
                    Spacer(Modifier.size(6.dp))
                    DocoraSkeleton(Modifier.size(46.dp, 11.dp))
                }
                if (index < 2) {
                    VerticalDivider(
                        modifier = Modifier.height(30.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

/** A row of chip-shaped placeholders under the Categories heading. */
@Composable
private fun HomeCategoriesSkeleton() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(4) {
            DocoraSkeleton(
                modifier = Modifier.size(72.dp, 30.dp),
                shape = RoundedCornerShape(999.dp),
            )
        }
    }
}

/** Recent-list skeleton: thumbnail block + two text lines, matching the real row height. */
@Composable
private fun HomeRecentSkeleton() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            repeat(4) { index ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DocoraSkeleton(
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        DocoraSkeleton(Modifier.fillMaxWidth(0.72f).height(14.dp))
                        Spacer(Modifier.size(6.dp))
                        DocoraSkeleton(Modifier.fillMaxWidth(0.44f).height(10.dp))
                    }
                }
                if (index < 3) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
}
