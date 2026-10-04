package com.vedica.labs.ind.app.docora.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.ui.components.DocoraProgressCard
import com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraSpacing
import com.vedica.labs.ind.app.docora.ui.screens.Screen

/**
 * The dashboard.
 *
 * Everything is a row of one `LazyColumn`: the header and hero are single items, the two rails are
 * one item each (they scroll horizontally on their own), and the recent documents are real list
 * items, so the list virtualises them instead of composing the whole section up front. Each item
 * carries a stable key and a content type, which lets the list reuse a row's composition when a
 * document moves and keeps scroll position stable across a data refresh.
 *
 * The screen has no `Scaffold` (the shell supplies the navigation chrome), so it claims the status
 * bar inset itself; the other tabs get it for free.
 */
@Composable
fun HomeScreen(controller: NavHostController, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spacing = LocalDocoraSpacing.current
    val indexing = state.indexing
    val recent = state.recentDocuments

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(
            start = spacing.screenHorizontal,
            end = spacing.screenHorizontal,
            top = spacing.screenVertical,
            bottom = spacing.bottomScrollInset,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        item(key = "header", contentType = "header") {
            HomeHeader(
                onSettings = { controller.navigate(Screen.Settings.route) },
                onSearchOpen = { controller.navigate(Screen.Search.route) },
            )
        }

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

        item(key = "hero", contentType = "hero") {
            HomeHero(
                total = state.stats.totalDocuments,
                bytes = state.stats.totalBytes,
                onScan = { controller.navigate(Screen.Scanner.route) },
                onImport = { controller.navigate(Screen.Documents.route) },
            )
        }

        item(key = "collectionsHeader", contentType = "section") {
            HomeSectionHeader(
                title = stringResource(R.string.home_collections),
                modifier = Modifier.padding(top = spacing.small),
            )
        }
        item(key = "collections", contentType = "collections") {
            HomeCollectionsRow(
                state = state,
                onOpenCollection = { controller.navigate(Screen.Documents.route) },
            )
        }

        item(key = "categoriesHeader", contentType = "section") {
            HomeSectionHeader(
                title = stringResource(R.string.home_categories),
                modifier = Modifier.padding(top = spacing.small),
            )
        }
        item(key = "categories", contentType = "categories") {
            HomeCategoriesRow(
                state = state,
                onOpenCategory = { controller.navigate(Screen.Documents.route) },
            )
        }

        item(key = "recentHeader", contentType = "section") {
            HomeSectionHeader(
                title = stringResource(R.string.home_recent_documents),
                actionLabel = stringResource(R.string.home_see_all),
                onAction = { controller.navigate(Screen.Documents.route) },
                modifier = Modifier.padding(top = spacing.small),
            )
        }

        if (recent.isEmpty()) {
            item(key = "recentEmpty", contentType = "empty") {
                EmptyLibraryCard(
                    onScan = { controller.navigate(Screen.Scanner.route) },
                    onImport = { controller.navigate(Screen.Documents.route) },
                )
            }
        } else {
            items(
                items = recent,
                key = { document -> document.id },
                contentType = { "recent" },
            ) { document ->
                RecentDocumentRow(
                    document = document,
                    renderPreview = { uri, target -> viewModel.renderPreview(uri, target) },
                    onClick = { controller.navigate(Screen.detailsRoute(document.id)) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}
