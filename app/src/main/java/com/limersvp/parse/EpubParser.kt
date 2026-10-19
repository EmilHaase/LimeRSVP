package com.limersvp.parse

import com.limersvp.core.BookBuilder
import com.limersvp.core.Scripts
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipFile

class ParsedBook(
    val content: com.limersvp.core.BookContent,
    val title: String,
    val author: String,
    val coverBytes: ByteArray?,
)

class ImportException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { NOT_A_BOOK, LOCKED, NO_TEXT, DAMAGED, TOO_BIG }
}

/**
 * EPUB 2 and 3: the spine in reading order, chapters from the nav document or the NCX, the
 * cover, and where the real text starts (landmarks / guide).
 */
class EpubParser(private val file: File) {

    private class Item(val id: String, val path: String, val type: String, val props: String)
    private class TocEntry(val title: String, val path: String, val depth: Int)

    fun parse(progress: (Float) -> Unit): ParsedBook {
        val zip = try {
            ZipFile(file)
        } catch (_: Exception) {
            throw ImportException(ImportException.Reason.DAMAGED)
        }
        zip.use { z ->
            val container = z.text("META-INF/container.xml") ?: throw ImportException(ImportException.Reason.NOT_A_BOOK)
            val opfPath = xml(container).allElements.firstOrNull { it.local() == "rootfile" }?.attr("full-path")
                ?.takeIf { it.isNotBlank() } ?: throw ImportException(ImportException.Reason.DAMAGED)
            val opfDir = opfPath.substringBeforeLast('/', "")
            val opf = xml(z.text(opfPath) ?: throw ImportException(ImportException.Reason.DAMAGED))

            val meta = opf.allElements.firstOrNull { it.local() == "metadata" }
            val title = meta?.children()?.firstOrNull { it.local() == "title" }?.text()?.trim().orEmpty()
            val author = meta?.children()?.filter { it.local() == "creator" }?.joinToString(", ") { it.text().trim() }.orEmpty()
            var language = meta?.children()?.firstOrNull { it.local() == "language" }?.text()?.trim().orEmpty()

            val items = LinkedHashMap<String, Item>()
            opf.allElements.filter { it.local() == "item" }.forEach {
                val href = it.attr("href")
                if (href.isNotEmpty()) items[it.attr("id")] = Item(it.attr("id"), resolve(opfDir, href), it.attr("media-type"), it.attr("properties"))
            }
            val spineEl = opf.allElements.firstOrNull { it.local() == "spine" }
            val spine = spineEl?.children()?.filter { it.local() == "itemref" && it.attr("linear") != "no" }
                ?.mapNotNull { items[it.attr("idref")] }
                ?.filter { it.type.contains("html") || it.path.endsWith("html", true) || it.path.endsWith("htm", true) }
                .orEmpty()
            if (spine.isEmpty()) throw ImportException(ImportException.Reason.NO_TEXT)

            // Table of contents.
            val navItem = items.values.firstOrNull { it.props.split(' ').contains("nav") }
            val ncxItem = spineEl?.attr("toc")?.let { items[it] }
                ?: items.values.firstOrNull { it.type == "application/x-dtbncx+xml" }
            var landmarkPath: String? = null
            val toc = ArrayList<TocEntry>()
            navItem?.let { nav ->
                z.text(nav.path)?.let { html ->
                    val doc = Jsoup.parse(html)
                    val navDir = nav.path.substringBeforeLast('/', "")
                    val navs = doc.select("nav")
                    val tocNav = navs.firstOrNull { it.attr("epub:type").contains("toc") } ?: navs.firstOrNull()
                    tocNav?.selectFirst("ol")?.let { readNavList(it, navDir, 0, toc) }
                    navs.firstOrNull { it.attr("epub:type").contains("landmarks") }
                        ?.select("a")?.firstOrNull { it.attr("epub:type").contains("bodymatter") }
                        ?.let { landmarkPath = resolve(navDir, it.attr("href")) }
                }
            }
            if (toc.size < 2 && ncxItem != null) {
                z.text(ncxItem.path)?.let { ncx ->
                    val doc = xml(ncx)
                    val ncxDir = ncxItem.path.substringBeforeLast('/', "")
                    val navMap = doc.allElements.firstOrNull { it.local() == "navMap" }
                    if (navMap != null) {
                        toc.clear()
                        readNavPoints(navMap, ncxDir, 0, toc)
                    }
                }
            }
            if (landmarkPath == null) {
                opf.allElements.firstOrNull { it.local() == "reference" && (it.attr("type") == "text" || it.attr("type") == "start" || it.attr("type") == "bodymatter") }
                    ?.let { landmarkPath = resolve(opfDir, it.attr("href")) }
            }

            if (language.isBlank()) {
                val sample = z.text(spine[minOf(spine.size - 1, spine.size / 2)].path)?.let { Jsoup.parse(it).text().take(4000) }.orEmpty()
                language = Scripts.guessLanguage(sample)
            }

            // The text, in reading order.
            val builder = BookBuilder(language)
            val anchors = HashMap<String, Int>()
            val skipPaths = setOfNotNull(navItem?.path)
            for ((i, item) in spine.withIndex()) {
                progress(i.toFloat() / spine.size)
                if (item.path in skipPaths && spine.size > 1) continue
                val html = z.text(item.path) ?: continue
                val doc = try {
                    Jsoup.parse(html)
                } catch (_: Exception) {
                    continue
                }
                if (isWholeDocNotes(doc)) continue
                HtmlWalker(builder, anchors, item.path).walk(doc.body())
            }
            if (builder.size == 0) throw ImportException(ImportException.Reason.NO_TEXT)

            // Chapters: every TOC entry that points somewhere we kept.
            for (e in toc) {
                val token = anchors[e.path] ?: anchors[e.path.substringBefore('#')] ?: continue
                builder.markChapter(e.title, e.depth, token)
            }
            val partial = builder.build()
            val landmark = landmarkPath?.let { anchors[it] ?: anchors[it.substringBefore('#')] }
            val start = Heuristics.startToken(partial.chapters, partial.size, landmark)
            val content = builder.build(start)

            progress(1f)
            return ParsedBook(
                content = content,
                title = title,
                author = author,
                coverBytes = findCover(z, opf, items, spine),
            )
        }
    }

    private fun readNavList(ol: Element, dir: String, depth: Int, out: MutableList<TocEntry>) {
        for (li in ol.children().filter { it.normalName() == "li" }) {
            val a = li.children().firstOrNull { it.normalName() == "a" } ?: li.selectFirst("a")
            val title = (a ?: li.children().firstOrNull { it.normalName() == "span" })?.text()?.trim().orEmpty()
            val href = a?.attr("href").orEmpty()
            if (title.isNotEmpty() && href.isNotEmpty()) out.add(TocEntry(title, resolve(dir, href), depth))
            li.children().firstOrNull { it.normalName() == "ol" }?.let { readNavList(it, dir, depth + 1, out) }
        }
    }

    private fun readNavPoints(parent: Element, dir: String, depth: Int, out: MutableList<TocEntry>) {
        for (np in parent.children().filter { it.local() == "navPoint" }) {
            val label = np.children().firstOrNull { it.local() == "navLabel" }?.text()?.trim().orEmpty()
            val src = np.children().firstOrNull { it.local() == "content" }?.attr("src").orEmpty()
            if (label.isNotEmpty() && src.isNotEmpty()) out.add(TocEntry(label, resolve(dir, src), depth))
            readNavPoints(np, dir, depth + 1, out)
        }
    }

    /** A document that is nothing but endnotes. */
    private fun isWholeDocNotes(doc: Document): Boolean {
        val body = doc.body() ?: return true
        val types = (body.attr("epub:type") + " " + (body.children().singleOrNull()?.attr("epub:type") ?: "")).lowercase()
        return types.contains("endnotes") || types.contains("footnotes") || types.contains("rearnotes")
    }

    private fun findCover(z: ZipFile, opf: Document, items: Map<String, Item>, spine: List<Item>): ByteArray? {
        val byProp = items.values.firstOrNull { it.props.split(' ').contains("cover-image") }
        val byMeta = opf.allElements.firstOrNull { it.local() == "meta" && it.attr("name") == "cover" }
            ?.attr("content")?.let { items[it] }
        val byName = items.values.firstOrNull { it.type.startsWith("image/") && it.id.contains("cover", true) }
        val img = listOfNotNull(byProp, byMeta, byName).firstOrNull { it.type.startsWith("image/") && !it.type.contains("svg") }
        if (img != null) return z.bytes(img.path)
        // Otherwise the first picture of the first document.
        val first = spine.firstOrNull() ?: return null
        val html = z.text(first.path) ?: return null
        val doc = Jsoup.parse(html)
        val src = doc.selectFirst("img[src]")?.attr("src") ?: doc.selectFirst("image")?.let { it.attr("xlink:href").ifEmpty { it.attr("href") } }
        return src?.takeIf { it.isNotBlank() }?.let { z.bytes(resolve(first.path.substringBeforeLast('/', ""), it)) }
    }

    companion object {
        fun xml(s: String): Document = Jsoup.parse(s, "", Parser.xmlParser())

        private fun Element.local() = tagName().substringAfter(':')

        fun ZipFile.text(path: String): String? = bytes(path)?.let { decode(it) }

        fun ZipFile.bytes(path: String): ByteArray? {
            val entry = getEntry(path) ?: getEntry(path.removePrefix("/"))
                ?: entries().asSequence().firstOrNull { it.name.equals(path, ignoreCase = true) }
                ?: return null
            if (entry.size > 64L * 1024 * 1024) return null
            return getInputStream(entry).use { it.readBytes() }
        }

        private fun decode(b: ByteArray): String {
            // XHTML is UTF-8 in practice; honour a UTF-16 BOM when present.
            if (b.size >= 2 && ((b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()) || (b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()))) {
                return String(b, Charsets.UTF_16)
            }
            val s = String(b, Charsets.UTF_8)
            return if (s.startsWith('\uFEFF')) s.substring(1) else s
        }

        /** Resolve [href] against the folder [dir] inside the zip, dropping "./" and "../". */
        fun resolve(dir: String, href: String): String {
            val decoded = try {
                URLDecoder.decode(href.replace("+", "%2B"), "UTF-8")
            } catch (_: Exception) {
                href
            }
            val frag = decoded.substringAfter('#', "")
            val path = decoded.substringBefore('#')
            val parts = ArrayList<String>()
            if (!path.startsWith("/") && dir.isNotEmpty()) parts.addAll(dir.split('/'))
            for (p in path.split('/')) {
                when (p) {
                    "", "." -> {}
                    ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                    else -> parts.add(p)
                }
            }
            val joined = parts.joinToString("/")
            return if (frag.isNotEmpty()) "$joined#$frag" else if (path.isEmpty()) "$dir#$frag" else joined
        }
    }
}
