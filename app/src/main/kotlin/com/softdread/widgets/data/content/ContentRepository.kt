package com.softdread.widgets.data.content

import android.annotation.SuppressLint
import android.content.Context
import com.softdread.widgets.domain.model.Personality
import com.softdread.widgets.domain.model.WidgetType
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Loads the generated content pack from assets and caches it in memory.
 *
 * Each widget owns a single asset file, so a battery widget update parses ~50 KB
 * rather than the whole 1 500-string library. Parsed documents are cached for
 * the process lifetime; a widget update after the first is a map lookup.
 */
class ContentRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val cache = ConcurrentHashMap<String, ContentDocument>()

    @Volatile
    private var manifest: ContentManifest? = null

    suspend fun document(widget: WidgetType): ContentDocument = withContext(Dispatchers.IO) {
        val asset = requireNotNull(widget.contentAsset) { "${widget.id} has no content asset" }
        cache.getOrPut(asset) { parse(asset) }
    }

    suspend fun manifest(): ContentManifest = withContext(Dispatchers.IO) {
        manifest ?: parseManifest().also { manifest = it }
    }

    /** Blocking variant for use inside already-backgrounded Glance composition. */
    fun documentBlocking(widget: WidgetType): ContentDocument {
        val asset = requireNotNull(widget.contentAsset) { "${widget.id} has no content asset" }
        return cache.getOrPut(asset) { parse(asset) }
    }

    private fun parse(asset: String): ContentDocument =
        json.decodeFromString(ContentDocument.serializer(), readAsset("content/$asset"))

    private fun parseManifest(): ContentManifest =
        json.decodeFromString(ContentManifest.serializer(), readAsset("content/manifest.json"))

    private fun readAsset(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    companion object {
        // Holds the Application context only, which lives as long as the
        // process; the constructor below always unwraps to it.
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: ContentRepository? = null

        fun get(context: Context): ContentRepository =
            instance ?: synchronized(this) {
                instance ?: ContentRepository(context.applicationContext).also { instance = it }
            }
    }
}

/** Convenience accessors shared by the widget renderers and the app previews. */
fun ContentDocument.pool(stateKey: String, personality: Personality): List<ContentResponse> {
    val state = states.firstOrNull { it.key == stateKey } ?: return emptyList()
    val exact = state.pools.firstOrNull { it.mode == personality.key }
    // A content pack that predates a newly added personality falls back to
    // Neutral rather than rendering nothing.
    val pool = exact ?: state.pools.firstOrNull { it.mode == Personality.DEFAULT.key }
    return pool?.responses.orEmpty()
}

fun ContentDocument.state(stateKey: String): ContentState? = states.firstOrNull { it.key == stateKey }
