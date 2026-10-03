package com.vedica.labs.ind.app.docora.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.model.AppSettings
import com.vedica.labs.ind.app.docora.core.model.TrashRetention
import com.vedica.labs.ind.app.docora.core.util.FileSizeFormatter
import com.vedica.labs.ind.app.docora.ui.components.DocoraSectionTitle
import com.vedica.labs.ind.app.docora.ui.components.DocoraSettingsRow
import com.vedica.labs.ind.app.docora.ui.components.DocoraSwitchRow

@Composable
fun StorageSection(s: AppSettings, cacheBytes: Long, vm: SettingsViewModel) {
    val scheme = MaterialTheme.colorScheme
    DocoraSectionTitle(stringResource(R.string.settings_section_storage))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DocoraSettingsRow(
            icon = Icons.Filled.DeleteOutline,
            iconTint = scheme.error,
            iconBackground = scheme.errorContainer,
            title = stringResource(R.string.settings_trash_retention),
            subtitle = retentionLabel(s.trashRetention),
            onClick = { vm.setTrashRetention(nextRetention(s.trashRetention)) },
        )
        DocoraSettingsRow(
            icon = Icons.Filled.Storage,
            iconTint = scheme.primary,
            iconBackground = scheme.primaryContainer,
            title = stringResource(R.string.settings_thumbnail_cache_limit),
            subtitle = "${s.thumbnailCacheLimitMb} MB • ${FileSizeFormatter.format(cacheBytes)} used",
            onClick = { vm.setThumbnailCacheLimit(s.thumbnailCacheLimitMb + 64) },
        )
        DocoraSettingsRow(
            icon = Icons.Filled.Storage,
            iconTint = scheme.tertiary,
            iconBackground = scheme.tertiaryContainer,
            title = stringResource(R.string.storage_clear_cache),
            subtitle = stringResource(R.string.settings_cache_clear_summary),
            onClick = { vm.clearThumbnailCache() },
        )
    }
}

@Composable
fun SearchSection(s: AppSettings, vm: SettingsViewModel) {
    val scheme = MaterialTheme.colorScheme
    DocoraSectionTitle(stringResource(R.string.settings_section_search))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DocoraSwitchRow(
            icon = Icons.Filled.Search,
            iconTint = scheme.primary,
            iconBackground = scheme.primaryContainer,
            title = stringResource(R.string.settings_index_ocr),
            subtitle = null,
            checked = s.indexOcrText,
            onCheckedChange = { vm.setIndexOcr(it) },
        )
        DocoraSwitchRow(
            icon = Icons.Filled.Search,
            iconTint = scheme.secondary,
            iconBackground = scheme.secondaryContainer,
            title = stringResource(R.string.settings_index_document_text),
            subtitle = null,
            checked = s.indexDocumentText,
            onCheckedChange = { vm.setIndexDocuments(it) },
        )
        DocoraSettingsRow(
            icon = Icons.Filled.Search,
            iconTint = scheme.tertiary,
            iconBackground = scheme.tertiaryContainer,
            title = stringResource(R.string.settings_reindex),
            subtitle = stringResource(R.string.settings_reindex_summary),
            onClick = { vm.rebuildSearchIndex() },
        )
    }
}

fun retentionLabel(r: TrashRetention): String {
    return when (r) {
        TrashRetention.SEVEN_DAYS -> "7 days"
        TrashRetention.THIRTY_DAYS -> "30 days"
        TrashRetention.NINETY_DAYS -> "90 days"
        TrashRetention.FOREVER -> "Forever"
    }
}

fun nextRetention(r: TrashRetention): TrashRetention {
    val all = TrashRetention.entries
    return all[(all.indexOf(r) + 1) % all.size]
}
