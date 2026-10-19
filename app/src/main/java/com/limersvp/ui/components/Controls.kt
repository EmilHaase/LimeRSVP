package com.limersvp.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limersvp.feel.LocalHaptics
import com.limersvp.feel.LocalReduceMotion
import com.limersvp.ui.theme.LocalPalette
import com.limersvp.ui.theme.Numbers
import kotlinx.coroutines.launch

/** Round icon button. Presses down a touch, no ripple. */
@Composable
fun IconBtn(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalPalette.current.text,
    background: Color = Color.Transparent,
    size: Dp = 44.dp,
) {
    val press = remember { MutableInteractionSource() }
    var down by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (down) 0.88f else 1f, spring(dampingRatio = 0.5f, stiffness = 900f), label = "press")
    Box(
        modifier
            .size(size)
            .graphicsLayer { scaleX = s; scaleY = s }
            .clip(CircleShape)
            .background(background)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown().consume()
                    down = true
                    var inside = true
                    while (true) {
                        val e = awaitPointerEvent()
                        val ch = e.changes.first()
                        ch.consume()
                        inside = ch.position.x in 0f..this.size.width.toFloat() && ch.position.y in 0f..this.size.height.toFloat()
                        if (!ch.pressed) break
                    }
                    down = false
                    if (inside) onClick()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** A pill with a word on it. The one big action of a screen. */
@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    icon: ImageVector? = null,
) {
    val p = LocalPalette.current
    var down by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (down) 0.94f else 1f, spring(dampingRatio = 0.5f, stiffness = 900f), label = "pill")
    Row(
        modifier
            .graphicsLayer { scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(50))
            .background(if (primary) p.accent else p.raised)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    down = true
                    var inside = true
                    while (true) {
                        val ch = awaitPointerEvent().changes.first()
                        ch.consume()
                        inside = ch.position.x in -20f..this.size.width + 20f && ch.position.y in -20f..this.size.height + 20f
                        if (!ch.pressed) break
                    }
                    down = false
                    if (inside) onClick()
                }
            }
            .heightIn(min = 48.dp)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (primary) p.onAccent else p.text, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = if (primary) p.onAccent else p.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1)
    }
}

/** On/off with a thumb that travels. */
@Composable
fun Toggle(on: Boolean, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val x by animateDpAsState(if (on) 20.dp else 0.dp, spring(dampingRatio = 0.6f, stiffness = 700f), label = "toggle")
    val track by androidx.compose.animation.animateColorAsState(if (on) p.accent else p.track, tween(160), label = "track")
    Box(
        modifier
            .size(width = 46.dp, height = 26.dp)
            .clip(RoundedCornerShape(50))
            .background(track)
            .padding(3.dp)
    ) {
        Box(
            Modifier
                .offset(x = x)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (on) p.onAccent else p.dim)
        )
    }
}

/** A row of choices with one selection that travels between them. */
@Composable
fun <T> Choices(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val index = options.indexOf(selected).coerceAtLeast(0)
    var width by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val segment = if (options.isEmpty()) 0f else width.toFloat() / options.size
    val x by animateFloatAsState(index * segment, spring(dampingRatio = 0.75f, stiffness = 600f), label = "choice")
    Box(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.track)
            .onSizeChanged { width = it.width }
    ) {
        if (width > 0) {
            Box(
                Modifier
                    .graphicsLayer { translationX = x }
                    .padding(3.dp)
                    .width(with(density) { segment.toDp() } - 6.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(9.dp))
                    .background(p.raised)
            )
        }
        Row(Modifier.fillMaxSize()) {
            options.forEach { o ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(remember { MutableInteractionSource() }, null) { onSelect(o) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(o), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (o == selected) p.text else p.dim,
                        fontWeight = if (o == selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

/** A number that counts up to its new value when it arrives (never for ticking values). */
@Composable
fun CountUp(value: Double, format: (Double) -> String, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val anim = remember { Animatable(if (reduce) value.toFloat() else 0f) }
    LaunchedEffect(value) {
        if (reduce) anim.snapTo(value.toFloat()) else anim.animateTo(value.toFloat(), tween(750))
    }
    Text(format(anim.value.toDouble()), style = Numbers.merge(style), modifier = modifier, maxLines = 1)
}

/** A slowly breathing dot and a line saying what we wait for. Shown only after a grace period. */
@Composable
fun Waiting(text: String, progress: Float?, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val reduce = LocalReduceMotion.current
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(reduce) {
        if (!reduce) while (true) {
            pulse.animateTo(1f, tween(900, easing = LinearEasing)); pulse.animateTo(0f, tween(900, easing = LinearEasing))
        }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (progress != null) {
            val shown by animateFloatAsState(progress, tween(300), label = "wait")
            Canvas(Modifier.size(44.dp)) {
                drawCircle(p.track, size.minDimension / 2 - 2.dp.toPx(), style = Stroke(3.dp.toPx()))
                drawArc(p.accent, -90f, 360f * shown, false, topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()),
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
        } else {
            Box(
                Modifier
                    .size(12.dp)
                    .graphicsLayer { val s = 0.7f + 0.3f * pulse.value; scaleX = s; scaleY = s; alpha = 0.5f + 0.5f * pulse.value }
                    .clip(CircleShape)
                    .background(p.accent)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(text, color = p.dim, fontSize = 15.sp)
    }
}

/**
 * A panel that grows out of the button that opened it and shrinks back into it.
 * Back and a tap outside close it.
 */
@Composable
fun GrowPanel(
    visible: Boolean,
    origin: TransformOrigin,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = LocalPalette.current
    val reduce = LocalReduceMotion.current
    if (visible) BackHandler(onBack = onDismiss)
    AnimatedVisibility(visible, enter = fadeIn(tween(if (reduce) 0 else 120)), exit = fadeOut(tween(if (reduce) 0 else 160))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (p.light) 0.35f else 0.6f))
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume(); onDismiss() } }
        )
    }
    AnimatedVisibility(
        visible,
        enter = if (reduce) fadeIn(tween(0)) else scaleIn(spring(dampingRatio = 0.8f, stiffness = 520f), initialScale = 0.6f, transformOrigin = origin) + fadeIn(tween(110)),
        exit = if (reduce) fadeOut(tween(0)) else scaleOut(tween(170), targetScale = 0.6f, transformOrigin = origin) + fadeOut(tween(150)),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(p.surface)
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } },
            content = content,
        )
    }
}

/**
 * Only for actions that lose data: hold it, and a line travels around its edge on the real
 * clock. One tick when it commits.
 */
@Composable
fun HoldButton(text: String, onConfirmed: () -> Unit, modifier: Modifier = Modifier, holdMs: Int = 1200) {
    val p = LocalPalette.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .background(p.raised)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    val job = scope.launch {
                        progress.animateTo(1f, tween(((1f - progress.value) * holdMs).toInt(), easing = LinearEasing))
                        haptics.click()
                        onConfirmed()
                    }
                    while (true) {
                        val ch = awaitPointerEvent().changes.first()
                        ch.consume()
                        if (!ch.pressed) break
                    }
                    if (progress.value < 1f) {
                        job.cancel()
                        scope.launch { progress.animateTo(0f, tween(220)) }
                    }
                }
            }
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val f = progress.value
            if (f > 0f) {
                val st = 2.5.dp.toPx()
                val cr = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                val path = androidx.compose.ui.graphics.Path().apply {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(st / 2, st / 2, size.width - st / 2, size.height - st / 2, cr))
                }
                val measure = androidx.compose.ui.graphics.PathMeasure().apply { setPath(path, false) }
                val seg = androidx.compose.ui.graphics.Path()
                measure.getSegment(0f, measure.length * f, seg, true)
                drawPath(seg, p.warn, style = Stroke(st, cap = StrokeCap.Round))
            }
        }
        Text(text, color = p.warn, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

/** A labelled row in a settings list. The whole row is the touch target. */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val p = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .then(if (onClick != null) Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 16.sp)
            if (value != null) Text(value, color = p.dim, fontSize = 13.sp)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp)); trailing()
        }
    }
}
