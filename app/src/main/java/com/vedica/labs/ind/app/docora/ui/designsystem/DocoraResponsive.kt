package com.vedica.labs.ind.app.docora.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vedica.labs.ind.app.docora.core.model.ViewMode

/**
 * Width classes used for responsive layout.
 *
 * Deliberately derived from `LocalConfiguration.screenWidthDp` rather than the adaptive
 * `WindowSizeClass` artifact: the app already depends on Material 3 adaptive navigation, but the
 * breakpoints below are the only ones Docora needs, and reading the configuration keeps every
 * screen free of an extra dependency and works in previews.
 */
enum class DocoraWidthClass {
    /** Phones in portrait: single column, two narrow grid columns at most. */
    COMPACT,

    /** Large phones in landscape, small tablets: three grid columns. */
    MEDIUM,

    /** Tablets and foldables unfolded: four grid columns, and settings can use two columns. */
    EXPANDED,
    ;

    val isCompact: Boolean get() = this == COMPACT
    val isAtLeastMedium: Boolean get() = this != COMPACT
}

@Composable
@ReadOnlyComposable
fun rememberDocoraWidthClass(): DocoraWidthClass =
    widthClassFor(LocalConfiguration.current.screenWidthDp)

/** Pure mapping so the breakpoints can be unit tested without a composition. */
fun widthClassFor(screenWidthDp: Int): DocoraWidthClass = when {
    screenWidthDp < 600 -> DocoraWidthClass.COMPACT
    screenWidthDp < 840 -> DocoraWidthClass.MEDIUM
    else -> DocoraWidthClass.EXPANDED
}

/**
 * Concrete column count for a [ViewMode] at a given width: the persisted mode expresses intent
 * ("big tiles", "compact grid"), while the width decides how many of them physically fit.
 */
fun gridColumnsFor(mode: ViewMode, widthClass: DocoraWidthClass): Int = when (mode) {
    ViewMode.LARGE_GRID -> if (widthClass.isCompact) 1 else 2
    ViewMode.GRID -> when (widthClass) {
        DocoraWidthClass.COMPACT -> 2
        DocoraWidthClass.MEDIUM -> 3
        DocoraWidthClass.EXPANDED -> 4
    }
    ViewMode.LIST, ViewMode.COMPACT -> 1
}

/**
 * Wraps scrolling content so it stays readable on very wide screens.
 *
 * A document list stretched across a 12" tablet is hard to scan, so content is centred and capped
 * at [maxWidth]; on phones the cap is never reached and the modifier is effectively a no-op.
 */
@Composable
fun DocoraContentColumn(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 840.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally),
    ) {
        Box(modifier = Modifier.fillMaxWidth().widthIn(max = maxWidth), content = content)
    }
}
