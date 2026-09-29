package com.eddigits.eddido.data

import com.eddigits.eddido.ai.TypeSafeTaskAi
import com.eddigits.eddido.model.Kind
import com.eddigits.eddido.model.ReminderKind
import com.eddigits.eddido.model.RepeatUnit
import com.eddigits.eddido.parse.QuickAddParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * Calls the real Jev API through the app's own code path (Jev → JevReader → TaskResolver).
 * Runs only when JEV_KEY is set: JEV_KEY=... ./gradlew testDebugUnitTest --tests '*JevLiveTest*'
 */
class JevLiveTest {
    private val key = System.getenv("JEV_KEY").orEmpty()
    private val now = LocalDateTime.now()
    private val today = now.toLocalDate()
    private val projects = TaskRepository.DEFAULT_PROJECTS

    private fun draft(text: String): Draft = runBlocking {
        val jev = TypeSafeTaskAi(apiKey = key, model = "jev-latest").analyze(text, projects, now)!!
        val kind = jev.kind ?: Kind.TASK
        DraftBuilder.build(kind, text, QuickAddParser.parse(text, now), ManualChoices(), jev, null, false, now)
            .also { d -> println("${d.kind} | $text → '${d.task.title}' ${d.task.due} time=${d.task.hasTime} rep=${d.task.recurrence?.label()} rem=${d.task.reminder} proj=${d.task.project} label='${d.label}' dur=${d.durationSeconds} ai=${d.task.aiDerived}") }
    }

    private fun next(at: LocalTime, from: LocalDate = today): LocalDateTime =
        from.atTime(at).let { if (from == today && !it.isAfter(now)) it.plusDays(1) else it }

    @Test fun tamilEveningDoctor() {
        assumeTrue(key.isNotBlank())
        val d = draft("naalai saayangalam 6 manikku doctor")
        assertEquals("Doctor", d.task.title)
        assertEquals(today.plusDays(1).atTime(18, 0), d.task.due)
    }

    @Test fun hindiMorningGym() {
        assumeTrue(key.isNotBlank())
        val d = draft("kal subah 7 baje gym")
        assertEquals(today.plusDays(1).atTime(7, 0), d.task.due)
    }

    @Test fun dinnerAt8IsEvening() {
        assumeTrue(key.isNotBlank())
        // The parser alone reads a bare "8" as 8 AM; Jev knows dinner is in the evening.
        assertEquals(next(LocalTime.of(20, 0)), draft("dinner at 8 with priya").task.due)
    }

    @Test fun beforeDiwali() {
        assumeTrue(key.isNotBlank())
        val d = draft("renew car insurance before Diwali")
        assertEquals("Renew car insurance", d.task.title)
        assertEquals(LocalDate.of(2026, 11, 7), d.task.due?.toLocalDate())
        assertEquals(null, d.task.recurrence)
    }

    @Test fun countdownToDiwali() {
        assumeTrue(key.isNotBlank())
        val d = draft("days until Diwali")
        assertEquals(Kind.COUNTDOWN, d.kind)
        assertEquals(LocalDate.of(2026, 11, 8), d.countdownDate)
        assertEquals("Diwali", d.label)
    }

    @Test fun sunscreenDailyReminder() {
        assumeTrue(key.isNotBlank())
        val d = draft("Daily sunscreen use pannanum mor remind pannu")
        assertEquals(RepeatUnit.DAY, d.task.recurrence?.unit)
        assertEquals(ReminderKind.NOTIFY, d.task.reminder)
        assertEquals(LocalTime.of(9, 0), d.task.due?.toLocalTime())
    }

    @Test fun fridayIsOnceNotWeekly() {
        assumeTrue(key.isNotBlank())
        val d = draft("submit report friday urgent")
        assertEquals("Submit report", d.task.title)
        assertEquals(today.with(TemporalAdjusters.next(DayOfWeek.FRIDAY)), d.task.due?.toLocalDate())
        assertEquals(null, d.task.recurrence)
        assertEquals(1, d.task.priority)
    }

    @Test fun tamilTimer() {
        assumeTrue(key.isNotBlank())
        val d = draft("10 nimisham timer vai")
        assertEquals(Kind.TIMER, d.kind)
        assertEquals(600, d.durationSeconds)
    }

    @Test fun wakeUpAt6() {
        assumeTrue(key.isNotBlank())
        val d = draft("wake me up at 6")
        assertEquals(next(LocalTime.of(6, 0)), d.task.due)
        assertEquals(ReminderKind.ALARM, d.task.reminder)
    }
}
