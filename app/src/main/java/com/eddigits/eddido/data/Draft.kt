package com.eddigits.eddido.data

import com.eddigits.eddido.ai.AiResult
import com.eddigits.eddido.model.Kind
import com.eddigits.eddido.parse.KindGuess
import com.eddigits.eddido.parse.Festivals
import com.eddigits.eddido.parse.ParsedTask
import com.eddigits.eddido.parse.QuickAddParser
import java.time.LocalDate
import java.time.LocalDateTime

/** What the creator will make if you accept now: the task fields plus the per-tab details. */
data class Draft(
    val kind: Kind,
    val task: Resolved,
    /** What it's for: timer/stopwatch/focus label, countdown event, note text. */
    val label: String,
    val durationSeconds: Int,
    val focusMinutes: Int,
    val breakMinutes: Int,
    val items: List<String>,
    val listName: String,
    val perDay: Int,
    val countdownDate: LocalDate?,
    /** Which per-tab details came from the AI (for the ✨ marks). */
    val aiDetails: Set<String>,
) {
    /** Enough to accept? A countdown needs a date; everything else needs some text. */
    val ready: Boolean
        get() = when (kind) {
            Kind.COUNTDOWN -> countdownDate != null && label.isNotBlank()
            Kind.LIST -> items.isNotEmpty()
            Kind.STOPWATCH -> true
            Kind.TIMER -> durationSeconds > 0
            else -> task.title.isNotBlank()
        }
}

object DraftBuilder {
    fun build(
        kind: Kind,
        text: String,
        parsed: ParsedTask,
        manual: ManualChoices,
        jev: AiResult?,
        language: AiResult?,
        fallbackToday: Boolean,
        now: LocalDateTime,
    ): Draft {
        val task = TaskResolver.resolve(text, parsed, manual, jev, language, null, fallbackToday, now)
        val ai = mutableSetOf<String>()
        // A chat model only knows the details for the tab it chose itself. Without one,
        // Jev's meaning answers (read by code) apply to whichever tab is showing.
        val lang = language?.takeIf { it.kind == null || it.kind == kind }
            ?: jev?.let { JevReader.read(text, QuickAddParser.parse(text, now), it, now) }

        val duration = lang?.durationSeconds?.also { ai += "duration" } ?: KindGuess.durationSeconds(text)
        val label = when (kind) {
            // A note is kept word for word.
            Kind.NOTE -> if (language != null) lang?.title?.also { ai += "label" } ?: text.trim() else text.trim()
            // "days until Diwali": if every word was about the date, name it after the festival.
            Kind.COUNTDOWN -> task.title.takeIf { it.isNotBlank() && it != text.trim() || jev?.meaning?.festival == null }
                ?: jev?.meaning?.festival?.let(Festivals::label)?.also { ai += "label" } ?: task.title
            Kind.TIMER, Kind.STOPWATCH, Kind.FOCUS -> lang?.title?.also { ai += "label" } ?: offlineLabel(text)
            else -> task.title
        }
        val items = lang?.items?.takeIf { it.isNotEmpty() }?.also { ai += "items" } ?: KindGuess.items(text)
        return Draft(
            kind = kind,
            task = task,
            label = label.trim(),
            durationSeconds = if (kind == Kind.TIMER) duration ?: 5 * 60 else duration ?: 0,
            focusMinutes = if (kind == Kind.FOCUS) (duration?.div(60))?.coerceIn(1, 240) ?: 25 else 25,
            breakMinutes = lang?.breakMinutes?.also { ai += "break" } ?: KindGuess.breakMinutes(text) ?: 5,
            items = items,
            listName = lang?.listName?.also { ai += "listName" } ?: KindGuess.listName(text),
            perDay = lang?.perDay?.takeIf { it > 1 }?.also { ai += "perDay" } ?: KindGuess.perDay(text) ?: 1,
            countdownDate = task.due?.toLocalDate()?.takeIf { !it.isBefore(now.toLocalDate()) },
            aiDetails = ai,
        )
    }

    /** "set a 10 min timer for tea" → "Tea". */
    private fun offlineLabel(text: String): String {
        var t = text.replace(Regex("(?i)\\b(?:set|start|begin|run|a|an|the|please|timer|stop\\s?watch|stop\\s?clock|focus|pomodoro|session|mode|on|for|with|of|vai|podu)\\b"), " ")
        t = t.replace(Regex("(?i)\\d+(?:\\.\\d+)?\\s*(?:h|hr|hrs|hours?|m|min|mins|minutes?|nimisham|minit|s|sec|secs|seconds?)\\b"), " ")
        t = t.replace(Regex("(?i)\\b\\d+\\s*(?:m|min|mins|minutes?)?\\s*break\\b|\\bbreak\\b"), " ")
        return t.replace(Regex("\\s+"), " ").trim().replaceFirstChar { it.uppercase() }
    }
}
