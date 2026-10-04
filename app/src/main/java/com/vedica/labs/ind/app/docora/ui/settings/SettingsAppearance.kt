package com.vedica.labs.ind.app.docora.ui.settings

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.model.AppSettings
import com.vedica.labs.ind.app.docora.core.model.ThemeMode

/**
 * Everything the user sees before they open a document: theme, wallpaper colour and motion.
 *
 * Each row says what it does (icon + summary) and shows the current value, opening a sheet -
 * never cycling blindly through options.
 */
@Composable
fun AppearanceSection(s: AppSettings, vm: SettingsViewModel) {
    var themeSheet by remember { mutableStateOf(false) }
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    SettingsSection(
        title = stringResource(R.string.settings_section_appearance),
        subtitle = stringResource(R.string.settings_section_appearance_summary),
    ) {
        SettingsRow(
            icon = themeIcon(s.themeMode),
            title = stringResource(R.string.settings_theme),
            subtitle = stringResource(R.string.settings_theme_summary),
            trailing = { SettingsValueTrailing(themeLabel(s.themeMode)) },
            onClick = { themeSheet = true },
        )
        SettingsDivider()
        SettingsSwitchRow(
            icon = Icons.Filled.Palette,
            title = stringResource(R.string.settings_dynamic_colour),
            subtitle = if (dynamicSupported) {
                stringResource(R.string.settings_dynamic_colour_summary)
            } else {
                stringResource(R.string.settings_dynamic_colour_unsupported)
            },
            checked = s.useDynamicColor && dynamicSupported,
            enabled = dynamicSupported,
            onCheckedChange = { vm.setDynamicColor(it) },
        )
        SettingsDivider()
        SettingsSwitchRow(
            icon = Icons.Filled.Animation,
            title = stringResource(R.string.settings_reduce_motion),
            subtitle = stringResource(R.string.settings_reduce_motion_summary),
            checked = s.reduceMotion,
            onCheckedChange = { vm.setReduceMotion(it) },
        )
    }

    if (themeSheet) {
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_theme),
            message = stringResource(R.string.settings_theme_summary),
            options = listOf(
                SettingsChoice(ThemeMode.SYSTEM, stringResource(R.string.settings_theme_system)),
                SettingsChoice(ThemeMode.LIGHT, stringResource(R.string.settings_theme_light)),
                SettingsChoice(ThemeMode.DARK, stringResource(R.string.settings_theme_dark)),
            ),
            selected = s.themeMode,
            onSelect = { vm.setTheme(it); themeSheet = false },
            onDismiss = { themeSheet = false },
        )
    }
}

private fun themeIcon(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> Icons.Filled.SettingsBrightness
    ThemeMode.LIGHT -> Icons.Filled.LightMode
    ThemeMode.DARK -> Icons.Filled.DarkMode
}

@Composable
private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
}
