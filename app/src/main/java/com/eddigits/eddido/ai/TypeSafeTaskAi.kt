package com.eddigits.eddido.ai

import android.util.Log
import com.eddigits.eddido.BuildConfig
import com.eddigits.eddido.model.ReminderKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime

/**
 * TypeSafe's Jev (System One): one call answers every question below in parallel,
 * in a few hundred milliseconds. Jev only *chooses*; it never writes text or does
 * date math, so title/due stay with [com.eddigits.eddido.parse.QuickAddParser].
 * Everything TypeSafe-specific lives in this file.
 */
class TypeSafeTaskAi(
    private val apiKey: String = BuildConfig.TYPESAFE_API_KEY,
    private val model: String = BuildConfig.JEV_MODEL,
) : TaskAi {

    override suspend fun analyze(text: String, projects: List<String>, now: LocalDateTime): AiResult? {
        if (apiKey.isBlank()) return null
        return runCatching { call(text, projects) }
            .onFailure { Log.w(TAG, "Jev failed: ${it.message}") }
            .getOrNull()
    }

    private suspend fun call(text: String, projects: List<String>): AiResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("model", model)
            .put("state", JSONObject().put("text", text))
            .put("questions", questions(projects))

        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 8_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("HTTP $code ${raw.take(200)}")
            parse(JSONObject(raw).getJSONObject("answers"))
        } finally {
            conn.disconnect()
        }
    }

    private fun questions(projects: List<String>): JSONObject {
        val projectCriteria = JSONObject()
        projects.forEach { p -> projectCriteria.put(p, PROJECT_HINTS[p] ?: JSONObject.NULL) }
        if (!projects.contains("Inbox")) projectCriteria.put("Inbox", PROJECT_HINTS["Inbox"])

        val q = JSONObject()
            .put("project", choice("Which list does this to-do item belong in", projectCriteria))
            .put(
                "priority",
                JSONObject().put("type", "score").put("instructions", "How urgent is this task")
                    .put("criteria", JSONArray(listOf("Normal, no urgency", "Somewhat important", "Important, soon", "Urgent, must not be missed"))),
            )
            .put(
                "reminder",
                choice(
                    "How should the person be alerted when it is due",
                    JSONObject()
                        .put("alarm", "They want a ringing alarm, to be woken up, or a timer")
                        .put("notify", "A normal reminder notification")
                        .put("none", "No alert needed"),
                ),
            )
        LABELS.forEach { (label, question) -> q.put("label_$label", JSONObject().put("type", "noul").put("instructions", question)) }
        return q
    }

    private fun choice(instructions: String, criteria: JSONObject) =
        JSONObject().put("type", "choice").put("instructions", instructions).put("criteria", criteria)

    private fun parse(a: JSONObject): AiResult {
        val project = a.optJSONObject("project")?.takeIf { it.optDouble("confidence") >= 0.35 }?.optString("choice")
        val score = a.optJSONObject("priority")?.optDouble("score") ?: 0.0
        val priority = when {
            score >= 2.5 -> 1
            score >= 1.8 -> 2
            else -> null
        }
        val reminder = when (a.optJSONObject("reminder")?.optString("choice")) {
            "alarm" -> ReminderKind.ALARM
            "notify" -> ReminderKind.NOTIFY
            else -> null
        }
        val labels = LABELS.keys.filter { (a.optJSONObject("label_$it")?.optDouble("noul") ?: 0.0) >= 0.7 }
        return AiResult(
            title = null,
            project = project?.takeIf { it.isNotBlank() },
            labels = labels,
            priority = priority,
            reminder = reminder,
            due = null,
            hasTime = false,
            recurrence = null,
            provider = "typesafe/$model",
        )
    }

    companion object {
        private const val TAG = "TypeSafeTaskAi"
        private const val ENDPOINT = "https://api.typesafe.ai/v1/systemone"

        private val PROJECT_HINTS = mapOf(
            "Inbox" to "None of the other lists fits, or it is unclear",
            "Personal" to "Family, friends, social plans, birthdays, personal errands",
            "Work" to "Job, office, clients, meetings, reports, deadlines",
            "Shopping" to "Buying or ordering things, groceries",
            "Health" to "Doctor, medicine, exercise, gym, diet, sleep",
            "Finance" to "Paying bills, rent, loans, insurance, taxes, banking",
            "Home" to "Cleaning, cooking, repairs, household chores",
            "Study" to "Exams, homework, courses, reading, learning",
        )

        /** Fixed label set; Jev answers a yes/no per label, all in the same call. */
        private val LABELS = linkedMapOf(
            "family" to "The task involves a family member",
            "call" to "The task is to phone or video-call someone",
            "errand" to "The task means going out somewhere to get something done",
            "bills" to "The task is about paying a bill or a due payment",
            "appointment" to "The task is an appointment at a set time with a doctor, office or service",
            "travel" to "The task is about a trip, flight, train or bus journey",
            "birthday" to "The task is about a birthday or anniversary",
        )
    }
}
