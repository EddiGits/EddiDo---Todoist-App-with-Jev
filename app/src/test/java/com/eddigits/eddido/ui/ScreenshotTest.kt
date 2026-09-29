package com.eddigits.eddido.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.eddigits.eddido.ai.AiResult
import com.eddigits.eddido.data.DraftBuilder
import com.eddigits.eddido.data.ManualChoices
import com.eddigits.eddido.model.Kind
import com.eddigits.eddido.model.Recurrence
import com.eddigits.eddido.model.RepeatUnit
import com.eddigits.eddido.parse.QuickAddParser
import com.eddigits.eddido.ui.theme.EddiDoTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

/** Renders the creator's tab row and live preview for each tab, as they look on a phone. */
class ScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT), maxPercentDifference = 0.1)

    private val now = LocalDateTime.now()

    private fun shot(kind: Kind, text: String, ai: AiResult?, ghost: Boolean = false) {
        val draft = DraftBuilder.build(kind, text, QuickAddParser.parse(text, now), ManualChoices(), null, ai, false, now)
        paparazzi.snapshot(name = kind.key) {
            Frame { KindTabs(kind, onSelect = {}, ghost = ghost); DraftPreview(draft, text, Modifier.padding(top = 24.dp)) }
        }
    }

    @Composable
    private fun Frame(content: @Composable () -> Unit) = EddiDoTheme {
        androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(8.dp)) { content() }
        }
    }

    @Test fun task() = shot(
        Kind.TASK, "Phone mom tomorrrow morninnng",
        AiResult("llm", title = "Phone mom", dateAnswered = true, due = now.plusDays(1).withHour(9).withMinute(0), hasTime = true, project = "Personal", labels = listOf("family", "call"), kind = Kind.TASK),
    )

    @Test fun timer() = shot(Kind.TIMER, "10 min timer for tea", AiResult("llm", title = "Tea", kind = Kind.TIMER, durationSeconds = 600))
    @Test fun stopwatch() = shot(Kind.STOPWATCH, "set a stop clock", null)
    @Test fun focus() = shot(Kind.FOCUS, "focus 25 min on report", AiResult("llm", title = "Report", kind = Kind.FOCUS, durationSeconds = 1500, breakMinutes = 5))
    @Test fun habit() = shot(
        Kind.HABIT, "drink water 8 times a day",
        AiResult("llm", title = "Drink water", dateAnswered = true, due = now.plusDays(1).withHour(9), recurrence = Recurrence(RepeatUnit.DAY), kind = Kind.HABIT, perDay = 8),
        ghost = true,
    )
    @Test fun list() = shot(Kind.LIST, "buy milk, eggs and bread", AiResult("llm", kind = Kind.LIST, items = listOf("Milk", "Eggs", "Bread"), listName = "Shopping"))
    @Test fun countdown() = shot(Kind.COUNTDOWN, "days until Diwali", AiResult("llm", title = "Diwali", dateAnswered = true, due = LocalDateTime.of(2026, 11, 8, 0, 0), kind = Kind.COUNTDOWN))
    @Test fun note() = shot(Kind.NOTE, "idea: app that reads prescriptions", AiResult("llm", title = "Idea: app that reads prescriptions", kind = Kind.NOTE))
}
