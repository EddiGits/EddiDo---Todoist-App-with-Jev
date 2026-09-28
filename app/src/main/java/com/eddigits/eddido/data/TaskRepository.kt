package com.eddigits.eddido.data

import android.content.Context
import com.eddigits.eddido.ai.AiProvider
import com.eddigits.eddido.ai.AiResult
import com.eddigits.eddido.ai.TaskAi
import com.eddigits.eddido.alarm.ReminderScheduler
import com.eddigits.eddido.model.ReminderKind
import com.eddigits.eddido.model.Task
import com.eddigits.eddido.parse.ParsedTask
import com.eddigits.eddido.parse.QuickAddParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

/**
 * All tasks, kept in memory and saved as one JSON file. Small, dependency-free and
 * plenty fast for a personal to-do list.
 */
class TaskRepository private constructor(
    private val context: Context,
    private val ai: TaskAi = AiProvider.choices,
    private val dateAi: TaskAi? = AiProvider.dates,
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
     * Quick add: save instantly from the offline parse, then let the AI refine
     * project/labels/priority in the background.
     */
    fun addFromText(text: String, description: String = "", manual: ManualChoices = ManualChoices()): Task {
        val parsed = QuickAddParser.parse(text)
        val task = manual.applyTo(
            Task(
                id = System.currentTimeMillis(),
                title = parsed.title.ifBlank { text.trim() },
                description = description,
                due = parsed.due,
                hasTime = parsed.hasTime,
                priority = parsed.priority ?: 4,
                project = parsed.project?.let(::ensureProject) ?: Task.INBOX,
                labels = parsed.labels,
                recurrence = parsed.recurrence,
                reminder = parsed.reminder,
                aiPending = true,
                sourceText = text,
            ),
        ).let { t -> t.copy(project = ensureProject(t.project)) }
        upsert(task)
        // Anything the user chose with a chip counts as explicit, same as typed "p1" or "#work".
        val explicit = parsed.copy(
            due = if (manual.dueSet) manual.due else parsed.due,
            priority = manual.priority ?: parsed.priority,
            project = manual.project ?: parsed.project,
            explicitReminder = manual.reminder ?: parsed.explicitReminder,
        )
        scope.launch { refineWithAi(task.id, explicit, dueLocked = manual.dueSet || manual.recurrenceSet) }
        return task
    }

    /**
     * Two AI passes run side by side, and each result is applied the moment it lands:
     * [ai] picks project/labels/priority/reminder (Jev: ~0.4 s), and [dateAi] reads a date
     * only when the offline parser found none, e.g. Tamil or Hindi text.
     */
    private suspend fun refineWithAi(id: Long, parsed: ParsedTask, dueLocked: Boolean) = coroutineScope {
        val text = get(id)?.sourceText ?: return@coroutineScope
        val dates = dateAi
        if (dates != null && parsed.due == null && !dueLocked) launch {
            runCatching { dates.analyze(text, _projects.value, LocalDateTime.now()) }.getOrNull()
                ?.let { r -> merge(id, parsed, dueLocked, r.copy(project = null, labels = emptyList(), priority = null), finish = false) }
        }
        val result = runCatching { ai.analyze(text, _projects.value, LocalDateTime.now()) }.getOrNull()
        if (result == null) get(id)?.let { upsert(it.copy(aiPending = false)) }
        else merge(id, parsed, dueLocked, result, finish = true)
    }

    /** Read-merge-write under the lock so the two passes never overwrite each other. */
    private fun merge(id: Long, parsed: ParsedTask, dueLocked: Boolean, result: AiResult, finish: Boolean) {
        synchronized(lock) { mergeLocked(id, parsed, dueLocked, result, finish) }
    }

    private fun mergeLocked(id: Long, parsed: ParsedTask, dueLocked: Boolean, result: AiResult, finish: Boolean) {
        val latest = get(id) ?: return
        // The user's explicit words always win over the AI's guess.
        val aiDue = result.due?.takeIf { !dueLocked && parsed.due == null && latest.due == null && it.isAfter(LocalDateTime.now().minusMinutes(1)) }
        var refined = latest.copy(
            // If the AI found a date the parser missed, the parser's title still contains the date words.
            title = if (parsed.title.isBlank() || aiDue != null) result.title ?: latest.title else latest.title,
            project = if (parsed.project == null && latest.project == Task.INBOX) result.project?.let(::ensureProject) ?: latest.project else latest.project,
            labels = (latest.labels + result.labels).distinct().take(5),
            priority = if (parsed.priority == null && latest.priority == 4) result.priority ?: 4 else latest.priority,
            recurrence = latest.recurrence ?: if (aiDue != null) result.recurrence else null,
            aiPending = latest.aiPending && !finish,
        )
        if (aiDue != null) refined = refined.copy(due = aiDue, hasTime = result.hasTime)
        if (parsed.explicitReminder == null) {
            // Anything with a time gets at least a notification; the AI can upgrade it to an alarm.
            val kind = result.reminder?.takeIf { it != ReminderKind.NONE }
                ?: latest.reminder.takeIf { it != ReminderKind.NONE }
                ?: ReminderKind.NOTIFY
            refined = refined.copy(reminder = if (refined.hasTime) kind else ReminderKind.NONE)
        }
        upsert(refined)
    }

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
        val DEFAULT_PROJECTS = listOf(Task.INBOX, "Personal", "Work", "Shopping", "Health", "Finance", "Home", "Study")

        @Volatile private var instance: TaskRepository? = null

        fun get(context: Context): TaskRepository =
            instance ?: synchronized(this) { instance ?: TaskRepository(context.applicationContext).also { instance = it } }
    }
}
