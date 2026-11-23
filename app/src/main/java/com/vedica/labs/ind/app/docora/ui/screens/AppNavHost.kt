package com.vedica.labs.ind.app.docora.ui.screens

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.vedica.labs.ind.app.docora.ui.components.BottomBarBottomGap
import com.vedica.labs.ind.app.docora.ui.components.BottomBarHeight
import com.vedica.labs.ind.app.docora.ui.components.DocoraBottomBar
import com.vedica.labs.ind.app.docora.ui.components.DocoraBottomBarItem
import com.vedica.labs.ind.app.docora.ui.components.LocalBottomBarInset
import com.vedica.labs.ind.app.docora.ui.designsystem.DocoraMotion
import com.vedica.labs.ind.app.docora.ui.designsystem.DocoraThemeTokens
import com.vedica.labs.ind.app.docora.ui.designsystem.rememberDocoraWidthClass
import com.vedica.labs.ind.app.docora.ui.home.HomeScreen
import com.vedica.labs.ind.app.docora.ui.search.SearchScreen
import com.vedica.labs.ind.app.docora.ui.settings.SettingsScreen

private data class TopLevelDestination(
    val screen: Screen,
    val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

/**
 * The destinations the bottom bar and navigation rail can select (PRD §26).
 *
 * Movement between two of these is a *lateral* move: they are peers, with no hierarchy between
 * them, and the user can reach either one directly. Movement involving anything else (details,
 * viewer, scanner, search) is a *hierarchical* move that goes one level deeper or comes back up.
 */
private val TopLevelRoutes: Set<String> = setOf(
    Screen.Home.route,
    Screen.Documents.route,
    Screen.Tools.route,
    Screen.Settings.route,
)

/**
 * Material's shared-axis travel distance. A fixed dp value rather than a fraction of the screen so
 * the same gesture reads identically on a phone and on a tablet.
 */
private val SharedAxisOffset = 30.dp

/**
 * Lateral travel distance between two tabs. Deliberately shorter than [SharedAxisOffset] but large
 * enough to be visible: a tab switch is a sideways nudge in the direction of the bar, not a full
 * push into a new level.
 */
private val TabSwitchOffset = 40.dp

/** Left-to-right order of the bar, used only to decide which way a tab switch should travel. */
private val TopLevelOrder: List<String> = listOf(
    Screen.Home.route,
    Screen.Documents.route,
    Screen.Tools.route,
    Screen.Settings.route,
)

private fun NavBackStackEntry.isTopLevelRoute(): Boolean =
    destination.route.orEmpty() in TopLevelRoutes

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isLateralMove(): Boolean =
    initialState.isTopLevelRoute() && targetState.isTopLevelRoute()

/**
 * +1 when the user moved right along the bar, -1 when they moved left, 0 when the direction is
 * unknown. Feeding this into the lateral transition is what makes Home -> Settings feel like a
 * journey in one direction instead of a generic cross-fade.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.lateralDirection(): Int {
    val from = TopLevelOrder.indexOf(initialState.destination.route.orEmpty())
    val to = TopLevelOrder.indexOf(targetState.destination.route.orEmpty())
    if (from < 0 || to < 0) return 0
    return (to - from).coerceIn(-1, 1)
}

/**
 * Lateral, entering: fade-through with a nudge in the direction of the tapped tab. The incoming
 * tab settles from 94% while the outgoing one drifts the other way, so the pair reads as one
 * sideways movement rather than as two unrelated screens. The emphasized duration is used on
 * purpose: at the quick standard duration the change was so short that it felt like a hard cut.
 */
private fun lateralEnter(motion: DocoraMotion, direction: Int, offsetPx: Int): EnterTransition =
    fadeIn(tween(motion.emphasized, easing = motion.standardEasing)) +
        scaleIn(
            animationSpec = tween(motion.emphasized, easing = motion.emphasizedEasing),
            initialScale = 0.94f,
        ) +
        slideInHorizontally(
            animationSpec = tween(motion.emphasized, easing = motion.emphasizedEasing),
            initialOffsetX = { direction * offsetPx },
        )

private fun lateralExit(motion: DocoraMotion, direction: Int, offsetPx: Int): ExitTransition =
    fadeOut(tween(motion.standard, easing = motion.standardEasing)) +
        scaleOut(
            animationSpec = tween(motion.standard, easing = motion.standardEasing),
            targetScale = 0.98f,
        ) +
        slideOutHorizontally(
            animationSpec = tween(motion.standard, easing = motion.emphasizedEasing),
            targetOffsetX = { -direction * offsetPx },
        )

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
 * Phones get a standard docked bottom navigation bar with labels; medium and expanded widths
 * switch to a navigation rail. The bar is shown for the four browsable top-level destinations - the
 * scanner, search, viewer and details screens go full-bleed so documents and the camera use the
 * whole screen.
 */
@Composable
fun DocoraApp() {
    val controller = rememberNavController()
    val motion = DocoraThemeTokens.motion
    val items = listOf(
        TopLevelDestination(
            screen = Screen.Home,
            labelRes = R.string.nav_home,
            icon = Icons.Outlined.Home,
            selectedIcon = Icons.Filled.Home,
        ),
        TopLevelDestination(
            screen = Screen.Documents,
            labelRes = R.string.nav_documents,
            icon = Icons.AutoMirrored.Outlined.LibraryBooks,
            selectedIcon = Icons.AutoMirrored.Filled.LibraryBooks,
        ),
        TopLevelDestination(
            screen = Screen.Tools,
            labelRes = R.string.nav_tools,
            icon = Icons.Outlined.Build,
            selectedIcon = Icons.Filled.Build,
        ),
        TopLevelDestination(
            screen = Screen.Settings,
            labelRes = R.string.nav_settings,
            icon = Icons.Outlined.Settings,
            selectedIcon = Icons.Filled.Settings,
        ),
    )
    val backStack by controller.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination
    // Category is a deep-linked Documents page, so the bar stays visible and Documents stays selected.
    val onCategory = currentDestination?.hierarchy?.any { it.route == Screen.Category.route } == true
    val isTopLevel = onCategory || items.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
    }
    val widthClass = rememberDocoraWidthClass()
    val showBottomBar = isTopLevel && widthClass.isCompact
    val density = LocalDensity.current
    val systemNavigationInset = with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val bottomChromeInset = if (showBottomBar) {
        BottomBarHeight + BottomBarBottomGap + systemNavigationInset
    } else {
        0.dp
    }

    CompositionLocalProvider(LocalBottomBarInset provides bottomChromeInset) {
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                Row(Modifier.fillMaxSize()) {
                    if (isTopLevel && !widthClass.isCompact) {
                        NavigationRail {
                            items.forEach { item ->
                                val selected = currentDestination
                                    ?.hierarchy
                                    ?.any { it.route == item.screen.route } == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { controller.navigateTopLevel(item.screen) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.icon,
                                            contentDescription = null,
                                        )
                                    },
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

            AnimatedVisibility(
                visible = showBottomBar,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(
                    animationSpec = tween(motion.emphasized, easing = motion.emphasizedEasing),
                    initialOffsetY = { it },
                ) + fadeIn(tween(motion.standard, easing = motion.standardEasing)),
                exit = slideOutVertically(
                    animationSpec = tween(motion.standard, easing = motion.standardEasing),
                    targetOffsetY = { it },
                ) + fadeOut(tween(motion.quick, easing = motion.standardEasing)),
            ) {
                val navItems = items.map { item ->
                    DocoraBottomBarItem(
                        label = stringResource(item.labelRes),
                        icon = item.icon,
                        selectedIcon = item.selectedIcon,
                    )
                }
                val selectedIndex = if (onCategory) {
                    items.indexOfFirst { it.screen == Screen.Documents }
                } else {
                    items.indexOfFirst { item ->
                        currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
                    }
                }.coerceAtLeast(0)
                DocoraBottomBar(
                    items = navItems,
                    selectedIndex = selectedIndex,
                    onSelect = { controller.navigateTopLevel(items[it].screen) },
                )
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
internal fun NavHostController.navigateTopLevel(screen: Screen) {
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
    val tabSwitchOffsetPx = with(LocalDensity.current) { TabSwitchOffset.roundToPx() }

    NavHost(
        navController = controller,
        startDestination = startDestination.route,
        modifier = Modifier.fillMaxSize(),
        // PRD §40: motion explains the state change and never delays interaction.
        enterTransition = {
            if (isLateralMove()) {
                lateralEnter(motion, lateralDirection(), tabSwitchOffsetPx)
            } else {
                forwardEnter(motion, sharedAxisOffsetPx)
            }
        },
        exitTransition = {
            if (isLateralMove()) {
                lateralExit(motion, lateralDirection(), tabSwitchOffsetPx)
            } else {
                forwardExit(motion, sharedAxisOffsetPx)
            }
        },
        popEnterTransition = {
            if (isLateralMove()) {
                lateralEnter(motion, lateralDirection(), tabSwitchOffsetPx)
            } else {
                backEnter(motion, sharedAxisOffsetPx)
            }
        },
        popExitTransition = {
            if (isLateralMove()) {
                lateralExit(motion, lateralDirection(), tabSwitchOffsetPx)
            } else {
                backExit(motion, sharedAxisOffsetPx)
            }
        },
    ) {
        composable(Screen.Home.route) {
            HomeScreen(controller = controller)
        }
        composable(Screen.Documents.route) {
            DocumentsScreen(controller = controller)
        }
        composable(
            route = Screen.Category.route,
            arguments = listOf(navArgument(Screen.ARG_CATEGORY) { type = NavType.StringType }),
        ) {
            DocumentsScreen(controller = controller)
        }
        composable(Screen.Tools.route) {
            ToolsScreen(controller = controller)
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
        composable(
            route = Screen.PdfEditor.route,
            arguments = listOf(
                navArgument(Screen.ARG_DOCUMENT_ID) { type = NavType.StringType },
                navArgument(Screen.ARG_PAGE) { type = NavType.IntType },
            ),
        ) {
            PdfEditorScreen(controller = controller)
        }
        composable(
            route = Screen.PdfTools.route,
            arguments = listOf(navArgument(Screen.ARG_DOCUMENT_ID) { type = NavType.StringType }),
        ) { entry ->
            PdfToolsScreen(
                controller = controller,
                documentId = entry.arguments?.getString(Screen.ARG_DOCUMENT_ID),
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(controller = controller)
        }
    }
}
