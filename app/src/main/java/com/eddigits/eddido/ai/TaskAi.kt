package com.eddigits.eddido.ai

import com.eddigits.eddido.BuildConfig
import com.eddigits.eddido.model.Kind
import com.eddigits.eddido.model.Recurrence
import com.eddigits.eddido.model.ReminderKind
import java.time.LocalDateTime

/**
 * What an AI provider decides about a typed task. Every provider returns this shape,
 * so the rest of the app never sees an API format. A provider leaves out what it
 * cannot answer: Jev only chooses (project, labels, priority, reminder); the language
 * model also writes the title and works out dates and repeats.
 *
 * [com.eddigits.eddido.data.TaskResolver] turns these into the final task.
 */
data class AiResult(
    val provider: String,
    /** Clean title in the user's own words; null when this provider does not write titles. */
    val title: String? = null,
    /** True when this provider answered the date question; [due] null then means "no date". */
    val dateAnswered: Boolean = false,
    val due: LocalDateTime? = null,
    val hasTime: Boolean = false,
    val recurrence: Recurrence? = null,
    val project: String? = null,
    /** A project the user named with #name. */
    val explicitProject: String? = null,
    val labels: List<String> = emptyList(),
    /** 1 (urgent) … 4 (normal); null when this provider does not judge priority. */
    val priority: Int? = null,
    /** The user wrote p1–p4, urgent, asap or important. */
    val explicitPriority: Boolean = false,
    /** null when this provider does not decide, or is not confident enough. */
    val reminder: ReminderKind? = null,
    /** Which tab this text belongs to, with a probability per tab (Jev), or a single answer (language model). */
    val kind: Kind? = null,
    val kindProbabilities: Map<Kind, Double> = emptyMap(),
    /** Timer length, or one focus stretch. */
    val durationSeconds: Int? = null,
    val breakMinutes: Int? = null,
    /** List items, e.g. ["Milk", "Eggs"], and the list's name. */
    val items: List<String> = emptyList(),
    val listName: String? = null,
    /** Habit: times per day. */
    val perDay: Int? = null,
    /** Jev's reading of the text's meaning; code turns it into dates and a title ([JevReader]). */
    val meaning: JevMeaning? = null,
)

/**
 * What Jev chose about the text's meaning. Each field is only set when Jev was confident
 * enough; "none" answers are kept, because "no day is mentioned" is an answer too.
 */
data class JevMeaning(
    /** today, tomorrow, day_after, weekday, this_weekend, next_week, written_date, festival, none */
    val day: String? = null,
    val weekday: java.time.DayOfWeek? = null,
    /** morning, afternoon, evening, night, clock, none */
    val part: String? = null,
    /** am, pm, none */
    val meridiem: String? = null,
    /** none, daily, weekdays, weekly, monthly, yearly, hourly, minutes */
    val repeat: String? = null,
    /** seconds, minutes, hours, none */
    val unit: String? = null,
    val festival: String? = null,
    /** The words Jev was asked about, and which of them only say when/how/remind (not the task). */
    val words: List<String> = emptyList(),
    val schedulingWords: Set<Int> = emptySet(),
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
 * The one place providers are chosen, from the keys in secrets.properties.
 * - [jev]: TypeSafe Jev, ~0.4 s. Project, labels, priority, reminder.
 * - [language]: an OpenRouter chat model, ~2–5 s. Title, dates and repeats. Off by default.
 * - [offline]: keyword rules, used only when the AI cannot be reached.
 */
object AiProvider {
    val jev: TaskAi? by lazy { if (BuildConfig.TYPESAFE_API_KEY.isNotBlank()) TypeSafeTaskAi() else null }
    /** Off unless USE_OPENROUTER=true: the app runs on Jev plus its own parser, like Shapeshift. */
    val language: TaskAi? by lazy {
        if (BuildConfig.USE_OPENROUTER && BuildConfig.OPENROUTER_API_KEY.isNotBlank()) OpenRouterTaskAi() else null
    }
    val offline = OfflineTaskAi()

    val label: String
        get() = listOfNotNull(
            jev?.let { "Jev ${BuildConfig.JEV_MODEL}" },
            language?.let { "OpenRouter ${BuildConfig.OPENROUTER_MODEL}" },
        ).joinToString(" + ").ifEmpty { "offline keyword sorting (no key)" }
}
