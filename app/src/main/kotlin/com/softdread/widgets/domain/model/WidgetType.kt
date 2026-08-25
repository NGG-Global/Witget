package com.softdread.widgets.domain.model

/**
 * The eight widgets in V1.
 *
 * [contentAsset] is the file under `assets/content/` that holds this widget's
 * response pools; [colourRole] is the permanent colour the design sheet assigns
 * to the widget ("one colour per widget, it never changes across sizes, states
 * or modes").
 */
enum class WidgetType(
    val id: String,
    val contentAsset: String?,
    val colourRole: ColourRole,
) {
    SCREEN_TIME("screen_time", "screentime.json", ColourRole.CLAY),
    DAILY_JOKE("daily_joke", "joke.json", ColourRole.AMBER),
    BATTERY("battery", "battery.json", ColourRole.SAGE),
    DAY_VIBE("day_vibe", "dayvibe.json", ColourRole.CREAM),
    WEATHER("weather", "weather.json", ColourRole.EMBER),
    COUNTDOWN("countdown", "countdown.json", ColourRole.SLATE),
    TIME_PROGRESS("time_progress", "progress.json", ColourRole.INK),
    MAGIC_8_BALL("magic_8_ball", "magic8ball.json", ColourRole.NIGHT),
    ;

    companion object {
        fun fromId(id: String?): WidgetType? = entries.firstOrNull { it.id == id }
    }
}

/** The named swatches from the design sheet's "palette · one owner per swatch". */
enum class ColourRole { CLAY, EMBER, AMBER, SAGE, SLATE, NIGHT, INK, CREAM }
