package com.vedica.labs.ind.app.docora.ui.designsystem

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Overrides the system bar icon colour for a screen with its own chrome colour.
 *
 * The app defaults are applied once by `DocoraTheme` from the light/dark setting. That is right
 * for every screen that draws on a normal surface, but wrong for the reader and the scanner: they
 * paint near-black chrome (or a camera preview) regardless of the theme, so in light mode the
 * default dark status bar icons disappear into the background. Those screens call this with
 * [useDarkIcons] = false while they are on screen, and the previous appearance is restored when
 * they leave.
 */
@Composable
fun SystemBarAppearance(useDarkIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = (view.context as? Activity)?.window ?: return

    DisposableEffect(window, view, useDarkIcons) {
        val controller = WindowCompat.getInsetsController(window, view)
        val previousStatus = controller.isAppearanceLightStatusBars
        val previousNavigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = useDarkIcons
        controller.isAppearanceLightNavigationBars = useDarkIcons
        onDispose {
            controller.isAppearanceLightStatusBars = previousStatus
            controller.isAppearanceLightNavigationBars = previousNavigation
        }
    }
}
