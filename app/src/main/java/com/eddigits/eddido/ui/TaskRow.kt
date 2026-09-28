package com.eddigits.eddido.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eddigits.eddido.model.ReminderKind
import com.eddigits.eddido.model.Task
import com.eddigits.eddido.ui.theme.priorityColor

@Composable
fun TaskRow(
    task: Task,
    showProject: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            PriorityCheck(task.priority, task.completed, onToggle)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (task.description.isNotBlank()) {
                    Text(
                        task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                MetaLine(task, showProject)
            }
        }
        HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun MetaLine(task: Task, showProject: Boolean) {
    val items = buildList<@Composable () -> Unit> {
        task.due?.let { due ->
            val c = dueColor(due, task.hasTime)
            add { Meta(Icons.Outlined.CalendarToday, dueLabel(due, task.hasTime), c) }
        }
        task.recurrence?.let { r -> add { Meta(Icons.Outlined.Repeat, r.label(), MaterialTheme.colorScheme.onSurfaceVariant) } }
        when (task.reminder) {
            ReminderKind.ALARM -> add { Meta(Icons.Filled.Alarm, null, MaterialTheme.colorScheme.onSurfaceVariant) }
            ReminderKind.NOTIFY -> if (task.hasTime) add { Meta(Icons.Outlined.NotificationsNone, null, MaterialTheme.colorScheme.onSurfaceVariant) }
            ReminderKind.NONE -> Unit
        }
        task.labels.forEach { l -> add { Meta(Icons.Outlined.Label, l, MaterialTheme.colorScheme.onSurfaceVariant) } }
        if (task.aiPending) add { Meta(Icons.Filled.AutoAwesome, "sorting…", MaterialTheme.colorScheme.primary) }
    }
    if (items.isEmpty() && !showProject) return
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            items.take(5).forEach { it() }
        }
        if (showProject) {
            Text(
                task.project + " #",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Meta(icon: ImageVector, text: String?, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
        if (text != null) {
            Spacer(Modifier.width(3.dp))
            Text(text, fontSize = 12.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun PriorityCheck(priority: Int, checked: Boolean, onToggle: () -> Unit) {
    val c = priorityColor(priority)
    Box(
        Modifier
            .padding(top = 2.dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(if (checked) c else c.copy(alpha = if (priority < 4) 0.12f else 0f))
            .border(1.6.dp, c, CircleShape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
    }
}
