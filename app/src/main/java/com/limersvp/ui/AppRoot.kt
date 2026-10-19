package com.limersvp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.limersvp.Container
import com.limersvp.feel.LocalHaptics
import com.limersvp.feel.LocalReduceMotion
import com.limersvp.feel.rememberReduceMotion
import com.limersvp.ui.library.LibraryScreen
import com.limersvp.ui.reader.ReaderScreen
import com.limersvp.ui.reader.findActivity
import com.limersvp.ui.settings.SettingsScreen
import com.limersvp.ui.stats.StatsScreen
import com.limersvp.ui.theme.LimeTheme
import com.limersvp.ui.theme.LocalPalette

@Composable
fun AppRoot(c: Container) {
    val settings by c.settings.state.collectAsStateWithLifecycle()
    LimeTheme(settings.theme, settings.highlight) {
        CompositionLocalProvider(
            LocalHaptics provides c.haptics,
            LocalReduceMotion provides rememberReduceMotion(),
        ) {
            val p = LocalPalette.current
            val view = LocalView.current
            SideEffect {
                view.context.findActivity()?.window?.let { w ->
                    val ctl = WindowCompat.getInsetsController(w, view)
                    ctl.isAppearanceLightStatusBars = p.light
                    ctl.isAppearanceLightNavigationBars = p.light
                }
            }
            var tab by rememberSaveable { mutableIntStateOf(0) }
            var reading by rememberSaveable { mutableStateOf<String?>(null) }
            val request by c.openRequest.collectAsStateWithLifecycle()
            LaunchedEffect(request) {
                request?.let { reading = it; c.openRequest.value = null }
            }
            BackHandler(enabled = reading == null && tab != 0) { tab = 0 }

            Box(Modifier.fillMaxSize().background(p.bg)) {
                // The tabs stay alive: switching rebuilds nothing. Hidden ones take no touches.
                Tab(tab == 0) { LibraryScreen(c) { reading = it } }
                Tab(tab == 1) { StatsScreen(c, visible = tab == 1 && reading == null) }
                Tab(tab == 2) { SettingsScreen(c) { reading = it } }
                PillNav(tab, { tab = it }, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp))

                // The reader is the heaviest screen: it gets the plainest transition.
                AnimatedVisibility(
                    reading != null,
                    enter = fadeIn(tween(if (LocalReduceMotion.current) 0 else 160)),
                    exit = fadeOut(tween(if (LocalReduceMotion.current) 0 else 140)),
                    modifier = Modifier.zIndex(10f),
                ) {
                    var shownId by rememberSaveable { mutableStateOf(reading) }
                    if (reading != null) shownId = reading
                    shownId?.let { id -> ReaderScreen(id) { reading = null } }
                }
            }
        }
    }
}

@Composable
private fun Tab(selected: Boolean, content: @Composable () -> Unit) {
    val reduce = LocalReduceMotion.current
    val a by animateFloatAsState(if (selected) 1f else 0f, tween(if (reduce) 0 else 160), label = "tab")
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(if (selected) 1f else 0f)
            .graphicsLayer { alpha = a }
            .then(
                if (selected) Modifier else Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            )
    ) { content() }
}

/** A floating pill. The selection travels to the tab you pick; the icon grows a touch on a spring. */
@Composable
private fun PillNav(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    val icons = listOf(Glyphs.Library, Glyphs.Stats, Glyphs.Tune)
    val item = 64.dp
    val x by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.72f, stiffness = 520f), label = "nav")
    Box(
        modifier
            .zIndex(5f)
            .clip(RoundedCornerShape(50))
            .background(p.raised.copy(alpha = 0.94f))
            .padding(5.dp)
    ) {
        Box(
            Modifier
                .graphicsLayer { translationX = x * item.toPx() }
                .width(item)
                .height(48.dp)
                .clip(RoundedCornerShape(50))
                .background(p.accentSoft)
        )
        Row {
            icons.forEachIndexed { i, icon ->
                val s by animateFloatAsState(if (i == selected) 1.12f else 1f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "navIcon")
                Box(
                    Modifier
                        .width(item)
                        .height(48.dp)
                        .pointerInput(i) { awaitEachGesture { awaitFirstDown().consume(); onSelect(i) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon, null,
                        tint = if (i == selected) p.accent else p.dim,
                        modifier = Modifier.size(24.dp).graphicsLayer { scaleX = s; scaleY = s },
                    )
                }
            }
        }
    }
}
