package com.vedica.labs.ind.app.docora.ui.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Motion tokens (PRD §40).
 *
 * Animations must communicate a state change and must never delay interaction, so the
 * durations are short and the springs are critically damped. Reduced-motion handling
 * collapses these to near-instant values rather than removing animation entirely, which
 * keeps state changes visible without movement.
 */
data class DocoraMotion(
    val reducedMotion: Boolean = false,
) {
    private val factor: Float get() = if (reducedMotion) 0.15f else 1f

    /** Small state changes: chip selection, icon swaps, favourite toggles. */
    val quick: Int get() = (120 * factor).toInt().coerceAtLeast(1)

    /** Standard transitions: content swaps, list item appearance, sheet content. */
    val standard: Int get() = (220 * factor).toInt().coerceAtLeast(1)

    /** Screen level transitions and shared-element motion. */
    val emphasized: Int get() = (320 * factor).toInt().coerceAtLeast(1)

    /** Ambient/decorative loops (hero shimmer, processing pulse). Never used for navigation. */
    val ambient: Int get() = (1_400 * factor).toInt().coerceAtLeast(1)

    val standardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val emphasizedEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    fun <T> quickSpec(): FiniteAnimationSpec<T> = tween(durationMillis = quick, easing = standardEasing)

    fun <T> standardSpec(): FiniteAnimationSpec<T> = tween(durationMillis = standard, easing = standardEasing)

    fun <T> emphasizedSpec(): FiniteAnimationSpec<T> = tween(durationMillis = emphasized, easing = emphasizedEasing)

    /** Spring used for selection and drag interactions: no visible overshoot. */
    fun <T> selectionSpring(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.9f,
        stiffness = 900f * factor,
    )

    /** Spring for content that appears or changes size (list items, expanding panels). */
    fun <T> spatialSpring(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.82f,
        stiffness = 420f * factor,
    )

    /** Heavier spring for large surfaces: sheets, hero cards, review panels. */
    fun <T> surfaceSpring(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.86f,
        stiffness = 260f * factor,
    )

    /**
     * Stagger offset for an item at [index] in a list that is animating in.
     * Capped so a long list never takes a noticeable amount of time to finish appearing.
     */
    fun stagger(index: Int, stepMillis: Int = 45, maxMillis: Int = 320): Int {
        if (reducedMotion) return 0
        return (index * stepMillis).coerceAtMost(maxMillis)
    }
}

val LocalDocoraMotion = staticCompositionLocalOf { DocoraMotion() }
