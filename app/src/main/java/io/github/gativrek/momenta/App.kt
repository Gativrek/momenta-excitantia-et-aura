package io.github.gativrek.momenta

import android.app.Application

/**
 * Application entry point. Its only job is to own the [AppContainer], so every
 * long-lived object (repositories, database, DataStore) is created exactly once per process.
 */
class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
