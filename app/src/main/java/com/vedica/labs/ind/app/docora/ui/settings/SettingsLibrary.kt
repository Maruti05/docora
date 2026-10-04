package com.vedica.labs.ind.app.docora.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.model.AppSettings
import com.vedica.labs.ind.app.docora.core.model.SortDirection
import com.vedica.labs.ind.app.docora.core.model.SortOrder
import com.vedica.labs.ind.app.docora.core.model.ViewMode
import com.vedica.labs.ind.app.docora.ui.components.DocoraSectionTitle
import com.vedica.labs.ind.app.docora.ui.components.DocoraSettingsRow

@Composable
fun LibrarySection(s: AppSettings, vm: SettingsViewModel) {
    val scheme = MaterialTheme.colorScheme
    DocoraSectionTitle(stringResource(R.string.settings_section_appearance))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DocoraSettingsRow(
            icon = Icons.Filled.GridView,
            iconTint = scheme.primary,
            iconBackground = scheme.primaryContainer,
            title = stringResource(R.string.action_view_mode),
            subtitle = s.defaultViewMode.name.lowercase().replace('_', ' '),
            onClick = { vm.setViewMode(nextViewMode(s.defaultViewMode)) },
        )
        DocoraSettingsRow(
            icon = Icons.AutoMirrored.Filled.Sort,
            iconTint = scheme.tertiary,
            iconBackground = scheme.tertiaryContainer,
            title = stringResource(R.string.action_sort),
            subtitle = "${s.defaultSortOrder.name.lowercase()} • ${s.defaultSortDirection.name.lowercase()}",
            onClick = {
                val next = if (s.defaultSortDirection == SortDirection.ASCENDING) SortDirection.DESCENDING else SortDirection.ASCENDING
                vm.setSort(s.defaultSortOrder, next)
            },
        )
    }
}

@Composable
fun BackupSection(vm: SettingsViewModel) {
    val scheme = MaterialTheme.colorScheme
    DocoraSectionTitle(stringResource(R.string.settings_section_backup))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DocoraSettingsRow(
            icon = Icons.Filled.Backup,
            iconTint = scheme.primary,
            iconBackground = scheme.primaryContainer,
            title = stringResource(R.string.settings_backup_export),
            subtitle = stringResource(R.string.settings_backup_export_summary),
            onClick = {},
        )
        DocoraSettingsRow(
            icon = Icons.Filled.Backup,
            iconTint = scheme.secondary,
            iconBackground = scheme.secondaryContainer,
            title = stringResource(R.string.settings_backup_import),
            subtitle = stringResource(R.string.settings_backup_import_summary),
            onClick = {},
        )
    }
}

@Composable
fun AboutSection(version: String) {
    val scheme = MaterialTheme.colorScheme
    DocoraSectionTitle(stringResource(R.string.settings_section_about))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DocoraSettingsRow(
            icon = Icons.Filled.Info,
            iconTint = scheme.primary,
            iconBackground = scheme.primaryContainer,
            title = "Version $version",
            subtitle = stringResource(R.string.settings_privacy_summary),
            onClick = null,
        )
        DocoraSettingsRow(
            icon = Icons.Filled.Lock,
            iconTint = scheme.tertiary,
            iconBackground = scheme.tertiaryContainer,
            title = stringResource(R.string.settings_privacy),
            subtitle = stringResource(R.string.settings_privacy_summary),
            onClick = null,
        )
    }
}

fun nextViewMode(m: ViewMode): ViewMode {
    val all = ViewMode.entries
    return all[(all.indexOf(m) + 1) % all.size]
}

fun nextSort(o: SortOrder): SortOrder {
    val all = SortOrder.entries
    return all[(all.indexOf(o) + 1) % all.size]
}
