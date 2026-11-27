package io.github.gativrek.momenta.ui.screens.aura

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.data.aura.AuraRank
import io.github.gativrek.momenta.data.aura.AuraRanks
import io.github.gativrek.momenta.ui.theme.moment
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** One emblem per rung of the ladder, bottom to top (design/icons/rank_*.svg). */
@DrawableRes
private val EMBLEMS =
    intArrayOf(
        R.drawable.ic_rank_static,
        R.drawable.ic_rank_spark,
        R.drawable.ic_rank_glow,
        R.drawable.ic_rank_resonance,
        R.drawable.ic_rank_main_character,
        R.drawable.ic_rank_overdrive,
        R.drawable.ic_rank_overflow,
        R.drawable.ic_rank_aura_farmer,
    )

/** A rank's name, from the `aura_ranks` array; Larper Larry while [larry]. */
@Composable
internal fun rankName(
    rank: AuraRank,
    larry: Boolean = false,
): String {
    if (larry) return stringResource(R.string.aura_rank_larry)
    val names = stringArrayResource(R.array.aura_ranks)
    return names.getOrElse(rank.tier) { names.last() }
}

/**
 * The top rank wears the Moment colour, Larper Larry the colour of a chicken's comb (the
 * theme's error red), the rest the theme accent.
 */
@Composable
internal fun rankColor(
    rank: AuraRank,
    larry: Boolean = false,
): Color =
    when {
        larry -> MaterialTheme.colorScheme.error
        rank.isTop -> MaterialTheme.colorScheme.moment
        else -> MaterialTheme.colorScheme.primary
    }

internal fun monthName(month: YearMonth): String =
    month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())

/**
 * A rank's emblem. With [glow], soft rings behind it that grow with the rank: none at
 * Static (or as Larry), seven at the top. [tint] overrides the rank's colour (e.g. for a
 * rank not reached yet).
 */
@Composable
internal fun RankEmblem(
    rank: AuraRank,
    size: Dp,
    modifier: Modifier = Modifier,
    larry: Boolean = false,
    glow: Boolean = true,
    tint: Color? = null,
) {
    val color = tint ?: rankColor(rank, larry)
    val rings = if (glow && !larry) rank.tier else 0
    Box(
        modifier =
            modifier.size(size).drawBehind {
                val outer = this.size.minDimension / 2
                for (i in 0 until rings) {
                    // Outermost first; each ring a little smaller and the layers add up toward the middle.
                    val radius = outer * (1f - i / AuraRanks.LADDER.size.toFloat() * RING_SPREAD)
                    drawCircle(color.copy(alpha = RING_ALPHA), radius = radius)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (larry) R.drawable.ic_rank_larper_larry else EMBLEMS[rank.tier]),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(if (glow) size * ICON_SHARE else size),
        )
    }
}

private const val RING_ALPHA = 0.06f
private const val RING_SPREAD = 0.8f
private const val ICON_SHARE = 0.6f
