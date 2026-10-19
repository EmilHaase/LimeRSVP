package com.limersvp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.limersvp.feel.breathe
import com.limersvp.feel.rememberBreather
import com.limersvp.ui.theme.LocalPalette
import kotlin.math.abs

/**
 * The book as a line: read part filled, chapters as small gaps. Drag along it to travel through
 * the book (what you will land on is shown above the finger). A tap is answered with a breath:
 * jumping by a tap would be too easy to do by accident.
 */
@Composable
fun ProgressRail(
    fraction: () -> Float,
    marks: FloatArray,
    onScrub: (Float) -> Unit,
    onScrubEnd: (Float?) -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    val p = LocalPalette.current
    var dragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableFloatStateOf(0f) }
    val scrub by rememberUpdatedState(onScrub)
    val end by rememberUpdatedState(onScrubEnd)
    val breather = rememberBreather()
    val thick by animateFloatAsState(if (dragging) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 600f), label = "thick")
    val alpha by animateFloatAsState(if (dimmed && !dragging) 0.35f else 1f, tween(220), label = "railDim")

    Canvas(
        modifier
            .fillMaxWidth()
            .height(30.dp)
            .graphicsLayer { this.alpha = alpha }
            .breathe(breather)
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    var moved = false
                    while (true) {
                        val e = awaitPointerEvent()
                        val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        ch.consume()
                        if (!moved && abs(ch.position.x - down.position.x) > slop) {
                            moved = true; dragging = true
                        }
                        if (moved) {
                            dragFrac = (ch.position.x / size.width).coerceIn(0f, 1f)
                            scrub(dragFrac)
                        }
                    }
                    if (moved) {
                        dragging = false; end(dragFrac)
                    } else {
                        breather.breathe(0.03f)
                        end(null)
                    }
                }
            },
    ) {
        val h = (3f + 3f * thick).dp.toPx()
        val y = size.height / 2f - h / 2f
        val w = size.width
        val gap = 2.dp.toPx()
        val f = if (dragging) dragFrac else fraction().coerceIn(0f, 1f)
        val showMarks = marks.size in 2..160 && w / marks.size > 10.dp.toPx()
        val r = CornerRadius(h / 2f)
        if (!showMarks) {
            drawRoundRect(p.track, Offset(0f, y), Size(w, h), r)
            drawRoundRect(p.accent, Offset(0f, y), Size(w * f, h), r)
        } else {
            // Segments between chapter starts.
            var i = 0
            while (i < marks.size) {
                val s = marks[i] * w
                val e = if (i + 1 < marks.size) marks[i + 1] * w - gap else w
                if (e > s) {
                    drawRoundRect(p.track, Offset(s, y), Size(e - s, h), r)
                    val fe = minOf(e, f * w)
                    if (fe > s) drawRoundRect(p.accent, Offset(s, y), Size(fe - s, h), r)
                }
                i++
            }
            if (marks[0] > 0f) {
                val e = marks[0] * w - gap
                if (e > 0) {
                    drawRoundRect(p.track, Offset(0f, y), Size(e, h), r)
                    drawRoundRect(p.accent, Offset(0f, y), Size(minOf(e, f * w), h), r)
                }
            }
        }
        if (dragging) {
            drawCircle(p.text, 7.dp.toPx(), Offset(f * w, size.height / 2f))
        }
    }
}
