package com.softdread.widgets.data.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire model for the generated content pack in `assets/content/`.
 *
 * These files are produced by `tools/content/extract_content.py` from the
 * Content Bible and must not be edited by hand. See `docs/content-model.md`.
 */
@Serializable
data class ContentDocument(
    val schemaVersion: Int,
    val widget: String,
    val locale: String,
    val states: List<ContentState>,
    /** Screen Time only: the 50-unit equivalency library. */
    val equivalencyUnits: List<EquivalencyUnit> = emptyList(),
    /** Daily Joke only: the 120-joke curated library. */
    val jokes: List<Joke> = emptyList(),
    /** Daily Joke only: per-personality category weighting. */
    val categoryWeights: List<CategoryWeighting> = emptyList(),
)

@Serializable
data class ContentState(
    val key: String,
    val label: String,
    val pools: List<ContentPool>,
)

@Serializable
data class ContentPool(
    val mode: String,
    val responses: List<ContentResponse>,
)

@Serializable
data class ContentResponse(
    val id: String,
    val text: String,
    /** Variables the string needs; a response is ineligible unless all resolve. */
    @SerialName("vars") val requiredVariables: List<String> = emptyList(),
)

@Serializable
data class EquivalencyUnit(
    val id: String,
    val category: String,
    val minutes: Double,
    val label: String,
    /** Motif glyph name, curated per unit in the extractor ("FILM", "TOAST"). */
    val glyph: String? = null,
)

@Serializable
data class Joke(
    val id: String,
    val category: String,
    val text: String,
    /** Motif glyph name, curated per joke in the extractor ("PAINT", "SOCK"). */
    val glyph: String? = null,
    /** ColourRole name the tile retints to for this joke ("SLATE" for blue paint). */
    val colour: String? = null,
)

@Serializable
data class CategoryWeighting(
    val mode: String,
    val weights: List<CategoryWeight>,
)

@Serializable
data class CategoryWeight(
    val category: String,
    val weight: Int,
)

@Serializable
data class ContentManifest(
    val schemaVersion: Int,
    val locale: String,
    val source: ContentSource,
    val generator: String,
    val files: List<ContentFileEntry>,
    val equivalencyUnitCount: Int,
    val jokeCount: Int,
    val totalResponseCount: Int,
)

@Serializable
data class ContentSource(val document: String, val sha256: String)

@Serializable
data class ContentFileEntry(val name: String, val responseCount: Int)
