package com.ff.launcher.data.glance

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

private val Context.weatherDataStore: DataStore<Preferences> by preferencesDataStore(name = "weather_cache")

data class WeatherData(
    val tempC: Float = 22f,
    val tempF: Float = (22f * 9f / 5f) + 32f,
    val conditionCode: Int = 0,
    val conditionText: String = "Clear",
    val highC: Float = 25f,
    val lowC: Float = 19f,
    val locationName: String = "Local Weather",
    val lastUpdatedMs: Long = 0L,
    val isLoading: Boolean = false,
) {
    fun formatTemp(useFahrenheit: Boolean): String {
        val t = if (useFahrenheit) tempF.roundToInt() else tempC.roundToInt()
        val unit = if (useFahrenheit) "°F" else "°C"
        return "$t$unit"
    }

    fun conditionEmoji(): String = when (conditionCode) {
        0 -> "☀️"
        1, 2 -> "🌤️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> "🌧️"
        71, 73, 75, 77, 85, 86 -> "❄️"
        95, 96, 99 -> "⛈️"
        else -> "⛅"
    }
}

/**
 * Manages weather fetching, caching, and parsing via Open-Meteo API.
 */
class WeatherRepository(private val context: Context) {

    companion object {
        private const val TAG = "WeatherRepository"
        private const val CACHE_EXPIRATION_MS = 60 * 60 * 1000L // 1 hour

        private val KEY_TEMP_C = floatPreferencesKey("weather_temp_c")
        private val KEY_CONDITION_CODE = intPreferencesKey("weather_condition_code")
        private val KEY_CONDITION_TEXT = stringPreferencesKey("weather_condition_text")
        private val KEY_HIGH_C = floatPreferencesKey("weather_high_c")
        private val KEY_LOW_C = floatPreferencesKey("weather_low_c")
        private val KEY_LOCATION_NAME = stringPreferencesKey("weather_location_name")
        private val KEY_LAST_UPDATED = longPreferencesKey("weather_last_updated")

        fun getWeatherDescription(code: Int): String = when (code) {
            0 -> "Clear sky"
            1 -> "Mainly clear"
            2 -> "Partly cloudy"
            3 -> "Overcast"
            45, 48 -> "Foggy"
            51, 53, 55 -> "Drizzle"
            61, 63, 65 -> "Rain"
            71, 73, 75 -> "Snow"
            80, 81, 82 -> "Rain showers"
            95, 96, 99 -> "Thunderstorm"
            else -> "Cloudy"
        }
    }

    val cachedWeather: Flow<WeatherData> = context.weatherDataStore.data.map { prefs ->
        val tempC = prefs[KEY_TEMP_C] ?: 24f
        val code = prefs[KEY_CONDITION_CODE] ?: 0
        WeatherData(
            tempC = tempC,
            tempF = (tempC * 9f / 5f) + 32f,
            conditionCode = code,
            conditionText = prefs[KEY_CONDITION_TEXT] ?: getWeatherDescription(code),
            highC = prefs[KEY_HIGH_C] ?: (tempC + 3f),
            lowC = prefs[KEY_LOW_C] ?: (tempC - 3f),
            locationName = prefs[KEY_LOCATION_NAME] ?: "Local Weather",
            lastUpdatedMs = prefs[KEY_LAST_UPDATED] ?: 0L,
            isLoading = false,
        )
    }

    suspend fun getOrFetchWeather(forceRefresh: Boolean = false): WeatherData = withContext(Dispatchers.IO) {
        val current = cachedWeather.first()
        val now = System.currentTimeMillis()
        if (!forceRefresh && (now - current.lastUpdatedMs) < CACHE_EXPIRATION_MS && current.lastUpdatedMs > 0L) {
            return@withContext current
        }

        val location = resolveLocation()
        val lat = location?.latitude ?: 37.7749 // San Francisco default if no location
        val lon = location?.longitude ?: -122.4194

        val fetched = fetchFromOpenMeteo(lat, lon)
        if (fetched != null) {
            saveToCache(fetched)
            return@withContext fetched
        }
        current
    }

    @SuppressLint("MissingPermission")
    private fun resolveLocation(): Location? {
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCoarse) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return runCatching {
            lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }.getOrNull()
    }

    private fun fetchFromOpenMeteo(lat: Double, lon: Double): WeatherData? {
        return runCatching {
            val urlString = "https://api.open-meteo.com/v1/forecast?" +
                "latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                "&timezone=auto"

            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.requestMethod = "GET"

            if (conn.responseCode != 200) {
                Log.w(TAG, "Open-Meteo returned HTTP ${conn.responseCode}")
                return null
            }

            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val jsonText = reader.readText()
            reader.close()

            val root = JSONObject(jsonText)
            val currentObj = root.getJSONObject("current")
            val tempC = currentObj.getDouble("temperature_2m").toFloat()
            val code = currentObj.getInt("weather_code")

            val dailyObj = root.optJSONObject("daily")
            val maxArray = dailyObj?.optJSONArray("temperature_2m_max")
            val minArray = dailyObj?.optJSONArray("temperature_2m_min")
            val highC = maxArray?.optDouble(0, (tempC + 3).toDouble())?.toFloat() ?: (tempC + 3f)
            val lowC = minArray?.optDouble(0, (tempC - 3).toDouble())?.toFloat() ?: (tempC - 3f)

            val tz = root.optString("timezone", "").substringAfterLast("/").replace("_", " ")
            val locName = if (tz.isNotBlank()) tz else "Local Weather"

            WeatherData(
                tempC = tempC,
                tempF = (tempC * 9f / 5f) + 32f,
                conditionCode = code,
                conditionText = getWeatherDescription(code),
                highC = highC,
                lowC = lowC,
                locationName = locName,
                lastUpdatedMs = System.currentTimeMillis(),
                isLoading = false,
            )
        }.onFailure { Log.w(TAG, "Weather fetch error", it) }.getOrNull()
    }

    private suspend fun saveToCache(data: WeatherData) {
        context.weatherDataStore.edit { prefs ->
            prefs[KEY_TEMP_C] = data.tempC
            prefs[KEY_CONDITION_CODE] = data.conditionCode
            prefs[KEY_CONDITION_TEXT] = data.conditionText
            prefs[KEY_HIGH_C] = data.highC
            prefs[KEY_LOW_C] = data.lowC
            prefs[KEY_LOCATION_NAME] = data.locationName
            prefs[KEY_LAST_UPDATED] = data.lastUpdatedMs
        }
    }
}
