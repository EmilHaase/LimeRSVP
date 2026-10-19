package com.limersvp.parse

import com.limersvp.core.Chapter

/** Words that mark chapters and front matter, in the languages people are likely to read. */
object Heuristics {
    /** Words followed by a number: "Chapter 3", "Kapitola IV", "Part One". */
    private val NUMBERED = listOf(
        "chapter", "chap", "kapitola", "kapitel", "chapitre", "capítulo", "capitulo", "capitolo",
        "hoofdstuk", "rozdział", "rozdzial", "глава", "розділ", "fejezet", "poglavlje", "kapittel",
        "luku", "bölüm", "κεφάλαιο", "część", "část", "díl", "book", "part", "teil", "partie", "parte",
        "livre", "libro", "kniha", "appendix", "příloha", "anhang", "section",
    )

    /** Words that are a chapter on their own: "Prologue", "Epilog". */
    private val ALONE = listOf(
        "prologue", "prolog", "prólogo", "epilogue", "epilog", "epílogo", "interlude", "introduction",
        "úvod", "einleitung", "preface", "předmluva", "vorwort", "afterword", "doslov", "nachwort",
        "foreword", "závěr", "conclusion",
    )

    private val NUMBER_WORDS = listOf(
        "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
        "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty",
        "thirty", "forty", "fifty", "first", "second", "third", "fourth", "fifth", "sixth", "seventh",
        "eighth", "ninth", "tenth", "last", "první", "druhá", "druhý", "třetí", "čtvrtá", "čtvrtý",
        "pátá", "pátý", "šestá", "sedmá", "osmá", "devátá", "desátá", "eins", "zwei", "drei", "vier",
        "fünf", "erstes", "zweites", "drittes", "un", "une", "deux", "trois", "premier", "première",
        "uno", "dos", "tres", "primero", "primera", "один", "первая",
    )

    private val chapterRegex = Regex(
        "^(" + NUMBERED.joinToString("|") { Regex.escape(it) } + ")\\.?\\s+" +
            "([0-9]{1,4}|[ivxlcdm]{1,7}|" + NUMBER_WORDS.joinToString("|") { Regex.escape(it) } + ")" +
            "(\\b|[.:)]).{0,70}$",
        RegexOption.IGNORE_CASE,
    )
    private val aloneRegex = Regex(
        "^(" + ALONE.joinToString("|") { Regex.escape(it) } + ")([.:]\\s*.{0,60})?$",
        RegexOption.IGNORE_CASE,
    )
    private val numberOnly = Regex("^\\s*([0-9]{1,3}|[IVXLCDM]{1,7})\\.?\\s*$")
    private val cjkChapter = Regex("^\\s*第[一二三四五六七八九十百千零〇0-9０-９]+[章回节節部卷篇话話].{0,40}$")

    fun looksLikeChapter(line: String): Boolean {
        val t = line.trim()
        if (t.isEmpty() || t.length > 90) return false
        return chapterRegex.matches(t) || aloneRegex.matches(t) || numberOnly.matches(t) || cjkChapter.matches(t)
    }

    private val FRONT = listOf(
        "cover", "title page", "title", "half title", "copyright", "contents", "table of contents", "toc",
        "dedication", "epigraph", "acknowledg", "about the author", "about the publisher", "also by",
        "praise", "frontispiece", "colophon", "imprint", "obálka", "titul", "tiráž", "obsah", "věnování",
        "inhalt", "inhaltsverzeichnis", "impressum", "widmung", "titelseite", "couverture", "sommaire",
        "table des matières", "dédicace", "índice", "indice", "contenido", "portada", "dedicatoria",
        "copertina", "sommario", "содержание", "оглавление", "обложка", "spis treści", "okładka",
        "目次", "目录", "表紙", "封面", "版权",
    )

    fun isFrontMatter(title: String): Boolean {
        val t = title.trim().lowercase()
        if (t.isEmpty()) return true
        return FRONT.any { t == it || t.startsWith("$it ") || t.startsWith("$it:") || (it.length > 6 && t.contains(it)) }
    }

    /**
     * Where to start reading the first time: the first chapter that is not cover, copyright or
     * contents, as long as it is near the start (never skip a third of a book by mistake).
     */
    fun startToken(chapters: List<Chapter>, total: Int, landmark: Int?): Int {
        if (landmark != null && landmark in 0 until total && landmark < total * 0.3) return landmark
        val first = chapters.firstOrNull { !isFrontMatter(it.title) } ?: return 0
        return if (first.token < total * 0.15) first.token else 0
    }
}
