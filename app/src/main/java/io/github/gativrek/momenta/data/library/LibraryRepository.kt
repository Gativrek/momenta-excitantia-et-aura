package io.github.gativrek.momenta.data.library

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn

/**
 * The music library as a live stream. MediaStore is the source of truth (KICKOFF.md):
 * nothing is cached on disk, we re-query whenever MediaStore says something changed.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class LibraryRepository(
    private val resolver: ContentResolver,
    private val source: MediaStoreSource,
    scope: CoroutineScope,
) {
    /** Fires whenever any audio row is added, removed or edited (e.g. files copied over USB). */
    private val mediaStoreChanges: Flow<Unit> =
        callbackFlow {
            val observer =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        trySend(Unit)
                    }
                }
            // notifyForDescendants = true: changes to individual rows (…/media/123) count too.
            resolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
            awaitClose { resolver.unregisterContentObserver(observer) }
        }

    /**
     * Every music track, unsorted. Loads immediately on first collection, then reloads
     * 500 ms after a burst of MediaStore changes settles (a USB copy fires hundreds).
     *
     * Shared: all screens collecting this trigger a single query, and the result is kept
     * for 5 s after the last collector leaves so tab switches and rotation don't re-query.
     */
    val tracks: Flow<List<Track>> =
        merge(flowOf(Unit), mediaStoreChanges.debounce(CHANGE_DEBOUNCE_MS))
            .mapLatest { loadOrEmpty() }
            .shareIn(scope, SharingStarted.WhileSubscribed(KEEP_ALIVE_MS), replay = 1)

    /** Tracks sorted for the Songs list. Sorting runs off the main thread. */
    val tracksByTitle: Flow<List<Track>> =
        tracks
            .map { it.sortedWith(TrackFields.titleComparator()) }
            .flowOn(Dispatchers.Default)

    /** Albums, alphabetical. Grouping runs off the main thread. */
    val albums: Flow<List<Album>> = tracks.map { LibraryGrouping.albums(it) }.flowOn(Dispatchers.Default)

    /** Artists, alphabetical, unknown last. */
    val artists: Flow<List<Artist>> = tracks.map { LibraryGrouping.artists(it) }.flowOn(Dispatchers.Default)

    /** One album, or null once it no longer exists (e.g. its files were deleted). */
    fun album(id: Long): Flow<Album?> = albums.map { list -> list.firstOrNull { it.id == id } }

    fun artist(id: Long): Flow<Artist?> = artists.map { list -> list.firstOrNull { it.id == id } }

    /** One level of the folder tree; "" is the root. */
    fun folder(path: String): Flow<FolderContents> =
        tracks.map { LibraryGrouping.folderContents(it, path) }.flowOn(Dispatchers.Default)

    private suspend fun loadOrEmpty(): List<Track> =
        try {
            source.queryTracks()
        } catch (e: SecurityException) {
            // Permission missing or revoked. The UI gates on the permission, so this is a race.
            Log.w(TAG, "Audio permission missing while querying MediaStore", e)
            emptyList()
        }

    private companion object {
        const val TAG = "LibraryRepository"
        const val CHANGE_DEBOUNCE_MS = 500L
        const val KEEP_ALIVE_MS = 5_000L
    }
}
