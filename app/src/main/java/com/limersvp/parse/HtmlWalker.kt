package com.limersvp.parse

import com.limersvp.core.BookBuilder
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Walks an (X)HTML body into paragraphs, leaving out what should never be read: scripts, ruby
 * readings (furigana would read every word twice), footnote markers, footnotes themselves,
 * page-number markers, hidden elements, images and math.
 */
class HtmlWalker(
    private val builder: BookBuilder,
    /** "path#id" -> token, filled while walking, for the table of contents. */
    private val anchors: MutableMap<String, Int>,
    private val docPath: String,
) {
    private val para = StringBuilder()
    private var headingLevel = 0
    private var preDepth = 0

    fun walk(root: Element) {
        anchors.putIfAbsent(docPath, builder.size)
        visit(root)
        flush()
    }

    private fun flush() {
        if (para.isNotBlank()) builder.addParagraph(para.toString(), headingLevel)
        para.setLength(0)
        headingLevel = 0
    }

    private fun visit(node: Node) {
        when (node) {
            is TextNode -> {
                val t = node.wholeText
                if (preDepth > 0) para.append(t) else para.append(t.replace('\n', ' '))
            }

            is Element -> element(node)
        }
    }

    private fun element(e: Element) {
        val tag = e.normalName()
        if (tag in SKIP || isHidden(e) || isNote(e)) return
        val id = e.id()
        // Tokens are added when a paragraph ends, so size is where the current paragraph starts.
        if (id.isNotEmpty()) anchors.putIfAbsent("$docPath#$id", builder.size)
        if (tag == "br") {
            para.append('\n'); return
        }
        if (tag == "hr") {
            flush(); return
        }
        val level = headingLevelOf(e, tag)
        val block = level > 0 || tag in BLOCKS
        if (block) flush()
        if (level > 0) headingLevel = level
        if (tag == "pre") preDepth++
        // Table cells and list items read as their own short paragraphs.
        for (child in e.childNodes()) visit(child)
        if (tag == "pre") preDepth--
        if (tag == "td" || tag == "th") para.append(' ')
        if (block) flush()
    }

    private fun headingLevelOf(e: Element, tag: String): Int {
        if (tag.length == 2 && tag[0] == 'h' && tag[1] in '1'..'6') return tag[1] - '0'
        val type = e.attr("epub:type")
        if (type.contains("title") && tag != "a" && tag != "span") return 2
        if (e.attr("role") == "heading") return e.attr("aria-level").toIntOrNull()?.coerceIn(1, 6) ?: 2
        return 0
    }

    private fun isHidden(e: Element): Boolean {
        if (e.hasAttr("hidden")) return true
        val style = e.attr("style").lowercase().replace(" ", "")
        if (style.contains("display:none") || style.contains("visibility:hidden")) return true
        val cls = e.className().lowercase()
        return cls.contains("pagenum") || cls.contains("page-number") || cls.contains("pagebreak") ||
            e.attr("epub:type").contains("pagebreak") || e.attr("role") == "doc-pagebreak"
    }

    private fun isNote(e: Element): Boolean {
        val type = e.attr("epub:type").lowercase()
        val role = e.attr("role").lowercase()
        if (type.contains("noteref") || role == "doc-noteref" || role == "doc-backlink") return true
        if (type.contains("footnote") || type.contains("endnote") || type.contains("rearnote") ||
            role == "doc-footnote" || role == "doc-endnote" || role == "doc-endnotes"
        ) return true
        val tag = e.normalName()
        if (tag == "aside") return true
        // <a href="#fn3"><sup>3</sup></a> or <sup><a href="#n1">[1]</a></sup>
        if (tag == "sup" || tag == "a") {
            val text = e.text().trim()
            if (text.length <= 5 && NOTE_MARK.matches(text)) {
                val linked = (tag == "a" && e.attr("href").contains('#')) ||
                    e.selectFirst("a[href*=#]") != null ||
                    (tag == "sup" && e.parent()?.let { it.normalName() == "a" && it.attr("href").contains('#') } == true)
                if (linked) return true
            }
        }
        return false
    }

    companion object {
        private val SKIP = hashSetOf(
            "script", "style", "head", "title", "meta", "link", "rt", "rp", "noscript", "svg", "math",
            "img", "image", "video", "audio", "iframe", "object", "embed", "template", "button", "input",
            "select", "textarea", "canvas", "map", "nav",
        )
        private val BLOCKS = hashSetOf(
            "p", "div", "section", "article", "blockquote", "li", "dd", "dt", "tr", "pre", "figcaption",
            "caption", "header", "footer", "main", "body", "ul", "ol", "dl", "table", "tbody", "thead",
            "address", "center", "figure", "details", "summary", "hgroup", "poem", "stanza", "line",
        )
        private val NOTE_MARK = Regex("^[\\[(]?([0-9]{1,3}|[*†‡§]{1,3}|[ivxIVX]{1,4}|[a-z])[\\])]?$")
    }
}
