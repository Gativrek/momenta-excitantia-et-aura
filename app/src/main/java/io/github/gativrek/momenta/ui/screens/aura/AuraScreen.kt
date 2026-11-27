package io.github.gativrek.momenta.ui.screens.aura

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.gativrek.momenta.App
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.data.aura.AuraProfile
import io.github.gativrek.momenta.data.aura.AuraRanks
import io.github.gativrek.momenta.data.aura.AuraStats
import io.github.gativrek.momenta.data.aura.MonthRank
import io.github.gativrek.momenta.data.library.LibraryFoldersStore
import io.github.gativrek.momenta.data.library.LibraryRepository
import io.github.gativrek.momenta.data.listening.ListeningDao
import io.github.gativrek.momenta.data.moments.MomentsRepository
import io.github.gativrek.momenta.playback.PlayerConnection
import io.github.gativrek.momenta.ui.components.ArtworkImage
import io.github.gativrek.momenta.ui.screens.moments.SavedMoment
import io.github.gativrek.momenta.ui.theme.moment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class AuraViewModel(
    private val connection: PlayerConnection,
    library: LibraryRepository,
    moments: MomentsRepository,
    listening: ListeningDao,
    folders: LibraryFoldersStore,
) : ViewModel() {
    /** Null until the log has been read once. Folders left out of the library are left out here too. */
    val profile: StateFlow<AuraProfile?> =
        combine(listening.observeListens(), folders.choices) { listens, choices ->
            val kept = listens.filter { choices.isIncluded(it.folderPath) }
            AuraStats.profile(kept, System.currentTimeMillis(), ZoneId.systemDefault())
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Every Moment with its song, most heard first. */
    val moments: StateFlow<List<SavedMoment>> =
        combine(library.tracks, moments.byTrack) { tracks, byTrack ->
            val byId = tracks.associateBy { it.id }
            byTrack
                .flatMap { (trackId, list) ->
                    val track = byId[trackId] ?: return@flatMap emptyList()
                    list.map { SavedMoment(track, it) }
                }.sortedWith(compareByDescending<SavedMoment> { it.moment.heardCount }.thenBy { it.moment.createdAt })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun play(saved: SavedMoment) = connection.playTracks(listOf(saved.track), 0, saved.moment.runUpStartMs)

    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    val c = (this[APPLICATION_KEY] as App).container
                    AuraViewModel(
                        c.playerConnection,
                        c.libraryRepository,
                        c.momentsRepository,
                        c.database.listeningDao(),
                        c.libraryFolders,
                    )
                }
            }
    }
}

/**
 * Your Aura: a rank earned by minutes listened this calendar month, from Static up to The
 * Aura Farmer, back to the bottom on the 1st. Below it the ladder, how earlier months
 * ranked, time spent inside Moments, the songs and artists you keep coming back to, and what
 * was playing on this day in earlier years. Shareable as a card in your own theme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraScreen(
    onBack: () -> Unit,
    onPlayStarted: () -> Unit,
    viewModel: AuraViewModel = viewModel(factory = AuraViewModel.Factory),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val moments by viewModel.moments.collectAsStateWithLifecycle()
    var sharing by rememberSaveable { mutableStateOf(false) }
    val hypeMs = AuraStats.hypeMs(moments.map { it.moment })
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.aura_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (profile?.isEmpty == false) {
                        IconButton(onClick = { sharing = true }) {
                            Icon(
                                painterResource(R.drawable.ic_share),
                                contentDescription = stringResource(R.string.aura_share),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        val p = profile ?: return@Scaffold
        if (p.isEmpty) {
            EmptyAura(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item(key = "rank") { RankHero(p.thisMonth) }
            ladder(p.thisMonth)
            pastMonths(p)
            item(key = "tiles") { StatTiles(p, hypeMs) }
            topMoments(moments.filter { it.moment.heardCount > 0 }.take(TOP_MOMENTS)) {
                viewModel.play(it)
                onPlayStarted()
            }
            topSongs(p)
            topArtists(p)
            onThisDay(p)
            item { Footer(p.since) }
        }
    }
    if (sharing) {
        profile?.let { p ->
            ShareAuraDialog(
                profile = p,
                hypeMs = hypeMs,
                topMoment = moments.firstOrNull { it.moment.heardCount > 0 },
                onDismiss = { sharing = false },
            )
        }
    }
}

/** The rank this month has reached, and how far it is to the next. */
@Composable
private fun RankHero(month: MonthRank) {
    val rank = month.rank
    val next = AuraRanks.next(rank)
    val daysToReset = month.month.lengthOfMonth() - LocalDate.now().dayOfMonth + 1
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.aura_rank_lead, monthName(month.month)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = rankName(rank, month.larry),
                    style = MaterialTheme.typography.headlineLarge,
                    color = rankColor(rank, month.larry),
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = stringResource(R.string.aura_rank_listened, formatSpan(month.listenedMs)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            RankEmblem(rank, larry = month.larry, size = HERO_EMBLEM, modifier = Modifier.padding(start = 12.dp))
        }
        if (month.larry) {
            Text(
                text = stringResource(R.string.aura_larry_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = rankColor(rank, larry = true),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        LinearProgressIndicator(
            progress = { AuraRanks.progress(month.listenedMs, rank) },
            color = rankColor(next ?: rank),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            drawStopIndicator = {},
        )
        Text(
            text =
                if (next != null) {
                    stringResource(
                        R.string.aura_rank_to_next,
                        // Rounded up to the minute, so "x more" really is enough.
                        formatSpan((next.minMs - month.listenedMs + 59_999) / 60_000 * 60_000),
                        rankName(next),
                    )
                } else {
                    stringResource(R.string.aura_rank_top)
                },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        // Only The Aura Farmer gets told.
        if (next == null) {
            Text(
                text = stringResource(R.string.aura_rank_top_secret),
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (rank.tier > 0) {
            Text(
                text = pluralStringResource(R.plurals.aura_rank_resets, daysToReset, daysToReset),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Every rank, top first, with what it takes and where you stand. */
private fun LazyListScope.ladder(month: MonthRank) {
    val current = month.rank
    section(R.string.aura_ladder)
    items(AuraRanks.LADDER.reversed(), key = { "ladder-${it.tier}" }) { rank ->
        val reached = rank.tier <= current.tier
        val here = rank == current
        // Larper Larry stands in for Static while you're him.
        val larry = here && month.larry
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RankEmblem(
                rank,
                larry = larry,
                size = LADDER_EMBLEM,
                glow = false,
                tint = if (reached) null else MaterialTheme.colorScheme.outlineVariant,
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(
                    text = rankName(rank, larry),
                    style = if (here) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    color =
                        when {
                            here -> rankColor(rank, larry)
                            reached -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (here) {
                    Text(
                        text = stringResource(R.string.aura_ladder_you),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text =
                    if (rank.minMinutes == 0L) {
                        stringResource(R.string.aura_ladder_start)
                    } else {
                        stringResource(R.string.aura_ladder_hours, (rank.minMinutes / 60).toInt())
                    },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Earlier months: the rank each one reached, and its era if an album stood out. */
private fun LazyListScope.pastMonths(profile: AuraProfile) {
    if (profile.pastMonths.isEmpty()) return
    section(R.string.aura_past_months)
    items(profile.pastMonths, key = { "month-${it.month}" }) { month ->
        val era = month.era?.let { stringResource(R.string.aura_era, it.name) }
        val name = monthName(month.month) + " " + month.month.year
        RankRow(
            rank = null,
            title = rankName(month.rank, month.larry),
            subtitle = if (era != null) "$name · $era" else name,
            value = formatSpan(month.listenedMs),
            titleColor = rankColor(month.rank, month.larry).takeIf { month.rank.isTop || month.larry },
            emblem = { RankEmblem(month.rank, larry = month.larry, size = LADDER_EMBLEM, glow = false) },
        )
    }
}

@Composable
private fun StatTiles(
    profile: AuraProfile,
    hypeMs: Long,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatTile(formatSpan(profile.totalListenedMs), stringResource(R.string.aura_stat_listened), Modifier.weight(1f))
        StatTile(
            formatSpan(hypeMs),
            stringResource(R.string.aura_card_hype),
            Modifier.weight(1f),
            accent = true,
        )
        StatTile(
            profile.songsHeard.toString(),
            pluralStringResource(R.plurals.aura_stat_songs, profile.songsHeard),
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = TILE_ALPHA),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = if (accent) MaterialTheme.colorScheme.moment else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

private fun LazyListScope.section(title: Int) {
    item(contentType = "section") {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 8.dp),
        )
    }
}

private fun LazyListScope.topMoments(
    moments: List<SavedMoment>,
    onPlay: (SavedMoment) -> Unit,
) {
    if (moments.isEmpty()) return
    section(R.string.aura_top_moments)
    items(moments, key = { "moment-${it.moment.id}" }) { saved ->
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { onPlay(saved) }
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkImage(track = saved.track, size = 44.dp)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(
                    text = saved.moment.name?.takeIf(String::isNotBlank) ?: saved.track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // A named Moment leads with its name, then the song; otherwise the song, then the artist.
                Text(
                    text =
                        if (saved.moment.name.isNullOrBlank()) {
                            saved.track.artist ?: stringResource(R.string.unknown_artist)
                        } else {
                            saved.track.title
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text =
                    pluralStringResource(
                        R.plurals.moments_heard_short,
                        saved.moment.heardCount,
                        saved.moment.heardCount,
                    ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.moment,
            )
        }
    }
}

private fun LazyListScope.topSongs(profile: AuraProfile) {
    if (profile.topSongs.isEmpty()) return
    section(R.string.aura_top_songs)
    itemsIndexed(profile.topSongs, key = { _, it -> "song-${it.songId}" }) { index, song ->
        RankRow(
            rank = index + 1,
            title = song.title,
            subtitle = song.artist ?: stringResource(R.string.unknown_artist),
            value = formatSpan(song.listenedMs),
        )
    }
}

private fun LazyListScope.topArtists(profile: AuraProfile) {
    if (profile.topArtists.isEmpty()) return
    section(R.string.aura_top_artists)
    itemsIndexed(profile.topArtists, key = { _, it -> "artist-${it.name}" }) { index, artist ->
        RankRow(rank = index + 1, title = artist.name, subtitle = null, value = formatSpan(artist.listenedMs))
    }
}

private fun LazyListScope.onThisDay(profile: AuraProfile) {
    if (profile.onThisDay.isEmpty()) return
    section(R.string.aura_on_this_day)
    profile.onThisDay.forEach { day ->
        items(day.songs, key = { "day-${day.yearsAgo}-${it.songId}" }) { song ->
            RankRow(
                rank = null,
                title = song.title,
                subtitle = pluralStringResource(R.plurals.aura_years_ago, day.yearsAgo, day.yearsAgo),
                value = formatSpan(song.listenedMs),
            )
        }
    }
}

@Composable
private fun RankRow(
    rank: Int?,
    title: String,
    subtitle: String?,
    value: String,
    titleColor: Color? = null,
    emblem: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (emblem != null) {
            emblem()
            Spacer(Modifier.width(16.dp))
        }
        if (rank != null) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(32.dp),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor ?: Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Footer(since: Long?) {
    val date =
        since?.let {
            DateTimeFormatter
                .ofLocalizedDate(FormatStyle.LONG)
                .format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
        }
    Text(
        text = date?.let { stringResource(R.string.aura_footer, it) } ?: "",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp),
    )
}

@Composable
private fun EmptyAura(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.ic_aura),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = stringResource(R.string.aura_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = stringResource(R.string.aura_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** "45 s", "18 min", "3 h 12 min". */
internal fun formatSpan(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        ms <= 0 -> "0 min"
        minutes == 0L -> "${ms / 1_000} s"
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}

private const val TOP_MOMENTS = 3
private val HERO_EMBLEM = 88.dp
private val LADDER_EMBLEM = 28.dp
private const val TILE_ALPHA = 0.7f
