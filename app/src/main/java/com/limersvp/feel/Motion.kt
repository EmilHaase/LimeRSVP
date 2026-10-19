package com.limersvp.feel

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** True when the system says "remove animations": decoration stands still, transitions take one frame. */
val LocalReduceMotion = staticCompositionLocalOf { false }
val LocalHaptics = staticCompositionLocalOf<Haptics> { error("no haptics") }

@Composable
fun rememberReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** A head shake: the answer of something that does nothing when tapped. No ripple, no vibration. */
@Stable
class Shaker(private val scope: CoroutineScope) {
    val x = Animatable(0f)
    fun shake() {
        scope.launch {
            x.snapTo(0f)
            x.animateTo(0f, keyframes {
                durationMillis = 340
                -7f at 50; 6f at 110; -4f at 170; 2f at 240; 0f at 340
            })
        }
    }
}

@Composable
fun rememberShaker(): Shaker {
    val scope = rememberCoroutineScope()
    return remember { Shaker(scope) }
}

fun Modifier.shake(s: Shaker): Modifier = graphicsLayer { translationX = s.x.value * density }

/** A breath: a fact acknowledging a touch without pretending to be a button. */
@Stable
class Breather(private val scope: CoroutineScope) {
    val s = Animatable(1f)
    fun breathe(amount: Float = 0.06f) {
        scope.launch {
            s.animateTo(1f + amount, spring(dampingRatio = 0.9f, stiffness = 900f))
            s.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f))
        }
    }
}

@Composable
fun rememberBreather(): Breather {
    val scope = rememberCoroutineScope()
    return remember { Breather(scope) }
}

fun Modifier.breathe(b: Breather): Modifier = graphicsLayer {
    scaleX = b.s.value; scaleY = b.s.value
}

/** Something that should appear only if a wait lasts: never flashes for fast things. */
@Composable
fun rememberGrace(active: Boolean, graceMs: Long = 400): Boolean {
    var shown by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(active) {
        if (active) {
            delay(graceMs); shown = true
        } else shown = false
    }
    return shown && active
}

val Bouncy = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
val Snappy = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium)

@Composable
fun dpPx(v: Float): Float = with(LocalDensity.current) { v.dp.toPx() }
