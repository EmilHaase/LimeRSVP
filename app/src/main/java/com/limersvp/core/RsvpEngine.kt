package com.limersvp.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

sealed interface Frame
class WordFrame(val text: String, val orpStart: Int, val orpEnd: Int) : Frame
class HeadingFrame(val lines: List<String>) : Frame

/**
 * The reading clock. Driven by display frames, so every word lands on a real frame and the
 * timing never drifts: the time a word overshoots is carried into the next one.
 *
 * Main thread only. [position] and [part] are snapshot state, read them where they are drawn.
 */
class RsvpEngine(val content: BookContent, timing: Timing) {
    var timing: Timing = timing
        set(value) {
            field = value; cacheKey = -1L
        }

    var position by mutableIntStateOf(0)
        private set
    var part by mutableIntStateOf(0)
        private set
    var playing by mutableStateOf(false)
        private set
    var finished by mutableStateOf(false)
        private set

    /** Target speed, words per minute at one unit per word. */
    var wpm by mutableIntStateOf(600)

    /** 0..1 of the ramp, for anyone who wants to show it. */
    var rampFactor by mutableStateOf(1f)
        private set

    var rampMillis = 2000L
    var rewindOnResume = true
    var stopAtChapterEnd = false

    var onChapterStart: ((Int) -> Unit)? = null
    var onFinished: (() -> Unit)? = null
    var onStoppedAtChapter: (() -> Unit)? = null

    /** Tokens passed since the last [takeWordsRead]. */
    private var wordsRead = 0
    private var progress = 0.0
    private var lastFrame = 0L
    private var rampStart = 0L
    private var rampNanos = 0L
    private var releasedAt = 0L
    private var userPlaced = true

    private var parts: List<String> = emptyList()
    private var cacheKey = -1L
    private var cachedFrame: Frame = WordFrame("", 0, 0)
    private var cachedUnits = 1f
    private var blockEnd = 0

    init {
        place(content.startToken)
    }

    fun takeWordsRead(): Int = wordsRead.also { wordsRead = 0 }

    // ---- control -----------------------------------------------------------------------------

    private var beforePress = 0
    private var beforePlaced = false
    private var beforeReleased = 0L

    fun press(now: Long = System.nanoTime()) {
        if (playing || content.size == 0) return
        if (finished) return
        beforePress = position; beforePlaced = userPlaced; beforeReleased = releasedAt
        val since = if (releasedAt == 0L) Long.MAX_VALUE else now - releasedAt
        if (rewindOnResume && !userPlaced && since > BREAK_NANOS) {
            val s = content.sentenceStart(position, maxBack = 25)
            if (s != position) place(s)
        }
        userPlaced = false
        // A short lift barely needs a ramp: the eyes are still in it.
        val scale = when {
            since < 1_500_000_000L -> 0.3
            since < 6_000_000_000L -> 0.65
            else -> 1.0
        }
        rampNanos = (rampMillis * 1_000_000L * scale).toLong()
        progress = 0.0
        lastFrame = 0L
        playing = true
    }

    /**
     * Take back a press as if it never happened (it was the first finger of a two-finger
     * gesture). Only possible while nothing has been read yet; returns whether it worked.
     */
    fun cancelPress(): Boolean {
        if (!playing || wordsRead > 0 || part > 0) return false
        playing = false
        progress = 0.0
        rampFactor = 1f
        place(beforePress)
        userPlaced = beforePlaced
        releasedAt = beforeReleased
        return true
    }

    fun release(now: Long = System.nanoTime()) {
        if (!playing) return
        playing = false
        progress = 0.0
        restLeftMs = 0.0
        sinceRestMs = 0.0
        releasedAt = now
        rampFactor = 1f
    }

    /** Move to [token] because the person chose it. No rewind on the next start. */
    fun seek(token: Int) {
        if (content.size == 0) return
        finished = false
        place(token.coerceIn(0, content.size - 1))
        userPlaced = true
        progress = 0.0
    }

    /**
     * Back to where the person left the book. Coming back after a break starts at the beginning
     * of that sentence, shown right away, so the word on screen is the word reading starts from.
     */
    fun restore(token: Int, rewind: Boolean) {
        if (content.size == 0) return
        val t = token.coerceIn(0, content.size - 1)
        place(if (rewind) content.sentenceStart(t, maxBack = 25) else t)
        userPlaced = true
    }

    fun restart() {
        finished = false
        seek(content.startToken)
    }

    // ---- clock -------------------------------------------------------------------------------

    fun onFrame(frameNanos: Long) {
        if (!playing) return
        if (lastFrame == 0L) {
            lastFrame = frameNanos; rampStart = frameNanos; return
        }
        val dtMs = ((frameNanos - lastFrame).coerceIn(0L, 120_000_000L)) / 1_000_000.0
        lastFrame = frameNanos
        val f = rampAt(frameNanos)
        rampFactor = f
        val unitMs = 60_000.0 / (wpm.coerceAtLeast(30) * f)
        sinceRestMs += dtMs
        var budget = dtMs
        var guard = 0
        while (budget > 0 && playing && guard++ < 4) {
            if (restLeftMs > 0) {
                val use = minOf(budget, restLeftMs)
                restLeftMs -= use; budget -= use
                if (restLeftMs > 0) break
                advance()
                continue
            }
            val wordMs = unitMs * currentUnits()
            val left = (1.0 - progress) * wordMs
            if (budget < left) {
                progress += budget / wordMs; budget = 0.0
            } else {
                budget -= left
                progress = 0.0
                if (endsSentence && sinceRestMs > REST_AFTER_MS && timing.smart && timing.pauseScale > 0f) {
                    // Stay on the sentence's last word a moment: a natural place to blink.
                    restLeftMs = REST_MS; sinceRestMs = 0.0
                    continue
                }
                advance()
            }
        }
    }

    private var sinceRestMs = 0.0
    private var restLeftMs = 0.0
    private var endsSentence = false

    private fun rampAt(now: Long): Float {
        if (rampNanos <= 0L) return 1f
        val t = ((now - rampStart).toDouble() / rampNanos).coerceIn(0.0, 1.0)
        val s = t * t * (3 - 2 * t)
        return (RAMP_FROM + (1 - RAMP_FROM) * s).toFloat()
    }

    private fun advance() {
        if (part < parts.size - 1) {
            part++; cacheKey = -1L; return
        }
        val next = nextStart()
        // A paragraph end or a heading card is already a rest for the eyes.
        if (content.flag(position, TokenFlags.PARA_END) || content.flag(position, TokenFlags.HEADING)) sinceRestMs = 0.0
        wordsRead += next - position
        if (next >= content.size) {
            playing = false
            finished = true
            releasedAt = System.nanoTime()
            onFinished?.invoke()
            return
        }
        val ch = content.chapterIndexOf(next)
        val crossed = ch >= 0 && content.chapters[ch].token == next && ch != content.chapterIndexOf(position)
        place(next)
        if (crossed) {
            onChapterStart?.invoke(ch)
            if (stopAtChapterEnd) {
                playing = false
                releasedAt = System.nanoTime()
                onStoppedAtChapter?.invoke()
            }
        }
    }

    private fun nextStart(): Int = if (content.flag(position, TokenFlags.HEADING)) blockEnd else position + 1

    private fun place(token: Int) {
        var t = token
        // Never land in the middle of a heading card.
        while (t > 0 && content.flag(t, TokenFlags.HEADING) && content.flag(t - 1, TokenFlags.HEADING)) t--
        position = t
        part = 0
        cacheKey = -1L
        restLeftMs = 0.0
        parts = if (content.size == 0 || content.flag(t, TokenFlags.HEADING)) emptyList()
        else {
            val w = content.token(t)
            if (timing.splitLongWords) LongWords.split(w) else listOf(w)
        }
    }

    // ---- what to show ------------------------------------------------------------------------

    /** Reads [position] and [part]: call it where it is drawn. */
    fun frame(): Frame {
        val p = position
        val pt = part
        val key = (p.toLong() shl 8) or pt.toLong()
        if (key != cacheKey) build(p, pt, key)
        return cachedFrame
    }

    private fun currentUnits(): Float {
        frame(); return cachedUnits
    }

    private fun build(p: Int, pt: Int, key: Long) {
        cacheKey = key
        if (content.size == 0) {
            cachedFrame = WordFrame("", 0, 0); cachedUnits = 1f; return
        }
        if (content.flag(p, TokenFlags.HEADING)) {
            var e = p
            while (e < content.size && content.flag(e, TokenFlags.HEADING) && e - p < 60) e++
            blockEnd = e
            val lines = ArrayList<String>()
            var lineStart = p
            for (i in p until e) {
                val f = content.flags[i].toInt()
                if (f and (TokenFlags.PARA_END or TokenFlags.LINE_END) != 0 || i == e - 1) {
                    lines.add(content.text.substring(content.starts[lineStart], content.ends[i]).replace('\n', ' '))
                    lineStart = i + 1
                }
            }
            cachedFrame = HeadingFrame(lines.take(4))
            cachedUnits = timing.headingUnits(e - p)
            return
        }
        val pieces = parts.ifEmpty { listOf(content.token(p)) }
        val idx = pt.coerceIn(0, pieces.size - 1)
        val text = pieces[idx]
        val orp = Orp.range(text)
        cachedFrame = WordFrame(text, orp.first, orp.last + 1)
        val last = idx == pieces.size - 1
        val flags = if (last) content.flags[p].toInt() else 0
        val starts = idx == 0 && Timing.isSentenceStart(content, p)
        cachedUnits = timing.weight(text, flags, starts) + if (!last) 0.1f else 0f
        endsSentence = last && Punctuation.endsSentence(text)
    }

    companion object {
        const val RAMP_FROM = 0.4

        /** A lift longer than this is a break: the next start goes back to the sentence start. */
        const val BREAK_NANOS = 30_000_000_000L

        /**
         * RSVP readers blink far less than normal readers, which tires the eyes. After this much
         * reading without a paragraph break, the next sentence end holds a moment longer.
         */
        const val REST_AFTER_MS = 20_000.0
        const val REST_MS = 380.0
    }
}
