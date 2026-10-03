package com.vedica.labs.ind.app.docora.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.model.AppSettings
import com.vedica.labs.ind.app.docora.core.model.ScanFilter
import com.vedica.labs.ind.app.docora.core.model.ScanMode
import com.vedica.labs.ind.app.docora.core.model.ScannerQuality
import com.vedica.labs.ind.app.docora.ui.components.DocoraSectionTitle
import com.vedica.labs.ind.app.docora.ui.components.DocoraSettingsRow
import com.vedica.labs.ind.app.docora.ui.components.DocoraSwitchRow

@Composable
fun ScannerSection(s: AppSettings, vm: SettingsViewModel) {
    val scheme = MaterialTheme.colorScheme
    DocoraSectionTitle(stringResource(R.string.settings_section_scanner))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DocoraSettingsRow(
            icon = Icons.Filled.HighQuality,
            iconTint = scheme.primary,
            iconBackground = scheme.primaryContainer,
            title = stringResource(R.string.settings_scanner_quality),
            subtitle = qualityLabel(s.scannerQuality),
            onClick = { vm.setScannerQuality(nextQuality(s.scannerQuality)) },
        )
        DocoraSettingsRow(
            icon = Icons.Filled.CameraAlt,
            iconTint = scheme.tertiary,
            iconBackground = scheme.tertiaryContainer,
            title = stringResource(R.string.settings_scanner_default_filter),
            subtitle = filterLabel(s.scannerFilter),
            onClick = { vm.setScannerFilter(nextFilter(s.scannerFilter)) },
        )
        DocoraSwitchRow(
            icon = Icons.Filled.AutoAwesome,
            iconTint = scheme.secondary,
            iconBackground = scheme.secondaryContainer,
            title = stringResource(R.string.settings_scanner_auto_capture),
            subtitle = null,
            checked = s.scannerAutoCapture,
            onCheckedChange = { vm.setScannerAutoCapture(it) },
        )
        DocoraSwitchRow(
            icon = Icons.Filled.Style,
            iconTint = scheme.primary,
            iconBackground = scheme.primaryContainer,
            title = stringResource(R.string.settings_scanner_ocr),
            subtitle = null,
            checked = s.ocrAfterScan,
            onCheckedChange = { vm.setOcrAfterScan(it) },
        )
    }
}

fun qualityLabel(q: ScannerQuality): String {
    return when (q) {
        ScannerQuality.STANDARD -> "Standard"
        ScannerQuality.HIGH -> "High"
        ScannerQuality.MAXIMUM -> "Maximum"
    }
}

fun nextQuality(q: ScannerQuality): ScannerQuality {
    val all = ScannerQuality.entries
    return all[(all.indexOf(q) + 1) % all.size]
}

fun filterLabel(f: ScanFilter): String {
    return when (f) {
        ScanFilter.ORIGINAL -> "Original"
        ScanFilter.ENHANCE -> "Enhance"
        ScanFilter.GRAYSCALE -> "Grayscale"
        ScanFilter.BLACK_AND_WHITE -> "Black and white"
        ScanFilter.MAGIC_COLOUR -> "Magic colour"
    }
}

fun nextFilter(f: ScanFilter): ScanFilter {
    val all = ScanFilter.entries
    return all[(all.indexOf(f) + 1) % all.size]
}

fun modeLabel(m: ScanMode): String = m.name.lowercase().replace('_', ' ')
