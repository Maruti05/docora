package com.vedica.labs.ind.app.docora.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.model.AppSettings
import com.vedica.labs.ind.app.docora.core.model.AutoLockTimeout

/**
 * Keeping other people out: the lock itself, how fast it re-engages, and the screenshot policy.
 *
 * Turning App Lock on always proves identity first (see
 * [SettingsViewModel.requestAppLockEnabled]); flipping the switch off is instant on purpose, so a
 * broken sensor can never trap the owner outside their own library.
 */
@Composable
fun SecuritySection(s: AppSettings, vm: SettingsViewModel) {
    var timeoutSheet by remember { mutableStateOf(false) }
    val lockAvailable = vm.canUseBiometrics || s.appLockEnabled

    SettingsSection(title = stringResource(R.string.settings_section_security)) {
        SettingsSwitchRow(
            icon = if (s.appLockEnabled) Icons.Filled.Lock else Icons.Filled.Fingerprint,
            title = stringResource(R.string.settings_app_lock),
            subtitle = if (lockAvailable) {
                stringResource(R.string.settings_app_lock_summary)
            } else {
                stringResource(R.string.settings_app_lock_unavailable)
            },
            checked = s.appLockEnabled,
            enabled = lockAvailable,
            onCheckedChange = { enabled ->
                if (enabled) vm.requestAppLockEnabled() else vm.setAppLockDisabled()
            },
        )
        SettingsDivider()
        SettingsRow(
            icon = Icons.Filled.Timer,
            title = stringResource(R.string.settings_auto_lock),
            subtitle = autoLockLabel(s.autoLockTimeout),
            enabled = s.appLockEnabled,
            trailing = {
                if (s.appLockEnabled) SettingsValueTrailing(autoLockLabel(s.autoLockTimeout))
            },
            onClick = if (s.appLockEnabled) {
                { timeoutSheet = true }
            } else {
                null
            },
        )
        SettingsDivider()
        SettingsSwitchRow(
            icon = Icons.Filled.VisibilityOff,
            title = stringResource(R.string.settings_secure_screen),
            subtitle = stringResource(R.string.settings_secure_screen_summary),
            checked = s.secureScreenEnabled,
            onCheckedChange = { vm.setSecureScreen(it) },
        )
        SettingsDivider()
        SettingsRow(
            icon = Icons.Filled.Lock,
            title = stringResource(R.string.settings_lock_now),
            subtitle = stringResource(R.string.settings_lock_now_summary),
            accent = SettingsAccent.TERTIARY,
            enabled = s.appLockEnabled,
            onClick = { vm.lockNow() },
        )
    }

    if (timeoutSheet) {
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_auto_lock),
            message = stringResource(R.string.settings_auto_lock_summary),
            options = AutoLockTimeout.entries.map { timeout ->
                SettingsChoice(timeout, autoLockLabel(timeout))
            },
            selected = s.autoLockTimeout,
            onSelect = { vm.setAutoLock(it); timeoutSheet = false },
            onDismiss = { timeoutSheet = false },
        )
    }
}

@Composable
private fun autoLockLabel(timeout: AutoLockTimeout): String = when (timeout) {
    AutoLockTimeout.IMMEDIATELY -> stringResource(R.string.settings_auto_lock_immediately)
    AutoLockTimeout.ONE_MINUTE -> stringResource(R.string.settings_auto_lock_1)
    AutoLockTimeout.FIVE_MINUTES -> stringResource(R.string.settings_auto_lock_5)
    AutoLockTimeout.FIFTEEN_MINUTES -> stringResource(R.string.settings_auto_lock_15)
    AutoLockTimeout.NEVER -> stringResource(R.string.settings_auto_lock_never)
}

