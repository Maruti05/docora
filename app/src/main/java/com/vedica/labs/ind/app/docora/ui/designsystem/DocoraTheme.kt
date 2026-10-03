package com.vedica.labs.ind.app.docora.ui.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.vedica.labs.ind.app.docora.core.model.ThemeMode

/**
 * Docora's top-level theme (PRD §39).
 *
 * Honors system theme preference, dynamic color (Android 12+), and provides access to
 * the custom token spaces: [DocoraSpacing], [DocoraExtendedColors], and [DocoraMotion].
 */
@Composable
fun DocoraTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DocoraDarkColorScheme
        else -> DocoraLightColorScheme
    }

    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors
    val spacing = DocoraSpacing()
    val motion = DocoraMotion(reducedMotion = reducedMotion)
    val gradients = remember(colorScheme, extendedColors) { buildGradients(colorScheme, extendedColors) }

    CompositionLocalProvider(
        LocalDocoraSpacing provides spacing,
        LocalDocoraExtendedColors provides extendedColors,
        LocalDocoraMotion provides motion,
        LocalDocoraGradients provides gradients,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DocoraTypography,
            shapes = DocoraShapes,
            content = content,
        )
    }
}

/**
 * Derives the brand gradients from the active colour scheme.
 *
 * Dynamic colour (Android 12+) replaces `primary`/`tertiary` with wallpaper-derived tones; because
 * every gradient is built from those roles, the identity survives the swap instead of clashing
 * with it, and the highlight colour comes from the semantic palette rather than a hardcoded green.
 */
private fun buildGradients(
    scheme: androidx.compose.material3.ColorScheme,
    extended: DocoraExtendedColors,
): DocoraGradients = DocoraGradients(
    hero = Brush.linearGradient(
        listOf(scheme.primary, scheme.tertiary, scheme.primary),
    ),
    heroGlow = Brush.radialGradient(
        colors = listOf(extended.heroAccent.copy(alpha = 0.45f), Color.Transparent),
    ),
    featured = Brush.linearGradient(listOf(scheme.primary, scheme.secondary)),
    scannerTopScrim = Brush.verticalGradient(
        listOf(extended.scannerScrim, Color.Transparent),
    ),
    scannerBottomScrim = Brush.verticalGradient(
        listOf(Color.Transparent, extended.scannerScrim),
    ),
    skeletonSheen = Brush.horizontalGradient(
        listOf(Color.Transparent, extended.skeletonHighlight, Color.Transparent),
    ),
)

/**
 * Convenience accessor object for Docora design system tokens.
 */
object DocoraThemeTokens {
    val spacing: DocoraSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalDocoraSpacing.current

    val extendedColors: DocoraExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalDocoraExtendedColors.current

    val motion: DocoraMotion
        @Composable
        @ReadOnlyComposable
        get() = LocalDocoraMotion.current

    val gradients: DocoraGradients
        @Composable
        @ReadOnlyComposable
        get() = LocalDocoraGradients.current
}
