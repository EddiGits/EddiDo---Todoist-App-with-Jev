package com.eddigits.eddido.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.NextWeek
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.eddigits.eddido.ui.theme.DateLater
import com.eddigits.eddido.ui.theme.DateToday
import com.eddigits.eddido.ui.theme.DateTomorrow
import com.eddigits.eddido.ui.theme.DateWeek
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

/**
 * Todoist-style date sheet: quick picks, then an optional calendar and time.
 * [onPick] receives null to clear the date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueDialog(
    initial: LocalDateTime?,
    initialHasTime: Boolean,
    onDismiss: () -> Unit,
    onPick: (LocalDateTime?, Boolean) -> Unit,
) {
    var step by remember { mutableStateOf("menu") }
    var date by remember { mutableStateOf(initial?.toLocalDate() ?: LocalDate.now()) }
    val today = LocalDate.now()

    when (step) {
        "menu" -> AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            title = { Text(initial?.let { dueLabel(it, initialHasTime) } ?: "Schedule") },
            text = {
                Column {
                    QuickDate(Icons.Outlined.CalendarMonth, "Today", DateToday, dayLabel(today)) { date = today; step = "time" }
                    QuickDate(Icons.Outlined.LightMode, "Tomorrow", DateTomorrow, dayLabel(today.plusDays(1))) {
                        date = today.plusDays(1); step = "time"
                    }
                    val sat = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                    QuickDate(Icons.Outlined.Weekend, "This weekend", DateWeek, dayLabel(sat)) { date = sat; step = "time" }
                    val mon = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                    QuickDate(Icons.Outlined.NextWeek, "Next week", DateWeek, dayLabel(mon)) { date = mon; step = "time" }
                    QuickDate(Icons.Outlined.WbTwilight, "Pick a date…", DateLater, "") { step = "date" }
                    QuickDate(Icons.Outlined.Block, "No date", DateLater, "") { onPick(null, false) }
                }
            },
        )
        "date" -> {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            )
            DatePickerDialog(
                onDismissRequest = onDismiss,
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        step = "time"
                    }) { Text("Next") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            ) { DatePicker(state) }
        }
        "time" -> {
            val start = initial?.takeIf { initialHasTime }?.toLocalTime() ?: LocalTime.of(9, 0)
            val state = rememberTimePickerState(start.hour, start.minute, is24Hour = false)
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Time for ${dayLabel(date)}") },
                text = { TimePicker(state) },
                confirmButton = {
                    TextButton(onClick = { onPick(date.atTime(state.hour, state.minute), true) }) { Text("Set time") }
                },
                dismissButton = {
                    TextButton(onClick = { onPick(date.atStartOfDay(), false) }) { Text("No time") }
                },
            )
        }
    }
}

@Composable
private fun QuickDate(icon: ImageVector, label: String, color: Color, hint: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Simple list dialog used for priority, reminder, repeat and project choices. */
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onDismiss: () -> Unit,
    leading: @Composable (T) -> Unit = {},
    footer: (@Composable () -> Unit)? = null,
    onPick: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Surface(
                        onClick = { onPick(value) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (value == selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            leading(value)
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                footer?.let { Spacer(Modifier.height(8.dp)); it() }
            }
        },
    )
}

@Composable
fun TextEntryDialog(title: String, initial: String, hint: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, placeholder = { Text(hint) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onDone(value) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
