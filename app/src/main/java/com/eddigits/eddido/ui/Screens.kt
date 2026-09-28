package com.eddigits.eddido.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.eddigits.eddido.BuildConfig
import com.eddigits.eddido.data.TaskRepository
import com.eddigits.eddido.model.Task
import com.eddigits.eddido.ui.theme.Brand
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

sealed interface Screen {
    val title: String
    data object Inbox : Screen { override val title = "Inbox" }
    data object Today : Screen { override val title = "Today" }
    data object Upcoming : Screen { override val title = "Upcoming" }
    data object Browse : Screen { override val title = "Browse" }
    data object Completed : Screen { override val title = "Completed" }
    data class Project(val name: String) : Screen { override val title = name }
    data class Label(val name: String) : Screen { override val title = "@$name" }
}

private val taskOrder = compareBy<Task>({ it.due == null }, { it.due }, { it.priority }, { it.createdAt })

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EddiDoApp(repo: TaskRepository) {
    val tasks by repo.tasks.collectAsState()
    val projects by repo.projects.collectAsState()
    var screen by remember { mutableStateOf<Screen>(Screen.Today) }
    var root by remember { mutableStateOf<Screen>(Screen.Today) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Long?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun toggle(t: Task) {
        val before = t
        val after = repo.complete(t.id, !t.completed) ?: return
        if (!before.completed) scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val msg = if (after.completed) "Completed" else "Next: ${after.due?.let { dueLabel(it, after.hasTime) }}"
            if (snackbar.showSnackbar(msg, "Undo", withDismissAction = false) == SnackbarResult.ActionPerformed) repo.upsert(before)
        }
    }

    androidx.activity.compose.BackHandler(enabled = screen != root) { screen = root }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screen.title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    if (screen != root) TextButton(onClick = { screen = root }) { Text("‹ Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                listOf(
                    Triple(Screen.Inbox, Icons.Filled.Inbox, Icons.Outlined.Inbox),
                    Triple(Screen.Today, Icons.Filled.Today, Icons.Outlined.Today),
                    Triple(Screen.Upcoming, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
                    Triple(Screen.Browse, Icons.Filled.GridView, Icons.Outlined.GridView),
                ).forEach { (s, on, off) ->
                    val selected = root == s
                    NavigationBarItem(
                        selected = selected,
                        onClick = { root = s; screen = s },
                        icon = { Icon(if (selected) on else off, null) },
                        label = { Text(s.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Brand, selectedTextColor = Brand, indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            if (screen != Screen.Browse && screen != Screen.Completed) {
                FloatingActionButton(onClick = { adding = true }, containerColor = Brand, shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Filled.Add, "Add task", tint = Color.White)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (val s = screen) {
                Screen.Browse -> BrowseScreen(tasks, projects) { screen = it }
                else -> TaskListScreen(s, tasks, onToggle = ::toggle, onOpen = { editing = it.id })
            }
        }
    }

    if (adding) {
        QuickAddSheet(
            projects = projects,
            defaultProject = (screen as? Screen.Project)?.name,
            defaultToday = screen == Screen.Today,
            onDismiss = { adding = false },
        ) { text, desc, manual ->
            val t = repo.addFromText(text, desc, manual)
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar("Added “${t.title}”" + (t.due?.let { " · " + dueLabel(it, t.hasTime) } ?: ""))
            }
        }
    }

    editing?.let { id ->
        val task = tasks.firstOrNull { it.id == id }
        if (task == null) editing = null
        else EditTaskSheet(
            task = task,
            projects = projects,
            onDismiss = { editing = null },
            onSave = { repo.upsert(it) },
            onDelete = {
                editing = null
                repo.delete(id)
                scope.launch {
                    if (snackbar.showSnackbar("Deleted “${task.title}”", "Undo") == SnackbarResult.ActionPerformed) repo.upsert(task)
                }
            },
        )
    }
}

@Composable
private fun TaskListScreen(screen: Screen, all: List<Task>, onToggle: (Task) -> Unit, onOpen: (Task) -> Unit) {
    val now = LocalDateTime.now()
    val today = now.toLocalDate()
    val open = all.filter { !it.completed }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        if (screen == Screen.Today) item { PermissionBanner() }
        when (screen) {
            Screen.Today -> {
                val overdue = open.filter { t -> t.due != null && t.due.toLocalDate().isBefore(today) }.sortedWith(taskOrder)
                val todays = open.filter { t -> t.due?.toLocalDate() == today }.sortedWith(taskOrder)
                if (overdue.isNotEmpty()) section("Overdue", overdue, true, onToggle, onOpen)
                section(upcomingHeader(today), todays, true, onToggle, onOpen)
                if (overdue.isEmpty() && todays.isEmpty()) item { Empty("Nothing due today", "Tap + and type something like\n“call mom tomorrow 7pm” or “10 min timer”") }
            }
            Screen.Upcoming -> {
                val dated = open.filter { it.due != null }.sortedWith(taskOrder)
                val overdue = dated.filter { it.due!!.toLocalDate().isBefore(today) }
                if (overdue.isNotEmpty()) section("Overdue", overdue, true, onToggle, onOpen)
                dated.filter { !it.due!!.toLocalDate().isBefore(today) }
                    .groupBy { it.due!!.toLocalDate() }
                    .forEach { (d, list) -> section(upcomingHeader(d), list, true, onToggle, onOpen) }
                if (dated.isEmpty()) item { Empty("No upcoming tasks", "Tasks with a date show up here") }
            }
            Screen.Completed -> {
                val done = all.filter { it.completed }.sortedByDescending { it.completedAt ?: 0 }
                items(done, key = { it.id }) { TaskRow(it, true, { onToggle(it) }, { onOpen(it) }) }
                if (done.isEmpty()) item { Empty("No completed tasks yet", "") }
            }
            else -> {
                val list = when (screen) {
                    Screen.Inbox -> open.filter { it.project == Task.INBOX }
                    is Screen.Project -> open.filter { it.project == screen.name }
                    is Screen.Label -> open.filter { screen.name in it.labels }
                    else -> open
                }.sortedWith(taskOrder)
                items(list, key = { it.id }) { TaskRow(it, screen !is Screen.Project && screen != Screen.Inbox, { onToggle(it) }, { onOpen(it) }) }
                if (list.isEmpty()) item {
                    if (screen == Screen.Inbox) Empty("Your inbox is clear", "New tasks land here until the AI sorts them into a project")
                    else Empty("No tasks here", "")
                }
            }
        }
    }
}

private fun LazyListScope.section(title: String, list: List<Task>, showProject: Boolean, onToggle: (Task) -> Unit, onOpen: (Task) -> Unit) {
    item(key = "h-$title") { SectionHeader(title, list.size) }
    items(list, key = { it.id }) { TaskRow(it, showProject, { onToggle(it) }, { onOpen(it) }) }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (title == "Overdue") Brand else MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.width(8.dp))
        if (count > 0) Text("$count", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Empty(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        if (body.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(body, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
    }
}

@Composable
private fun BrowseScreen(tasks: List<Task>, projects: List<String>, onOpen: (Screen) -> Unit) {
    val open = tasks.filter { !it.completed }
    val labels = open.flatMap { it.labels }.groupingBy { it }.eachCount().toSortedMap()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { PermissionBanner() }
        item { SectionHeader("Projects", 0) }
        items(projects) { p ->
            BrowseRow(if (p == Task.INBOX) Icons.Outlined.Inbox else Icons.Outlined.Tag, p, open.count { it.project == p }) {
                onOpen(if (p == Task.INBOX) Screen.Inbox else Screen.Project(p))
            }
        }
        if (labels.isNotEmpty()) {
            item { SectionHeader("Labels", 0) }
            items(labels.keys.toList()) { l -> BrowseRow(Icons.AutoMirrored.Outlined.Label, l, labels[l] ?: 0) { onOpen(Screen.Label(l)) } }
        }
        item { SectionHeader("More", 0) }
        item { BrowseRow(Icons.Outlined.CheckCircle, "Completed", tasks.count { it.completed }) { onOpen(Screen.Completed) } }
        item {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Brand, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "AI: ${com.eddigits.eddido.ai.AiProvider.label}",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BrowseRow(icon: ImageVector, label: String, count: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(label, Modifier.weight(1f), fontSize = 16.sp)
        if (count > 0) Text("$count", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Nudges for the permissions alarms depend on; hidden once everything is granted. */
@Composable
private fun PermissionBanner() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val problems = remember(tick) { permissionProblems(ctx) }
    if (problems.isEmpty()) return
    Card(
        Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WarningAmber, null, tint = Brand)
                Spacer(Modifier.width(8.dp))
                Text("Reminders need permission", fontWeight = FontWeight.SemiBold)
            }
            problems.forEach { (label, intent) ->
                TextButton(onClick = { runCatching { ctx.startActivity(intent) } }) { Text(label) }
            }
        }
    }
}

private fun permissionProblems(ctx: Context): List<Pair<String, Intent>> {
    val out = mutableListOf<Pair<String, Intent>>()
    val pkg = Uri.parse("package:${ctx.packageName}")
    val nm = ctx.getSystemService(NotificationManager::class.java)
    if (!nm.areNotificationsEnabled()) {
        out += "Allow notifications" to Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
        out += "Allow exact alarms" to Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
    }
    if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {
        out += "Allow full-screen alarms" to Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
    }
    return out
}
