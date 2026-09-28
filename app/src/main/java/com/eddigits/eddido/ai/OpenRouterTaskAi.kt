package com.eddigits.eddido.ai

import android.util.Log
import com.eddigits.eddido.BuildConfig
import com.eddigits.eddido.model.Recurrence
import com.eddigits.eddido.model.ReminderKind
import com.eddigits.eddido.model.RepeatUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * An OpenRouter chat model reads the whole task: title, date, time, repeat, reminder,
 * priority, project and labels, in English, Tamil or Hindi, typos included.
 * Everything OpenRouter-specific lives in this file.
 */
class OpenRouterTaskAi(
    private val apiKey: String = BuildConfig.OPENROUTER_API_KEY,
    private val models: List<Model> = listOf(
        // Most accurate in testing (17/17 on real phrases), 3–7 s.
        Model(BuildConfig.OPENROUTER_MODEL),
        // Backup: ~2 s with thinking off, a little less accurate.
        Model("deepseek/deepseek-v4.1-flash", fast = true),
    ).distinctBy { it.id },
) : TaskAi {

    data class Model(val id: String, val fast: Boolean = false)

    override suspend fun analyze(text: String, projects: List<String>, now: LocalDateTime): AiResult? {
        if (apiKey.isBlank()) return null
        for (model in models) {
            val result = runCatching { call(model, text, projects, now) }
                .onFailure { Log.w(TAG, "OpenRouter ${model.id} failed: ${it.message}") }
                .getOrNull()
            if (result != null) return result
        }
        return null
    }

    private suspend fun call(model: Model, text: String, projects: List<String>, now: LocalDateTime): AiResult? =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("model", model.id)
                .put("temperature", 0)
                .put("max_tokens", 1500)
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt(projects, now)))
                    .put(JSONObject().put("role", "user").put("content", text)))
            if (model.fast) {
                body.put("reasoning", JSONObject().put("enabled", false))
                body.put("response_format", JSONObject().put("type", "json_object"))
            }

            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 25_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Title", "EddiDo")
            }
            try {
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = conn.responseCode
                val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) error("HTTP $code ${raw.take(200)}")
                val content = JSONObject(raw).getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").optString("content")
                parse(content, model.id)
            } finally {
                conn.disconnect()
            }
        }

    private fun systemPrompt(projects: List<String>, now: LocalDateTime) = """
        You turn one typed to-do item into structured fields for a Todoist-like app.
        The text may mix English with Tamil or Hindi written in English letters, and may have typos.
        Common words: naalai/nalaki/nalaiki = tomorrow, inniki/indru = today, kalaila/kaalai = morning,
        madhiyam = afternoon, saayangalam = evening, rathiri = night, N manikku = at N o'clock,
        dhinamum = daily, remind pannu / nyabagam paduthu = remind me; Hindi: kal = tomorrow, aaj = today,
        parso = day after tomorrow, subah = morning, shaam = evening, raat = night, N baje = at N o'clock,
        roz = daily, yaad dilana = remind me. "mor" = morning.

        Current local date-time: ${now.truncatedTo(ChronoUnit.MINUTES)} (${now.dayOfWeek}).
        Existing projects: ${projects.joinToString(", ")}.

        Reply with ONLY one JSON object, no prose, no code fences:
        {
          "title": the task itself in the user's own words and language, with every date, time, repeat, reminder, priority, #project and @label word removed; fix obvious typos; first letter capitalised,
          "due": local date-time "YYYY-MM-DDTHH:MM", or null when no date, time or repeat is mentioned,
          "has_time": true when a time of day, a part of day or a relative time ("in 20 minutes") was given, or a reminder/alarm was asked for,
          "repeat": null or {"unit": "minute|hour|day|week|month|year", "interval": N, "days": ["MONDAY", ...]},
          "reminder": "alarm" when it should ring (alarm, wake me up, timer); "notify" when they ask to be reminded or give a time of day; otherwise "none",
          "priority": 1 urgent/asap/p1, 2 important/p2, 3 p3, 4 normal,
          "explicit_priority": true only when they wrote p1-p4, urgent, asap or important,
          "project": the best existing project, or a short new one when none fits, or "Inbox" when unsure,
          "explicit_project": the name when they wrote #name, else null,
          "labels": every word they wrote as @label (lowercase, without @), plus up to 2 helpful one-word lowercase labels
        }

        Date rules:
        - Parts of day: morning 09:00, afternoon 14:00, evening 18:00, night 21:00, tonight = today 20:00.
        - A time with no date means today if that time is still ahead, otherwise tomorrow.
        - A bare hour 1-6 with no am/pm and no part of day means pm, except for alarms and wake-ups.
        - A reminder or alarm with no time uses 09:00.
        - "every weekday" = week with MONDAY..FRIDAY. "daily" = day. The due is the next occurrence that is still ahead.
        - "N min timer" or "in N minutes" = now plus N minutes.
        - A weekday name means its next occurrence after today. "12 oct" means the next 12 October.
        - The due must never be in the past.
    """.trimIndent()

    private fun parse(content: String, model: String): AiResult? {
        val json = Regex("\\{[\\s\\S]*\\}").find(content)?.value ?: return null
        val o = JSONObject(json)
        fun str(key: String) = o.optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }

        val repeat = o.optJSONObject("repeat")?.let { r ->
            val unit = runCatching { RepeatUnit.valueOf(r.optString("unit").uppercase()) }.getOrNull() ?: return@let null
            val days = r.optJSONArray("days")?.let { a ->
                (0 until a.length()).mapNotNull { runCatching { DayOfWeek.valueOf(a.getString(it).uppercase()) }.getOrNull() }.toSet()
            } ?: emptySet()
            Recurrence(unit, r.optInt("interval", 1).coerceAtLeast(1), if (unit == RepeatUnit.WEEK) days else emptySet())
        }
        // A "due" that is not a real date counts as no answer at all, not as "no date".
        val dueText = str("due")
        val due = dueText?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        if (dueText != null && due == null) return null

        return AiResult(
            provider = model,
            title = str("title"),
            dateAnswered = true,
            due = due,
            hasTime = due != null && o.optBoolean("has_time"),
            recurrence = repeat.takeIf { due != null },
            project = str("project"),
            explicitProject = str("explicit_project")?.removePrefix("#"),
            labels = o.optJSONArray("labels")?.let { a ->
                (0 until a.length()).map { a.getString(it).lowercase().trim().removePrefix("@") }.filter { it.isNotEmpty() && ' ' !in it }
            } ?: emptyList(),
            priority = o.optInt("priority", 4).takeIf { it in 1..4 } ?: 4,
            explicitPriority = o.optBoolean("explicit_priority"),
            reminder = when (o.optString("reminder").lowercase()) {
                "alarm" -> ReminderKind.ALARM
                "notify" -> ReminderKind.NOTIFY
                "none" -> ReminderKind.NONE
                else -> null
            },
        )
    }

    companion object {
        private const val TAG = "OpenRouterTaskAi"
        private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
    }
}
