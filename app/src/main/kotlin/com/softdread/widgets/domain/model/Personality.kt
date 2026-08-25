package com.softdread.widgets.domain.model

/**
 * The five personality modes defined by the Content Bible (section 1).
 *
 * [key] matches the mode key used in the generated content assets, so adding a
 * sixth mode is a matter of adding rows to the Bible, re-running the content
 * extractor and adding one entry here — no widget code changes.
 */
enum class Personality(val key: String) {
    NEUTRAL("NEUTRAL"),
    FRIENDLY("FRIENDLY"),
    DRY("DRY"),
    SARCASTIC("SARCASTIC"),
    CHAOTIC("CHAOTIC"),
    ;

    companion object {
        val DEFAULT: Personality = NEUTRAL

        fun fromKeyOrDefault(key: String?): Personality =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
