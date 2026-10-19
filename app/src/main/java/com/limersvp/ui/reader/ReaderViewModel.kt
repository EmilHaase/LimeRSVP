package com.limersvp.ui.reader

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.limersvp.LimeApp
import com.limersvp.core.BookContent
import com.limersvp.core.RsvpEngine
import com.limersvp.core.Timing
import com.limersvp.data.PlayMode
import com.limersvp.data.Settings
import com.limersvp.ui.components.SpeedScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Load {
    data class Opening(val progress: Float?) : Load
    data object Missing : Load
    data object Ready : Load
}

class ReaderViewModel(app: Application, val bookId: String) : AndroidViewModel(app) {
    private val c = (app as LimeApp).container
    val settingsRepo = c.settings
    val haptics = c.haptics

    var load by mutableStateOf<Load>(Load.Opening(null))
        private set
    var engine by mutableStateOf<RsvpEngine?>(null)
        private set
    var content: BookContent? = null
        private set

    /** Prefix sums of word units; null while being computed. */
    var prefix by mutableStateOf<DoubleArray?>(null)
        private set

    /** Where the person was before their last jump, so they can go back. */
    var undoTo by mutableStateOf<Int?>(null)
        private set
    private var wordsSinceJump = 0

    var finishedNow by mutableStateOf(false)
        private set

    /** Bumped when the reader crosses into a new chapter while reading. */
    var chapterPulse by mutableIntStateOf(0)
        private set

    val title get() = c.library.get(bookId)?.title.orEmpty()

    private var pressNanos = 0L
    private var pressWall = 0L
    private var timingJob: Job? = null
    private var appliedTiming: Timing? = null

    init {
        viewModelScope.launch { open() }
    }

    private suspend fun open() {
        val meta = c.library.get(bookId)
        if (meta == null) {
            load = Load.Missing; return
        }
        val content = c.importer.open(bookId) { f -> load = Load.Opening(f) }
        if (content == null || content.size == 0) {
            load = Load.Missing; return
        }
        this.content = content
        val s = settingsRepo.value
        val timing = timingOf(s)
        appliedTiming = timing
        val e = RsvpEngine(content, timing)
        if (meta.started || meta.position > 0) e.restore(meta.position, rewind = s.rewind)
        apply(e, s)
        e.onChapterStart = {
            haptics.click()
            chapterPulse++
            save()
        }
        e.onStoppedAtChapter = { commit() }
        e.onFinished = { finish() }
        engine = e
        load = Load.Ready
        if (!s.tutorialDone) tutorialStep = Tutorial.READ
        c.library.update(bookId) { it.copy(openedAt = System.currentTimeMillis()) }
        if (meta.finished) finishedNow = true
        prefix = withContext(Dispatchers.Default) { timing.prefix(content) }
        viewModelScope.launch {
            settingsRepo.state.collect { s2 -> engine?.let { apply(it, s2) } }
        }
    }

    private fun timingOf(s: Settings) = Timing(s.smartTiming, s.pauses.scale, s.splitLong, content?.language.orEmpty())

    private fun apply(e: RsvpEngine, s: Settings) {
        if (!grabbingSpeed) e.wpm = s.wpm
        e.rampMillis = s.ramp.millis
        e.rewindOnResume = s.rewind
        e.stopAtChapterEnd = s.stopAtChapter
        val t = timingOf(s)
        val old = appliedTiming
        if (old == null || old.smart != t.smart || old.pauseScale != t.pauseScale || old.splitLongWords != t.splitLongWords) {
            appliedTiming = t
            e.timing = t
            val content = content ?: return
            timingJob?.cancel()
            timingJob = viewModelScope.launch {
                delay(150)
                prefix = withContext(Dispatchers.Default) { t.prefix(content) }
            }
        }
    }

    // ---- reading ------------------------------------------------------------------------------

    fun press() {
        val e = engine ?: return
        if (e.finished || e.playing) return
        e.press()
        if (e.playing) {
            pressNanos = System.nanoTime(); pressWall = System.currentTimeMillis()
            e.takeWordsRead()
        }
    }

    /**
     * Finger up. A quick tap (nothing read yet) goes back to the start of this sentence, or of the
     * one before when already there.
     */
    fun release(tapBack: Boolean = true) {
        val e = engine ?: return
        if (!e.playing) return
        val quick = pressNanos != 0L && System.nanoTime() - pressNanos < TAP_NANOS
        if (tapBack && quick && e.cancelPress()) {
            pressNanos = 0L
            e.takeWordsRead()
            stepBack()
            return
        }
        e.release()
        commit()
    }

    private fun stepBack() {
        val e = engine ?: return
        val c = content ?: return
        val here = c.sentenceStart(e.position)
        val target = if (here < e.position) here else c.sentenceStart((here - 1).coerceAtLeast(0))
        if (target == e.position) {
            // Already at the very start: the gesture still counts for the tutorial.
            if (tutorialStep == Tutorial.BACK) learned(Tutorial.BACK) else haptics.wall()
            return
        }
        e.seek(target)
        if (tutorialStep == Tutorial.BACK) learned(Tutorial.BACK) else haptics.tick()
        save()
    }

    /** Undo a press that turned out to be the start of a two-finger gesture. */
    fun cancelPress() {
        val e = engine ?: return
        if (!e.playing) return
        if (e.cancelPress()) {
            pressNanos = 0L
            e.takeWordsRead()
        } else release()
    }

    fun toggle() {
        val e = engine ?: return
        if (e.playing) release(tapBack = false) else press()
    }

    /** Book the reading stretch that just ended into the statistics, and save the place. */
    private fun commit() {
        val e = engine ?: return
        if (pressNanos != 0L) {
            val ms = (System.nanoTime() - pressNanos) / 1_000_000
            val words = e.takeWordsRead()
            pressNanos = 0L
            if (ms > 300) {
                c.stats.record(bookId, pressWall, ms, words)
                c.library.update(bookId) { it.copy(readMs = it.readMs + ms, words = it.words + words) }
            }
            wordsSinceJump += words
            if (words >= 6) learned(Tutorial.READ)
            if (wordsSinceJump > 40) undoTo = null
        }
        save()
    }

    private fun finish() {
        commit()
        finishedNow = true
        haptics.success()
        c.library.update(bookId) { it.copy(finishedAt = System.currentTimeMillis()) }
        if (!settingsRepo.value.leaf) settingsRepo.update { it.copy(leaf = true) }
    }

    fun readAgain() {
        val e = engine ?: return
        finishedNow = false
        e.restart()
        c.library.update(bookId) { it.copy(finishedAt = 0L) }
        save()
    }

    fun dismissFinished() {
        finishedNow = false
    }

    // ---- moving around -------------------------------------------------------------------------

    /** A jump the person may want to undo (chapters, the progress line, the page view). */
    fun jump(token: Int, remember: Boolean = true) {
        val e = engine ?: return
        val from = e.position
        if (remember && kotlin.math.abs(token - from) > 30 && undoTo == null) undoTo = from
        wordsSinceJump = 0
        e.seek(token)
        if (undoTo == e.position) undoTo = null
        save()
    }

    fun undo() {
        val t = undoTo ?: return
        undoTo = null
        engine?.seek(t)
        save()
    }

    /** Scrubbing with two fingers moves without remembering every word on the way. */
    fun scrubTo(token: Int) {
        engine?.seek(token)
    }

    fun scrubStart(): Int = engine?.position ?: 0

    fun scrubEnd(from: Int) {
        val e = engine ?: return
        if (kotlin.math.abs(e.position - from) > 30 && undoTo == null) undoTo = from
        if (kotlin.math.abs(e.position - from) >= 3) learned(Tutorial.SCRUB)
        wordsSinceJump = 0
        save()
    }

    // ---- speed --------------------------------------------------------------------------------

    var grabbingSpeed = false
        private set

    private var speedAtGrab = 0

    fun grabSpeed(on: Boolean) {
        grabbingSpeed = on
        val wpm = engine?.wpm ?: return
        if (on) speedAtGrab = wpm
        else {
            settingsRepo.update { it.copy(wpm = wpm) }
            if (wpm != speedAtGrab) learned(Tutorial.SPEED)
        }
    }

    // ---- tutorial -----------------------------------------------------------------------------

    /** The gesture being taught, or -1 when the tutorial is off. */
    var tutorialStep by mutableIntStateOf(-1)
        private set

    /** The person just did [step]: move on to the next one. */
    private fun learned(step: Int) {
        if (tutorialStep != step) return
        haptics.click()
        var next = step + 1
        if (next == Tutorial.BACK && settingsRepo.value.playMode == PlayMode.TAP) next++
        tutorialStep = next
        if (next == Tutorial.DONE) viewModelScope.launch {
            delay(2600); finishTutorial()
        }
    }

    fun finishTutorial() {
        tutorialStep = -1
        settingsRepo.update { it.copy(tutorialDone = true) }
    }

    fun previewSpeed(wpm: Int) {
        engine?.wpm = wpm.coerceIn(SpeedScale.MIN, SpeedScale.MAX)
    }

    fun nudgeSpeed(delta: Int) {
        val e = engine ?: return
        val next = (e.wpm + delta).coerceIn(SpeedScale.MIN, SpeedScale.MAX)
        if (next == e.wpm) {
            haptics.wall(); return
        }
        e.wpm = next
        if (next == SpeedScale.HOME) haptics.click() else haptics.tick()
        settingsRepo.update { it.copy(wpm = next) }
    }

    // ---- facts --------------------------------------------------------------------------------

    /** Milliseconds left from [from] to [to] at the current speed, or null while unknown. */
    fun msBetween(from: Int, to: Int, wpm: Int = engine?.wpm ?: 600): Long? {
        val pf = prefix ?: return null
        val a = pf[from.coerceIn(0, pf.size - 1)]
        val b = pf[to.coerceIn(0, pf.size - 1)]
        return ((b - a) * 60_000.0 / wpm.coerceAtLeast(1)).toLong()
    }

    fun save() {
        val e = engine ?: return
        val content = content ?: return
        val pf = prefix
        val ch = content.chapterIndexOf(e.position)
        c.library.update(bookId) {
            it.copy(
                position = e.position,
                total = content.size,
                chapterTitle = if (ch >= 0) content.chapters[ch].title else "",
                unitsLeft = if (pf != null) pf[content.size] - pf[e.position] else it.unitsLeft,
                unitsTotal = if (pf != null) pf[content.size] else it.unitsTotal,
                openedAt = System.currentTimeMillis(),
            )
        }
    }

    fun onBackground() {
        release()
        save()
        c.flush()
    }

    private companion object {
        const val TAP_NANOS = 200_000_000L
    }

    override fun onCleared() {
        release()
        save()
        c.flush()
    }
}

/** The tutorial's steps, in order. */
object Tutorial {
    const val READ = 0
    const val BACK = 1
    const val SCRUB = 2
    const val SPEED = 3
    const val DONE = 4
}
