package com.eddigits.eddido.parse

import com.eddigits.eddido.model.Kind

/**
 * Offline guesses for the things the AI normally decides: which tab, how long, which
 * items. Used only while the AI is thinking and when there is no internet.
 */
object KindGuess {
    private val rules: List<Pair<Kind, Regex>> = listOf(
        Kind.STOPWATCH to "stop\\s?watch|stop\\s?clock",
        Kind.TIMER to "\\btimer\\b|\\bcount\\s?down\\s+\\d+\\s*(?:s|sec|min|m|h|hour)",
        Kind.FOCUS to "\\bfocus\\b|pomodoro|deep\\s+work|study\\s+session",
        Kind.COUNTDOWN to "days?\\s+(?:until|till|to|left)|count\\s?down\\s+(?:to|till|until)|how\\s+many\\s+days",
        Kind.HABIT to "\\bhabit\\b|\\bstreak\\b|\\d+\\s+times\\s+(?:a|per)\\s+day",
        Kind.LIST to "^(?:shopping|grocery|groceries|packing|to\\s?do)?\\s*list\\b|^(?:grocery|groceries|packing)\\s*:|^buy\\s+[^,]+,",
        Kind.NOTE to "^(?:note|idea|thought)s?\\s*[:\\-]",
    ).map { (k, p) -> k to Regex("(?i)$p") }

    fun kind(text: String): Kind? = rules.firstOrNull { it.second.containsMatchIn(text) }?.first

    private val DURATION = Regex("(?i)(\\d+(?:\\.\\d+)?)\\s*(h|hr|hrs|hours?|m|min|mins|minutes?|nimisham|minit|s|sec|secs|seconds?)\\b")

    /** Total seconds of every duration in the text ("1 h 30 min" = 5400), or null. */
    fun durationSeconds(text: String): Int? {
        val total = DURATION.findAll(text).sumOf { m ->
            val n = m.groupValues[1].toDouble()
            val u = m.groupValues[2].lowercase()
            when {
                u.startsWith("h") -> n * 3600
                u.startsWith("s") -> n
                else -> n * 60
            }
        }
        return total.toInt().takeIf { it > 0 }
    }

    /** "buy milk, eggs and bread" → [Milk, Eggs, Bread], as Shapeshift's todo parser does. */
    fun items(text: String): List<String> {
        var rest = text.replace('\n', ',').replace(Regex("\\s+"), " ").trim()
        rest = rest.replace(Regex("(?i)^(?:to ?do|todo list|shopping list|grocery list|packing list|groceries|grocery|packing|list)\\s*:?\\s*"), "")
        rest = rest.replace(Regex("(?i)^(?:buy|get|pick up|grab|order)\\s+"), "")
        return rest.split(Regex("(?i)\\s*(?:,|;|\\s&\\s|\\band\\b)\\s*"))
            .map { it.trim().replace(Regex("(?i)^(?:buy|get|also)\\s+"), "").trimEnd('.', '!') }
            .filter { it.isNotEmpty() }
            .map { it.replaceFirstChar(Char::uppercase) }
    }

    fun listName(text: String): String = when {
        Regex("(?i)pack").containsMatchIn(text) -> "Packing"
        Regex("(?i)to ?do").containsMatchIn(text) -> "To-do"
        else -> "Shopping"
    }

    /** "8 times a day" → 8. */
    fun perDay(text: String): Int? =
        Regex("(?i)(\\d+)\\s+times\\s+(?:a|per)\\s+day").find(text)?.groupValues?.get(1)?.toIntOrNull()

    /** "focus 25 min with 5 min break" → 5. */
    fun breakMinutes(text: String): Int? =
        Regex("(?i)(\\d+)\\s*(?:m|min|mins|minutes?)\\s+break|break\\s+(?:of\\s+)?(\\d+)").find(text)
            ?.let { (it.groupValues[1].ifEmpty { it.groupValues[2] }).toIntOrNull() }
}
