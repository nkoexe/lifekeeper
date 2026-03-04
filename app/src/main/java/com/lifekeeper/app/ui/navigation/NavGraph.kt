package com.lifekeeper.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lifekeeper.app.ui.edit.EditModesScreen
import com.lifekeeper.app.ui.mode.ModeScreen
import com.lifekeeper.app.ui.summary.SummaryScreen

// ── Routes ────────────────────────────────────────────────────────────────────
object Routes {
    const val MODES      = "modes"
    const val SUMMARY    = "summary"
    const val EDIT_MODES = "edit_modes"
}

// ── Tab descriptors ───────────────────────────────────────────────────────────
private data class TopLevelTab(
    val route: String,
    val label: String,
    val icon:  ImageVector,
)

private val TOP_LEVEL_TABS = listOf(
    TopLevelTab(Routes.MODES,   "Modes",   Icons.Outlined.Home),
    TopLevelTab(Routes.SUMMARY, "Summary", Icons.Outlined.BarChart),
)

// O(1) lookup — is the given route a bottom-nav tab?
private val TAB_ROUTES = TOP_LEVEL_TABS.map { it.route }.toHashSet()

// ── Transition constants ──────────────────────────────────────────────────────
// M3 fade-through: exit is intentionally short so the screen clears before the
// new one arrives — asymmetric by design.
private const val TAB_EXIT_DURATION  = 90
private const val TAB_ENTER_DURATION = 220

// Standard push/pop durations.
private const val PUSH_DURATION = 300
private const val POP_DURATION  = 250

// ── Transition helpers ────────────────────────────────────────────────────────
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch() =
    initialState.destination.route in TAB_ROUTES &&
    targetState.destination.route  in TAB_ROUTES

// NavHost-level defaults — tab switches use M3 fade-through; push uses slide+fade.
private val defaultEnterTransition:
    AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    if (isTabSwitch())
        fadeIn(tween(TAB_ENTER_DURATION, easing = LinearEasing))
    else
        slideInHorizontally(tween(PUSH_DURATION)) { it / 6 } + fadeIn(tween(PUSH_DURATION))
}

private val defaultExitTransition:
    AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (isTabSwitch())
        fadeOut(tween(TAB_EXIT_DURATION, easing = LinearEasing))
    else
        slideOutHorizontally(tween(POP_DURATION)) { -it / 6 } + fadeOut(tween(POP_DURATION))
}

private val defaultPopEnterTransition:
    AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(tween(POP_DURATION)) { -it / 6 } + fadeIn(tween(POP_DURATION))
}

private val defaultPopExitTransition:
    AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(tween(POP_DURATION)) { it / 6 } + fadeOut(tween(POP_DURATION))
}

// ── NavGraph ──────────────────────────────────────────────────────────────────
@Composable
fun NavGraph() {
    val navController = rememberNavController()

    // Recompose only on back-stack changes (cheap — destination identity only).
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute   = backStackEntry?.destination?.route

    // Bottom navigation is shown only on top-level tab destinations.
    val showBottomBar = currentRoute in TAB_ROUTES

    // No BackHandler needed: popUpTo(startDestination, inclusive=false) keeps Modes at the
    // bottom of the back stack, so NavController already pops Summary → Modes naturally.
    // A manual BackHandler would steal the gesture before predictive back can animate.

    // Shell scaffold — owns the bottom NavigationBar; each leaf screen provides
    // its own TopAppBar and any FABs.
    // contentWindowInsets=0 prevents the shell from duplicating the system insets
    // that each inner Scaffold already handles via its own contentWindowInsets.
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    // The NavigationBar itself consumes the navigation-bar (gesture /
                    // button) inset so the bar background extends to the true bottom
                    // of the screen on edge-to-edge builds.
                    windowInsets = WindowInsets.navigationBars,
                ) {
                    TOP_LEVEL_TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick  = {
                                navController.navigate(tab.route) {
                                    // Keep the start destination at the bottom of the
                                    // back stack so the user can always back to Modes.
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    // Don't duplicate the destination if already selected.
                                    launchSingleTop = true
                                    // Restore scroll / VM state of the previously left tab.
                                    restoreState    = true
                                }
                            },
                            icon    = { Icon(tab.icon, contentDescription = tab.label) },
                            label   = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        // innerPadding.bottom = NavigationBar height (gesture area included inside it).
        // Applying it here ensures no screen content disappears behind the bar.
        NavHost(
            navController    = navController,
            startDestination = Routes.MODES,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // Opaque background prevents content from bleeding through transitions.
                .background(MaterialTheme.colorScheme.background),
            enterTransition    = defaultEnterTransition,
            exitTransition     = defaultExitTransition,
            popEnterTransition = defaultPopEnterTransition,
            popExitTransition  = defaultPopExitTransition,
        ) {
            // ── Tab destinations ───────────────────────────────────────────
            composable(Routes.MODES) {
                ModeScreen(onOpenEditModes = { navController.navigate(Routes.EDIT_MODES) })
            }
            composable(Routes.SUMMARY) {
                SummaryScreen()
            }

            // ── Push destination (no bottom bar) ──────────────────────────
            composable(Routes.EDIT_MODES) {
                EditModesScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
