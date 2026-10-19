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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Immutable
data class DayStat(val day: LocalDate, val ms: Long = 0, val words: Long = 0, val sessions: Int = 0)

@Immutable
data class Session(val bookId: String, val start: Long, val end: Long, val ms: Long, val words: Long)

@Immutable
data class StatsState(
    val days: Map<LocalDate, DayStat> = emptyMap(),
    val sessions: List<Session> = emptyList(),
) {
    val totalMs get() = days.values.sumOf { it.ms }
    val totalWords get() = days.values.sumOf { it.words }

    /** Real reading speed: words passed per minute of actual reading. Null when unknown. */
    val avgWpm: Int?
        get() = if (totalMs < 30_000) null else (totalWords * 60_000.0 / totalMs).toInt()

    fun day(d: LocalDate) = days[d] ?: DayStat(d)

    /** Days in a row with at least a minute of reading, ending today (or yesterday). */
    fun streak(today: LocalDate = LocalDate.now()): Int {
        var d = if (day(today).ms >= 60_000) today else today.minusDays(1)
        var n = 0
        while (day(d).ms >= 60_000) {
            n++; d = d.minusDays(1)
        }
        return n
    }
}

class StatsRepository(context: Context, private val scope: CoroutineScope) {
    private val file = AtomicFile(File(context.filesDir, "stats.json"))
    private val _state = MutableStateFlow(load())
    val state: StateFlow<StatsState> = _state.asStateFlow()
    private val lock = Mutex()
    private var saveJob: Job? = null

    /** One stretch of actual reading (finger down to finger up). */
    fun record(bookId: String, startEpochMs: Long, ms: Long, words: Int) {
        if (ms <= 0) return
        val zone = ZoneId.systemDefault()
        val day = Instant.ofEpochMilli(startEpochMs).atZone(zone).toLocalDate()
        val end = startEpochMs + ms
        _state.update { s ->
            val last = s.sessions.lastOrNull()
            val continues = last != null && last.bookId == bookId && startEpochMs - last.end < SESSION_GAP_MS &&
                Instant.ofEpochMilli(last.start).atZone(zone).toLocalDate() == day
            val sessions = if (continues) {
                s.sessions.dropLast(1) + last!!.copy(end = end, ms = last.ms + ms, words = last.words + words)
            } else (s.sessions + Session(bookId, startEpochMs, end, ms, words.toLong())).takeLast(MAX_SESSIONS)
            val d = s.day(day)
            val nd = d.copy(ms = d.ms + ms, words = d.words + words, sessions = d.sessions + if (continues) 0 else 1)
            s.copy(days = s.days + (day to nd), sessions = sessions)
        }
        scheduleSave()
    }

    fun forgetBook(bookId: String) {
        _state.update { s -> s.copy(sessions = s.sessions.filterNot { it.bookId == bookId }) }
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1500); saveNow()
        }
    }

    fun flush() {
        saveJob?.cancel(); scope.launch { saveNow() }
    }

    private suspend fun saveNow() = lock.withLock {
        val s = _state.value
        val root = JSONObject()
        val days = JSONArray()
        for (d in s.days.values) days.put(JSONObject().apply {
            put("d", d.day.toString()); put("ms", d.ms); put("w", d.words); put("s", d.sessions)
        })
        val sessions = JSONArray()
        for (x in s.sessions) sessions.put(JSONObject().apply {
            put("b", x.bookId); put("st", x.start); put("en", x.end); put("ms", x.ms); put("w", x.words)
        })
        root.put("days", days); root.put("sessions", sessions)
        val out = file.startWrite()
        try {
            out.write(root.toString().toByteArray()); file.finishWrite(out)
        } catch (_: Exception) {
            file.failWrite(out)
        }
    }

    private fun load(): StatsState = try {
        val root = JSONObject(String(file.readFully()))
        val days = HashMap<LocalDate, DayStat>()
        val da = root.optJSONArray("days") ?: JSONArray()
        for (i in 0 until da.length()) {
            val o = da.getJSONObject(i)
            val d = LocalDate.parse(o.getString("d"))
            days[d] = DayStat(d, o.optLong("ms"), o.optLong("w"), o.optInt("s"))
        }
        val sa = root.optJSONArray("sessions") ?: JSONArray()
        val sessions = (0 until sa.length()).map {
            val o = sa.getJSONObject(it)
            Session(o.getString("b"), o.optLong("st"), o.optLong("en"), o.optLong("ms"), o.optLong("w"))
        }
        StatsState(days, sessions)
    } catch (_: Exception) {
        StatsState()
    }

    companion object {
        const val SESSION_GAP_MS = 120_000L
        const val MAX_SESSIONS = 400
    }
}
