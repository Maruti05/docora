package com.vedica.labs.ind.app.docora.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Height of the bar's interactive content, excluding the system navigation inset. */
val BottomBarHeight: Dp = 76.dp

/** Kept for layout maths in the app shell: the docked bar sits flush with the screen edge. */
val BottomBarBottomGap: Dp = 0.dp

/** Bottom chrome inset published by the app shell so tab content can scroll clear of the bar. */
val LocalBottomBarInset = staticCompositionLocalOf { 0.dp }

/** One destination in the bottom navigation bar. */
data class DocoraBottomBarItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

/**
 * Standard, docked bottom navigation bar (PRD §26).
 *
 * Unlike a floating pill, this bar is anchored edge to edge at the bottom of the screen: it owns
 * the system navigation inset, draws a hairline separator instead of elevation, and shows an icon
 * stacked above its label for every destination. That is the familiar Android pattern, so the
 * primary navigation reads as chrome rather than as another card in the content.
 *
 * The "advanced" part is the selection motion. A single capsule behind the icons glides between
 * destinations on a critically damped spring instead of being re-created under the new item, so a
 * jump from Home to Settings is one continuous movement. On top of that each icon cross-fades
 * between its outlined and filled variants, bounces once as it takes focus, and the label
 * weights up. A light haptic tick confirms the switch. All of this is short enough that it never
 * delays the destination change, and the bar respects the reduced-motion preference.
 */
@Composable
fun DocoraBottomBar(
    items: List<DocoraBottomBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val scheme = MaterialTheme.colorScheme

    // One shared float drives the sliding capsule; re-targeting it mid-flight keeps the movement
    // continuous when the user taps several tabs in quick succession.
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "bottomBarSelection",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = BarTopCornerRadius, topEnd = BarTopCornerRadius),
        color = scheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // The Surface paints behind this padding, so the bar background also fills the
                // gesture/navigation area instead of leaving a visible strip below it.
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            HorizontalDivider(
                thickness = 1.dp,
                color = scheme.outlineVariant.copy(alpha = 0.6f),
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BottomBarHeight),
            ) {
                val itemWidth = maxWidth / items.size.toFloat()
                val capsuleWidth = IndicatorWidth.coerceAtMost(itemWidth - IndicatorHorizontalInset * 2f)
                val capsuleX = itemWidth * animatedIndex +
                    (itemWidth - capsuleWidth) / 2f

                // Sibling of the icons so it can travel across item boundaries.
                Box(
                    modifier = Modifier
                        .offset(x = capsuleX, y = IndicatorTop)
                        .size(width = capsuleWidth, height = IndicatorHeight)
                        .clip(RoundedCornerShape(IndicatorHeight / 2f))
                        .background(scheme.primaryContainer),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .selectableGroup(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, item ->
                        BottomBarItem(
                            item = item,
                            selected = index == selectedIndex,
                            onClick = { onSelect(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.BottomBarItem(
    item: DocoraBottomBarItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val iconScale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.86f
            selected -> 1.1f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "bottomBarIconScale",
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        animationSpec = tween(220),
        label = "bottomBarIconColor",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) scheme.onSurface else scheme.onSurfaceVariant.copy(alpha = 0.78f),
        animationSpec = tween(220),
        label = "bottomBarLabelColor",
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                // No ripple: the only feedback is the icon itself (capsule, bounce, tint).
                // A rectangular ripple/state layer was painting a large tinted block over the
                // whole item, which read as a stray shadow box rather than as navigation.
                indication = null,
                role = Role.Tab,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .width(IndicatorWidth)
                .height(IndicatorHeight),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = if (selected) item.selectedIcon else item.icon,
                transitionSpec = {
                    (
                        scaleIn(
                            animationSpec = tween(180),
                            initialScale = 0.62f,
                        ) + fadeIn(tween(180))
                        ) togetherWith (
                        scaleOut(
                            animationSpec = tween(120),
                            targetScale = 0.62f,
                        ) + fadeOut(tween(120))
                        )
                },
                label = "bottomBarIcon",
            ) { icon ->
                Icon(
                    imageVector = icon,
                    // The visible label already names the destination, so the icon is decorative.
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** Narration-free labels stay readable at the default size; the capsule hugs the icon. */
private val IndicatorWidth: Dp = 58.dp
private val IndicatorHeight: Dp = 30.dp

/** Keeps the capsule centred on the icon box: (76 - 30 - 2 - 16) / 2. */
private val IndicatorTop: Dp = 14.dp
private val IndicatorHorizontalInset: Dp = 8.dp

/** Soft top corners so the docked bar reads as a surface, not as a hard screen edge. */
private val BarTopCornerRadius: Dp = 24.dp
