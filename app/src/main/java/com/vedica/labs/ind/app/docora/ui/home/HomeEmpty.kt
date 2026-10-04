package com.vedica.labs.ind.app.docora.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraSpacing

/**
 * The first-run state of the library.
 *
 * It stays on the dashboard rather than replacing it, so the sections a user will soon fill are
 * already visible underneath, and it offers both ways in - capture a page, or bring in files that
 * already exist - instead of a single dead-end button.
 */
@Composable
fun EmptyLibraryCard(
    onScan: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val spacing = LocalDocoraSpacing.current
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.extraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(scheme.primaryContainer, scheme.tertiaryContainer))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = scheme.onPrimaryContainer,
                    modifier = Modifier.size(30.dp),
                )
            }
            Text(
                text = stringResource(R.string.empty_library_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.empty_library_message),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(spacing.small))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                Card(
                    onClick = onScan,
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = spacing.large, vertical = spacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DocumentScanner,
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(spacing.small))
                        Text(stringResource(R.string.action_scan_document), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Card(
                    onClick = onImport,
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = scheme.secondaryContainer,
                        contentColor = scheme.onSecondaryContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = spacing.large, vertical = spacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.UploadFile,
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(spacing.small))
                        Text(stringResource(R.string.action_import_files), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
