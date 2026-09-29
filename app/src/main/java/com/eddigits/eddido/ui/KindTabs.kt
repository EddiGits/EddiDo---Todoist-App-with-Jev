package com.eddigits.eddido.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eddigits.eddido.model.Kind
import com.eddigits.eddido.ui.motion.LocalReducedMotion
import com.eddigits.eddido.ui.motion.Motion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * The icon tab row. The highlight is a "liquid" pill: when it moves, its leading edge
 * springs ahead quickly and the trailing edge follows more slowly, so it stretches and
 * then snaps into place. While the AI is only guessing ([ghost]) the pill is dashed.
 */
@Composable
fun KindTabs(
    selected: Kind,
    onSelect: (Kind) -> Unit,
    modifier: Modifier = Modifier,
    ghost: Boolean = false,
    showLabel: Boolean = true,
) {
    val kinds = Kind.entries
    val index = kinds.indexOf(selected)
    var width by remember { mutableIntStateOf(0) }
    val reduce = LocalReducedMotion.current

    val left = remember { Animatable(index.toFloat()) }
    val right = remember { Animatable(index + 1f) }
    var lastIndex by remember { mutableIntStateOf(index) }
    LaunchedEffect(index) {
        val movingRight = index > lastIndex
        lastIndex = index
        if (reduce) {
            left.snapTo(index.toFloat()); right.snapTo(index + 1f); return@LaunchedEffect
        }
        coroutineScope {
            // The edge in the direction of travel leads; the other one trails.
            launch { left.animateTo(index.toFloat(), if (movingRight) Motion.settle() else Motion.snappy()) }
            launch { right.animateTo(index + 1f, if (movingRight) Motion.snappy() else Motion.settle()) }
        }
    }
    val pillColor by animateColorAsState(selected.accent, Motion.settle(), label = "pill")
    val pillAlpha by animateFloatAsState(if (ghost) 0.28f else 1f, Motion.settle(), label = "pillAlpha")

    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .onSizeChanged { width = it.width },
        ) {
            val slot = if (kinds.isEmpty()) 0f else width / kinds.size.toFloat()
            Canvas(Modifier.fillMaxSize()) {
                val inset = 4.dp.toPx()
                val x0 = left.value * slot + inset
                val x1 = right.value * slot - inset
                val r = CornerRadius(size.height / 2f)
                drawRoundRect(pillColor.copy(alpha = pillAlpha), Offset(x0, 4.dp.toPx()), Size(x1 - x0, size.height - 8.dp.toPx()), r)
                if (ghost) {
                    drawRoundRect(
                        pillColor, Offset(x0, 4.dp.toPx()), Size(x1 - x0, size.height - 8.dp.toPx()), r,
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                    )
                }
            }
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                kinds.forEach { k ->
                    val on = k == selected
                    val scale by animateFloatAsState(if (on) 1.12f else 1f, Motion.pop(), label = "iconScale")
                    val tint by animateColorAsState(
                        when {
                            on && !ghost -> Color.White
                            on -> k.accent
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        Motion.settle(), label = "iconTint",
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(k) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            k.icon, k.label, tint = tint,
                            modifier = Modifier.size(22.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                        )
                    }
                }
            }
        }
        if (showLabel) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(Motion.settle()) { it / 3 * dir } + fadeIn(Motion.fade()) + scaleIn(Motion.settle(), 0.9f)) togetherWith
                        (slideOutHorizontally(Motion.snappy()) { -it / 3 * dir } + fadeOut(Motion.exit()))
                },
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                contentAlignment = Alignment.Center,
                label = "tabLabel",
            ) { k ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        if (ghost) "${k.label}?" else k.label,
                        color = k.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
