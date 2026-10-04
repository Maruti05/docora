package com.vedica.labs.ind.app.docora.ui.screens

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.ui.designsystem.DocoraMotion
import com.vedica.labs.ind.app.docora.ui.designsystem.DocoraThemeTokens
import com.vedica.labs.ind.app.docora.ui.designsystem.rememberDocoraWidthClass
import com.vedica.labs.ind.app.docora.ui.home.HomeScreen
import com.vedica.labs.ind.app.docora.ui.search.SearchScreen
import com.vedica.labs.ind.app.docora.ui.settings.SettingsScreen

private data class TopLevelDestination(
    val screen: Screen,
    val labelRes: Int,
    val icon: @Composable () -> Unit,
)

/**
 * The destinations the bottom bar and navigation rail can select (PRD §26).
 *
 * Movement between two of these is a *lateral* move: they are peers, with no hierarchy between
 * them, and the user can reach either one directly. Movement involving anything else (details,
 * viewer) is a *hierarchical* move that goes one level deeper or comes back up.
 *
 * The distinction matters because the two get different motion. Sliding peer tabs sideways implied
 * a forward/back relationship that does not exist, which is why tab switching felt directionless
 * and arbitrary.
 */
private val TopLevelRoutes: Set<String> = setOf(
    Screen.Home.route,
    Screen.Documents.route,
    Screen.Scanner.route,
    Screen.Search.route,
    Screen.Settings.route,
)

/**
 * Material's shared-axis travel distance. A fixed dp value rather than a fraction of the screen so
 * the same gesture reads identically on a phone and on a tablet.
 */
private val SharedAxisOffset = 30.dp

private fun NavBackStackEntry.isTopLevelRoute(): Boolean =
    destination.route.orEmpty() in TopLevelRoutes

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isLateralMove(): Boolean =
    initialState.isTopLevelRoute() && targetState.isTopLevelRoute()

/**
 * Lateral, entering: fade-through. The incoming tab cross-fades while settling from 96%, and
 * deliberately does not slide in from an edge because there is no "from" for a peer destination.
 *
 * The scale is subtler than the 0.92 in the Material spec because these are full-bleed surfaces
 * (including a black camera screen), where a larger scale reads as the screen shrinking rather
 * than as content arriving.
 */
private fun lateralEnter(motion: DocoraMotion): EnterTransition =
    fadeIn(tween(motion.standard, easing = motion.standardEasing)) +
        scaleIn(
            animationSpec = tween(motion.standard, easing = motion.emphasizedEasing),
            initialScale = 0.96f,
        )

private fun lateralExit(motion: DocoraMotion): ExitTransition =
    fadeOut(tween(motion.standard, easing = motion.standardEasing))

/**
 * Hierarchical, entering (push): shared-axis X. The new screen arrives from the right while the one
 * it covers leaves to the left by the same distance.
 */
private fun forwardEnter(motion: DocoraMotion, offsetPx: Int): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(motion.emphasized, easing = motion.emphasizedEasing),
        initialOffsetX = { offsetPx },
    ) + fadeIn(tween(motion.emphasized, easing = motion.standardEasing))

private fun forwardExit(motion: DocoraMotion, offsetPx: Int): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(motion.emphasized, easing = motion.emphasizedEasing),
        targetOffsetX = { -offsetPx },
    ) + fadeOut(tween(motion.emphasized, easing = motion.standardEasing))

/**
 * Hierarchical, leaving (pop): the exact mirror of [forwardEnter] / [forwardExit], so going back is
 * unmistakably the reverse of going in and the predictive back gesture animates truthfully.
 */
private fun backEnter(motion: DocoraMotion, offsetPx: Int): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(motion.emphasized, easing = motion.emphasizedEasing),
        initialOffsetX = { -offsetPx },
    ) + fadeIn(tween(motion.emphasized, easing = motion.standardEasing))

private fun backExit(motion: DocoraMotion, offsetPx: Int): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(motion.emphasized, easing = motion.emphasizedEasing),
        targetOffsetX = { offsetPx },
    ) + fadeOut(tween(motion.emphasized, easing = motion.standardEasing))

/**
 * App shell (PRD §26, §57).
 *
 * Phones get a bottom navigation bar; medium and expanded widths switch to a navigation
 * rail. The bar is only shown for the five top-level destinations - viewer, details and
 * scanner go full-bleed so documents use the whole screen.
 */
@Composable
fun DocoraApp() {
    val controller = rememberNavController()
    val items = listOf(
        TopLevelDestination(Screen.Home, R.string.nav_home) {
            Icon(Icons.Filled.Home, contentDescription = null)
        },
        TopLevelDestination(Screen.Documents, R.string.nav_documents) {
            Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null)
        },
        TopLevelDestination(Screen.Scanner, R.string.nav_scan) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
        },
        TopLevelDestination(Screen.Search, R.string.nav_search) {
            Icon(Icons.Filled.Search, contentDescription = null)
        },
        TopLevelDestination(Screen.Settings, R.string.nav_settings) {
            Icon(Icons.Filled.Settings, contentDescription = null)
        },
    )
    val backStack by controller.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination
    val isTopLevel = items.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
    }
    val widthClass = rememberDocoraWidthClass()

    Scaffold(bottomBar = {
        if (isTopLevel && widthClass.isCompact) {
            NavigationBar {
                items.forEach { item ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == item.screen.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { controller.navigateTopLevel(item.screen) },
                        icon = item.icon,
                        label = { Text(stringResource(item.labelRes)) },
                    )
                }
            }
        }
    }) { padding ->
        val bottomInset = if (isTopLevel && widthClass.isCompact) padding.calculateBottomPadding() else 0.dp
        Row(
            Modifier
                .fillMaxSize()
                .padding(bottom = bottomInset),
        ) {
            if (isTopLevel && !widthClass.isCompact) {
                NavigationRail {
                    items.forEach { item ->
                        val selected = currentDestination
                            ?.hierarchy
                            ?.any { it.route == item.screen.route } == true
                        NavigationRailItem(
                            selected = selected,
                            onClick = { controller.navigateTopLevel(item.screen) },
                            icon = item.icon,
                            label = { Text(stringResource(item.labelRes)) },
                        )
                    }
                }
            }
            Box(Modifier.fillMaxSize()) {
                AppNavHost(controller = controller)
            }
        }
    }
}

/**
 * Single top-level navigation pattern: restores state, never stacks copies of a tab.
 *
 * The start destination (Home) is handled differently on purpose. `popUpTo(startDestination)`
 * is non-inclusive, so Home always stays on the back stack, and asking for `restoreState` on a
 * destination that was never popped makes NavController restore the saved stack instead of
 * performing the navigation - the tap is silently dropped and the previous tab stays on screen.
 * That is why every tab except Home worked.
 *
 * So for Home we only pop back down to it and never restore; the other tabs keep the
 * save/restore pair so their scroll position and filters survive a round trip.
 */
private fun NavHostController.navigateTopLevel(screen: Screen) {
    // Tapping the tab you are already on must not stack or re-enter the destination.
    if (currentDestination?.hierarchy?.any { it.route == screen.route } == true) return

    val startRoute = graph.findStartDestination().route
    val isStartDestination = screen.route == startRoute

    navigate(screen.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = !isStartDestination
        }
        launchSingleTop = true
        restoreState = !isStartDestination
    }
}

@Composable
fun AppNavHost(
    controller: NavHostController,
    startDestination: Screen = Screen.Home,
) {
    val motion = DocoraThemeTokens.motion
    // The transition lambdas are not composable, so density has to be resolved out here and captured.
    val sharedAxisOffsetPx = with(LocalDensity.current) { SharedAxisOffset.roundToPx() }

    NavHost(
        navController = controller,
        startDestination = startDestination.route,
        modifier = Modifier.fillMaxSize(),
        // PRD §40: motion explains the state change and never delays interaction.
        //
        // Every pair of lambdas below gives its enter and its exit the same duration. That is not a
        // style choice. The previous spec faded the outgoing screen out in `quick` while fading the
        // incoming one in over `standard`, so between those two values the outgoing screen had gone
        // and the incoming one was still translucent - and the window background flashed through.
        // It was invisible on the light tabs, where the background colour matched, but very obvious
        // on Scanner (black) and Viewer (dark stage). Equal durations keep the two screens
        // overlapping for the whole transition, so they can only ever blend.
        //
        // Lateral moves route their push *and* pop through the same two builders. Without that,
        // switching to Home (which pops) and switching to any other tab (which pushes) played
        // different animations for the same user action.
        enterTransition = {
            if (isLateralMove()) lateralEnter(motion) else forwardEnter(motion, sharedAxisOffsetPx)
        },
        exitTransition = {
            if (isLateralMove()) lateralExit(motion) else forwardExit(motion, sharedAxisOffsetPx)
        },
        popEnterTransition = {
            if (isLateralMove()) lateralEnter(motion) else backEnter(motion, sharedAxisOffsetPx)
        },
        popExitTransition = {
            if (isLateralMove()) lateralExit(motion) else backExit(motion, sharedAxisOffsetPx)
        },
    ) {
        composable(Screen.Home.route) {
            HomeScreen(controller = controller)
        }
        composable(Screen.Documents.route) {
            DocumentsScreen(controller = controller)
        }
        composable(Screen.Scanner.route) {
            ScannerScreen(controller = controller)
        }
        composable(Screen.Search.route) {
            SearchScreen(controller = controller)
        }
        composable(
            route = Screen.Details.route,
            arguments = listOf(navArgument(Screen.ARG_DOCUMENT_ID) { type = NavType.StringType }),
        ) { entry ->
            DetailsScreen(
                controller = controller,
                documentId = entry.arguments?.getString(Screen.ARG_DOCUMENT_ID),
            )
        }
        composable(
            route = Screen.Viewer.route,
            arguments = listOf(navArgument(Screen.ARG_DOCUMENT_ID) { type = NavType.StringType }),
        ) { entry ->
            ViewerScreen(
                controller = controller,
                documentId = entry.arguments?.getString(Screen.ARG_DOCUMENT_ID),
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(controller = controller)
        }
    }
}
