package com.eddigits.eddido.parse

import java.time.LocalDate
import java.time.MonthDay

/**
 * Festival dates, so "days until Diwali" works without a chat model: Jev picks *which*
 * festival, code looks up *when*. Lunar festivals move every year; dates are from
 * drikpanchang.com / timeanddate.com (Eid depends on the moon and can shift by a day).
 */
object Festivals {
    val names: List<Pair<String, String>> = listOf(
        "diwali" to "Diwali / Deepavali",
        "pongal" to "Pongal / Sankranti",
        "christmas" to "Christmas",
        "new_year" to "New Year",
        "holi" to "Holi",
        "eid" to "Eid al-Fitr",
        "onam" to "Onam",
    )

    private val fixed = mapOf(
        "christmas" to MonthDay.of(12, 25),
        "new_year" to MonthDay.of(1, 1),
    )

    private val lunar = mapOf(
        "diwali" to listOf("2026-11-08", "2027-10-29", "2028-10-17"),
        "pongal" to listOf("2027-01-15", "2028-01-15"),
        "holi" to listOf("2027-03-23"),
        "eid" to listOf("2027-03-10"),
        "onam" to listOf("2027-09-12"),
    ).mapValues { (_, v) -> v.map(LocalDate::parse) }

    fun label(key: String): String = names.firstOrNull { it.first == key }?.second?.substringBefore(" /") ?: key

    /** "Diwali 2026-11-08, Pongal 2027-01-15, …": the table, for the chat model's prompt. */
    fun upcoming(today: LocalDate): String =
        names.mapNotNull { (key, _) -> next(key, today)?.let { "${label(key)} $it" } }.joinToString(", ")

    /** The next date of [key] on or after [today], or null when the table has none. */
    fun next(key: String, today: LocalDate): LocalDate? {
        fixed[key]?.let { md ->
            val d = md.atYear(today.year)
            return if (d.isBefore(today)) md.atYear(today.year + 1) else d
        }
        return lunar[key]?.firstOrNull { !it.isBefore(today) }
    }
}
