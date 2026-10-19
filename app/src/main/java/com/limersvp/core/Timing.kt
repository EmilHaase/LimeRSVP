package com.limersvp.core

/**
 * How long a word stays, in "word units": 1.0 is one word at the chosen speed.
 * At 600 wpm one unit is 100 ms.
 */
class Timing(
    val smart: Boolean = true,
    /** Scales the extra time after commas, sentences and paragraphs. */
    val pauseScale: Float = 1f,
    val splitLongWords: Boolean = true,
) {
    fun weight(word: String, flags: Int): Float {
        if (!smart) return if (flags and TokenFlags.PARA_END != 0) 1.5f else 1f
        val cjk = word.isNotEmpty() && Scripts.isCjk(word.codePointAt(0))
        val letters = Orp.letterCount(word)
        var w = if (cjk) {
            // Readers take in about one Han character in the time of a short word.
            (0.35f + 0.6f * letters).coerceIn(0.9f, 4f)
        } else when {
            letters <= 5 -> 1f
            else -> 1f + minOf(letters - 5, 12) * 0.055f
        }
        if (word.any { it.isDigit() }) w += 0.25f
        var pause = when {
            Punctuation.endsSentence(word) -> 1.1f
            Punctuation.endsClause(word) -> 0.5f
            else -> 0f
        }
        if (flags and TokenFlags.LINE_END != 0) pause = maxOf(pause, 0.6f)
        if (flags and TokenFlags.PARA_END != 0) pause = maxOf(pause, 1.6f)
        return w + pause * pauseScale
    }

    /** Units for a whole token, including the extra pieces a long word is split into. */
    fun tokenUnits(word: String, flags: Int): Float {
        var w = weight(word, flags)
        if (splitLongWords) {
            val letters = Orp.letterCount(word)
            if (letters > LongWords.MAX && !Scripts.hasUnspaced(word)) w += 0.35f * ((letters - 1) / LongWords.MAX)
        }
        return w
    }

    /** A heading card: long enough to read its few words calmly. */
    fun headingUnits(wordCount: Int): Float = (2.2f + 0.45f * wordCount).coerceAtMost(9f)

    /** Prefix sums of units, so time left is exact for the current speed (minus your own pauses). */
    fun prefix(content: BookContent): DoubleArray {
        val out = DoubleArray(content.size + 1)
        var acc = 0.0
        var i = 0
        while (i < content.size) {
            out[i] = acc
            val f = content.flags[i].toInt()
            if (f and TokenFlags.HEADING != 0) {
                // The whole heading block is one card.
                var j = i
                while (j < content.size && content.flag(j, TokenFlags.HEADING)) j++
                acc += headingUnits(j - i)
                for (k in i + 1 until j) out[k] = acc
                i = j
                continue
            }
            acc += tokenUnits(content.token(i), f)
            i++
        }
        out[content.size] = acc
        return out
    }
}
