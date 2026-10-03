package com.vedica.labs.ind.app.docora.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.model.ThemeMode
import com.vedica.labs.ind.app.docora.ui.components.DocoraSectionTitle

@Composable
fun AppearanceSection(selected: ThemeMode, dynamic: Boolean, onTheme: (ThemeMode) -> Unit, onDynamic: (Boolean) -> Unit) {
    DocoraSectionTitle(stringResource(R.string.settings_section_appearance))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeChip(
            label = stringResource(R.string.settings_theme_system),
            selected = selected == ThemeMode.SYSTEM,
            onClick = { onTheme(ThemeMode.SYSTEM) },
            modifier = Modifier.weight(1f),
        )
        ThemeChip(
            label = stringResource(R.string.settings_theme_light),
            selected = selected == ThemeMode.LIGHT,
            onClick = { onTheme(ThemeMode.LIGHT) },
            modifier = Modifier.weight(1f),
        )
        ThemeChip(
            label = stringResource(R.string.settings_theme_dark),
            selected = selected == ThemeMode.DARK,
            onClick = { onTheme(ThemeMode.DARK) },
            modifier = Modifier.weight(1f),
        )
    }
    androidx.compose.material3.Switch(
        checked = dynamic,
        onCheckedChange = onDynamic,
        modifier = Modifier.padding(vertical = 4.dp),
    )
    Text(
        stringResource(R.string.settings_dynamic_colour_summary),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null) }
        } else {
            { Icon(Icons.Filled.Palette, contentDescription = null) }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = modifier,
    )
}

@Composable
fun SettingsSectionGap() {
    Column(Modifier.padding(vertical = 2.dp)) {}
}
