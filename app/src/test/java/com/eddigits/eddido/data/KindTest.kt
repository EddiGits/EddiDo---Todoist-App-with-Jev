package com.eddigits.eddido.data

import com.eddigits.eddido.ai.AiResult
import com.eddigits.eddido.model.Kind
import com.eddigits.eddido.parse.KindGuess
import com.eddigits.eddido.parse.QuickAddParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class KindTest {
    private val start = KindDecider.State(kind = Kind.TASK, ghost = true)

    @Test fun confidentAnswerSwitchesStraightAway() {
        val s = KindDecider.decide(start, mapOf(Kind.TIMER to 0.99, Kind.TASK to 0.01), "10 min timer")
        assertEquals(Kind.TIMER, s.kind)
        assertFalse(s.ghost)
    }

    @Test fun middlingAnswerIsAGhost() {
        val s = KindDecider.decide(start, mapOf(Kind.TASK to 0.65, Kind.HABIT to 0.31), "Daily sunscreen remind pannu")
        assertEquals(Kind.TASK, s.kind)
        assertTrue(s.ghost)
        assertEquals(listOf(Kind.HABIT), s.alternatives)
    }

    @Test fun committedTabNeedsTwoWinsToChange() {
        val committed = KindDecider.decide(start, mapOf(Kind.TASK to 0.95), "call mom")
        val once = KindDecider.decide(committed, mapOf(Kind.NOTE to 0.6, Kind.TASK to 0.4), "call mom about")
        assertEquals(Kind.TASK, once.kind) // no flicker on one wobbly answer
        val twice = KindDecider.decide(once, mapOf(Kind.NOTE to 0.6, Kind.TASK to 0.4), "call mom about the")
        assertEquals(Kind.NOTE, twice.kind)
    }

    @Test fun veryConfidentChallengerWinsAtOnce() {
        val committed = KindDecider.decide(start, mapOf(Kind.TASK to 0.95), "set a")
        assertEquals(Kind.STOPWATCH, KindDecider.decide(committed, mapOf(Kind.STOPWATCH to 0.99), "set a stop clock").kind)
    }

    @Test fun tappedTabStaysUntilTextChangesALot() {
        val forced = KindDecider.force(Kind.NOTE, "buy milk")
        assertEquals(Kind.NOTE, KindDecider.decide(forced, mapOf(Kind.LIST to 0.99), "buy milks").kind)
        assertEquals(Kind.LIST, KindDecider.decide(forced, mapOf(Kind.LIST to 0.99), "buy milk, eggs, bread and butter").kind)
    }

    @Test fun offlineGuesses() {
        assertEquals(Kind.STOPWATCH, KindGuess.kind("set a stop clock"))
        assertEquals(Kind.TIMER, KindGuess.kind("10 min timer for tea"))
        assertEquals(Kind.FOCUS, KindGuess.kind("focus 25 min on report"))
        assertEquals(Kind.COUNTDOWN, KindGuess.kind("days until Diwali"))
        assertEquals(Kind.HABIT, KindGuess.kind("drink water 8 times a day"))
        assertEquals(Kind.LIST, KindGuess.kind("buy milk, eggs and bread"))
        assertEquals(Kind.NOTE, KindGuess.kind("idea: app that reads prescriptions"))
        assertNull(KindGuess.kind("call mom tomorrow 7pm"))
        assertEquals(5400, KindGuess.durationSeconds("timer 1 h 30 min"))
        assertEquals(listOf("Milk", "Eggs", "Bread"), KindGuess.items("buy milk, eggs and bread"))
        assertEquals(8, KindGuess.perDay("drink water 8 times a day"))
    }

    private val now = LocalDateTime.of(2026, 9, 29, 12, 40)
    private fun draft(kind: Kind, text: String, language: AiResult? = null) =
        DraftBuilder.build(kind, text, QuickAddParser.parse(text, now), ManualChoices(), null, language, false, now)

    @Test fun timerDraftOfflineAndWithAi() {
        val offline = draft(Kind.TIMER, "10 min timer for tea")
        assertEquals(600, offline.durationSeconds)
        assertEquals("Tea", offline.label)
        val ai = draft(Kind.TIMER, "10 nimisham timer vai", AiResult("llm", title = "", kind = Kind.TIMER, durationSeconds = 600))
        assertEquals(600, ai.durationSeconds)
        assertTrue("duration" in ai.aiDetails)
    }

    @Test fun listDraftUsesAiItems() {
        val d = draft(Kind.LIST, "packing list: charger, passport, towel", AiResult("llm", kind = Kind.LIST, items = listOf("Charger", "Passport", "Towel"), listName = "Packing"))
        assertEquals(listOf("Charger", "Passport", "Towel"), d.items)
        assertEquals("Packing", d.listName)
        assertTrue(d.ready)
    }

    @Test fun countdownNeedsADate() {
        assertFalse(draft(Kind.COUNTDOWN, "days until Diwali").ready)
        val d = draft(
            Kind.COUNTDOWN, "days until Diwali",
            AiResult("llm", title = "Diwali", dateAnswered = true, due = LocalDateTime.of(2026, 11, 8, 0, 0), kind = Kind.COUNTDOWN),
        )
        assertEquals(LocalDate.of(2026, 11, 8), d.countdownDate)
        assertEquals("Diwali", d.label)
        assertTrue(d.ready)
    }

    @Test fun languageModelDetailsForAnotherTabAreIgnored() {
        // The model thought "task", but you tapped Timer: its fields don't apply.
        val d = draft(Kind.TIMER, "tea 5 min", AiResult("llm", title = "Tea", kind = Kind.TASK, durationSeconds = 9999))
        assertEquals(300, d.durationSeconds)
    }

    @Test fun numberWordsInTimers() {
        // The phone screenshot: this showed the 5:00 default and the label "Two seconds only."
        val d = draft(Kind.TIMER, "Set a timer for two seconds only.")
        assertEquals(2, d.durationSeconds)
        assertEquals("", d.label)
        assertEquals(1500, KindGuess.durationSeconds("twenty five minutes focus"))
        assertEquals(120, KindGuess.durationSeconds("rendu nimisham timer"))
        assertEquals(600, KindGuess.durationSeconds("das minute ka timer"))
        assertEquals(1800, KindGuess.durationSeconds("half an hour"))
        assertEquals("Tea", draft(Kind.TIMER, "set a ten minute timer for tea").label)
    }
}
