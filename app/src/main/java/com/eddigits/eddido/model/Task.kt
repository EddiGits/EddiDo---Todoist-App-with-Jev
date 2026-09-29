package com.eddigits.eddido.model

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** How a task tells you it is due. */
enum class ReminderKind { NONE, NOTIFY, ALARM }

enum class RepeatUnit { MINUTE, HOUR, DAY, WEEK, MONTH, YEAR }

/**
 * A repeat rule. [days] narrows a weekly rule to specific weekdays ("every mon and thu",
 * "every weekday"); it is ignored for the other units.
 */
data class Recurrence(
    val unit: RepeatUnit,
    val interval: Int = 1,
    val days: Set<DayOfWeek> = emptySet(),
) {
    /** The first occurrence strictly after [after]. */
    fun next(after: LocalDateTime): LocalDateTime = when (unit) {
        RepeatUnit.MINUTE -> after.plusMinutes(interval.toLong())
        RepeatUnit.HOUR -> after.plusHours(interval.toLong())
        RepeatUnit.DAY -> after.plusDays(interval.toLong())
        RepeatUnit.WEEK ->
            if (days.isEmpty()) after.plusWeeks(interval.toLong())
            else (1..7).map { after.plusDays(it.toLong()) }.first { it.dayOfWeek in days }
        RepeatUnit.MONTH -> after.plusMonths(interval.toLong())
        RepeatUnit.YEAR -> after.plusYears(interval.toLong())
    }

    fun label(): String {
        val n = if (interval == 1) "" else "$interval "
        val s = if (interval == 1) "" else "s"
        return when {
            unit == RepeatUnit.WEEK && days == WEEKDAYS -> "Every weekday"
            unit == RepeatUnit.WEEK && days == WEEKEND -> "Every weekend"
            unit == RepeatUnit.WEEK && days.isNotEmpty() ->
                "Every " + days.sorted().joinToString(", ") { it.name.take(3).lowercase().replaceFirstChar(Char::uppercase) }
            unit == RepeatUnit.DAY && interval == 1 -> "Daily"
            unit == RepeatUnit.WEEK && interval == 1 -> "Weekly"
            unit == RepeatUnit.MONTH && interval == 1 -> "Monthly"
            unit == RepeatUnit.YEAR && interval == 1 -> "Yearly"
            else -> "Every $n${unit.name.lowercase()}$s"
        }
    }

    fun toJson() = JSONObject()
        .put("unit", unit.name)
        .put("interval", interval)
        .put("days", JSONArray(days.map { it.name }))

    companion object {
        val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

        fun fromJson(o: JSONObject?): Recurrence? {
            if (o == null) return null
            val days = o.optJSONArray("days")?.let { a -> (0 until a.length()).map { DayOfWeek.valueOf(a.getString(it)) }.toSet() }
            return Recurrence(RepeatUnit.valueOf(o.getString("unit")), o.optInt("interval", 1), days ?: emptySet())
        }
    }
}

data class Task(
    val id: Long,
    val title: String,
    val description: String = "",
    /** Local date-time the task is due. When [hasTime] is false only the date part matters. */
    val due: LocalDateTime? = null,
    val hasTime: Boolean = false,
    /** 1 = highest (red) … 4 = none, as in Todoist. */
    val priority: Int = 4,
    val project: String = INBOX,
    val labels: List<String> = emptyList(),
    val recurrence: Recurrence? = null,
    val reminder: ReminderKind = ReminderKind.NONE,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** True while the AI is still refining category/labels in the background. */
    val aiPending: Boolean = false,
    /** What the user typed, kept so the AI can be re-run. */
    val sourceText: String = "",
    /** Tracked as a habit: shows in the Habits tab with a streak. */
    val habit: Boolean = false,
    /** Times per day for a habit ("drink water 8 times a day"). */
    val perDay: Int = 1,
    /** Habit check-ins: ISO date → count done that day. */
    val habitLog: Map<String, Int> = emptyMap(),
) {
    fun doneOn(date: LocalDate): Int = habitLog[date.toString()] ?: 0

    /** Days in a row, ending today (or yesterday if today isn't done yet), where the daily target was met. */
    fun streak(today: LocalDate = LocalDate.now()): Int {
        var d = if (doneOn(today) >= perDay) today else today.minusDays(1)
        var n = 0
        while (doneOn(d) >= perDay) { n++; d = d.minusDays(1) }
        return n
    }

    /** When the reminder should fire, or null if it should not. */
    fun fireAt(): LocalDateTime? = if (reminder != ReminderKind.NONE && hasTime && !completed) due else null

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("description", description)
        .put("due", due?.toString())
        .put("hasTime", hasTime)
        .put("priority", priority)
        .put("project", project)
        .put("labels", JSONArray(labels))
        .put("recurrence", recurrence?.toJson())
        .put("reminder", reminder.name)
        .put("completed", completed)
        .put("completedAt", completedAt)
        .put("createdAt", createdAt)
        .put("aiPending", aiPending)
        .put("sourceText", sourceText)
        .put("habit", habit)
        .put("perDay", perDay)
        .put("habitLog", JSONObject(habitLog))

    companion object {
        const val INBOX = "Inbox"

        fun fromJson(o: JSONObject) = Task(
            id = o.getLong("id"),
            title = o.optString("title"),
            description = o.optString("description"),
            due = o.optString("due").takeIf { it.isNotEmpty() && it != "null" }?.let(LocalDateTime::parse),
            hasTime = o.optBoolean("hasTime"),
            priority = o.optInt("priority", 4),
            project = o.optString("project", INBOX),
            labels = o.optJSONArray("labels")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            recurrence = Recurrence.fromJson(o.optJSONObject("recurrence")),
            reminder = runCatching { ReminderKind.valueOf(o.optString("reminder")) }.getOrDefault(ReminderKind.NONE),
            completed = o.optBoolean("completed"),
            completedAt = if (o.isNull("completedAt")) null else o.optLong("completedAt"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            aiPending = o.optBoolean("aiPending"),
            sourceText = o.optString("sourceText"),
            habit = o.optBoolean("habit"),
            perDay = o.optInt("perDay", 1).coerceAtLeast(1),
            habitLog = o.optJSONObject("habitLog")?.let { h -> h.keys().asSequence().associateWith { h.optInt(it) } } ?: emptyMap(),
        )
    }
}

fun LocalDateTime.toEpochMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
fun Long.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())
