package com.ff.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class TriggerEdgeSide { LEFT, RIGHT }

enum class TriggerMode { TAP_ONLY, SWIPE_ONLY, TAP_AND_SWIPE }

/**
 * Live configuration of the subtle edge trigger pill. All values are persisted in DataStore
 * and flow back out as a single immutable snapshot.
 */
data class TriggerConfig(
    val edgeSide: TriggerEdgeSide = TriggerEdgeSide.RIGHT,
    val verticalBias: Float = 0.22f,
    val heightDp: Int = 56,
    val widthDp: Int = 5,
    val opacity: Float = 0.35f,
    val mode: TriggerMode = TriggerMode.TAP_AND_SWIPE,
) {
    /** Swipe activation (and therefore gesture exclusion) is active unless tap-only. */
    val swipeEnabled: Boolean get() = mode != TriggerMode.TAP_ONLY

    val tapEnabled: Boolean get() = mode != TriggerMode.SWIPE_ONLY
}

private val Context.triggerDataStore by preferencesDataStore(name = "trigger_prefs")

/** Persists the edge trigger tuning parameters. */
class TriggerPreferencesRepository(context: Context) {

    private val dataStore = context.applicationContext.triggerDataStore

    /**
     * An [IOException] (disk full, corrupted backing file) degrades to defaults instead of
     * crashing the eagerly-collected startup flow; anything else is a real bug and propagates.
     */
    val config: Flow<TriggerConfig> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            TriggerConfig(
                edgeSide = prefs[KEY_EDGE_SIDE].toEnum(TriggerEdgeSide.RIGHT),
                verticalBias = (prefs[KEY_VERTICAL_BIAS] ?: DEFAULT.verticalBias)
                    .coerceIn(VERTICAL_BIAS_RANGE),
                heightDp = (prefs[KEY_HEIGHT_DP] ?: DEFAULT.heightDp).coerceIn(HEIGHT_RANGE),
                widthDp = (prefs[KEY_WIDTH_DP] ?: DEFAULT.widthDp).coerceIn(WIDTH_RANGE),
                opacity = (prefs[KEY_OPACITY] ?: DEFAULT.opacity).coerceIn(OPACITY_RANGE),
                mode = prefs[KEY_MODE].toEnum(TriggerMode.TAP_AND_SWIPE),
            )
        }

    suspend fun setEdgeSide(side: TriggerEdgeSide) =
        dataStore.edit { it[KEY_EDGE_SIDE] = side.name }

    suspend fun setVerticalBias(bias: Float) = dataStore.edit {
        it[KEY_VERTICAL_BIAS] = bias.coerceIn(VERTICAL_BIAS_RANGE)
    }

    suspend fun setHeightDp(height: Int) = dataStore.edit {
        it[KEY_HEIGHT_DP] = height.coerceIn(HEIGHT_RANGE)
    }

    suspend fun setWidthDp(width: Int) = dataStore.edit {
        it[KEY_WIDTH_DP] = width.coerceIn(WIDTH_RANGE)
    }

    suspend fun setOpacity(opacity: Float) = dataStore.edit {
        it[KEY_OPACITY] = opacity.coerceIn(OPACITY_RANGE)
    }

    suspend fun setMode(mode: TriggerMode) = dataStore.edit { it[KEY_MODE] = mode.name }

    companion object {
        /** Single source of truth for every tunable range — the UI sliders reference these. */
        val VERTICAL_BIAS_RANGE = 0.05f..0.90f
        val HEIGHT_RANGE = 32..100
        val WIDTH_RANGE = 3..12
        val OPACITY_RANGE = 0f..1f

        /** Float copies for Material3 [androidx.compose.material3.Slider] value ranges. */
        val HEIGHT_RANGE_F = HEIGHT_RANGE.first.toFloat()..HEIGHT_RANGE.last.toFloat()
        val WIDTH_RANGE_F = WIDTH_RANGE.first.toFloat()..WIDTH_RANGE.last.toFloat()

        private val DEFAULT = TriggerConfig()

        private val KEY_EDGE_SIDE = stringPreferencesKey("trigger_edge_side")
        private val KEY_VERTICAL_BIAS = floatPreferencesKey("trigger_vertical_bias")
        private val KEY_HEIGHT_DP = intPreferencesKey("trigger_height_dp")
        private val KEY_WIDTH_DP = intPreferencesKey("trigger_width_dp")
        private val KEY_OPACITY = floatPreferencesKey("trigger_opacity")
        private val KEY_MODE = stringPreferencesKey("trigger_mode")

        private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
            this?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default
    }
}
