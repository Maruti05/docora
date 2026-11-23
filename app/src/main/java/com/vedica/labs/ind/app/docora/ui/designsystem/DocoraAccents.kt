package com.vedica.labs.ind.app.docora.ui.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * A reusable accent: the vivid colour that tints an icon, plus the wash behind it.
 *
 * Accents are the app's "colourful icon" system. Every tile, rail and stat chip draws its glyph
 * on top of a faint wash of the same hue, so colour always carries meaning (a category, a file
 * type, a quick action) instead of being decoration.
 */
data class DocoraAccent(
    val tint: Color,
    val container: Color,
) {
    companion object {
        /** A bubble that reads on both themes for any mid-tone tint. */
        fun of(tint: Color): DocoraAccent = DocoraAccent(
            tint = tint,
            container = tint.copy(alpha = 0.16f),
        )
    }
}

/**
 * The accent palette, resolved for the active theme.
 *
 * Light mode uses saturated mid-tones; dark mode uses lifted, brighter variants so the glyphs
 * stay legible on deep surfaces. Composables read the palette through [LocalDocoraAccents]
 * instead of hardcoding hex values, which keeps every screen on the same colour language.
 */
data class DocoraAccentPalette(
    val blue: DocoraAccent,
    val cyan: DocoraAccent,
    val teal: DocoraAccent,
    val green: DocoraAccent,
    val lime: DocoraAccent,
    val amber: DocoraAccent,
    val orange: DocoraAccent,
    val red: DocoraAccent,
    val pink: DocoraAccent,
    val purple: DocoraAccent,
    val indigo: DocoraAccent,
    val slate: DocoraAccent,
) {
    companion object {
        internal val Light = DocoraAccentPalette(
            blue = DocoraAccent(Color(0xFF2563EB), Color(0x292563EB)),
            cyan = DocoraAccent(Color(0xFF0891B2), Color(0x290891B2)),
            teal = DocoraAccent(Color(0xFF0D9488), Color(0x290D9488)),
            green = DocoraAccent(Color(0xFF16A34A), Color(0x2916A34A)),
            lime = DocoraAccent(Color(0xFF65A30D), Color(0x2965A30D)),
            amber = DocoraAccent(Color(0xFFD97706), Color(0x29D97706)),
            orange = DocoraAccent(Color(0xFFEA580C), Color(0x29EA580C)),
            red = DocoraAccent(Color(0xFFDC2626), Color(0x29DC2626)),
            pink = DocoraAccent(Color(0xFFDB2777), Color(0x29DB2777)),
            purple = DocoraAccent(Color(0xFF7C3AED), Color(0x297C3AED)),
            indigo = DocoraAccent(Color(0xFF4F46E5), Color(0x294F46E5)),
            slate = DocoraAccent(Color(0xFF475569), Color(0x29475569)),
        )

        internal val Dark = DocoraAccentPalette(
            blue = DocoraAccent(Color(0xFF60A5FA), Color(0x2E60A5FA)),
            cyan = DocoraAccent(Color(0xFF22D3EE), Color(0x2E22D3EE)),
            teal = DocoraAccent(Color(0xFF2DD4BF), Color(0x2E2DD4BF)),
            green = DocoraAccent(Color(0xFF4ADE80), Color(0x2E4ADE80)),
            lime = DocoraAccent(Color(0xFFA3E635), Color(0x2EA3E635)),
            amber = DocoraAccent(Color(0xFFFBBF24), Color(0x2EFBBF24)),
            orange = DocoraAccent(Color(0xFFFB923C), Color(0x2EFB923C)),
            red = DocoraAccent(Color(0xFFF87171), Color(0x2EF87171)),
            pink = DocoraAccent(Color(0xFFF472B6), Color(0x2EF472B6)),
            purple = DocoraAccent(Color(0xFFA78BFA), Color(0x2EA78BFA)),
            indigo = DocoraAccent(Color(0xFF818CF8), Color(0x2E818CF8)),
            slate = DocoraAccent(Color(0xFF94A3B8), Color(0x2E94A3B8)),
        )
    }
}

/**
 * Resolves the palette from the current theme, so a dynamic-colour swap or theme change
 * keeps every accent consistent without call sites knowing which mode is active.
 */
@Composable
fun docoraAccentPalette(): DocoraAccentPalette =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        DocoraAccentPalette.Dark
    } else {
        DocoraAccentPalette.Light
    }

val LocalDocoraAccents = staticCompositionLocalOf { DocoraAccentPalette.Light }
