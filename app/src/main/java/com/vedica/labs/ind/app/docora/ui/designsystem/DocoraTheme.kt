package com.vedica.labs.ind.app.docora.ui.designsystem

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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

    SyncSystemBarIconAppearance(darkTheme)

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
 * Keeps the system bar icons legible against the active theme.
 *
 * The app draws edge to edge with transparent system bars, so the clock, battery and signal icons
 * are painted by the OS directly over our own content. Their colour is decided by the
 * `isAppearanceLightStatusBars` flag, not by the XML theme: `android:windowLightStatusBar` is read
 * once when the window is created, so it only ever reflects the mode the app *launched* in.
 *
 * That is what made the icons vanish in dark mode. Launching in light mode set the flag to "light
 * icons", which is correct for a light background; switching to dark at runtime - either from the
 * system setting or from Docora's own Light/Dark preference - repaints the background dark while
 * the OS keeps drawing the dark icons, so the status bar goes black-on-black. The reverse also
 * gave white icons on white.
 *
 * Re-applying the flag whenever the resolved theme changes fixes both directions, and also covers
 * the in-app theme switch, which never touches the system configuration at all.
 */
@Composable
private fun SyncSystemBarIconAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as? Activity)?.window ?: return
        SideEffect {
            WindowCompat.getInsetsController(window, view).apply {
                // "Light status bar" means dark icons; it must be the inverse of a dark theme.
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
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
