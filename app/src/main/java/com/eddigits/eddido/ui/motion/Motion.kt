package com.eddigits.eddido.ui.motion

import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Text
import androidx.compose.ui.unit.IntOffset
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Motion tokens, taken from Shapeshift's motion.ts and converted from
 * (stiffness, damping, mass) to Compose's (dampingRatio, stiffness):
 * dampingRatio = damping / (2·√(stiffness·mass)).
 */
object Motion {
    private fun ratio(stiffness: Float, damping: Float, mass: Float = 1f) = damping / (2f * sqrt(stiffness * mass))

    /** The container morph: fast, barely overshoots. (380, 34, 0.9) */
    fun <T> morph() = spring<T>(dampingRatio = ratio(380f, 34f, 0.9f), stiffness = 380f / 0.9f)

    /** Settling content. (260, 30) */
    fun <T> settle() = spring<T>(dampingRatio = ratio(260f, 30f), stiffness = 260f)

    /** Small, quick things: chips, the tab pill's leading edge. (520, 38) */
    fun <T> snappy() = spring<T>(dampingRatio = ratio(520f, 38f), stiffness = 520f)

    /** Numbers and rings. (180, 26) */
    fun <T> number() = spring<T>(dampingRatio = ratio(180f, 26f), stiffness = 180f)

    /** A playful bounce for moments of success. */
    fun <T> pop() = spring<T>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)

    /** Exits are faster than entries and always ease out. */
    fun <T> exit() = tween<T>(durationMillis = 120, easing = FastOutSlowInEasing)
    fun <T> fade() = tween<T>(durationMillis = 150, easing = FastOutSlowInEasing)
}

/** True when the phone's "Remove animations" is on; motion then becomes short fades. */
val LocalReducedMotion = staticCompositionLocalOf { false }

fun reducedMotion(view: View): Boolean = runCatching {
    Settings.Global.getFloat(view.context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}.getOrDefault(false)

/** A light, satisfying tick. Confirm uses the system "confirm" haptic where available. */
fun View.tick() = performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
fun View.confirm() = performHapticFeedback(
    if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
)

/**
 * Digits that roll like an odometer: each character slides up (counting up) or down
 * (counting down) independently, so only the digits that change move.
 */
@Composable
fun RollingText(text: String, style: TextStyle, color: Color = Color.Unspecified, countingDown: Boolean = false) {
    Row {
        text.forEachIndexed { i, ch ->
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    val dir = if (countingDown) -1 else 1
                    (slideInVertically(Motion.snappy()) { h -> h * dir } + fadeIn(Motion.fade())) togetherWith
                        (slideOutVertically(Motion.snappy()) { h -> -h * dir } + fadeOut(Motion.exit()))
                },
                label = "digit$i",
            ) { c -> Text(c.toString(), style = style, color = color) }
        }
    }
}

/**
 * A burst of confetti-like particles from the centre of [modifier]'s box, played each
 * time [trigger] changes. Used when you accept something in the creator.
 */
@Composable
fun Burst(trigger: Int, colors: List<Color>, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    val progress = remember(trigger) { Animatable(0f) }
    val particles = remember(trigger) {
        List(26) {
            val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
            val speed = 0.55f + Random.nextFloat() * 0.45f
            Particle(cos(angle) * speed, sin(angle) * speed, colors[it % colors.size], 3f + Random.nextFloat() * 5f)
        }
    }
    LaunchedEffect(trigger) { progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    Canvas(modifier) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        val reach = size.minDimension * 0.9f
        particles.forEach { pt ->
            val x = center.x + pt.dx * reach * p
            // A little gravity: particles arc downwards as they fly out.
            val y = center.y + pt.dy * reach * p + 220f * p * p
            drawCircle(pt.color.copy(alpha = 1f - p), radius = pt.size * (1f - 0.4f * p), center = Offset(x, y))
        }
    }
}

private class Particle(val dx: Float, val dy: Float, val color: Color, val size: Float)

fun IntOffset.scaled(f: Float) = IntOffset((x * f).toInt(), (y * f).toInt())
