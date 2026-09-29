package com.eddigits.eddido.ai

import android.util.Log
import com.eddigits.eddido.BuildConfig
import com.eddigits.eddido.model.Kind
import com.eddigits.eddido.model.ReminderKind
import com.eddigits.eddido.parse.Festivals
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
            .put("questions", questions(projects, words(text)))

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
            parse(JSONObject(raw).getJSONObject("answers"), words(text))
        } finally {
            conn.disconnect()
        }
    }

    private fun questions(projects: List<String>, words: List<String>): JSONObject {
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
        meaningQuestions(q, words)
        return q
    }

    /**
     * Shapeshift's rule is "never ask Jev to extract values or do date math". So instead of
     * asking *what date*, we ask *which kind of day* (tomorrow, a weekday, a festival…), and
     * code computes the date. Tested on English, Tamil and Hindi phrases.
     */
    private fun meaningQuestions(q: JSONObject, words: List<String>) {
        q.put("day", choice("Which day is the task for, relative to today", JSONObject()
            .put("today", "Today or tonight")
            .put("tomorrow", "Tomorrow (e.g. tomorrow, naalai, kal)")
            .put("day_after", "The day after tomorrow (e.g. parso)")
            .put("weekday", "A named day of the week such as Friday")
            .put("this_weekend", "This weekend")
            .put("next_week", "Next week")
            .put("written_date", "A calendar date written in the text such as 12 oct or 25/12")
            .put("festival", "A festival or holiday such as Diwali or Christmas")
            .put("none", "No day is mentioned")))
        q.put("weekday", choice("Which day of the week is named, in any language", JSONObject().apply {
            java.time.DayOfWeek.entries.forEach { put(it.name.lowercase(), it.name.lowercase().replaceFirstChar(Char::uppercase)) }
            put("none", "No day of the week")
        }))
        q.put("part", choice("Which part of the day is mentioned", JSONObject()
            .put("morning", "Morning").put("afternoon", "Afternoon").put("evening", "Evening")
            .put("night", "Night or tonight").put("clock", "Only a clock time such as 7pm or 6:30").put("none", "No time of day")))
        q.put("meridiem", choice("If a clock time is given, is it before or after noon", JSONObject()
            .put("am", "Before noon (a.m., morning)").put("pm", "After noon (p.m., afternoon, evening, night)").put("none", "No clock time")))
        q.put("repeat", choice("Do the words themselves say this happens again and again (every, each, daily, weekly, dhinamum, roz…)", JSONObject()
            .put("none", "No repeat word is used: it happens once, even if such tasks usually recur (like paying rent) or it is tied to a date, weekday or festival")
            .put("daily", "Every day").put("weekdays", "Every weekday, Monday to Friday")
            .put("weekly", "Every week, or every given weekday").put("monthly", "Every month").put("yearly", "Every year")
            .put("hourly", "Every hour or every few hours").put("minutes", "Every few minutes")))
        q.put("unit", choice("What unit is the length of time in", JSONObject()
            .put("seconds", "Seconds").put("minutes", "Minutes (e.g. min, nimisham)").put("hours", "Hours (e.g. hr, mani neram)").put("none", "No length of time")))
        q.put("festival", choice("Which festival or holiday is named by its name", JSONObject().apply {
            Festivals.names.forEach { (key, label) -> put(key, label) }
            put("other", "A different named festival or holiday, such as Ganesh Chaturthi, Good Friday or Ramzan, or the eve of a listed one")
            put("none", "No festival or holiday is named; a plain date such as 14 feb is not a festival")
        }))
        // Jev decides by itself whether a chat model is needed (31/31 on test phrases with the
        // cut-offs in meaning()): only for dates that need a rule or knowledge Jev can't pick.
        q.put("calc", JSONObject().put("type", "noul").put("instructions",
            "Finding the date needs a rule or private knowledge: a numbered or last weekday of a month (third Saturday, last Friday, first Monday), " +
                "a number of days before or after an event, a moon phase, or a personal date like my birthday or payday. " +
                "Counting down to a date, or doing something before a named festival, does not count"))
        // One yes/no per word: code drops the words that only say when/how, leaving the title.
        words.forEachIndexed { i, w ->
            q.put("w$i", JSONObject().put("type", "noul").put("instructions",
                "The word '$w' (word ${i + 1}) only says when, how often, how long, or asks to set, start, remind or alert, rather than naming the task itself"))
        }
    }

    private fun meaning(a: JSONObject, words: List<String>): JevMeaning {
        fun pick(key: String, min: Double = 0.5): String? =
            a.optJSONObject(key)?.takeIf { it.optDouble("confidence") >= min }?.optString("choice")
        // "Which day" is often right even at low confidence ("Nalaki" → tomorrow, 42%), as long as it isn't "none".
        val day = a.optJSONObject("day")?.let { d ->
            val c = d.optString("choice"); val conf = d.optDouble("confidence")
            c.takeIf { conf >= 0.6 || (c != "none" && conf >= 0.35) }
        }
        return JevMeaning(
            day = day,
            weekday = pick("weekday")?.takeIf { it != "none" }?.let { runCatching { java.time.DayOfWeek.valueOf(it.uppercase()) }.getOrNull() },
            part = pick("part"),
            meridiem = pick("meridiem"),
            repeat = pick("repeat", 0.6),
            unit = pick("unit"),
            festival = pick("festival", 0.6)?.takeIf { it != "none" && it != "other" },
            needsHelp = pick("festival", 0.5) == "other" || (a.optJSONObject("calc")?.optDouble("noul") ?: 0.0) >= 0.75,
            words = words,
            schedulingWords = words.indices.filter { (a.optJSONObject("w$it")?.optDouble("noul") ?: 0.0) >= 0.5 }.toSet(),
        )
    }

    private fun choice(instructions: String, criteria: JSONObject) =
        JSONObject().put("type", "choice").put("instructions", instructions).put("criteria", criteria)

    private fun parse(a: JSONObject, words: List<String>): AiResult {
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
            meaning = meaning(a, words),
            kind = a.optJSONObject("kind")?.let { Kind.fromKey(it.optString("choice")) },
            kindProbabilities = a.optJSONObject("kind")?.optJSONObject("probabilities")?.let { p ->
                p.keys().asSequence().mapNotNull { k -> Kind.fromKey(k)?.let { it to p.optDouble(k) } }.toMap()
            } ?: emptyMap(),
        )
    }

    companion object {
        private const val TAG = "TypeSafeTaskAi"

        /** Up to 16 words get their own yes/no; longer texts fall back to the parser's title. */
        fun words(text: String): List<String> = Regex("\\S+").findAll(text).map { it.value }.toList().let { if (it.size > 16) emptyList() else it }
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
