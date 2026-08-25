package com.softdread.widgets.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softdread.widgets.R
import com.softdread.widgets.data.prefs.AppearanceMode
import com.softdread.widgets.data.weather.OpenMeteoProvider
import com.softdread.widgets.design.SoftDreadSpacing
import com.softdread.widgets.design.SoftDreadTheme
import com.softdread.widgets.design.SoftDreadType
import com.softdread.widgets.design.ThemePack
import com.softdread.widgets.domain.model.Personality
import com.softdread.widgets.ui.SoftDreadViewModel
import com.softdread.widgets.ui.blurbRes
import com.softdread.widgets.ui.components.PillGroup
import com.softdread.widgets.ui.components.ChoicePill
import com.softdread.widgets.ui.components.Masthead
import com.softdread.widgets.ui.components.SectionLabel
import com.softdread.widgets.ui.components.Rule
import com.softdread.widgets.ui.components.SoftDreadCard
import com.softdread.widgets.ui.components.SoftDreadField
import com.softdread.widgets.ui.components.SoftDreadOutlinedButton
import com.softdread.widgets.ui.components.SoftDreadTextAction
import com.softdread.widgets.ui.components.SpecLine
import com.softdread.widgets.ui.components.SwitchRow
import com.softdread.widgets.ui.labelRes

/**
 * Global settings: the default personality every widget inherits, the pack's
 * theme, units, the shared weather location, and the app's privacy statement.
 */
@Composable
fun SettingsScreen(viewModel: SoftDreadViewModel, modifier: Modifier = Modifier) {
    val chrome = SoftDreadTheme.chrome
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val manifest by viewModel.manifest.collectAsStateWithLifecycle()
    val locationResults by viewModel.locationResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var historyCleared by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SoftDreadSpacing.XLarge),
    ) {
        Masthead(
            title = stringResource(R.string.settings_title),
            subtitle = stringResource(R.string.gallery_subtitle),
        )
        Spacer(Modifier.height(SoftDreadSpacing.XLarge))

        SectionLabel(stringResource(R.string.settings_personality))
        Spacer(Modifier.height(SoftDreadSpacing.Medium))
        PillGroup {
            Personality.entries.forEach { personality ->
                ChoicePill(
                    label = stringResource(personality.labelRes),
                    selected = preferences.defaultPersonality == personality,
                    onClick = { viewModel.setDefaultPersonality(personality) },
                )
            }
        }
        Spacer(Modifier.height(SoftDreadSpacing.Small))
        Text(
            text = stringResource(preferences.defaultPersonality.blurbRes),
            style = MaterialTheme.typography.bodyMedium,
            color = chrome.secondaryType,
        )
        Spacer(Modifier.height(SoftDreadSpacing.XSmall))
        Text(
            text = stringResource(R.string.settings_personality_blurb),
            style = MaterialTheme.typography.bodySmall,
            color = chrome.secondaryType,
        )

        Spacer(Modifier.height(SoftDreadSpacing.XXLarge))
        SectionLabel(stringResource(R.string.settings_theme))
        Spacer(Modifier.height(SoftDreadSpacing.Medium))
        PillGroup {
            ThemePack.entries.forEach { pack ->
                ChoicePill(
                    label = stringResource(pack.labelRes),
                    selected = preferences.themePackKey == pack.displayKey,
                    onClick = { viewModel.setThemePack(pack) },
                )
            }
        }
        Spacer(Modifier.height(SoftDreadSpacing.Small))
        Text(
            text = stringResource(R.string.settings_theme_blurb),
            style = MaterialTheme.typography.bodySmall,
            color = chrome.secondaryType,
        )

        Spacer(Modifier.height(SoftDreadSpacing.XXLarge))
        SectionLabel(stringResource(R.string.settings_appearance))
        Spacer(Modifier.height(SoftDreadSpacing.Medium))
        PillGroup {
            AppearanceMode.entries.forEach { mode ->
                ChoicePill(
                    label = stringResource(mode.labelRes),
                    selected = preferences.appearance == mode,
                    onClick = { viewModel.setAppearance(mode) },
                )
            }
        }

        Spacer(Modifier.height(SoftDreadSpacing.XXLarge))
        SectionLabel(stringResource(R.string.settings_units))
        Spacer(Modifier.height(SoftDreadSpacing.Medium))
        SoftDreadCard {
            Column {
                SwitchRow(
                    label = stringResource(R.string.settings_celsius),
                    checked = preferences.useCelsius,
                    onCheckedChange = viewModel::setUseCelsius,
                )
                SwitchRow(
                    label = stringResource(R.string.settings_week_monday),
                    checked = preferences.weekStartsOnMonday,
                    onCheckedChange = viewModel::setWeekStartsOnMonday,
                )
            }
        }

        Spacer(Modifier.height(SoftDreadSpacing.XXLarge))
        SectionLabel(stringResource(R.string.settings_weather_location))
        Spacer(Modifier.height(SoftDreadSpacing.Medium))
        SoftDreadCard {
            Column {
                Text(
                    text = preferences.defaultLocation?.name
                        ?: stringResource(R.string.settings_weather_auto),
                    style = MaterialTheme.typography.bodyLarge,
                    color = chrome.onSurface,
                )
                Spacer(Modifier.height(SoftDreadSpacing.Medium))
                SoftDreadField(
                    value = query,
                    onValueChange = {
                        query = it
                        if (it.length >= 2) viewModel.searchLocations(it) else viewModel.clearLocationResults()
                    },
                    label = stringResource(R.string.settings_weather_search),
                    placeholder = "tel aviv",
                )
                if (isSearching) {
                    Spacer(Modifier.height(SoftDreadSpacing.Medium))
                    SpecLine(stringResource(R.string.settings_weather_searching))
                } else if (query.length >= 2 && locationResults.isEmpty()) {
                    Spacer(Modifier.height(SoftDreadSpacing.Medium))
                    SpecLine(stringResource(R.string.settings_weather_no_results))
                }
                locationResults.forEachIndexed { index, location ->
                    if (index == 0) Spacer(Modifier.height(SoftDreadSpacing.Small))
                    Rule()
                    SoftDreadTextAction(
                        label = listOfNotNull(location.name, location.country).joinToString(" · "),
                        onClick = {
                            viewModel.setDefaultLocation(location)
                            query = ""
                            viewModel.clearLocationResults()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (preferences.defaultLocation != null) {
                    Spacer(Modifier.height(SoftDreadSpacing.Large))
                    SoftDreadOutlinedButton(
                        label = stringResource(R.string.settings_weather_auto),
                        onClick = { viewModel.setDefaultLocation(null) },
                    )
                }
            }
        }

        Spacer(Modifier.height(SoftDreadSpacing.XXLarge))
        SectionLabel(stringResource(R.string.settings_reset_history))
        Spacer(Modifier.height(SoftDreadSpacing.Medium))
        SoftDreadCard {
            Column {
                Text(
                    text = stringResource(R.string.settings_reset_history_blurb),
                    style = MaterialTheme.typography.bodyMedium,
                    color = chrome.secondaryType,
                )
                Spacer(Modifier.height(SoftDreadSpacing.Medium))
                SoftDreadOutlinedButton(
                    label = stringResource(R.string.settings_reset_history),
                    onClick = { viewModel.resetRepeatHistory { historyCleared = true } },
                )
                if (historyCleared) {
                    Spacer(Modifier.height(SoftDreadSpacing.Medium))
                    SpecLine(stringResource(R.string.settings_reset_done))
                }
            }
        }

        Spacer(Modifier.height(SoftDreadSpacing.XXLarge))
        SectionLabel(stringResource(R.string.settings_privacy))
        Spacer(Modifier.height(SoftDreadSpacing.Medium))
        SoftDreadCard {
            Column {
                Text(
                    text = stringResource(R.string.settings_privacy_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = chrome.secondaryType,
                )
                Spacer(Modifier.height(SoftDreadSpacing.Medium))
                Text(
                    text = stringResource(R.string.settings_attribution),
                    style = MaterialTheme.typography.titleSmall,
                    color = chrome.onSurface,
                )
                SpecLine(OpenMeteoProvider().attribution)
                manifest?.let { content ->
                    Spacer(Modifier.height(SoftDreadSpacing.Medium))
                    Text(
                        text = stringResource(R.string.settings_content_pack),
                        style = MaterialTheme.typography.titleSmall,
                        color = chrome.onSurface,
                    )
                    val stringCount = content.totalResponseCount +
                        content.jokeCount +
                        content.equivalencyUnitCount
                    SpecLine(
                        pluralStringResource(
                            R.plurals.settings_content_pack_body,
                            stringCount,
                            stringCount,
                            content.files.size,
                        ),
                    )
                }
            }
        }
    }
}
