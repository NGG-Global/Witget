package com.softdread.widgets.domain.logic

/** A normalised weather observation, provider-agnostic by design. */
data class WeatherReading(
    val temperatureCelsius: Double,
    val feelsLikeCelsius: Double,
    val weatherCode: Int,
    val windSpeedKmh: Double,
    val windGustKmh: Double,
    val uvIndex: Double?,
    val rainChancePercent: Int?,
    val highCelsius: Double?,
    val lowCelsius: Double?,
    /** Rain probability for the next few hours, oldest first. */
    val hourlyRainChance: List<Int> = emptyList(),
    val isDay: Boolean = true,
    val locationName: String? = null,
    val observedAtEpochMillis: Long = 0L,
)

/** The eleven conditions the Content Bible writes copy for. */
enum class WeatherCondition(val stateKey: String, val priority: Int) {
    EXTREME_HEAT("WX1", 1),
    THUNDER_HEAVY_RAIN("WX4", 2),
    VERY_COLD("WX6", 3),
    HIGH_UV("WX3", 4),
    RAIN("WX5", 5),
    STRONG_WIND("WX8", 6),
    HOT("WX2", 7),
    COLD("WX7", 8),
    HUMID("WX9", 9),
    PLEASANT("WX10", 10),
    CLOUDY("WX11", 11),
    ;

    /**
     * Conditions where useful guidance must stay explicit. The Bible is firm
     * that humour may follow, never replace, "very high UV", "extreme heat" or
     * "storm" messaging.
     */
    val isSafetyRelevant: Boolean
        get() = this == EXTREME_HEAT || this == THUNDER_HEAVY_RAIN || this == VERY_COLD || this == HIGH_UV
}

/**
 * Resolves a weather reading to exactly one Content Bible condition.
 *
 * The Bible fixes the priority order and every threshold; this object encodes
 * them once so no widget re-derives them. Note that the Bible's state numbering
 * (WX1..WX11) is not its priority order — WX2 "Hot" sits at priority 7 — so the
 * mapping lives on [WeatherCondition] rather than being inferred from the key.
 */
object WeatherLogic {

    /** WMO codes 95-99 are thunderstorms; 65/67/82 are heavy rain intensities. */
    private val THUNDER_CODES = setOf(95, 96, 99)
    private val HEAVY_RAIN_CODES = setOf(65, 67, 82)
    private val RAIN_CODES = setOf(51, 53, 55, 56, 57, 61, 63, 66, 80, 81)
    private val SNOW_CODES = setOf(71, 73, 75, 77, 85, 86)

    fun resolve(reading: WeatherReading): WeatherCondition {
        val temp = reading.temperatureCelsius
        val feels = reading.feelsLikeCelsius
        val code = reading.weatherCode
        val uv = reading.uvIndex ?: 0.0
        val rainChance = reading.rainChancePercent ?: 0

        return when {
            temp >= 38.0 || feels >= 40.0 -> WeatherCondition.EXTREME_HEAT
            code in THUNDER_CODES || code in HEAVY_RAIN_CODES -> WeatherCondition.THUNDER_HEAVY_RAIN
            temp <= 3.0 -> WeatherCondition.VERY_COLD
            uv >= 8.0 -> WeatherCondition.HIGH_UV
            code in RAIN_CODES || code in SNOW_CODES -> WeatherCondition.RAIN
            reading.windSpeedKmh >= 35.0 || reading.windGustKmh >= 50.0 -> WeatherCondition.STRONG_WIND
            temp >= 32.0 -> WeatherCondition.HOT
            temp <= 10.0 -> WeatherCondition.COLD
            feels >= temp + 4.0 && temp >= 24.0 -> WeatherCondition.HUMID
            temp in 18.0..26.0 && rainChance < 20 && reading.windSpeedKmh < 25.0 -> WeatherCondition.PLEASANT
            else -> WeatherCondition.CLOUDY
        }
    }

    /**
     * The satellite circle's colour role. The design sheet is specific: "the
     * satellite circle is the sky, not an icon: amber = sun, slate = rain,
     * cream = cloud. Never a weather glyph."
     */
    fun skyRole(condition: WeatherCondition): com.softdread.widgets.domain.model.ColourRole = when (condition) {
        WeatherCondition.RAIN,
        WeatherCondition.THUNDER_HEAVY_RAIN,
        WeatherCondition.VERY_COLD,
        -> com.softdread.widgets.domain.model.ColourRole.SLATE

        WeatherCondition.CLOUDY,
        WeatherCondition.COLD,
        WeatherCondition.STRONG_WIND,
        -> com.softdread.widgets.domain.model.ColourRole.CREAM

        else -> com.softdread.widgets.domain.model.ColourRole.AMBER
    }

    fun variables(reading: WeatherReading, useCelsius: Boolean): Map<String, String> {
        val variables = mutableMapOf(
            "temp" to Formatting.temperature(reading.temperatureCelsius, useCelsius),
            "feels" to Formatting.temperature(reading.feelsLikeCelsius, useCelsius),
            "wind" to Formatting.integer(reading.windSpeedKmh.toLong()),
        )
        reading.uvIndex?.let { variables["uv"] = Formatting.oneDecimal(it) }
        reading.rainChancePercent?.let { variables["rain_chance"] = it.toString() }
        return variables
    }

    /** A short plain-language condition label kept beside the personality line. */
    fun conditionLabel(condition: WeatherCondition): String = when (condition) {
        WeatherCondition.EXTREME_HEAT -> "extreme heat"
        WeatherCondition.THUNDER_HEAVY_RAIN -> "storm"
        WeatherCondition.VERY_COLD -> "very cold"
        WeatherCondition.HIGH_UV -> "very high uv"
        WeatherCondition.RAIN -> "rain"
        WeatherCondition.STRONG_WIND -> "strong wind"
        WeatherCondition.HOT -> "hot"
        WeatherCondition.COLD -> "cold"
        WeatherCondition.HUMID -> "humid"
        WeatherCondition.PLEASANT -> "pleasant"
        WeatherCondition.CLOUDY -> "cloudy"
    }
}
