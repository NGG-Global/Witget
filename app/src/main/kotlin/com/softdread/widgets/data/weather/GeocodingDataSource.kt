package com.softdread.widgets.data.weather

import com.softdread.widgets.data.prefs.SavedLocation
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * City search for the manual-location path.
 *
 * Uses Open-Meteo's geocoding API — same provider, same licence, still no key —
 * so a user who declines location permission can still get real weather by
 * naming their city.
 */
class GeocodingDataSource(
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val timeoutMillis: Int = 10_000,
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String, limit: Int = 8): List<SavedLocation> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()
        val url = "$endpoint?name=${URLEncoder.encode(trimmed, "UTF-8")}&count=$limit&format=json"
        runCatching {
            val body = readBody(URL(url))
            json.decodeFromString(GeocodingResponse.serializer(), body).results.orEmpty().map {
                SavedLocation(
                    name = listOfNotNull(it.name, it.admin1).distinct().joinToString(", "),
                    latitude = it.latitude,
                    longitude = it.longitude,
                    country = it.country,
                    timeZoneId = it.timezone,
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun readBody(url: URL): String {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://geocoding-api.open-meteo.com/v1/search"
    }
}

@Serializable
private data class GeocodingResponse(val results: List<GeocodingResult>? = null)

@Serializable
private data class GeocodingResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    @SerialName("admin1") val admin1: String? = null,
    val timezone: String? = null,
)
