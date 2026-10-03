package com.vedica.labs.ind.app.docora.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vedica.labs.ind.app.docora.R
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

/** Single top-level navigation pattern: restores state, never stacks copies of a tab. */
private fun NavHostController.navigateTopLevel(screen: Screen) {
    navigate(screen.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppNavHost(
    controller: NavHostController,
    startDestination: Screen = Screen.Home,
) {
    val motion = DocoraThemeTokens.motion
    NavHost(
        navController = controller,
        startDestination = startDestination.route,
        modifier = Modifier.fillMaxSize(),
        // Subtle horizontal motion that never delays interaction (PRD §40).
        enterTransition = {
            fadeIn(tween(motion.standard)) +
                slideInHorizontally(tween(motion.emphasized)) { it / 24 }
        },
        exitTransition = { fadeOut(tween(motion.quick)) },
        popEnterTransition = { fadeIn(tween(motion.standard)) },
        popExitTransition = {
            fadeOut(tween(motion.quick)) +
                slideOutHorizontally(tween(motion.emphasized)) { it / 24 }
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
