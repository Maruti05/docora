package com.vedica.labs.ind.app.docora.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.model.DashboardStats
import com.vedica.labs.ind.app.docora.core.util.FileSizeFormatter

/**
 * The screen's opening card: how big the library is, right under the title, plus the privacy
 * promise that frames every setting below it.
 */
@Composable
fun SettingsHeroCard(stats: DashboardStats) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_hero_title),
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onPrimaryContainer,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeroStat(
                    icon = Icons.Filled.Description,
                    value = "${stats.totalDocuments}",
                    modifier = Modifier.weight(1f),
                )
                HeroStat(
                    icon = Icons.Filled.PhotoCamera,
                    value = "${stats.scans}",
                    modifier = Modifier.weight(1f),
                )
                HeroStat(
                    icon = Icons.Filled.Favorite,
                    value = "${stats.favorites}",
                    modifier = Modifier.weight(1f),
                )
            }
            val documents = pluralStringResource(
                R.plurals.plural_documents,
                stats.totalDocuments,
                stats.totalDocuments,
            )
            val scans = pluralStringResource(
                R.plurals.plural_scans,
                stats.scans,
                stats.scans,
            )
            Text(
                text = "$documents · $scans · ${FileSizeFormatter.format(stats.totalBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onPrimaryContainer,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = stringResource(R.string.settings_privacy_badge_icon),
                    tint = scheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = stringResource(R.string.settings_privacy_badge),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun HeroStat(
    icon: ImageVector,
    value: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(scheme.primary.copy(alpha = 0.12f))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = scheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = scheme.onPrimaryContainer,
        )
    }
}
