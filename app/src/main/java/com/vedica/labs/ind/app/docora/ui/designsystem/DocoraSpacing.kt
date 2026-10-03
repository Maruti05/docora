package com.vedica.labs.ind.app.docora.ui.designsystem

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing scale.
 *
 * Every gap in Docora comes from this scale. Using a 4dp base with a deliberately short
 * ladder keeps layouts consistent and prevents the "almost aligned" look that appears
 * when screens invent their own values (PRD §23, §39).
 */
data class DocoraSpacing(
    val none: Dp = 0.dp,
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 16.dp,
    val extraLarge: Dp = 24.dp,
    val huge: Dp = 32.dp,
    val giant: Dp = 48.dp,
) {
    /** Horizontal page padding used by every top-level screen. */
    val screenHorizontal: Dp = 20.dp

    /** Vertical rhythm between major sections on the dashboard. */
    val sectionGap: Dp = 28.dp

    /** Minimum touch target required by accessibility guidance. */
    val minTouchTarget: Dp = 48.dp

    /** Height of a grid tile's thumbnail area for the default grid. */
    val gridThumbnailHeight: Dp = 148.dp

    /** Height of a list row's thumbnail. */
    val listThumbnailSize: Dp = 56.dp

    /** Vertical page padding above and below scrolling screen content. */
    val screenVertical: Dp = 12.dp

    /** Gap between cards in a grid, and between stacked list rows. */
    val gridGap: Dp = 12.dp

    /** Inner padding of a card, sheet section or dialog body. */
    val cardPadding: Dp = 16.dp

    /** Gap between chips inside a horizontally scrolling chip row. */
    val chipGap: Dp = 8.dp

    /** Bottom scroll padding that keeps the last row clear of the navigation bar. */
    val bottomScrollInset: Dp = 88.dp

    /** Height of the scanner's capture controls dock. */
    val captureDockHeight: Dp = 168.dp
}

val LocalDocoraSpacing = staticCompositionLocalOf { DocoraSpacing() }
