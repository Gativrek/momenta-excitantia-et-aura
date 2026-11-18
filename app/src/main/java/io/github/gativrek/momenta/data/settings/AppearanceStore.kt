package io.github.gativrek.momenta.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.gativrek.momenta.ui.theme.ThemeSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The user's theme, kept in Preferences DataStore as one encoded string.
 *
 * [theme] is null until the file has been read once (a few milliseconds at startup); the
 * Activity shows the plain window background until then, so the default theme never
 * flashes before the chosen one. Edits update [theme] at once, so dragging a slider is
 * instant, and are written in the background: the latest edit always wins, and a burst of
 * edits (a slider drag) becomes one or two writes, not one per frame.
 */
class AppearanceStore(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    private val state = MutableStateFlow<ThemeSpec?>(null)
    val theme: StateFlow<ThemeSpec?> = state.asStateFlow()
    private val writeLock = Mutex()

    /** True while a write is queued: a slider drag queues one write, not one per frame. */
    private val writeQueued = AtomicBoolean(false)

    init {
        scope.launch {
            val stored = ThemeSpec.decode(dataStore.data.first()[THEME])
            // An edit made before the first read finished wins over what was on disk.
            state.compareAndSet(null, stored)
        }
    }

    fun set(spec: ThemeSpec) {
        state.value = spec
        if (!writeQueued.compareAndSet(false, true)) return
        scope.launch {
            writeLock.withLock {
                // Cleared before reading the value: an edit landing during the write queues another.
                writeQueued.set(false)
                val latest = state.value ?: return@withLock
                dataStore.edit { it[THEME] = latest.encode() }
            }
        }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
    }
}
