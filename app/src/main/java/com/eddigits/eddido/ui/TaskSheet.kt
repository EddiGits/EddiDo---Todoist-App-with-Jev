package com.eddigits.eddido.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.eddigits.eddido.ai.AiResult
import com.eddigits.eddido.data.ManualChoices
import com.eddigits.eddido.data.TaskResolver
import com.eddigits.eddido.model.Recurrence
import com.eddigits.eddido.model.ReminderKind
import com.eddigits.eddido.model.RepeatUnit
import com.eddigits.eddido.model.Task
import com.eddigits.eddido.parse.QuickAddParser
import com.eddigits.eddido.parse.Span
import com.eddigits.eddido.ui.theme.priorityColor
import java.time.LocalDateTime

/** The values the chips show and edit. */
data class TaskFields(
    val due: LocalDateTime? = null,
    val hasTime: Boolean = false,
    val priority: Int = 4,
    val reminder: ReminderKind = ReminderKind.NONE,
    val recurrence: Recurrence? = null,
    val project: String = Task.INBOX,
    val labels: List<String> = emptyList(),
    /** Which chips show an AI suggestion rather than something typed or picked. */
    val aiDerived: Set<String> = emptySet(),
)

/** Quick add: type naturally, the chips light up as dates, repeats and priorities are recognised. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    projects: List<String>,
    defaultProject: String?,
    defaultToday: Boolean,
    previewJev: suspend (String) -> AiResult?,
    previewLanguage: suspend (String) -> AiResult?,
    onDismiss: () -> Unit,
    onSubmit: (text: String, description: String, manual: ManualChoices, fallbackToday: Boolean) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(ManualChoices(project = defaultProject)) }
    val parsed = remember(text) { QuickAddParser.parse(text) }

    // Live AI: once typing pauses, ask Jev (~0.4 s) and the language model (~2–5 s) in parallel.
    // Answers are cached, so saving reuses them instead of asking again.
    var jev by remember { mutableStateOf<Pair<String, AiResult>?>(null) }
    var language by remember { mutableStateOf<Pair<String, AiResult>?>(null) }
    var aiBusy by remember { mutableStateOf(false) }
    LaunchedEffect(text) {
        val t = text.trim()
        if (t.length < 4) { aiBusy = false; return@LaunchedEffect }
        delay(500)
        aiBusy = true
        coroutineScope {
            launch { previewJev(t)?.let { jev = t to it } }
            launch { previewLanguage(t)?.let { language = t to it } }
        }
        aiBusy = false
    }
    val current = text.trim()
    // Same decision as the saved task (see TaskResolver), so what you see is what you get.
    val resolved = TaskResolver.resolve(
        text = current,
        parsed = parsed,
        manual = manual,
        jev = jev?.takeIf { it.first == current }?.second,
        language = language?.takeIf { it.first == current }?.second,
        offlineProject = null,
        fallbackToday = defaultToday,
        now = LocalDateTime.now(),
    )
    val fields = TaskFields(
        due = resolved.due,
        hasTime = resolved.hasTime,
        priority = resolved.priority,
        reminder = resolved.reminder,
        recurrence = resolved.recurrence,
        project = resolved.project,
        labels = resolved.labels,
        aiDerived = resolved.aiDerived,
    )
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun submit() {
        if (text.isBlank()) return
        onSubmit(text.trim(), description.trim(), manual, defaultToday)
        text = ""; description = ""
        manual = ManualChoices(project = defaultProject)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.imePadding().navigationBarsPadding()) {
            PlainField(
                value = text,
                onValueChange = { text = it },
                placeholder = "e.g. Wake me up every weekday 6am",
                big = true,
                modifier = Modifier.focusRequester(focus),
                transformation = HighlightSpans(parsed.spans, MaterialTheme.colorScheme.primary),
                imeAction = ImeAction.Send,
                onIme = ::submit,
            )
            PlainField(description, { description = it }, "Description", big = false)
            if (text.isBlank()) {
                Text(
                    "Try “pay rent on 5th p1”, “call mom tomorrow 7pm”, “10 min timer”, “naalai kalaila amma ku call”",
                    Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    val status = when {
                        resolved.aiDerived.isNotEmpty() -> buildList {
                            if ("title" in resolved.aiDerived && resolved.title != current) add("“${resolved.title}”")
                            if ("project" in resolved.aiDerived) add(resolved.project)
                            if ("labels" in resolved.aiDerived) resolved.labels.takeIf { it.isNotEmpty() }?.let { l -> add(l.joinToString(" ") { "@$it" }) }
                        }.joinToString(" · ").ifEmpty { "AI agrees" } + if (aiBusy) " …" else ""
                        aiBusy -> "Thinking…"
                        else -> "AI reads the date, time, repeat and project as you type"
                    }
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            AttributeChips(
                fields = fields,
                projects = projects,
                onDue = { d, t -> manual = manual.copy(dueSet = true, due = d, hasTime = t) },
                onPriority = { manual = manual.copy(priority = it) },
                onReminder = { manual = manual.copy(reminder = it) },
                onRepeat = { manual = manual.copy(recurrenceSet = true, recurrence = it) },
                onLabels = { manual = manual.copy(labels = it) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ProjectPicker(fields.project, projects, ai = "project" in fields.aiDerived) { manual = manual.copy(project = it) }
                Spacer(Modifier.weight(1f))
                FilledIconButton(
                    onClick = ::submit,
                    enabled = text.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.padding(end = 8.dp).size(width = 52.dp, height = 40.dp),
                ) { Icon(Icons.AutoMirrored.Filled.Send, "Add task", tint = Color.White) }
            }
        }
    }
}

/** Edit an existing task with the same chips. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskSheet(
    task: Task,
    projects: List<String>,
    onDismiss: () -> Unit,
    onSave: (Task) -> Unit,
    onDelete: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf(task.title) }
    var description by remember { mutableStateOf(task.description) }
    var f by remember {
        mutableStateOf(TaskFields(task.due, task.hasTime, task.priority, task.reminder, task.recurrence, task.project, task.labels))
    }

    fun save() {
        var t = task.copy(
            title = title.trim().ifEmpty { task.title },
            description = description.trim(),
            due = f.due, hasTime = f.due != null && f.hasTime,
            priority = f.priority, reminder = f.reminder, recurrence = f.recurrence,
            project = f.project, labels = f.labels,
        )
        if (t.reminder != ReminderKind.NONE && t.due != null && !t.hasTime) t = t.copy(due = t.due!!.toLocalDate().atTime(9, 0), hasTime = true)
        onSave(t)
    }

    ModalBottomSheet(onDismissRequest = { save(); onDismiss() }, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.imePadding().navigationBarsPadding()) {
            PlainField(title, { title = it }, "Task name", big = true)
            PlainField(description, { description = it }, "Description", big = false, singleLine = false)
            AttributeChips(
                fields = f,
                projects = projects,
                onDue = { d, t -> f = f.copy(due = d, hasTime = t, reminder = if (t && f.reminder == ReminderKind.NONE) ReminderKind.NOTIFY else f.reminder) },
                onPriority = { f = f.copy(priority = it) },
                onReminder = { f = f.copy(reminder = it) },
                onRepeat = { f = f.copy(recurrence = it) },
                onLabels = { f = f.copy(labels = it) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ProjectPicker(f.project, projects) { f = f.copy(project = it) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                TextButton(onClick = { save(); onDismiss() }) { Text("Save") }
            }
            if (task.sourceText.isNotBlank() && task.sourceText != task.title) {
                Text(
                    "You typed: “${task.sourceText}”",
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AttributeChips(
    fields: TaskFields,
    projects: List<String>,
    onDue: (LocalDateTime?, Boolean) -> Unit,
    onPriority: (Int) -> Unit,
    onReminder: (ReminderKind) -> Unit,
    onRepeat: (Recurrence?) -> Unit,
    onLabels: (List<String>) -> Unit,
) {
    var dialog by remember { mutableStateOf<String?>(null) }
    var more by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val due = fields.due
        Chip(
            Icons.Outlined.CalendarToday,
            if (due != null) dueLabel(due, fields.hasTime) else "Date",
            if (due != null) dueColor(due, fields.hasTime) else null,
            ai = "date" in fields.aiDerived,
        ) { dialog = "date" }
        Chip(
            if (fields.priority < 4) Icons.Filled.Flag else Icons.Outlined.Flag,
            if (fields.priority < 4) "P${fields.priority}" else "Priority",
            if (fields.priority < 4) priorityColor(fields.priority) else null,
            ai = "priority" in fields.aiDerived,
        ) { dialog = "priority" }
        Chip(
            when (fields.reminder) {
                ReminderKind.ALARM -> Icons.Filled.Alarm
                ReminderKind.NOTIFY -> Icons.Outlined.NotificationsNone
                ReminderKind.NONE -> Icons.Outlined.NotificationsOff
            },
            when (fields.reminder) {
                ReminderKind.ALARM -> "Alarm"
                ReminderKind.NOTIFY -> "Reminder"
                ReminderKind.NONE -> "Reminders"
            },
            if (fields.reminder != ReminderKind.NONE) MaterialTheme.colorScheme.primary else null,
            ai = "reminder" in fields.aiDerived,
        ) { dialog = "reminder" }
        if (fields.recurrence != null) {
            Chip(Icons.Outlined.Repeat, fields.recurrence.label(), MaterialTheme.colorScheme.primary) { dialog = "repeat" }
        }
        if (fields.labels.isNotEmpty()) {
            Chip(Icons.Outlined.Label, fields.labels.joinToString(", "), MaterialTheme.colorScheme.primary) { dialog = "labels" }
        }
        Box {
            Chip(Icons.Outlined.MoreHoriz, null, null) { more = true }
            DropdownMenu(more, { more = false }) {
                DropdownMenuItem(text = { Text("Labels") }, leadingIcon = { Icon(Icons.Outlined.Label, null) }, onClick = { more = false; dialog = "labels" })
                DropdownMenuItem(text = { Text("Repeat") }, leadingIcon = { Icon(Icons.Outlined.Repeat, null) }, onClick = { more = false; dialog = "repeat" })
            }
        }
    }

    when (dialog) {
        "date" -> DueDialog(fields.due, fields.hasTime, { dialog = null }) { d, t -> onDue(d, t); dialog = null }
        "priority" -> ChoiceDialog(
            "Priority", (1..4).map { it to if (it == 4) "Priority 4 (none)" else "Priority $it" }, fields.priority, { dialog = null },
            leading = { Icon(Icons.Filled.Flag, null, tint = priorityColor(it)) },
        ) { onPriority(it); dialog = null }
        "reminder" -> ChoiceDialog(
            "Reminder",
            listOf(ReminderKind.NONE to "No reminder", ReminderKind.NOTIFY to "Notification at due time", ReminderKind.ALARM to "Ringing alarm at due time"),
            fields.reminder, { dialog = null },
            leading = {
                Icon(
                    when (it) {
                        ReminderKind.ALARM -> Icons.Filled.Alarm
                        ReminderKind.NOTIFY -> Icons.Outlined.NotificationsNone
                        ReminderKind.NONE -> Icons.Outlined.NotificationsOff
                    }, null,
                )
            },
        ) { onReminder(it); dialog = null }
        "repeat" -> {
            val options = listOf<Pair<Recurrence?, String>>(
                null to "Does not repeat",
                Recurrence(RepeatUnit.DAY) to "Every day",
                Recurrence(RepeatUnit.WEEK, days = Recurrence.WEEKDAYS) to "Every weekday (Mon–Fri)",
                Recurrence(RepeatUnit.WEEK) to "Every week",
                Recurrence(RepeatUnit.MONTH) to "Every month",
                Recurrence(RepeatUnit.YEAR) to "Every year",
                Recurrence(RepeatUnit.HOUR) to "Every hour",
            )
            ChoiceDialog("Repeat", options, fields.recurrence, { dialog = null }) { onRepeat(it); dialog = null }
        }
        "labels" -> TextEntryDialog("Labels", fields.labels.joinToString(", "), "family, errand", { dialog = null }) { v ->
            onLabels(v.split(',', ' ').map { it.trim().removePrefix("@").lowercase() }.filter { it.isNotEmpty() }.distinct())
            dialog = null
        }
    }
}

@Composable
private fun ProjectPicker(project: String, projects: List<String>, ai: Boolean = false, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(if (project == Task.INBOX) Icons.Outlined.Inbox else Icons.Outlined.Tag, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(project, color = MaterialTheme.colorScheme.onSurface)
            if (ai) Icon(Icons.Filled.AutoAwesome, "AI suggestion", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp).size(12.dp))
            Icon(Icons.Filled.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(open, { open = false }) {
            projects.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p) },
                    leadingIcon = { Icon(if (p == Task.INBOX) Icons.Outlined.Inbox else Icons.Outlined.Tag, null) },
                    onClick = { onPick(p); open = false },
                )
            }
            DropdownMenuItem(text = { Text("New project…") }, onClick = { open = false; adding = true })
        }
    }
    if (adding) TextEntryDialog("New project", "", "e.g. Travel", { adding = false }) { name ->
        if (name.isNotBlank()) onPick(name.trim().replaceFirstChar { it.uppercase() })
        adding = false
    }
}

@Composable
private fun Chip(icon: ImageVector, label: String?, tint: Color?, ai: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, tint?.copy(alpha = 0.6f) ?: MaterialTheme.colorScheme.outline),
    ) {
        val c = tint ?: MaterialTheme.colorScheme.onSurfaceVariant
        Icon(icon, null, tint = c, modifier = Modifier.size(18.dp))
        if (label != null) {
            Spacer(Modifier.width(6.dp))
            Text(label, color = tint ?: MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, maxLines = 1)
        }
        if (ai) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.AutoAwesome, "AI suggestion", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    big: Boolean,
    modifier: Modifier = Modifier,
    singleLine: Boolean = big,
    transformation: VisualTransformation = VisualTransformation.None,
    imeAction: ImeAction = ImeAction.Default,
    onIme: () -> Unit = {},
) {
    val transparent = Color.Transparent
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium) },
        textStyle = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
        singleLine = singleLine,
        visualTransformation = transformation,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = imeAction),
        keyboardActions = KeyboardActions(onSend = { onIme() }, onDone = { onIme() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = transparent, unfocusedContainerColor = transparent,
            focusedIndicatorColor = transparent, unfocusedIndicatorColor = transparent,
        ),
    )
}

/** Tints the words the parser understood, like Todoist's quick-add highlighting. */
private class HighlightSpans(private val spans: List<Span>, private val color: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val out = buildAnnotatedString {
            append(text.text)
            spans.forEach { s ->
                val end = (s.range.last + 1).coerceAtMost(text.length)
                if (s.range.first < end) addStyle(SpanStyle(color = color, background = color.copy(alpha = 0.15f)), s.range.first, end)
            }
        }
        return TransformedText(out, OffsetMapping.Identity)
    }

    override fun equals(other: Any?) = other is HighlightSpans && other.spans == spans && other.color == color
    override fun hashCode() = spans.hashCode()
}
