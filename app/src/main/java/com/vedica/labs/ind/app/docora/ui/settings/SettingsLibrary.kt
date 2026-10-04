package com.vedica.labs.ind.app.docora.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.model.AppSettings
import com.vedica.labs.ind.app.docora.core.model.SortDirection
import com.vedica.labs.ind.app.docora.core.model.SortOrder
import com.vedica.labs.ind.app.docora.core.model.ViewMode
import com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraSpacing

/**
 * The shape of the library itself: how the browser opens and in what order.
 *
 * These are the same defaults the documents screen reads on launch, so what the user picks here
 * is exactly what they see there. Sorting is a single row holding both the key and the direction,
 * because the two only make sense together.
 */
@Composable
fun LibrarySection(s: AppSettings, vm: SettingsViewModel) {
    var viewSheet by remember { mutableStateOf(false) }
    var sortSheet by remember { mutableStateOf(false) }
    val orderLabel = sortOrderLabel(s.defaultSortOrder)

    SettingsSection(
        title = stringResource(R.string.settings_section_library),
        subtitle = stringResource(R.string.settings_section_library_summary),
    ) {
        SettingsRow(
            icon = viewModeIcon(s.defaultViewMode),
            title = stringResource(R.string.settings_view_mode),
            subtitle = viewModeSummary(s.defaultViewMode),
            trailing = { SettingsValueTrailing(viewModeLabel(s.defaultViewMode)) },
            onClick = { viewSheet = true },
        )
        SettingsDivider()
        SettingsRow(
            icon = Icons.AutoMirrored.Filled.Sort,
            title = stringResource(R.string.settings_sorting),
            subtitle = sortDirectionSummary(s.defaultSortDirection),
            accent = SettingsAccent.SECONDARY,
            trailing = { SettingsValueTrailing(orderLabel) },
            onClick = { sortSheet = true },
        )
    }

    if (viewSheet) {
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_view_mode),
            message = stringResource(R.string.settings_view_mode_summary),
            options = ViewMode.entries.map { mode ->
                SettingsChoice(mode, viewModeLabel(mode), viewModeSummary(mode))
            },
            selected = s.defaultViewMode,
            onSelect = { vm.setViewMode(it); viewSheet = false },
            onDismiss = { viewSheet = false },
        )
    }
    if (sortSheet) {
        SortSheet(
            order = s.defaultSortOrder,
            direction = s.defaultSortDirection,
            onSelectOrder = { vm.setSort(it, s.defaultSortDirection) },
            onSelectDirection = { vm.setSort(s.defaultSortOrder, it) },
            onDismiss = { sortSheet = false },
        )
    }
}

/**
 * One sheet for the whole sorting decision: pick the key, then the direction, without leaving the
 * sheet. Changes apply immediately so the row underneath always mirrors what was chosen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(
    order: SortOrder,
    direction: SortDirection,
    onSelectOrder: (SortOrder) -> Unit,
    onSelectDirection: (SortDirection) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = LocalDocoraSpacing.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
        ) {
            Text(
                text = stringResource(R.string.settings_sorting),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.settings_sorting_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.small))
            SortOrder.entries.forEach { entry ->
                SortOptionRow(
                    title = sortOrderLabel(entry),
                    selected = entry == order,
                    onClick = { onSelectOrder(entry) },
                )
            }
            Spacer(Modifier.height(spacing.small))
            HorizontalDivider()
            Spacer(Modifier.height(spacing.small))
            Text(
                text = stringResource(R.string.settings_sort_direction),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            SortDirection.entries.forEach { entry ->
                SortOptionRow(
                    title = sortDirectionLabel(entry),
                    summary = sortDirectionSummary(entry),
                    selected = entry == direction,
                    onClick = { onSelectDirection(entry) },
                )
            }
            Spacer(Modifier.height(spacing.extraLarge))
        }
    }
}

/** One selectable line inside [SortSheet]; the selected line is tinted and checked. */
@Composable
private fun SortOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    summary: String? = null,
) {
    val spacing = LocalDocoraSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Spacer(Modifier.width(spacing.medium))
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private fun viewModeIcon(mode: ViewMode): ImageVector = when (mode) {
    ViewMode.LIST -> Icons.AutoMirrored.Filled.ViewList
    ViewMode.COMPACT -> Icons.Filled.ViewModule
    ViewMode.GRID -> Icons.Filled.GridView
    ViewMode.LARGE_GRID -> Icons.Filled.GridView
}

@Composable
private fun viewModeLabel(mode: ViewMode): String = when (mode) {
    ViewMode.LIST -> stringResource(R.string.settings_view_list)
    ViewMode.COMPACT -> stringResource(R.string.settings_view_compact)
    ViewMode.GRID -> stringResource(R.string.settings_view_grid)
    ViewMode.LARGE_GRID -> stringResource(R.string.settings_view_large)
}

@Composable
private fun viewModeSummary(mode: ViewMode): String = when (mode) {
    ViewMode.LIST -> stringResource(R.string.settings_view_list_summary)
    ViewMode.COMPACT -> stringResource(R.string.settings_view_compact_summary)
    ViewMode.GRID -> stringResource(R.string.settings_view_grid_summary)
    ViewMode.LARGE_GRID -> stringResource(R.string.settings_view_large_summary)
}

@Composable
private fun sortOrderLabel(order: SortOrder): String = when (order) {
    SortOrder.NAME -> stringResource(R.string.settings_sort_name)
    SortOrder.DATE_MODIFIED -> stringResource(R.string.settings_sort_date_modified)
    SortOrder.DATE_CREATED -> stringResource(R.string.settings_sort_date_created)
    SortOrder.SIZE -> stringResource(R.string.settings_sort_size)
    SortOrder.TYPE -> stringResource(R.string.settings_sort_type)
    SortOrder.LAST_OPENED -> stringResource(R.string.settings_sort_last_opened)
}

@Composable
private fun sortDirectionLabel(direction: SortDirection): String = when (direction) {
    SortDirection.ASCENDING -> stringResource(R.string.settings_sort_ascending)
    SortDirection.DESCENDING -> stringResource(R.string.settings_sort_descending)
}

@Composable
private fun sortDirectionSummary(direction: SortDirection): String = when (direction) {
    SortDirection.ASCENDING -> stringResource(R.string.settings_sort_ascending_summary)
    SortDirection.DESCENDING -> stringResource(R.string.settings_sort_descending_summary)
}
