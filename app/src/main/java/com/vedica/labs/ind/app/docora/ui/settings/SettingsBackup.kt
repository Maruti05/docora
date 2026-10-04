package com.vedica.labs.ind.app.docora.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.vedica.labs.ind.app.docora.R

/**
 * Getting data in and out: a portable backup and the escape hatch that returns every option to
 * its default.
 *
 * The actual file is picked through the system file chooser ([SettingsScreen] owns the
 * launchers), so Docora never needs a storage permission for this - the one file the user
 * explicitly chose is the only one touched.
 */
@Composable
fun BackupSection(
    busy: SettingsBusy,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    vm: SettingsViewModel,
) {
    var resetDialog by remember { mutableStateOf(false) }
    val working = busy == SettingsBusy.WORKING

    SettingsSection(title = stringResource(R.string.settings_section_backup_reset)) {
        SettingsRow(
            icon = Icons.Filled.Backup,
            title = stringResource(R.string.settings_backup_export),
            subtitle = stringResource(R.string.settings_backup_export_summary2),
            enabled = !working,
            trailing = { if (working) SettingsSpinnerTrailing() },
            onClick = onExport,
        )
        SettingsDivider()
        SettingsRow(
            icon = Icons.Filled.Restore,
            title = stringResource(R.string.settings_backup_import),
            subtitle = stringResource(R.string.settings_backup_import_summary2),
            accent = SettingsAccent.SECONDARY,
            enabled = !working,
            trailing = { if (working) SettingsSpinnerTrailing() },
            onClick = onRestore,
        )
        SettingsDivider()
        SettingsRow(
            icon = Icons.Filled.RestartAlt,
            title = stringResource(R.string.settings_reset),
            subtitle = stringResource(R.string.settings_reset_summary),
            accent = SettingsAccent.ERROR,
            onClick = { resetDialog = true },
        )
    }

    if (resetDialog) {
        SettingsConfirmDialog(
            title = stringResource(R.string.settings_reset_confirm_title),
            message = stringResource(R.string.settings_reset_confirm_message),
            confirmLabel = stringResource(R.string.settings_reset_confirm_action),
            onConfirm = {
                vm.resetAllSettings()
                resetDialog = false
            },
            onDismiss = { resetDialog = false },
        )
    }
}
