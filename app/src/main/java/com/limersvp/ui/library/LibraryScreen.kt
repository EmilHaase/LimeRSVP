package com.limersvp.ui.library

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.limersvp.Container
import com.limersvp.R
import com.limersvp.data.BookMeta
import com.limersvp.data.BookStatus
import com.limersvp.data.ReaderTheme
import com.limersvp.feel.LocalReduceMotion
import com.limersvp.feel.breathe
import com.limersvp.feel.rememberBreather
import com.limersvp.parse.ImportEvent
import com.limersvp.parse.ImportException
import com.limersvp.ui.Glyphs
import com.limersvp.ui.components.HoldButton
import com.limersvp.ui.components.IconBtn
import com.limersvp.ui.components.Pill
import com.limersvp.ui.format.Fmt
import com.limersvp.ui.theme.LocalPalette
import com.limersvp.ui.theme.Numbers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private val MIME = arrayOf("application/epub+zip", "application/pdf", "text/plain", "text/html", "application/octet-stream", "application/xhtml+xml")

@Composable
fun LibraryScreen(c: Container, onOpen: (String) -> Unit) {
    val p = LocalPalette.current
    val books by c.library.books.collectAsStateWithLifecycle()
    val progress by c.importer.progress.collectAsStateWithLifecycle()
    val settings by c.settings.state.collectAsStateWithLifecycle()
    var notice by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { c.importer.import(it) }
    }
    LaunchedEffect(Unit) {
        c.importer.events.collect { e ->
            if (e is ImportEvent.Already && !e.open) notice = "Already in your library"
        }
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2600); notice = null
        }
    }

    val sorted = remember(books) { books.sortedByDescending { maxOf(it.openedAt, it.addedAt) } }
    val hero = sorted.firstOrNull { it.status == BookStatus.READY && it.started && !it.finished }
    val rest = sorted.filter { it.id != hero?.id }

    Box(Modifier.fillMaxSize()) {
        if (books.isEmpty()) {
            Empty(onAdd = { picker.launch(MIME) }, onSample = { c.importer.importResource(R.raw.sample, "LimeRSVP.html", open = true) })
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(148.dp),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "head") {
                    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Library", style = MaterialTheme.typography.headlineLarge, color = p.text, modifier = Modifier.weight(1f))
                        IconBtn(Glyphs.Plus, { picker.launch(MIME) }, tint = p.onAccent, background = p.accent, size = 46.dp)
                    }
                }
                if (hero != null) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "hero-" + hero.id) {
                        Continue(c, hero, settings.wpm, settings.theme, onOpen)
                    }
                }
                items(rest, key = { it.id }) { b ->
                    BookCard(c, b, progress[b.id], settings.wpm, settings.theme, onOpen, Modifier.animateItem())
                }
            }
        }
        AnimatedVisibility(
            notice != null, enter = fadeIn(tween(160)), exit = fadeOut(tween(220)),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp),
        ) {
            Text(
                notice.orEmpty(), color = p.text, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(p.raised).padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun Empty(onAdd: () -> Unit, onSample: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LimeMark(Modifier.size(84.dp))
        Spacer(Modifier.height(28.dp))
        Text("Add an EPUB or PDF", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(24.dp))
        Pill("Add a book", onAdd, icon = Glyphs.Plus)
        Spacer(Modifier.height(10.dp))
        Pill("Try a sample", onSample, primary = false)
        Spacer(Modifier.height(80.dp))
    }
}

/** The lime: rind, segments, and the reading notch at the top. Tap it and it turns. */
@Composable
fun LimeMark(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val breather = rememberBreather()
    Canvas(modifier.breathe(breather).combinedClickable(remember { MutableInteractionSource() }, null) { breather.breathe(0.08f) }) {
        val r = size.minDimension / 2f
        drawCircle(p.accent, r)
        drawCircle(p.bg, r * 0.86f)
        drawCircle(p.accent.copy(alpha = 0.55f), r * 0.8f)
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0 + 22.5)
            drawLine(
                p.bg, center,
                Offset(center.x + (Math.sin(a) * r * 0.8f).toFloat(), center.y - (Math.cos(a) * r * 0.8f).toFloat()),
                r * 0.07f, StrokeCap.Round,
            )
        }
        drawCircle(p.bg, r * 0.12f)
    }
}

@Composable
private fun Continue(c: Container, b: BookMeta, wpm: Int, theme: ReaderTheme, onOpen: (String) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(p.surface)
            .combinedClickable(remember { MutableInteractionSource() }, null) { onOpen(b.id) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(c, b, theme, Modifier.width(76.dp).aspectRatio(2f / 3f))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("Continue", color = p.accent, fontSize = 12.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(b.title, color = p.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (b.chapterTitle.isNotBlank()) {
                Text(b.chapterTitle, color = p.dim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(12.dp))
            Bar(b.fraction)
            Spacer(Modifier.height(6.dp))
            Text(
                Fmt.percent(b.fraction) + "  ·  " + Fmt.estimate(left(b, wpm)) + " left",
                style = Numbers.copy(fontSize = 12.sp, color = p.dim), maxLines = 1,
            )
        }
    }
}

private fun left(b: BookMeta, wpm: Int): Long? =
    if (b.unitsLeft < 0) null else (b.unitsLeft * 60_000.0 / wpm.coerceAtLeast(1)).toLong()

@Composable
private fun Bar(f: Float) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(p.track)) {
        Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).fillMaxHeight().background(p.accent))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(c: Container, b: BookMeta, importing: Float?, wpm: Int, theme: ReaderTheme, onOpen: (String) -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    var menu by remember { mutableStateOf(false) }
    val breather = rememberBreather()
    Box(modifier) {
        Column(
            Modifier
                .breathe(breather)
                .combinedClickable(
                    remember { MutableInteractionSource() }, null,
                    onLongClick = { menu = true },
                ) {
                    when (b.status) {
                        BookStatus.READY -> onOpen(b.id)
                        BookStatus.FAILED -> c.importer.retry(b.id)
                        BookStatus.IMPORTING -> breather.breathe(0.03f)
                    }
                }
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                Cover(c, b, theme, Modifier.fillMaxSize())
                if (b.status == BookStatus.READY && b.started) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp)) { Bar(b.fraction) }
                }
                if (b.status == BookStatus.IMPORTING) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(p.bg.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                        Ring(importing ?: 0f)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(b.title, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
            Text(statusLine(b, wpm), style = Numbers.copy(fontSize = 12.sp, color = if (b.status == BookStatus.FAILED) p.warn else p.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(18.dp), containerColor = p.raised) {
            if (b.status == BookStatus.READY) {
                DropdownMenuItem(
                    text = { Text(if (b.finished) "Mark as unread" else "Mark as finished", color = p.text) },
                    leadingIcon = { Icon(Glyphs.Flag, null, tint = p.dim) },
                    onClick = {
                        menu = false
                        c.library.update(b.id) { it.copy(finishedAt = if (it.finished) 0L else System.currentTimeMillis()) }
                    },
                )
                if (b.started) DropdownMenuItem(
                    text = { Text("Start over", color = p.text) },
                    leadingIcon = { Icon(Glyphs.Restart, null, tint = p.dim) },
                    onClick = {
                        menu = false
                        c.library.update(b.id) { it.copy(position = it.startToken, finishedAt = 0L, unitsLeft = it.unitsTotal) }
                    },
                )
            }
            Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                HoldButton("Hold to remove", {
                    menu = false
                    c.stats.forgetBook(b.id)
                    c.library.remove(b.id)
                })
            }
        }
    }
}

private fun statusLine(b: BookMeta, wpm: Int): String = when (b.status) {
    BookStatus.IMPORTING -> "Getting it ready"
    BookStatus.FAILED -> when (b.error) {
        ImportException.Reason.LOCKED.name -> "Locked with a password"
        ImportException.Reason.NO_TEXT.name -> "No text, only images"
        ImportException.Reason.NOT_A_BOOK.name -> "Not a book file"
        ImportException.Reason.TOO_BIG.name -> "Too big for this phone"
        else -> "Couldn't read it · tap to retry"
    }
    BookStatus.READY -> when {
        b.finished -> "Finished"
        b.started -> Fmt.percent(b.fraction) + " · " + Fmt.minutes(left(b, wpm))
        else -> Fmt.minutes(left(b, wpm))
    }
}

@Composable
private fun Ring(f: Float) {
    val p = LocalPalette.current
    val shown by animateFloatAsState(f, tween(300), label = "ring")
    Canvas(Modifier.size(40.dp)) {
        val st = 3.dp.toPx()
        drawCircle(p.track, size.minDimension / 2 - st, style = Stroke(st))
        drawArc(p.accent, -90f, 360f * shown, false, Offset(st, st), androidx.compose.ui.geometry.Size(size.width - 2 * st, size.height - 2 * st), style = Stroke(st, cap = StrokeCap.Round))
    }
}

private val covers = LruCache<String, ImageBitmap>(48)

/** The real cover, or a plain one with the title. Its space never changes when the picture lands. */
@Composable
fun Cover(c: Container, b: BookMeta, theme: ReaderTheme, modifier: Modifier) {
    val p = LocalPalette.current
    val reduce = LocalReduceMotion.current
    val file = remember(b.id) { c.library.coverFile(b.id) }
    var bmp by remember(b.id, b.cover) { mutableStateOf(covers.get(file.path)) }
    LaunchedEffect(b.id, b.cover) {
        if (bmp == null && b.cover) {
            bmp = withContext(Dispatchers.IO) { loadCover(file) }?.also { covers.put(file.path, it) }
        }
    }
    val shown by animateFloatAsState(if (bmp != null) 1f else 0f, tween(if (reduce) 0 else 220), label = "cover")
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(p.raised)) {
        if (bmp == null || shown < 1f) PlainCover(b, Modifier.fillMaxSize())
        bmp?.let {
            Image(
                it, null, contentScale = ContentScale.Crop,
                colorFilter = coverFilter(theme),
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = shown },
            )
        }
    }
}

private fun loadCover(f: File): ImageBitmap? = try {
    BitmapFactory.decodeFile(f.path)?.asImageBitmap()
} catch (_: Throwable) {
    null
}

/** In night red, pictures turn red too; in dim, they dim. */
private fun coverFilter(theme: ReaderTheme): ColorFilter? = when (theme) {
    ReaderTheme.RED -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        0.25f, 0.5f, 0.1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f,
    )))
    ReaderTheme.AMBER -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        0.3f, 0.55f, 0.1f, 0f, 0f, 0.2f, 0.4f, 0.07f, 0f, 0f, 0.05f, 0.1f, 0.02f, 0f, 0f, 0f, 0f, 0f, 1f, 0f,
    )))
    ReaderTheme.DIM -> ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(0.55f, 0.55f, 0.55f, 1f) })
    else -> null
}

@Composable
private fun PlainCover(b: BookMeta, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Box(Modifier.width(22.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(p.accent))
        Column {
            Text(
                b.title, color = p.text, fontSize = 17.sp, lineHeight = 21.sp, fontFamily = FontFamily.Serif,
                maxLines = 5, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Start,
            )
            if (b.author.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(b.author, color = p.dim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(b.format.uppercase(), color = p.faint, fontSize = 10.sp, letterSpacing = 1.5.sp)
    }
}
