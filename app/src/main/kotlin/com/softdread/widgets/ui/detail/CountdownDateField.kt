package com.softdread.widgets.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import com.softdread.widgets.R
import com.softdread.widgets.data.prefs.WidgetInstanceConfig
import com.softdread.widgets.design.SoftDreadShape
import com.softdread.widgets.design.SoftDreadSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Date and time entry for a countdown.
 *
 * The picked date is combined with the picked time in the countdown's own zone
 * and stored as an absolute instant, so the target does not drift when the user
 * travels, and the widget's remaining-time arithmetic has a single unambiguous
 * moment to work from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownDateField(
    config: WidgetInstanceConfig,
    onChange: ((WidgetInstanceConfig) -> WidgetInstanceConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember(config.countdownZoneId) {
        config.countdownZoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
    }
    val current = remember(config.countdownTargetEpochMillis, zone) {
        if (config.countdownTargetEpochMillis > 0L) {
            ZonedDateTime.ofInstant(Instant.ofEpochMilli(config.countdownTargetEpochMillis), zone)
        } else {
            ZonedDateTime.now(zone).plusDays(7).withHour(9).withMinute(0)
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    fun commit(date: LocalDate, time: LocalTime) {
        val target = ZonedDateTime.of(date, time, zone)
        onChange {
            it.copy(
                countdownTargetEpochMillis = target.toInstant().toEpochMilli(),
                countdownZoneId = zone.id,
                countdownCreatedAtEpochMillis = if (it.countdownCreatedAtEpochMillis > 0L) {
                    it.countdownCreatedAtEpochMillis
                } else {
                    System.currentTimeMillis()
                },
            )
        }
    }

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SoftDreadSpacing.Small)) {
        OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
            Text(current.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
        }
        OutlinedButton(onClick = { showTimePicker = true }) {
            Text(current.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")))
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = current.toLocalDate()
                .atStartOfDay(ZoneId.of("UTC"))
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    // The picker reports UTC midnight; read it back in UTC so the
                    // calendar date the user tapped is the date that is stored.
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        commit(date, current.toLocalTime())
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.common_done)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.config_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = current.hour,
            initialMinute = current.minute,
            is24Hour = true,
        )
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Surface(
                shape = RoundedCornerShape(SoftDreadShape.CardRadius),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.padding(SoftDreadSpacing.Large)) {
                    TimePicker(state = timeState)
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text(stringResource(R.string.config_cancel))
                        }
                        TextButton(onClick = {
                            commit(current.toLocalDate(), LocalTime.of(timeState.hour, timeState.minute))
                            showTimePicker = false
                        }) { Text(stringResource(R.string.common_done)) }
                    }
                }
            }
        }
    }
}
