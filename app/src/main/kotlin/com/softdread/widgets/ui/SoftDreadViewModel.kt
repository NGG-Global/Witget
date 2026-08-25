package com.softdread.widgets.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.softdread.widgets.data.content.ContentManifest
import com.softdread.widgets.data.content.ContentRepository
import com.softdread.widgets.data.device.CalendarAccount
import com.softdread.widgets.data.device.CalendarDataSource
import com.softdread.widgets.data.prefs.AppearanceMode
import com.softdread.widgets.data.prefs.GlobalPreferences
import com.softdread.widgets.data.prefs.SavedLocation
import com.softdread.widgets.data.prefs.SoftDreadStore
import com.softdread.widgets.data.prefs.WidgetInstanceConfig
import com.softdread.widgets.data.weather.GeocodingDataSource
import com.softdread.widgets.design.ThemePack
import com.softdread.widgets.domain.model.Personality
import com.softdread.widgets.domain.model.WidgetBreakpoint
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.ui.permissions.PermissionStatus
import com.softdread.widgets.ui.preview.WidgetPreview
import com.softdread.widgets.ui.preview.WidgetPreviewer
import com.softdread.widgets.work.WidgetRefreshScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the gallery needs for one widget card. */
data class GalleryCard(
    val entry: WidgetCatalogEntry,
    val preview: WidgetPreview?,
    val placedCount: Int,
)

/**
 * State for the gallery, detail and settings screens.
 *
 * Previews run the widgets' real pipelines through [WidgetPreviewer], so what
 * the gallery shows is what the home screen will show — with one exception the
 * UI is explicit about: a widget that still needs setup falls back to a labelled
 * sample rather than pretending it has data.
 */
class SoftDreadViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SoftDreadStore.get(application)
    private val geocoding = GeocodingDataSource()

    val preferences: StateFlow<GlobalPreferences> = store.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlobalPreferences())

    private val _cards = MutableStateFlow<List<GalleryCard>>(emptyList())
    val cards: StateFlow<List<GalleryCard>> = _cards.asStateFlow()

    private val _permissions = MutableStateFlow(PermissionStatus.read(application))
    val permissions: StateFlow<PermissionStatus> = _permissions.asStateFlow()

    private val _manifest = MutableStateFlow<ContentManifest?>(null)
    val manifest: StateFlow<ContentManifest?> = _manifest.asStateFlow()

    private val _locationResults = MutableStateFlow<List<SavedLocation>>(emptyList())
    val locationResults: StateFlow<List<SavedLocation>> = _locationResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    init {
        viewModelScope.launch {
            _manifest.value = ContentRepository.get(application).manifest()
            WidgetRefreshScheduler.scheduleAll(application)
        }
    }

    fun refresh(isDark: Boolean) {
        _permissions.value = PermissionStatus.read(getApplication())
        viewModelScope.launch {
            val prefs = store.currentPreferences()
            val configs = store.allConfigs()
            _cards.value = WidgetCatalog.entries.map { entry ->
                val placed = configs.count { it.widgetTypeId == entry.type.id }
                val config = configs.firstOrNull { it.widgetTypeId == entry.type.id }
                    ?: previewConfig(entry.type)
                GalleryCard(
                    entry = entry,
                    preview = runCatching {
                        WidgetPreviewer.previewOrSample(
                            context = getApplication(),
                            type = entry.type,
                            config = config,
                            preferences = prefs,
                            breakpoint = WidgetBreakpoint.COMPACT,
                            isDark = isDark,
                        )
                    }.getOrNull(),
                    placedCount = placed,
                )
            }
        }
    }

    /**
     * A stand-in configuration for a widget that has not been placed yet.
     * Countdown gets a representative target so its gallery card can show the
     * shape of a real countdown instead of a setup prompt.
     */
    private fun previewConfig(type: WidgetType): WidgetInstanceConfig =
        WidgetInstanceConfig.default(PREVIEW_WIDGET_ID, type)

    fun setDefaultPersonality(personality: Personality) = update {
        it.copy(defaultPersonalityKey = personality.key)
    }

    fun setThemePack(pack: ThemePack) = update { it.copy(themePackKey = pack.displayKey) }

    fun setAppearance(mode: AppearanceMode) = update { it.copy(appearance = mode) }

    fun setUseCelsius(useCelsius: Boolean) = update { it.copy(useCelsius = useCelsius) }

    fun setWeekStartsOnMonday(monday: Boolean) = update { it.copy(weekStartsOnMonday = monday) }

    fun setDefaultLocation(location: SavedLocation?) = update { it.copy(defaultLocation = location) }

    fun completeOnboarding() = update { it.copy(onboardingComplete = true) }

    fun searchLocations(query: String) {
        viewModelScope.launch {
            _isSearching.value = true
            _locationResults.value = geocoding.search(query)
            _isSearching.value = false
        }
    }

    fun clearLocationResults() {
        _locationResults.value = emptyList()
    }

    fun calendars(): List<CalendarAccount> = CalendarDataSource(getApplication()).calendars()

    /** Clears every instance's anti-repeat history, per the settings action. */
    fun resetRepeatHistory(onDone: () -> Unit) {
        viewModelScope.launch {
            store.allConfigs().forEach { store.clearHistory(it.instanceKey) }
            WidgetRefreshScheduler.refreshNow(getApplication(), WidgetType.entries)
            onDone()
        }
    }

    private fun update(transform: (GlobalPreferences) -> GlobalPreferences) {
        viewModelScope.launch {
            store.updatePreferences(transform)
            WidgetRefreshScheduler.refreshNow(getApplication(), WidgetType.entries)
        }
    }

    companion object {
        /** Reserved id for previews; the platform never allocates a negative id. */
        const val PREVIEW_WIDGET_ID = -1
    }
}
