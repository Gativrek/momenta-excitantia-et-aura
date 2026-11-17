package io.github.gativrek.momenta.ui.screens.artists

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.ui.components.PlaceholderScreen

@Composable
fun ArtistsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        iconRes = R.drawable.ic_nav_artists,
        title = stringResource(R.string.nav_artists),
        subtitle = stringResource(R.string.placeholder_coming_soon),
        modifier = modifier,
    )
}
