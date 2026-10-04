package com.vedica.labs.ind.app.docora.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.backup.BackupFile
import com.vedica.labs.ind.app.docora.core.util.DateFormatter
import com.vedica.labs.ind.app.docora.ui.designsystem.DocoraContentColumn
import com.vedica.labs.ind.app.docora.ui.designsystem.DocoraThemeTokens
import com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraSpacing

/**
 * The settings screen.
 *
 * A hero card with live library numbers opens the page, then one foldable card per topic:
 * appearance, library defaults, security, scanner, storage & search, backup and about. Picker rows
 * always show the current value and open a bottom sheet; maintenance actions show their own
 * spinner; the export/restore rows hand a user-chosen file to the ViewModel through the system
 * file chooser. Nothing here needs a permission and nothing reaches the network.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(controller: NavHostController, vm: SettingsViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cacheBytes by vm.cacheBytes.collectAsStateWithLifecycle()
    val dashboard by vm.dashboard.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val reindexProgress by vm.reindexProgress.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snacks = remember { SnackbarHostState() }
    val motion = DocoraThemeTokens.motion
    val spacing = LocalDocoraSpacing.current
    val snackMessage = message?.let { resolveMessage(it) }

    // The auth prompt wraps the system dialog, so it needs the localised strings resolved here.
    val unlockTitle = stringResource(R.string.settings_app_lock_unlock_title)
    val unlockSubtitle = stringResource(R.string.settings_app_lock_unlock_subtitle)
    SideEffect {
        vm.unlockTitle = unlockTitle
        vm.unlockSubtitle = unlockSubtitle
    }

    LaunchedEffect(Unit) { vm.refreshStats() }
    LaunchedEffect(snackMessage) {
        if (snackMessage != null) {
            snacks.showSnackbar(snackMessage)
            vm.clearMessage()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFile.MIME_TYPE),
    ) { uri: Uri? ->
        if (uri != null) vm.exportBackup(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) vm.restoreBackup(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = { controller.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snacks) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        DocoraContentColumn(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.screenHorizontal, vertical = spacing.screenVertical),
                verticalArrangement = Arrangement.spacedBy(spacing.extraLarge),
            ) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(motion.standardSpec()) +
                        slideInVertically(motion.standardSpec()) { it / 4 },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.extraLarge)) {
                        SettingsHeroCard(stats = dashboard)
                        if (busy == SettingsBusy.REINDEX && reindexProgress != null) {
                            LinearProgressIndicator(
                                progress = { (reindexProgress ?: 0f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        AppearanceSection(s = settings, vm = vm)
                        LibrarySection(s = settings, vm = vm)
                        SecuritySection(s = settings, vm = vm)
                        ScannerSection(s = settings, vm = vm)
                        StorageSection(s = settings, cacheBytes = cacheBytes, busy = busy, vm = vm)
                        BackupSection(
                            busy = busy,
                            onExport = {
                                exportLauncher.launch(
                                    "docora-backup-${DateFormatter.formatFileStamp(System.currentTimeMillis())}.${BackupFile.FILE_EXTENSION}",
                                )
                            },
                            onRestore = { restoreLauncher.launch(arrayOf(BackupFile.MIME_TYPE)) },
                            vm = vm,
                        )
                        AboutSection()
                        Spacer(Modifier.height(spacing.bottomScrollInset))
                    }
                }
            }
        }
    }
}

/**
 * [SettingsMessage] carries raw resource ids because a ViewModel must never resolve strings
 * itself; this single resolver is the only place payloads become text.
 */
@Composable
private fun resolveMessage(message: SettingsMessage): String =
    stringResource(message.textRes, *message.args.toTypedArray())
