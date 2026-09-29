package com.eddigits.eddido.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eddigits.eddido.ai.AiProvider
import com.eddigits.eddido.ai.AiResult
import com.eddigits.eddido.data.AppStore
import com.eddigits.eddido.data.Draft
import com.eddigits.eddido.data.DraftBuilder
import com.eddigits.eddido.data.KindDecider
import com.eddigits.eddido.data.ManualChoices
import com.eddigits.eddido.data.TaskRepository
import com.eddigits.eddido.model.Kind
import com.eddigits.eddido.parse.KindGuess
import com.eddigits.eddido.parse.QuickAddParser
import com.eddigits.eddido.ui.motion.Burst
import com.eddigits.eddido.ui.motion.LocalReducedMotion
import com.eddigits.eddido.ui.motion.Motion
import com.eddigits.eddido.ui.motion.confirm
import com.eddigits.eddido.ui.motion.tick
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * The full-screen creator. Type at the bottom; as you pause, Jev picks the tab and the
 * highlight slides there while the preview above morphs into what you are making.
 * Nothing is created until you press the accept button.
 */
@Composable
fun Composer(
    startKind: Kind,
    repo: TaskRepository,
    store: AppStore,
    projects: List<String>,
    fallbackToday: Boolean,
    onClose: () -> Unit,
    onCreated: (Kind, String) -> Unit,
) {
    val view = LocalView.current
    val reduce = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(ManualChoices()) }
    var decider by remember { mutableStateOf(KindDecider.State(kind = startKind, ghost = true)) }
    var jev by remember { mutableStateOf<Pair<String, AiResult>?>(null) }
    var language by remember { mutableStateOf<Pair<String, AiResult>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var burst by remember { mutableIntStateOf(0) }
    var accepting by remember { mutableStateOf(false) }

    // Ask Jev once typing pauses (~0.4–0.8 s): one call picks the tab and the meaning
    // (which day, part of day, repeat…), and code turns that into dates and a title.
    LaunchedEffect(text) {
        val t = text.trim()
        if (t.length < 2) { busy = false; decider = KindDecider.State(kind = decider.kind, ghost = true); return@LaunchedEffect }
        // Shapeshift waits ~120 ms; one cheap Jev call per pause, cached per text.
        delay(120)
        busy = true
        coroutineScope {
            launch {
                val r = repo.previewJev(t)
                if (r != null) {
                    jev = t to r
                    if (r.kindProbabilities.isNotEmpty()) decider = KindDecider.decide(decider, r.kindProbabilities, t)
                    // Jev decided it needs help with the date: ask the chat model once, after a
                    // longer pause, so a half-typed phrase never costs a call.
                    if (r.meaning?.needsHelp == true) {
                        delay(600)
                        repo.previewHelp(t, r)?.let { language = t to it }
                    }
                } else {
                    // No Jev key, or no internet: the offline guess keeps the tab following along.
                    KindGuess.kind(t)?.let { g -> decider = KindDecider.decide(decider, mapOf(g to 0.75), t) }
                }
            }
            launch {
                val r = repo.previewLanguage(t)
                if (r != null) {
                    language = t to r
                    // Without Jev, the language model's own answer picks the tab.
                    if (AiProvider.jev == null) r.kind?.let { k -> decider = KindDecider.decide(decider, mapOf(k to 0.9), t) }
                }
            }
        }
        busy = false
    }

    // A tick you can feel each time the tab moves on its own.
    var lastKind by remember { mutableStateOf(decider.kind) }
    LaunchedEffect(decider.kind) { if (decider.kind != lastKind) { view.tick(); lastKind = decider.kind } }

    val current = text.trim()
    val parsed = remember(text) { QuickAddParser.parse(text) }
    val draft = DraftBuilder.build(
        kind = decider.kind,
        text = current,
        parsed = parsed,
        manual = manual,
        jev = jev?.takeIf { it.first == current }?.second,
        language = language?.takeIf { it.first == current }?.second,
        fallbackToday = fallbackToday && decider.kind == Kind.TASK,
        now = LocalDateTime.now(),
    )
    val accent by animateColorAsState(decider.kind.accent, Motion.settle(), label = "accent")
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun accept() {
        if (accepting || !draft.ready) return
        accepting = true
        view.confirm()
        burst++
        val label = create(draft, current, manual, fallbackToday, repo, store)
        scope.launch {
            delay(if (reduce) 60 else 420)
            onCreated(draft.kind, label)
        }
    }

    // A Surface, so text inside picks up the theme's text colour (not the default black).
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
            Text("Create", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(busy, enter = fadeIn(Motion.fade()) + scaleIn(Motion.pop()), exit = fadeOut(Motion.exit())) {
                ThinkingDots(accent)
            }
            Spacer(Modifier.width(12.dp))
        }
        KindTabs(
            selected = decider.kind,
            ghost = decider.ghost && current.isNotEmpty(),
            onSelect = { k -> view.tick(); decider = KindDecider.force(k, current) },
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        // The preview morphs between tabs: the new one springs in from the side the tab
        // moved to, the old one shrinks away quickly.
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            // Nothing typed yet: say plainly how this works, with examples to tap.
            androidx.compose.animation.AnimatedVisibility(
                visible = current.isEmpty(),
                enter = fadeIn(Motion.fade()) + scaleIn(Motion.settle(), 0.94f),
                exit = fadeOut(Motion.exit()) + scaleOut(Motion.exit(), 0.96f),
            ) {
                TypeNaturallyHint(accent) { example -> text = example }
            }
            if (current.isNotEmpty()) AnimatedContent(
                targetState = draft.kind,
                transitionSpec = {
                    if (reduce) fadeIn(Motion.fade()) togetherWith fadeOut(Motion.fade())
                    else {
                        val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (slideInHorizontally(Motion.morph()) { it / 4 * dir } + scaleIn(Motion.morph(), 0.86f) + fadeIn(Motion.fade())) togetherWith
                            (slideOutHorizontally(Motion.exit()) { -it / 6 * dir } + scaleOut(Motion.exit(), 1.06f) + fadeOut(Motion.exit()))
                    }
                },
                label = "preview",
            ) { k ->
                // Rebuild for the kind being shown, so an exiting preview keeps its own look.
                val shown = if (k == draft.kind) draft else draft.copy(kind = k)
                val lift by animateFloatAsState(if (accepting) 1.06f else 1f, Motion.pop(), label = "lift")
                DraftPreview(shown, current, Modifier.fillMaxWidth().graphicsLayer { scaleX = lift; scaleY = lift })
            }
            Burst(burst, listOf(accent, Color.White, accent.copy(alpha = 0.6f)), Modifier.fillMaxSize())
        }

        // "Or maybe…" chips when Jev is torn between tabs.
        AnimatedVisibility(
            visible = current.isNotEmpty() && decider.alternatives.isNotEmpty(),
            enter = expandVertically(Motion.settle()) + fadeIn(Motion.fade()),
            exit = shrinkVertically(Motion.exit()) + fadeOut(Motion.exit()),
        ) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Text("Or", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                decider.alternatives.forEach { k ->
                    AssistChip(
                        onClick = { view.tick(); decider = KindDecider.force(k, current) },
                        label = { Text(k.label) },
                        leadingIcon = { Icon(k.icon, null, tint = k.accent, modifier = Modifier.size(16.dp)) },
                        colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.onSurface),
                    )
                }
            }
        }

        // Task and habit details stay editable with the same chips as before.
        AnimatedVisibility(
            visible = draft.kind == Kind.TASK || draft.kind == Kind.HABIT,
            enter = expandVertically(Motion.settle()) + fadeIn(Motion.fade()),
            exit = shrinkVertically(Motion.exit()) + fadeOut(Motion.exit()),
        ) {
            Column {
                AttributeChips(
                    fields = TaskFields(
                        draft.task.due, draft.task.hasTime, draft.task.priority, draft.task.reminder,
                        draft.task.recurrence, draft.task.project, draft.task.labels, draft.task.aiDerived,
                    ),
                    projects = projects,
                    onDue = { d, t -> manual = manual.copy(dueSet = true, due = d, hasTime = t) },
                    onPriority = { manual = manual.copy(priority = it) },
                    onReminder = { manual = manual.copy(reminder = it) },
                    onRepeat = { manual = manual.copy(recurrenceSet = true, recurrence = it) },
                    onLabels = { manual = manual.copy(labels = it) },
                )
                ProjectPicker(draft.task.project, projects, ai = "project" in draft.task.aiDerived, modifier = Modifier.padding(start = 8.dp)) {
                    manual = manual.copy(project = it)
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            PlainField(
                value = text,
                onValueChange = { text = it },
                placeholder = if (current.isEmpty()) cyclingExample() else placeholderFor(decider.kind),
                big = false,
                singleLine = false,
                modifier = Modifier.weight(1f).focusRequester(focus),
                transformation = HighlightSpans(parsed.spans, accent),
                imeAction = ImeAction.Send,
                onIme = ::accept,
            )
            val scale by animateFloatAsState(if (draft.ready && current.isNotEmpty() || draft.kind == Kind.STOPWATCH) 1f else 0.85f, Motion.pop(), label = "send")
            FilledIconButton(
                onClick = ::accept,
                enabled = draft.ready && (current.isNotEmpty() || draft.kind == Kind.STOPWATCH),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent, disabledContainerColor = accent.copy(alpha = 0.3f)),
                modifier = Modifier.size(52.dp).graphicsLayer { scaleX = scale; scaleY = scale },
            ) {
                AnimatedContent(draft.kind, transitionSpec = { scaleIn(Motion.pop()) + fadeIn(Motion.fade()) togetherWith scaleOut(Motion.exit()) + fadeOut(Motion.exit()) }, label = "acceptIcon") { k ->
                    Icon(
                        when (k) {
                            Kind.TIMER, Kind.STOPWATCH, Kind.FOCUS -> Icons.Filled.PlayArrow
                            Kind.NOTE, Kind.LIST -> Icons.Filled.ArrowUpward
                            else -> Icons.Filled.Check
                        },
                        "Accept", tint = Color.White,
                    )
                }
            }
        }
    }
}
}

/** Examples for the welcome screen and the text box: a mix of languages and tabs. */
private val EXAMPLES = listOf(
    "Call mom tomorrow 7pm",
    "naalai saayangalam 6 manikku doctor",
    "kal subah 7 baje gym",
    "10 min timer for tea",
    "Buy milk, eggs and bread",
    "Days until Diwali",
    "Drink water 8 times a day",
    "Focus 25 min on report",
)

/** Shapeshift-style cycling placeholder: a new example every few seconds while the box is empty. */
@Composable
private fun cyclingExample(): String {
    var i by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(2600); i = (i + 1) % EXAMPLES.size } }
    return EXAMPLES[i] + "…"
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TypeNaturallyHint(accent: Color, onExample: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Type it the way you'd say it",
            fontSize = 26.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "In any language: English, தமிழ், हिंदी, or a mix.\nThe right tab opens by itself and the details fill in as you type.",
            fontSize = 15.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Text("Try one", fontSize = 13.sp, color = accent, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EXAMPLES.take(6).forEach { e ->
                AssistChip(onClick = { onExample(e) }, label = { Text(e, fontSize = 13.sp) })
            }
        }
    }
}

private fun placeholderFor(k: Kind) = when (k) {
    Kind.TASK -> "Call mom tomorrow 7pm…"
    Kind.TIMER -> "10 min timer for tea…"
    Kind.STOPWATCH -> "Start a stopwatch…"
    Kind.FOCUS -> "Focus 25 min on report…"
    Kind.HABIT -> "Drink water 8 times a day…"
    Kind.LIST -> "Buy milk, eggs and bread…"
    Kind.COUNTDOWN -> "Days until Diwali…"
    Kind.NOTE -> "Idea: …"
}

/** Makes what the draft describes. Returns a short label for the confirmation. */
private fun create(draft: Draft, text: String, manual: ManualChoices, fallbackToday: Boolean, repo: TaskRepository, store: AppStore): String =
    when (draft.kind) {
        Kind.TASK -> repo.addFromText(text, manual = manual, fallbackToday = fallbackToday).title
        Kind.HABIT -> repo.addFromText(text, manual = manual, habitPerDay = draft.perDay).title
        Kind.TIMER -> store.startTimer(draft.label, draft.durationSeconds * 1000L).let { formatClock(draft.durationSeconds.toLong()) + " timer" }
        Kind.STOPWATCH -> { store.startStopwatch(draft.label); "Stopwatch" }
        Kind.FOCUS -> { store.startFocus(draft.label, draft.focusMinutes, draft.breakMinutes); "${draft.focusMinutes} min focus" }
        Kind.LIST -> store.addToList(draft.listName, draft.items).let { "${draft.items.size} items → ${it.name}" }
        Kind.COUNTDOWN -> store.addCountdown(draft.label, draft.countdownDate!!).title
        Kind.NOTE -> { store.addNote(draft.label.ifBlank { text }); "Note saved" }
    }

@Composable
private fun ThinkingDots(color: Color) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val a by t.animateFloat(
                0.25f, 1f,
                androidx.compose.animation.core.infiniteRepeatable(
                    androidx.compose.animation.core.tween(500, delayMillis = i * 150),
                    androidx.compose.animation.core.RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(Modifier.size(7.dp).graphicsLayer { alpha = a; scaleX = 0.7f + 0.3f * a; scaleY = 0.7f + 0.3f * a }.background(color, CircleShape))
        }
        Spacer(Modifier.width(4.dp))
        Text("thinking", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}
