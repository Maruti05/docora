package com.vedica.labs.ind.app.docora.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.model.AppSettings
import com.vedica.labs.ind.app.docora.core.model.ScanFilter
import com.vedica.labs.ind.app.docora.core.model.ScanMode
import com.vedica.labs.ind.app.docora.core.model.ScannerQuality
import com.vedica.labs.ind.app.docora.ui.scanner.scanFilterLabelRes
import com.vedica.labs.ind.app.docora.ui.scanner.scanModeLabelRes

/**
 * What every new scan looks like: the capture mode, the image quality, the default colour
 * treatment and whether the shutter fires itself.
 *
 * Labels are shared with the scanner's own mode and filter strips, so "Magic colour" means the
 * same thing in both places instead of drifting into two different names.
 */
@Composable
fun ScannerSection(s: AppSettings, vm: SettingsViewModel) {
    var modeSheet by remember { mutableStateOf(false) }
    var qualitySheet by remember { mutableStateOf(false) }
    var filterSheet by remember { mutableStateOf(false) }
    val filterLabel = stringResource(scanFilterLabelRes(s.scannerFilter))

    SettingsSection(
        title = stringResource(R.string.settings_section_scanner),
        subtitle = stringResource(R.string.settings_scanner_summary),
    ) {
        SettingsRow(
            icon = Icons.Filled.PhotoCamera,
            title = stringResource(R.string.settings_scanner_mode),
            subtitle = stringResource(scanModeLabelRes(s.scannerMode)),
            trailing = { SettingsValueTrailing(stringResource(scanModeLabelRes(s.scannerMode))) },
            onClick = { modeSheet = true },
        )
        SettingsDivider()
        SettingsRow(
            icon = Icons.Filled.HighQuality,
            title = stringResource(R.string.settings_scanner_quality),
            subtitle = qualityLabel(s.scannerQuality),
            trailing = { SettingsValueTrailing(qualityLabel(s.scannerQuality)) },
            onClick = { qualitySheet = true },
        )
        SettingsDivider()
        SettingsRow(
            icon = Icons.Filled.AutoAwesome,
            title = stringResource(R.string.settings_scanner_default_filter),
            subtitle = filterLabel,
            trailing = { SettingsValueTrailing(filterLabel) },
            onClick = { filterSheet = true },
        )
        SettingsDivider()
        SettingsSwitchRow(
            icon = Icons.Filled.CameraAlt,
            title = stringResource(R.string.settings_scanner_auto_capture),
            subtitle = stringResource(R.string.settings_scanner_auto_capture_summary),
            checked = s.scannerAutoCapture,
            onCheckedChange = { vm.setScannerAutoCapture(it) },
        )
    }

    if (modeSheet) {
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_scanner_mode),
            message = stringResource(R.string.settings_scanner_mode_summary),
            options = ScanMode.entries.map { mode ->
                SettingsChoice(mode, stringResource(scanModeLabelRes(mode)))
            },
            selected = s.scannerMode,
            onSelect = { vm.setScannerMode(it); modeSheet = false },
            onDismiss = { modeSheet = false },
        )
    }
    if (qualitySheet) {
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_scanner_quality),
            message = stringResource(R.string.settings_scanner_quality_summary),
            options = ScannerQuality.entries.map { quality ->
                SettingsChoice(quality, qualityLabel(quality), qualitySummary(quality))
            },
            selected = s.scannerQuality,
            onSelect = { vm.setScannerQuality(it); qualitySheet = false },
            onDismiss = { qualitySheet = false },
        )
    }
    if (filterSheet) {
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_scanner_default_filter),
            message = stringResource(R.string.settings_scanner_default_filter_summary),
            options = ScanFilter.entries.map { filter ->
                SettingsChoice(filter, stringResource(scanFilterLabelRes(filter)))
            },
            selected = s.scannerFilter,
            onSelect = { vm.setScannerFilter(it); filterSheet = false },
            onDismiss = { filterSheet = false },
        )
    }
}

@Composable
private fun qualityLabel(quality: ScannerQuality): String = when (quality) {
    ScannerQuality.STANDARD -> stringResource(R.string.settings_scanner_quality_standard)
    ScannerQuality.HIGH -> stringResource(R.string.settings_scanner_quality_high)
    ScannerQuality.MAXIMUM -> stringResource(R.string.settings_scanner_quality_maximum)
}

@Composable
private fun qualitySummary(quality: ScannerQuality): String = when (quality) {
    ScannerQuality.STANDARD -> stringResource(R.string.settings_scanner_quality_standard_summary)
    ScannerQuality.HIGH -> stringResource(R.string.settings_scanner_quality_high_summary)
    ScannerQuality.MAXIMUM -> stringResource(R.string.settings_scanner_quality_maximum_summary)
}

