package com.vedica.labs.ind.app.docora.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.model.AppSettings
import com.vedica.labs.ind.app.docora.core.model.TrashRetention
import com.vedica.labs.ind.app.docora.core.util.FileSizeFormatter

/**
 * Disk space and the search index, grouped together because both describe what Docora keeps on
 * this device: how long the trash holds documents, how big the thumbnail cache may grow, and the
 * two maintenance passes over the index and the search history.
 *
 * Each maintenance action shows a spinner while it runs; the ViewModel serialises them, so a
 * second tap while one is in flight is ignored instead of racing it.
 */
@Composable
fun StorageSection(s: AppSettings, cacheBytes: Long, busy: SettingsBusy, vm: SettingsViewModel) {
    var retentionSheet by remember { mutableStateOf(false) }
    var cacheSheet by remember { mutableStateOf(false) }
    val cacheSummary = stringResource(
        R.string.settings_thumbnail_cache_summary,
        s.thumbnailCacheLimitMb,
        FileSizeFormatter.format(cacheBytes),
    )
    val reindexing = busy == SettingsBusy.REINDEX
    val working = busy == SettingsBusy.WORKING

    SettingsSection(title = stringResource(R.string.settings_section_storage_search)) {
        SettingsRow(
            icon = Icons.Filled.DeleteOutline,
            title = stringResource(R.string.settings_trash_retention),
            subtitle = retentionLabel(s.trashRetention),
            accent = SettingsAccent.ERROR,
            trailing = { SettingsValueTrailing(retentionLabel(s.trashRetention)) },
            onClick = { retentionSheet = true },
        )
        SettingsDivider()
        SettingsRow(
            icon = Icons.Filled.Storage,
            title = stringResource(R.string.settings_thumbnail_cache_limit),
            subtitle = cacheSummary,
            trailing = { SettingsValueTrailing("${s.thumbnailCacheLimitMb} MB") },
            onClick = { cacheSheet = true },
        )
        SettingsDivider()
        SettingsRow(
            icon = Icons.Filled.CleaningServices,
            title = stringResource(R.string.storage_clear_cache),
            subtitle = stringResource(R.string.settings_cache_clear_summary),
            enabled = !working,
            trailing = { if (working) SettingsSpinnerTrailing() },
            onClick = { vm.clearThumbnailCache() },
        )
        SettingsDivider()
        SettingsRow(
            icon = Icons.AutoMirrored.Filled.ManageSearch,
            title = stringResource(R.string.settings_index_rebuild),
            subtitle = stringResource(R.string.settings_index_rebuild_summary),
            accent = SettingsAccent.SECONDARY,
            enabled = !reindexing,
            trailing = { if (reindexing) SettingsSpinnerTrailing() },
            onClick = { vm.rebuildSearchIndex() },
        )
        SettingsDivider()
        SettingsRow(
            icon = Icons.Filled.History,
            title = stringResource(R.string.settings_search_history),
            subtitle = historyLabel(s.recentSearches.size),
            accent = SettingsAccent.SECONDARY,
            enabled = s.recentSearches.isNotEmpty(),
            onClick = { vm.clearRecentSearches() },
        )
    }

    if (retentionSheet) {
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_trash_retention),
            message = stringResource(R.string.settings_trash_retention_summary),
            options = TrashRetention.entries.map { retention ->
                SettingsChoice(retention, retentionLabel(retention))
            },
            selected = s.trashRetention,
            onSelect = { vm.setTrashRetention(it); retentionSheet = false },
            onDismiss = { retentionSheet = false },
        )
    }
    if (cacheSheet) {
        SettingsSliderSheet(
            title = stringResource(R.string.settings_thumbnail_cache_sheet_title),
            message = stringResource(R.string.settings_thumbnail_cache_sheet_message),
            valueLabel = { value -> stringResource(R.string.settings_storage_used, "$value MB") },
            applyLabel = stringResource(R.string.settings_cache_apply),
            current = s.thumbnailCacheLimitMb,
            range = SettingsViewModel.ThumbnailCacheLimits.MIN_MB..AppSettings.MAX_THUMBNAIL_CACHE_MB,
            step = SettingsViewModel.ThumbnailCacheLimits.STEP_MB,
            onApply = { vm.setThumbnailCacheLimit(it) },
            onDismiss = { cacheSheet = false },
        )
    }
}

@Composable
private fun retentionLabel(retention: TrashRetention): String = when (retention) {
    TrashRetention.SEVEN_DAYS -> stringResource(R.string.settings_trash_retention_7)
    TrashRetention.THIRTY_DAYS -> stringResource(R.string.settings_trash_retention_30)
    TrashRetention.NINETY_DAYS -> stringResource(R.string.settings_trash_retention_90)
    TrashRetention.FOREVER -> stringResource(R.string.settings_trash_retention_forever)
}

@Composable
private fun historyLabel(count: Int): String = if (count == 0) {
    stringResource(R.string.settings_search_history_summary_empty)
} else {
    stringResource(R.string.settings_search_history_summary_count, count)
}
