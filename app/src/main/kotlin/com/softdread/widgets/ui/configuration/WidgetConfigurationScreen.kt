package com.softdread.widgets.ui.configuration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.softdread.widgets.R
import com.softdread.widgets.data.prefs.AppearanceMode
import com.softdread.widgets.data.prefs.GlobalPreferences
import com.softdread.widgets.data.prefs.WidgetInstanceConfig
import com.softdread.widgets.design.SoftDreadPalette
import com.softdread.widgets.design.SoftDreadSpacing
import com.softdread.widgets.design.SoftDreadTheme
import com.softdread.widgets.domain.model.Personality
import com.softdread.widgets.domain.model.WidgetBreakpoint
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.ui.WidgetCatalog
import com.softdread.widgets.ui.components.ChoicePill
import com.softdread.widgets.ui.components.PreviewTile
import com.softdread.widgets.ui.components.SectionLabel
import com.softdread.widgets.ui.detail.WidgetSpecificSettings
import com.softdread.widgets.ui.labelRes
import com.softdread.widgets.ui.preview.WidgetPreview
import com.softdread.widgets.ui.preview.WidgetPreviewer

/**
 * The configuration screen shown when a widget is first placed or later
 * reconfigured. The preview updates as the controls change, so the user sees the
 * tile they are about to keep.
 */
@Composable
fun WidgetConfigurationScreen(
    config: WidgetInstanceConfig,
    preferences: GlobalPreferences,
    isDark: Boolean,
    onChange: ((WidgetInstanceConfig) -> WidgetInstanceConfig) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val chrome = SoftDreadTheme.chrome
    val type = config.widgetType ?: return
    val entry = WidgetCatalog.entry(type)
    var preview by remember { mutableStateOf<WidgetPreview?>(null) }

    LaunchedEffect(config, isDark) {
        preview = runCatching {
            WidgetPreviewer.previewOrSample(
                context = context,
                type = type,
                config = config,
                preferences = preferences,
                breakpoint = WidgetBreakpoint.COMPACT,
                isDark = isDark,
            )
        }.getOrNull()
    }

    Scaffold(modifier = modifier, containerColor = chrome.wallpaper) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(SoftDreadSpacing.XLarge),
        ) {
            Text(
                text = stringResource(entry.nameRes),
                style = MaterialTheme.typography.headlineLarge,
                color = chrome.onSurface,
            )
            Spacer(Modifier.height(SoftDreadSpacing.Small))
            Text(
                text = stringResource(entry.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = chrome.secondaryType,
            )
            Spacer(Modifier.height(SoftDreadSpacing.Large))

            preview?.let {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PreviewTile(
                        role = type.colourRole,
                        breakpoint = WidgetBreakpoint.COMPACT,
                        content = it.content,
                        isDark = isDark,
                        modifier = Modifier.fillMaxWidth(0.55f),
                    )
                }
            }

            if (type == WidgetType.COUNTDOWN && !config.isConfigured) {
                Spacer(Modifier.height(SoftDreadSpacing.Medium))
                Text(
                    text = stringResource(R.string.countdown_needs_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoftDreadPalette.Clay,
                )
            }

            Spacer(Modifier.height(SoftDreadSpacing.XLarge))
            SectionLabel(stringResource(R.string.detail_personality))
            Spacer(Modifier.height(SoftDreadSpacing.Small))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SoftDreadSpacing.Small)) {
                ChoicePill(
                    label = stringResource(
                        R.string.detail_personality_global,
                        stringResource(preferences.defaultPersonality.labelRes),
                    ),
                    selected = config.personalityKey == null,
                    onClick = { onChange { it.copy(personalityKey = null) } },
                )
                Personality.entries.forEach { personality ->
                    ChoicePill(
                        label = stringResource(personality.labelRes),
                        selected = config.personalityKey == personality.key,
                        onClick = { onChange { it.copy(personalityKey = personality.key) } },
                    )
                }
            }

            Spacer(Modifier.height(SoftDreadSpacing.Large))
            SectionLabel(stringResource(R.string.detail_appearance))
            Spacer(Modifier.height(SoftDreadSpacing.Small))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SoftDreadSpacing.Small)) {
                AppearanceMode.entries.forEach { mode ->
                    ChoicePill(
                        label = stringResource(mode.labelRes),
                        selected = config.appearance == mode,
                        onClick = { onChange { it.copy(appearance = mode) } },
                    )
                }
            }

            Spacer(Modifier.height(SoftDreadSpacing.Large))
            SectionLabel(stringResource(R.string.detail_settings))
            Spacer(Modifier.height(SoftDreadSpacing.Small))
            WidgetSpecificSettings(
                type = type,
                config = config,
                calendars = emptyList(),
                onChange = onChange,
            )

            Spacer(Modifier.height(SoftDreadSpacing.XXLarge))
            Row(horizontalArrangement = Arrangement.spacedBy(SoftDreadSpacing.Medium)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text(stringResource(R.string.config_cancel))
                }
                Button(
                    onClick = onSave,
                    enabled = config.isConfigured,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SoftDreadPalette.Clay,
                        contentColor = SoftDreadPalette.TypeOnColour,
                    ),
                ) {
                    Text(stringResource(R.string.config_save))
                }
            }
        }
    }
}
