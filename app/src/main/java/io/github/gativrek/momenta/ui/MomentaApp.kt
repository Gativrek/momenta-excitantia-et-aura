package io.github.gativrek.momenta.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.gativrek.momenta.ui.navigation.MomentaNavHost
import io.github.gativrek.momenta.ui.navigation.TopLevelDestination
import io.github.gativrek.momenta.ui.theme.MomentaTheme

/**
 * Root composable: bottom navigation around the five library tabs.
 * The mini-player (Phase 2) will sit between the content and the NavigationBar.
 */
@Composable
fun MomentaApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    // hierarchy also matches nested detail screens, so "Albums" stays
                    // highlighted while viewing an album (Phase 3).
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                // Standard tab behaviour: one back stack entry per tab, state saved
                                // and restored when switching, re-tapping a tab doesn't stack copies.
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(painterResource(destination.iconRes), contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        MomentaNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Preview
@Composable
private fun MomentaAppPreview() {
    MomentaTheme { MomentaApp() }
}
