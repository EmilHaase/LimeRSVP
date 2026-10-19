package com.limersvp.core

import android.icu.text.BreakIterator
import android.icu.util.ULocale

/**
 * Turns paragraphs of any language into RSVP tokens.
 *
 * Spaced scripts split on spaces, with lone punctuation glued to its neighbour ("word —",
 * "« Bonjour"). Unspaced scripts (Chinese, Japanese, Thai, Khmer...) are segmented with ICU's
 * dictionary word breaker, and Japanese particles ride along with the word before them.
 */
class BookBuilder(language: String) {
    var language: String = language
        set(value) {
            field = value; wordBreaker = null
        }

    private val sb = StringBuilder(1 shl 16)
    private val starts = IntList(1 shl 14)
    private val ends = IntList(1 shl 14)
    private val flags = ByteList(1 shl 14)
    private val chapters = ArrayList<Chapter>()
    private val headings = ArrayList<Heading>()

    private class Heading(val token: Int, val level: Int, val text: String)

    val size: Int get() = starts.size

    private var paraStart = 0
    private var needSpace = false
    private var pendingOpener: String? = null
    private var wordBreaker: BreakIterator? = null

    /** Add one paragraph. '\n' inside it is a hard line break. [headingLevel] 1..6 for headings. */
    fun addParagraph(raw: String, headingLevel: Int = 0) {
        val cleaned = TextClean.clean(raw)
        paraStart = size
        pendingOpener = null
        val lines = cleaned.split('\n')
        for ((li, line) in lines.withIndex()) {
            val l = TextClean.collapse(line)
            if (l.isEmpty() || Punctuation.isSceneBreak(l)) continue
            val before = size
            addLine(l)
            if (size > before && li < lines.size - 1) {
                flags.or(size - 1, TokenFlags.LINE_END)
            }
        }
        val dangling = pendingOpener
        if (dangling != null && size > paraStart) extendLast(dangling.trimEnd(), glue = false)
        pendingOpener = null
        if (size == paraStart) return
        flags.or(size - 1, TokenFlags.PARA_END)
        if (headingLevel > 0) {
            for (i in paraStart until size) flags.or(i, TokenFlags.HEADING)
            headings.add(Heading(paraStart, headingLevel, sb.substring(starts[paraStart], ends[size - 1])))
        }
        sb.append('\n')
        needSpace = false
    }

    fun markChapter(title: String, depth: Int = 0, token: Int = size) {
        val t = TextClean.collapse(TextClean.clean(title)).take(120)
        if (t.isNotEmpty()) chapters.add(Chapter(t, depth, token))
    }

    private fun addLine(line: String) {
        for (chunk in line.split(' ')) {
            if (chunk.isEmpty()) continue
            if (Scripts.hasUnspaced(chunk)) {
                val segs = segment(chunk)
                for ((i, s) in segs.withIndex()) addChunk(s, glue = i > 0)
            } else if (chunk.indexOf('—') > 0 || chunk.indexOf('―') > 0) {
                // "did—not" reads as two words; the dash stays with the first.
                val parts = DASH_JOIN.split(chunk)
                for ((i, s) in parts.withIndex()) if (s.isNotEmpty()) addChunk(s, glue = i > 0)
            } else addChunk(chunk, glue = false)
        }
    }

    private fun addChunk(chunk: String, glue: Boolean) {
        if (Punctuation.isOnlyPunctuation(chunk)) {
            if (Punctuation.isOpener(chunk) || size == paraStart) {
                pendingOpener = (pendingOpener ?: "") + chunk + if (glue) "" else " "
            } else extendLast(chunk, glue)
            return
        }
        if (needSpace && !glue) sb.append(if (flags.size > 0 && (flags[flags.size - 1] and TokenFlags.LINE_END) != 0) '\n' else ' ')
        val start = sb.length
        pendingOpener?.let { sb.append(it) }
        pendingOpener = null
        sb.append(chunk)
        starts.add(start)
        ends.add(sb.length)
        flags.add(if (glue) TokenFlags.GLUE else 0)
        needSpace = true
    }

    private fun extendLast(chunk: String, glue: Boolean) {
        if (size == 0) return
        if (!glue) sb.append(' ')
        sb.append(chunk)
        ends[size - 1] = sb.length
    }

    private fun segment(chunk: String): List<String> {
        val bi = wordBreaker ?: BreakIterator.getWordInstance(
            if (language.isBlank()) ULocale.ROOT else ULocale.forLanguageTag(language)
        ).also { wordBreaker = it }
        bi.setText(chunk)
        val raw = ArrayList<String>()
        var s = bi.first()
        var e = bi.next()
        while (e != BreakIterator.DONE) {
            raw.add(chunk.substring(s, e)); s = e; e = bi.next()
        }
        val out = ArrayList<String>(raw.size)
        var opener = ""
        for (seg in raw) {
            val last = out.lastOrNull()
            when {
                Punctuation.isOnlyPunctuation(seg) -> {
                    if (Punctuation.isOpener(seg) || last == null) opener += seg
                    else out[out.size - 1] = last + seg
                }
                // Japanese particles (は, が, を, に...) belong to the word before them.
                last != null && seg.length <= 2 && seg.all { Scripts.isHiragana(it.code) } &&
                    Orp.letterCount(last) + seg.length <= 6 && !Punctuation.endsSentence(last) ->
                    out[out.size - 1] = last + seg

                else -> {
                    out.add(opener + seg); opener = ""
                }
            }
        }
        if (opener.isNotEmpty()) {
            if (out.isEmpty()) out.add(opener) else out[out.size - 1] = out.last() + opener
        }
        return out
    }

    /** Chapters from headings, for books whose table of contents is missing or useless. */
    fun headingChapters(): List<Chapter> {
        if (headings.isEmpty()) return emptyList()
        // Consecutive heading paragraphs ("Chapter 1" + "The Boy Who Lived") are one chapter.
        val blocks = ArrayList<Heading>()
        var i = 0
        while (i < headings.size) {
            val first = headings[i]
            var text = first.text
            var level = first.level
            var endTok = tokenAfterParagraph(first.token)
            var j = i + 1
            while (j < headings.size && headings[j].token == endTok) {
                text += " · " + headings[j].text
                level = minOf(level, headings[j].level)
                endTok = tokenAfterParagraph(headings[j].token)
                j++
            }
            blocks.add(Heading(first.token, level, text.replace('\n', ' ')))
            i = j
        }
        val minLevel = blocks.minOf { it.level }
        val top = blocks.filter { it.level == minLevel }
        val chosen = if (top.size >= 2) top else blocks.filter { it.level <= minLevel + 1 }
        return chosen.map { Chapter(it.text.take(120), 0, it.token) }
    }

    private fun tokenAfterParagraph(token: Int): Int {
        var t = token
        while (t < size && (flags[t] and TokenFlags.PARA_END) == 0) t++
        return t + 1
    }

    fun hasChapters() = chapters.size >= 2

    fun build(startToken: Int = 0, useHeadingFallback: Boolean = true): BookContent {
        var list = chapters.filter { it.token in 0 until size }
        if (list.size < 2 && useHeadingFallback) {
            val fromHeadings = headingChapters()
            if (fromHeadings.size >= 2) list = fromHeadings
        }
        val ordered = list.withIndex()
            .sortedWith(compareBy({ it.value.token }, { it.index }))
            .map { it.value }
            .distinctBy { it.title to it.token }
        // An entry with no text of its own ("Cover" right before "Copyright") is dropped,
        // unless it is the parent of the next one ("Part One" + "Chapter 1").
        val sorted = ordered.filterIndexed { i, c ->
            val next = ordered.getOrNull(i + 1)
            next == null || next.token != c.token || c.depth < next.depth
        }
        val text = sb.toString()
        return BookContent(
            text = text,
            starts = starts.toArray(),
            ends = ends.toArray(),
            flags = flags.toArray(),
            chapters = sorted,
            startToken = startToken.coerceIn(0, (size - 1).coerceAtLeast(0)),
            language = language,
        )
    }

    private companion object {
        val DASH_JOIN = Regex("(?<=[\\p{L}\\p{N}][—―])(?=[\\p{L}\\p{N}])")
    }
}
