package com.softdread.widgets.domain.logic

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Weather condition resolution.
 *
 * The Bible fixes both the thresholds and the priority order, and those two are
 * not the same ordering as the state numbering — WX2 "Hot" sits at priority 7 —
 * so the boundary cases here also check that a higher-priority condition wins
 * even when a lower one also matches.
 */
class WeatherLogicTest {

    private fun reading(
        temp: Double = 20.0,
        feels: Double = 20.0,
        code: Int = 3,
        wind: Double = 5.0,
        gust: Double = 8.0,
        uv: Double? = 2.0,
        rain: Int? = 5,
    ) = WeatherReading(
        temperatureCelsius = temp,
        feelsLikeCelsius = feels,
        weatherCode = code,
        windSpeedKmh = wind,
        windGustKmh = gust,
        uvIndex = uv,
        rainChancePercent = rain,
        highCelsius = temp + 2,
        lowCelsius = temp - 5,
    )

    @Test
    fun `extreme heat triggers on either temperature or feels-like`() {
        assertThat(WeatherLogic.resolve(reading(temp = 38.0))).isEqualTo(WeatherCondition.EXTREME_HEAT)
        assertThat(WeatherLogic.resolve(reading(temp = 30.0, feels = 40.0)))
            .isEqualTo(WeatherCondition.EXTREME_HEAT)
        assertThat(WeatherLogic.resolve(reading(temp = 37.0, feels = 39.0)))
            .isEqualTo(WeatherCondition.HOT)
    }

    @Test
    fun `hot band boundaries`() {
        assertThat(WeatherLogic.resolve(reading(temp = 31.9))).isNotEqualTo(WeatherCondition.HOT)
        assertThat(WeatherLogic.resolve(reading(temp = 32.0))).isEqualTo(WeatherCondition.HOT)
        assertThat(WeatherLogic.resolve(reading(temp = 37.0))).isEqualTo(WeatherCondition.HOT)
    }

    @Test
    fun `cold band boundaries`() {
        assertThat(WeatherLogic.resolve(reading(temp = 3.0))).isEqualTo(WeatherCondition.VERY_COLD)
        assertThat(WeatherLogic.resolve(reading(temp = 3.1))).isEqualTo(WeatherCondition.COLD)
        assertThat(WeatherLogic.resolve(reading(temp = 10.0))).isEqualTo(WeatherCondition.COLD)
        assertThat(WeatherLogic.resolve(reading(temp = 10.1))).isNotEqualTo(WeatherCondition.COLD)
    }

    @Test
    fun `storms outrank very cold, and very cold outranks high UV`() {
        assertThat(WeatherLogic.resolve(reading(temp = 1.0, code = 95)))
            .isEqualTo(WeatherCondition.THUNDER_HEAVY_RAIN)
        assertThat(WeatherLogic.resolve(reading(temp = 1.0, uv = 9.0)))
            .isEqualTo(WeatherCondition.VERY_COLD)
    }

    @Test
    fun `high UV boundary`() {
        assertThat(WeatherLogic.resolve(reading(temp = 22.0, uv = 7.9))).isNotEqualTo(WeatherCondition.HIGH_UV)
        assertThat(WeatherLogic.resolve(reading(temp = 22.0, uv = 8.0))).isEqualTo(WeatherCondition.HIGH_UV)
    }

    @Test
    fun `strong wind triggers on sustained speed or gust`() {
        assertThat(WeatherLogic.resolve(reading(wind = 34.0, gust = 49.0)))
            .isNotEqualTo(WeatherCondition.STRONG_WIND)
        assertThat(WeatherLogic.resolve(reading(wind = 35.0))).isEqualTo(WeatherCondition.STRONG_WIND)
        assertThat(WeatherLogic.resolve(reading(gust = 50.0))).isEqualTo(WeatherCondition.STRONG_WIND)
    }

    @Test
    fun `humid needs both the feels-like gap and a warm base`() {
        assertThat(WeatherLogic.resolve(reading(temp = 24.0, feels = 28.0)))
            .isEqualTo(WeatherCondition.HUMID)
        // The same gap below 24 degrees is not humid.
        assertThat(WeatherLogic.resolve(reading(temp = 23.0, feels = 27.0)))
            .isNotEqualTo(WeatherCondition.HUMID)
    }

    @Test
    fun `pleasant requires the full set of conditions`() {
        assertThat(WeatherLogic.resolve(reading(temp = 22.0, feels = 22.0, rain = 10, wind = 10.0)))
            .isEqualTo(WeatherCondition.PLEASANT)
        assertThat(WeatherLogic.resolve(reading(temp = 22.0, feels = 22.0, rain = 20, wind = 10.0)))
            .isEqualTo(WeatherCondition.CLOUDY)
        assertThat(WeatherLogic.resolve(reading(temp = 17.9, feels = 17.9, rain = 5, wind = 5.0)))
            .isEqualTo(WeatherCondition.CLOUDY)
    }

    @Test
    fun `everything unmatched falls through to cloudy`() {
        assertThat(WeatherLogic.resolve(reading(temp = 28.0, feels = 28.0, rain = 40)))
            .isEqualTo(WeatherCondition.CLOUDY)
    }

    @Test
    fun `every condition maps to a distinct Bible state key`() {
        val keys = WeatherCondition.entries.map { it.stateKey }
        assertThat(keys).containsNoDuplicates()
        assertThat(keys).hasSize(11)
    }

    @Test
    fun `priorities are a strict ordering with no gaps`() {
        val priorities = WeatherCondition.entries.map { it.priority }.sorted()
        assertThat(priorities).isEqualTo((1..11).toList())
    }

    @Test
    fun `the four safety conditions are flagged`() {
        val safety = WeatherCondition.entries.filter { it.isSafetyRelevant }
        assertThat(safety).containsExactly(
            WeatherCondition.EXTREME_HEAT,
            WeatherCondition.THUNDER_HEAVY_RAIN,
            WeatherCondition.VERY_COLD,
            WeatherCondition.HIGH_UV,
        )
    }

    @Test
    fun `temperature conversion is correct in both units`() {
        assertThat(Formatting.temperature(34.0, useCelsius = true)).isEqualTo("34")
        assertThat(Formatting.temperature(0.0, useCelsius = false)).isEqualTo("32")
        assertThat(Formatting.temperature(100.0, useCelsius = false)).isEqualTo("212")
    }
}
