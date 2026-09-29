package com.eddigits.eddido.ai

import android.util.Log
import com.eddigits.eddido.BuildConfig
import com.eddigits.eddido.model.Kind
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
 * in a few hundred milliseconds. Jev only *chooses*; it cannot write a title or work
 * out a date, so those come from the language model ([OpenRouterTaskAi]).
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
                // Wording tuned on real phrases (16/17 right; the miss was low-confidence).
                choice(
                    "Does the person want to be alerted at a specific moment for this to-do item",
                    JSONObject()
                        .put("alarm", "They want it to ring loudly: an alarm, being woken up, or a timer")
                        .put("notify", "They ask to be reminded, or they give a clock time or part of day (morning, evening, tonight, 6pm, in 20 minutes, every 2 hours)")
                        .put("none", "A plain to-do with no reminder request and no time of day; a date or weekday alone does not count"),
                ),
            )
        // Which tab: 24/24 right on real phrases, including Tamil and Hindi.
        val kinds = JSONObject()
        Kind.entries.forEach { kinds.put(it.key, it.jevHint) }
        q.put("kind", choice("What does the person want to create with this text", kinds))
        LABELS.forEach { (label, question) -> q.put("label_$label", JSONObject().put("type", "noul").put("instructions", question)) }
        return q
    }

    private fun choice(instructions: String, criteria: JSONObject) =
        JSONObject().put("type", "choice").put("instructions", instructions).put("criteria", criteria)

    private fun parse(a: JSONObject): AiResult {
        val project = a.optJSONObject("project")?.takeIf { it.optDouble("confidence") >= 0.35 }?.optString("choice")
        val score = a.optJSONObject("priority")?.optDouble("score") ?: 0.0
        val reminder = a.optJSONObject("reminder")?.takeIf { it.optDouble("confidence") >= 0.5 }?.let {
            when (it.optString("choice")) {
                "alarm" -> ReminderKind.ALARM
                "notify" -> ReminderKind.NOTIFY
                "none" -> ReminderKind.NONE
                else -> null
            }
        }
        return AiResult(
            provider = "typesafe/$model",
            project = project?.takeIf { it.isNotBlank() },
            labels = LABELS.keys.filter { (a.optJSONObject("label_$it")?.optDouble("noul") ?: 0.0) >= 0.7 },
            priority = when {
                score >= 2.5 -> 1
                score >= 1.8 -> 2
                else -> 4
            },
            reminder = reminder,
            kind = a.optJSONObject("kind")?.let { Kind.fromKey(it.optString("choice")) },
            kindProbabilities = a.optJSONObject("kind")?.optJSONObject("probabilities")?.let { p ->
                p.keys().asSequence().mapNotNull { k -> Kind.fromKey(k)?.let { it to p.optDouble(k) } }.toMap()
            } ?: emptyMap(),
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
