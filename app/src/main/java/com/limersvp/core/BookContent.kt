package com.limersvp.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/** A chapter as the book's own table of contents (or our heuristics) names it. */
class Chapter(val title: String, val depth: Int, val token: Int)

object TokenFlags {
    /** A paragraph ends after this token. */
    const val PARA_END = 1

    /** The token belongs to a heading paragraph. Headings are shown as a title card. */
    const val HEADING = 2

    /** A hard line break (poetry, addresses) follows this token. */
    const val LINE_END = 4

    /** No space before this token in the flowing text (CJK, Thai...). */
    const val GLUE = 8
}

/**
 * The whole readable text of a book, flattened.
 *
 * [text] is the flowing text: tokens separated by a space (or nothing for glued scripts),
 * paragraphs separated by '\n'. Token i is `text[starts[i], ends[i])`. Keeping one string plus
 * index arrays keeps a 600k-word novel at a few MB instead of half a million String objects.
 */
class BookContent(
    val text: String,
    val starts: IntArray,
    val ends: IntArray,
    val flags: ByteArray,
    val chapters: List<Chapter>,
    /** Where the real content begins (after cover, copyright, contents...). */
    val startToken: Int,
    val language: String,
) {
    val size: Int get() = starts.size

    fun token(i: Int): String = text.substring(starts[i], ends[i])

    fun flag(i: Int, f: Int): Boolean = (flags[i].toInt() and f) != 0

    /** First token of every paragraph. */
    val paraStarts: IntArray by lazy {
        val out = IntList()
        if (size > 0) out.add(0)
        for (i in 0 until size - 1) if (flag(i, TokenFlags.PARA_END)) out.add(i + 1)
        out.toArray()
    }

    val paragraphCount: Int get() = paraStarts.size

    fun paragraphOf(token: Int): Int = floorIndex(paraStarts, token)

    fun paragraphRange(p: Int): IntRange {
        val s = paraStarts[p]
        val e = if (p + 1 < paraStarts.size) paraStarts[p + 1] - 1 else size - 1
        return s..e
    }

    fun paragraphText(p: Int): String {
        val r = paragraphRange(p)
        return text.substring(starts[r.first], ends[r.last])
    }

    /** Index into [chapters] of the chapter containing [token], or -1 before the first one. */
    fun chapterIndexOf(token: Int): Int {
        var lo = 0
        var hi = chapters.size - 1
        var best = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (chapters[mid].token <= token) {
                best = mid; lo = mid + 1
            } else hi = mid - 1
        }
        return best
    }

    /** End (exclusive) token of chapter [index]. */
    fun chapterEnd(index: Int): Int {
        val start = chapters[index].token
        for (j in index + 1 until chapters.size) if (chapters[j].token > start) return chapters[j].token
        return size
    }

    /** The token at or before the character [offset] of [text]. */
    fun tokenAtChar(offset: Int): Int {
        var lo = 0
        var hi = size - 1
        var best = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (starts[mid] <= offset) {
                best = mid; lo = mid + 1
            } else hi = mid - 1
        }
        return best
    }

    /** Start of the sentence containing [token] (never crossing a paragraph). */
    fun sentenceStart(token: Int, maxBack: Int = 40): Int {
        var i = token
        var steps = 0
        while (i > 0 && steps < maxBack) {
            val prev = i - 1
            if (flag(prev, TokenFlags.PARA_END)) break
            if (Punctuation.endsSentence(token(prev))) break
            i--; steps++
        }
        return if (steps >= maxBack) token else i
    }

    fun sentenceEnd(token: Int, maxForward: Int = 40): Int {
        var i = token
        var steps = 0
        while (i < size - 1 && steps < maxForward) {
            if (flag(i, TokenFlags.PARA_END)) break
            if (Punctuation.endsSentence(token(i))) break
            i++; steps++
        }
        return i
    }

    // ---- storage ---------------------------------------------------------------------------

    fun writeTo(file: File) {
        val w = ByteWriter()
        w.bytes(MAGIC)
        w.varInt(FORMAT)
        w.string(language)
        w.varInt(startToken)
        w.string(text)
        w.varInt(size)
        var prevEnd = 0
        for (i in 0 until size) {
            w.varInt(starts[i] - prevEnd)
            w.varInt(ends[i] - starts[i])
            prevEnd = ends[i]
        }
        w.bytes(flags)
        w.varInt(chapters.size)
        for (c in chapters) {
            w.string(c.title); w.varInt(c.depth); w.varInt(c.token)
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        DeflaterOutputStream(tmp.outputStream().buffered(1 shl 16), Deflater(Deflater.BEST_SPEED)).use {
            it.write(w.buf.toByteArray())
        }
        if (!tmp.renameTo(file)) {
            file.delete(); tmp.renameTo(file)
        }
    }

    companion object {
        private val MAGIC = byteArrayOf('L'.code.toByte(), 'R'.code.toByte(), 'S'.code.toByte(), 'V'.code.toByte())

        /** Bump when the parsers change what they produce; old caches are re-parsed. */
        const val FORMAT = 4

        fun readFrom(file: File): BookContent? = try {
            read(file)
        } catch (_: Exception) {
            null
        }

        private fun read(file: File): BookContent? {
            val data = InflaterInputStream(file.inputStream().buffered(1 shl 16)).use { it.readBytes() }
            val r = ByteReader(data)
            for (b in MAGIC) if (r.byte() != b) return null
            if (r.varInt() != FORMAT) return null
            val language = r.string()
            val startToken = r.varInt()
            val text = r.string()
            val n = r.varInt()
            val starts = IntArray(n)
            val ends = IntArray(n)
            var prevEnd = 0
            for (i in 0 until n) {
                starts[i] = prevEnd + r.varInt()
                ends[i] = starts[i] + r.varInt()
                prevEnd = ends[i]
            }
            val flags = r.bytes(n)
            val cn = r.varInt()
            val chapters = ArrayList<Chapter>(cn)
            repeat(cn) { chapters.add(Chapter(r.string(), r.varInt(), r.varInt())) }
            return BookContent(text, starts, ends, flags, chapters, startToken.coerceIn(0, (n - 1).coerceAtLeast(0)), language)
        }
    }
}

fun floorIndex(sorted: IntArray, value: Int): Int {
    var lo = 0
    var hi = sorted.size - 1
    var best = 0
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (sorted[mid] <= value) {
            best = mid; lo = mid + 1
        } else hi = mid - 1
    }
    return best
}

class IntList(capacity: Int = 1024) {
    private var a = IntArray(capacity)
    var size = 0
        private set

    fun add(v: Int) {
        if (size == a.size) a = a.copyOf(a.size * 2)
        a[size++] = v
    }

    operator fun get(i: Int) = a[i]
    operator fun set(i: Int, v: Int) {
        a[i] = v
    }

    fun toArray(): IntArray = a.copyOf(size)
}

class ByteList(capacity: Int = 1024) {
    private var a = ByteArray(capacity)
    var size = 0
        private set

    fun add(v: Int) {
        if (size == a.size) a = a.copyOf(a.size * 2)
        a[size++] = v.toByte()
    }

    operator fun get(i: Int) = a[i].toInt()
    fun or(i: Int, f: Int) {
        a[i] = (a[i].toInt() or f).toByte()
    }

    fun toArray(): ByteArray = a.copyOf(size)
}

private class ByteWriter {
    val buf = ByteArrayOutputStream(1 shl 20)
    fun bytes(b: ByteArray) = buf.write(b)
    fun varInt(v: Int) {
        var x = v
        while (x and 0x7F.inv() != 0) {
            buf.write((x and 0x7F) or 0x80); x = x ushr 7
        }
        buf.write(x)
    }

    fun string(s: String) {
        val b = s.toByteArray(Charsets.UTF_8)
        varInt(b.size); buf.write(b)
    }
}

private class ByteReader(private val d: ByteArray) {
    private var p = 0
    fun byte(): Byte = d[p++]
    fun varInt(): Int {
        var shift = 0
        var result = 0
        while (true) {
            val b = d[p++].toInt()
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
    }

    fun string(): String {
        val n = varInt()
        val s = String(d, p, n, Charsets.UTF_8)
        p += n
        return s
    }

    fun bytes(n: Int): ByteArray {
        val out = d.copyOfRange(p, p + n); p += n; return out
    }
}
