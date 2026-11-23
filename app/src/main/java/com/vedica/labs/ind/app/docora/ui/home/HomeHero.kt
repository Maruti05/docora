package com.vedica.labs.ind.app.docora.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.util.FileSizeFormatter

/**
 * A quiet overview of the library: three numbers, no decoration.
 *
 * This replaces the row of tinted stat chips. Colour was doing no work there - the numbers are
 * the information - so the card is a single surface divided by hairlines, with values in the
 * foreground colour and labels in the muted one.
 */
@Composable
fun HomeOverviewCard(
    total: Int,
    favourites: Int,
    bytes: Long,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OverviewStat(
                value = total.toString(),
                label = stringResource(R.string.home_stat_documents),
                modifier = Modifier.weight(1f),
            )
            OverviewDivider()
            OverviewStat(
                value = favourites.toString(),
                label = stringResource(R.string.home_stat_favourites),
                modifier = Modifier.weight(1f),
            )
            OverviewDivider()
            OverviewStat(
                value = FileSizeFormatter.format(bytes),
                label = stringResource(R.string.collection_storage),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun OverviewStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
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
private fun OverviewDivider() {
    VerticalDivider(
        modifier = Modifier.height(30.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
    )
}
