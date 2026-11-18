package io.github.gativrek.momenta.data.settings

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

/** App-wide switches for Moments. */
data class MomentsSettings(
    /** Volume boosts during Moments, for every Moment at once. */
    val boosts: Boolean = true,
    /**
     * Chill mode (studying): hype off. No boosts and no Moment reactions, without touching
     * any Moment's own settings, so turning it off brings everything back as it was.
     */
    val chill: Boolean = false,
) {
    val boostsActive: Boolean get() = boosts && !chill
    val reactionsActive: Boolean get() = !chill
}

class MomentsSettingsStore(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    val settings: StateFlow<MomentsSettings> =
        dataStore.data
            .map { MomentsSettings(boosts = it[BOOSTS] ?: true, chill = it[CHILL] ?: false) }
            .stateIn(scope, SharingStarted.Eagerly, MomentsSettings())

    fun setBoosts(on: Boolean) = write(BOOSTS, on)

    fun setChill(on: Boolean) = write(CHILL, on)

    private fun write(
        key: Preferences.Key<Boolean>,
        value: Boolean,
    ) {
        scope.launch { dataStore.edit { it[key] = value } }
    }

    private companion object {
        val BOOSTS = booleanPreferencesKey("boosts")
        val CHILL = booleanPreferencesKey("chill")
    }
}
