package com.softdread.widgets.data.weather

import com.softdread.widgets.domain.logic.WeatherReading

/** A geographic point a forecast can be requested for. */
data class WeatherQuery(
    val latitude: Double,
    val longitude: Double,
    val locationName: String?,
)

/** The outcome of one provider call. */
sealed interface WeatherFetch {
    data class Success(val reading: WeatherReading) : WeatherFetch
    data class Failure(val reason: FailureReason, val message: String? = null) : WeatherFetch
}

enum class FailureReason { NETWORK, PROVIDER_ERROR, NOT_CONFIGURED }

/**
 * A weather source.
 *
 * Widgets depend on this interface rather than on a provider, so swapping
 * Open-Meteo for a paid service later means adding one class and changing one
 * construction site. Implementations must not throw; a failed call is a
 * [WeatherFetch.Failure] so the widget can fall back to its cache.
 */
interface WeatherProvider {
    val attribution: String
    val requiresApiKey: Boolean
    suspend fun fetch(query: WeatherQuery): WeatherFetch
}
