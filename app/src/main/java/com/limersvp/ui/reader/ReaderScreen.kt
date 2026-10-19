package com.limersvp.ui.reader

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.limersvp.LimeApp
import com.limersvp.R
import com.limersvp.core.BookContent
import com.limersvp.core.HeadingFrame
import com.limersvp.core.Orp
import com.limersvp.core.RsvpEngine
import com.limersvp.core.WordFrame
import com.limersvp.data.PlayMode
import com.limersvp.data.Settings
import com.limersvp.data.VolumeKeys
import com.limersvp.feel.LocalHaptics
import com.limersvp.feel.LocalReduceMotion
import com.limersvp.feel.rememberGrace
import com.limersvp.ui.Glyphs
import com.limersvp.ui.components.GrowPanel
import com.limersvp.ui.components.IconBtn
import com.limersvp.ui.components.Pill
import com.limersvp.ui.components.ProgressRail
import com.limersvp.ui.components.SpeedKnob
import com.limersvp.ui.components.SpeedScale
import com.limersvp.ui.components.Waiting
import com.limersvp.ui.format.Fmt
import com.limersvp.ui.format.rememberFmt
import com.limersvp.ui.theme.LocalPalette
import com.limersvp.ui.theme.Numbers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

private enum class Gesture { NONE, SCRUB, SPEED }
private enum class Panel { NONE, CHAPTERS, OPTIONS, PAGE }

@Composable
fun ReaderScreen(bookId: String, onClose: () -> Unit) {
    val app = LocalContext.current.applicationContext as Application
    val vm: ReaderViewModel = viewModel(
        key = "reader-$bookId",
        factory = viewModelFactory { initializer { ReaderViewModel(app, bookId) } },
    )
    val settings by vm.settingsRepo.state.collectAsStateWithLifecycle()
    val p = LocalPalette.current
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onBackground() }
    Immersive()
    KeepScreenOn(settings.keepScreenOn)

    Box(Modifier.fillMaxSize().background(p.bg)) {
        when (val l = vm.load) {
            is Load.Opening -> {
                BackHandler(onBack = onClose)
                val show = rememberGrace(true)
                if (show) Waiting(
                    stringResource(if (l.progress != null) R.string.reader_preparing else R.string.reader_opening),
                    l.progress,
                    Modifier.align(Alignment.Center),
                )
            }

            Load.Missing -> {
                BackHandler(onBack = onClose)
                Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.reader_missing), color = p.text, fontSize = 18.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.reader_missing_hint), color = p.dim, fontSize = 15.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Pill(stringResource(R.string.library), onClose)
                }
            }

            Load.Ready -> {
                val e = vm.engine
                val content = vm.content
                if (e != null && content != null) Reader(vm, e, content, settings, onClose)
            }
        }
        if (settings.dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = settings.dim)))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Reader(vm: ReaderViewModel, engine: RsvpEngine, content: BookContent, settings: Settings, onClose: () -> Unit) {
    val p = LocalPalette.current
    val fmt = rememberFmt()
    val haptics = LocalHaptics.current
    val reduce = LocalReduceMotion.current
    val density = LocalDensity.current
    val container = (LocalContext.current.applicationContext as LimeApp).container

    var gesture by remember { mutableStateOf(Gesture.NONE) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var knobGrabbed by remember { mutableStateOf(false) }
    var railFrac by remember { mutableStateOf<Float?>(null) }
    var railStartTok by remember { mutableIntStateOf(0) }
    val playing = engine.playing

    // Where we are, updated only while stopped: what is shown around the word never churns while reading.
    var still by remember { mutableIntStateOf(engine.position) }
    LaunchedEffect(engine) {
        snapshotFlow { engine.playing to engine.position }.collect { (pl, pos) -> if (!pl) still = pos }
    }

    // The clock: only runs while reading.
    LaunchedEffect(engine) {
        snapshotFlow { engine.playing }.collectLatest { on ->
            if (on) while (true) withFrameNanos { engine.onFrame(it) }
        }
    }

    // The chrome leaves at once when reading starts, and comes back after a short hold so a quick
    // lift-and-press never flickers it.
    var chrome by remember { mutableStateOf(true) }
    LaunchedEffect(playing, gesture) {
        if (playing || gesture != Gesture.NONE) chrome = false else {
            delay(if (chrome) 0 else 350); chrome = true
        }
    }
    val chromeAlpha by animateFloatAsState(if (chrome) 1f else 0f, tween(if (reduce) 0 else if (chrome) 240 else 110), label = "chrome")

    // Volume keys.
    val vmState by rememberUpdatedState(vm)
    val mode by rememberUpdatedState(settings.volumeKeys)
    DisposableEffect(Unit) {
        container.keyHandler = handler@{ ev ->
            if (ev.keyCode != KeyEvent.KEYCODE_VOLUME_UP && ev.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return@handler false
            when (mode) {
                VolumeKeys.OFF -> false
                VolumeKeys.SPEED -> {
                    if (ev.action == KeyEvent.ACTION_DOWN) vmState.nudgeSpeed(if (ev.keyCode == KeyEvent.KEYCODE_VOLUME_UP) SpeedScale.STEP else -SpeedScale.STEP)
                    true
                }

                VolumeKeys.READ -> {
                    if (ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0) vmState.press()
                    if (ev.action == KeyEvent.ACTION_UP) vmState.release()
                    true
                }
            }
        }
        onDispose { container.keyHandler = null }
    }

    BackHandler(enabled = panel == Panel.NONE) { onClose() }

    val rtl = remember(content) { content.language.substringBefore('-') in setOf("ar", "he", "fa", "ur", "yi", "iw") }
    val marks = remember(content) {
        FloatArray(content.chapters.size) { content.chapters[it].token.toFloat() / content.size.coerceAtLeast(1) }
    }

    Box(Modifier.fillMaxSize()) {
        // 1. The whole screen is the reading surface.
        Box(
            Modifier
                .fillMaxSize()
                .readerGestures(
                    vm = vm, engine = engine, content = content, holdMode = settings.playMode == PlayMode.HOLD,
                    rtl = rtl, onGesture = { gesture = it }, haptics = haptics,
                )
        )

        val insets = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
        Column(Modifier.fillMaxSize().windowInsetsPadding(insets)) {
            // Top bar.
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 6.dp)
                    .graphicsLayer { alpha = chromeAlpha },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBtn(Glyphs.Back, onClose, enabled = chrome)
                Spacer(Modifier.weight(1f))
                IconBtn(Glyphs.Page, { panel = Panel.PAGE }, enabled = chrome, tint = p.dim)
                IconBtn(Glyphs.Chapters, { panel = Panel.CHAPTERS }, enabled = chrome, tint = p.dim)
                IconBtn(Glyphs.Tune, { panel = Panel.OPTIONS }, enabled = chrome, tint = p.dim)
            }

            // The word.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                WordStage({ engine.frame() }, settings, Modifier.fillMaxSize())
                if (settings.context && engine.frame() is WordFrame) {
                    ContextLine(
                        content, still, settings,
                        Modifier
                            .align(Alignment.Center)
                            .offset(y = (settings.wordSize * 1.15f + 36).dp)
                            .padding(horizontal = 28.dp)
                            .graphicsLayer { alpha = chromeAlpha * 0.9f },
                    )
                }
                UndoChip(vm, chrome, Modifier.align(Alignment.TopCenter).padding(top = 4.dp))
            }

            // Where we are.
            BottomInfo(vm, content, still, chromeAlpha, railFrac)
            ProgressRail(
                fraction = { engine.position.toFloat() / (content.size - 1).coerceAtLeast(1) },
                marks = marks,
                dimmed = playing,
                onScrub = { f ->
                    if (railFrac == null) railStartTok = engine.position
                    val tok = (f * (content.size - 1)).roundToInt()
                    val before = railFrac?.let { content.chapterIndexOf((it * (content.size - 1)).roundToInt()) }
                    if (before != null && before != content.chapterIndexOf(tok)) haptics.tick()
                    railFrac = f
                },
                onScrubEnd = { f ->
                    railFrac = null
                    if (f != null) {
                        // Land on the start of the paragraph: a cleaner place to begin.
                        val tok = (f * (content.size - 1)).roundToInt()
                        vm.jump(content.paraStarts[content.paragraphOf(tok)])
                    }
                },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 2.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val chIndex = content.chapterIndexOf(still)
                val chEnd = if (chIndex >= 0) content.chapterEnd(chIndex) else content.size
                Text(
                    if (content.chapters.isEmpty()) "" else stringResource(R.string.fmt_in_chapter, fmt.minutes(vm.msBetween(still, chEnd))),
                    style = Numbers.copy(fontSize = 12.sp, color = p.dim),
                    modifier = Modifier.weight(1f).graphicsLayer { alpha = chromeAlpha },
                    maxLines = 1,
                )
                Box(contentAlignment = Alignment.Center) {
                    SpeedKnob(
                        wpm = engine.wpm,
                        onChange = { vm.previewSpeed(it) },
                        onGrab = { g -> knobGrabbed = g; vm.grabSpeed(g) },
                        driven = gesture == Gesture.SPEED,
                        dimmed = playing,
                        leaf = settings.leaf,
                    )
                    SpeedBubble(
                        visible = knobGrabbed || gesture == Gesture.SPEED,
                        wpm = engine.wpm,
                        left = vm.msBetween(still, content.size, engine.wpm),
                        modifier = Modifier.align(Alignment.TopCenter).offset(y = (-100).dp).wrapContentSize(unbounded = true),
                    )
                }
                Text(
                    stringResource(R.string.fmt_left, fmt.minutes(vm.msBetween(still, content.size))),
                    style = Numbers.copy(fontSize = 12.sp, color = p.dim, textAlign = TextAlign.End),
                    modifier = Modifier.weight(1f).graphicsLayer { alpha = chromeAlpha },
                    maxLines = 1,
                )
            }
        }

        // 2. Scrubbing with two fingers: the page, following the word.
        AnimatedVisibility(
            gesture == Gesture.SCRUB,
            enter = fadeIn(tween(if (reduce) 0 else 110)),
            exit = fadeOut(tween(if (reduce) 0 else 180)),
        ) {
            Box(Modifier.fillMaxSize().background(p.bg)) {
                BookView(content, { engine.position }, follow = true, settings = settings, topPadding = 56.dp)
                ScrubHeader(engine, content, Modifier.windowInsetsPadding(insets).padding(top = 14.dp).align(Alignment.TopCenter))
            }
        }

        // 3. Panels grow out of their buttons.
        GrowPanel(
            panel == Panel.PAGE, TransformOrigin(0.72f, 0.03f), { panel = Panel.NONE },
            Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize().background(p.bg)) {
                BookView(content, { engine.position }, follow = false, settings = settings, onPick = { vm.jump(it) }, topPadding = 64.dp)
                Row(
                    Modifier.windowInsetsPadding(insets).fillMaxWidth().height(56.dp).padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBtn(Glyphs.Close, { panel = Panel.NONE })
                    val ch = content.chapterIndexOf(engine.position)
                    Text(
                        if (ch >= 0) content.chapters[ch].title else "",
                        color = p.dim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 16.dp),
                    )
                }
            }
        }
        GrowPanel(
            panel == Panel.CHAPTERS, TransformOrigin(0.82f, 0.03f), { panel = Panel.NONE },
            Modifier.windowInsetsPadding(insets).padding(10.dp).fillMaxWidth().fillMaxHeight(0.86f),
        ) {
            ChapterList(content, engine.position, { a, b -> vm.msBetween(a, b) }, { i ->
                vm.jump(content.chapters[i].token)
                panel = Panel.NONE
            }, Modifier.fillMaxSize())
        }
        GrowPanel(
            panel == Panel.OPTIONS, TransformOrigin(0.93f, 0.03f), { panel = Panel.NONE },
            Modifier.windowInsetsPadding(insets).padding(10.dp).fillMaxWidth().fillMaxHeight(0.86f),
        ) {
            val f = engine.frame()
            val word = stringResource(R.string.reader_sample_word)
            val sample = (f as? WordFrame) ?: WordFrame(word, Orp.range(word).first, Orp.range(word).last + 1)
            ReaderOptions(settings, sample, { block -> vm.settingsRepo.update(block) })
        }

        // 4. The end of the book.
        Finished(vm, onClose)
    }
}

/**
 * One finger held: read. Two fingers: sideways moves through the text (the page opens), up and
 * down changes the speed (the knob turns). The axis locks once, with one tick.
 */
private fun Modifier.readerGestures(
    vm: ReaderViewModel,
    engine: RsvpEngine,
    content: BookContent,
    holdMode: Boolean,
    rtl: Boolean,
    onGesture: (Gesture) -> Unit,
    haptics: com.limersvp.feel.Haptics,
): Modifier = pointerInput(holdMode, rtl) {
    val slop = viewConfiguration.touchSlop
    val wordPx = 13.dp.toPx()
    val speedStepPx = 20.dp.toPx()
    val shuttleFrom = 190.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (holdMode) vm.press()
        var two = false
        var axis = 0
        var acc = androidx.compose.ui.geometry.Offset.Zero
        var lock = androidx.compose.ui.geometry.Offset.Zero
        val prev = HashMap<PointerId, androidx.compose.ui.geometry.Offset>()
        var startTok = 0
        var startWpm = 0
        var lastWpm = 0
        var lastChapter = -1
        var wallHit = false
        var shuttle = 0.0
        var lastTime = down.uptimeMillis
        var maxMove = 0f
        while (true) {
            val ev = withTimeoutOrNull(16L) { awaitPointerEvent() }
            val now = ev?.changes?.firstOrNull()?.uptimeMillis ?: (lastTime + 16)
            val dt = (now - lastTime).coerceIn(0, 100) / 1000.0
            lastTime = now
            if (ev != null) {
                val pressed = ev.changes.filter { it.pressed }
                if (pressed.isEmpty()) {
                    ev.changes.forEach { it.consume() }
                    break
                }
                if (!two && pressed.size >= 2) {
                    two = true
                    // The first finger of a two-finger gesture never meant "read".
                    vm.cancelPress()
                    prev.clear(); pressed.forEach { prev[it.id] = it.position }
                } else if (two) {
                    var sx = 0f
                    var sy = 0f
                    var n = 0
                    for (c in pressed) prev[c.id]?.let { pp -> sx += c.position.x - pp.x; sy += c.position.y - pp.y; n++ }
                    prev.clear(); pressed.forEach { prev[it.id] = it.position }
                    if (n > 0 && pressed.size >= 2) acc += androidx.compose.ui.geometry.Offset(sx / n, sy / n)
                    if (axis == 0 && acc.getDistance() > slop * 1.3f) {
                        axis = if (abs(acc.x) >= abs(acc.y)) 1 else 2
                        lock = acc
                        haptics.click()
                        if (axis == 1) {
                            startTok = vm.scrubStart()
                            lastChapter = content.chapterIndexOf(startTok)
                            onGesture(Gesture.SCRUB)
                        } else {
                            startWpm = engine.wpm; lastWpm = startWpm
                            vm.grabSpeed(true)
                            onGesture(Gesture.SPEED)
                        }
                    }
                } else {
                    maxMove = maxOf(maxMove, (ev.changes.first().position - down.position).getDistance())
                }
                ev.changes.forEach { it.consume() }
            }
            if (two && axis == 1) {
                // Cursor-like: right is forward (left in right-to-left books).
                val dx = (acc.x - lock.x) * if (rtl) -1f else 1f
                val u = abs(dx) / wordPx
                val jog = sign(dx) * (u + (u / 16f) * (u / 16f) * 8f)
                // Hold far out and it keeps travelling, faster the further you are.
                if (abs(dx) > shuttleFrom) {
                    val k = (abs(dx) - shuttleFrom) / (50.dp.toPx())
                    shuttle += sign(dx) * (15.0 * k * k).coerceAtMost(400.0) * dt
                }
                val target = startTok + jog.roundToInt() + shuttle.toInt()
                val clamped = target.coerceIn(0, content.size - 1)
                if (clamped != target) {
                    if (!wallHit) haptics.wall()
                    wallHit = true
                    shuttle = (clamped - startTok - jog.roundToInt()).toDouble()
                } else wallHit = false
                if (clamped != engine.position) {
                    vm.scrubTo(clamped)
                    val ch = content.chapterIndexOf(clamped)
                    if (ch != lastChapter) {
                        haptics.tick(); lastChapter = ch
                    }
                }
            } else if (two && axis == 2) {
                val dy = acc.y - lock.y
                val steps = (-dy / speedStepPx).roundToInt()
                val raw = startWpm + steps * SpeedScale.STEP
                val w = raw.coerceIn(SpeedScale.MIN, SpeedScale.MAX)
                if (w != lastWpm) {
                    if (w == SpeedScale.HOME) haptics.click() else haptics.tick()
                    lastWpm = w
                    vm.previewSpeed(w)
                }
                if (raw != w) {
                    if (!wallHit) haptics.wall()
                    wallHit = true
                } else wallHit = false
            }
        }
        if (!two) {
            if (holdMode) vm.release()
            else if (maxMove < slop) vm.toggle()
        } else {
            when (axis) {
                1 -> vm.scrubEnd(startTok)
                2 -> vm.grabSpeed(false)
            }
            onGesture(Gesture.NONE)
        }
    }
}

@Composable
private fun ContextLine(content: BookContent, position: Int, settings: Settings, modifier: Modifier) {
    val p = LocalPalette.current
    val text = remember(content, position, p.text, p.dim) {
        val s = content.sentenceStart(position, 30)
        val e = content.sentenceEnd(position, 30)
        val base = content.starts[s]
        val raw = content.text.substring(base, content.ends[e]).replace('\n', ' ')
        AnnotatedString(
            raw,
            listOf(AnnotatedString.Range(SpanStyle(color = p.text), (content.starts[position] - base).coerceIn(0, raw.length), (content.ends[position] - base).coerceIn(0, raw.length))),
        )
    }
    Text(
        text,
        color = p.faint,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontFamily = familyOf(settings.font, p.light),
        textAlign = TextAlign.Center,
        maxLines = 3,
        minLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun BottomInfo(vm: ReaderViewModel, content: BookContent, still: Int, alpha: Float, railFrac: Float?) {
    val p = LocalPalette.current
    val fmt = rememberFmt()
    Box(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 20.dp)) {
        val pos = railFrac?.let { (it * (content.size - 1)).roundToInt() } ?: still
        val ch = content.chapterIndexOf(pos)
        val title = if (ch >= 0) content.chapters[ch].title else vm.title
        val pct = fmt.percent(pos.toFloat() / (content.size - 1).coerceAtLeast(1))
        val a = if (railFrac != null) 1f else alpha
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().graphicsLayer { this.alpha = a },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title, color = if (railFrac != null) p.text else p.dim, fontSize = 14.sp,
                fontWeight = if (railFrac != null) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(pct, style = Numbers.copy(fontSize = 14.sp, color = p.dim))
        }
    }
}

@Composable
private fun ScrubHeader(engine: RsvpEngine, content: BookContent, modifier: Modifier) {
    val p = LocalPalette.current
    val fmt = rememberFmt()
    val pos = engine.position
    val ch = content.chapterIndexOf(pos)
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(p.raised)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ch >= 0) {
            Text(content.chapters[ch].title, color = p.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(10.dp))
        }
        Text(fmt.percent(pos.toFloat() / (content.size - 1).coerceAtLeast(1)), style = Numbers.copy(fontSize = 13.sp, color = p.dim))
    }
}

@Composable
private fun UndoChip(vm: ReaderViewModel, chrome: Boolean, modifier: Modifier) {
    val p = LocalPalette.current
    val fmt = rememberFmt()
    val to = vm.undoTo
    val content = vm.content
    AnimatedVisibility(
        to != null && chrome && content != null,
        enter = fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = 600f), initialScale = 0.85f),
        exit = fadeOut(tween(140)),
        modifier = modifier,
    ) {
        val target = to ?: 0
        val pct = content?.let { fmt.percent(target.toFloat() / (it.size - 1).coerceAtLeast(1)) }.orEmpty()
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(p.raised)
                .clickable(remember { MutableInteractionSource() }, null) { vm.undo() }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Glyphs.Undo, null, tint = p.text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.reader_back_to, pct), color = p.text, fontSize = 14.sp)
        }
    }
}

/** The speed above the finger (the finger covers the knob), with what it means for this book. */
@Composable
private fun SpeedBubble(visible: Boolean, wpm: Int, left: Long?, modifier: Modifier) {
    val p = LocalPalette.current
    val fmt = rememberFmt()
    val reduce = LocalReduceMotion.current
    AnimatedVisibility(
        visible,
        enter = if (reduce) fadeIn(tween(0)) else scaleIn(spring(dampingRatio = 0.6f, stiffness = 700f), initialScale = 0.5f, transformOrigin = TransformOrigin(0.5f, 1f)) + fadeIn(tween(90)),
        exit = if (reduce) fadeOut(tween(0)) else scaleOut(tween(160), targetScale = 0.6f, transformOrigin = TransformOrigin(0.5f, 1f)) + fadeOut(tween(140)),
        modifier = modifier,
    ) {
        Column(
            Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(p.raised)
                .padding(horizontal = 22.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(wpm.toString(), style = Numbers.copy(fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = p.text))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.wpm), color = p.dim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
            }
            Text(stringResource(R.string.fmt_left, fmt.estimate(left)), style = Numbers.copy(fontSize = 12.sp, color = p.dim), maxLines = 1)
        }
    }
}

@Composable
private fun Finished(vm: ReaderViewModel, onClose: () -> Unit) {
    val p = LocalPalette.current
    val fmt = rememberFmt()
    val reduce = LocalReduceMotion.current
    val c = (LocalContext.current.applicationContext as LimeApp).container
    AnimatedVisibility(vm.finishedNow, enter = fadeIn(tween(if (reduce) 0 else 200)), exit = fadeOut(tween(150))) {
        Box(Modifier.fillMaxSize().background(p.bg.copy(alpha = 0.94f)).pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }) {
            val meta = c.library.get(vm.bookId)
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.finished), color = p.accent, fontSize = 15.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text(vm.title, color = p.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(24.dp))
                if (meta != null && meta.readMs > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        Fact(fmt.spent(meta.readMs), stringResource(R.string.reader_reading))
                        val wpm = if (meta.readMs > 30_000) (meta.words * 60_000 / meta.readMs).toString() else Fmt.UNKNOWN
                        Fact(wpm, stringResource(R.string.reader_wpm_average))
                    }
                    Spacer(Modifier.height(32.dp))
                }
                Pill(stringResource(R.string.library), onClose)
                Spacer(Modifier.height(10.dp))
                Pill(stringResource(R.string.reader_read_again), { vm.readAgain() }, primary = false, icon = Glyphs.Restart)
            }
        }
    }
}

@Composable
private fun Fact(value: String, label: String) {
    val p = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Numbers.copy(fontSize = 20.sp, color = p.text, fontWeight = FontWeight.SemiBold))
        Text(label, color = p.dim, fontSize = 12.sp)
    }
}

@Composable
private fun Immersive() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = view.context.findActivity()?.window
        val ctl = window?.let { WindowCompat.getInsetsController(it, view) }
        ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { ctl?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

@Composable
private fun KeepScreenOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(on) {
        val window = view.context.findActivity()?.window
        if (on) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
