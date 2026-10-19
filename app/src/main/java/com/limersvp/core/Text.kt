package com.limersvp.core

import java.lang.Character.UnicodeScript

/** Script facts the tokenizer and the ORP need. Works on code points, so emoji and astral Han are fine. */
object Scripts {
    /** Scripts written without spaces between words. */
    fun isUnspaced(cp: Int): Boolean = when (scriptOf(cp)) {
        UnicodeScript.HAN, UnicodeScript.HIRAGANA, UnicodeScript.KATAKANA,
        UnicodeScript.THAI, UnicodeScript.LAO, UnicodeScript.KHMER, UnicodeScript.MYANMAR,
        UnicodeScript.TIBETAN -> true

        else -> false
    }

    /** Han and Kana: one character carries roughly a short word. */
    fun isCjk(cp: Int): Boolean = when (scriptOf(cp)) {
        UnicodeScript.HAN, UnicodeScript.HIRAGANA, UnicodeScript.KATAKANA -> true
        else -> false
    }

    fun isHiragana(cp: Int) = scriptOf(cp) == UnicodeScript.HIRAGANA

    fun isRtl(cp: Int): Boolean {
        val d = Character.getDirectionality(cp)
        return d == Character.DIRECTIONALITY_RIGHT_TO_LEFT || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
    }

    private fun scriptOf(cp: Int): UnicodeScript = try {
        UnicodeScript.of(cp)
    } catch (_: IllegalArgumentException) {
        UnicodeScript.UNKNOWN
    }

    fun hasUnspaced(s: String): Boolean {
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            if (isUnspaced(cp)) return true
            i += Character.charCount(cp)
        }
        return false
    }

    fun isWordChar(cp: Int) = Character.isLetterOrDigit(cp) ||
        Character.getType(cp) == Character.NON_SPACING_MARK.toInt() ||
        Character.getType(cp) == Character.COMBINING_SPACING_MARK.toInt()

    /** Guess a BCP-47 language from the dominant script when the book does not say. */
    fun guessLanguage(sample: String): String {
        val counts = HashMap<UnicodeScript, Int>()
        var i = 0
        while (i < sample.length) {
            val cp = sample.codePointAt(i)
            if (Character.isLetter(cp)) counts.merge(scriptOf(cp), 1, Int::plus)
            i += Character.charCount(cp)
        }
        val kana = (counts[UnicodeScript.HIRAGANA] ?: 0) + (counts[UnicodeScript.KATAKANA] ?: 0)
        if (kana > 20) return "ja"
        return when (counts.maxByOrNull { it.value }?.key) {
            UnicodeScript.HAN -> "zh"
            UnicodeScript.HANGUL -> "ko"
            UnicodeScript.THAI -> "th"
            UnicodeScript.LAO -> "lo"
            UnicodeScript.KHMER -> "km"
            UnicodeScript.MYANMAR -> "my"
            UnicodeScript.ARABIC -> "ar"
            UnicodeScript.HEBREW -> "he"
            UnicodeScript.CYRILLIC -> "ru"
            UnicodeScript.GREEK -> "el"
            UnicodeScript.DEVANAGARI -> "hi"
            else -> ""
        }
    }
}

object Punctuation {
    private const val SENTENCE = ".!?…。！？‼⁇⁈⁉؟।॥။።"
    private const val CLAUSE = ",;:—–、，；：،؛·"
    private const val CLOSERS = "\"'”’»›)]}」』】〕〉》）"
    const val OPENERS = "\"'“‘„«‹([{「『【〔〈《（¿¡"
    private const val SCENE_BREAK = "*⁂•·~#—-_=◆◇❧§○●◦"

    private val ABBREVIATIONS = hashSetOf(
        "mr", "mrs", "ms", "dr", "st", "sr", "jr", "vs", "etc", "e.g", "i.e", "prof", "inc", "ltd",
        "co", "no", "vol", "ch", "fig", "p", "pp", "cf", "approx", "dept", "est", "mt", "jan", "feb",
        "mar", "apr", "jun", "jul", "aug", "sep", "sept", "oct", "nov", "dec",
        // Czech, German, French, Spanish
        "tj", "tzv", "např", "atd", "apod", "resp", "mj", "sv", "z.b", "bzw", "usw", "ca", "nr",
        "mme", "mlle", "sra", "sr", "srta", "ud", "uds",
    )

    /** The last character that is not a closing quote or bracket. */
    private fun lastMeaningful(t: String): Int {
        var i = t.length - 1
        while (i >= 0 && CLOSERS.indexOf(t[i]) >= 0) i--
        return i
    }

    fun endsSentence(t: String): Boolean {
        val i = lastMeaningful(t)
        if (i < 0) return false
        val c = t[i]
        if (SENTENCE.indexOf(c) < 0) return false
        if (c == '.') {
            // "Mr." "e.g." "J." are not the end of a sentence.
            val word = t.substring(0, i).trimStart { OPENERS.indexOf(it) >= 0 }.lowercase()
            if (word.length == 1 && word[0].isLetter()) return false
            if (word in ABBREVIATIONS) return false
            if (word.isNotEmpty() && word.all { it.isDigit() }) return word.length > 3 // "3." in a list
        }
        return true
    }

    fun endsClause(t: String): Boolean {
        val i = lastMeaningful(t)
        return i >= 0 && CLAUSE.indexOf(t[i]) >= 0
    }

    fun isOnlyPunctuation(s: String): Boolean {
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            if (Scripts.isWordChar(cp)) return false
            i += Character.charCount(cp)
        }
        return true
    }

    fun isOpener(s: String) = s.isNotEmpty() && s.all { OPENERS.indexOf(it) >= 0 }

    /** "* * *", "⁂", "———", "~" : a scene break, not something to read. */
    fun isSceneBreak(s: String): Boolean {
        val t = s.trim()
        if (t.isEmpty() || t.length > 24) return false
        return t.all { it.isWhitespace() || SCENE_BREAK.indexOf(it) >= 0 }
    }
}

object TextClean {
    private val SPACES = Regex("[\\s\\u2000-\\u200A\\u3000\\u205F\\u1680]+")

    /**
     * Drop what should never be read: soft hyphens, zero-width spaces, BOMs, ligatures turned back
     * into letters. Non-breaking spaces survive, they keep "10 km" together on screen.
     */
    fun clean(raw: String): String {
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            when {
                c == '\u00AD' || c == '\u200B' || c == '\uFEFF' || c == '\u2060' || c == '\u180E' -> {}
                c in 'ﬀ'..'ﬆ' -> sb.append(java.text.Normalizer.normalize(c.toString(), java.text.Normalizer.Form.NFKC))
                c == '\u00A0' || c == '\u202F' || c == '\u2007' -> sb.append('\u00A0')
                c == '\t' || c == '\r' -> sb.append(' ')
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    fun collapse(s: String): String = SPACES.replace(s, " ").trim()
}

/** The Optimal Recognition Point: the letter the eye should land on. */
object Orp {
    private val graphemes = ThreadLocal.withInitial { android.icu.text.BreakIterator.getCharacterInstance() }

    /** Char range [start, end) of the ORP grapheme in [word]. */
    fun range(word: String): IntRange {
        if (word.isEmpty()) return 0 until 0
        // Core: skip leading/trailing punctuation so quotes never get the highlight.
        var s = 0
        while (s < word.length) {
            val cp = word.codePointAt(s)
            if (Scripts.isWordChar(cp)) break
            s += Character.charCount(cp)
        }
        var e = word.length
        while (e > s) {
            val cp = word.codePointBefore(e)
            if (Scripts.isWordChar(cp)) break
            e -= Character.charCount(cp)
        }
        if (s >= e) {
            s = 0; e = word.length
        }
        val core = word.substring(s, e)
        val bi = graphemes.get()!!
        bi.setText(core)
        val bounds = ArrayList<Int>(core.length + 1)
        var b = bi.first()
        while (b != android.icu.text.BreakIterator.DONE) {
            bounds.add(b); b = bi.next()
        }
        val n = bounds.size - 1
        if (n <= 0) return s until e
        val cjk = Scripts.isCjk(core.codePointAt(0))
        val idx = if (cjk) (n - 1) / 2 else when {
            n <= 1 -> 0
            n <= 5 -> 1
            n <= 9 -> 2
            n <= 13 -> 3
            else -> 4
        }
        return (s + bounds[idx]) until (s + bounds[idx + 1])
    }

    /** Graphemes in [s] (approximately, counting letters for speed). */
    fun letterCount(s: String): Int {
        var n = 0
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            if (Character.isLetterOrDigit(cp)) n++
            i += Character.charCount(cp)
        }
        return n
    }
}

/** Splits very long words into readable pieces: "extraordi-" "narily". */
object LongWords {
    const val MAX = 13

    fun split(word: String): List<String> {
        val letters = Orp.letterCount(word)
        if (letters <= MAX || Scripts.hasUnspaced(word)) return listOf(word)
        // Prefer the word's own hyphens and slashes.
        val natural = Regex("(?<=[-/–—_.])(?=\\p{L})").split(word).filter { it.isNotEmpty() }
        if (natural.size > 1 && natural.all { Orp.letterCount(it) <= MAX + 2 }) return natural
        val parts = (letters + MAX - 1) / MAX
        val per = (letters + parts - 1) / parts
        val out = ArrayList<String>(parts)
        val sb = StringBuilder()
        var count = 0
        var consumed = 0
        var i = 0
        while (i < word.length) {
            val cp = word.codePointAt(i)
            sb.appendCodePoint(cp)
            if (Character.isLetterOrDigit(cp)) {
                count++; consumed++
            }
            i += Character.charCount(cp)
            // Keep combining marks with their letter.
            while (i < word.length && isMark(word.codePointAt(i))) {
                val m = word.codePointAt(i); sb.appendCodePoint(m); i += Character.charCount(m)
            }
            if (count >= per && letters - consumed >= 3 && out.size < parts - 1) {
                sb.append('-')
                out.add(sb.toString()); sb.clear(); count = 0
            }
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }

    private fun isMark(cp: Int): Boolean {
        val t = Character.getType(cp)
        return t == Character.NON_SPACING_MARK.toInt() || t == Character.COMBINING_SPACING_MARK.toInt() ||
            t == Character.ENCLOSING_MARK.toInt()
    }
}
