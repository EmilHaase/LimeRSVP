package com.limersvp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limersvp.R
import com.limersvp.feel.LocalHaptics
import com.limersvp.feel.LocalReduceMotion
import com.limersvp.ui.theme.LocalPalette
import com.limersvp.ui.theme.Numbers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

/** The knob's scale: 600 wpm straight up, 100 at the far left, 1200 at the far right. */
object SpeedScale {
    const val MIN = 100
    const val MAX = 1200
    const val HOME = 600
    const val STEP = 25
    const val SWEEP = 150f

    fun angleOf(wpm: Float): Float =
        if (wpm < HOME) (wpm - HOME) / (HOME - MIN) * SWEEP else (wpm - HOME) / (MAX - HOME) * SWEEP

    fun wpmOf(angle: Float): Float {
        val a = angle.coerceIn(-SWEEP, SWEEP)
        return if (a < 0) HOME + a / SWEEP * (HOME - MIN) else HOME + a / SWEEP * (MAX - HOME)
    }

    fun snap(wpm: Float): Int = ((wpm / STEP).roundToInt() * STEP).coerceIn(MIN, MAX)

    /** Past an end the knob gives a little, more and more stiffly, never a hard wall. */
    fun rubber(raw: Float): Float = when {
        raw > SWEEP -> SWEEP + 16f * tanh((raw - SWEEP) / 16f)
        raw < -SWEEP -> -SWEEP - 16f * tanh((-SWEEP - raw) / 16f)
        else -> raw
    }
}

/**
 * A real knob: grab it and turn it. Detents every 25 wpm tick under the finger (on motors that
 * can tick), 600 has its own click, both ends stiffen and click once. A tap spins it one turn
 * (it is a fact, not a button), a long press sends it home to 600.
 *
 * [driven] is true while two fingers change the speed elsewhere: the knob turns and lifts the same way.
 */
@Composable
fun SpeedKnob(
    wpm: Int,
    onChange: (Int) -> Unit,
    onGrab: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    driven: Boolean = false,
    dimmed: Boolean = false,
    leaf: Boolean = false,
    size: Dp = 78.dp,
) {
    val p = LocalPalette.current
    val haptics = LocalHaptics.current
    val reduce = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val anim = remember { Animatable(SpeedScale.angleOf(wpm.toFloat())) }
    var grabbed by remember { mutableStateOf(false) }
    var dragAngle by remember { mutableFloatStateOf(0f) }
    val currentWpm by rememberUpdatedState(wpm)
    val change by rememberUpdatedState(onChange)
    val grab by rememberUpdatedState(onGrab)

    // Speed changed from outside (two fingers, volume keys): follow it on a spring.
    LaunchedEffect(wpm) {
        if (!grabbed) {
            val target = SpeedScale.angleOf(wpm.toFloat())
            if (reduce) anim.snapTo(target) else anim.animateTo(target, spring(dampingRatio = 0.62f, stiffness = 420f))
        }
    }

    val active = grabbed || driven
    val lift by animateFloatAsState(if (active) 1.1f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "lift")
    val alpha by animateFloatAsState(if (dimmed && !active) 0.32f else 1f, tween(if (dimmed) 220 else 160), label = "dim")

    // The touch area is larger than the drawing: a finger that lands near the knob takes it.
    Box(
        modifier
            .size(size + 24.dp)
            .graphicsLayer {
                scaleX = lift; scaleY = lift; this.alpha = alpha
            }
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val c = Offset(this.size.width / 2f, this.size.height / 2f)
                    val start = currentWpm
                    var raw = SpeedScale.angleOf(start.toFloat())
                    dragAngle = raw
                    grabbed = true
                    grab(true)
                    var prevA = angleDeg(down.position - c)
                    val outcome = withTimeoutOrNull(520L) {
                        var r: String? = null
                        while (r == null) {
                            val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            if (ch == null || !ch.pressed) r = "up"
                            else {
                                ch.consume()
                                if ((ch.position - down.position).getDistance() > slop) r = "turn"
                            }
                        }
                        r
                    } ?: "long"

                    var last = start
                    if (outcome == "long") {
                        last = SpeedScale.HOME
                        haptics.click()
                        change(last)
                        grabbed = false
                        scope.launch {
                            anim.snapTo(raw)
                            anim.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 300f))
                        }
                        // Wait for the finger to leave; it may still turn after the reset.
                        while (true) {
                            val e = awaitPointerEvent()
                            val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                            ch.consume()
                            if (!ch.pressed) break
                        }
                        grab(false)
                        return@awaitEachGesture
                    }
                    if (outcome == "up") {
                        grabbed = false
                        grab(false)
                        if (!reduce) scope.launch {
                            // One gentle turn: wind back, spin, settle with a small overshoot.
                            val home = anim.value
                            anim.animateTo(home - 14f, tween(120))
                            anim.animateTo(home + 360f, spring(dampingRatio = 0.6f, stiffness = 110f))
                            anim.snapTo(home)
                        }
                        return@awaitEachGesture
                    }
                    var atWall = false
                    while (true) {
                        val e = awaitPointerEvent()
                        val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        ch.consume()
                        val v = ch.position - c
                        // Near the centre the angle is meaningless: ignore it there.
                        if (v.getDistance() > this.size.width * 0.12f) {
                            val a = angleDeg(v)
                            var d = a - prevA
                            if (d > 180f) d -= 360f
                            if (d < -180f) d += 360f
                            raw = (raw + d).coerceIn(-SpeedScale.SWEEP - 90f, SpeedScale.SWEEP + 90f)
                            prevA = a
                        }
                        dragAngle = SpeedScale.rubber(raw)
                        val w = SpeedScale.snap(SpeedScale.wpmOf(raw))
                        if (w != last) {
                            if (w == SpeedScale.HOME) haptics.click() else haptics.tick()
                            last = w
                            change(w)
                        }
                        val beyond = abs(raw) > SpeedScale.SWEEP
                        if (beyond && !atWall) haptics.wall()
                        atWall = beyond
                    }
                    grabbed = false
                    grab(false)
                    scope.launch {
                        anim.snapTo(dragAngle)
                        anim.animateTo(SpeedScale.angleOf(last.toFloat()), spring(dampingRatio = 0.5f, stiffness = 380f))
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val va = if (grabbed) dragAngle else anim.value
            val r = this.size.minDimension / 2f
            val ring = r - 2.dp.toPx()
            val stroke = 2.4.dp.toPx()
            val tl = Offset(center.x - ring, center.y - ring)
            val arcSize = androidx.compose.ui.geometry.Size(ring * 2, ring * 2)
            // The scale.
            drawArc(p.track, -90f - SpeedScale.SWEEP, SpeedScale.SWEEP * 2, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            // How far from 600: an arc from the top to where the knob points.
            val shown = va.coerceIn(-SpeedScale.SWEEP, SpeedScale.SWEEP)
            if (abs(shown) > 0.5f) {
                drawArc(p.accent, -90f, shown, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            // Home mark at the top.
            drawLine(p.dim, Offset(center.x, center.y - ring - 1.dp.toPx()), Offset(center.x, center.y - ring + 4.dp.toPx()), 1.6.dp.toPx(), StrokeCap.Round)

            // The knob body.
            val body = ring - 6.dp.toPx()
            drawCircle(
                Brush.radialGradient(
                    listOf(if (active) p.raised.copy(alpha = 1f) else p.raised, p.surface),
                    center = Offset(center.x, center.y - body * 0.35f), radius = body * 1.4f,
                ),
                body,
            )
            drawCircle(if (active) p.faint else p.track, body, style = Stroke(1.dp.toPx()))
            // Knurls turn with the knob, so a spin is visible.
            val n = 36
            for (i in 0 until n) {
                val a = Math.toRadians((va + i * (360f / n)).toDouble())
                val o = body - 1.5.dp.toPx()
                val inner = body - 4.5.dp.toPx()
                drawLine(
                    p.faint.copy(alpha = 0.7f),
                    Offset(center.x + (sin(a) * inner).toFloat(), center.y - (cos(a) * inner).toFloat()),
                    Offset(center.x + (sin(a) * o).toFloat(), center.y - (cos(a) * o).toFloat()),
                    1.dp.toPx(),
                )
            }
            // The pointer dot.
            val a = Math.toRadians(va.toDouble())
            val pr = body - 9.dp.toPx()
            drawCircle(
                if (leaf) Color(0xFFE8C766) else p.accent,
                2.8.dp.toPx(),
                Offset(center.x + (sin(a) * pr).toFloat(), center.y - (cos(a) * pr).toFloat()),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                wpm.toString(),
                style = Numbers.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = p.text),
            )
            Text(stringResource(R.string.wpm), style = Numbers.copy(fontSize = 9.sp, color = p.dim, letterSpacing = 0.6.sp))
        }
    }
}

private fun angleDeg(v: Offset): Float = (atan2(v.x.toDouble(), -v.y.toDouble()) * 180.0 / PI).toFloat()
