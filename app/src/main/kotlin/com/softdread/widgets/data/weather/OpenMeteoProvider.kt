package com.softdread.widgets.data.weather

import com.softdread.widgets.domain.logic.WeatherReading
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Open-Meteo weather source.
 *
 * Chosen for V1 because it needs no API key, no account and no payment during
 * development, and its free tier is licensed CC BY 4.0 for non-commercial use —
 * so there is no secret to leak and nothing to configure before the app builds.
 * Attribution is surfaced in the app's settings screen and in the README.
 *
 * Coordinates are rounded to two decimals (roughly 1 km) before they leave the
 * device. That is more than precise enough for a temperature band and keeps the
 * request from carrying a house-level location.
 */
class OpenMeteoProvider(
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val timeoutMillis: Int = 10_000,
) : WeatherProvider {

    override val attribution: String = "Weather data by Open-Meteo.com (CC BY 4.0)"
    override val requiresApiKey: Boolean = false

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetch(query: WeatherQuery): WeatherFetch = withContext(Dispatchers.IO) {
        val latitude = roundCoordinate(query.latitude)
        val longitude = roundCoordinate(query.longitude)
        val url = buildString {
            append(endpoint)
            append("?latitude=").append(latitude)
            append("&longitude=").append(longitude)
            append("&current=").append(CURRENT_FIELDS)
            append("&daily=").append(DAILY_FIELDS)
            append("&hourly=precipitation_probability")
            append("&timezone=auto&forecast_days=1")
            append("&temperature_unit=celsius&wind_speed_unit=kmh")
        }

        try {
            val body = readBody(URL(url))
            val payload = json.decodeFromString(OpenMeteoResponse.serializer(), body)
            val current = payload.current
                ?: return@withContext WeatherFetch.Failure(FailureReason.PROVIDER_ERROR, "no current block")
            WeatherFetch.Success(
                WeatherReading(
                    temperatureCelsius = current.temperature,
                    feelsLikeCelsius = current.apparentTemperature ?: current.temperature,
                    weatherCode = current.weatherCode ?: 0,
                    windSpeedKmh = current.windSpeed ?: 0.0,
                    windGustKmh = current.windGust ?: 0.0,
                    uvIndex = payload.daily?.uvIndexMax?.firstOrNull(),
                    rainChancePercent = payload.daily?.precipitationProbabilityMax?.firstOrNull()
                        ?: payload.hourly?.precipitationProbability?.filterNotNull()?.maxOrNull(),
                    highCelsius = payload.daily?.temperatureMax?.firstOrNull(),
                    lowCelsius = payload.daily?.temperatureMin?.firstOrNull(),
                    hourlyRainChance = upcomingRainChance(payload),
                    isDay = (current.isDay ?: 1) == 1,
                    locationName = query.locationName,
                    observedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        } catch (io: IOException) {
            WeatherFetch.Failure(FailureReason.NETWORK, io.message)
        } catch (error: Exception) {
            WeatherFetch.Failure(FailureReason.PROVIDER_ERROR, error.message)
        }
    }

    private fun readBody(url: URL): String {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Rain probability for the hours still ahead today, which the 4x4 tile plots
     * as the sheet's five-segment strip. The API returns the whole local day, so
     * the elapsed hours are dropped before the strip is built.
     */
    private fun upcomingRainChance(payload: OpenMeteoResponse): List<Int> {
        val values = payload.hourly?.precipitationProbability ?: return emptyList()
        val times = payload.hourly.time ?: return emptyList()
        val currentHour = payload.current?.time?.substringAfter('T')?.substringBefore(':')?.toIntOrNull()
            ?: return values.filterNotNull().take(STRIP_SEGMENTS)
        val startIndex = times.indexOfFirst {
            (it.substringAfter('T').substringBefore(':').toIntOrNull() ?: 0) >= currentHour
        }.coerceAtLeast(0)
        return values.drop(startIndex).filterNotNull().take(STRIP_SEGMENTS)
    }

    /** Two decimals is about 1 km — enough for a forecast, not enough to locate a home. */
    private fun roundCoordinate(value: Double): String =
        String.format(Locale.US, "%.2f", (value * 100.0).roundToInt() / 100.0)

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.open-meteo.com/v1/forecast"
        private const val CURRENT_FIELDS =
            "temperature_2m,apparent_temperature,is_day,weather_code,wind_speed_10m,wind_gusts_10m"
        private const val DAILY_FIELDS =
            "temperature_2m_max,temperature_2m_min,uv_index_max,precipitation_probability_max"

        /** The design sheet's precipitation strip is five segments wide. */
        private const val STRIP_SEGMENTS = 5
    }
}

@Serializable
private data class OpenMeteoResponse(
    val current: OpenMeteoCurrent? = null,
    val daily: OpenMeteoDaily? = null,
    val hourly: OpenMeteoHourly? = null,
)

@Serializable
private data class OpenMeteoCurrent(
    val time: String? = null,
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("apparent_temperature") val apparentTemperature: Double? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
    @SerialName("wind_speed_10m") val windSpeed: Double? = null,
    @SerialName("wind_gusts_10m") val windGust: Double? = null,
    @SerialName("is_day") val isDay: Int? = null,
)

@Serializable
private data class OpenMeteoDaily(
    @SerialName("temperature_2m_max") val temperatureMax: List<Double>? = null,
    @SerialName("temperature_2m_min") val temperatureMin: List<Double>? = null,
    @SerialName("uv_index_max") val uvIndexMax: List<Double>? = null,
    @SerialName("precipitation_probability_max") val precipitationProbabilityMax: List<Int>? = null,
)

@Serializable
private data class OpenMeteoHourly(
    val time: List<String>? = null,
    @SerialName("precipitation_probability") val precipitationProbability: List<Int?>? = null,
)
