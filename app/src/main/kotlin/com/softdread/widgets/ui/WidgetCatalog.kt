package com.softdread.widgets.ui

import androidx.annotation.StringRes
import com.softdread.widgets.R
import com.softdread.widgets.data.prefs.AppearanceMode
import com.softdread.widgets.data.prefs.CountdownUnit
import com.softdread.widgets.data.prefs.EquivalencyCategory
import com.softdread.widgets.data.prefs.JokeCategory
import com.softdread.widgets.data.prefs.ProgressScope
import com.softdread.widgets.design.ThemePack
import com.softdread.widgets.domain.model.Personality
import com.softdread.widgets.domain.model.WidgetType

/** What a widget needs before it can show real data. */
enum class SetupRequirement { NONE, USAGE_ACCESS, CALENDAR, LOCATION_OR_CITY, CONFIGURATION }

/**
 * Display metadata for the gallery.
 *
 * User-visible names and blurbs are string resources rather than literals, so a
 * future locale needs no code change — which is the same reason the response
 * library lives in versioned content assets rather than in the composables.
 */
data class WidgetCatalogEntry(
    val type: WidgetType,
    @param:StringRes val nameRes: Int,
    @param:StringRes val descriptionRes: Int,
    val requirement: SetupRequirement,
    val isInteractive: Boolean = false,
)

object WidgetCatalog {

    val entries: List<WidgetCatalogEntry> = listOf(
        WidgetCatalogEntry(
            WidgetType.SCREEN_TIME,
            R.string.widget_screen_time_label,
            R.string.widget_screen_time_description,
            SetupRequirement.USAGE_ACCESS,
        ),
        WidgetCatalogEntry(
            WidgetType.DAILY_JOKE,
            R.string.widget_daily_joke_label,
            R.string.widget_daily_joke_description,
            SetupRequirement.NONE,
        ),
        WidgetCatalogEntry(
            WidgetType.BATTERY,
            R.string.widget_battery_label,
            R.string.widget_battery_description,
            SetupRequirement.NONE,
        ),
        WidgetCatalogEntry(
            WidgetType.DAY_VIBE,
            R.string.widget_day_vibe_label,
            R.string.widget_day_vibe_description,
            SetupRequirement.CALENDAR,
        ),
        WidgetCatalogEntry(
            WidgetType.WEATHER,
            R.string.widget_weather_label,
            R.string.widget_weather_description,
            SetupRequirement.LOCATION_OR_CITY,
        ),
        WidgetCatalogEntry(
            WidgetType.COUNTDOWN,
            R.string.widget_countdown_label,
            R.string.widget_countdown_description,
            SetupRequirement.CONFIGURATION,
        ),
        WidgetCatalogEntry(
            WidgetType.TIME_PROGRESS,
            R.string.widget_time_progress_label,
            R.string.widget_time_progress_description,
            SetupRequirement.NONE,
        ),
        WidgetCatalogEntry(
            WidgetType.MAGIC_8_BALL,
            R.string.widget_magic_8_ball_label,
            R.string.widget_magic_8_ball_description,
            SetupRequirement.NONE,
            isInteractive = true,
        ),
    )

    fun entry(type: WidgetType): WidgetCatalogEntry = entries.first { it.type == type }
}

@get:StringRes
val Personality.labelRes: Int
    get() = when (this) {
        Personality.NEUTRAL -> R.string.personality_neutral
        Personality.FRIENDLY -> R.string.personality_friendly
        Personality.DRY -> R.string.personality_dry
        Personality.SARCASTIC -> R.string.personality_sarcastic
        Personality.CHAOTIC -> R.string.personality_chaotic
    }

@get:StringRes
val Personality.blurbRes: Int
    get() = when (this) {
        Personality.NEUTRAL -> R.string.personality_neutral_blurb
        Personality.FRIENDLY -> R.string.personality_friendly_blurb
        Personality.DRY -> R.string.personality_dry_blurb
        Personality.SARCASTIC -> R.string.personality_sarcastic_blurb
        Personality.CHAOTIC -> R.string.personality_chaotic_blurb
    }

@get:StringRes
val AppearanceMode.labelRes: Int
    get() = when (this) {
        AppearanceMode.SYSTEM -> R.string.appearance_system
        AppearanceMode.LIGHT -> R.string.appearance_light
        AppearanceMode.DARK -> R.string.appearance_dark
    }

@get:StringRes
val ThemePack.labelRes: Int
    get() = when (this) {
        ThemePack.CLAY_HOUSE -> R.string.theme_clay_house
        ThemePack.DUSK_HOUSE -> R.string.theme_dusk_house
        ThemePack.ORCHARD_HOUSE -> R.string.theme_orchard_house
        ThemePack.PAPER_HOUSE -> R.string.theme_paper_house
    }

@get:StringRes
val ProgressScope.labelRes: Int
    get() = when (this) {
        ProgressScope.DAY -> R.string.progress_day
        ProgressScope.WEEK -> R.string.progress_week
        ProgressScope.MONTH -> R.string.progress_month
        ProgressScope.YEAR -> R.string.progress_year
    }

@get:StringRes
val CountdownUnit.labelRes: Int
    get() = when (this) {
        CountdownUnit.AUTO -> R.string.countdown_unit_auto
        CountdownUnit.SLEEPS -> R.string.countdown_unit_sleeps
        CountdownUnit.WEEKENDS -> R.string.countdown_unit_weekends
        CountdownUnit.WEEKS -> R.string.countdown_unit_weeks
        CountdownUnit.MONTHS -> R.string.countdown_unit_months
    }

@get:StringRes
val EquivalencyCategory.labelRes: Int
    get() = when (this) {
        EquivalencyCategory.MIXED -> R.string.category_mixed
        EquivalencyCategory.POP_CULTURE -> R.string.category_pop_culture
        EquivalencyCategory.ENTERTAINMENT -> R.string.category_entertainment
        EquivalencyCategory.LIFE -> R.string.category_life
        EquivalencyCategory.PRODUCTIVITY -> R.string.category_productivity
        EquivalencyCategory.ABSURD -> R.string.category_absurd
        EquivalencyCategory.AUDIO -> R.string.category_audio
        EquivalencyCategory.READING -> R.string.category_reading
        EquivalencyCategory.SOCIAL -> R.string.category_social
        EquivalencyCategory.GAMING -> R.string.category_gaming
    }

@get:StringRes
val JokeCategory.labelRes: Int
    get() = when (this) {
        JokeCategory.DAD_WORDPLAY -> R.string.joke_category_dad
        JokeCategory.NERD_TECH -> R.string.joke_category_nerd
        JokeCategory.WORK_LIFE -> R.string.joke_category_work
        JokeCategory.ANTI_JOKE -> R.string.joke_category_anti
        JokeCategory.ABSURD -> R.string.joke_category_absurd
    }
