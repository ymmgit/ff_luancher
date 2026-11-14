package com.ff.launcher.data.glance

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.headerDataStore: DataStore<Preferences> by preferencesDataStore(name = "header_preferences")

enum class HeaderMode {
    COMBINED,
    CLOCK_ONLY,
    CALENDAR_ONLY,
    WEATHER_ONLY,
    HIDDEN,
}

data class HeaderConfig(
    val mode: HeaderMode = HeaderMode.COMBINED,
    val useFahrenheit: Boolean = false,
)

class HeaderPreferencesRepository(private val context: Context) {

    private companion object {
        val KEY_HEADER_MODE = stringPreferencesKey("header_mode")
        val KEY_USE_FAHRENHEIT = booleanPreferencesKey("header_use_fahrenheit")
    }

    val headerConfig: Flow<HeaderConfig> = context.headerDataStore.data.map { prefs ->
        val modeStr = prefs[KEY_HEADER_MODE] ?: HeaderMode.COMBINED.name
        val mode = runCatching { HeaderMode.valueOf(modeStr) }.getOrDefault(HeaderMode.COMBINED)
        val useFahrenheit = prefs[KEY_USE_FAHRENHEIT] ?: false
        HeaderConfig(mode = mode, useFahrenheit = useFahrenheit)
    }

    suspend fun setHeaderMode(mode: HeaderMode) {
        context.headerDataStore.edit { prefs ->
            prefs[KEY_HEADER_MODE] = mode.name
        }
    }

    suspend fun setUseFahrenheit(useFahrenheit: Boolean) {
        context.headerDataStore.edit { prefs ->
            prefs[KEY_USE_FAHRENHEIT] = useFahrenheit
        }
    }
}
