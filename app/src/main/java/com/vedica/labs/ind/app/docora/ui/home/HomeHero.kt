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
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.util.FileSizeFormatter
import com.vedica.labs.ind.app.docora.ui.designsystem.DocoraThemeTokens
import com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraSpacing

/**
 * The dashboard hero.
 *
 * It carries the two numbers a user actually wants at a glance - how many documents and how much
 * space they take - plus the two actions that create documents, so the most common job (add
 * something, or see what you have) is reachable without scrolling. The gradient and the glow come
 * from the theme's gradient tokens rather than raw colours, so the card survives a dynamic-colour
 * palette swap instead of clashing with it.
 *
 * Content colours are derived from `onPrimary`, which is by construction the readable foreground for
 * the primary-toned gradient in both light and dark themes.
 */
@Composable
fun HomeHero(
    total: Int,
    bytes: Long,
    onScan: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val spacing = LocalDocoraSpacing.current
    val gradients = DocoraThemeTokens.gradients
    val onHero = scheme.onPrimary
    val isEmpty = total == 0

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradients.hero)
                .background(gradients.heroGlow)
                .padding(spacing.extraLarge),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
                Text(
                    text = stringResource(R.string.home_hero_greeting),
                    style = MaterialTheme.typography.labelLarge,
                    color = onHero.copy(alpha = 0.85f),
                )
                Text(
                    text = if (isEmpty) {
                        stringResource(R.string.empty_library_title)
                    } else {
                        pluralStringResource(R.plurals.plural_documents, total, total)
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = onHero,
                )
                Text(
                    text = if (isEmpty) {
                        stringResource(R.string.empty_library_message)
                    } else {
                        stringResource(R.string.home_hero_storage, FileSizeFormatter.format(bytes))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = onHero.copy(alpha = 0.85f),
                )
                Spacer(Modifier.size(spacing.medium))
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                    HeroAction(
                        label = stringResource(R.string.action_scan_document),
                        icon = Icons.Filled.DocumentScanner,
                        filled = true,
                        onClick = onScan,
                    )
                    HeroAction(
                        label = stringResource(R.string.action_import_files),
                        icon = Icons.Filled.UploadFile,
                        filled = false,
                        onClick = onImport,
                    )
                }
            }
        }
    }
}

/** A pill action laid over the hero gradient; [filled] marks the single primary action. */
@Composable
private fun HeroAction(
    label: String,
    icon: ImageVector,
    filled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val spacing = LocalDocoraSpacing.current
    val content = if (filled) scheme.primary else scheme.onPrimary
    val container = if (filled) scheme.onPrimary else scheme.onPrimary.copy(alpha = 0.18f)
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = spacing.medium + spacing.extraSmall,
                vertical = spacing.small + spacing.extraSmall,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(spacing.small))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
