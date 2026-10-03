package com.vedica.labs.ind.app.docora.ui.designsystem

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Brand gradients.
 *
 * Gradient brushes cannot be expressed as plain Material 3 tokens (a [Brush] is built from the
 * colour scheme, not stored in it), so they live in their own composition local and are derived
 * once per theme change in [DocoraTheme]. Screens then ask for a *meaning* (hero card, camera
 * scrim) instead of rebuilding a gradient from raw colours, which is what keeps the palette
 * consistent when the theme is retuned.
 */
data class DocoraGradients(
    /** Primary marketing surface: the home hero card. */
    val hero: Brush,
    /** Soft radial highlight layered over [hero] so the card has depth. */
    val heroGlow: Brush,
    /** Fill for selected/featured chips and tiles. */
    val featured: Brush,
    /** Vertical shade laid over the camera preview, top and bottom. */
    val scannerTopScrim: Brush,
    val scannerBottomScrim: Brush,
    /** Sheen swept across a skeleton block while content loads. */
    val skeletonSheen: Brush,
)

/** Defaults are built from the light palette so a preview can render without a themed parent. */
val LocalDocoraGradients = staticCompositionLocalOf {
    DocoraGradients(
        hero = Brush.linearGradient(listOf(Color(0xFF1B4636), Color(0xFF3B6470))),
        heroGlow = Brush.radialGradient(listOf(Color(0x4D9BE8C4), Color(0x009BE8C4))),
        featured = Brush.linearGradient(listOf(Color(0xFF1B4636), Color(0xFF2C6E49))),
        scannerTopScrim = Brush.verticalGradient(listOf(Color(0xB3000000), Color(0x00000000))),
        scannerBottomScrim = Brush.verticalGradient(listOf(Color(0x00000000), Color(0xCC000000))),
        skeletonSheen = Brush.horizontalGradient(
            listOf(Color(0x00FFFFFF), Color(0x66FFFFFF), Color(0x00FFFFFF)),
        ),
    )
}
