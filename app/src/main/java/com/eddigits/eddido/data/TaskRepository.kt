package com.eddigits.eddido.data

import android.content.Context
import com.eddigits.eddido.ai.AiProvider
import com.eddigits.eddido.ai.AiResult
import com.eddigits.eddido.ai.TaskAi
import com.eddigits.eddido.alarm.ReminderScheduler
import com.eddigits.eddido.model.Task
import com.eddigits.eddido.parse.ParsedTask
import com.eddigits.eddido.parse.QuickAddParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * All tasks, kept in memory and saved as one JSON file. Small, dependency-free and
 * plenty fast for a personal to-do list.
 */
class TaskRepository private constructor(
    private val context: Context,
    private val jevAi: TaskAi? = AiProvider.jev,
    private val languageAi: TaskAi? = AiProvider.language,
) {
    private val file = File(context.filesDir, "tasks.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val _projects = MutableStateFlow(DEFAULT_PROJECTS)
    val projects: StateFlow<List<String>> = _projects.asStateFlow()

    init {
        load()
    }

    fun get(id: Long): Task? = _tasks.value.firstOrNull { it.id == id }

    /**
     * Quick add. The task is saved at once with whatever is known (AI answers the live
     * preview already fetched, else the offline parser), then re-decided by
     * [TaskResolver] as each AI answer arrives.
     *
     * @param fallbackToday adding from the Today view: date the task today when nothing
     *   else gives a date. It is a soft default, so a date the AI reads replaces it.
     */
    fun addFromText(
        text: String,
        description: String = "",
        manual: ManualChoices = ManualChoices(),
        fallbackToday: Boolean = false,
    ): Task {
        val pend = Pending(
            text = text.trim(),
            parsed = QuickAddParser.parse(text),
            manual = manual,
            fallbackToday = fallbackToday,
            jev = jevAi?.let { answered(JEV, text) },
            language = languageAi?.let { answered(LANGUAGE, text) },
        )
        pend.jevDone = jevAi == null || pend.jev != null
        pend.languageDone = languageAi == null || pend.language != null

        val task = pend.toTask(
            Task(id = System.currentTimeMillis(), title = "", description = description, sourceText = pend.text),
        )
        upsert(task)
        if (task.aiPending) {
            pend.written = task
            pending[task.id] = pend
            scope.launch {
                coroutineScope {
                    if (!pend.jevDone) launch {
                        val r = cached(JEV, pend.text, jevAi!!).await()
                        applyAnswer(task.id) { jev = r; jevDone = true }
                    }
                    if (!pend.languageDone) launch {
                        val r = cached(LANGUAGE, pend.text, languageAi!!).await()
                        applyAnswer(task.id) { language = r; languageDone = true }
                    }
                }
                finishAi(task.id)
            }
        }
        return task
    }

    /** Everything needed to re-decide a task while its AI answers arrive. */
    private inner class Pending(
        val text: String,
        val parsed: ParsedTask,
        val manual: ManualChoices,
        val fallbackToday: Boolean,
        var jev: AiResult?,
        var language: AiResult?,
    ) {
        var jevDone = false
        var languageDone = false
        var written: Task? = null

        fun toTask(base: Task): Task {
            val r = TaskResolver.resolve(
                text, parsed, manual, jev, language,
                offlineProject = AiProvider.offline.projectFor(text),
                fallbackToday = fallbackToday,
                now = LocalDateTime.now(),
            )
            return base.copy(
                title = r.title,
                due = r.due,
                hasTime = r.hasTime,
                recurrence = r.recurrence,
                priority = r.priority,
                project = ensureProject(r.project),
                labels = r.labels,
                reminder = r.reminder,
                aiPending = !(jevDone && languageDone),
            )
        }
    }

    private val pending = ConcurrentHashMap<Long, Pending>()

    private fun applyAnswer(id: Long, update: Pending.() -> Unit) {
        synchronized(lock) { applyAnswerLocked(id, update) }
    }

    private fun applyAnswerLocked(id: Long, update: Pending.() -> Unit) {
        val p = pending[id] ?: return
        val current = get(id)
        // Deleted, completed or edited by you since: the AI must not overwrite your change.
        if (current == null || current != p.written) {
            pending.remove(id); return
        }
        p.update()
        val next = p.toTask(current)
        if (next != current) upsert(next)
        p.written = next
    }

    private fun finishAi(id: Long) {
        synchronized(lock) {
            pending.remove(id)
            get(id)?.takeIf { it.aiPending }?.let { upsert(it.copy(aiPending = false)) }
        }
    }

    // ── AI answers, cached per text so the live preview and the save share one call ──

    private class CacheEntry(val answer: Deferred<AiResult?>, val createdAt: Long)

    private val aiCache = ConcurrentHashMap<String, CacheEntry>()

    private fun key(kind: String, text: String) = "$kind|${text.trim().lowercase()}"

    /** Answers expire after two minutes, so "in 20 minutes" is never read against a stale clock. */
    private fun fresh(e: CacheEntry?) = e != null && System.currentTimeMillis() - e.createdAt < CACHE_MS

    private fun cached(kind: String, text: String, provider: TaskAi): Deferred<AiResult?> {
        val key = key(kind, text)
        if (aiCache.size > 200) aiCache.clear()
        val entry = aiCache.compute(key) { _, old ->
            if (fresh(old)) old else CacheEntry(
                scope.async(start = CoroutineStart.LAZY) {
                    runCatching { provider.analyze(text.trim(), _projects.value, LocalDateTime.now()) }.getOrNull()
                        .also { if (it == null) aiCache.remove(key) } // failed: let the next attempt retry
                },
                System.currentTimeMillis(),
            )
        }!!
        entry.answer.start()
        return entry.answer
    }

    /** An answer that has already arrived, without waiting. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun answered(kind: String, text: String): AiResult? =
        aiCache[key(kind, text)]?.takeIf { fresh(it) && it.answer.isCompleted }?.let { runCatching { it.answer.getCompleted() }.getOrNull() }

    /** Jev's choices for the live chips (~0.4 s); null without a TypeSafe key. */
    suspend fun previewJev(text: String): AiResult? = jevAi?.let { cached(JEV, text, it).await() }

    /** The language model's full reading for the live chips (~2–5 s); null without an OpenRouter key. */
    suspend fun previewLanguage(text: String): AiResult? = languageAi?.let { cached(LANGUAGE, text, it).await() }

    fun upsert(task: Task) {
        synchronized(lock) {
            _tasks.update { list -> list.filterNot { it.id == task.id } + task }
            save()
        }
        ReminderScheduler.schedule(context, task)
    }

    fun delete(id: Long) {
        synchronized(lock) {
            _tasks.update { list -> list.filterNot { it.id == id } }
            save()
        }
        ReminderScheduler.cancel(context, id, includeSnooze = true)
    }

    /**
     * Completing a repeating task moves it to its next date, as Todoist does.
     * Returns the updated task.
     */
    fun complete(id: Long, done: Boolean = true): Task? {
        val t = get(id) ?: return null
        val rec = t.recurrence
        val updated = if (done && rec != null && t.due != null) {
            var next = rec.next(t.due)
            val now = LocalDateTime.now()
            // Skip occurrences already in the past (e.g. completing a week-old daily task).
            while (if (t.hasTime) !next.isAfter(now) else next.toLocalDate().isBefore(now.toLocalDate())) next = rec.next(next)
            t.copy(due = next)
        } else {
            t.copy(completed = done, completedAt = if (done) System.currentTimeMillis() else null)
        }
        upsert(updated)
        return updated
    }

    /** Move an alarm/reminder [minutes] later without changing the task's date. */
    fun snooze(id: Long, minutes: Long) {
        val t = get(id) ?: return
        ReminderScheduler.scheduleAt(context, t, LocalDateTime.now().plusMinutes(minutes), snooze = true)
    }

    fun addProject(name: String) {
        ensureProject(name)
    }

    private fun ensureProject(name: String): String {
        val clean = name.trim().replaceFirstChar { it.uppercase() }
        val existing = _projects.value.firstOrNull { it.equals(clean, ignoreCase = true) }
        if (existing != null) return existing
        synchronized(lock) {
            _projects.update { it + clean }
            save()
        }
        return clean
    }

    fun rescheduleAll() {
        _tasks.value.forEach { ReminderScheduler.schedule(context, it) }
    }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val o = JSONObject(file.readText())
            val arr = o.optJSONArray("tasks") ?: JSONArray()
            _tasks.value = (0 until arr.length()).mapNotNull { runCatching { Task.fromJson(arr.getJSONObject(it)) }.getOrNull() }
            o.optJSONArray("projects")?.let { a ->
                _projects.value = (DEFAULT_PROJECTS + (0 until a.length()).map { a.getString(it) }).distinct()
            }
        }
    }

    private fun save() {
        val o = JSONObject()
            .put("tasks", JSONArray(_tasks.value.map { it.toJson() }))
            .put("projects", JSONArray(_projects.value))
        val tmp = File(file.parentFile, "tasks.json.tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(file)
    }

    companion object {
        private const val JEV = "jev"
        private const val LANGUAGE = "language"
        private const val CACHE_MS = 2 * 60_000L

        val DEFAULT_PROJECTS = listOf(Task.INBOX, "Personal", "Work", "Shopping", "Health", "Finance", "Home", "Study")

        @Volatile private var instance: TaskRepository? = null

        fun get(context: Context): TaskRepository =
            instance ?: synchronized(this) { instance ?: TaskRepository(context.applicationContext).also { instance = it } }
    }
}
