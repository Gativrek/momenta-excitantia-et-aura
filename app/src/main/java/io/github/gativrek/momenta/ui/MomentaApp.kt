package io.github.gativrek.momenta.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.ui.components.MiniPlayer
import io.github.gativrek.momenta.ui.navigation.MomentaNavHost
import io.github.gativrek.momenta.ui.navigation.NOW_PLAYING_ROUTE
import io.github.gativrek.momenta.ui.navigation.QUEUE_ROUTE
import io.github.gativrek.momenta.ui.navigation.SEARCH_ROUTE
import io.github.gativrek.momenta.ui.navigation.TopLevelDestination
import io.github.gativrek.momenta.ui.screens.NavArgs
import io.github.gativrek.momenta.ui.screens.nowplaying.PlayerViewModel
import io.github.gativrek.momenta.ui.screens.permission.rememberNotificationPermissionRequest

/**
 * Root composable: the five library tabs, with the mini-player sitting on top of the
 * bottom bar whenever something is loaded. Now Playing covers everything.
 */
@Composable
fun MomentaApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // Now Playing and the queue are full-screen: no mini-player, no tabs.
    val fullScreen = currentDestination?.route in setOf(NOW_PLAYING_ROUTE, QUEUE_ROUTE)
    // Search keeps the mini-player (you start music from it) but not the tabs.
    val showTabs = !fullScreen && currentDestination?.route != SEARCH_ROUTE
    // The tab whose root list is showing, if any: it gets a title bar with the search button.
    // Detail screens have their own top bar. Folders use one route for every level, root = "".
    val tabRoot =
        TopLevelDestination.entries.firstOrNull { tab ->
            currentDestination?.route?.substringBefore('?') == "${tab.route}/list" &&
                backStackEntry?.arguments?.getString(NavArgs.PATH).isNullOrEmpty()
        }

    // One PlayerViewModel for the whole app (scoped to the Activity), shared by both players.
    val playerViewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory)
    val requestNotifications = rememberNotificationPermissionRequest()

    Scaffold(
        topBar = {
            if (tabRoot != null) {
                TabTopBar(
                    title = stringResource(tabRoot.labelRes),
                    onSearch = { navController.navigate(SEARCH_ROUTE) { launchSingleTop = true } },
                )
            }
        },
        bottomBar = {
            if (!fullScreen) {
                Column {
                    MiniPlayerSlot(
                        playerViewModel = playerViewModel,
                        onExpand = { navController.navigate(NOW_PLAYING_ROUTE) { launchSingleTop = true } },
                    )
                    if (showTabs) BottomBar(navController, currentDestination)
                }
            }
        },
    ) { innerPadding ->
        MomentaNavHost(
            navController = navController,
            playerViewModel = playerViewModel,
            onPlayStarted = requestNotifications,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabTopBar(
    title: String,
    onSearch: () -> Unit,
) {
    TopAppBar(
        title = { Text(title) },
        actions = {
            IconButton(onClick = onSearch) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.action_search))
            }
        },
    )
}

@Composable
private fun MiniPlayerSlot(
    playerViewModel: PlayerViewModel,
    onExpand: () -> Unit,
) {
    val state by playerViewModel.state.collectAsStateWithLifecycle()
    val nowPlaying = state.nowPlaying ?: return
    val position by playerViewModel.position.collectAsStateWithLifecycle(initialValue = 0L)
    MiniPlayer(
        nowPlaying = nowPlaying,
        playWhenReady = state.playWhenReady,
        hasNext = state.hasNext,
        progress = { if (state.durationMs > 0) (position.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f },
        onExpand = onExpand,
        onTogglePlayPause = playerViewModel::togglePlayPause,
        onNext = playerViewModel::next,
    )
}

@Composable
private fun BottomBar(
    navController: NavHostController,
    currentDestination: NavDestination?,
) {
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
}
