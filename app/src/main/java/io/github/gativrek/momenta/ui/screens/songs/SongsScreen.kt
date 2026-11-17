package io.github.gativrek.momenta.ui.screens.songs

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.ui.components.PlaceholderScreen

@Composable
fun SongsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        iconRes = R.drawable.ic_nav_songs,
        title = stringResource(R.string.nav_songs),
        subtitle = stringResource(R.string.placeholder_coming_soon),
        modifier = modifier,
    )
}
