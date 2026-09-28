package com.eddigits.eddido.parse

import com.eddigits.eddido.model.Recurrence
import com.eddigits.eddido.model.ReminderKind
import com.eddigits.eddido.model.RepeatUnit
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.temporal.TemporalAdjusters

enum class SpanKind { DATE, TIME, REPEAT, PRIORITY, PROJECT, LABEL, REMINDER }

data class Span(val range: IntRange, val kind: SpanKind, val text: String)

data class ParsedTask(
    val title: String,
    val due: LocalDateTime?,
    val hasTime: Boolean,
    val recurrence: Recurrence?,
    /** Explicit priority from the text (p1, "urgent"), or null if none was given. */
    val priority: Int?,
    /** Explicit #project, or null. */
    val project: String?,
    val labels: List<String>,
    /** Explicit reminder intent ("alarm", "remind me"), or null to use the default. */
    val explicitReminder: ReminderKind?,
    val reminder: ReminderKind,
    val spans: List<Span>,
)

/**
 * Reads dates, times, repeats, priority, #project and @labels out of free text,
 * Todoist quick-add style. Pure code, no network: this is the "code computes" half.
 * The AI only decides category/labels on top of it (see [com.eddigits.eddido.ai.TaskAi]).
 */
object QuickAddParser {
    private const val I = "(?i)"
    private const val WD_FULL = "monday|tuesday|wednesday|thursday|friday|saturday|sunday"
    private const val WD_ANY = "mon(?:day)?|tue(?:s(?:day)?)?|wed(?:nesday)?|thu(?:r(?:s(?:day)?)?)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?"
    private const val MONTHS = "jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?"
    private const val NUM = "\\d+|an?|one|two|three|four|five|six|seven|eight|nine|ten|fifteen|twenty|thirty|forty five|half an?"
    private const val UNIT = "seconds?|secs?|minutes?|mins?|m|hours?|hrs?|h|days?|weeks?|months?"

    private class Ctx(val text: String) {
        val claimed = BooleanArray(text.length)
        val spans = mutableListOf<Span>()

        fun find(pattern: String, kind: SpanKind): MatchResult? {
            var m = Regex(I + pattern).find(text)
            while (m != null) {
                if (m.range.none { claimed[it] }) {
                    m.range.forEach { claimed[it] = true }
                    spans += Span(m.range, kind, m.value.trim())
                    return m
                }
                m = m.next()
            }
            return null
        }

        fun findAll(pattern: String, kind: SpanKind): List<MatchResult> = generateSequence { find(pattern, kind) }.toList()

        /** The text with every claimed phrase blanked out. */
        fun remainder(): String = buildString { text.forEachIndexed { i, ch -> append(if (claimed[i]) ' ' else ch) } }
    }

    fun parse(input: String, now: LocalDateTime = LocalDateTime.now()): ParsedTask {
        val c = Ctx(input)
        val today = now.toLocalDate()

        // #project and @labels
        val project = c.find("(?<![\\w&])#([\\p{L}\\d_-]+)", SpanKind.PROJECT)?.groupValues?.get(1)
            ?.replaceFirstChar { it.uppercase() }
        val labels = c.findAll("(?<![\\w.])@([\\p{L}\\d_-]+)", SpanKind.LABEL).map { it.groupValues[1].lowercase() }

        // Priority
        var priority: Int? = c.find("\\bp([1-4])\\b", SpanKind.PRIORITY)?.groupValues?.get(1)?.toInt()
        if (priority == null && c.find("\\b(?:urgent(?:ly)?|asap|critical|top priority)\\b|!!!", SpanKind.PRIORITY) != null) priority = 1
        if (priority == null && c.find("\\b(?:important|high priority)\\b|!!", SpanKind.PRIORITY) != null) priority = 2
        if (priority == null && c.find("\\b(?:medium priority)\\b", SpanKind.PRIORITY) != null) priority = 3
        if (priority == null && c.find("\\b(?:low priority)\\b", SpanKind.PRIORITY) != null) priority = 4

        // Timers: "10 min timer", "timer for 25 minutes"
        var titleFallback: String? = null
        var explicitReminder: ReminderKind? = null
        var due: LocalDateTime? = null
        var hasTime = false
        val timer = c.find("\\b(?:set\\s+(?:a\\s+)?)?(?:timer\\s+(?:for\\s+)?($NUM)\\s*($UNIT)|($NUM)\\s*($UNIT)\\s+timer)\\b", SpanKind.REMINDER)
        if (timer != null) {
            val g = timer.groupValues
            val (n, u) = if (g[1].isNotEmpty()) g[1] to g[2] else g[3] to g[4]
            due = addDuration(now, words(n), u)
            hasTime = true
            explicitReminder = ReminderKind.ALARM
            titleFallback = "Timer · ${formatDuration(words(n), u)}"
        }

        // Alarm / reminder intent words
        var wake = false
        if (explicitReminder == null) {
            val alarm = c.find("\\b(?:set\\s+(?:an?\\s+)?alarm|alarm|(wake\\s+me(?:\\s+up)?|wake\\s+up))\\b", SpanKind.REMINDER)
            if (alarm != null) {
                explicitReminder = ReminderKind.ALARM
                wake = alarm.groupValues[1].isNotEmpty()
                titleFallback = if (wake) "Wake up" else "Alarm"
            }
        }
        if (explicitReminder == null &&
            c.find("\\b(?:please\\s+)?(?:remind\\s+me(?:\\s+(?:to|about|of))?|reminder(?:\\s+(?:to|for))?:?|don'?t\\s+forget(?:\\s+to)?|remember\\s+to)\\b", SpanKind.REMINDER) != null
        ) explicitReminder = ReminderKind.NOTIFY
        if (c.find("\\b(?:no\\s+reminder|silently)\\b", SpanKind.REMINDER) != null) explicitReminder = ReminderKind.NONE

        // Repeats
        var recurrence: Recurrence? = null
        var partOfDay: LocalTime? = null
        run {
            c.find("\\b(?:every|each)\\s+(?:week\\s?days?|work\\s?days?)\\b", SpanKind.REPEAT)?.let { recurrence = Recurrence(RepeatUnit.WEEK, days = Recurrence.WEEKDAYS); return@run }
            c.find("\\b(?:every|each)\\s+weekends?\\b", SpanKind.REPEAT)?.let { recurrence = Recurrence(RepeatUnit.WEEK, days = Recurrence.WEEKEND); return@run }
            c.find("\\b(?:every|each)\\s+((?:$WD_ANY)(?:\\s*(?:,|and|&)?\\s*(?:$WD_ANY)\\b)*)", SpanKind.REPEAT)?.let { m ->
                val days = Regex("(?i)$WD_ANY").findAll(m.groupValues[1]).mapNotNull { dayOf(it.value) }.toSet()
                recurrence = Recurrence(RepeatUnit.WEEK, days = days); return@run
            }
            c.find("\\b(?:every|each)\\s+(morning|afternoon|evening|night)\\b", SpanKind.REPEAT)?.let { m ->
                recurrence = Recurrence(RepeatUnit.DAY); partOfDay = partOfDayTime(m.groupValues[1]); return@run
            }
            c.find("\\b(?:every|each)\\s+(other\\s+|\\d+\\s+)?(min(?:ute)?|hour|hr|day|week|month|year)s?\\b", SpanKind.REPEAT)?.let { m ->
                val n = m.groupValues[1].trim().let { if (it == "other") 2 else it.toIntOrNull() ?: 1 }
                recurrence = Recurrence(unitOf(m.groupValues[2]), n); return@run
            }
            c.find("\\b(?:every\\s*day|daily|each\\s+day)\\b", SpanKind.REPEAT)?.let { recurrence = Recurrence(RepeatUnit.DAY); return@run }
            c.find("\\bhourly\\b", SpanKind.REPEAT)?.let { recurrence = Recurrence(RepeatUnit.HOUR); return@run }
            c.find("\\bweekly\\b", SpanKind.REPEAT)?.let { recurrence = Recurrence(RepeatUnit.WEEK); return@run }
            c.find("\\bmonthly\\b", SpanKind.REPEAT)?.let { recurrence = Recurrence(RepeatUnit.MONTH); return@run }
            c.find("\\b(?:yearly|annually)\\b", SpanKind.REPEAT)?.let { recurrence = Recurrence(RepeatUnit.YEAR); return@run }
        }

        // Relative: "in 20 minutes", "after 2 hours", "in 3 days"
        var date: LocalDate? = null
        if (due == null) {
            c.find("\\b(?:in|after)\\s+($NUM)\\s*($UNIT)\\b", SpanKind.DATE)?.let { m ->
                val u = m.groupValues[2].lowercase()
                val at = addDuration(now, words(m.groupValues[1]), u)
                if (u.startsWith("s") || u.startsWith("m") && !u.startsWith("mo") || u.startsWith("h")) {
                    due = at; hasTime = true
                } else date = at.toLocalDate()
            }
        }

        // Absolute dates
        if (due == null && date == null) date = findDate(c, today)

        // Times
        var time: LocalTime? = null
        if (due == null) {
            time = findTime(c, preferMorning = wake || explicitReminder == ReminderKind.ALARM)
            if (time == null && c.find("\\btonight\\b", SpanKind.DATE) != null) {
                date = date ?: today; time = LocalTime.of(20, 0)
            }
            if (time == null) time = partOfDay
        }

        // Resolve into one due date-time
        if (due == null) {
            val rec = recurrence
            when {
                rec != null && (rec.unit == RepeatUnit.MINUTE || rec.unit == RepeatUnit.HOUR) && date == null && time == null -> {
                    due = rec.next(now); hasTime = true
                }
                date != null || time != null || rec != null -> {
                    var d = date ?: today
                    if (rec?.unit == RepeatUnit.WEEK && rec.days.isNotEmpty() && date == null) {
                        d = (0..6).map { today.plusDays(it.toLong()) }.first { it.dayOfWeek in rec.days }
                    }
                    // A reminder or alarm needs a time; default to 9 AM like Todoist.
                    val t = time ?: if (explicitReminder == ReminderKind.NOTIFY || explicitReminder == ReminderKind.ALARM) LocalTime.of(9, 0) else null
                    hasTime = t != null
                    var at = d.atTime(t ?: LocalTime.MIDNIGHT)
                    if (date == null && hasTime && !at.isAfter(now)) at = rec?.next(at) ?: at.plusDays(1)
                    if (rec?.unit == RepeatUnit.WEEK && rec.days.isNotEmpty() && at.dayOfWeek !in rec.days) at = rec.next(at)
                    due = at
                }
            }
        }

        val reminder = explicitReminder ?: if (hasTime) ReminderKind.NOTIFY else ReminderKind.NONE
        val title = tidy(c.remainder()).ifEmpty { titleFallback ?: "" }.replaceFirstChar { it.uppercase() }

        return ParsedTask(
            title = title,
            due = due,
            hasTime = hasTime,
            recurrence = recurrence,
            priority = priority,
            project = project,
            labels = labels,
            explicitReminder = explicitReminder,
            reminder = reminder,
            spans = c.spans.sortedBy { it.range.first },
        )
    }

    private fun findDate(c: Ctx, today: LocalDate): LocalDate? {
        c.find("\\b(?:day\\s+after\\s+tomorrow|overmorrow)\\b", SpanKind.DATE)?.let { return today.plusDays(2) }
        c.find("\\b(?:tomorrow|tmrw|tmr|tomorow|tommorow|tommorrow)\\b", SpanKind.DATE)?.let { return today.plusDays(1) }
        c.find("\\btoday\\b", SpanKind.DATE)?.let { return today }
        c.find("\\b(?:this\\s+)?weekend\\b", SpanKind.DATE)?.let {
            return if (today.dayOfWeek == DayOfWeek.SUNDAY) today else today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
        }
        c.find("\\bnext\\s+week\\b", SpanKind.DATE)?.let { return today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) }
        c.find("\\bnext\\s+month\\b", SpanKind.DATE)?.let { return today.plusMonths(1).withDayOfMonth(1) }
        c.find("\\bend\\s+of\\s+(?:the\\s+)?month\\b", SpanKind.DATE)?.let { return today.with(TemporalAdjusters.lastDayOfMonth()) }
        c.find("\\b(?:(next|this|coming|on)\\s+)?($WD_FULL)\\b|\\b(?:next|this|coming|on)\\s+($WD_ANY)\\b", SpanKind.DATE)?.let { m ->
            val day = dayOf(m.groupValues[2].ifEmpty { m.groupValues[3] }) ?: return@let
            var d = today.with(TemporalAdjusters.next(day))
            // "next friday" said on a Monday means the Friday after this one.
            if (m.groupValues[1].equals("next", true) && d.isBefore(today.plusDays(7)) && today.dayOfWeek < day) d = d.plusWeeks(1)
            return d
        }
        c.find("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?($MONTHS)\\b(?:,?\\s+(\\d{4}))?", SpanKind.DATE)?.let { m ->
            return makeDate(today, m.groupValues[1].toInt(), monthOf(m.groupValues[2]), m.groupValues[3].toIntOrNull())
        }
        c.find("\\b($MONTHS)\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b(?:,?\\s+(\\d{4}))?", SpanKind.DATE)?.let { m ->
            return makeDate(today, m.groupValues[2].toInt(), monthOf(m.groupValues[1]), m.groupValues[3].toIntOrNull())
        }
        // Day first, as written in India and the UK: 25/12, 25-12-2026
        c.find("\\b(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?\\b", SpanKind.DATE)?.let { m ->
            val month = m.groupValues[2].toInt()
            if (month in 1..12) {
                val y = m.groupValues[3].toIntOrNull()?.let { if (it < 100) 2000 + it else it }
                return makeDate(today, m.groupValues[1].toInt(), Month.of(month), y)
            }
        }
        c.find("\\b(?:on\\s+)?(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)\\b(?!\\s+(?:of\\s+)?(?:$MONTHS))", SpanKind.DATE)?.let { m ->
            val day = m.groupValues[1].toInt()
            var d = runCatching { today.withDayOfMonth(day) }.getOrNull() ?: return null
            if (d.isBefore(today)) d = runCatching { today.plusMonths(1).withDayOfMonth(day) }.getOrNull() ?: return null
            return d
        }
        return null
    }

    private fun findTime(c: Ctx, preferMorning: Boolean): LocalTime? {
        c.find("(?:\\b(?:at|by|@)\\s*)?\\b(\\d{1,2})(?:[:.](\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)(?![a-z])", SpanKind.TIME)?.let { m ->
            var h = m.groupValues[1].toInt() % 12
            if (m.groupValues[3].lowercase().startsWith("p")) h += 12
            val min = m.groupValues[2].toIntOrNull() ?: 0
            if (h in 0..23 && min in 0..59) return LocalTime.of(h, min)
        }
        c.find("(?:\\b(?:at|by)\\s+)?\\b([01]?\\d|2[0-3])[:.]([0-5]\\d)\\b(?!\\s*(?:%|/))", SpanKind.TIME)?.let { m ->
            val h = m.groupValues[1].toInt()
            val t = LocalTime.of(h, m.groupValues[2].toInt())
            // "at 7:30" without am/pm: assume the next sensible one.
            return if (h in 1..6 && !preferMorning) t.plusHours(12) else t
        }
        c.find("\\b(?:at|by${if (preferMorning) "|for" else ""})\\s+(\\d{1,2})\\b(?!\\s*(?:%|/|-|min|mins|minutes?|hours?|hrs?|days?|weeks?|months?|years?|st|nd|rd|th))", SpanKind.TIME)?.let { m ->
            val h = m.groupValues[1].toInt()
            if (h in 0..23) return LocalTime.of(if (h in 1..6 && !preferMorning) h + 12 else h, 0)
        }
        c.find("\\b(?:at\\s+)?(noon|midday|midnight)\\b", SpanKind.TIME)?.let { m ->
            return if (m.groupValues[1].lowercase() == "midnight") LocalTime.of(23, 59) else LocalTime.NOON
        }
        c.find("\\b(?:in\\s+the\\s+|this\\s+|at\\s+)?(morning|afternoon|evening|night)\\b", SpanKind.TIME)?.let { m ->
            return partOfDayTime(m.groupValues[1])
        }
        return null
    }

    private fun partOfDayTime(p: String): LocalTime = when (p.lowercase()) {
        "morning" -> LocalTime.of(9, 0)
        "afternoon" -> LocalTime.of(14, 0)
        "evening" -> LocalTime.of(18, 0)
        else -> LocalTime.of(21, 0)
    }

    private fun makeDate(today: LocalDate, day: Int, month: Month?, year: Int?): LocalDate? {
        month ?: return null
        val y = year ?: today.year
        val d = runCatching { LocalDate.of(y, month, day) }.getOrNull() ?: return null
        return if (year == null && d.isBefore(today)) d.plusYears(1) else d
    }

    private fun dayOf(s: String): DayOfWeek? {
        val k = s.lowercase().take(3)
        return DayOfWeek.entries.firstOrNull { it.name.lowercase().startsWith(k) }
    }

    private fun monthOf(s: String): Month? {
        val k = s.lowercase().take(3)
        return Month.entries.firstOrNull { it.name.lowercase().startsWith(k) }
    }

    private fun unitOf(s: String): RepeatUnit = when (s.lowercase()) {
        "min", "minute" -> RepeatUnit.MINUTE
        "hour", "hr" -> RepeatUnit.HOUR
        "week" -> RepeatUnit.WEEK
        "month" -> RepeatUnit.MONTH
        "year" -> RepeatUnit.YEAR
        else -> RepeatUnit.DAY
    }

    private fun words(n: String): Double = when (n.lowercase().trim()) {
        "a", "an", "one" -> 1.0
        "half a", "half an" -> 0.5
        "two" -> 2.0; "three" -> 3.0; "four" -> 4.0; "five" -> 5.0; "six" -> 6.0
        "seven" -> 7.0; "eight" -> 8.0; "nine" -> 9.0; "ten" -> 10.0
        "fifteen" -> 15.0; "twenty" -> 20.0; "thirty" -> 30.0; "forty five" -> 45.0
        else -> n.toDoubleOrNull() ?: 1.0
    }

    private fun seconds(n: Double, unit: String): Long {
        val u = unit.lowercase()
        val mult = when {
            u.startsWith("s") -> 1L
            u.startsWith("mo") -> 30L * 86400
            u.startsWith("m") -> 60L
            u.startsWith("h") -> 3600L
            u.startsWith("d") -> 86400L
            u.startsWith("w") -> 7L * 86400
            else -> 60L
        }
        return (n * mult).toLong()
    }

    private fun addDuration(now: LocalDateTime, n: Double, unit: String): LocalDateTime {
        val u = unit.lowercase()
        return if (u.startsWith("mo")) now.plusMonths(n.toLong()) else now.plusSeconds(seconds(n, unit))
    }

    private fun formatDuration(n: Double, unit: String): String {
        val s = seconds(n, unit)
        return when {
            s % 3600 == 0L -> "${s / 3600} h"
            s >= 60 -> "${s / 60} min"
            else -> "$s s"
        }
    }

    /** Collapse whitespace and drop connector words stranded by removing a phrase. */
    fun tidy(s: String): String {
        var out = s.replace(Regex("\\s+"), " ").trim().trim(',', ';', ':', '-', '.').trim()
        val dangling = Regex("(?i)\\s+(on|at|by|for|with|to|in|and|the|this|next|from|every|of|@)$")
        val leading = Regex("(?i)^(on|at|by|for|and|the|to|that|i\\s+need\\s+to|i\\s+have\\s+to)\\s+")
        repeat(4) {
            val next = out.replace(dangling, "").replace(leading, "").trim().trim(',', ';', ':', '-').trim()
            if (next == out) return out
            out = next
        }
        return out
    }
}
