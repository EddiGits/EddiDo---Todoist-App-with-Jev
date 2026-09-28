package com.eddigits.eddido.ai

import com.eddigits.eddido.model.Recurrence
import com.eddigits.eddido.model.ReminderKind
import java.time.LocalDateTime

/**
 * What the AI decides about a typed task. Every provider returns this same shape,
 * so the rest of the app never sees an API format.
 *
 * Fields are hints: [com.eddigits.eddido.data.TaskRepository] keeps anything the user
 * typed explicitly (#project, p1, "alarm", a parsed date) over what the AI suggests.
 */
data class AiResult(
    val title: String?,
    val project: String?,
    val labels: List<String>,
    val priority: Int?,
    val reminder: ReminderKind?,
    /** Only used when the deterministic parser found no date at all. */
    val due: LocalDateTime?,
    val hasTime: Boolean,
    val recurrence: Recurrence?,
    val provider: String,
)

/**
 * The single seam for AI. To move from OpenRouter to TypeSafe, write a
 * `TypeSafeTaskAi : TaskAi` and change the one line in [AiProvider].
 */
interface TaskAi {
    /** Returns null when the AI is unavailable; callers then keep the offline result. */
    suspend fun analyze(text: String, projects: List<String>, now: LocalDateTime): AiResult?
}

object AiProvider {
    val default: TaskAi by lazy {
        // ← Swap providers here. e.g. TypeSafeTaskAi(BuildConfig.TYPESAFE_API_KEY)
        FallbackTaskAi(OpenRouterTaskAi(), OfflineTaskAi())
    }
}

/** Tries [primary]; if it fails or is not configured, uses [fallback]. */
class FallbackTaskAi(private val primary: TaskAi, private val fallback: TaskAi) : TaskAi {
    override suspend fun analyze(text: String, projects: List<String>, now: LocalDateTime): AiResult? =
        primary.analyze(text, projects, now) ?: fallback.analyze(text, projects, now)
}
