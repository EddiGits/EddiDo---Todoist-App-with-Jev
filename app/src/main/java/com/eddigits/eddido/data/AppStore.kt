package com.eddigits.eddido.data

import android.content.Context
import com.eddigits.eddido.alarm.ClockAlarms
import com.eddigits.eddido.alarm.Notifications
import com.eddigits.eddido.model.CheckList
import com.eddigits.eddido.model.Countdown
import com.eddigits.eddido.model.FocusLog
import com.eddigits.eddido.model.FocusPhase
import com.eddigits.eddido.model.FocusSession
import com.eddigits.eddido.model.ListItem
import com.eddigits.eddido.model.Note
import com.eddigits.eddido.model.Stopwatch
import com.eddigits.eddido.model.TimerItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

/**
 * Everything that isn't a task: timers, the stopwatch, focus sessions, lists, countdowns
 * and notes. Kept in memory and saved as one JSON file, like tasks.
 * Times are wall-clock millis, so running timers survive the app being closed.
 */
class AppStore private constructor(private val context: Context) {
    private val file = File(context.filesDir, "extras.json")
    private val lock = Any()
    private val ids = AtomicLong(System.currentTimeMillis())
    private fun newId() = ids.incrementAndGet()

    private val _timers = MutableStateFlow<List<TimerItem>>(emptyList())
    val timers: StateFlow<List<TimerItem>> = _timers.asStateFlow()
    private val _stopwatch = MutableStateFlow(Stopwatch())
    val stopwatch: StateFlow<Stopwatch> = _stopwatch.asStateFlow()
    private val _focus = MutableStateFlow<FocusSession?>(null)
    val focus: StateFlow<FocusSession?> = _focus.asStateFlow()
    private val _focusLog = MutableStateFlow<List<FocusLog>>(emptyList())
    val focusLog: StateFlow<List<FocusLog>> = _focusLog.asStateFlow()
    private val _lists = MutableStateFlow<List<CheckList>>(emptyList())
    val lists: StateFlow<List<CheckList>> = _lists.asStateFlow()
    private val _countdowns = MutableStateFlow<List<Countdown>>(emptyList())
    val countdowns: StateFlow<List<Countdown>> = _countdowns.asStateFlow()
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    init {
        load()
    }

    private inline fun edit(block: () -> Unit) = synchronized(lock) { block(); save() }

    // ── Timers ──

    fun startTimer(label: String, durationMs: Long): TimerItem {
        val now = System.currentTimeMillis()
        val t = TimerItem(newId(), label, durationMs, endsAt = now + durationMs, remainingMs = durationMs)
        edit { _timers.value = listOf(t) + _timers.value }
        armTimer(t)
        return t
    }

    fun pauseTimer(id: Long) = updateTimer(id) { t ->
        val now = System.currentTimeMillis()
        t.copy(endsAt = null, remainingMs = t.remainingAt(now))
    }

    fun resumeTimer(id: Long) = updateTimer(id) { t ->
        t.copy(endsAt = System.currentTimeMillis() + t.remainingMs, finished = false)
    }

    fun resetTimer(id: Long) = updateTimer(id) { t -> t.copy(endsAt = null, remainingMs = t.durationMs, finished = false) }

    fun addMinute(id: Long) = updateTimer(id) { t ->
        val now = System.currentTimeMillis()
        when {
            t.finished -> t.copy(finished = false, endsAt = now + 60_000, remainingMs = 60_000, durationMs = t.durationMs + 60_000)
            t.endsAt != null -> t.copy(endsAt = t.endsAt + 60_000, durationMs = t.durationMs + 60_000)
            else -> t.copy(remainingMs = t.remainingMs + 60_000, durationMs = t.durationMs + 60_000)
        }
    }

    fun deleteTimer(id: Long) {
        edit { _timers.value = _timers.value.filterNot { it.id == id } }
        ClockAlarms.cancel(context, timerKey(id))
        Notifications.cancelTimer(context, id)
    }

    /** Called when a timer's alarm fires. */
    fun onTimerDone(id: Long): TimerItem? {
        var done: TimerItem? = null
        edit {
            _timers.value = _timers.value.map { if (it.id == id) it.copy(finished = true, endsAt = null, remainingMs = 0).also { d -> done = d } else it }
        }
        return done
    }

    private fun updateTimer(id: Long, f: (TimerItem) -> TimerItem) {
        var updated: TimerItem? = null
        edit { _timers.value = _timers.value.map { if (it.id == id) f(it).also { u -> updated = u } else it } }
        updated?.let(::armTimer)
    }

    private fun armTimer(t: TimerItem) {
        if (t.running) {
            ClockAlarms.schedule(context, timerKey(t.id), t.endsAt!!)
            Notifications.showTimerRunning(context, t)
        } else {
            ClockAlarms.cancel(context, timerKey(t.id))
            Notifications.cancelTimer(context, t.id)
        }
    }

    // ── Stopwatch ──

    fun startStopwatch(label: String) = edit {
        _stopwatch.value = Stopwatch(label = label, startedAt = System.currentTimeMillis())
    }

    fun toggleStopwatch() = edit {
        val s = _stopwatch.value
        val now = System.currentTimeMillis()
        _stopwatch.value = if (s.running) s.copy(startedAt = null, accumulatedMs = s.elapsedAt(now)) else s.copy(startedAt = now)
    }

    fun lap() = edit {
        val s = _stopwatch.value
        if (s.running) _stopwatch.value = s.copy(laps = s.laps + s.elapsedAt(System.currentTimeMillis()))
    }

    fun resetStopwatch() = edit { _stopwatch.value = Stopwatch() }

    // ── Focus ──

    fun startFocus(label: String, focusMin: Int, breakMin: Int, rounds: Int = 4) {
        val s = FocusSession(label, focusMin, breakMin, rounds, phaseEndsAt = System.currentTimeMillis() + focusMin * 60_000L, phaseRemainingMs = focusMin * 60_000L)
        edit { _focus.value = s }
        armFocus(s)
    }

    fun toggleFocus() {
        val s = _focus.value ?: return
        val now = System.currentTimeMillis()
        val next = if (s.running) s.copy(phaseEndsAt = null, phaseRemainingMs = s.remainingAt(now)) else s.copy(phaseEndsAt = now + s.phaseRemainingMs)
        edit { _focus.value = next }
        armFocus(next)
    }

    fun skipFocusPhase() {
        val s = _focus.value ?: return
        advanceFocus(s, logFocus = false)
    }

    fun stopFocus() {
        edit { _focus.value = null }
        ClockAlarms.cancel(context, FOCUS_KEY)
        Notifications.cancelFocus(context)
    }

    /** Called when a focus or break stretch ends. */
    fun onFocusPhaseEnd(): FocusSession? {
        val s = _focus.value ?: return null
        return advanceFocus(s, logFocus = true)
    }

    private fun advanceFocus(s: FocusSession, logFocus: Boolean): FocusSession {
        val now = System.currentTimeMillis()
        val next = when {
            s.phase == FocusPhase.FOCUS && s.round >= s.rounds -> s.copy(done = true, phaseEndsAt = null, phaseRemainingMs = 0)
            s.phase == FocusPhase.FOCUS -> s.copy(phase = FocusPhase.BREAK, phaseEndsAt = now + s.breakMin * 60_000L, phaseRemainingMs = s.breakMin * 60_000L)
            else -> s.copy(phase = FocusPhase.FOCUS, round = s.round + 1, phaseEndsAt = now + s.focusMin * 60_000L, phaseRemainingMs = s.focusMin * 60_000L)
        }
        edit {
            if (logFocus && s.phase == FocusPhase.FOCUS) _focusLog.value = _focusLog.value + FocusLog(LocalDate.now(), s.focusMin, s.label)
            _focus.value = next
        }
        armFocus(next)
        return next
    }

    private fun armFocus(s: FocusSession) {
        if (s.running) {
            ClockAlarms.schedule(context, FOCUS_KEY, s.phaseEndsAt!!)
            Notifications.showFocusRunning(context, s)
        } else {
            ClockAlarms.cancel(context, FOCUS_KEY)
            if (s.done) Notifications.cancelFocus(context) else Notifications.showFocusRunning(context, s)
        }
    }

    fun focusMinutesToday(): Int = _focusLog.value.filter { it.date == LocalDate.now() }.sumOf { it.minutes }

    // ── Lists ──

    /** Adds items to the list with this name, creating it if needed. Returns the list. */
    fun addToList(name: String, items: List<String>): CheckList {
        var result: CheckList? = null
        edit {
            val existing = _lists.value.firstOrNull { it.name.equals(name, ignoreCase = true) }
            val newItems = items.map { ListItem(newId(), it) }
            result = existing?.copy(items = existing.items + newItems) ?: CheckList(newId(), name, newItems)
            _lists.value = listOf(result!!) + _lists.value.filterNot { it.id == result!!.id }
        }
        return result!!
    }

    fun toggleItem(listId: Long, itemId: Long) = updateList(listId) { l ->
        l.copy(items = l.items.map { if (it.id == itemId) it.copy(checked = !it.checked) else it })
    }

    fun addItem(listId: Long, text: String) = updateList(listId) { l -> l.copy(items = l.items + ListItem(newId(), text)) }
    fun deleteItem(listId: Long, itemId: Long) = updateList(listId) { l -> l.copy(items = l.items.filterNot { it.id == itemId }) }
    fun clearChecked(listId: Long) = updateList(listId) { l -> l.copy(items = l.items.filterNot { it.checked }) }
    fun renameList(listId: Long, name: String) = updateList(listId) { it.copy(name = name) }
    fun deleteList(listId: Long) = edit { _lists.value = _lists.value.filterNot { it.id == listId } }

    private fun updateList(id: Long, f: (CheckList) -> CheckList) = edit {
        _lists.value = _lists.value.map { if (it.id == id) f(it) else it }
    }

    // ── Countdowns ──

    fun addCountdown(title: String, date: LocalDate): Countdown {
        val c = Countdown(newId(), title, date)
        edit { _countdowns.value = _countdowns.value + c }
        return c
    }

    fun updateCountdown(c: Countdown) = edit { _countdowns.value = _countdowns.value.map { if (it.id == c.id) c else it } }
    fun deleteCountdown(id: Long) = edit { _countdowns.value = _countdowns.value.filterNot { it.id == id } }

    // ── Notes ──

    fun addNote(text: String): Note {
        val n = Note(newId(), text)
        edit { _notes.value = listOf(n) + _notes.value }
        return n
    }

    fun updateNote(id: Long, text: String) = edit {
        _notes.value = _notes.value.map { if (it.id == id) it.copy(text = text, updatedAt = System.currentTimeMillis()) else it }
    }

    fun togglePin(id: Long) = edit { _notes.value = _notes.value.map { if (it.id == id) it.copy(pinned = !it.pinned) else it } }
    fun deleteNote(id: Long) = edit { _notes.value = _notes.value.filterNot { it.id == id } }

    /** After a reboot or update: put back the alarms for running timers and focus. */
    fun rearm() {
        _timers.value.filter { it.running }.forEach(::armTimer)
        _focus.value?.let(::armFocus)
    }

    // ── Persistence ──

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val o = JSONObject(file.readText())
            fun <T> arr(key: String, f: (JSONObject) -> T): List<T> =
                o.optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { runCatching { f(a.getJSONObject(it)) }.getOrNull() } } ?: emptyList()
            _timers.value = arr("timers", TimerItem::fromJson)
            _stopwatch.value = Stopwatch.fromJson(o.optJSONObject("stopwatch"))
            _focus.value = FocusSession.fromJson(o.optJSONObject("focus"))
            _focusLog.value = arr("focusLog", FocusLog::fromJson)
            _lists.value = arr("lists", CheckList::fromJson)
            _countdowns.value = arr("countdowns", Countdown::fromJson)
            _notes.value = arr("notes", Note::fromJson)
        }
    }

    private fun save() {
        val o = JSONObject()
            .put("timers", JSONArray(_timers.value.map { it.toJson() }))
            .put("stopwatch", _stopwatch.value.toJson())
            .put("focus", _focus.value?.toJson())
            .put("focusLog", JSONArray(_focusLog.value.takeLast(500).map { it.toJson() }))
            .put("lists", JSONArray(_lists.value.map { it.toJson() }))
            .put("countdowns", JSONArray(_countdowns.value.map { it.toJson() }))
            .put("notes", JSONArray(_notes.value.map { it.toJson() }))
        val tmp = File(file.parentFile, "extras.json.tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(file)
    }

    companion object {
        const val FOCUS_KEY = "focus"
        fun timerKey(id: Long) = "timer:$id"

        @Volatile private var instance: AppStore? = null

        fun get(context: Context): AppStore =
            instance ?: synchronized(this) { instance ?: AppStore(context.applicationContext).also { instance = it } }
    }
}
