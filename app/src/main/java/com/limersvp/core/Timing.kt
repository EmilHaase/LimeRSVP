package com.limersvp.core

/**
 * How long a word stays, in "word units": 1.0 is one word at the chosen speed.
 * At 600 wpm one unit is 100 ms.
 *
 * Based on eye-movement research (Rayner and colleagues): readers dwell longer on long and rare
 * words and on names, glide over short function words, and need a real pause at the end of a
 * sentence (taking that pause away measurably hurts comprehension in RSVP). Clause and
 * paragraph pauses are what RSVP readers prefer. Proportions follow other careful readers
 * (sentence ~2x, comma ~1.4x, paragraph ~2.5x, long words +15-40%, names +30-50%).
 */
class Timing(
    val smart: Boolean = true,
    /** Scales the extra time after commas, sentences and paragraphs. */
    val pauseScale: Float = 1f,
    val splitLongWords: Boolean = true,
    /** BCP-47 language of the book; German capitalises every noun, so capitals mean less there. */
    val language: String = "",
) {
    private val namesCount = language.substringBefore('-').lowercase() !in setOf("de", "lb")

    /**
     * Units for one shown word. [sentenceStart] is true for the first word after a sentence or
     * paragraph end: a capital there is not a name, and a fresh sentence gets a breath more.
     */
    fun weight(word: String, flags: Int, sentenceStart: Boolean = false): Float {
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
        if (!cjk) {
            val core = core(word)
            when {
                // "the", "of", "a": recognised at a glance, often skipped by the eye entirely.
                letters <= 4 && core.lowercase() in FUNCTION_WORDS -> w = 0.8f
                // A name mid-sentence: unfamiliar, no help from context.
                namesCount && !sentenceStart && letters >= 2 && core.first().isUpperCase() &&
                    core.drop(1).any { it.isLowerCase() } -> w += 0.3f
            }
            // Compounds read as two words ("well-known").
            if (HYPHEN.containsMatchIn(core)) w += 0.3f
        }
        if (word.any { it.isDigit() }) w += 0.25f
        if (sentenceStart) w += 0.1f
        var pause = when {
            word.endsWith("…") || word.endsWith("...") -> 1.5f
            Punctuation.endsSentence(word) -> 1.1f
            word.endsWith(';') || word.endsWith(':') -> 0.6f
            Punctuation.endsClause(word) -> 0.45f
            word.endsWith(')') || word.endsWith(']') -> 0.45f
            else -> 0f
        }
        if (flags and TokenFlags.LINE_END != 0) pause = maxOf(pause, 0.6f)
        if (flags and TokenFlags.PARA_END != 0) pause = maxOf(pause, 1.6f)
        return w + pause * pauseScale
    }

    /** Units for a whole token, including the extra pieces a long word is split into. */
    fun tokenUnits(word: String, flags: Int, sentenceStart: Boolean = false): Float {
        var w = weight(word, flags, sentenceStart)
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
            acc += tokenUnits(content.token(i), f, isSentenceStart(content, i))
            i++
        }
        out[content.size] = acc
        return out
    }

    companion object {
        private val HYPHEN = Regex("\\p{L}-\\p{L}")

        fun isSentenceStart(content: BookContent, i: Int): Boolean =
            i == 0 || content.flag(i - 1, TokenFlags.PARA_END) || Punctuation.endsSentence(content.token(i - 1))

        /** The word without the quotes and punctuation around it. */
        fun core(word: String): String = word.trim { !it.isLetterOrDigit() }.ifEmpty { word }

        /** Short words the eye takes in at a glance, in the languages people are likely to read. */
        private val FUNCTION_WORDS = hashSetOf(
            // English
            "a", "an", "the", "of", "to", "in", "on", "at", "by", "for", "and", "or", "but", "as", "is",
            "it", "be", "was", "are", "he", "she", "we", "i", "you", "his", "her", "its", "my", "me",
            "him", "us", "our", "so", "if", "no", "not", "do", "that", "this", "with", "from", "had",
            "has", "have", "were", "will", "can", "then", "than", "when", "what", "who", "all", "out",
            "up", "into", "just", "they", "them", "your", "there",
            // Czech / Slovak
            "v", "ve", "na", "se", "si", "je", "to", "že", "z", "ze", "do", "k", "ke", "o", "s", "by",
            "byl", "jak", "ale", "pro", "po", "od", "za", "u", "už", "jen", "tak", "ten", "ta", "co",
            "kde", "než", "či", "nebo", "jsem", "mi", "mu", "ho", "jí", "ji", "aby", "když",
            // German
            "der", "die", "das", "und", "ist", "im", "zu", "den", "dem", "des", "ein", "eine", "mit",
            "von", "auf", "es", "er", "sie", "ich", "wir", "sich", "an", "als", "auch", "wie", "aber",
            "bei", "war", "hat", "nur", "noch",
            // French
            "le", "la", "les", "de", "du", "un", "une", "et", "est", "en", "à", "il", "je", "ne", "pas",
            "que", "qui", "sa", "son", "au", "aux", "par", "sur", "ce", "nous",
            // Spanish / Italian / Portuguese
            "el", "los", "las", "del", "y", "que", "es", "se", "con", "su", "lo", "al", "mi", "yo",
            "il", "di", "che", "e", "per", "non", "da", "um", "uma", "os", "as", "com", "não",
        )
    }
}
