package io.github.gativrek.momenta

import android.content.Context
import io.github.gativrek.momenta.data.library.LibraryRepository
import io.github.gativrek.momenta.data.library.MediaStoreSource
import io.github.gativrek.momenta.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

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

    val libraryRepository: LibraryRepository by lazy {
        LibraryRepository(
            resolver = appContext.contentResolver,
            source = MediaStoreSource(appContext.contentResolver),
            scope = applicationScope,
        )
    }

    /** Connected/disconnected by MainActivity's onStart/onStop. */
    val playerConnection: PlayerConnection by lazy { PlayerConnection(appContext) }
}
