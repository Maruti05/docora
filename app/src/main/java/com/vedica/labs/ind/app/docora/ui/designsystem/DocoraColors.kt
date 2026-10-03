package com.vedica.labs.ind.app.docora.ui.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Brand palette.
 *
 * The product identity is a calm, document-first green: it reads as "paper and ink"
 * rather than as a chat or a media app, and keeps a strong contrast ratio in both themes.
 */
private val Evergreen = Color(0xFF1B4636)
private val EvergreenLight = Color(0xFF9BE8C4)
private val EvergreenContainer = Color(0xFFCDEFDD)
private val OnEvergreenContainer = Color(0xFF04291B)
private val Sand = Color(0xFFF6F3EC)
private val Ink = Color(0xFF191C1A)
private val Graphite = Color(0xFF202421)
private val MutedInk = Color(0xFF424945)
private val Slate = Color(0xFF5B6360)

/** Light scheme: soft paper background, evergreen accents. */
internal val DocoraLightColorScheme: ColorScheme = lightColorScheme(
    primary = Evergreen,
    onPrimary = Color.White,
    primaryContainer = EvergreenContainer,
    onPrimaryContainer = OnEvergreenContainer,
    secondary = Color(0xFF4F6357),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2E8DA),
    onSecondaryContainer = Color(0xFF0C1F15),
    tertiary = Color(0xFF3B6470),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBFE9F8),
    onTertiaryContainer = Color(0xFF001F28),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBFDF9),
    onBackground = Ink,
    surface = Color(0xFFFBFDF9),
    onSurface = Ink,
    surfaceVariant = Color(0xFFDCE5DE),
    onSurfaceVariant = MutedInk,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F8F4),
    surfaceContainer = Color(0xFFEFF2EE),
    surfaceContainerHigh = Color(0xFFE9ECE8),
    surfaceContainerHighest = Color(0xFFE3E7E2),
    outline = Color(0xFF727973),
    outlineVariant = Color(0xFFC2C9C2),
    inverseSurface = Graphite,
    inverseOnSurface = Color(0xFFEFF1ED),
    inversePrimary = EvergreenLight,
)

/** Dark scheme: deep, low-glare surfaces for reading in the dark. */
internal val DocoraDarkColorScheme: ColorScheme = darkColorScheme(
    primary = EvergreenLight,
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF0B5038),
    onPrimaryContainer = Color(0xFFB8F2D6),
    secondary = Color(0xFFB6CCBE),
    onSecondary = Color(0xFF213528),
    secondaryContainer = Color(0xFF374B3E),
    onSecondaryContainer = Color(0xFFD2E8DA),
    tertiary = Color(0xFFA3CCDA),
    onTertiary = Color(0xFF04333F),
    tertiaryContainer = Color(0xFF224B57),
    onTertiaryContainer = Color(0xFFBFE9F8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101410),
    onBackground = Color(0xFFE0E4DF),
    surface = Color(0xFF101410),
    onSurface = Color(0xFFE0E4DF),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFC0C9C2),
    surfaceContainerLowest = Color(0xFF0B0F0C),
    surfaceContainerLow = Color(0xFF171B18),
    surfaceContainer = Color(0xFF1C211D),
    surfaceContainerHigh = Color(0xFF262B27),
    surfaceContainerHighest = Color(0xFF313632),
    outline = Color(0xFF8A938C),
    outlineVariant = Color(0xFF404943),
    inverseSurface = Color(0xFFE0E4DF),
    inverseOnSurface = Color(0xFF2D322E),
    inversePrimary = Evergreen,
)

/**
 * Semantic colours that Material 3 does not model: the small accents that make a
 * document list scannable (file-type badges, favourite stars, progress states).
 * They live in the design system instead of being hardcoded in composables (PRD §39).
 */
@Suppress("unused")
data class DocoraExtendedColors(
    val pdfBadge: Color,
    val imageBadge: Color,
    val textBadge: Color,
    val officeBadge: Color,
    val archiveBadge: Color,
    val favourite: Color,
    val ocrPending: Color,
    val ocrDone: Color,
    val ocrFailed: Color,
    val gridTileBackground: Color,
    val selectionOverlay: Color,
    val cropGuide: Color,
    /** Bright accent laid over the hero gradient so it never reads as a flat rectangle. */
    val heroAccent: Color,
    /** Neutral blocks of a loading skeleton, and the moving highlight that sweeps over them. */
    val skeleton: Color,
    val skeletonHighlight: Color,
    /** Scrim laid over the camera preview so white controls stay legible on a bright page. */
    val scannerScrim: Color,
    /** Guidance frame colour once the frame looks like a document. */
    val guideLocked: Color,
    val success: Color,
    val warning: Color,
)

internal val LightExtendedColors = DocoraExtendedColors(
    pdfBadge = Color(0xFFD9483B),
    imageBadge = Color(0xFF3C7DBF),
    textBadge = Color(0xFF5B6B7C),
    officeBadge = Color(0xFF2C6E49),
    archiveBadge = Color(0xFF8A6A1F),
    favourite = Color(0xFFE0A32E),
    ocrPending = Color(0xFF8A6A1F),
    ocrDone = Color(0xFF2C6E49),
    ocrFailed = Color(0xFFB3261E),
    gridTileBackground = Color(0xFFEFF2EE),
    selectionOverlay = Evergreen.copy(alpha = 0.20f),
    cropGuide = Color(0xFF2C6E49),
    heroAccent = Color(0xFF78D5A6),
    skeleton = Color(0xFFE3E7E2),
    skeletonHighlight = Color(0xFFF3F6F2),
    scannerScrim = Color(0xB3000000),
    guideLocked = Color(0xFF2C6E49),
    success = Color(0xFF2C6E49),
    warning = Color(0xFF8A6A1F),
)

internal val DarkExtendedColors = DocoraExtendedColors(
    pdfBadge = Color(0xFFFFB4A9),
    imageBadge = Color(0xFFA9C7F5),
    textBadge = Color(0xFFBFCAD6),
    officeBadge = Color(0xFF9EE6BC),
    archiveBadge = Color(0xFFE7C978),
    favourite = Color(0xFFF4C86A),
    ocrPending = Color(0xFFE7C978),
    ocrDone = Color(0xFF9EE6BC),
    ocrFailed = Color(0xFFFFB4AB),
    gridTileBackground = Color(0xFF262B27),
    selectionOverlay = EvergreenLight.copy(alpha = 0.24f),
    cropGuide = Color(0xFF9BE8C4),
    heroAccent = Color(0xFF2C6E49),
    skeleton = Color(0xFF313632),
    skeletonHighlight = Color(0xFF454C47),
    scannerScrim = Color(0xCC000000),
    guideLocked = Color(0xFF9BE8C4),
    success = Color(0xFF9EE6BC),
    warning = Color(0xFFE7C978),
)

val LocalDocoraExtendedColors = staticCompositionLocalOf { LightExtendedColors }
