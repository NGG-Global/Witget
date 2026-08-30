package com.softdread.widgets.widgets.weather

import androidx.glance.appwidget.GlanceAppWidget
import com.softdread.widgets.data.content.pool
import com.softdread.widgets.data.weather.WeatherRepository
import com.softdread.widgets.data.weather.WeatherState
import com.softdread.widgets.design.SoftDreadTiles
import com.softdread.widgets.domain.logic.Formatting
import com.softdread.widgets.domain.logic.WeatherCondition
import com.softdread.widgets.domain.logic.WeatherLogic
import com.softdread.widgets.domain.logic.WeatherReading
import com.softdread.widgets.domain.model.WidgetBreakpoint
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.domain.selection.AntiRepeatPolicies
import com.softdread.widgets.widgets.common.LeadingVisual
import com.softdread.widgets.widgets.common.SoftDreadWidget
import com.softdread.widgets.widgets.common.SoftDreadWidgetReceiver
import com.softdread.widgets.widgets.common.TileContent
import com.softdread.widgets.widgets.common.WidgetEnvironment
import com.softdread.widgets.widgets.common.WidgetPayload
import com.softdread.widgets.widgets.common.openAppAction
import com.softdread.widgets.widgets.common.refreshAction
import com.softdread.widgets.widgets.common.setupContent

/**
 * Weather, Translated.
 *
 * The real numbers stay on the tile at every size — temperature always, plus
 * feels-like, high/low and rain probability as the tile grows. For the four
 * safety-relevant conditions the Content Bible names, the plain condition label
 * is pinned beside the temperature so the humour follows the warning rather than
 * replacing it.
 *
 * Offline behaviour is deliberate: a failed fetch falls back to the cache and
 * marks it stale. The user never sees a network error.
 */
class WeatherWidget : SoftDreadWidget(WidgetType.WEATHER) {

    override suspend fun buildPayload(environment: WidgetEnvironment): WidgetPayload {
        val action = openAppAction(environment.context, WidgetType.WEATHER, environment.config.appWidgetId)
        val repository = WeatherRepository(environment.context)
        val location = environment.config.savedLocation ?: environment.preferences.defaultLocation

        val state = repository.state(
            savedLocation = location,
            useDeviceLocation = environment.config.useDeviceLocation,
            // A widget build can be running inside a broadcast; the hourly
            // worker owns the network, this owns the cache.
            allowNetworkWhenCached = false,
        )

        val reading = when (state) {
            is WeatherState.Ready -> state.reading
            WeatherState.NeedsLocation -> return setup(
                environment, action,
                "location needed",
                "Pick a city, or let the widget use your approximate location.",
                "tap to choose",
            )
            WeatherState.NeedsLocationPermission -> return setup(
                environment, action,
                "location access needed",
                "Grant approximate location, or choose a city by name instead.",
                "tap to fix",
            )
            WeatherState.Unavailable -> return setup(
                environment, action,
                "weather unavailable",
                "No forecast has been fetched yet and the network is unreachable.",
                "tap to retry",
            )
        }
        val isStale = (state as WeatherState.Ready).isStale

        val condition = WeatherLogic.resolve(reading)
        val useCelsius = environment.preferences.useCelsius
        val document = environment.content.document(WidgetType.WEATHER)
        val variables = WeatherLogic.variables(reading, useCelsius)
        val temperature = "${Formatting.temperature(reading.temperatureCelsius, useCelsius)}°"
        val feelsLike = "feels ${Formatting.temperature(reading.feelsLikeCelsius, useCelsius)}°"
        val skyColour = SoftDreadTiles
            .colours(WeatherLogic.skyRole(condition), dark = environment.isDark)
            .surface

        val content = environment.breakpoints.associateWith { breakpoint ->
            val voice = environment.session.select(
                poolKey = "weather:${condition.stateKey}",
                candidates = document.pool(condition.stateKey, environment.personality),
                policy = AntiRepeatPolicies.WEATHER,
                variables = variables,
                maxChars = breakpoint.maxResponseChars,
            )?.text.orEmpty()

            val detail = when {
                isStale -> "last known"
                condition.isSafetyRelevant -> WeatherLogic.conditionLabel(condition)
                else -> reading.locationName
            }

            val description = buildString {
                append("$temperature, $feelsLike, ${WeatherLogic.conditionLabel(condition)}")
                reading.rainChancePercent?.let { append(", $it percent chance of rain") }
                if (isStale) append(". Last known reading")
                if (voice.isNotBlank()) append(". $voice")
            }

            when (breakpoint) {
                WidgetBreakpoint.TINY -> TileContent(
                    label = "weather",
                    labelDetail = detail,
                    heroValue = temperature,
                    contentDescription = description,
                )

                WidgetBreakpoint.COMPACT -> TileContent(
                    label = "weather",
                    labelDetail = detail,
                    heroValue = temperature,
                    voice = voice,
                    contentDescription = description,
                    satelliteRole = WeatherLogic.skyRole(condition),
                )

                WidgetBreakpoint.STANDARD, WidgetBreakpoint.WIDE -> TileContent(
                    label = "weather, translated",
                    labelDetail = detail,
                    heroValue = temperature,
                    metric = feelsLike,
                    voice = voice,
                    leading = LeadingVisual.Disc(skyColour),
                    contentDescription = description,
                    satelliteRole = WeatherLogic.skyRole(condition),
                )

                WidgetBreakpoint.EXPANDED, WidgetBreakpoint.HERO -> TileContent(
                    label = "weather, translated",
                    labelDetail = detail,
                    heroValue = temperature,
                    metric = highLow(reading, feelsLike, useCelsius),
                    strip = reading.hourlyRainChance.map { it / 100f },
                    pill = voice,
                    contentDescription = description,
                    satelliteRole = WeatherLogic.skyRole(condition),
                )
            }
        }
        // A tap re-fetches (or falls back to cache); setup states keep the
        // open-app action for the location picker.
        return WidgetPayload(content, onClick = refreshAction(WidgetType.WEATHER))
    }

    private fun setup(
        environment: WidgetEnvironment,
        action: androidx.glance.action.Action,
        headline: String,
        explanation: String,
        callToAction: String,
    ) = WidgetPayload(
        environment.breakpoints.associateWith {
            setupContent("weather", headline, explanation, callToAction)
        },
        onClick = action,
    )

    private fun highLow(reading: WeatherReading, feelsLike: String, useCelsius: Boolean): String {
        val high = reading.highCelsius?.let { Formatting.temperature(it, useCelsius) }
        val low = reading.lowCelsius?.let { Formatting.temperature(it, useCelsius) }
        // One line: the metric rides beside the hero, where a newline cannot render.
        return if (high != null && low != null) "$feelsLike · high $high · low $low" else feelsLike
    }

}

class WeatherWidgetReceiver : SoftDreadWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()
}

/** Exposed so the Weather condition can be previewed in the app gallery. */
internal fun previewCondition(reading: WeatherReading): WeatherCondition = WeatherLogic.resolve(reading)
