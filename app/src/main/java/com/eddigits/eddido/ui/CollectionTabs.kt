package com.eddigits.eddido.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eddigits.eddido.data.AppStore
import com.eddigits.eddido.data.TaskRepository
import com.eddigits.eddido.model.CheckList
import com.eddigits.eddido.model.Countdown
import com.eddigits.eddido.model.Kind
import com.eddigits.eddido.model.Note
import com.eddigits.eddido.model.Task
import com.eddigits.eddido.ui.motion.Motion
import com.eddigits.eddido.ui.motion.confirm
import com.eddigits.eddido.ui.motion.tick
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
private fun Empty(kind: Kind, title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(kind.icon, null, tint = kind.accent.copy(alpha = 0.8f), modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Spacer(Modifier.height(6.dp))
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

private fun Modifier.card() = this
    .fillMaxWidth()
    .padding(horizontal = 12.dp, vertical = 6.dp)

// ── Habits ──

@Composable
fun HabitsTab(repo: TaskRepository, onEdit: (Task) -> Unit) {
    val tasks by repo.tasks.collectAsState()
    val habits = tasks.filter { it.habit && !it.completed }.sortedBy { it.createdAt }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
        if (habits.isEmpty()) item {
            Empty(Kind.HABIT, "No habits yet", "Tap + and type “drink water 8 times a day” or “meditate every morning”.\nAny repeating task can also be tracked as a habit from its edit screen.")
        }
        items(habits, key = { it.id }) { h -> HabitCard(h, repo, onEdit, Modifier.animateItem()) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HabitCard(h: Task, repo: TaskRepository, onEdit: (Task) -> Unit, modifier: Modifier) {
    val view = LocalView.current
    val today = LocalDate.now()
    val done = h.doneOn(today)
    val complete = done >= h.perDay
    val accent = Kind.HABIT.accent
    val bump = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Row(
        modifier
            .card()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = { onEdit(h) })
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(48.dp)) {
            val flame by animateColorAsState(if (h.streak() > 0) accent else MaterialTheme.colorScheme.onSurfaceVariant, Motion.settle(), label = "flame")
            Icon(Icons.Filled.LocalFireDepartment, null, tint = flame, modifier = Modifier.size(28.dp))
            Text("${h.streak()}", fontWeight = FontWeight.Bold, color = flame)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(h.title, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                (h.recurrence?.label() ?: "Daily") + if (h.perDay > 1) " · $done / ${h.perDay} today" else "",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            // Last seven days: filled when the daily target was met.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (6 downTo 0).forEach { back ->
                    val d = today.minusDays(back.toLong())
                    val met = h.doneOn(d) >= h.perDay
                    val c by animateColorAsState(if (met) accent else Color.Transparent, Motion.settle(), label = "dot")
                    Box(Modifier.size(14.dp).clip(CircleShape).background(c).border(1.5.dp, accent.copy(alpha = if (met) 1f else 0.45f), CircleShape))
                }
            }
        }
        Ring((done / h.perDay.toFloat()), accent, size = 56.dp, stroke = 5.dp) {
            Box(
                Modifier
                    .size(44.dp)
                    .graphicsLayer { scaleX = bump.value; scaleY = bump.value }
                    .clip(CircleShape)
                    .background(if (complete) accent else Color.Transparent)
                    .clickable {
                        if (complete) {
                            repo.habitCheckIn(h.id, -h.perDay)
                            view.tick()
                        } else {
                            repo.habitCheckIn(h.id, 1)
                            if (done + 1 >= h.perDay) view.confirm() else view.tick()
                            scope.launch { bump.snapTo(0.7f); bump.animateTo(1f, Motion.pop()) }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (complete) Icon(Icons.Filled.Check, "Done", tint = Color.White)
                else Text(if (h.perDay > 1) "+1" else "", fontWeight = FontWeight.SemiBold, color = accent)
            }
        }
    }
}

// ── Lists ──

@Composable
fun ListsTab(store: AppStore) {
    val lists by store.lists.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
        if (lists.isEmpty()) item { Empty(Kind.LIST, "No lists yet", "Tap + and type “buy milk, eggs and bread”.") }
        items(lists, key = { it.id }) { l -> ListCard(l, store, Modifier.animateItem()) }
    }
}

@Composable
private fun ListCard(l: CheckList, store: AppStore, modifier: Modifier) {
    val accent = Kind.LIST.accent
    val view = LocalView.current
    var adding by remember { mutableStateOf("") }
    Column(
        modifier
            .card()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
            .animateContentSize(Motion.settle()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(l.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = accent, modifier = Modifier.weight(1f))
            Text("${l.items.count { it.checked }}/${l.items.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            IconButton(onClick = { store.clearChecked(l.id) }, enabled = l.items.any { it.checked }) { Icon(Icons.Outlined.DeleteSweep, "Clear ticked") }
            IconButton(onClick = { store.deleteList(l.id) }) { Icon(Icons.Outlined.Delete, "Delete list") }
        }
        // Unticked first; ticked ones sink to the bottom.
        l.items.sortedBy { it.checked }.forEach { item ->
            androidx.compose.runtime.key(item.id) {
                val fill by animateColorAsState(if (item.checked) accent else Color.Transparent, Motion.settle(), label = "tick")
                val fade by animateFloatAsState(if (item.checked) 0.5f else 1f, Motion.settle(), label = "fade")
                Row(
                    Modifier.fillMaxWidth().clickable { view.tick(); store.toggleItem(l.id, item.id) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(fill).border(2.dp, accent, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                        if (item.checked) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        item.text, fontSize = 16.sp, modifier = Modifier.weight(1f).graphicsLayer { alpha = fade },
                        textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                    )
                }
            }
        }
        TextField(
            value = adding,
            onValueChange = { adding = it },
            placeholder = { Text("Add item") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                if (adding.isNotBlank()) { store.addItem(l.id, adding.trim().replaceFirstChar { it.uppercase() }); adding = "" }
            }),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
    }
}

// ── Countdown ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownTab(store: AppStore) {
    val items by store.countdowns.collectAsState()
    var editing by remember { mutableStateOf<Countdown?>(null) }
    val today = LocalDate.now()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
        if (items.isEmpty()) item { Empty(Kind.COUNTDOWN, "Nothing to count down to", "Tap + and type “days until Diwali” or “countdown to my birthday 14 feb”.") }
        items(items.sortedBy { it.date }, key = { it.id }) { c ->
            val days = ChronoUnit.DAYS.between(today, c.date).toInt()
            val shown = remember(c.id) { Animatable(0f) }
            LaunchedEffect(days) { shown.animateTo(days.toFloat(), androidx.compose.animation.core.tween(900)) }
            Row(
                Modifier.animateItem().card().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { editing = c }.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
                    Text(
                        when { days == 0 -> "🎉"; days < 0 -> "${-days}"; else -> "${shown.value.toInt()}" },
                        fontSize = 40.sp, fontWeight = FontWeight.Bold, color = if (days < 0) MaterialTheme.colorScheme.onSurfaceVariant else Kind.COUNTDOWN.accent,
                    )
                    Text(when { days == 0 -> "today"; days < 0 -> "days ago"; days == 1 -> "day"; else -> "days" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text(c.date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        }
    }
    editing?.let { c ->
        var title by remember(c.id) { mutableStateOf(c.title) }
        var pickDate by remember(c.id) { mutableStateOf(false) }
        var date by remember(c.id) { mutableStateOf(c.date) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Countdown") },
            text = {
                Column {
                    OutlinedTextField(title, { title = it }, singleLine = true, label = { Text("Event") })
                    TextButton(onClick = { pickDate = true }) { Text(date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH))) }
                }
            },
            confirmButton = { TextButton(onClick = { store.updateCountdown(c.copy(title = title.trim().ifEmpty { c.title }, date = date)); editing = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { store.deleteCountdown(c.id); editing = null }) { Text("Delete") } },
        )
        if (pickDate) {
            val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
            DatePickerDialog(
                onDismissRequest = { pickDate = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        pickDate = false
                    }) { Text("OK") }
                },
            ) { DatePicker(state) }
        }
    }
}

// ── Notes ──

@Composable
fun NotesTab(store: AppStore) {
    val notes by store.notes.collectAsState()
    var editing by remember { mutableStateOf<Note?>(null) }
    val sorted = notes.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
    if (sorted.isEmpty()) {
        Empty(Kind.NOTE, "No notes", "Tap + and type anything worth keeping, like “idea: …” or “wifi password is …”.")
    } else {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 110.dp),
            verticalItemSpacing = 10.dp,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(sorted, key = { it.id }) { n ->
                val yellow = Kind.NOTE.accent
                Box(
                    Modifier
                        .animateItem()
                        .rotate(if (n.id % 2 == 0L) -1f else 1f)
                        .clip(RoundedCornerShape(4.dp, 4.dp, 20.dp, 4.dp))
                        .background(yellow.copy(alpha = 0.18f))
                        .border(1.dp, yellow.copy(alpha = if (n.pinned) 0.9f else 0.35f), RoundedCornerShape(4.dp, 4.dp, 20.dp, 4.dp))
                        .clickable { editing = n }
                        .padding(14.dp),
                ) {
                    Text(n.text, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 12, overflow = TextOverflow.Ellipsis)
                    if (n.pinned) Icon(Icons.Filled.PushPin, "Pinned", tint = yellow, modifier = Modifier.size(14.dp).align(Alignment.TopEnd))
                }
            }
        }
    }
    editing?.let { n ->
        var text by remember(n.id) { mutableStateOf(n.text) }
        AlertDialog(
            onDismissRequest = { store.updateNote(n.id, text); editing = null },
            text = {
                OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().height(260.dp))
            },
            confirmButton = { TextButton(onClick = { store.updateNote(n.id, text); editing = null }) { Text("Save") } },
            dismissButton = {
                Row {
                    IconButton(onClick = { store.togglePin(n.id); editing = null }) {
                        Icon(if (n.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, "Pin")
                    }
                    IconButton(onClick = { store.deleteNote(n.id); editing = null }) { Icon(Icons.Outlined.Delete, "Delete") }
                }
            },
        )
    }
}
