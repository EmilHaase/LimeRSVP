package com.limersvp.data

import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class BookStatus { IMPORTING, READY, FAILED }

@Immutable
data class BookMeta(
    val id: String,
    val title: String,
    val author: String = "",
    val format: String = "",
    val language: String = "",
    val file: String = "",
    val hash: String = "",
    val cover: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val openedAt: Long = 0L,
    val position: Int = 0,
    val total: Int = 0,
    val startToken: Int = 0,
    val status: BookStatus = BookStatus.IMPORTING,
    val error: String? = null,
    val readMs: Long = 0L,
    val words: Long = 0L,
    val finishedAt: Long = 0L,
    val version: Int = 0,
    val chapters: Int = 0,
    val chapterTitle: String = "",
    /** Units left / total at the last save, for "~2 h left" without opening the book. */
    val unitsLeft: Double = -1.0,
    val unitsTotal: Double = -1.0,
) {
    val fraction: Float
        get() = when {
            finishedAt > 0L -> 1f
            unitsTotal > 0 && unitsLeft >= 0 -> (1 - unitsLeft / unitsTotal).toFloat().coerceIn(0f, 1f)
            total > 0 -> (position.toFloat() / total).coerceIn(0f, 1f)
            else -> 0f
        }
    val finished get() = finishedAt > 0L
    val started get() = openedAt > 0L
}

class LibraryRepository(context: Context, private val scope: CoroutineScope) {
    val booksDir = File(context.filesDir, "books").apply { mkdirs() }
    val contentDir = File(context.filesDir, "content").apply { mkdirs() }
    val coverDir = File(context.filesDir, "covers").apply { mkdirs() }

    private val file = AtomicFile(File(context.filesDir, "library.json"))
    private val _books = MutableStateFlow(load())
    val books: StateFlow<List<BookMeta>> = _books.asStateFlow()
    private val lock = Mutex()
    private var saveJob: Job? = null

    init {
        // A book that was being imported when the app died will never finish by itself.
        _books.update { list ->
            list.map {
                if (it.status == BookStatus.IMPORTING) it.copy(status = BookStatus.FAILED, error = ERR_INTERRUPTED) else it
            }
        }
    }

    fun get(id: String): BookMeta? = _books.value.firstOrNull { it.id == id }
    fun byHash(hash: String): BookMeta? = _books.value.firstOrNull { it.hash == hash }

    fun upsert(meta: BookMeta) {
        _books.update { list ->
            val i = list.indexOfFirst { it.id == meta.id }
            if (i < 0) listOf(meta) + list else list.toMutableList().also { it[i] = meta }
        }
        scheduleSave()
    }

    fun update(id: String, block: (BookMeta) -> BookMeta) {
        _books.update { list -> list.map { if (it.id == id) block(it) else it } }
        scheduleSave()
    }

    fun remove(id: String) {
        val meta = get(id) ?: return
        _books.update { list -> list.filterNot { it.id == id } }
        scheduleSave()
        scope.launch {
            File(booksDir, meta.file).delete()
            contentFile(id).delete()
            coverFile(id).delete()
        }
    }

    fun contentFile(id: String) = File(contentDir, "$id.lrsv")
    fun coverFile(id: String) = File(coverDir, "$id.jpg")
    fun originalFile(meta: BookMeta) = File(booksDir, meta.file)

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(400)
            saveNow()
        }
    }

    fun flush() {
        saveJob?.cancel()
        scope.launch { saveNow() }
    }

    private suspend fun saveNow() = lock.withLock {
        val arr = JSONArray()
        for (b in _books.value) arr.put(b.toJson())
        val out = file.startWrite()
        try {
            out.write(arr.toString().toByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }

    private fun load(): List<BookMeta> = try {
        val text = String(file.readFully())
        val arr = JSONArray(text)
        (0 until arr.length()).mapNotNull { runCatching { arr.getJSONObject(it).toBook() }.getOrNull() }
    } catch (_: Exception) {
        emptyList()
    }

    companion object {
        const val ERR_INTERRUPTED = "interrupted"
    }
}

private fun BookMeta.toJson() = JSONObject().apply {
    put("id", id); put("title", title); put("author", author); put("format", format)
    put("language", language); put("file", file); put("hash", hash); put("cover", cover)
    put("addedAt", addedAt); put("openedAt", openedAt); put("position", position); put("total", total)
    put("startToken", startToken); put("status", status.name); put("error", error ?: "")
    put("readMs", readMs); put("words", words); put("finishedAt", finishedAt); put("version", version)
    put("chapters", chapters); put("chapterTitle", chapterTitle); put("unitsLeft", unitsLeft)
    put("unitsTotal", unitsTotal)
}

private fun JSONObject.toBook() = BookMeta(
    id = getString("id"),
    title = optString("title"),
    author = optString("author"),
    format = optString("format"),
    language = optString("language"),
    file = optString("file"),
    hash = optString("hash"),
    cover = optBoolean("cover"),
    addedAt = optLong("addedAt"),
    openedAt = optLong("openedAt"),
    position = optInt("position"),
    total = optInt("total"),
    startToken = optInt("startToken"),
    status = runCatching { BookStatus.valueOf(optString("status")) }.getOrDefault(BookStatus.FAILED),
    error = optString("error").ifEmpty { null },
    readMs = optLong("readMs"),
    words = optLong("words"),
    finishedAt = optLong("finishedAt"),
    version = optInt("version"),
    chapters = optInt("chapters"),
    chapterTitle = optString("chapterTitle"),
    unitsLeft = optDouble("unitsLeft", -1.0),
    unitsTotal = optDouble("unitsTotal", -1.0),
)
