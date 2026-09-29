package com.eddigits.eddido.data

import com.eddigits.eddido.ai.AiResult
import com.eddigits.eddido.ai.JevMeaning
import com.eddigits.eddido.model.Recurrence
import com.eddigits.eddido.model.RepeatUnit
import com.eddigits.eddido.parse.Festivals
import com.eddigits.eddido.parse.KindGuess
import com.eddigits.eddido.parse.NumberWords
import com.eddigits.eddido.parse.ParsedTask
import com.eddigits.eddido.parse.QuickAddParser
import com.eddigits.eddido.parse.SpanKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * "Jev decides, code computes": turns Jev's choices about the text's meaning (which day,
 * which part of the day, am/pm, repeat, festival, which words are scheduling words) into
 * a title, a date-time and a repeat. The result has the same shape a language model's
 * answer would, so [TaskResolver] treats it the same way.
 */
object JevReader {

    fun read(text: String, parsed: ParsedTask, jev: AiResult, now: LocalDateTime): AiResult? {
        val m = jev.meaning ?: return null
        val today = now.toLocalDate()

        // ── Date: Jev says which kind of day, code works out the date ──
        val date: LocalDate? = when (m.day) {
            "today" -> today
            "tomorrow" -> today.plusDays(1)
            "day_after" -> today.plusDays(2)
            "this_weekend" -> if (today.dayOfWeek == DayOfWeek.SUNDAY) today else today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            "next_week" -> today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            "weekday" -> (m.weekday ?: parsed.due?.dayOfWeek)?.let { today.with(TemporalAdjusters.next(it)) }
            "written_date" -> parsed.due?.toLocalDate()
            "festival" -> m.festival?.let { Festivals.next(it, today) }?.let {
                // "before Diwali" means the day before.
                if (Regex("(?i)\\bbefore\\b|\\bmunnadi\\b|\\bse\\s+pehle\\b").containsMatchIn(text)) it.minusDays(1) else it
            }
            else -> null
        }

        // ── Time: code reads the digits, Jev settles am/pm and parts of the day ──
        val time: LocalTime? = timeOf(parsed, m)

        // ── Repeat ──
        val recurrence: Recurrence? = when (m.repeat) {
            "daily" -> Recurrence(RepeatUnit.DAY)
            "weekdays" -> Recurrence(RepeatUnit.WEEK, days = Recurrence.WEEKDAYS)
            "weekly" -> Recurrence(
                RepeatUnit.WEEK,
                days = parsed.recurrence?.days?.takeIf { it.isNotEmpty() } ?: listOfNotNull(m.weekday ?: date?.dayOfWeek).toSet(),
            )
            "monthly" -> Recurrence(RepeatUnit.MONTH)
            "yearly" -> Recurrence(RepeatUnit.YEAR)
            "hourly" -> Recurrence(RepeatUnit.HOUR, everyN(text))
            "minutes" -> Recurrence(RepeatUnit.MINUTE, everyN(text))
            else -> null
        }

        // Jev had no opinion on the date at all: let the parser's reading stand.
        if (m.day == null && m.part == null && m.repeat == null) return titleOnly(text, parsed, m, jev)

        val due: LocalDateTime? = when {
            recurrence != null && (recurrence.unit == RepeatUnit.HOUR || recurrence.unit == RepeatUnit.MINUTE) && date == null && time == null ->
                recurrence.next(now)
            date != null -> date.atTime(time ?: LocalTime.MIDNIGHT)
            time != null || recurrence != null -> {
                var d = today
                if (recurrence?.unit == RepeatUnit.WEEK && recurrence.days.isNotEmpty()) {
                    d = (0..6).map { today.plusDays(it.toLong()) }.first { it.dayOfWeek in recurrence.days }
                }
                var at = d.atTime(time ?: LocalTime.MIDNIGHT)
                if (time != null && !at.isAfter(now)) at = recurrence?.next(at) ?: at.plusDays(1)
                at
            }
            else -> null
        }

        return AiResult(
            provider = "jev+code",
            title = title(text, parsed, m),
            dateAnswered = true,
            due = due,
            hasTime = due != null && (time != null || recurrence?.unit == RepeatUnit.HOUR || recurrence?.unit == RepeatUnit.MINUTE),
            recurrence = recurrence,
            // What you typed explicitly (p1, #work, @label) still counts as explicit.
            explicitProject = parsed.project,
            labels = parsed.labels,
            priority = parsed.priority,
            explicitPriority = parsed.priority != null,
            durationSeconds = durationSeconds(text, m),
        )
    }

    /**
     * Merge the chat model's answer (asked only because Jev said it needed help) with Jev's
     * own decisions: Jev still decides whether it repeats, and your explicit p1/#/@ stand.
     */
    fun withHelp(raw: AiResult, base: AiResult?, jev: AiResult): AiResult = raw.copy(
        title = raw.title?.takeIf { it.isNotBlank() } ?: base?.title,
        recurrence = if (jev.meaning?.repeat == "none") null else raw.recurrence,
        explicitProject = base?.explicitProject,
        labels = base?.labels.orEmpty(),
        priority = base?.priority,
        explicitPriority = base?.explicitPriority == true,
        durationSeconds = base?.durationSeconds,
    )

    private fun titleOnly(text: String, parsed: ParsedTask, m: JevMeaning, jev: AiResult) = AiResult(
        provider = "jev+code",
        title = title(text, parsed, m),
        explicitProject = parsed.project,
        labels = parsed.labels,
        priority = parsed.priority,
        explicitPriority = parsed.priority != null,
        durationSeconds = durationSeconds(text, m),
    ).takeIf { jev.meaning != null }

    /**
     * The title: every word Jev did not flag as a scheduling word, minus anything the parser
     * already recognised (dates, times, p1, #project, @label).
     */
    fun title(text: String, parsed: ParsedTask, m: JevMeaning): String? {
        if (m.words.isEmpty()) return null
        // Plain "wake me up at 6" / "alarm at 7" / "10 min timer": nothing but the alarm itself,
        // and the parser already names it; Jev's per-word answers wobble on "wake"/"up".
        if (parsed.title == "Wake up" || parsed.title == "Alarm" || parsed.title.startsWith("Timer ·")) return null
        val claimed = parsed.spans.filter { it.kind != SpanKind.REMINDER && it.kind != SpanKind.REPEAT }.map { it.range }
        val kept = Regex("\\S+").findAll(text).mapIndexedNotNull { i, w ->
            val inSpan = claimed.any { r -> w.range.first <= r.last && r.first <= w.range.last }
            w.value.takeUnless { i in m.schedulingWords || inSpan }
        }.joinToString(" ")
        val title = QuickAddParser.tidy(kept)
        // "wake me up at 6" leaves just "me": nothing left to name the task, so the parser's title stands.
        if (title.split(' ').all { it.lowercase() in FILLER }) return null
        return title.replaceFirstChar { it.uppercase() }
    }

    private val FILLER = setOf("", "me", "my", "i", "please", "pls", "plz", "a", "an", "the", "to", "it", "enakku", "mujhe")

    private fun timeOf(parsed: ParsedTask, m: JevMeaning): LocalTime? {
        val clock = parsed.spans.firstOrNull { it.kind == SpanKind.TIME && it.text.any(Char::isDigit) }
        if (clock != null) {
            val match = Regex("(\\d{1,2})(?:[:.](\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)?", RegexOption.IGNORE_CASE).find(clock.text)
            if (match != null) {
                var h = match.groupValues[1].toInt()
                val min = match.groupValues[2].toIntOrNull() ?: 0
                val written = match.groupValues[3].lowercase()
                val pm = when {
                    written.startsWith("p") -> true
                    written.startsWith("a") -> false
                    m.meridiem == "pm" || m.part in listOf("afternoon", "evening", "night") -> true
                    m.meridiem == "am" || m.part == "morning" -> false
                    else -> null
                }
                if (h in 1..12 && pm != null) h = (h % 12) + if (pm) 12 else 0
                else if (pm == null) return parsed.due?.takeIf { parsed.hasTime }?.toLocalTime() ?: LocalTime.of(h % 24, min)
                if (h in 0..23 && min in 0..59) return LocalTime.of(h, min)
            }
        }
        return when (m.part) {
            "morning" -> LocalTime.of(9, 0)
            "afternoon" -> LocalTime.of(14, 0)
            "evening" -> LocalTime.of(18, 0)
            "night" -> if (m.day == "today") LocalTime.of(20, 0) else LocalTime.of(21, 0)
            else -> null
        }
    }

    /** "every 2 hours" → 2. */
    private fun everyN(text: String): Int =
        Regex("(?i)every\\s+(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1

    /** Code reads the number; Jev says what unit it's in (so "10 nimisham" works). */
    private fun durationSeconds(text: String, m: JevMeaning): Int? {
        KindGuess.durationSeconds(text)?.let { return it }
        val n = Regex("(?i)\\b(${NumberWords.PATTERN})\\b").find(text)?.groupValues?.get(1)?.let(NumberWords::value) ?: return null
        return when (m.unit) {
            "seconds" -> n
            "minutes" -> n * 60
            "hours" -> n * 3600
            else -> null
        }?.toInt()?.takeIf { it > 0 }
    }
}
