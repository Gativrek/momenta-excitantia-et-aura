package io.github.gativrek.momenta.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import io.github.gativrek.momenta.ui.screens.NavArgs
import io.github.gativrek.momenta.ui.screens.albums.AlbumDetailScreen
import io.github.gativrek.momenta.ui.screens.albums.AlbumsScreen
import io.github.gativrek.momenta.ui.screens.appearance.AppearanceScreen
import io.github.gativrek.momenta.ui.screens.artists.ArtistDetailScreen
import io.github.gativrek.momenta.ui.screens.artists.ArtistsScreen
import io.github.gativrek.momenta.ui.screens.folders.FoldersScreen
import io.github.gativrek.momenta.ui.screens.nowplaying.NowPlayingScreen
import io.github.gativrek.momenta.ui.screens.nowplaying.PlayerViewModel
import io.github.gativrek.momenta.ui.screens.playlists.PlaylistDetailScreen
import io.github.gativrek.momenta.ui.screens.playlists.PlaylistsScreen
import io.github.gativrek.momenta.ui.screens.queue.QueueScreen
import io.github.gativrek.momenta.ui.screens.search.SearchScreen
import io.github.gativrek.momenta.ui.screens.songs.SongsScreen

/** Full-screen player, opened from the mini-player. Not a tab, so the bottom bar hides. */
const val NOW_PLAYING_ROUTE = "now_playing"

/** The queue, opened from Now Playing. Also full-screen. */
const val QUEUE_ROUTE = "queue"

/** Theme settings, opened from the top bar of any tab. Full-screen, so the whole screen previews the theme. */
const val APPEARANCE_ROUTE = "appearance"

/** Search, opened from the top bar of any tab. Not inside a tab: results open in their own tab. */
const val SEARCH_ROUTE = "search"

/**
 * Each tab is a nested graph whose route is the tab's route. Detail screens live inside
 * their tab's graph, so the tab stays highlighted and keeps its own back stack: open an
 * album, switch to Songs and back, and the album is still there.
 *
 * Album detail is reachable from two tabs (Albums, Artists), so it's registered in both
 * graphs under a tab-specific route.
 */
@Composable
fun MomentaNavHost(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    onPlayStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.Songs.route,
        modifier = modifier,
    ) {
        navigation(route = TopLevelDestination.Songs.route, startDestination = "songs/list") {
            composable("songs/list") { SongsScreen(onPlayStarted = onPlayStarted) }
        }

        val albumsTab = TopLevelDestination.Albums.route
        navigation(route = albumsTab, startDestination = "$albumsTab/list") {
            composable("$albumsTab/list") {
                AlbumsScreen(onAlbumClick = { navController.navigate(albumRoute(albumsTab, it.id)) })
            }
            albumDetail(albumsTab, navController, onPlayStarted)
        }

        val artistsTab = TopLevelDestination.Artists.route
        navigation(route = artistsTab, startDestination = "$artistsTab/list") {
            composable("$artistsTab/list") {
                ArtistsScreen(onArtistClick = { navController.navigate("$artistsTab/artist/${it.id}") })
            }
            composable(
                route = "$artistsTab/artist/{${NavArgs.ARTIST_ID}}",
                arguments = listOf(navArgument(NavArgs.ARTIST_ID) { type = NavType.LongType }),
            ) {
                ArtistDetailScreen(
                    onBack = { navController.popBackStack() },
                    onAlbumClick = { navController.navigate(albumRoute(artistsTab, it.id)) },
                    onPlayStarted = onPlayStarted,
                )
            }
            albumDetail(artistsTab, navController, onPlayStarted)
        }

        val foldersTab = TopLevelDestination.Folders.route
        navigation(route = foldersTab, startDestination = "$foldersTab/list") {
            // One destination for every level: the path is an optional query argument, "" = root.
            composable(
                route = "$foldersTab/list?${NavArgs.PATH}={${NavArgs.PATH}}",
                arguments =
                    listOf(
                        navArgument(NavArgs.PATH) {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
            ) { entry ->
                val path = entry.arguments?.getString(NavArgs.PATH).orEmpty()
                FoldersScreen(
                    isRoot = path.isEmpty(),
                    // Paths contain "/" and spaces, so they're URI-encoded into the route.
                    onFolderClick = {
                        navController.navigate(
                            "$foldersTab/list?${NavArgs.PATH}=${Uri.encode(it.path)}",
                        )
                    },
                    onBack = { navController.popBackStack() },
                    onPlayStarted = onPlayStarted,
                )
            }
        }

        val playlistsTab = TopLevelDestination.Playlists.route
        navigation(route = playlistsTab, startDestination = "$playlistsTab/list") {
            composable("$playlistsTab/list") {
                PlaylistsScreen(onPlaylistClick = { navController.navigate("$playlistsTab/playlist/$it") })
            }
            composable(
                route = "$playlistsTab/playlist/{${NavArgs.PLAYLIST_ID}}",
                arguments = listOf(navArgument(NavArgs.PLAYLIST_ID) { type = NavType.LongType }),
            ) {
                PlaylistDetailScreen(onBack = { navController.popBackStack() }, onPlayStarted = onPlayStarted)
            }
        }

        composable(NOW_PLAYING_ROUTE) {
            NowPlayingScreen(
                viewModel = playerViewModel,
                onCollapse = { navController.popBackStack() },
                onOpenQueue = { navController.navigate(QUEUE_ROUTE) { launchSingleTop = true } },
            )
        }

        composable(SEARCH_ROUTE) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onAlbumClick = { navController.navigate(albumRoute(albumsTab, it.id)) },
                onArtistClick = { navController.navigate("$artistsTab/artist/${it.id}") },
                onPlayStarted = onPlayStarted,
            )
        }

        composable(APPEARANCE_ROUTE) {
            AppearanceScreen(onBack = { navController.popBackStack() })
        }

        composable(QUEUE_ROUTE) {
            QueueScreen(viewModel = playerViewModel, onBack = { navController.popBackStack() })
        }
    }
}

private fun albumRoute(
    tab: String,
    albumId: Long,
) = "$tab/album/$albumId"

private fun NavGraphBuilder.albumDetail(
    tab: String,
    navController: NavHostController,
    onPlayStarted: () -> Unit,
) {
    composable(
        route = "$tab/album/{${NavArgs.ALBUM_ID}}",
        arguments = listOf(navArgument(NavArgs.ALBUM_ID) { type = NavType.LongType }),
    ) {
        AlbumDetailScreen(onBack = { navController.popBackStack() }, onPlayStarted = onPlayStarted)
    }
}
