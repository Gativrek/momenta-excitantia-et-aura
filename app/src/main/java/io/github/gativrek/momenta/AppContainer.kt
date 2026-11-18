package io.github.gativrek.momenta

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.gativrek.momenta.data.library.LibraryRepository
import io.github.gativrek.momenta.data.library.MediaStoreSource
import io.github.gativrek.momenta.data.moments.MomentsRepository
import io.github.gativrek.momenta.data.playlists.MomentaDatabase
import io.github.gativrek.momenta.data.playlists.PlaylistRepository
import io.github.gativrek.momenta.data.settings.AppearanceStore
import io.github.gativrek.momenta.data.settings.MomentsSettingsStore
import io.github.gativrek.momenta.data.tags.TagWriter
import io.github.gativrek.momenta.playback.PlaybackStateStore
import io.github.gativrek.momenta.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus

/**
 * Manual dependency injection: the single place that constructs repositories and
 * storage, and hands them to ViewModels and the playback service via constructors.
 *
 * It holds the *application* context only, so nothing in here can leak an Activity.
 * Members are `by lazy` so startup stays cheap and nothing runs before it's needed.
 */
class AppContainer(
    context: Context,
) {
    private val appContext: Context = context.applicationContext

    /**
     * Lives as long as the process. For work that must outlive any one screen, such as
     * sharing the library flow between tabs. SupervisorJob: one failure doesn't cancel the rest.
     */
    val applicationScope = CoroutineScope(SupervisorJob())

    val mediaStoreSource: MediaStoreSource by lazy { MediaStoreSource(appContext.contentResolver) }

    val libraryRepository: LibraryRepository by lazy {
        LibraryRepository(
            resolver = appContext.contentResolver,
            source = mediaStoreSource,
            scope = applicationScope,
        )
    }

    /** One DataStore per file per process, hence created here and nowhere else. */
    val playbackStateStore: PlaybackStateStore by lazy {
        PlaybackStateStore(
            PreferenceDataStoreFactory.create(scope = applicationScope + Dispatchers.IO) {
                appContext.preferencesDataStoreFile(PLAYBACK_STATE_FILE)
            },
        )
    }

    val database: MomentaDatabase by lazy { MomentaDatabase.create(appContext) }

    val playlistRepository: PlaylistRepository by lazy {
        PlaylistRepository(database.playlistDao(), libraryRepository, applicationScope)
    }

    /** Every Moment, matched to the library's current files. */
    val momentsRepository: MomentsRepository by lazy {
        MomentsRepository(database.momentsDao(), database.listeningDao(), libraryRepository, applicationScope)
    }

    val momentsSettings: MomentsSettingsStore by lazy {
        MomentsSettingsStore(
            PreferenceDataStoreFactory.create(scope = applicationScope + Dispatchers.IO) {
                appContext.preferencesDataStoreFile(MOMENTS_SETTINGS_FILE)
            },
            scope = applicationScope,
        )
    }

    /** Writes tags into music files (and repairs an interrupted write at startup). */
    val tagWriter: TagWriter by lazy { TagWriter(appContext) }

    /** The user's theme. Created on first use, which is the first frame: MainActivity reads it at once. */
    val appearanceStore: AppearanceStore by lazy {
        AppearanceStore(
            PreferenceDataStoreFactory.create(scope = applicationScope + Dispatchers.IO) {
                appContext.preferencesDataStoreFile(APPEARANCE_FILE)
            },
            scope = applicationScope,
        )
    }

    /** Connected/disconnected by MainActivity's onStart/onStop. */
    val playerConnection: PlayerConnection by lazy { PlayerConnection(appContext) }

    private companion object {
        /** Also named in the backup rules (res/xml), which exclude it. */
        const val PLAYBACK_STATE_FILE = "playback_state"
        const val MOMENTS_SETTINGS_FILE = "moments_settings"

        /** Backed up like any setting: a theme is worth keeping on a new phone. */
        const val APPEARANCE_FILE = "appearance"
    }
}
