package io.github.gativrek.momenta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.data.library.Artwork
import io.github.gativrek.momenta.data.library.Track
import io.github.gativrek.momenta.data.library.artworkModel

/** Cover art for a library track. */
@Composable
fun ArtworkImage(
    track: Track,
    size: Dp,
    modifier: Modifier = Modifier,
) = ArtworkImage(artwork = artworkModel(track), size = size, modifier = modifier)

/**
 * Cover art in a rounded square. The line-icon note underneath shows while loading and
 * stays visible when there is no art (the image simply never draws over it).
 */
@Composable
fun ArtworkImage(
    artwork: Artwork?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(RoundedCornerShape(size / 8))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_nav_songs),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size / 2),
        )
        if (artwork != null) {
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
