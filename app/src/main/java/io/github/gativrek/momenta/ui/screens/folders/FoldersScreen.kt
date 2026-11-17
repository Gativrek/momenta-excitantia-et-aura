package io.github.gativrek.momenta.ui.screens.folders

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.ui.components.PlaceholderScreen

@Composable
fun FoldersScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        iconRes = R.drawable.ic_nav_folders,
        title = stringResource(R.string.nav_folders),
        subtitle = stringResource(R.string.placeholder_coming_soon),
        modifier = modifier,
    )
}
