package com.vedica.labs.ind.app.docora.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.vedica.labs.ind.app.docora.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(controller: NavHostController, vm: SettingsViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cacheBytes by vm.cacheBytes.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snacks = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.refreshCacheSize() }
    LaunchedEffect(message) {
        if (message != null) {
            snacks.showSnackbar(message ?: "")
            vm.clearMessage()
        }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        snackbarHost = { SnackbarHost(snacks) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(padding).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 3 },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    AppearanceSection(
                        selected = settings.themeMode,
                        dynamic = settings.useDynamicColor,
                        onTheme = { vm.setTheme(it) },
                        onDynamic = { vm.setDynamicColor(it) },
                    )
                    LibrarySection(s = settings, vm = vm)
                    SecuritySection(s = settings, vm = vm)
                    ScannerSection(s = settings, vm = vm)
                    StorageSection(s = settings, cacheBytes = cacheBytes, vm = vm)
                    SearchSection(s = settings, vm = vm)
                    BackupSection(vm = vm)
                    AboutSection(version = "1.0.0")
                }
            }
        }
    }
}
