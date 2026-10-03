package com.vedica.labs.ind.app.docora.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.model.AppSettings
import com.vedica.labs.ind.app.docora.core.model.AutoLockTimeout
import com.vedica.labs.ind.app.docora.ui.components.DocoraSectionTitle
import com.vedica.labs.ind.app.docora.ui.components.DocoraSettingsRow
import com.vedica.labs.ind.app.docora.ui.components.DocoraSwitchRow

@Composable
fun SecuritySection(s: AppSettings, vm: SettingsViewModel) {
    val scheme = MaterialTheme.colorScheme
    DocoraSectionTitle(stringResource(R.string.settings_section_security))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DocoraSwitchRow(
            icon = Icons.Filled.Lock,
            iconTint = scheme.primary,
            iconBackground = scheme.primaryContainer,
            title = stringResource(R.string.settings_app_lock),
            subtitle = stringResource(R.string.settings_app_lock_summary),
            checked = s.appLockEnabled,
            onCheckedChange = { vm.setAppLock(it) },
            enabled = vm.canUseBiometrics || s.appLockEnabled,
        )
        DocoraSettingsRow(
            icon = Icons.Filled.Timer,
            iconTint = scheme.tertiary,
            iconBackground = scheme.tertiaryContainer,
            title = stringResource(R.string.settings_auto_lock),
            subtitle = autoLockLabel(s.autoLockTimeout),
            onClick = { vm.setAutoLock(nextAutoLock(s.autoLockTimeout)) },
        )
        DocoraSwitchRow(
            icon = Icons.Filled.VisibilityOff,
            iconTint = scheme.secondary,
            iconBackground = scheme.secondaryContainer,
            title = stringResource(R.string.settings_secure_screen),
            subtitle = stringResource(R.string.settings_secure_screen_summary),
            checked = s.secureScreenEnabled,
            onCheckedChange = { vm.setSecureScreen(it) },
        )
        DocoraSwitchRow(
            icon = Icons.Filled.Fingerprint,
            iconTint = scheme.primary,
            iconBackground = scheme.primaryContainer,
            title = stringResource(R.string.settings_encrypt_text),
            subtitle = stringResource(R.string.settings_encrypt_text_summary),
            checked = s.encryptExtractedText,
            onCheckedChange = { vm.setEncryptText(it) },
        )
    }
}

fun autoLockLabel(timeout: AutoLockTimeout): String {
    return when (timeout) {
        AutoLockTimeout.IMMEDIATELY -> "Immediately"
        AutoLockTimeout.ONE_MINUTE -> "After 1 minute"
        AutoLockTimeout.FIVE_MINUTES -> "After 5 minutes"
        AutoLockTimeout.FIFTEEN_MINUTES -> "After 15 minutes"
        AutoLockTimeout.NEVER -> "Never"
    }
}

fun nextAutoLock(current: AutoLockTimeout): AutoLockTimeout {
    val all = AutoLockTimeout.entries
    return all[(all.indexOf(current) + 1) % all.size]
}
