package com.limersvp.parse

import com.limersvp.core.BookBuilder
import com.limersvp.core.Scripts
import org.jsoup.Jsoup
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** Plain text: paragraphs from blank lines (or lines), hard-wrapped lines joined, chapters by their names. */
class TxtParser(private val file: File) {

    fun parse(progress: (Float) -> Unit): ParsedBook {
        if (file.length() > 64L * 1024 * 1024) throw ImportException(ImportException.Reason.TOO_BIG)
        var text = decode(file.readBytes())
        text = stripGutenberg(text).replace("\r\n", "\n").replace('\r', '\n')
        val builder = BookBuilder(Scripts.guessLanguage(text.take(6000)))
        val blocks = splitParagraphs(text)
        if (blocks.isEmpty()) throw ImportException(ImportException.Reason.NO_TEXT)
        var title = ""
        for ((i, raw) in blocks.withIndex()) {
            if (i % 200 == 0) progress(i.toFloat() / blocks.size)
            val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue
            val joined = joinWrapped(lines)
            val single = lines.size <= 2 && joined.length <= 80
            if (single && Heuristics.looksLikeChapter(lines.first())) {
                builder.markChapter(joined.replace('\n', ' '))
                builder.addParagraph(joined, headingLevel = 2)
            } else {
                if (title.isEmpty() && i < 5 && single) title = joined
                builder.addParagraph(joined)
            }
        }
        if (builder.size == 0) throw ImportException(ImportException.Reason.NO_TEXT)
        val partial = builder.build()
        progress(1f)
        return ParsedBook(
            content = builder.build(Heuristics.startToken(partial.chapters, partial.size, null)),
            title = title,
            author = "",
            coverBytes = null,
        )
    }

    private fun splitParagraphs(t: String): List<String> {
        val byBlank = t.split(Regex("\\n\\s*\\n"))
        // A file without blank lines uses one line per paragraph.
        return if (byBlank.size < 3 && t.count { it == '\n' } > 10) t.split('\n') else byBlank
    }

    /** Lines of a hard-wrapped paragraph become one; short lines (poetry, lists) keep their breaks. */
    private fun joinWrapped(lines: List<String>): String {
        if (lines.size == 1) return lines[0]
        val longest = lines.maxOf { it.length }
        val wrapped = longest > 45 && lines.dropLast(1).count { it.length > longest * 0.6 } >= (lines.size - 1) * 0.7
        if (!wrapped) return lines.joinToString("\n")
        val sb = StringBuilder()
        for (l in lines) {
            if (sb.isEmpty()) sb.append(l)
            else if (sb.last() == '-' && sb.length > 1 && sb[sb.length - 2].isLetter() && l.firstOrNull()?.isLowerCase() == true) {
                sb.setLength(sb.length - 1); sb.append(l)
            } else sb.append(' ').append(l)
        }
        return sb.toString()
    }

    private fun stripGutenberg(t: String): String {
        val start = Regex("\\*\\*\\* ?START OF (THE|THIS) PROJECT GUTENBERG[^\\n]*\\n").find(t)
        val end = Regex("\\*\\*\\* ?END OF (THE|THIS) PROJECT GUTENBERG").find(t)
        val s = start?.range?.last?.plus(1) ?: 0
        val e = end?.range?.first ?: t.length
        return if (e > s) t.substring(s, e) else t
    }

    companion object {
        /** UTF-8 when it is valid UTF-8, otherwise the legacy code page that fits the phone's language. */
        fun decode(b: ByteArray): String {
            if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) return String(b, 3, b.size - 3, Charsets.UTF_8)
            if (b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16LE)
            if (b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16BE)
            return try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(b)).toString()
            } catch (_: CharacterCodingException) {
                val cp = when (Locale.getDefault().language) {
                    "cs", "sk", "pl", "hu", "sl", "hr", "ro", "bs", "sr" -> "windows-1250"
                    "ru", "uk", "bg", "be", "mk" -> "windows-1251"
                    "el" -> "windows-1253"
                    "tr" -> "windows-1254"
                    else -> "windows-1252"
                }
                String(b, runCatching { Charset.forName(cp) }.getOrDefault(Charsets.ISO_8859_1))
            }
        }
    }
}

/** A single HTML file (also the built-in sample). */
class HtmlParser(private val file: File) {
    fun parse(progress: (Float) -> Unit): ParsedBook {
        val doc = Jsoup.parse(TxtParser.decode(file.readBytes()))
        val lang = doc.selectFirst("html")?.attr("lang").orEmpty().ifBlank { Scripts.guessLanguage(doc.text().take(6000)) }
        val builder = BookBuilder(lang)
        progress(0.3f)
        HtmlWalker(builder, HashMap(), "doc").walk(doc.body())
        if (builder.size == 0) throw ImportException(ImportException.Reason.NO_TEXT)
        progress(1f)
        val title = doc.title()
        return ParsedBook(builder.build(), title, doc.selectFirst("meta[name=author]")?.attr("content").orEmpty(), null)
    }
}
