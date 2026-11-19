package io.github.gativrek.momenta.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.gativrek.momenta.data.library.Album
import io.github.gativrek.momenta.data.library.AlbumKey
import io.github.gativrek.momenta.data.library.Track
import io.github.gativrek.momenta.ui.theme.ThemeSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Themes pinned to albums and to single songs: while a pinned song (or a song from a pinned
 * album) is the one playing, the whole app wears that theme, and goes back to the user's own
 * theme after. A song's own pin wins over its album's.
 *
 * Albums are keyed by AlbumKey and songs by folder and file name, so pins survive rescans.
 */
class ThemePinsStore(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    /** Album key → the theme pinned to it. */
    val pins: StateFlow<Map<String, ThemeSpec>> = pinsWith(PREFIX)

    /** Song key ([songKeyOf]) → the theme pinned to that song. */
    val songPins: StateFlow<Map<String, ThemeSpec>> = pinsWith(SONG_PREFIX)

    private fun pinsWith(prefix: String): StateFlow<Map<String, ThemeSpec>> =
        dataStore.data
            .map { prefs ->
                prefs
                    .asMap()
                    .mapNotNull { (key, value) ->
                        if (!key.name.startsWith(prefix) || value !is String) return@mapNotNull null
                        key.name.removePrefix(prefix) to ThemeSpec.decode(value)
                    }.toMap()
            }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Pins [spec] to the song [track], or unpins it when [spec] is null. */
    fun pinSong(
        track: Track,
        spec: ThemeSpec?,
    ) {
        val key = stringPreferencesKey(SONG_PREFIX + songKeyOf(track))
        scope.launch { dataStore.edit { if (spec == null) it.remove(key) else it[key] = spec.encode() } }
    }

    /** Pins [spec] to [album], or unpins it when [spec] is null. */
    fun pin(
        album: Album,
        spec: ThemeSpec?,
    ) {
        val keys = keysOf(album)
        scope.launch {
            dataStore.edit { prefs ->
                keys.forEach { k ->
                    val key = stringPreferencesKey(PREFIX + k)
                    if (spec == null) prefs.remove(key) else prefs[key] = spec.encode()
                }
            }
        }
    }

    fun pinnedTo(album: Album): ThemeSpec? = keysOf(album).firstNotNullOfOrNull { pins.value[it] }

    companion object {
        private const val PREFIX = "pin:"
        private const val SONG_PREFIX = "song:"

        /** A song by its folder and file name: still the same song after a rescan. */
        fun songKeyOf(track: Track): String = track.folderPath + track.displayName

        fun keyOf(track: Track): String = AlbumKey.of(track)

        fun keysOf(album: Album): Set<String> = AlbumKey.of(album)
    }
}
