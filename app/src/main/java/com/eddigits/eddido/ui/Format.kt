package com.eddigits.eddido.ui

import androidx.compose.ui.graphics.Color
import com.eddigits.eddido.ui.theme.DateLater
import com.eddigits.eddido.ui.theme.DateOverdue
import com.eddigits.eddido.ui.theme.DateToday
import com.eddigits.eddido.ui.theme.DateTomorrow
import com.eddigits.eddido.ui.theme.DateWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

fun dayLabel(d: LocalDate, today: LocalDate = LocalDate.now()): String = when {
    d == today -> "Today"
    d == today.plusDays(1) -> "Tomorrow"
    d == today.minusDays(1) -> "Yesterday"
    d.isAfter(today) && d.isBefore(today.plusDays(7)) -> d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    d.year == today.year -> d.format(DAY_MONTH)
    else -> d.format(DAY_MONTH_YEAR)
}

fun dueLabel(due: LocalDateTime, hasTime: Boolean, now: LocalDateTime = LocalDateTime.now()): String {
    val day = dayLabel(due.toLocalDate(), now.toLocalDate())
    return if (hasTime) "$day ${due.format(TIME)}" else day
}

fun timeLabel(t: LocalDateTime): String = t.format(TIME)

fun dueColor(due: LocalDateTime, hasTime: Boolean, now: LocalDateTime = LocalDateTime.now()): Color {
    val today = now.toLocalDate()
    val d = due.toLocalDate()
    return when {
        (hasTime && due.isBefore(now)) || d.isBefore(today) -> DateOverdue
        d == today -> DateToday
        d == today.plusDays(1) -> DateTomorrow
        d.isBefore(today.plusDays(7)) -> DateWeek
        else -> DateLater
    }
}

fun upcomingHeader(d: LocalDate, today: LocalDate = LocalDate.now()): String {
    val base = d.format(DAY_MONTH) + " · " + d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    return when (d) {
        today -> "$base · Today"
        today.plusDays(1) -> "$base · Tomorrow"
        else -> base
    }
}
