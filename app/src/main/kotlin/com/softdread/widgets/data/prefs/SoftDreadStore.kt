package com.softdread.widgets.data.prefs

import android.annotation.SuppressLint
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.domain.selection.PoolHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.softDreadDataStore: DataStore<Preferences> by preferencesDataStore(name = "soft_dread")

/**
 * Persistence for global preferences, per-instance widget configuration and
 * anti-repeat history.
 *
 * Values are stored as JSON blobs under one key each rather than as flattened
 * primitives, which keeps schema evolution to a single data class change and
 * makes deleting a widget's entire footprint a two-key removal.
 *
 * Room is deliberately not used: nothing here is relational, the largest record
 * is a bounded list of response IDs, and DataStore already gives us the
 * coroutine and Flow surface the widgets consume.
 */
class SoftDreadStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val store get() = context.softDreadDataStore

    // --- global preferences -------------------------------------------------

    val preferences: Flow<GlobalPreferences> = store.data.map { prefs ->
        prefs[GLOBAL_KEY]?.let { decodeOrNull(GlobalPreferences.serializer(), it) } ?: GlobalPreferences()
    }

    suspend fun currentPreferences(): GlobalPreferences = preferences.first()

    suspend fun updatePreferences(transform: (GlobalPreferences) -> GlobalPreferences) {
        store.edit { prefs ->
            val current = prefs[GLOBAL_KEY]?.let { decodeOrNull(GlobalPreferences.serializer(), it) }
                ?: GlobalPreferences()
            prefs[GLOBAL_KEY] = json.encodeToString(GlobalPreferences.serializer(), transform(current))
        }
    }

    // --- per-instance widget configuration ----------------------------------

    fun config(appWidgetId: Int): Flow<WidgetInstanceConfig?> = store.data.map { prefs ->
        prefs[configKey(appWidgetId)]?.let { decodeOrNull(WidgetInstanceConfig.serializer(), it) }
    }

    suspend fun currentConfig(appWidgetId: Int): WidgetInstanceConfig? = config(appWidgetId).first()

    /**
     * Reads the config for [appWidgetId], creating a default for [type] when the
     * widget was placed without passing through the configuration activity —
     * which is what happens for widget types that do not declare one.
     */
    suspend fun configOrCreate(appWidgetId: Int, type: WidgetType): WidgetInstanceConfig {
        currentConfig(appWidgetId)?.let { existing ->
            if (existing.widgetTypeId == type.id) return existing
        }
        val created = WidgetInstanceConfig.default(appWidgetId, type)
        saveConfig(created)
        return created
    }

    suspend fun saveConfig(config: WidgetInstanceConfig) {
        store.edit { prefs ->
            prefs[configKey(config.appWidgetId)] =
                json.encodeToString(WidgetInstanceConfig.serializer(), config)
        }
    }

    suspend fun updateConfig(appWidgetId: Int, transform: (WidgetInstanceConfig) -> WidgetInstanceConfig) {
        store.edit { prefs ->
            val current = prefs[configKey(appWidgetId)]
                ?.let { decodeOrNull(WidgetInstanceConfig.serializer(), it) }
                ?: return@edit
            prefs[configKey(appWidgetId)] =
                json.encodeToString(WidgetInstanceConfig.serializer(), transform(current))
        }
    }

    /** Removes a deleted widget's configuration and its anti-repeat history. */
    suspend fun deleteInstance(appWidgetId: Int) {
        store.edit { prefs ->
            val config = prefs[configKey(appWidgetId)]
                ?.let { decodeOrNull(WidgetInstanceConfig.serializer(), it) }
            prefs.remove(configKey(appWidgetId))
            config?.let { prefs.remove(historyKey(it.instanceKey)) }
        }
    }

    /**
     * Drops configuration for widgets the host no longer knows about. Android
     * usually delivers `ACTION_APPWIDGET_DELETED`, but a restore from backup or
     * a launcher crash can leave orphans behind.
     */
    suspend fun pruneOrphans(liveAppWidgetIds: Set<Int>) {
        store.edit { prefs ->
            val staleConfigKeys = prefs.asMap().keys
                .filter { it.name.startsWith(CONFIG_PREFIX) }
                .filter { key ->
                    val id = key.name.removePrefix(CONFIG_PREFIX).toIntOrNull()
                    id == null || id !in liveAppWidgetIds
                }
            val staleInstanceKeys = staleConfigKeys.mapNotNull { key ->
                prefs[stringPreferencesKey(key.name)]
                    ?.let { decodeOrNull(WidgetInstanceConfig.serializer(), it) }
                    ?.instanceKey
            }
            staleConfigKeys.forEach { prefs.remove(stringPreferencesKey(it.name)) }
            staleInstanceKeys.forEach { prefs.remove(historyKey(it)) }
        }
    }

    suspend fun allConfigs(): List<WidgetInstanceConfig> = store.data.first().asMap()
        .filterKeys { it.name.startsWith(CONFIG_PREFIX) }
        .values
        .filterIsInstance<String>()
        .mapNotNull { decodeOrNull(WidgetInstanceConfig.serializer(), it) }

    // --- anti-repeat history ------------------------------------------------

    /**
     * All pools' history for one widget instance, keyed by pool name. Stored as a
     * single record so widget deletion is a single key removal and a read costs
     * one decode regardless of how many pools the widget uses.
     */
    suspend fun history(instanceKey: String): Map<String, PoolHistory> {
        val raw = store.data.first()[historyKey(instanceKey)] ?: return emptyMap()
        return decodeOrNull(historySerializer, raw) ?: emptyMap()
    }

    suspend fun poolHistory(instanceKey: String, poolKey: String): PoolHistory =
        history(instanceKey)[poolKey] ?: PoolHistory()

    suspend fun recordHistory(instanceKey: String, poolKey: String, updated: PoolHistory) {
        store.edit { prefs ->
            val existing = prefs[historyKey(instanceKey)]
                ?.let { decodeOrNull(historySerializer, it) }
                .orEmpty()
            prefs[historyKey(instanceKey)] =
                json.encodeToString(historySerializer, existing + (poolKey to updated))
        }
    }

    suspend fun recordHistories(instanceKey: String, updates: Map<String, PoolHistory>) {
        if (updates.isEmpty()) return
        store.edit { prefs ->
            val existing = prefs[historyKey(instanceKey)]
                ?.let { decodeOrNull(historySerializer, it) }
                .orEmpty()
            prefs[historyKey(instanceKey)] = json.encodeToString(historySerializer, existing + updates)
        }
    }

    suspend fun clearHistory(instanceKey: String) {
        store.edit { prefs -> prefs.remove(historyKey(instanceKey)) }
    }

    // --- internals ----------------------------------------------------------

    /**
     * Decoding never throws into a widget update: a preference written by an
     * older build that no longer parses is treated as absent, and the caller
     * falls back to a default. Losing a personality override is acceptable;
     * crashing the launcher's widget host is not.
     */
    private fun <T> decodeOrNull(
        serializer: kotlinx.serialization.KSerializer<T>,
        raw: String,
    ): T? = runCatching { json.decodeFromString(serializer, raw) }.getOrNull()

    private val historySerializer = MapSerializer(String.serializer(), PoolHistory.serializer())

    private fun configKey(appWidgetId: Int) = stringPreferencesKey("$CONFIG_PREFIX$appWidgetId")

    private fun historyKey(instanceKey: String) = stringPreferencesKey("$HISTORY_PREFIX$instanceKey")

    companion object {
        private const val CONFIG_PREFIX = "widget_config_"
        private const val HISTORY_PREFIX = "widget_history_"
        private val GLOBAL_KEY = stringPreferencesKey("global_preferences")

        // Holds the Application context only, which lives as long as the
        // process; the constructor below always unwraps to it.
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: SoftDreadStore? = null

        fun get(context: Context): SoftDreadStore =
            instance ?: synchronized(this) {
                instance ?: SoftDreadStore(context.applicationContext).also { instance = it }
            }
    }
}
