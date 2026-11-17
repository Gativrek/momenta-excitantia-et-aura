package io.github.gativrek.momenta.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import io.github.gativrek.momenta.ui.screens.albums.AlbumsScreen
import io.github.gativrek.momenta.ui.screens.artists.ArtistsScreen
import io.github.gativrek.momenta.ui.screens.folders.FoldersScreen
import io.github.gativrek.momenta.ui.screens.playlists.PlaylistsScreen
import io.github.gativrek.momenta.ui.screens.songs.SongsScreen

@Composable
fun MomentaNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.Songs.route,
        modifier = modifier,
    ) {
        composable(TopLevelDestination.Songs.route) { SongsScreen() }
        composable(TopLevelDestination.Albums.route) { AlbumsScreen() }
        composable(TopLevelDestination.Artists.route) { ArtistsScreen() }
        composable(TopLevelDestination.Folders.route) { FoldersScreen() }
        composable(TopLevelDestination.Playlists.route) { PlaylistsScreen() }
    }
}
