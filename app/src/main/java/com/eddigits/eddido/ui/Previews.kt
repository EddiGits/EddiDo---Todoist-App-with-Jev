package com.eddigits.eddido.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eddigits.eddido.data.Draft
import com.eddigits.eddido.model.Kind
import com.eddigits.eddido.model.ReminderKind
import com.eddigits.eddido.ui.motion.Motion
import com.eddigits.eddido.ui.motion.RollingText
import com.eddigits.eddido.ui.theme.priorityColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** The big live preview in the creator, one per tab. */
@Composable
fun DraftPreview(draft: Draft, text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        when (draft.kind) {
            Kind.TASK -> TaskPreview(draft, habit = false)
            Kind.HABIT -> HabitPreview(draft)
            Kind.TIMER -> TimerPreview(draft)
            Kind.STOPWATCH -> StopwatchPreview(draft)
            Kind.FOCUS -> FocusPreview(draft)
            Kind.LIST -> ListPreview(draft)
            Kind.COUNTDOWN -> CountdownPreview(draft)
            Kind.NOTE -> NotePreview(draft, text)
        }
    }
}

val BigDigits = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp)

fun formatClock(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
private fun Placeholder(s: String) = Text(s, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)

@Composable
private fun AiMark(on: Boolean) {
    if (on) Icon(Icons.Filled.AutoAwesome, "AI", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp).size(12.dp))
}

// ── Task ──

@Composable
private fun TaskPreview(draft: Draft, habit: Boolean) {
    val t = draft.task
    val accent = if (habit) Kind.HABIT.accent else Kind.TASK.accent
    Column(
        Modifier
            .padding(24.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(20.dp)
            .animateContentSize(Motion.settle()),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 3.dp).size(22.dp).border(2.dp, priorityColor(t.priority), CircleShape))
            Spacer(Modifier.width(12.dp))
            Row(Modifier.weight(1f)) {
                Text(
                    t.title.ifBlank { "Type what to do…" }, fontSize = 20.sp, fontWeight = FontWeight.Medium,
                    color = if (t.title.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                AiMark("title" in t.aiDerived)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            t.due?.let { d ->
                Meta(Icons.Outlined.CalendarToday, dueLabel(d, t.hasTime), dueColor(d, t.hasTime), "date" in t.aiDerived)
            }
            t.recurrence?.let { Meta(Icons.Outlined.Repeat, it.label(), MaterialTheme.colorScheme.onSurfaceVariant, false) }
            if (t.priority < 4) Meta(Icons.Filled.Flag, "P${t.priority}", priorityColor(t.priority), "priority" in t.aiDerived)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("# ${t.project}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            AiMark("project" in t.aiDerived)
            if (t.reminder != ReminderKind.NONE) {
                Spacer(Modifier.width(12.dp))
                Text(if (t.reminder == ReminderKind.ALARM) "⏰ Alarm" else "🔔 Reminder", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        if (t.labels.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(t.labels.joinToString("  ") { "@$it" }, color = accent, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Meta(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color, ai: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = color, fontSize = 14.sp)
        AiMark(ai)
    }
}

// ── Rings ──

/** A ring whose filled share animates with a spring; optional gaps split it into rounds. */
@Composable
fun Ring(
    fraction: Float,
    color: Color,
    size: Dp = 240.dp,
    stroke: Dp = 12.dp,
    segments: Int = 1,
    content: @Composable () -> Unit,
) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), Motion.number(), label = "ring")
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) { drawRing(f, color, track, stroke.toPx(), segments) }
        content()
    }
}

fun DrawScope.drawRing(fraction: Float, color: Color, track: Color, strokePx: Float, segments: Int = 1) {
    val inset = strokePx / 2
    val arcSize = Size(size.width - strokePx, size.height - strokePx)
    val gap = if (segments > 1) 6f else 0f
    val seg = 360f / segments
    for (i in 0 until segments) {
        drawArc(track, -90f + i * seg + gap / 2, seg - gap, false, Offset(inset, inset), arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
    }
    val sweep = 360f * fraction
    for (i in 0 until segments) {
        val start = i * seg
        val filled = (sweep - start).coerceIn(0f, seg)
        if (filled > 0f) {
            drawArc(color, -90f + start + gap / 2, (filled - gap).coerceAtLeast(0.1f), false, Offset(inset, inset), arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
        }
    }
}

// ── Timer ──

@Composable
private fun TimerPreview(draft: Draft) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // One lap of the ring is an hour, like a kitchen timer's dial.
        Ring(draft.durationSeconds / 3600f, Kind.TIMER.accent) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RollingText(formatClock(draft.durationSeconds.toLong()), BigDigits, countingDown = false)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if ("duration" in draft.aiDetails) "set by AI" else "timer", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    AiMark("duration" in draft.aiDetails)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (draft.label.isNotBlank()) Text(draft.label, fontSize = 20.sp, fontWeight = FontWeight.Medium) else Placeholder("What's it for? (optional)")
    }
}

// ── Stopwatch ──

@Composable
private fun StopwatchPreview(draft: Draft) {
    val spin = rememberInfiniteTransition(label = "sw")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = androidx.compose.animation.core.LinearEasing)), label = "orbit")
    val accent = Kind.STOPWATCH.accent
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(240.dp)) {
                val s = 12.dp.toPx()
                drawCircle(track, radius = size.minDimension / 2 - s / 2, style = Stroke(s))
                val r = size.minDimension / 2 - s / 2
                val rad = Math.toRadians(angle.toDouble() - 90)
                drawCircle(accent, radius = s * 0.9f, center = Offset(center.x + r * cos(rad).toFloat(), center.y + r * sin(rad).toFloat()))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("00:00.00", style = BigDigits.copy(fontSize = 52.sp))
                Text("ready to start", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (draft.label.isNotBlank()) Text(draft.label, fontSize = 20.sp, fontWeight = FontWeight.Medium) else Placeholder("Starts the moment you accept")
    }
}

// ── Focus ──

@Composable
private fun FocusPreview(draft: Draft) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Ring(1f, Kind.FOCUS.accent, segments = 4) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RollingText(formatClock(draft.focusMinutes * 60L), BigDigits)
                Text("4 rounds · ${draft.breakMinutes} min breaks", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (draft.label.isNotBlank()) Text(draft.label, fontSize = 20.sp, fontWeight = FontWeight.Medium) else Placeholder("What will you focus on?")
    }
}

// ── Habit ──

@Composable
private fun HabitPreview(draft: Draft) {
    val pulse = rememberInfiniteTransition(label = "flame")
    val scale by pulse.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "flameScale")
    val accent = Kind.HABIT.accent
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Icon(
            Icons.Filled.LocalFireDepartment, null, tint = accent,
            modifier = Modifier.size(88.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        )
        Spacer(Modifier.height(8.dp))
        Text(draft.task.title.ifBlank { "Name your habit" }, fontSize = 22.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            (draft.task.recurrence?.label() ?: "Daily") + if (draft.perDay > 1) " · ${draft.perDay}× a day" else "",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
        )
        Spacer(Modifier.height(16.dp))
        // One dot per daily check-in, popping in one after another.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(draft.perDay.coerceAtMost(12)) { i ->
                val state = remember(draft.perDay) { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(state, enter = scaleIn(Motion.pop(), 0.2f) + fadeIn(tween(160, delayMillis = i * 60))) {
                    Box(Modifier.size(14.dp).border(2.dp, accent, CircleShape))
                }
            }
        }
    }
}

// ── List ──

@Composable
private fun ListPreview(draft: Draft) {
    val accent = Kind.LIST.accent
    Column(
        Modifier
            .padding(24.dp)
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(20.dp)
            .animateContentSize(Motion.settle()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(draft.listName, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = accent)
            AiMark("listName" in draft.aiDetails)
            Spacer(Modifier.weight(1f))
            Text("${draft.items.size} items", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        Spacer(Modifier.height(10.dp))
        if (draft.items.isEmpty()) Placeholder("Separate items with commas")
        draft.items.forEachIndexed { i, item ->
            // Keyed by text, so each item drops in once as it appears.
            val state = remember(item) { MutableTransitionState(false).apply { targetState = true } }
            AnimatedVisibility(
                state,
                enter = slideInVertically(Motion.settle()) { -it / 2 } + expandVertically(Motion.settle()) + fadeIn(tween(180, delayMillis = (i * 40).coerceAtMost(240))),
            ) {
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(20.dp).border(2.dp, accent, RoundedCornerShape(6.dp)))
                    Spacer(Modifier.width(12.dp))
                    Text(item, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ── Countdown ──

@Composable
private fun CountdownPreview(draft: Draft) {
    val date = draft.countdownDate
    val days = date?.let { ChronoUnit.DAYS.between(LocalDate.now(), it).toInt() } ?: 0
    // Counts up from zero each time the date changes.
    val shown by animateIntAsState(days, tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "days")
    val accent = Kind.COUNTDOWN.accent
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        if (date == null) {
            Text("?", fontSize = 96.sp, fontWeight = FontWeight.Bold, color = accent.copy(alpha = 0.4f))
            Placeholder("Add a date, e.g. “days until 14 feb”")
        } else {
            Text(if (days == 0) "Today!" else "$shown", fontSize = 104.sp, fontWeight = FontWeight.Bold, color = accent, letterSpacing = (-2).sp)
            Text(if (days == 1) "day to go" else if (days == 0) "" else "days to go", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text(draft.label.ifBlank { "What are you counting down to?" }, fontSize = 22.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        date?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(it.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                AiMark("date" in draft.task.aiDerived)
            }
        }
    }
}

// ── Note ──

@Composable
private fun NotePreview(draft: Draft, text: String) {
    val yellow = Kind.NOTE.accent
    Box(
        Modifier
            .padding(32.dp)
            .rotate(-2f)
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp, 4.dp, 24.dp, 4.dp))
            .background(yellow.copy(alpha = 0.22f))
            .border(1.dp, yellow.copy(alpha = 0.5f), RoundedCornerShape(4.dp, 4.dp, 24.dp, 4.dp))
            .padding(22.dp)
            .animateContentSize(Motion.settle()),
    ) {
        Text(
            draft.label.ifBlank { text }.ifBlank { "Jot it down…" },
            fontSize = 19.sp, lineHeight = 26.sp,
            color = if (text.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}
