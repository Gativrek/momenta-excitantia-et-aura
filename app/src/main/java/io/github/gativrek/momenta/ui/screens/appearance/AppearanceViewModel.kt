package io.github.gativrek.momenta.ui.screens.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.gativrek.momenta.App
import io.github.gativrek.momenta.data.settings.AppearanceStore
import io.github.gativrek.momenta.ui.theme.ThemePreset
import io.github.gativrek.momenta.ui.theme.ThemeSpec
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn

/** The Appearance screen: every edit applies to the whole app at once and is saved. */
class AppearanceViewModel(
    private val store: AppearanceStore,
) : ViewModel() {
    val theme: StateFlow<ThemeSpec> =
        store.theme
            .filterNotNull()
            .stateIn(viewModelScope, SharingStarted.Eagerly, store.theme.value ?: ThemeSpec.Default)

    fun choosePreset(preset: ThemePreset) = store.set(preset.spec)

    fun update(transform: (ThemeSpec) -> ThemeSpec) = store.set(transform(theme.value))

    /** Back to the untouched preset the current theme started from. */
    fun reset() = store.set(theme.value.preset.spec)

    companion object {
        val Factory =
            viewModelFactory {
                initializer { AppearanceViewModel((this[APPLICATION_KEY] as App).container.appearanceStore) }
            }
    }
}
