package io.github.gativrek.momenta.data.library

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Which folders' audio is in the library. Android marks plenty of non-music audio as music
 * (WhatsApp's shared audio, for one), so the library goes by folder: the user's own
 * choices, and for folders they haven't decided on, a default that leaves out messaging
 * apps, recorders and system sounds.
 */
data class FolderChoices(
    /** Folder path ("Music/Album/") → in the library or not, as the user chose. */
    val choices: Map<String, Boolean> = emptyMap(),
) {
    fun isIncluded(folderPath: String): Boolean = choices[folderPath] ?: !LibraryFolders.hiddenByDefault(folderPath)
}

/** A folder that holds audio, for the chooser. */
data class AudioFolder(
    val path: String,
    val trackCount: Int,
    val included: Boolean,
)

object LibraryFolders {
    /** Folder names (any level) that contain these are messaging apps or recorders: not music. */
    private val NOT_MUSIC_PARTS =
        listOf(
            "whatsapp",
            "telegram",
            "signal",
            "messenger",
            "viber",
            "wechat",
            "discord",
            "recording",
            "recorder",
            "voice note",
        )

    /** Folders with exactly these names hold system sounds. */
    private val SYSTEM_SOUNDS = setOf("notifications", "ringtones", "alarms", "call", "calls")

    fun hiddenByDefault(folderPath: String): Boolean =
        folderPath
            .lowercase(Locale.ROOT)
            .split('/')
            .any { part -> part in SYSTEM_SOUNDS || NOT_MUSIC_PARTS.any { it in part } }

    /** Every folder that holds audio, with how many files and whether it's in the library. */
    fun summarize(
        allTracks: List<Track>,
        choices: FolderChoices,
    ): List<AudioFolder> =
        allTracks
            .groupingBy { it.folderPath }
            .eachCount()
            .map { (path, count) -> AudioFolder(path, count, choices.isIncluded(path)) }
            .sortedBy { it.path.lowercase(Locale.ROOT) }
}

/** The user's folder choices, kept with the other settings. */
class LibraryFoldersStore(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    val choices: StateFlow<FolderChoices> =
        dataStore.data
            .map { prefs ->
                FolderChoices(
                    prefs
                        .asMap()
                        .mapNotNull { (key, value) ->
                            if (!key.name.startsWith(PREFIX) || value !is Boolean) return@mapNotNull null
                            key.name.removePrefix(PREFIX) to value
                        }.toMap(),
                )
            }.stateIn(scope, SharingStarted.Eagerly, FolderChoices())

    fun setIncluded(
        folderPath: String,
        included: Boolean,
    ) {
        scope.launch { dataStore.edit { it[booleanPreferencesKey(PREFIX + folderPath)] = included } }
    }

    private companion object {
        const val PREFIX = "folder:"
    }
}
