package io.github.gativrek.momenta.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.gativrek.momenta.R

/** The five bottom-bar tabs, in display order. The first entry is the start destination. */
enum class TopLevelDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    Songs("songs", R.string.nav_songs, R.drawable.ic_nav_songs),
    Albums("albums", R.string.nav_albums, R.drawable.ic_nav_albums),
    Artists("artists", R.string.nav_artists, R.drawable.ic_nav_artists),
    Folders("folders", R.string.nav_folders, R.drawable.ic_nav_folders),
    Playlists("playlists", R.string.nav_playlists, R.drawable.ic_nav_playlists),
}
