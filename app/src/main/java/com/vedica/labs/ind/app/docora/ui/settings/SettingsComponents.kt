package com.vedica.labs.ind.app.docora.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vedica.labs.ind.app.docora.ui.designsystem.DocoraThemeTokens
import com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraSpacing

/**
 * The building blocks every settings group is assembled from.
 *
 * One [SettingsSection] is a tappable header plus a [SettingsGroup] card holding the rows of that
 * topic. Rows are separated by [SettingsDivider]; a row is either a [SettingsRow] (opens a sheet or
 * runs an action) or a [SettingsSwitchRow] (flips a boolean in place). Every value in the screen
 * comes from the design system (PRD §39), so the screen never invents its own spacing or colour.
 */
enum class SettingsAccent {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    ERROR,
}

/** Container/on-container pair for a row's icon bubble, resolved from the live colour scheme. */
@Composable
private fun accentColors(accent: SettingsAccent): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (accent) {
        SettingsAccent.PRIMARY -> scheme.primaryContainer to scheme.onPrimaryContainer
        SettingsAccent.SECONDARY -> scheme.secondaryContainer to scheme.onSecondaryContainer
        SettingsAccent.TERTIARY -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        SettingsAccent.ERROR -> scheme.errorContainer to scheme.onErrorContainer
    }
}

/**
 * A titled group of rows.
 *
 * The header folds the group away, so the screen stays short while every option is still one tap
 * from here. The chevron rotates with the same spring the rest of the app uses, and the expansion
 * itself fades and slides so it reads as the same card growing rather than content being swapped.
 */
@Composable
fun SettingsSection(
    title: String,
    subtitle: String? = null,
    collapsible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = LocalDocoraSpacing.current
    val motion = DocoraThemeTokens.motion
    var expanded by rememberSaveable(title) { mutableStateOf(true) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = motion.selectionSpring(),
    )

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (collapsible) {
                        Modifier.clickable(role = Role.Button) { expanded = !expanded }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = spacing.extraSmall, vertical = spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (collapsible) {
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp).rotate(chevronRotation),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(motion.standardSpec()) + expandVertically(motion.surfaceSpring()),
            exit = fadeOut(motion.quickSpec()) + shrinkVertically(motion.surfaceSpring()),
        ) {
            SettingsGroup(content = content)
        }
    }
}

/** One rounded card holding the rows of a single topic. */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(content = content)
    }
}

@Composable
fun ColumnScope.SettingsDivider() {
    val spacing = LocalDocoraSpacing.current
    HorizontalDivider(modifier = Modifier.padding(horizontal = spacing.large))
}

@Composable
fun ColumnScope.SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    accent: SettingsAccent = SettingsAccent.PRIMARY,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val spacing = LocalDocoraSpacing.current
    val (bubble, onBubble) = accentColors(accent)
    val alpha = if (enabled) 1f else 0.45f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null && enabled) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = spacing.large, vertical = spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(bubble.copy(alpha = alpha)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = onBubble.copy(alpha = alpha),
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(spacing.medium))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(spacing.extraSmall / 2))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(spacing.small))
            trailing()
        }
    }
}

/** Trailing for a picker row: the current value plus a chevron that says "this opens". */
@Composable
fun SettingsValueTrailing(value: String) {
    val spacing = LocalDocoraSpacing.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(spacing.extraSmall))
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Trailing for an in-flight action such as clearing the cache or writing a backup. */
@Composable
fun SettingsSpinnerTrailing() {
    CircularProgressIndicator(
        modifier = Modifier.size(22.dp),
        strokeWidth = 2.5.dp,
    )
}

/**
 * A boolean row: the whole row toggles, so the switch is a status indicator as well as a control.
 * The row is disabled with an explanation when [enabled] is false - a switch that silently does
 * nothing is worse than one that explains itself.
 */
@Composable
fun ColumnScope.SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    accent: SettingsAccent = SettingsAccent.PRIMARY,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        accent = accent,
        enabled = enabled,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
        },
        onClick = if (enabled) {
            { onCheckedChange(!checked) }
        } else {
            null
        },
    )
}
