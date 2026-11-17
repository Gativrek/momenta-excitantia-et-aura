package io.github.gativrek.momenta

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import io.github.gativrek.momenta.data.library.ArtworkFetcher
import io.github.gativrek.momenta.data.library.ArtworkKeyer

/**
 * Application entry point. Owns the [AppContainer], so every long-lived object
 * (repositories, database, DataStore) is created exactly once per process.
 *
 * Also configures Coil's app-wide ImageLoader: implementing [SingletonImageLoader.Factory]
 * here is how Coil 3 discovers it, and it's what `AsyncImage` uses everywhere.
 */
class App :
    Application(),
    SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components {
                add(ArtworkFetcher.Factory(contentResolver))
                add(ArtworkKeyer())
            }.build()
}
