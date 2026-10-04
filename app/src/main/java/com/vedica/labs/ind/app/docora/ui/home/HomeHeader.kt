package com.vedica.labs.ind.app.docora.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraSpacing

/**
 * The dashboard's top row: the app name, the settings entry and the search pill (PRD §24, §25).
 *
 * The pill is a real button, not a text field: typing on the home screen would put a keyboard over
 * the dashboard the moment the user tapped it, so the tap hands over to the dedicated search screen
 * where the query, suggestions and filters all live. That keeps search behaviour identical
 * everywhere (PRD §62) instead of maintaining a second, half-featured query state here.
 */
@Composable
fun HomeHeader(
    onSettings: () -> Unit,
    onSearchOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val spacing = LocalDocoraSpacing.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerHigh)
                    .clickable(onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.nav_settings),
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = scheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onSearchOpen),
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = spacing.large,
                    vertical = spacing.medium + spacing.extraSmall,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.home_search_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = spacing.medium),
                )
            }
        }
    }
}
