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
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

internal val Context.weatherDataStore: DataStore<Preferences> by preferencesDataStore(name = "soft_dread_weather")

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
    /** Injectable so tests get a store of their own; production uses the app's. */
    private val cacheStore: DataStore<Preferences> = context.weatherDataStore,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val cacheMapSerializer = MapSerializer(String.serializer(), CachedWeather.serializer())

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

    /**
     * Resolves what the tile should show.
     *
     * [allowNetworkWhenCached] is the difference between the two callers. The
     * background worker passes `true`: it runs under a network constraint, off
     * any deadline, and refreshing the cache is its whole job. A widget build
     * passes `false`, because it can be running inside a broadcast — the
     * platform's periodic update or a tap — where a request that waits on a
     * slow network holds the receiver open and risks an ANR. A widget therefore
     * renders whatever is cached for its location, honestly labelled when it is
     * past its freshness window, and only reaches for the network when it has
     * nothing at all for that place to show.
     */
    suspend fun state(
        savedLocation: SavedLocation?,
        useDeviceLocation: Boolean,
        forceRefresh: Boolean = false,
        allowNetworkWhenCached: Boolean = true,
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
        if (!allowNetworkWhenCached && cachedEntry != null) {
            return WeatherState.Ready(cachedEntry.first, isStale = true)
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

    /**
     * Returns the cached reading and its age, if one exists for [query].
     *
     * The cache holds one entry per place, not one entry overall. With a single
     * slot, two Weather widgets set to different cities evicted each other on
     * every refresh, so neither ever had a usable cache — and the pack promises
     * that instances are independent.
     */
    private suspend fun cached(query: WeatherQuery?): Pair<WeatherReading, Long>? {
        val entries = readCache()
        val cache = if (query == null) {
            entries.values.maxByOrNull { it.fetchedAtEpochMillis }
        } else {
            entries[cacheKey(query)] ?: entries.values.firstOrNull { it.matches(query) }
        } ?: return null
        val age = System.currentTimeMillis() - cache.fetchedAtEpochMillis
        return cache.reading.toReading() to age.coerceAtLeast(0)
    }

    private suspend fun readCache(): Map<String, CachedWeather> {
        val prefs = cacheStore.data.first()
        prefs[CACHE_MAP_KEY]?.let { raw ->
            runCatching { json.decodeFromString(cacheMapSerializer, raw) }.getOrNull()?.let { return it }
        }
        // A cache written by the single-slot build still reads, so upgrading
        // does not throw away the forecast the user already has.
        val legacy = prefs[CACHE_KEY]
            ?.let { runCatching { json.decodeFromString(CachedWeather.serializer(), it) }.getOrNull() }
        return legacy?.let { mapOf(cacheKey(it.latitude, it.longitude) to it) } ?: emptyMap()
    }

    private suspend fun store(query: WeatherQuery, reading: WeatherReading) {
        val payload = CachedWeather(
            reading = reading.toCached(),
            fetchedAtEpochMillis = System.currentTimeMillis(),
            latitude = query.latitude,
            longitude = query.longitude,
        )
        val updated = (readCache() + (cacheKey(query) to payload))
            .entries
            .sortedByDescending { it.value.fetchedAtEpochMillis }
            .take(MAX_CACHED_LOCATIONS)
            .associate { it.key to it.value }
        cacheStore.edit { prefs ->
            prefs[CACHE_MAP_KEY] = json.encodeToString(cacheMapSerializer, updated)
            prefs.remove(CACHE_KEY)
        }
    }

    private fun cacheKey(query: WeatherQuery): String = cacheKey(query.latitude, query.longitude)

    /** One key per ~1 km square, matching the precision actually sent upstream. */
    private fun cacheKey(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.2f,%.2f", latitude, longitude)

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

        /** Roughly 5 km — a cache hit for "the same place". */
        private const val LOCATION_MATCH_DEGREES = 0.05

        /** How many places stay cached; more than anyone places at once. */
        private const val MAX_CACHED_LOCATIONS = 8

        private val CACHE_KEY = stringPreferencesKey("weather_cache")
        private val CACHE_MAP_KEY = stringPreferencesKey("weather_cache_by_location")
    }
}
