package com.eddigits.eddido.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.eddigits.eddido.data.AppStore
import com.eddigits.eddido.model.Kind
import com.eddigits.eddido.ui.motion.LocalReducedMotion
import com.eddigits.eddido.ui.motion.Motion
import com.eddigits.eddido.ui.motion.confirm
import com.eddigits.eddido.ui.motion.reducedMotion
import com.eddigits.eddido.ui.motion.tick
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

@Composable
fun EddiDoApp(repo: TaskRepository, store: AppStore) {
    val tasks by repo.tasks.collectAsState()
    val projects by repo.projects.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Kind.TASK) }
    var screen by remember { mutableStateOf<Screen>(Screen.Today) }
    var root by remember { mutableStateOf<Screen>(Screen.Today) }
    var composing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Long?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val reduce = remember { reducedMotion(view) }
    var fabCenter by remember { mutableStateOf(Offset.Zero) }
    val reveal = remember { Animatable(0f) }

    fun toggle(t: Task) {
        val before = t
        val after = repo.complete(t.id, !t.completed) ?: return
        if (!before.completed) {
            view.confirm()
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val msg = if (after.completed) "Completed" else "Next: ${after.due?.let { dueLabel(it, after.hasTime) }}"
                if (snackbar.showSnackbar(msg, "Undo", withDismissAction = false) == SnackbarResult.ActionPerformed) repo.upsert(before)
            }
        }
    }

    fun openComposer() {
        composing = true
        scope.launch { reveal.animateTo(1f, if (reduce) tween(120) else tween(440, easing = FastOutSlowInEasing)) }
    }

    fun closeComposer(then: () -> Unit = {}) {
        scope.launch {
            reveal.animateTo(0f, if (reduce) tween(100) else tween(320, easing = FastOutSlowInEasing))
            composing = false
            then()
        }
    }

    androidx.activity.compose.BackHandler(enabled = composing) { closeComposer() }
    androidx.activity.compose.BackHandler(enabled = !composing && tab == Kind.TASK && screen != root) { screen = root }

    CompositionLocalProvider(LocalReducedMotion provides reduce) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
                Column(Modifier.padding(pad).fillMaxSize()) {
                    KindTabs(tab, onSelect = { view.tick(); tab = it }, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    // Tabs slide in from the side you moved towards.
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            if (reduce) fadeIn(Motion.fade()) togetherWith fadeOut(Motion.fade())
                            else {
                                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                                (slideInHorizontally(Motion.morph()) { it / 3 * dir } + fadeIn(Motion.fade())) togetherWith
                                    (slideOutHorizontally(Motion.exit()) { -it / 5 * dir } + fadeOut(Motion.exit()))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        label = "tab",
                    ) { k ->
                        when (k) {
                            Kind.TASK -> TasksTab(
                                screen = screen,
                                root = root,
                                tasks = tasks,
                                projects = projects,
                                onRoot = { root = it; screen = it },
                                onScreen = { screen = it },
                                onToggle = ::toggle,
                                onOpen = { editing = it.id },
                            )
                            Kind.TIMER -> TimerTab(store)
                            Kind.STOPWATCH -> StopwatchTab(store)
                            Kind.FOCUS -> FocusTab(store)
                            Kind.HABIT -> HabitsTab(repo) { editing = it.id }
                            Kind.LIST -> ListsTab(store)
                            Kind.COUNTDOWN -> CountdownTab(store)
                            Kind.NOTE -> NotesTab(store)
                        }
                    }
                }
            }

            val fabColor by animateColorAsState(tab.accent, Motion.settle(), label = "fab")
            val fabScale by animateFloatAsState(if (composing) 0.6f else 1f, Motion.pop(), label = "fabScale")
            FloatingActionButton(
                onClick = { view.tick(); openComposer() },
                containerColor = fabColor,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(20.dp)
                    .graphicsLayer { scaleX = fabScale; scaleY = fabScale }
                    .onGloballyPositioned { fabCenter = it.boundsInRoot().center },
            ) { Icon(Icons.Filled.Add, "Create", tint = Color.White) }

            // The creator grows out of the + button as a circle, and shrinks back into it.
            if (composing) {
                val progress = reveal.value
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            clip = true
                            shape = CircleRevealShape(progress, fabCenter)
                            alpha = (progress * 3f).coerceAtMost(1f)
                        },
                ) {
                    Composer(
                        startKind = tab,
                        repo = repo,
                        store = store,
                        projects = projects,
                        fallbackToday = tab == Kind.TASK && screen == Screen.Today,
                        onClose = { closeComposer() },
                        onCreated = { kind, label ->
                            closeComposer {
                                tab = kind
                                scope.launch {
                                    snackbar.currentSnackbarData?.dismiss()
                                    snackbar.showSnackbar(label)
                                }
                            }
                        },
                    )
                }
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

/** A circle centred on [origin] whose radius grows with [progress] until it covers the screen. */
private class CircleRevealShape(private val progress: Float, private val origin: Offset) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val o = if (origin == Offset.Zero) Offset(size.width, size.height) else origin
        val far = listOf(Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height))
            .maxOf { (it - o).getDistance() }
        val r = far * progress
        return Outline.Generic(Path().apply { addOval(Rect(o, r)) })
    }
}

/** The Tasks tab: Today / Upcoming / Inbox / Browse, switched with a small segmented row. */
@Composable
private fun TasksTab(
    screen: Screen,
    root: Screen,
    tasks: List<Task>,
    projects: List<String>,
    onRoot: (Screen) -> Unit,
    onScreen: (Screen) -> Unit,
    onToggle: (Task) -> Unit,
    onOpen: (Task) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (screen != root) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onScreen(root) }) { Text("‹ Back") }
                Text(screen.title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(Screen.Today, Screen.Upcoming, Screen.Inbox, Screen.Browse).forEach { s ->
                    val on = s == root
                    val bg by animateColorAsState(if (on) Kind.TASK.accent.copy(alpha = 0.16f) else Color.Transparent, Motion.settle(), label = "seg")
                    val fg by animateColorAsState(if (on) Kind.TASK.accent else MaterialTheme.colorScheme.onSurfaceVariant, Motion.settle(), label = "segText")
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(bg).clickable { onRoot(s) }.padding(horizontal = 14.dp, vertical = 7.dp),
                    ) { Text(s.title, color = fg, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) }
                }
            }
        }
        Box(Modifier.weight(1f)) {
            when (screen) {
                Screen.Browse -> BrowseScreen(tasks, projects, onScreen)
                else -> TaskListScreen(screen, tasks, onToggle = onToggle, onOpen = onOpen)
            }
        }
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
                items(done, key = { it.id }) { TaskRow(it, true, { onToggle(it) }, { onOpen(it) }, Modifier.animateItem()) }
                if (done.isEmpty()) item { Empty("No completed tasks yet", "") }
            }
            else -> {
                val list = when (screen) {
                    Screen.Inbox -> open.filter { it.project == Task.INBOX }
                    is Screen.Project -> open.filter { it.project == screen.name }
                    is Screen.Label -> open.filter { screen.name in it.labels }
                    else -> open
                }.sortedWith(taskOrder)
                items(list, key = { it.id }) { TaskRow(it, screen !is Screen.Project && screen != Screen.Inbox, { onToggle(it) }, { onOpen(it) }, Modifier.animateItem()) }
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
    items(list, key = { it.id }) { TaskRow(it, showProject, { onToggle(it) }, { onOpen(it) }, Modifier.animateItem()) }
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
    // Preview renders have no notification or alarm services to ask.
    if (androidx.compose.ui.platform.LocalInspectionMode.current) return
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
