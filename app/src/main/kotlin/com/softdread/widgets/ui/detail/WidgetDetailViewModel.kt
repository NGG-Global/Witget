package com.softdread.widgets.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.softdread.widgets.data.device.CalendarAccount
import com.softdread.widgets.data.device.CalendarDataSource
import com.softdread.widgets.data.prefs.GlobalPreferences
import com.softdread.widgets.data.prefs.SoftDreadStore
import com.softdread.widgets.data.prefs.WidgetInstanceConfig
import com.softdread.widgets.domain.model.WidgetBreakpoint
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.ui.preview.WidgetPreview
import com.softdread.widgets.ui.preview.WidgetPreviewer
import com.softdread.widgets.work.WidgetRefreshScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WidgetDetailState(
    val type: WidgetType? = null,
    val config: WidgetInstanceConfig? = null,
    val preferences: GlobalPreferences = GlobalPreferences(),
    val preview: WidgetPreview? = null,
    val breakpoint: WidgetBreakpoint = WidgetBreakpoint.COMPACT,
    val placedInstances: List<WidgetInstanceConfig> = emptyList(),
    val calendars: List<CalendarAccount> = emptyList(),
)

/**
 * Detail-screen state for one widget type, optionally scoped to one placed
 * instance.
 *
 * Editing a placed instance writes to that `appWidgetId` alone and refreshes
 * only that widget; editing the type from the gallery edits a template
 * configuration that new instances inherit. That separation is what lets two
 * countdowns or two 8 balls hold different settings.
 */
class WidgetDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SoftDreadStore.get(application)

    private val _state = MutableStateFlow(WidgetDetailState())
    val state: StateFlow<WidgetDetailState> = _state.asStateFlow()

    fun load(type: WidgetType, appWidgetId: Int?, isDark: Boolean) {
        viewModelScope.launch {
            val preferences = store.currentPreferences()
            val placed = store.placedConfigs().filter { it.widgetTypeId == type.id }
            // Opened from a placed widget: edit that instance. Opened from the
            // gallery: edit the type's template, which new instances inherit.
            val config = appWidgetId
                ?.let { id -> placed.firstOrNull { it.appWidgetId == id } }
                ?: placed.singleOrNull()
                ?: store.template(type)

            _state.value = _state.value.copy(
                type = type,
                config = config,
                preferences = preferences,
                placedInstances = placed,
                calendars = if (type == WidgetType.DAY_VIBE) {
                    CalendarDataSource(getApplication()).calendars()
                } else {
                    emptyList()
                },
            )
            refreshPreview(isDark)
        }
    }

    fun setBreakpoint(breakpoint: WidgetBreakpoint, isDark: Boolean) {
        _state.value = _state.value.copy(breakpoint = breakpoint)
        refreshPreview(isDark)
    }

    fun updateConfig(isDark: Boolean, transform: (WidgetInstanceConfig) -> WidgetInstanceConfig) {
        val current = _state.value.config ?: return
        val updated = transform(current)
        _state.value = _state.value.copy(config = updated)
        viewModelScope.launch {
            // Both instance and template edits persist. Only an instance edit
            // needs the home screen redrawn.
            store.saveConfig(updated)
            if (!updated.isTemplate) {
                updated.widgetType?.let { WidgetRefreshScheduler.refreshNow(getApplication(), listOf(it)) }
            }
            refreshPreview(isDark)
        }
    }

    fun refreshPreview(isDark: Boolean) {
        val state = _state.value
        val type = state.type ?: return
        val config = state.config ?: return
        viewModelScope.launch {
            val preview = runCatching {
                WidgetPreviewer.previewOrSample(
                    context = getApplication(),
                    type = type,
                    config = config,
                    preferences = state.preferences,
                    breakpoint = state.breakpoint,
                    isDark = isDark,
                )
            }.getOrNull()
            _state.value = _state.value.copy(preview = preview)
        }
    }
}
