package com.limersvp.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.limersvp.Container
import com.limersvp.data.BookMeta
import com.limersvp.data.StatsState
import com.limersvp.ui.components.CountUp
import com.limersvp.ui.format.Fmt
import com.limersvp.ui.theme.LocalPalette
import com.limersvp.ui.theme.Numbers
import java.time.LocalDate
import java.time.format.TextStyle

@Composable
fun StatsScreen(c: Container, visible: Boolean) {
    val p = LocalPalette.current
    val stats by c.stats.state.collectAsStateWithLifecycle()
    val books by c.library.books.collectAsStateWithLifecycle()
    val settings by c.settings.state.collectAsStateWithLifecycle()
    // Numbers count up the first time the page is seen, then only when they change.
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { if (visible) seen = true }
    fun v(x: Double) = if (seen) x else 0.0

    val today = LocalDate.now()
    val todayStat = stats.day(today)
    val read = remember(books) { books.filter { it.readMs > 0 }.sortedByDescending { it.readMs } }
    val sessions = stats.sessions
    val avgSession = if (sessions.isEmpty()) null else sessions.sumOf { it.ms } / sessions.size
    val longest = sessions.maxOfOrNull { it.ms }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Statistics", style = MaterialTheme.typography.headlineLarge, color = p.text, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Today", Modifier.weight(1f)) {
                    CountUp(v(todayStat.ms.toDouble()), { Fmt.spent(it.toLong()) }, big(p.text))
                    if (settings.dailyGoal > 0) {
                        Spacer(Modifier.height(8.dp))
                        val f = (todayStat.ms / (settings.dailyGoal * 60_000f)).coerceIn(0f, 1f)
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(p.track)) {
                            Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(p.accent))
                        }
                    }
                }
                Tile("Streak", Modifier.weight(1f)) {
                    val s = stats.streak(today)
                    CountUp(v(s.toDouble()), { "${it.toInt()} ${if (s == 1) "day" else "days"}" }, big(p.text))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Real speed", Modifier.weight(1f)) {
                    val w = stats.avgWpm
                    if (w == null) Text(Fmt.UNKNOWN, style = big(p.text)) else CountUp(v(w.toDouble()), { "${it.toInt()} wpm" }, big(p.text))
                }
                Tile("Total", Modifier.weight(1f)) {
                    CountUp(v(stats.totalMs.toDouble()), { Fmt.spent(it.toLong()) }, big(p.text))
                }
            }
        }
        item { Days(stats, today, settings.dailyGoal) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Words", Modifier.weight(1f)) { CountUp(v(stats.totalWords.toDouble()), { Fmt.count(it.toLong()) }, mid(p.text)) }
                Tile("Finished", Modifier.weight(1f)) { CountUp(v(books.count { it.finished }.toDouble()), { it.toInt().toString() }, mid(p.text)) }
                Tile("Sessions", Modifier.weight(1f)) { CountUp(v(sessions.size.toDouble()), { it.toInt().toString() }, mid(p.text)) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Usual session", Modifier.weight(1f)) { Text(avgSession?.let { Fmt.spent(it) } ?: Fmt.UNKNOWN, style = mid(p.text)) }
                Tile("Longest", Modifier.weight(1f)) { Text(longest?.let { Fmt.spent(it) } ?: Fmt.UNKNOWN, style = mid(p.text)) }
            }
        }
        item { SpeedTrend(stats, today) }
        if (read.isNotEmpty()) {
            item {
                Text("Books", color = p.dim, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
            }
            items(read, key = { it.id }) { b -> BookRow(b, settings.wpm) }
        }
    }
}

private fun big(c: androidx.compose.ui.graphics.Color) = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = c)
private fun mid(c: androidx.compose.ui.graphics.Color) = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c)

@Composable
private fun Tile(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(p.surface)
            .heightIn(min = 86.dp)
            .padding(16.dp)
    ) {
        Text(label, color = p.dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

/** The last two weeks, one bar per day; the goal as a dashed line. */
@Composable
private fun Days(stats: StatsState, today: LocalDate, goalMin: Int) {
    val p = LocalPalette.current
    val days = (13 downTo 0).map { today.minusDays(it.toLong()) }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val mins = days.map { stats.day(it).ms / 60_000f }
    val top = maxOf(mins.maxOrNull() ?: 0f, goalMin.toFloat(), 5f)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.surface).padding(16.dp)) {
        Text("Last 14 days", color = p.dim, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = days.size
            val slot = size.width / n
            val bw = slot * 0.56f
            for (i in 0 until n) {
                val h = (mins[i] / top) * size.height
                val x = i * slot + (slot - bw) / 2
                val isToday = i == n - 1
                if (mins[i] <= 0f) {
                    drawRoundRect(p.track, Offset(x, size.height - 3.dp.toPx()), Size(bw, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
                } else {
                    drawRoundRect(if (isToday) p.accent else p.accent.copy(alpha = 0.45f), Offset(x, size.height - h), Size(bw, h), CornerRadius(5.dp.toPx()))
                }
            }
            if (goalMin > 0) {
                val y = size.height - goalMin / top * size.height
                drawLine(p.dim, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            days.forEach { d ->
                Text(
                    d.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                    color = if (d == today) p.text else p.faint, fontSize = 11.sp,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/** Real reading speed per day over the last month (days with at least a minute of reading). */
@Composable
private fun SpeedTrend(stats: StatsState, today: LocalDate) {
    val p = LocalPalette.current
    val pts = (29 downTo 0).mapNotNull { back ->
        val d = today.minusDays(back.toLong())
        val s = stats.day(d)
        if (s.ms >= 60_000) (29 - back) to (s.words * 60_000f / s.ms) else null
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.surface).padding(16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Speed, last 30 days", color = p.dim, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (pts.isNotEmpty()) {
                Text("${pts.minOf { it.second }.toInt()}–${pts.maxOf { it.second }.toInt()} wpm", style = Numbers.copy(fontSize = 12.sp, color = p.dim))
            }
        }
        Spacer(Modifier.height(12.dp))
        if (pts.size < 2) {
            Text(Fmt.UNKNOWN, color = p.faint, fontSize = 18.sp, modifier = Modifier.padding(vertical = 20.dp))
        } else {
            Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                val lo = pts.minOf { it.second } * 0.9f
                val hi = maxOf(pts.maxOf { it.second } * 1.05f, lo + 1f)
                fun at(i: Int, w: Float) = Offset(i / 29f * size.width, size.height - (w - lo) / (hi - lo) * size.height)
                val path = Path()
                pts.forEachIndexed { k, (i, w) -> val o = at(i, w); if (k == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
                drawPath(path, p.accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                pts.forEach { (i, w) -> drawCircle(p.accent, 3.dp.toPx(), at(i, w)) }
            }
        }
    }
}

@Composable
private fun BookRow(b: BookMeta, wpm: Int) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.surface).padding(16.dp)) {
        Text(b.title, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Row {
            Mini("Read", Fmt.spent(b.readMs))
            Mini("Speed", if (b.readMs > 30_000) "${b.words * 60_000 / b.readMs} wpm" else Fmt.UNKNOWN)
            Mini("Done", Fmt.percent(b.fraction))
            Mini("Left", if (b.finished) "0 min" else if (b.unitsLeft < 0) Fmt.UNKNOWN else Fmt.minutes((b.unitsLeft * 60_000 / wpm).toLong()))
        }
    }
}

@Composable
private fun RowScope.Mini(label: String, value: String) {
    val p = LocalPalette.current
    Column(Modifier.weight(1f)) {
        Text(value, style = Numbers.copy(fontSize = 14.sp, color = p.text), maxLines = 1)
        Text(label, color = p.faint, fontSize = 11.sp)
    }
}
