package com.limersvp.parse

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.limersvp.core.BookContent
import com.limersvp.core.Timing
import com.limersvp.data.BookMeta
import com.limersvp.data.BookStatus
import com.limersvp.data.LibraryRepository
import com.limersvp.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

sealed interface ImportEvent {
    val id: String
    val open: Boolean

    class Ready(override val id: String, override val open: Boolean) : ImportEvent
    class Already(override val id: String, override val open: Boolean) : ImportEvent
    class Failed(override val id: String, override val open: Boolean) : ImportEvent
}

/**
 * Copies a book into the app, reads it once, and keeps the result. Parsing runs one book at a
 * time in the background and survives leaving the screen; progress is the real progress of
 * the parser (spine items, pages).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookImporter(
    private val context: Context,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val worker = Dispatchers.IO.limitedParallelism(1)
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()
    private val _events = MutableSharedFlow<ImportEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<ImportEvent> = _events.asSharedFlow()

    fun import(uri: Uri, open: Boolean = false) {
        scope.launch(worker) {
            val name = displayName(uri)
            val input = try {
                context.contentResolver.openInputStream(uri)
            } catch (_: Exception) {
                null
            } ?: return@launch
            input.use { importStream(it, name, open) }
        }
    }

    fun importResource(res: Int, name: String, open: Boolean) {
        scope.launch(worker) {
            context.resources.openRawResource(res).use { importStream(it, name, open) }
        }
    }

    fun retry(id: String) {
        scope.launch(worker) { parseInto(id, open = false) }
    }

    private suspend fun importStream(input: InputStream, name: String, open: Boolean) {
        val tmp = File(library.booksDir, "tmp-" + UUID.randomUUID())
        val md = MessageDigest.getInstance("SHA-1")
        try {
            tmp.outputStream().buffered(1 shl 16).use { out ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n); out.write(buf, 0, n)
                }
            }
        } catch (_: Exception) {
            tmp.delete(); return
        }
        val hash = md.digest().joinToString("") { "%02x".format(it) }
        library.byHash(hash)?.let { existing ->
            tmp.delete()
            if (existing.status == BookStatus.FAILED) parseInto(existing.id, open)
            else _events.tryEmit(ImportEvent.Already(existing.id, open))
            return
        }
        val format = detectFormat(tmp, name)
        val id = hash.take(12)
        val stored = File(library.booksDir, "$id.$format")
        stored.delete()
        if (!tmp.renameTo(stored)) {
            tmp.copyTo(stored, overwrite = true); tmp.delete()
        }
        library.upsert(
            BookMeta(
                id = id,
                title = name.substringBeforeLast('.').replace('_', ' ').trim().ifEmpty { name },
                format = format,
                file = stored.name,
                hash = hash,
                status = BookStatus.IMPORTING,
            )
        )
        parseInto(id, open)
    }

    private suspend fun parseInto(id: String, open: Boolean) {
        val meta = library.get(id) ?: return
        library.update(id) { it.copy(status = BookStatus.IMPORTING, error = null) }
        _progress.update { it + (id to 0f) }
        try {
            val parsed = parse(meta) { f -> _progress.update { m -> m + (id to f) } }
            val c = parsed.content
            c.writeTo(library.contentFile(id))
            val hasCover = parsed.coverBytes?.let { saveCover(it, library.coverFile(id)) } ?: false
            val prefix = Timing().prefix(c)
            val total = prefix[c.size]
            library.update(id) { m ->
                val position = when {
                    m.started -> m.position.coerceIn(0, c.size - 1)
                    settings.value.skipFrontMatter -> c.startToken
                    else -> 0
                }
                val ch = c.chapterIndexOf(position)
                m.copy(
                    title = parsed.title.trim().ifBlank { m.title },
                    author = parsed.author.trim(),
                    language = c.language,
                    total = c.size,
                    startToken = c.startToken,
                    position = position,
                    status = BookStatus.READY,
                    error = null,
                    version = BookContent.FORMAT,
                    chapters = c.chapters.size,
                    chapterTitle = if (ch >= 0) c.chapters[ch].title else "",
                    cover = hasCover,
                    unitsTotal = total,
                    unitsLeft = total - prefix[position],
                )
            }
            _events.tryEmit(ImportEvent.Ready(id, open))
        } catch (e: ImportException) {
            library.update(id) { it.copy(status = BookStatus.FAILED, error = e.reason.name) }
            _events.tryEmit(ImportEvent.Failed(id, open))
        } catch (e: OutOfMemoryError) {
            library.update(id) { it.copy(status = BookStatus.FAILED, error = ImportException.Reason.TOO_BIG.name) }
            _events.tryEmit(ImportEvent.Failed(id, open))
        } catch (e: Exception) {
            library.update(id) { it.copy(status = BookStatus.FAILED, error = ImportException.Reason.DAMAGED.name) }
            _events.tryEmit(ImportEvent.Failed(id, open))
        } finally {
            _progress.update { it - id }
        }
    }

    private fun parse(meta: BookMeta, progress: (Float) -> Unit): ParsedBook {
        val file = library.originalFile(meta)
        if (!file.exists()) throw ImportException(ImportException.Reason.DAMAGED)
        return when (meta.format) {
            "epub" -> EpubParser(file).parse(progress)
            "pdf" -> PdfParser(context, file).parse(skipFootnotes = true, progress)
            "html" -> HtmlParser(file).parse(progress)
            else -> TxtParser(file).parse(progress)
        }
    }

    /**
     * The book's text for the reader. Uses the saved result; reads the book again only when the
     * saved result is missing or was made by an older version of the parser.
     */
    suspend fun open(id: String, progress: (Float) -> Unit): BookContent? = withContext(worker) {
        val meta = library.get(id) ?: return@withContext null
        if (meta.version == BookContent.FORMAT) {
            BookContent.readFrom(library.contentFile(id))?.let { return@withContext it }
        }
        try {
            val parsed = parse(meta, progress)
            parsed.content.writeTo(library.contentFile(id))
            library.update(id) { it.copy(version = BookContent.FORMAT, total = parsed.content.size, chapters = parsed.content.chapters.size) }
            parsed.content
        } catch (_: Exception) {
            null
        }
    }

    private fun detectFormat(f: File, name: String): String {
        val head = ByteArray(8)
        val n = f.inputStream().use { it.read(head) }
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            n >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() -> "epub"
            n >= 4 && head[0] == '%'.code.toByte() && head[1] == 'P'.code.toByte() && head[2] == 'D'.code.toByte() -> "pdf"
            ext in setOf("html", "htm", "xhtml", "xht") -> "html"
            else -> {
                val start = f.inputStream().use { s -> ByteArray(512).let { b -> String(b, 0, maxOf(0, s.read(b))) } }.trimStart().lowercase()
                if (start.startsWith("<!doctype html") || start.startsWith("<html") || start.startsWith("<?xml")) "html" else "txt"
            }
        }
    }

    private fun displayName(uri: Uri): String {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { return it }
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Book"
    }

    private fun saveCover(bytes: ByteArray, out: File): Boolean = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outHeight / (sample * 2) >= 640) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        if (bmp == null) false else {
            val scaled = if (bmp.height > 640) Bitmap.createScaledBitmap(bmp, bmp.width * 640 / bmp.height, 640, true) else bmp
            val bos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 88, bos)
            out.writeBytes(bos.toByteArray())
            true
        }
    } catch (_: Throwable) {
        false
    }
}
