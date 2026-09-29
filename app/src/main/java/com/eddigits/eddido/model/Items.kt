package com.eddigits.eddido.model

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** A countdown timer. Running: [endsAt] is set. Paused: [remainingMs] holds what is left. */
data class TimerItem(
    val id: Long,
    val label: String,
    val durationMs: Long,
    val endsAt: Long? = null,
    val remainingMs: Long = durationMs,
    val finished: Boolean = false,
) {
    val running get() = endsAt != null && !finished

    fun remainingAt(now: Long): Long = when {
        finished -> 0
        endsAt != null -> (endsAt - now).coerceAtLeast(0)
        else -> remainingMs
    }

    fun toJson(): JSONObject = JSONObject().put("id", id).put("label", label).put("durationMs", durationMs)
        .put("endsAt", endsAt).put("remainingMs", remainingMs).put("finished", finished)

    companion object {
        fun fromJson(o: JSONObject) = TimerItem(
            id = o.getLong("id"),
            label = o.optString("label"),
            durationMs = o.getLong("durationMs"),
            endsAt = if (o.isNull("endsAt")) null else o.getLong("endsAt"),
            remainingMs = o.optLong("remainingMs"),
            finished = o.optBoolean("finished"),
        )
    }
}

/** The one stopwatch. Running: [startedAt] is set; elapsed = [accumulatedMs] + (now - startedAt). */
data class Stopwatch(
    val label: String = "",
    val startedAt: Long? = null,
    val accumulatedMs: Long = 0,
    /** Elapsed time at each lap, oldest first. */
    val laps: List<Long> = emptyList(),
) {
    val running get() = startedAt != null
    fun elapsedAt(now: Long) = accumulatedMs + (startedAt?.let { now - it } ?: 0)

    fun toJson(): JSONObject = JSONObject().put("label", label).put("startedAt", startedAt)
        .put("accumulatedMs", accumulatedMs).put("laps", JSONArray(laps))

    companion object {
        fun fromJson(o: JSONObject?) = if (o == null) Stopwatch() else Stopwatch(
            label = o.optString("label"),
            startedAt = if (o.isNull("startedAt")) null else o.getLong("startedAt"),
            accumulatedMs = o.optLong("accumulatedMs"),
            laps = o.optJSONArray("laps")?.let { a -> (0 until a.length()).map { a.getLong(it) } } ?: emptyList(),
        )
    }
}

enum class FocusPhase { FOCUS, BREAK }

/** A pomodoro-style session: [rounds] focus stretches with breaks between them. */
data class FocusSession(
    val label: String,
    val focusMin: Int = 25,
    val breakMin: Int = 5,
    val rounds: Int = 4,
    val round: Int = 1,
    val phase: FocusPhase = FocusPhase.FOCUS,
    val phaseEndsAt: Long? = null,
    val phaseRemainingMs: Long = focusMin * 60_000L,
    val done: Boolean = false,
) {
    val running get() = phaseEndsAt != null && !done
    val phaseLengthMs get() = (if (phase == FocusPhase.FOCUS) focusMin else breakMin) * 60_000L
    fun remainingAt(now: Long) = if (done) 0 else phaseEndsAt?.let { (it - now).coerceAtLeast(0) } ?: phaseRemainingMs

    fun toJson(): JSONObject = JSONObject().put("label", label).put("focusMin", focusMin).put("breakMin", breakMin)
        .put("rounds", rounds).put("round", round).put("phase", phase.name).put("phaseEndsAt", phaseEndsAt)
        .put("phaseRemainingMs", phaseRemainingMs).put("done", done)

    companion object {
        fun fromJson(o: JSONObject?) = o?.let {
            FocusSession(
                label = it.optString("label"),
                focusMin = it.optInt("focusMin", 25),
                breakMin = it.optInt("breakMin", 5),
                rounds = it.optInt("rounds", 4),
                round = it.optInt("round", 1),
                phase = runCatching { FocusPhase.valueOf(it.optString("phase")) }.getOrDefault(FocusPhase.FOCUS),
                phaseEndsAt = if (it.isNull("phaseEndsAt")) null else it.getLong("phaseEndsAt"),
                phaseRemainingMs = it.optLong("phaseRemainingMs"),
                done = it.optBoolean("done"),
            )
        }
    }
}

/** One finished focus stretch, for the "today" total and history. */
data class FocusLog(val date: LocalDate, val minutes: Int, val label: String) {
    fun toJson(): JSONObject = JSONObject().put("date", date.toString()).put("minutes", minutes).put("label", label)

    companion object {
        fun fromJson(o: JSONObject) = FocusLog(LocalDate.parse(o.getString("date")), o.optInt("minutes"), o.optString("label"))
    }
}

data class ListItem(val id: Long, val text: String, val checked: Boolean = false) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("text", text).put("checked", checked)

    companion object {
        fun fromJson(o: JSONObject) = ListItem(o.getLong("id"), o.optString("text"), o.optBoolean("checked"))
    }
}

data class CheckList(val id: Long, val name: String, val items: List<ListItem> = emptyList()) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("items", JSONArray(items.map { it.toJson() }))

    companion object {
        fun fromJson(o: JSONObject) = CheckList(
            o.getLong("id"),
            o.optString("name"),
            o.optJSONArray("items")?.let { a -> (0 until a.length()).map { ListItem.fromJson(a.getJSONObject(it)) } } ?: emptyList(),
        )
    }
}

data class Countdown(val id: Long, val title: String, val date: LocalDate) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("title", title).put("date", date.toString())

    companion object {
        fun fromJson(o: JSONObject) = Countdown(o.getLong("id"), o.optString("title"), LocalDate.parse(o.getString("date")))
    }
}

data class Note(val id: Long, val text: String, val pinned: Boolean = false, val updatedAt: Long = System.currentTimeMillis()) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("text", text).put("pinned", pinned).put("updatedAt", updatedAt)

    companion object {
        fun fromJson(o: JSONObject) = Note(o.getLong("id"), o.optString("text"), o.optBoolean("pinned"), o.optLong("updatedAt"))
    }
}
