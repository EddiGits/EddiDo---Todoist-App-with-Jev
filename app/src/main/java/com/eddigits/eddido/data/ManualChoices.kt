package com.eddigits.eddido.data

import com.eddigits.eddido.model.Recurrence
import com.eddigits.eddido.model.ReminderKind
import com.eddigits.eddido.model.Task
import java.time.LocalDateTime

/** What the user picked with the Date / Priority / Reminders / … chips; beats both the parser and the AI. */
data class ManualChoices(
    val dueSet: Boolean = false,
    val due: LocalDateTime? = null,
    val hasTime: Boolean = false,
    val priority: Int? = null,
    val reminder: ReminderKind? = null,
    val project: String? = null,
    val recurrenceSet: Boolean = false,
    val recurrence: Recurrence? = null,
    val labels: List<String> = emptyList(),
) {
    fun applyTo(t: Task): Task {
        var out = t
        if (dueSet) out = out.copy(due = due, hasTime = due != null && hasTime)
        if (priority != null) out = out.copy(priority = priority)
        if (project != null) out = out.copy(project = project)
        if (recurrenceSet) out = out.copy(recurrence = recurrence)
        if (labels.isNotEmpty()) out = out.copy(labels = (out.labels + labels).distinct())
        out = when {
            reminder != null -> out.copy(reminder = reminder)
            // Picking a time with the chip implies you want to be reminded.
            dueSet && out.hasTime && out.reminder == ReminderKind.NONE -> out.copy(reminder = ReminderKind.NOTIFY)
            else -> out
        }
        // A reminder needs a time to fire; fall back to 9 AM on the chosen day.
        if (out.reminder != ReminderKind.NONE && out.due != null && !out.hasTime) {
            out = out.copy(due = out.due!!.toLocalDate().atTime(9, 0), hasTime = true)
        }
        return out
    }
}
