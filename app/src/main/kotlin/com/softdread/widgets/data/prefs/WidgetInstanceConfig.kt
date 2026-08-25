package com.softdread.widgets.data.prefs

import com.softdread.widgets.domain.model.Personality
import com.softdread.widgets.domain.model.WidgetType
import kotlinx.serialization.Serializable

/** Whether a widget follows the system theme or is pinned light or dark. */
enum class AppearanceMode { SYSTEM, LIGHT, DARK }

/** The period a Time Progress instance tracks. */
enum class ProgressScope(val key: String, val stateKey: String) {
    DAY("day", "TP_S1"),
    WEEK("week", "TP_S2"),
    MONTH("month", "TP_S3"),
    YEAR("year", "TP_S4"),
    ;

    companion object {
        fun fromKeyOrDefault(key: String?): ProgressScope =
            entries.firstOrNull { it.key == key } ?: YEAR
    }
}

/** Equivalency categories offered by the Content Bible for Screen Time. */
enum class EquivalencyCategory(val key: String) {
    MIXED("MIXED"),
    POP_CULTURE("POP_CULTURE"),
    ENTERTAINMENT("ENTERTAINMENT"),
    LIFE("LIFE"),
    PRODUCTIVITY("PRODUCTIVITY"),
    ABSURD("ABSURD"),
    AUDIO("AUDIO"),
    READING("READING"),
    SOCIAL("SOCIAL"),
    GAMING("GAMING"),
    ;

    companion object {
        fun fromKeyOrDefault(key: String?): EquivalencyCategory =
            entries.firstOrNull { it.key == key } ?: MIXED
    }
}

/** The five joke categories in the curated library. */
enum class JokeCategory(val key: String) {
    DAD_WORDPLAY("DAD_WORDPLAY"),
    NERD_TECH("NERD_TECH"),
    WORK_LIFE("WORK_LIFE"),
    ANTI_JOKE("ANTI_JOKE"),
    ABSURD("ABSURD"),
    ;

    companion object {
        fun fromKeys(keys: Collection<String>): Set<JokeCategory> =
            keys.mapNotNullTo(mutableSetOf()) { key -> entries.firstOrNull { it.key == key } }
    }
}

/** The human-scale unit a Countdown instance frames its remaining time in. */
enum class CountdownUnit(val key: String) {
    AUTO("auto"),
    SLEEPS("sleeps"),
    WEEKENDS("weekends"),
    WEEKS("weeks"),
    MONTHS("months"),
    ;

    companion object {
        fun fromKeyOrDefault(key: String?): CountdownUnit =
            entries.firstOrNull { it.key == key } ?: AUTO
    }
}

/** A weather location the user chose explicitly, or one resolved from the device. */
@Serializable
data class SavedLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val timeZoneId: String? = null,
)

/**
 * Everything one placed widget remembers.
 *
 * Configuration is stored per `appWidgetId`, never per widget type, so a user
 * can run a Japan-trip countdown next to a birthday countdown, or a sarcastic
 * 8 ball next to a chaotic one. [personality] is nullable on purpose: `null`
 * means "follow the global default", which is what lets the settings screen
 * re-skin every widget at once.
 */
@Serializable
data class WidgetInstanceConfig(
    val appWidgetId: Int,
    val widgetTypeId: String,
    val personalityKey: String? = null,
    val appearance: AppearanceMode = AppearanceMode.SYSTEM,
    val useMaterialYouAccent: Boolean = false,

    // Screen Time
    val equivalencyCategoryKey: String? = null,

    // Daily Joke
    val jokeCategoryKeys: List<String> = JokeCategory.entries.map { it.key },

    // Day Vibe
    val calendarIds: List<Long> = emptyList(),
    val showEventTitles: Boolean = false,

    // Weather
    val useDeviceLocation: Boolean = true,
    val savedLocation: SavedLocation? = null,

    // Countdown
    val countdownTitle: String = "",
    val countdownTargetEpochMillis: Long = 0L,
    val countdownZoneId: String? = null,
    /** Used to show honest elapsed progress rather than a decorative bar. */
    val countdownCreatedAtEpochMillis: Long = 0L,
    val countdownUnitKey: String = CountdownUnit.AUTO.key,

    // Time Progress
    val progressScopeKey: String = ProgressScope.YEAR.key,
) {
    val widgetType: WidgetType? get() = WidgetType.fromId(widgetTypeId)

    val progressScope: ProgressScope get() = ProgressScope.fromKeyOrDefault(progressScopeKey)

    val equivalencyCategory: EquivalencyCategory
        get() = EquivalencyCategory.fromKeyOrDefault(equivalencyCategoryKey)

    val countdownUnit: CountdownUnit get() = CountdownUnit.fromKeyOrDefault(countdownUnitKey)

    val jokeCategories: Set<JokeCategory> get() = JokeCategory.fromKeys(jokeCategoryKeys)

    /** Resolves this instance's personality against the global default. */
    fun personality(globalDefault: Personality): Personality =
        personalityKey?.let { Personality.fromKeyOrDefault(it) } ?: globalDefault

    /** Stable key for anti-repeat history, unique per placed widget. */
    val instanceKey: String get() = "$widgetTypeId#$appWidgetId"

    /** True once the instance has everything it needs to render real content. */
    val isConfigured: Boolean
        get() = when (widgetType) {
            WidgetType.COUNTDOWN -> countdownTitle.isNotBlank() && countdownTargetEpochMillis > 0L
            else -> true
        }

    companion object {
        fun default(appWidgetId: Int, type: WidgetType) =
            WidgetInstanceConfig(appWidgetId = appWidgetId, widgetTypeId = type.id)
    }
}

/** App-wide preferences shared by every widget and by the app UI. */
@Serializable
data class GlobalPreferences(
    val defaultPersonalityKey: String = Personality.DEFAULT.key,
    val themePackKey: String = "clay_house",
    val appearance: AppearanceMode = AppearanceMode.SYSTEM,
    val onboardingComplete: Boolean = false,
    val useCelsius: Boolean = true,
    val weekStartsOnMonday: Boolean = true,
    val defaultLocation: SavedLocation? = null,
) {
    val defaultPersonality: Personality get() = Personality.fromKeyOrDefault(defaultPersonalityKey)
}
