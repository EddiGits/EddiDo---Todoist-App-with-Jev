package com.eddigits.eddido.parse

import com.eddigits.eddido.model.Recurrence
import com.eddigits.eddido.model.ReminderKind
import com.eddigits.eddido.model.RepeatUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class QuickAddParserTest {
    // Monday 28 Sep 2026, 10:00
    private val now = LocalDateTime.of(2026, 9, 28, 10, 0)
    private fun p(s: String) = QuickAddParser.parse(s, now)

    @Test fun tomorrowWithTime() {
        val r = p("call mom tomorrow 7pm")
        assertEquals("Call mom", r.title)
        assertEquals(LocalDateTime.of(2026, 9, 29, 19, 0), r.due)
        assertTrue(r.hasTime)
        assertEquals(ReminderKind.NOTIFY, r.reminder)
    }

    @Test fun wakeUpEveryWeekday() {
        val r = p("wake me up every weekday 6am")
        assertEquals("Wake up", r.title)
        assertEquals(ReminderKind.ALARM, r.reminder)
        assertEquals(Recurrence(RepeatUnit.WEEK, days = Recurrence.WEEKDAYS), r.recurrence)
        assertEquals(LocalDateTime.of(2026, 9, 29, 6, 0), r.due) // today's 6am has passed
    }

    @Test fun alarmAt6IsMorning() {
        assertEquals(LocalDateTime.of(2026, 9, 29, 6, 0), p("alarm at 6").due)
        val r = p("set an alarm for 6")
        assertEquals("Alarm", r.title)
        assertEquals(LocalDateTime.of(2026, 9, 29, 6, 0), r.due)
        assertEquals(ReminderKind.ALARM, r.reminder)
    }

    @Test fun alarmTomorrowWithLabel() {
        val r = p("alarm for 7:15am tomorrow for flight")
        assertEquals("Flight", r.title)
        assertEquals(LocalDateTime.of(2026, 9, 29, 7, 15), r.due)
    }

    @Test fun dayMonthSlash() {
        assertEquals(LocalDateTime.of(2026, 12, 25, 0, 0), p("xmas gifts 25/12").due)
    }

    @Test fun timer() {
        val r = p("10 min timer")
        assertEquals(now.plusMinutes(10), r.due)
        assertEquals(ReminderKind.ALARM, r.reminder)
        assertEquals("Timer · 10 min", r.title)
    }

    @Test fun relativeMinutes() {
        val r = p("remind me to check oven in 20 minutes")
        assertEquals("Check oven", r.title)
        assertEquals(now.plusMinutes(20), r.due)
        assertEquals(ReminderKind.NOTIFY, r.reminder)
    }

    @Test fun priorityProjectLabels() {
        val r = p("pay rent on 5th p1 #finance @home")
        assertEquals("Pay rent", r.title)
        assertEquals(1, r.priority)
        assertEquals("Finance", r.project)
        assertEquals(listOf("home"), r.labels)
        assertEquals(LocalDateTime.of(2026, 10, 5, 0, 0), r.due)
        assertFalse(r.hasTime)
    }

    @Test fun specificDays() {
        val r = p("gym every mon, wed and fri 6:30am")
        assertEquals("Gym", r.title)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), r.recurrence?.days)
        assertEquals(LocalDateTime.of(2026, 9, 30, 6, 30), r.due)
    }

    @Test fun dailyPill() {
        val r = p("take vitamin daily at 9pm")
        assertEquals("Take vitamin", r.title)
        assertEquals(RepeatUnit.DAY, r.recurrence?.unit)
        assertEquals(LocalDateTime.of(2026, 9, 28, 21, 0), r.due)
    }

    @Test fun monthDate() {
        val r = p("dentist 12 oct 4:15pm")
        assertEquals("Dentist", r.title)
        assertEquals(LocalDateTime.of(2026, 10, 12, 16, 15), r.due)
    }

    @Test fun weekdayName() {
        val r = p("submit report friday urgent")
        assertEquals("Submit report", r.title)
        assertEquals(1, r.priority)
        assertEquals(LocalDateTime.of(2026, 10, 2, 0, 0), r.due)
    }

    @Test fun plainTextHasNoDate() {
        val r = p("buy milk, eggs and bread")
        assertEquals("Buy milk, eggs and bread", r.title)
        assertNull(r.due)
        assertEquals(ReminderKind.NONE, r.reminder)
    }

    @Test fun reminderWithoutTimeDefaultsTo9() {
        val r = p("remind me to renew passport next week")
        assertEquals(LocalDateTime.of(2026, 10, 5, 9, 0), r.due)
    }

    @Test fun tonight() {
        val r = p("movie tonight")
        assertEquals("Movie", r.title)
        assertEquals(LocalDateTime.of(2026, 9, 28, 20, 0), r.due)
    }

    @Test fun everyTwoHours() {
        val r = p("drink water every 2 hours")
        assertEquals("Drink water", r.title)
        assertEquals(Recurrence(RepeatUnit.HOUR, 2), r.recurrence)
        assertEquals(now.plusHours(2), r.due)
    }

    // Typos and Tamil/Hindi, as typed on the phone
    @Test fun typos() {
        val r = p("Phone mom tomorrrow morninnng")
        assertEquals("Phone mom", r.title)
        assertEquals(LocalDateTime.of(2026, 9, 29, 9, 0), r.due)
    }

    @Test fun tamilTomorrow() {
        val r = p("Nalaki ammaku call pannu")
        assertEquals("Ammaku call pannu", r.title)
        assertEquals(LocalDateTime.of(2026, 9, 29, 0, 0), r.due)
    }

    @Test fun tamilEveningTime() {
        val r = p("naalai saayangalam 6 manikku doctor")
        assertEquals("Doctor", r.title)
        assertEquals(LocalDateTime.of(2026, 9, 29, 18, 0), r.due)
    }

    @Test fun hindiMorning() {
        val r = p("kal subah 7 baje gym jaana hai")
        assertEquals(LocalDateTime.of(2026, 9, 29, 7, 0), r.due)
        assertTrue(r.hasTime)
    }

    @Test fun eveningHintMakesItPm() {
        assertEquals(LocalDateTime.of(2026, 9, 29, 19, 30), p("dinner tomorrow evening 7:30").due)
    }

    @Test fun englishWordsNotMistaken() {
        val r = p("buy rat trap and kale")
        assertNull(r.due)
        assertEquals("Buy rat trap and kale", r.title)
    }

    @Test fun tanglishDailyReminder() {
        // Tuesday 12:33, like the screenshot: today's 9 AM has passed, so it starts tomorrow.
        val r = QuickAddParser.parse("Daily sunscreen use pannanum mor remind pannu", LocalDateTime.of(2026, 9, 29, 12, 33))
        assertEquals("Sunscreen use pannanum", r.title)
        assertEquals(RepeatUnit.DAY, r.recurrence?.unit)
        assertEquals(ReminderKind.NOTIFY, r.reminder)
        assertEquals(LocalDateTime.of(2026, 9, 30, 9, 0), r.due)
    }

    @Test fun hindiReminder() {
        val r = p("kal shaam 6 baje dawai yaad dilana")
        assertEquals(ReminderKind.NOTIFY, r.reminder)
        assertEquals(LocalDateTime.of(2026, 9, 29, 18, 0), r.due)
    }
}
