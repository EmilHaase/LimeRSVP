package com.limersvp.parse

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.limersvp.core.BookBuilder
import com.limersvp.core.Punctuation
import com.limersvp.core.Scripts
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.Writer

/**
 * PDF to readable text. A PDF only knows where glyphs sit on a page, so this rebuilds what a
 * reader sees: paragraphs joined across lines and pages, words hyphenated at line ends joined
 * again, running headers, footers and page numbers dropped, footnote markers and small-print
 * footnotes left out, and headings found by their size so chapters start where they really do.
 */
class PdfParser(private val context: Context, private val file: File) {

    private class Line(
        val text: String,
        val size: Float,
        val top: Float,
        val bottom: Float,
        var paraStart: Boolean,
    )

    private class Page(val lines: MutableList<Line>, val height: Float)

    fun parse(skipFootnotes: Boolean, progress: (Float) -> Unit): ParsedBook {
        PDFBoxResourceLoader.init(context.applicationContext)
        val doc = try {
            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())
        } catch (_: InvalidPasswordException) {
            throw ImportException(ImportException.Reason.LOCKED)
        } catch (_: Exception) {
            throw ImportException(ImportException.Reason.DAMAGED)
        }
        doc.use { d ->
            if (d.isEncrypted) d.setAllSecurityToBeRemoved(true)
            val n = d.numberOfPages
            if (n == 0) throw ImportException(ImportException.Reason.NO_TEXT)
            val info = d.documentInformation
            val title = info?.title?.trim().orEmpty()
            val author = info?.author?.trim().orEmpty()

            // 1. Lines with their font size, page by page.
            val pages = ArrayList<Page>(n)
            val stripper = LineStripper()
            for (p in 1..n) {
                progress(0.85f * (p - 1) / n)
                stripper.startPage = p
                stripper.endPage = p
                stripper.reset()
                try {
                    stripper.writeText(d, NullWriter)
                } catch (_: Exception) {
                    // One broken page should not lose the book.
                }
                pages.add(Page(stripper.lines.toMutableList(), stripper.pageHeight))
            }
            val totalChars = pages.sumOf { pg -> pg.lines.sumOf { it.text.length } }
            if (totalChars < 40 * minOf(n, 5)) throw ImportException(ImportException.Reason.NO_TEXT)

            // 2. The body text size: the size most characters are set in.
            val bodySize = bodySize(pages)

            // 3. Drop running headers/footers and page numbers.
            removeFurniture(pages)
            if (skipFootnotes) removeFootnotes(pages, bodySize)

            // 4. Outline (bookmarks) -> page numbers.
            val outline = ArrayList<Triple<String, Int, Int>>() // title, page index, depth
            try {
                d.documentCatalog.documentOutline?.let { readOutline(d, it, 0, outline) }
            } catch (_: Exception) {
            }

            // 5. Paragraphs.
            val sample = pages.take(30).joinToString(" ") { pg -> pg.lines.joinToString(" ") { it.text } }.take(6000)
            val builder = BookBuilder(Scripts.guessLanguage(sample))
            val pageStartToken = IntArray(n + 1)
            val headingTokens = ArrayList<Pair<String, Int>>() // detected heading text -> token
            val para = StringBuilder()
            var paraIsHeading = false

            fun flush() {
                if (para.isNotBlank()) {
                    val start = builder.size
                    builder.addParagraph(para.toString(), if (paraIsHeading) 2 else 0)
                    if (paraIsHeading && builder.size > start) headingTokens.add(para.toString().trim() to start)
                }
                para.setLength(0)
                paraIsHeading = false
            }

            for ((pi, page) in pages.withIndex()) {
                pageStartToken[pi] = builder.size
                for ((li, line) in page.lines.withIndex()) {
                    // Big type, short line. A lone big letter is a drop cap, not a heading.
                    val heading = line.size >= bodySize * 1.25f && line.text.length <= 90 && line.text.split(' ').size <= 14 &&
                        (line.text.count { it.isLetterOrDigit() } >= 2 || line.text.trim().all { it.isDigit() })
                    val first = li == 0
                    val continuesFromLastPage = first && pi > 0 && para.isNotEmpty() && !paraIsHeading &&
                        !heading && !endsParagraph(para) && startsLower(line.text)
                    when {
                        heading -> {
                            if (!paraIsHeading) flush()
                            if (paraIsHeading && para.isNotEmpty()) para.append('\n')
                            paraIsHeading = true
                            para.append(line.text)
                        }

                        paraIsHeading -> {
                            flush(); para.append(line.text)
                        }

                        continuesFromLastPage -> joinLine(para, line.text)
                        line.paraStart || para.isEmpty() -> {
                            flush(); para.append(line.text)
                        }

                        else -> joinLine(para, line.text)
                    }
                }
                // A heading never continues onto the next page.
                if (paraIsHeading) flush()
                progress(0.85f + 0.15f * pi / n)
            }
            flush()
            pageStartToken[n] = builder.size
            if (builder.size == 0) throw ImportException(ImportException.Reason.NO_TEXT)

            // 6. Chapters: the outline, placed on the heading it names when we can find it.
            if (outline.size >= 2) {
                for ((t, pageIndex, depth) in outline) {
                    val pageTok = pageStartToken[pageIndex.coerceIn(0, n)]
                    val nextTok = pageStartToken[(pageIndex + 1).coerceIn(0, n)]
                    val norm = normalize(t)
                    val onPage = headingTokens.firstOrNull { (h, tok) -> tok in pageTok until maxOf(nextTok, pageTok + 1) && normalize(h).let { it.contains(norm) || norm.contains(it) } }
                    builder.markChapter(t, depth, onPage?.second ?: pageTok)
                }
            } else {
                // No outline: headings that look like chapters, or every big heading.
                val chapterLike = headingTokens.filter { Heuristics.looksLikeChapter(it.first.lineSequence().first()) }
                val chosen = if (chapterLike.size >= 2) chapterLike else headingTokens
                for ((h, tok) in chosen) builder.markChapter(h.replace('\n', ' '), 0, tok)
            }
            val partial = builder.build(useHeadingFallback = false)
            val start = Heuristics.startToken(partial.chapters, partial.size, null)
            progress(1f)
            return ParsedBook(
                content = builder.build(start, useHeadingFallback = false),
                title = title,
                author = author,
                coverBytes = renderCover(),
            )
        }
    }

    private fun joinLine(para: StringBuilder, next: String) {
        val n = para.length
        if (n == 0) {
            para.append(next); return
        }
        val last = para[n - 1]
        // "exam-" + "ple" -> "example", but keep "well-" + "Known" and "x-" + "1".
        if ((last == '-' || last == '‐') && n >= 2 && para[n - 2].isLetter() && startsLower(next)) {
            para.setLength(n - 1); para.append(next)
        } else if (Scripts.hasUnspaced(last.toString()) && next.isNotEmpty() && Scripts.hasUnspaced(next.take(1))) {
            para.append(next)
        } else {
            para.append(' ').append(next)
        }
    }

    private fun startsLower(s: String): Boolean {
        val c = s.firstOrNull { it.isLetterOrDigit() } ?: return false
        return c.isLowerCase()
    }

    private fun endsParagraph(sb: StringBuilder): Boolean {
        val t = sb.trimEnd().toString()
        return t.isEmpty() || Punctuation.endsSentence(t.substringAfterLast(' '))
    }

    private fun bodySize(pages: List<Page>): Float {
        val hist = HashMap<Int, Int>()
        for (p in pages) for (l in p.lines) hist.merge((l.size * 2).toInt(), l.text.length, Int::plus)
        val best = hist.maxByOrNull { it.value }?.key ?: 20
        return best / 2f
    }

    private fun digitsKey(s: String) = s.lowercase().replace(Regex("[0-9ivxlcdm]+\\b"), "#").replace(Regex("\\s+"), " ").trim()

    private val pageNumber = Regex("^[\\s\\-–—|•·\\[(]*(page|p\\.|strana|str\\.|seite|s\\.|página|pagina)?\\s*([0-9]{1,4}|[ivxlcdm]{1,6})(\\s*(of|/|z|von|de)\\s*[0-9]{1,4})?[\\s\\-–—|•·\\])]*$", RegexOption.IGNORE_CASE)

    /** Headers and footers repeat on many pages; page numbers stand alone at the edges. */
    private fun removeFurniture(pages: List<Page>) {
        val counts = HashMap<String, Int>()
        for (p in pages) {
            val edge = (p.lines.take(2) + p.lines.takeLast(2)).distinct()
            for (l in edge) counts.merge(digitsKey(l.text), 1, Int::plus)
        }
        val threshold = maxOf(3, pages.size / 5)
        for (p in pages) {
            if (p.lines.isEmpty()) continue
            val keepFrom = p.lines.indices
            val edges = (keepFrom.take(2) + keepFrom.toList().takeLast(2)).toSet()
            val drop = HashSet<Int>()
            for (i in edges) {
                val l = p.lines[i]
                val key = digitsKey(l.text)
                if (pageNumber.matches(l.text.trim())) drop.add(i)
                else if (pages.size >= 6 && (counts[key] ?: 0) >= threshold && l.text.length < 120) drop.add(i)
            }
            if (drop.isNotEmpty()) {
                val kept = p.lines.filterIndexed { i, _ -> i !in drop }
                p.lines.clear(); p.lines.addAll(kept)
            }
        }
    }

    /** Small print in the lower part of the page, below the body text, is footnotes. */
    private fun removeFootnotes(pages: List<Page>, body: Float) {
        for (p in pages) {
            if (p.lines.size < 4 || p.height <= 0f) continue
            var i = p.lines.size - 1
            while (i > 0) {
                val l = p.lines[i]
                if (l.size < body * 0.86f && l.top > p.height * 0.55f) i-- else break
            }
            val cut = i + 1
            if (cut < p.lines.size && cut > 0) {
                val footStart = p.lines[cut]
                // Only when it starts like a note: "1 ", "* ", "12. ".
                if (Regex("^([0-9]{1,3}[.)]?|[*†‡])\\s?\\S").containsMatchIn(footStart.text)) {
                    while (p.lines.size > cut) p.lines.removeAt(p.lines.size - 1)
                }
            }
        }
    }

    private fun readOutline(doc: PDDocument, node: PDOutlineNode, depth: Int, out: MutableList<Triple<String, Int, Int>>) {
        var item = node.firstChild
        var guard = 0
        while (item != null && guard++ < 5000) {
            val page: PDPage? = try {
                item.findDestinationPage(doc)
            } catch (_: Exception) {
                null
            }
            val idx = page?.let { doc.pages.indexOf(it) } ?: -1
            val title = item.title?.trim().orEmpty()
            if (idx >= 0 && title.isNotEmpty()) out.add(Triple(title, idx, depth))
            if (depth < 4) readOutline(doc, item, depth + 1, out)
            item = item.nextSibling
        }
    }

    private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun renderCover(): ByteArray? = try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                if (r.pageCount == 0) return null
                r.openPage(0).use { page ->
                    val w = 360
                    val h = (w * page.height.toFloat() / page.width).toInt().coerceIn(200, 800)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val out = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    bmp.recycle()
                    out.toByteArray()
                }
            }
        }
    } catch (_: Throwable) {
        null
    }

    /** Collects lines with their size and position instead of writing text. */
    private class LineStripper : PDFTextStripper() {
        val lines = ArrayList<Line>()
        var pageHeight = 0f
        private val cur = StringBuilder()
        private var sizes = ArrayList<Float>()
        private var top = Float.MAX_VALUE
        private var bottom = 0f
        private var nextIsParaStart = true

        init {
            setSortByPosition(false)
            setShouldSeparateByBeads(true)
            setSuppressDuplicateOverlappingText(true)
        }

        fun reset() {
            lines.clear(); cur.setLength(0); sizes.clear(); top = Float.MAX_VALUE; bottom = 0f
            nextIsParaStart = true; pageHeight = 0f
        }

        override fun startPage(page: PDPage?) {
            pageHeight = page?.mediaBox?.height ?: 0f
        }

        override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
            if (text == null) return
            var t = text
            val pos = textPositions
            if (!pos.isNullOrEmpty()) {
                // Footnote markers: tiny digits glued to the end of a word ("word¹²").
                val base = pos.first().fontSizeInPt
                var cut = t.length
                var k = pos.size - 1
                while (k > 0 && cut > 1) {
                    val u = pos[k].unicode ?: break
                    if (u.length == 1 && u[0].isDigit() && pos[k].fontSizeInPt < base * 0.75f && !t[0].isDigit()) {
                        cut--; k--
                    } else break
                }
                if (cut < t.length && cut > 0 && t.substring(cut).all { it.isDigit() }) t = t.substring(0, cut)
                for (p in pos) {
                    sizes.add(p.fontSizeInPt.takeIf { it > 0.5f } ?: p.heightDir)
                    top = minOf(top, p.yDirAdj - p.heightDir)
                    bottom = maxOf(bottom, p.yDirAdj)
                }
            }
            cur.append(t)
        }

        override fun writeWordSeparator() {
            cur.append(' ')
        }

        override fun writeLineSeparator() {
            endLine()
        }

        override fun writeParagraphStart() {
            endLine(); nextIsParaStart = true
        }

        override fun writeParagraphEnd() {
            endLine(); nextIsParaStart = true
        }

        override fun writePageStart() {}
        override fun writePageEnd() {
            endLine()
        }

        private fun endLine() {
            val text = cur.toString().trim()
            if (text.isNotEmpty()) {
                sizes.sort()
                val size = if (sizes.isEmpty()) 0f else sizes[sizes.size / 2]
                lines.add(Line(text, size, if (top == Float.MAX_VALUE) 0f else top, bottom, nextIsParaStart))
                nextIsParaStart = false
            }
            cur.setLength(0); sizes = ArrayList(); top = Float.MAX_VALUE; bottom = 0f
        }
    }

    private object NullWriter : Writer() {
        override fun write(cbuf: CharArray, off: Int, len: Int) {}
        override fun flush() {}
        override fun close() {}
    }
}
