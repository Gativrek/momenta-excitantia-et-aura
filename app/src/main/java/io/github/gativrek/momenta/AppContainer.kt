package io.github.gativrek.momenta

import android.content.Context

/**
 * Manual dependency injection: the single place that constructs repositories and
 * storage, and hands them to ViewModels and the playback service via constructors.
 *
 * It holds the *application* context only, so nothing in here can leak an Activity.
 * Members are added phase by phase (LibraryRepository in Phase 1, PlaylistRepository
 * in Phase 5, ...) — typically as `val x by lazy { ... }` so startup stays cheap.
 */
class AppContainer(
    context: Context,
) {
    @Suppress("unused") // Consumed by the repositories added in Phase 1.
    private val appContext: Context = context.applicationContext
}
