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

/**
 * Asks an OpenRouter chat model to categorise a task and reply in JSON.
 * Everything OpenRouter-specific lives in this file.
 */
class OpenRouterTaskAi(
    private val apiKey: String = BuildConfig.OPENROUTER_API_KEY,
    private val models: List<String> = listOf(BuildConfig.OPENROUTER_MODEL, "google/gemma-4-26b-a4b-it:free").distinct(),
) : TaskAi {

    override suspend fun analyze(text: String, projects: List<String>, now: LocalDateTime): AiResult? {
        if (apiKey.isBlank()) return null
        for (model in models) {
            val result = runCatching { call(model, text, projects, now) }
                .onFailure { Log.w(TAG, "OpenRouter $model failed: ${it.message}") }
                .getOrNull()
            if (result != null) return result
        }
        return null
    }

    private suspend fun call(model: String, text: String, projects: List<String>, now: LocalDateTime): AiResult? =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("model", model)
                .put("temperature", 0)
                .put("max_tokens", 800)
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt(projects, now)))
                    .put(JSONObject().put("role", "user").put("content", text)))

            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 20_000
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
                parse(content, model)
            } finally {
                conn.disconnect()
            }
        }

    private fun systemPrompt(projects: List<String>, now: LocalDateTime) = """
        You organise to-do items for a Todoist-like app. The user types one task in natural language
        (English, possibly mixed with Tamil or Hindi words). Current local date-time: $now (${now.dayOfWeek}).
        Existing projects: ${projects.joinToString(", ")}.

        Reply with ONLY a JSON object, no prose, no code fences:
        {
          "title": short clean task title without date/time/repeat words, e.g. "Call mom",
          "project": the best existing project name, or a new short one if none fits; "Inbox" if unsure,
          "labels": 0-3 lowercase one-word labels, e.g. ["errand","family"],
          "priority": 1 (urgent) to 4 (normal),
          "reminder": "alarm" if they want an alarm/wake-up/ringing, "notify" for a reminder, else "none",
          "due": ISO local date-time "YYYY-MM-DDTHH:MM" if the text mentions when, else null,
          "has_time": true if a specific time of day was given,
          "repeat": null or {"unit": "minute|hour|day|week|month|year", "interval": 1, "days": ["MONDAY", ...]}
        }
    """.trimIndent()

    private fun parse(content: String, model: String): AiResult? {
        val json = Regex("\\{[\\s\\S]*\\}").find(content)?.value ?: return null
        val o = JSONObject(json)
        val repeat = o.optJSONObject("repeat")?.let { r ->
            val unit = runCatching { RepeatUnit.valueOf(r.optString("unit").uppercase()) }.getOrNull() ?: return@let null
            val days = r.optJSONArray("days")?.let { a ->
                (0 until a.length()).mapNotNull { runCatching { DayOfWeek.valueOf(a.getString(it).uppercase()) }.getOrNull() }.toSet()
            } ?: emptySet()
            Recurrence(unit, r.optInt("interval", 1).coerceAtLeast(1), days)
        }
        return AiResult(
            title = o.optString("title").takeIf { it.isNotBlank() && it != "null" },
            project = o.optString("project").takeIf { it.isNotBlank() && it != "null" },
            labels = o.optJSONArray("labels")?.let { a -> (0 until a.length()).map { a.getString(it).lowercase().trim() }.filter { it.isNotEmpty() } }
                ?: emptyList(),
            priority = o.optInt("priority", 0).takeIf { it in 1..4 },
            reminder = when (o.optString("reminder").lowercase()) {
                "alarm" -> ReminderKind.ALARM
                "notify" -> ReminderKind.NOTIFY
                "none" -> ReminderKind.NONE
                else -> null
            },
            due = o.optString("due").takeIf { it.isNotBlank() && it != "null" }?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
            hasTime = o.optBoolean("has_time"),
            recurrence = repeat,
            provider = model,
        )
    }

    companion object {
        private const val TAG = "OpenRouterTaskAi"
        private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
    }
}
