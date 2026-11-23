package com.vedica.labs.ind.app.docora.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraExtendedColors
import com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraMotion

/**
 * A quiet placeholder block used while real content is loading.
 *
 * The blocks pulse between two alphas instead of sweeping a bright gradient: it communicates
 * "working" without pulling the eye to every row of a list. Reduced-motion users get a static
 * block, which is why the animation is optional rather than a second component.
 */
@Composable
fun DocoraSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
) {
    val colors = LocalDocoraExtendedColors.current
    val motion = LocalDocoraMotion.current
    val transition = rememberInfiniteTransition(label = "skeleton")
    val animatedAlpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    val alpha = if (motion.reducedMotion) 0.85f else animatedAlpha

    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.skeleton.copy(alpha = alpha)),
    )
}
