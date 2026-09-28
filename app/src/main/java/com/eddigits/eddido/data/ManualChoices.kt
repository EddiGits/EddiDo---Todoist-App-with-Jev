package com.eddigits.eddido.data

import com.eddigits.eddido.model.Recurrence
import com.eddigits.eddido.model.ReminderKind
import java.time.LocalDateTime

/** What the user picked with the Date / Priority / Reminders / … chips; beats the AI and the parser (see [TaskResolver]). */
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
)
