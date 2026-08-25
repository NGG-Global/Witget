package com.softdread.widgets.data.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.softdread.widgets.data.prefs.SavedLocation
import com.softdread.widgets.domain.logic.WeatherReading
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.weatherDataStore: DataStore<Preferences> by preferencesDataStore(name = "soft_dread_weather")

/** What the Weather widget should render right now. */
sealed interface WeatherState {
    /** Fresh data, or cached data still inside its freshness window. */
    data class Ready(val reading: WeatherReading, val isStale: Boolean) : WeatherState

    /** Nothing cached and no location to fetch for: the widget needs setup. */
    data object NeedsLocation : WeatherState

    /** Automatic location was chosen but the permission is not granted. */
    data object NeedsLocationPermission : WeatherState

    /** No cache and the network call failed. */
    data object Unavailable : WeatherState
}

@Serializable
private data class CachedWeather(
    val reading: CachedReading,
    val fetchedAtEpochMillis: Long,
    val latitude: Double,
    val longitude: Double,
)

@Serializable
private data class CachedReading(
    val temperatureCelsius: Double,
    val feelsLikeCelsius: Double,
    val weatherCode: Int,
    val windSpeedKmh: Double,
    val windGustKmh: Double,
    val uvIndex: Double?,
    val rainChancePercent: Int?,
    val highCelsius: Double?,
    val lowCelsius: Double?,
    val hourlyRainChance: List<Int> = emptyList(),
    val isDay: Boolean,
    val locationName: String?,
    val observedAtEpochMillis: Long,
)

/**
 * Weather with caching, staleness and a graceful offline path.
 *
 * Behaviour the product requires:
 *  - a successful fetch is cached per location and reused for [FRESH_WINDOW_MILLIS];
 *  - a failed fetch falls back to the cache and marks it stale rather than
 *    showing the user a network error;
 *  - location is optional — a manually chosen city works with no location
 *    permission at all, and the rest of the pack is unaffected either way.
 */
class WeatherRepository(
    private val context: Context,
    private val provider: WeatherProvider = OpenMeteoProvider(),
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val attribution: String get() = provider.attribution

    /**
     * Resolves the location to use, in the order the product specifies:
     * an explicitly chosen city always wins over device location, because a
     * user who picked "Tel Aviv" meant it.
     */
    fun resolveLocation(
        savedLocation: SavedLocation?,
        useDeviceLocation: Boolean,
    ): WeatherQuery? {
        savedLocation?.let {
            return WeatherQuery(it.latitude, it.longitude, it.name)
        }
        if (!useDeviceLocation) return null
        val last = lastKnownLocation() ?: return null
        return WeatherQuery(last.first, last.second, null)
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Reads the platform's last known coarse location. The widget deliberately
     * does not request active location updates: a forecast does not justify
     * waking the GPS, and the last known fix is accurate enough for a city-level
     * query.
     */
    private fun lastKnownLocation(): Pair<Double, Double>? {
        if (!hasLocationPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
            LocationManager.GPS_PROVIDER,
        )
        return providers.firstNotNullOfOrNull { name ->
            try {
                manager.getLastKnownLocation(name)
            } catch (revoked: SecurityException) {
                // The permission can be revoked between the check above and this
                // call; treating that as "no location" keeps the widget on its
                // cached reading instead of crashing the launcher.
                null
            } catch (missingProvider: IllegalArgumentException) {
                null
            }
        }?.let { it.latitude to it.longitude }
    }

    suspend fun state(
        savedLocation: SavedLocation?,
        useDeviceLocation: Boolean,
        forceRefresh: Boolean = false,
    ): WeatherState {
        val query = resolveLocation(savedLocation, useDeviceLocation)
        if (query == null) {
            return if (useDeviceLocation && !hasLocationPermission()) {
                cached(null)?.let { WeatherState.Ready(it.first, isStale = true) }
                    ?: WeatherState.NeedsLocationPermission
            } else {
                WeatherState.NeedsLocation
            }
        }

        val cachedEntry = cached(query)
        if (!forceRefresh && cachedEntry != null && cachedEntry.second <= FRESH_WINDOW_MILLIS) {
            return WeatherState.Ready(cachedEntry.first, isStale = false)
        }

        return when (val fetch = provider.fetch(query)) {
            is WeatherFetch.Success -> {
                store(query, fetch.reading)
                WeatherState.Ready(fetch.reading, isStale = false)
            }
            is WeatherFetch.Failure -> cachedEntry
                ?.let { WeatherState.Ready(it.first, isStale = true) }
                ?: WeatherState.Unavailable
        }
    }

    /** Returns the cached reading and its age, if one exists for [query]. */
    private suspend fun cached(query: WeatherQuery?): Pair<WeatherReading, Long>? {
        val raw = context.weatherDataStore.data.first()[CACHE_KEY] ?: return null
        val cache = runCatching { json.decodeFromString(CachedWeather.serializer(), raw) }.getOrNull()
            ?: return null
        if (query != null && !cache.matches(query)) return null
        val age = System.currentTimeMillis() - cache.fetchedAtEpochMillis
        return cache.reading.toReading() to age.coerceAtLeast(0)
    }

    private suspend fun store(query: WeatherQuery, reading: WeatherReading) {
        val payload = CachedWeather(
            reading = reading.toCached(),
            fetchedAtEpochMillis = System.currentTimeMillis(),
            latitude = query.latitude,
            longitude = query.longitude,
        )
        context.weatherDataStore.edit { prefs ->
            prefs[CACHE_KEY] = json.encodeToString(CachedWeather.serializer(), payload)
        }
    }

    private fun CachedWeather.matches(query: WeatherQuery): Boolean =
        kotlin.math.abs(latitude - query.latitude) < LOCATION_MATCH_DEGREES &&
            kotlin.math.abs(longitude - query.longitude) < LOCATION_MATCH_DEGREES

    private fun WeatherReading.toCached() = CachedReading(
        temperatureCelsius, feelsLikeCelsius, weatherCode, windSpeedKmh, windGustKmh,
        uvIndex, rainChancePercent, highCelsius, lowCelsius, hourlyRainChance, isDay,
        locationName, observedAtEpochMillis,
    )

    private fun CachedReading.toReading() = WeatherReading(
        temperatureCelsius, feelsLikeCelsius, weatherCode, windSpeedKmh, windGustKmh,
        uvIndex, rainChancePercent, highCelsius, lowCelsius, hourlyRainChance, isDay,
        locationName, observedAtEpochMillis,
    )

    companion object {
        /** Cached forecasts stay authoritative for an hour before a refetch. */
        const val FRESH_WINDOW_MILLIS = 60L * 60L * 1000L

        /** Data older than this is labelled stale to the user. */
        const val STALE_AFTER_MILLIS = 3L * 60L * 60L * 1000L

        /** Roughly 5 km — a cache hit for "the same place". */
        private const val LOCATION_MATCH_DEGREES = 0.05

        private val CACHE_KEY = stringPreferencesKey("weather_cache")
    }
}
