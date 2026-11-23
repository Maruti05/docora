package com.vedica.labs.ind.app.docora.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.model.AppSettings

/**
 * Full-text search controls.
 *
 * The two switches decide what the background enricher feeds into the FTS index: text embedded in
 * PDFs/text files, and text recognised from scans. They are separate because OCR is the expensive
 * pass - a user who only cares about digital documents can turn it off and keep search fast.
 * Rebuilding and clearing history live here too, next to the data they affect.
 */
@Composable
fun SearchSection(s: AppSettings, busy: SettingsBusy, vm: SettingsViewModel) {
    val reindexing = busy == SettingsBusy.REINDEX

    SettingsSection(
        title = stringResource(R.string.settings_section_search),
        subtitle = stringResource(R.string.settings_section_search_summary),
    ) {
        SettingsSwitchRow(
            icon = Icons.Filled.TextFields,
            title = stringResource(R.string.settings_index_document_text),
            subtitle = stringResource(R.string.settings_index_document_text_summary),
            checked = s.indexDocumentText,
            onCheckedChange = vm::setIndexDocumentText,
        )
        SettingsDivider()
        SettingsSwitchRow(
            icon = Icons.AutoMirrored.Filled.ManageSearch,
            title = stringResource(R.string.settings_index_ocr),
            subtitle = stringResource(R.string.settings_index_ocr_summary),
            checked = s.indexOcrText,
            onCheckedChange = vm::setIndexOcrText,
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
}

@Composable
private fun historyLabel(count: Int): String = if (count == 0) {
    stringResource(R.string.settings_search_history_summary_empty)
} else {
    stringResource(R.string.settings_search_history_summary_count, count)
}
