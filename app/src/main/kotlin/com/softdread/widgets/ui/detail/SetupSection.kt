package com.softdread.widgets.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.softdread.widgets.R
import com.softdread.widgets.design.SoftDreadSpacing
import com.softdread.widgets.design.SoftDreadTheme
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.ui.components.SectionLabel
import com.softdread.widgets.ui.components.SoftDreadOutlinedButton
import com.softdread.widgets.ui.components.SoftDreadCard
import com.softdread.widgets.ui.components.StatusBadge
import com.softdread.widgets.ui.permissions.PermissionStatus

/**
 * Contextual permission UI.
 *
 * It appears only for the widget that needs the permission, and only until it is
 * granted — the app never opens with a stack of prompts, and denying one has no
 * effect on any other widget. Each card explains the benefit before offering the
 * action, per the product's onboarding rule.
 */
@Composable
fun SetupSection(
    type: WidgetType,
    permissions: PermissionStatus,
    onOpenUsageSettings: () -> Unit,
    onRequestCalendar: () -> Unit,
    onRequestLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = when (type) {
        WidgetType.SCREEN_TIME -> SetupSpec(
            titleRes = R.string.permission_usage_title,
            bodyRes = R.string.permission_usage_body,
            actionRes = R.string.permission_usage_action,
            granted = permissions.hasUsageAccess,
            onAction = onOpenUsageSettings,
        )
        WidgetType.DAY_VIBE -> SetupSpec(
            titleRes = R.string.permission_calendar_title,
            bodyRes = R.string.permission_calendar_body,
            actionRes = R.string.permission_calendar_action,
            granted = permissions.hasCalendar,
            onAction = onRequestCalendar,
        )
        WidgetType.WEATHER -> SetupSpec(
            titleRes = R.string.permission_location_title,
            bodyRes = R.string.permission_location_body,
            actionRes = R.string.permission_location_action,
            granted = permissions.hasCoarseLocation,
            onAction = onRequestLocation,
        )
        else -> null
    } ?: return

    val chrome = SoftDreadTheme.chrome
    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(stringResource(spec.titleRes))
        Spacer(Modifier.height(SoftDreadSpacing.Medium))
        SoftDreadCard {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(spec.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        color = chrome.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (spec.granted) {
                        StatusBadge(stringResource(R.string.permission_granted), positive = true)
                    }
                }
                Spacer(Modifier.height(SoftDreadSpacing.Small))
                Text(
                    text = stringResource(spec.bodyRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = chrome.secondaryType,
                )
                if (!spec.granted) {
                    Spacer(Modifier.height(SoftDreadSpacing.Medium))
                    SoftDreadOutlinedButton(
                        label = stringResource(spec.actionRes),
                        onClick = spec.onAction,
                    )
                }
            }
        }
        Spacer(Modifier.height(SoftDreadSpacing.XLarge))
    }
}

private data class SetupSpec(
    val titleRes: Int,
    val bodyRes: Int,
    val actionRes: Int,
    val granted: Boolean,
    val onAction: () -> Unit,
)
