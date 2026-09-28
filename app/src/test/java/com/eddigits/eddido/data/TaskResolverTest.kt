package com.eddigits.eddido.data

import com.eddigits.eddido.ai.AiResult
import com.eddigits.eddido.model.Recurrence
import com.eddigits.eddido.model.ReminderKind
import com.eddigits.eddido.model.RepeatUnit
import com.eddigits.eddido.parse.QuickAddParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TaskResolverTest {
    private val now = LocalDateTime.of(2026, 9, 29, 12, 40)

    private fun resolve(
        text: String,
        jev: AiResult? = null,
        language: AiResult? = null,
        manual: ManualChoices = ManualChoices(),
        fallbackToday: Boolean = false,
    ) = TaskResolver.resolve(text, QuickAddParser.parse(text, now), manual, jev, language, null, fallbackToday, now)

    private fun lang(
        title: String?, due: LocalDateTime?, hasTime: Boolean = due != null,
        recurrence: Recurrence? = null, reminder: ReminderKind? = null, project: String? = null,
        priority: Int = 4, explicitPriority: Boolean = false, explicitProject: String? = null, labels: List<String> = emptyList(),
    ) = AiResult("llm", title, true, due, hasTime, recurrence, project, explicitProject, labels, priority, explicitPriority, reminder)

    private fun jev(project: String? = null, priority: Int = 4, reminder: ReminderKind? = null, labels: List<String> = emptyList()) =
        AiResult("jev", project = project, priority = priority, reminder = reminder, labels = labels)

    @Test fun aiDecidesEverythingWhenAnswered() {
        val r = resolve(
            "Daily sunscreen use pannanum mor remind pannu",
            jev = jev(project = "Health", reminder = ReminderKind.NOTIFY),
            language = lang("Sunscreen use pannanum", LocalDateTime.of(2026, 9, 30, 9, 0), recurrence = Recurrence(RepeatUnit.DAY), labels = listOf("skincare")),
        )
        assertEquals("Sunscreen use pannanum", r.title)
        assertEquals(LocalDateTime.of(2026, 9, 30, 9, 0), r.due)
        assertEquals(RepeatUnit.DAY, r.recurrence?.unit)
        assertEquals(ReminderKind.NOTIFY, r.reminder)
        assertEquals("Health", r.project)
        assertTrue(r.aiDerived.containsAll(listOf("title", "date", "project", "reminder", "labels")))
    }

    @Test fun parserFillsInUntilAiAnswers() {
        val r = resolve("call mom tomorrow 7pm")
        assertEquals("Call mom", r.title)
        assertEquals(LocalDateTime.of(2026, 9, 30, 19, 0), r.due)
        assertTrue(r.aiDerived.isEmpty())
    }

    @Test fun aiDateBeatsParserDate() {
        val r = resolve("kal subah 7 baje gym", language = lang("Gym", LocalDateTime.of(2026, 9, 30, 7, 0)))
        assertEquals(LocalDateTime.of(2026, 9, 30, 7, 0), r.due)
        assertTrue("date" in r.aiDerived)
    }

    @Test fun modelMissingAnObviousDateKeepsParserDate() {
        val r = resolve("dentist 12 oct 4:15pm", language = lang("Dentist", null))
        assertEquals(LocalDateTime.of(2026, 10, 12, 16, 15), r.due)
        assertFalse("date" in r.aiDerived)
    }

    @Test fun pastAiDateIsRejected() {
        val r = resolve("call mom tomorrow 7pm", language = lang("Call mom", LocalDateTime.of(2026, 9, 28, 19, 0)))
        assertEquals(LocalDateTime.of(2026, 9, 30, 19, 0), r.due)
    }

    @Test fun modelFindsDateParserCannot() {
        val r = resolve(
            "renew car insurance before Diwali",
            language = lang("Renew car insurance", LocalDateTime.of(2026, 11, 7, 0, 0), hasTime = false),
        )
        assertEquals(LocalDateTime.of(2026, 11, 7, 0, 0), r.due)
        assertEquals("Renew car insurance", r.title)
    }

    @Test fun chipsBeatAi() {
        val r = resolve("buy milk", manual = ManualChoices(priority = 1, project = "Home"), jev = jev(project = "Shopping", priority = 4))
        assertEquals(1, r.priority)
        assertEquals("Home", r.project)
    }

    @Test fun typedPriorityIsNotLostWhenJevAnswersFirst() {
        assertEquals(1, resolve("pay rent p1", jev = jev(priority = 4)).priority)
    }

    @Test fun explicitHashProjectBeatsJev() {
        val r = resolve(
            "pay rent #home",
            jev = jev(project = "Finance"),
            language = lang("Pay rent", null, explicitProject = "Home", project = "Home"),
        )
        assertEquals("Home", r.project)
    }

    @Test fun jevDecidesReminderOverLanguageModel() {
        val r = resolve(
            "wake me up 6am",
            jev = jev(reminder = ReminderKind.ALARM),
            language = lang("Wake up", LocalDateTime.of(2026, 9, 30, 6, 0), reminder = ReminderKind.NOTIFY),
        )
        assertEquals(ReminderKind.ALARM, r.reminder)
    }

    @Test fun reminderWithoutTimeRingsAt9() {
        val r = resolve(
            "pay rent friday",
            jev = jev(reminder = ReminderKind.NOTIFY),
            language = lang("Pay rent", LocalDateTime.of(2026, 10, 2, 0, 0), hasTime = false),
        )
        assertEquals(LocalDateTime.of(2026, 10, 2, 9, 0), r.due)
        assertTrue(r.hasTime)
    }

    @Test fun reminderWithoutDateIsDropped() {
        val r = resolve("buy milk", jev = jev(reminder = ReminderKind.NOTIFY), language = lang("Buy milk", null))
        assertNull(r.due)
        assertEquals(ReminderKind.NONE, r.reminder)
    }

    @Test fun todayViewDefaultIsReplacedByAiDate() {
        val r = resolve(
            "Nalaki ammaku call pannu",
            fallbackToday = true,
            language = lang("Ammaku call pannu", LocalDateTime.of(2026, 9, 30, 0, 0), hasTime = false),
        )
        assertEquals(LocalDateTime.of(2026, 9, 30, 0, 0), r.due)
    }
}
