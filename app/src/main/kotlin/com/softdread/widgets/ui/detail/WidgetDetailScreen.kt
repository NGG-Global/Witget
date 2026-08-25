package com.softdread.widgets.ui.detail

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softdread.widgets.R
import com.softdread.widgets.data.device.ScreenTimeDataSource
import com.softdread.widgets.data.prefs.AppearanceMode
import com.softdread.widgets.design.SoftDreadPalette
import com.softdread.widgets.design.SoftDreadSpacing
import com.softdread.widgets.design.SoftDreadTheme
import com.softdread.widgets.design.SoftDreadType
import com.softdread.widgets.domain.model.Personality
import com.softdread.widgets.domain.model.WidgetBreakpoint
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.ui.PinWidget
import com.softdread.widgets.ui.WidgetCatalog
import com.softdread.widgets.ui.components.ChoicePill
import com.softdread.widgets.ui.components.PreviewTile
import com.softdread.widgets.ui.components.SectionLabel
import com.softdread.widgets.ui.components.SoftDreadCard
import com.softdread.widgets.ui.labelRes
import com.softdread.widgets.ui.permissions.PermissionStatus
import com.softdread.widgets.widgets.magic8ball.refreshMagic8Ball
import kotlinx.coroutines.launch

/**
 * Widget detail and customisation.
 *
 * The preview at the top is the real tile at the selected breakpoint, so every
 * control below it — personality, appearance, and the widget's own settings —
 * shows its effect immediately rather than describing it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetDetailScreen(
    type: WidgetType,
    appWidgetId: Int?,
    viewModel: WidgetDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val chrome = SoftDreadTheme.chrome
    val state by viewModel.state.collectAsStateWithLifecycle()
    val entry = WidgetCatalog.entry(type)
    val scope = rememberCoroutineScope()
    var permissions by remember { mutableStateOf(PermissionStatus.read(context)) }
    var pinFailed by remember { mutableStateOf(false) }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // Usage Access is granted outside the app, so re-read on return rather
        // than trusting a result code the Settings screen does not send.
        permissions = PermissionStatus.read(context)
        viewModel.refreshPreview(chrome.isDark)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        permissions = PermissionStatus.read(context)
        viewModel.refreshPreview(chrome.isDark)
    }

    LaunchedEffect(type, appWidgetId, chrome.isDark) {
        viewModel.load(type, appWidgetId, chrome.isDark)
    }

    Scaffold(
        modifier = modifier,
        containerColor = chrome.wallpaper,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(entry.nameRes), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = chrome.wallpaper,
                    titleContentColor = chrome.onSurface,
                    navigationIconContentColor = chrome.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SoftDreadSpacing.XLarge)
                .padding(bottom = SoftDreadSpacing.XXLarge),
        ) {
            Text(
                text = stringResource(entry.descriptionRes),
                style = MaterialTheme.typography.bodyLarge,
                color = chrome.secondaryType,
            )
            Spacer(Modifier.height(SoftDreadSpacing.Large))

            state.preview?.let { preview ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PreviewTile(
                        role = type.colourRole,
                        breakpoint = state.breakpoint,
                        content = preview.content,
                        isDark = chrome.isDark,
                        modifier = Modifier.fillMaxWidth(
                            if (state.breakpoint == WidgetBreakpoint.COMPACT) 0.55f else 1f,
                        ),
                    )
                }
                if (preview.isSample) {
                    Spacer(Modifier.height(SoftDreadSpacing.Small))
                    Text(
                        text = stringResource(R.string.gallery_sample_label),
                        style = SoftDreadType.Mono,
                        color = chrome.secondaryType,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(SoftDreadSpacing.Large))
            SectionLabel(stringResource(R.string.detail_size))
            Spacer(Modifier.height(SoftDreadSpacing.Small))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SoftDreadSpacing.Small)) {
                listOf(
                    WidgetBreakpoint.COMPACT to R.string.size_compact,
                    WidgetBreakpoint.STANDARD to R.string.size_standard,
                    WidgetBreakpoint.EXPANDED to R.string.size_expanded,
                ).forEach { (breakpoint, labelRes) ->
                    ChoicePill(
                        label = stringResource(labelRes),
                        selected = state.breakpoint == breakpoint,
                        onClick = { viewModel.setBreakpoint(breakpoint, chrome.isDark) },
                    )
                }
            }

            Spacer(Modifier.height(SoftDreadSpacing.XLarge))
            SetupSection(
                type = type,
                permissions = permissions,
                onOpenUsageSettings = { settingsLauncher.launch(ScreenTimeDataSource(context).settingsIntent()) },
                onRequestCalendar = { permissionLauncher.launch(android.Manifest.permission.READ_CALENDAR) },
                onRequestLocation = {
                    permissionLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                },
            )

            SectionLabel(stringResource(R.string.detail_personality))
            Spacer(Modifier.height(SoftDreadSpacing.Small))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SoftDreadSpacing.Small)) {
                ChoicePill(
                    label = stringResource(
                        R.string.detail_personality_global,
                        stringResource(state.preferences.defaultPersonality.labelRes),
                    ),
                    selected = state.config?.personalityKey == null,
                    onClick = { viewModel.updateConfig(chrome.isDark) { it.copy(personalityKey = null) } },
                )
                Personality.entries.forEach { personality ->
                    ChoicePill(
                        label = stringResource(personality.labelRes),
                        selected = state.config?.personalityKey == personality.key,
                        onClick = {
                            viewModel.updateConfig(chrome.isDark) { it.copy(personalityKey = personality.key) }
                        },
                    )
                }
            }

            Spacer(Modifier.height(SoftDreadSpacing.XLarge))
            SectionLabel(stringResource(R.string.detail_appearance))
            Spacer(Modifier.height(SoftDreadSpacing.Small))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SoftDreadSpacing.Small)) {
                AppearanceMode.entries.forEach { mode ->
                    ChoicePill(
                        label = stringResource(mode.labelRes),
                        selected = state.config?.appearance == mode,
                        onClick = { viewModel.updateConfig(chrome.isDark) { it.copy(appearance = mode) } },
                    )
                }
            }

            val config = state.config
            if (config?.isTemplate == true) {
                Spacer(Modifier.height(SoftDreadSpacing.Medium))
                Text(
                    text = stringResource(R.string.detail_template_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = chrome.secondaryType,
                )
            }
            if (config != null && hasTypeSettings(type)) {
                Spacer(Modifier.height(SoftDreadSpacing.XLarge))
                SectionLabel(stringResource(R.string.detail_settings))
                Spacer(Modifier.height(SoftDreadSpacing.Small))
                WidgetSpecificSettings(
                    type = type,
                    config = config,
                    calendars = state.calendars,
                    onChange = { transform -> viewModel.updateConfig(chrome.isDark, transform) },
                )
            }

            if (entry.isInteractive) {
                Spacer(Modifier.height(SoftDreadSpacing.Large))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            refreshMagic8Ball(context)
                            viewModel.refreshPreview(chrome.isDark)
                        }
                    },
                ) {
                    Text(stringResource(R.string.detail_ask_again))
                }
            }

            Spacer(Modifier.height(SoftDreadSpacing.XLarge))
            Button(
                onClick = {
                    scope.launch { pinFailed = !PinWidget.request(context, type) }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SoftDreadPalette.Clay,
                    contentColor = SoftDreadPalette.TypeOnColour,
                ),
            ) {
                Text(stringResource(R.string.detail_add_to_home))
            }

            if (pinFailed || !PinWidget.isSupported(context)) {
                Spacer(Modifier.height(SoftDreadSpacing.Small))
                Text(
                    text = stringResource(R.string.detail_add_manual),
                    style = MaterialTheme.typography.bodySmall,
                    color = chrome.secondaryType,
                )
            }

            Spacer(Modifier.height(SoftDreadSpacing.Large))
            SectionLabel(stringResource(R.string.detail_instances))
            Spacer(Modifier.height(SoftDreadSpacing.Small))
            SoftDreadCard {
                if (state.placedInstances.isEmpty()) {
                    Text(
                        text = stringResource(R.string.detail_instances_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = chrome.secondaryType,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(SoftDreadSpacing.Small)) {
                        state.placedInstances.forEach { instance ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = instanceLabel(instance, type),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = chrome.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = stringResource(
                                        instance.personality(state.preferences.defaultPersonality).labelRes,
                                    ),
                                    style = SoftDreadType.Mono,
                                    color = chrome.secondaryType,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun instanceLabel(
    instance: com.softdread.widgets.data.prefs.WidgetInstanceConfig,
    type: WidgetType,
): String = when (type) {
    WidgetType.COUNTDOWN -> instance.countdownTitle.ifBlank { "#${instance.appWidgetId}" }
    WidgetType.TIME_PROGRESS -> instance.progressScope.key
    else -> "#${instance.appWidgetId}"
}

private fun hasTypeSettings(type: WidgetType): Boolean = when (type) {
    WidgetType.BATTERY, WidgetType.MAGIC_8_BALL -> false
    else -> true
}
