package com.vedica.labs.ind.app.docora.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.ui.screens.Screen

@Composable
fun HomeRecentSection(state: HomeUiState, controller: NavHostController) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.home_recent_documents),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.home_see_all),
            style = MaterialTheme.typography.labelLarge,
            color = scheme.primary,
            modifier = Modifier.clickable { controller.navigate(Screen.Documents.route) },
        )
    }
    if (state.recentDocuments.isEmpty()) {
        EmptyLibraryCard(onScan = { controller.navigate(Screen.Scanner.route) })
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.recentDocuments.take(5).forEachIndexed { index, document ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(300, delayMillis = index * 60)) +
                        slideInVertically(tween(300, delayMillis = index * 60)) { it / 3 },
                ) {
                    RecentDocumentRow(
                        document = document,
                        onClick = { controller.navigate(Screen.detailsRoute(document.id)) },
                    )
                }
            }
        }
    }
}
