package com.vedica.labs.ind.app.docora.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.ui.components.DocoraProgressCard
import com.vedica.labs.ind.app.docora.ui.screens.Screen

@Composable
fun HomeScreen(controller: NavHostController, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        HomeHeader(
            onSettings = { controller.navigate(Screen.Settings.route) },
            onSearchOpen = { controller.navigate(Screen.Search.route) },
        )
        if (state.indexing.total > 0 && !state.indexing.isComplete) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 },
            ) {
                DocoraProgressCard(
                    title = stringResource(R.string.processing_title),
                    subtitle = stringResource(
                        R.string.processing_progress,
                        state.indexing.processed,
                        state.indexing.total,
                    ),
                    fraction = state.indexing.fraction,
                )
            }
        }
        HeroCard(
            total = state.stats.totalDocuments,
            bytes = state.stats.totalBytes,
            onScan = { controller.navigate(Screen.Scanner.route) },
            onImport = { controller.navigate(Screen.Documents.route) },
        )
        HomeCollections(state = state, onBrowse = { controller.navigate(Screen.Documents.route) })
        HomeCategoriesRow(state = state, onBrowse = { controller.navigate(Screen.Documents.route) })
        HomeRecentSection(state = state, controller = controller)
        Spacer(Modifier.height(8.dp))
    }
}
