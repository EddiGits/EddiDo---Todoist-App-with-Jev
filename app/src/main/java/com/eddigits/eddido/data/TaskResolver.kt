package com.eddigits.eddido.data

import com.eddigits.eddido.ai.AiResult
import com.eddigits.eddido.model.Recurrence
import com.eddigits.eddido.model.ReminderKind
import com.eddigits.eddido.model.Task
import com.eddigits.eddido.parse.ParsedTask
import java.time.LocalDateTime

/** The final values for a typed task, and which of them came from the AI. */
data class Resolved(
    val title: String,
    val due: LocalDateTime?,
    val hasTime: Boolean,
    val recurrence: Recurrence?,
    val priority: Int,
    val project: String,
    val labels: List<String>,
    val reminder: ReminderKind,
    /** Fields decided by the AI: "title", "date", "priority", "project", "reminder", "labels". */
    val aiDerived: Set<String>,
)

/**
 * Decides every field of a typed task. The AI decides everything it can. The app's own
 * parser only fills in until the AI answers, and stands in when the AI can't be reached.
 *
 * For each field, the first source that has an answer wins:
 * 1. what you picked with a chip ([ManualChoices]);
 * 2. what you typed explicitly, as read by the language model (#project, p1);
 * 3. Jev: project, labels, priority, reminder;
 * 4. the language model: title, date, time, repeat, and the rest when Jev has no answer;
 * 5. the offline parser and keyword rules.
 *
 * Used by both the live chips and the saved task, so they always agree.
 */
object TaskResolver {

    fun resolve(
        text: String,
        parsed: ParsedTask,
        manual: ManualChoices,
        jev: AiResult?,
        language: AiResult?,
        offlineProject: String?,
        fallbackToday: Boolean,
        now: LocalDateTime,
    ): Resolved {
        val ai = mutableSetOf<String>()

        val title = language?.title?.takeIf { it.isNotBlank() }?.also { ai += "title" }
            ?: parsed.title.ifBlank { text.trim() }

        // Date, time and repeat come as one unit from a single source.
        var due: LocalDateTime? = null
        var hasTime = false
        var recurrence: Recurrence? = null
        when {
            manual.dueSet -> { due = manual.due; hasTime = manual.hasTime && manual.due != null }
            language != null && language.dateAnswered && isUsable(language, now) && (language.due != null || parsed.due == null) -> {
                due = language.due; hasTime = language.hasTime; recurrence = language.recurrence
                if (due != null) ai += "date"
            }
            // The model saw no date where the parser clearly found one (or gave a past date):
            // keep the parser's date rather than silently dropping it.
            parsed.due != null -> { due = parsed.due; hasTime = parsed.hasTime; recurrence = parsed.recurrence }
        }
        if (manual.recurrenceSet) recurrence = manual.recurrence
        if (due == null && fallbackToday) due = now.toLocalDate().atStartOfDay()

        val priority = manual.priority
            ?: language?.takeIf { it.explicitPriority }?.priority?.also { ai += "priority" }
            // Until the language model answers, a typed "p1" read by the parser still counts.
            ?: parsed.priority.takeIf { language == null }
            ?: jev?.priority?.also { if (it < 4) ai += "priority" }
            ?: language?.priority?.also { if (it < 4) ai += "priority" }
            ?: parsed.priority
            ?: 4

        val project = manual.project
            ?: language?.explicitProject?.also { ai += "project" }
            ?: parsed.project.takeIf { language == null }
            ?: jev?.project?.also { ai += "project" }
            ?: language?.project?.also { ai += "project" }
            ?: offlineProject
            ?: Task.INBOX

        val aiLabels = (language?.labels ?: parsed.labels) + (jev?.labels ?: emptyList())
        if ((language?.labels.orEmpty() + jev?.labels.orEmpty()).isNotEmpty()) ai += "labels"
        val labels = (aiLabels + manual.labels).map { it.lowercase() }.distinct().take(5)

        var reminder = manual.reminder
            ?: jev?.reminder?.also { ai += "reminder" }
            ?: language?.reminder?.also { ai += "reminder" }
            ?: parsed.reminder
        // A reminder can only fire at a moment: without a date there is nothing to ring,
        // and without a time it rings at 9 AM, as in Todoist.
        if (reminder != ReminderKind.NONE) {
            val d = due
            if (d == null) reminder = ReminderKind.NONE
            else if (!hasTime) { due = d.toLocalDate().atTime(9, 0); hasTime = true }
        }
        if (reminder == ReminderKind.NONE) ai -= "reminder"

        return Resolved(title, due, hasTime, recurrence, priority, project, labels, reminder, ai)
    }

    /** The model's date is only trusted when it is not in the past (repeats roll forward anyway). */
    private fun isUsable(r: AiResult, now: LocalDateTime): Boolean {
        val due = r.due ?: return true
        if (r.recurrence != null) return true
        return if (r.hasTime) !due.isBefore(now.minusMinutes(2)) else !due.toLocalDate().isBefore(now.toLocalDate())
    }
}
