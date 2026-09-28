package com.eddigits.eddido.ai

import com.eddigits.eddido.BuildConfig
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
 * The single seam for AI. Each provider (TypeSafe, OpenRouter, offline) implements
 * this; [AiProvider] picks which one runs.
 */
interface TaskAi {
    /** Returns null when the AI is unavailable; callers then keep the offline result. */
    suspend fun analyze(text: String, projects: List<String>, now: LocalDateTime): AiResult?
}

/**
 * The one place providers are chosen. With a TypeSafe key, Jev does the choosing and
 * OpenRouter only reads dates the offline parser missed; without one, OpenRouter does both.
 * Any failure falls back to the offline keyword rules.
 */
object AiProvider {
    private val hasJev get() = BuildConfig.TYPESAFE_API_KEY.isNotBlank()

    /** Picks project, labels, priority and reminder kind. */
    val choices: TaskAi by lazy {
        if (hasJev) FallbackTaskAi(TypeSafeTaskAi(), FallbackTaskAi(OpenRouterTaskAi(), OfflineTaskAi()))
        else FallbackTaskAi(OpenRouterTaskAi(), OfflineTaskAi())
    }

    /** Reads dates the offline parser could not; null when [choices] already does it. */
    val dates: TaskAi? by lazy { if (hasJev) OpenRouterTaskAi() else null }

    val label: String
        get() = when {
            hasJev -> "TypeSafe ${BuildConfig.JEV_MODEL}" + if (BuildConfig.OPENROUTER_API_KEY.isNotBlank()) " · dates via OpenRouter" else ""
            BuildConfig.OPENROUTER_API_KEY.isNotBlank() -> "OpenRouter · ${BuildConfig.OPENROUTER_MODEL}"
            else -> "offline keyword sorting (no key)"
        }
}

/** Tries [primary]; if it fails or is not configured, uses [fallback]. */
class FallbackTaskAi(private val primary: TaskAi, private val fallback: TaskAi) : TaskAi {
    override suspend fun analyze(text: String, projects: List<String>, now: LocalDateTime): AiResult? =
        primary.analyze(text, projects, now) ?: fallback.analyze(text, projects, now)
}
