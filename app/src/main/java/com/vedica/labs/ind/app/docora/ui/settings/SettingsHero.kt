package com.vedica.labs.ind.app.docora.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.model.DashboardStats
import com.vedica.labs.ind.app.docora.core.util.FileSizeFormatter

/**
 * The screen's opening summary: how big the library is, and the privacy promise that frames every
 * setting below it.
 *
 * Deliberately the same neutral surface and hairline dividers as the Home overview, so Settings
 * reads as part of the same product rather than as a separate coloured dashboard.
 */
@Composable
fun SettingsHeroCard(stats: DashboardStats) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_hero_title),
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeroStat(
                    icon = Icons.Filled.Description,
                    value = stats.totalDocuments.toString(),
                    label = stringResource(R.string.home_stat_documents),
                    modifier = Modifier.weight(1f),
                )
                HeroDivider()
                HeroStat(
                    icon = Icons.Filled.PhotoCamera,
                    value = stats.scans.toString(),
                    label = stringResource(R.string.home_stat_scans),
                    modifier = Modifier.weight(1f),
                )
                HeroDivider()
                HeroStat(
                    icon = Icons.Filled.Favorite,
                    value = stats.favorites.toString(),
                    label = stringResource(R.string.home_stat_favourites),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(
                    R.string.home_hero_storage,
                    FileSizeFormatter.format(stats.totalBytes),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = stringResource(R.string.settings_privacy_badge_icon),
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.settings_privacy_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HeroStat(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = scheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HeroDivider() {
    VerticalDivider(
        modifier = Modifier.height(42.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
    )
}
