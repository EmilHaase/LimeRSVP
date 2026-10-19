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
            UnicodeScript.LATIN -> guessLatin(sample)
            else -> ""
        }
    }

    /** Latin-script languages by their telltale letters and most common words. */
    private fun guessLatin(sample: String): String {
        val t = sample.lowercase()
        fun n(chars: String) = t.count { it in chars }
        fun w(vararg words: String): Int {
            var c = 0
            for (x in words) c += Regex("\\b$x\\b").findAll(t).count()
            return c
        }
        val scores = mapOf(
            "cs" to n("řůě") * 3 + w("je", "se", "na", "že", "jsem", "ale"),
            "sk" to n("ľĺŕô") * 3 + w("je", "sa", "na", "že", "som", "ale"),
            "pl" to n("łąęśźż") * 3 + w("nie", "się", "jest", "że"),
            "de" to n("äöüß") * 2 + w("der", "die", "und", "ist", "nicht", "das"),
            "fr" to n("éèêçàù") + w("le", "la", "les", "et", "est", "une"),
            "es" to n("ñ¿¡") * 3 + w("el", "los", "las", "que", "es", "una"),
            "it" to w("il", "che", "della", "sono", "una", "non"),
            "pt" to n("ãõ") * 3 + w("não", "uma", "os", "que", "com"),
            "en" to w("the", "and", "of", "to", "is", "was"),
        )
        val best = scores.maxByOrNull { it.value } ?: return ""
        return if (best.value >= 5) best.key else ""
    }
}

object Punctuation {
    private const val SENTENCE = ".!?…。！？‼⁇⁈⁉؟।॥။።"
    private const val CLAUSE = ",;:—–、，；：،؛·"
    // At the end of a word these all close: Czech/German use “ ‘ « as closing marks („…“, ‚…‘, »…«).
    private const val CLOSERS = "\"'”’“‘»«›‹)]}」』】〕〉》）"
    const val OPENERS = "\"'“‘„«‹([{「『【〔〈《（¿¡"
    private const val SCENE_BREAK = "*⁂•·~#—-_=◆◇❧§○●◦"

    private val ABBREVIATIONS = hashSetOf(
        "mr", "mrs", "ms", "dr", "st", "sr", "jr", "vs", "etc", "e.g", "i.e", "prof", "inc", "ltd",
        "co", "no", "vol", "ch", "fig", "p", "pp", "cf", "approx", "dept", "est", "mt", "jan", "feb",
        "mar", "apr", "jun", "jul", "aug", "sep", "sept", "oct", "nov", "dec",
        // Czech, German, French, Spanish
        "tj", "tzv", "např", "atd", "apod", "resp", "mj", "sv", "z.b", "bzw", "usw", "ca", "nr",
        "př", "str", "obr", "tab", "kap", "čl", "odst", "písm", "ing", "mgr", "mudr", "judr", "phdr",
        "rndr", "doc", "bc", "pí", "sl", "nám", "ul", "tel", "max", "min", "cca", "vč", "popř",
        "zejm", "příp", "s.r.o", "a.s", "hod", "dr", "dipl", "evtl", "vgl", "ggf", "hr", "fr",
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
        // Letters with their combining marks, so "ř" or "é" is never cut in half.
        val units = ArrayList<String>(word.length)
        var i = 0
        while (i < word.length) {
            val start = i
            i += Character.charCount(word.codePointAt(i))
            while (i < word.length && isMark(word.codePointAt(i))) i += Character.charCount(word.codePointAt(i))
            units.add(word.substring(start, i))
        }
        val parts = (letters + MAX - 1) / MAX
        val per = units.size.toFloat() / parts
        val cuts = ArrayList<Int>(parts - 1)
        var last = 0
        for (k in 1 until parts) {
            val ideal = (per * k).toInt()
            var best = -1
            var bestScore = Int.MIN_VALUE
            for (c in (ideal - 3)..(ideal + 3)) {
                if (c - last < 3 || units.size - c < 3) continue
                // Cut where a reader would hyphenate: before a consonant that starts a syllable.
                val score = syllableScore(units, c) * 4 - kotlin.math.abs(c - ideal)
                if (score > bestScore) {
                    bestScore = score; best = c
                }
            }
            if (best < 0) best = ideal.coerceIn(last + 1, units.size - 1)
            cuts.add(best); last = best
        }
        val out = ArrayList<String>(parts)
        var from = 0
        for (c in cuts) {
            out.add(units.subList(from, c).joinToString("") + "-"); from = c
        }
        out.add(units.subList(from, units.size).joinToString(""))
        return out
    }

    /** How natural a hyphen before unit [c] is: V|CV best, VC|CV next, V|V last. */
    private fun syllableScore(u: List<String>, c: Int): Int {
        val prev = u[c - 1]
        val next = u[c]
        val after = u.getOrNull(c + 1)
        if (!isLetter(prev) || !isLetter(next)) return 0
        // Never inside a letter written with two ("ch" is one letter in Czech) or "sch".
        val pair = (prev + next).lowercase()
        if (pair in DIGRAPHS) return -3
        if (pair == "ch" || (c >= 2 && (u[c - 2] + prev + next).lowercase() == "sch")) return -3
        return when {
            isVowel(prev) && !isVowel(next) && after != null && isVowel(after) -> 3
            !isVowel(prev) && !isVowel(next) && after != null && isVowel(after) -> 2
            isVowel(prev) && isVowel(next) -> 1
            else -> 0
        }
    }

    private val DIGRAPHS = hashSetOf("ch", "ck", "ph", "th", "sh", "gh", "qu", "dz", "dž", "ng", "rh", "wh", "sc", "sz", "cz", "ll", "ny", "gn")

    private fun isLetter(s: String) = s.isNotEmpty() && Character.isLetter(s.codePointAt(0))

    private fun isVowel(s: String): Boolean {
        val base = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).firstOrNull()?.lowercaseChar() ?: return false
        return base in "aeiouyæøœ"
    }

    private fun isMark(cp: Int): Boolean {
        val t = Character.getType(cp)
        return t == Character.NON_SPACING_MARK.toInt() || t == Character.COMBINING_SPACING_MARK.toInt() ||
            t == Character.ENCLOSING_MARK.toInt()
    }
}
