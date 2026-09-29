package com.eddigits.eddido.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eddigits.eddido.data.AppStore
import com.eddigits.eddido.model.FocusPhase
import com.eddigits.eddido.model.Kind
import com.eddigits.eddido.model.TimerItem
import com.eddigits.eddido.ui.motion.Motion
import com.eddigits.eddido.ui.motion.RollingText
import com.eddigits.eddido.ui.motion.confirm
import com.eddigits.eddido.ui.motion.tick
import kotlinx.coroutines.isActive
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin

/** Wall-clock millis, refreshed every frame while [running], so clocks move smoothly. */
@Composable
fun rememberNow(running: Boolean): State<Long> = produceState(System.currentTimeMillis(), running) {
    value = System.currentTimeMillis()
    while (running && isActive) {
        androidx.compose.runtime.withFrameMillis { value = System.currentTimeMillis() }
    }
}

@Composable
private fun EmptyState(kind: Kind, title: String, body: String, extra: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(kind.icon, null, tint = kind.accent.copy(alpha = 0.8f), modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Spacer(Modifier.height(6.dp))
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        extra()
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, color: Color, big: Boolean = false, onClick: () -> Unit) {
    val view = LocalView.current
    FilledIconButton(
        onClick = { view.tick(); onClick() },
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = color),
        modifier = Modifier.size(if (big) 72.dp else 52.dp),
    ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(if (big) 34.dp else 24.dp)) }
}

// ── Timer ──

@Composable
fun TimerTab(store: AppStore) {
    val timers by store.timers.collectAsState()
    val now by rememberNow(timers.any { it.running })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
        if (timers.isEmpty()) item {
            EmptyState(Kind.TIMER, "No timers", "Tap + and type “10 min timer for tea”, or start one here:") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 5, 10, 25).forEach { m ->
                        AssistChip(onClick = { store.startTimer("", m * 60_000L) }, label = { Text("$m min") })
                    }
                }
            }
        }
        items(timers, key = { it.id }) { t -> TimerCard(t, now, store, Modifier.animateItem()) }
    }
}

@Composable
private fun TimerCard(t: TimerItem, now: Long, store: AppStore, modifier: Modifier) {
    val left = t.remainingAt(now)
    val accent by animateColorAsState(if (t.finished) Kind.TASK.accent else Kind.TIMER.accent, Motion.settle(), label = "timerColor")
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ring(if (t.durationMs == 0L) 0f else left / t.durationMs.toFloat(), accent, size = 96.dp, stroke = 8.dp) {
            Text(if (t.finished) "Done" else formatClock((left + 999) / 1000), fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(t.label.ifBlank { "Timer" }, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text(formatClock(t.durationMs / 1000) + when { t.finished -> " · finished"; t.running -> " · running"; else -> " · paused" }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { store.addMinute(t.id) }) { Text("+1 min") }
                IconButton(onClick = { store.resetTimer(t.id) }) { Icon(Icons.Outlined.Refresh, "Reset") }
                IconButton(onClick = { store.deleteTimer(t.id) }) { Icon(Icons.Outlined.Delete, "Delete") }
            }
        }
        if (!t.finished) {
            RoundButton(if (t.running) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (t.running) "Pause" else "Start", Kind.TIMER.accent) {
                if (t.running) store.pauseTimer(t.id) else store.resumeTimer(t.id)
            }
        }
    }
}

// ── Stopwatch ──

@Composable
fun StopwatchTab(store: AppStore) {
    val sw by store.stopwatch.collectAsState()
    val now by rememberNow(sw.running)
    val elapsed = sw.elapsedAt(now)
    val accent = Kind.STOPWATCH.accent
    val track = MaterialTheme.colorScheme.surfaceVariant
    val view = LocalView.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Box(Modifier.padding(top = 24.dp).size(270.dp), contentAlignment = Alignment.Center) {
                // A dot sweeps the ring once a minute, like a second hand.
                Canvas(Modifier.size(270.dp)) {
                    val s = 10.dp.toPx()
                    val r = size.minDimension / 2 - s / 2
                    drawCircle(track, r, style = Stroke(s))
                    val frac = (elapsed % 60_000) / 60_000f
                    drawArc(accent, -90f, 360f * frac, false, Offset(s / 2, s / 2), androidx.compose.ui.geometry.Size(size.width - s, size.height - s), style = Stroke(s, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    val rad = Math.toRadians(360.0 * frac - 90)
                    drawCircle(Color.White, s * 0.55f, Offset(center.x + r * cos(rad).toFloat(), center.y + r * sin(rad).toFloat()))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val cs = (elapsed / 10) % 100
                    Text(formatClock(elapsed / 1000), style = BigDigits.copy(fontSize = 58.sp))
                    Text(".%02d".format(cs), fontSize = 22.sp, color = accent)
                    if (sw.label.isNotBlank()) Text(sw.label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedIconButton(
                    onClick = { view.tick(); if (sw.running) store.lap() else store.resetStopwatch() },
                    enabled = sw.running || elapsed > 0,
                    modifier = Modifier.size(56.dp),
                ) { Icon(if (sw.running) Icons.Outlined.Flag else Icons.Outlined.Refresh, if (sw.running) "Lap" else "Reset") }
                RoundButton(if (sw.running) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (sw.running) "Pause" else "Start", accent, big = true) {
                    store.toggleStopwatch()
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        val laps = sw.laps
        if (laps.isNotEmpty()) {
            val splits = laps.mapIndexed { i, t -> t - (if (i == 0) 0 else laps[i - 1]) }
            val best = splits.minOrNull()
            val worst = splits.maxOrNull()
            itemsIndexed(splits.reversed(), key = { i, _ -> laps.size - i }) { i, split ->
                val n = laps.size - i
                val c = when {
                    splits.size < 2 -> MaterialTheme.colorScheme.onSurface
                    split == best -> Color(0xFF25B84C)
                    split == worst -> Kind.TASK.accent
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp).animateItem()) {
                    Text("Lap $n", color = c, modifier = Modifier.weight(1f))
                    Text(formatClock(split / 1000) + ".%02d".format((split / 10) % 100), color = c)
                }
            }
        }
    }
}

// ── Focus ──

@Composable
fun FocusTab(store: AppStore) {
    val s by store.focus.collectAsState()
    val log by store.focusLog.collectAsState()
    val now by rememberNow(s?.running == true)
    val view = LocalView.current
    LaunchedEffect(s?.phase, s?.round) { if (s != null) view.confirm() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            val session = s
            if (session == null || session.done) {
                EmptyState(
                    Kind.FOCUS,
                    if (session?.done == true) "Session complete 🎉" else "Ready to focus?",
                    "Tap + and type “focus 25 min on report”, or pick one:",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { store.startFocus("", 25, 5) }, label = { Text("25 / 5") })
                        AssistChip(onClick = { store.startFocus("", 50, 10) }, label = { Text("50 / 10") })
                        AssistChip(onClick = { store.startFocus("", 90, 15, rounds = 2) }, label = { Text("90 / 15") })
                    }
                }
            } else {
                val onBreak = session.phase == FocusPhase.BREAK
                val color by animateColorAsState(if (onBreak) Color(0xFF25B84C) else Kind.FOCUS.accent, Motion.settle(), label = "focusColor")
                val left = session.remainingAt(now)
                // Whole-session progress, shown as one segment per round.
                val roundsDone = session.round - 1 + if (onBreak) 1f else 1f - left / session.phaseLengthMs.toFloat()
                Spacer(Modifier.height(16.dp))
                Text(if (onBreak) "Break" else "Focus", color = color, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Ring(roundsDone / session.rounds, color, size = 260.dp, segments = session.rounds) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RollingText(formatClock((left + 999) / 1000), BigDigits, countingDown = true)
                        Text("Round ${session.round} of ${session.rounds}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
                if (session.label.isNotBlank()) {
                    Spacer(Modifier.height(10.dp)); Text(session.label, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedIconButton(onClick = { store.stopFocus() }, modifier = Modifier.size(52.dp)) { Icon(Icons.Filled.Stop, "Stop") }
                    RoundButton(if (session.running) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Pause", color, big = true) { store.toggleFocus() }
                    OutlinedIconButton(onClick = { store.skipFocusPhase() }, modifier = Modifier.size(52.dp)) { Icon(Icons.Filled.SkipNext, "Skip") }
                }
            }
        }
        item {
            val week = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
            val mins = week.map { d -> log.filter { it.date == d }.sumOf { it.minutes } }
            val max = (mins.maxOrNull() ?: 0).coerceAtLeast(25)
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text("Today: ${mins.last()} min focused", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    week.forEachIndexed { i, d ->
                        val h by animateFloatAsState(mins[i] / max.toFloat(), Motion.number(), label = "bar$i")
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.width(22.dp).height((70 * h).dp.coerceAtLeast(3.dp)).clip(RoundedCornerShape(6.dp)).background(Kind.FOCUS.accent.copy(alpha = if (i == 6) 1f else 0.5f)))
                            Text(d.dayOfWeek.name.take(1), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
