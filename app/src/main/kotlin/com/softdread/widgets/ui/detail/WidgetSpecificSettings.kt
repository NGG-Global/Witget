package com.softdread.widgets.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.softdread.widgets.R
import com.softdread.widgets.data.device.CalendarAccount
import com.softdread.widgets.data.prefs.CountdownUnit
import com.softdread.widgets.data.prefs.EquivalencyCategory
import com.softdread.widgets.data.prefs.JokeCategory
import com.softdread.widgets.data.prefs.ProgressScope
import com.softdread.widgets.data.prefs.WidgetInstanceConfig
import com.softdread.widgets.design.SoftDreadSpacing
import com.softdread.widgets.design.SoftDreadTheme
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.ui.components.PillGroup
import com.softdread.widgets.ui.components.ChoicePill
import com.softdread.widgets.ui.components.SoftDreadField
import com.softdread.widgets.ui.components.SwitchRow
import com.softdread.widgets.ui.labelRes

/**
 * Per-widget controls.
 *
 * Every option here maps to something the renderer actually reads — there are no
 * settings that quietly do nothing. Battery and Magic 8 Ball have no
 * type-specific settings at all, so the section is simply not shown for them
 * rather than padded with placeholders.
 */
@Composable
fun WidgetSpecificSettings(
    type: WidgetType,
    config: WidgetInstanceConfig,
    calendars: List<CalendarAccount>,
    onChange: ((WidgetInstanceConfig) -> WidgetInstanceConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chrome = SoftDreadTheme.chrome
    Column(modifier = modifier.fillMaxWidth()) {
        when (type) {
            WidgetType.SCREEN_TIME -> {
                Label(stringResource(R.string.screentime_category_label))
                PillGroup {
                    EquivalencyCategory.entries.forEach { category ->
                        ChoicePill(
                            label = stringResource(category.labelRes),
                            selected = config.equivalencyCategory == category,
                            onClick = { onChange { it.copy(equivalencyCategoryKey = category.key) } },
                        )
                    }
                }
            }

            WidgetType.DAILY_JOKE -> {
                Label(stringResource(R.string.joke_categories_label))
                PillGroup {
                    JokeCategory.entries.forEach { category ->
                        val enabled = category in config.jokeCategories
                        ChoicePill(
                            label = stringResource(category.labelRes),
                            selected = enabled,
                            onClick = {
                                onChange { current ->
                                    val keys = current.jokeCategoryKeys.toMutableList()
                                    if (enabled) keys.remove(category.key) else keys.add(category.key)
                                    // Never leave the pool empty; the last enabled
                                    // category cannot be switched off.
                                    current.copy(
                                        jokeCategoryKeys = keys.ifEmpty { current.jokeCategoryKeys },
                                    )
                                }
                            },
                        )
                    }
                }
            }

            WidgetType.DAY_VIBE -> {
                Label(stringResource(R.string.dayvibe_calendars_label))
                if (calendars.isEmpty()) {
                    Text(
                        text = stringResource(R.string.dayvibe_calendars_all),
                        style = MaterialTheme.typography.bodyMedium,
                        color = chrome.secondaryType,
                    )
                } else {
                    PillGroup {
                        ChoicePill(
                            label = stringResource(R.string.dayvibe_calendars_all),
                            selected = config.calendarIds.isEmpty(),
                            onClick = { onChange { it.copy(calendarIds = emptyList()) } },
                        )
                        calendars.forEach { calendar ->
                            val selected = calendar.id in config.calendarIds
                            ChoicePill(
                                label = calendar.displayName.ifBlank { calendar.accountName },
                                selected = selected,
                                onClick = {
                                    onChange { current ->
                                        val ids = current.calendarIds.toMutableList()
                                        if (selected) ids.remove(calendar.id) else ids.add(calendar.id)
                                        current.copy(calendarIds = ids)
                                    }
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(SoftDreadSpacing.Small))
                SwitchRow(
                    label = stringResource(R.string.dayvibe_titles_label),
                    checked = config.showEventTitles,
                    onCheckedChange = { value -> onChange { it.copy(showEventTitles = value) } },
                )
            }

            WidgetType.WEATHER -> {
                Label(stringResource(R.string.weather_location_label))
                SwitchRow(
                    label = stringResource(R.string.settings_weather_auto),
                    checked = config.useDeviceLocation,
                    onCheckedChange = { value -> onChange { it.copy(useDeviceLocation = value) } },
                )
                Text(
                    text = config.savedLocation?.name ?: stringResource(R.string.weather_use_global),
                    style = MaterialTheme.typography.bodyMedium,
                    color = chrome.secondaryType,
                )
            }

            WidgetType.COUNTDOWN -> {
                CountdownSettings(config = config, onChange = onChange)
            }

            WidgetType.TIME_PROGRESS -> {
                Label(stringResource(R.string.progress_scope_label))
                PillGroup {
                    ProgressScope.entries.forEach { scope ->
                        ChoicePill(
                            label = stringResource(scope.labelRes),
                            selected = config.progressScope == scope,
                            onClick = { onChange { it.copy(progressScopeKey = scope.key) } },
                        )
                    }
                }
            }

            WidgetType.BATTERY, WidgetType.MAGIC_8_BALL -> Unit
        }
    }
}

@Composable
fun CountdownSettings(
    config: WidgetInstanceConfig,
    onChange: ((WidgetInstanceConfig) -> WidgetInstanceConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SoftDreadField(
            value = config.countdownTitle,
            onValueChange = { value -> onChange { it.copy(countdownTitle = value.take(40)) } },
            label = stringResource(R.string.countdown_title_label),
            placeholder = stringResource(R.string.countdown_title_hint),
        )
        Spacer(Modifier.height(SoftDreadSpacing.Medium))
        CountdownDateField(config = config, onChange = onChange)
        Spacer(Modifier.height(SoftDreadSpacing.Medium))
        Label(stringResource(R.string.countdown_unit_label))
        PillGroup {
            CountdownUnit.entries.forEach { unit ->
                ChoicePill(
                    label = stringResource(unit.labelRes),
                    selected = config.countdownUnit == unit,
                    onClick = { onChange { it.copy(countdownUnitKey = unit.key) } },
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = SoftDreadTheme.chrome.secondaryType,
    )
    Spacer(Modifier.height(SoftDreadSpacing.Small))
}
