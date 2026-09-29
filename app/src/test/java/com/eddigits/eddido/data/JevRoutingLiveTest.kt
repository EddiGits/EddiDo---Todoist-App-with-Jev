package com.eddigits.eddido.data

import com.eddigits.eddido.ai.AiResult
import com.eddigits.eddido.ai.OpenRouterTaskAi
import com.eddigits.eddido.ai.TypeSafeTaskAi
import com.eddigits.eddido.model.Kind
import com.eddigits.eddido.parse.QuickAddParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Jev decides by itself whether the chat model is needed; this calls both real APIs.
 * JEV_KEY=… OPENROUTER_KEY=… ./gradlew testDebugUnitTest --tests '*JevRoutingLiveTest*'
 */
class JevRoutingLiveTest {
    private val jevKey = System.getenv("JEV_KEY").orEmpty()
    private val orKey = System.getenv("OPENROUTER_KEY").orEmpty()
    private val now = LocalDateTime.now()
    private val today = now.toLocalDate()

    private class Outcome(val routed: Boolean, val draft: Draft)

    private fun run(text: String): Outcome = runBlocking {
        val jev = TypeSafeTaskAi(apiKey = jevKey, model = "jev-latest").analyze(text, TaskRepository.DEFAULT_PROJECTS, now)!!
        val routed = jev.meaning?.needsHelp == true
        var help: AiResult? = null
        if (routed) {
            val raw = OpenRouterTaskAi(apiKey = orKey).fillTiming(text, jev, now)!!
            val base = JevReader.read(text, QuickAddParser.parse(text, now), jev, now)
            help = JevReader.withHelp(raw, base, jev)
        }
        val d = DraftBuilder.build(jev.kind ?: Kind.TASK, text, QuickAddParser.parse(text, now), ManualChoices(), jev, help, false, now)
        println("${if (routed) "ROUTED" else "jev   "} ${d.kind} | $text → '${d.task.title}' ${d.task.due} rep=${d.task.recurrence?.label()} countdown=${d.countdownDate} label='${d.label}'")
        Outcome(routed, d)
    }

    private fun keys() = assumeTrue(jevKey.isNotBlank() && orKey.isNotBlank())

    @Test fun everydayPhrasesStayWithJev() {
        keys()
        listOf("call mom tomorrow 7pm", "naalai saayangalam 6 manikku doctor", "days until Diwali", "pay rent on 5th p1", "10 nimisham timer vai")
            .forEach { assertFalse(it, run(it).routed) }
    }

    @Test fun lastFridayOfTheMonth() {
        keys()
        val o = run("pay tax on the last friday of the month")
        assertTrue(o.routed)
        var expect = today.with(TemporalAdjusters.lastInMonth(DayOfWeek.FRIDAY))
        if (expect.isBefore(today)) expect = today.plusMonths(1).with(TemporalAdjusters.lastInMonth(DayOfWeek.FRIDAY))
        assertEquals(expect, o.draft.task.due?.toLocalDate())
        assertEquals(null, o.draft.task.recurrence)
        assertEquals("Pay tax", o.draft.task.title)
    }

    @Test fun rentOnThe5thIsNotAssumedMonthly() {
        keys()
        assertEquals(null, run("pay rent on 5th p1").draft.task.recurrence)
    }

    @Test fun goodFriday() {
        keys()
        val o = run("remind me on Good Friday")
        assertTrue(o.routed)
        assertEquals(LocalDate.of(2027, 3, 26), o.draft.task.due?.toLocalDate())
    }

    @Test fun twoDaysAfterPongal() {
        keys()
        val o = run("call aunt two days after Pongal")
        assertTrue(o.routed)
        assertEquals(LocalDate.of(2027, 1, 17), o.draft.task.due?.toLocalDate())
    }

    @Test fun countdownToAFestivalNotInTheTable() {
        keys()
        val o = run("days until Ganesh Chaturthi")
        assertTrue(o.routed)
        assertEquals(Kind.COUNTDOWN, o.draft.kind)
        assertTrue(o.draft.ready)
    }
}
