package com.limersvp.ui.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limersvp.core.BookContent
import com.limersvp.core.TokenFlags
import com.limersvp.data.Settings
import com.limersvp.ui.theme.LocalPalette
import kotlin.math.abs

/**
 * The book as pages of text, with the current word lit. While two fingers scrub, it follows the
 * word smoothly (closing part of the distance every frame). In browse mode you scroll freely and
 * a tap on any word puts you there.
 */
@Composable
fun BookView(
    content: BookContent,
    highlight: () -> Int,
    follow: Boolean,
    settings: Settings,
    modifier: Modifier = Modifier,
    onPick: ((Int) -> Unit)? = null,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val p = LocalPalette.current
    val start = remember { content.paragraphOf(highlight()) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (start - 1).coerceAtLeast(0))
    val layouts = remember { HashMap<Int, TextLayoutResult>() }
    val hl by rememberUpdatedState(highlight)
    val following by rememberUpdatedState(follow)
    val family = remember(settings.font) { familyOf(settings.font) }

    LaunchedEffect(Unit) {
        // Settle the first frame so the word sits at the reading line, then follow.
        var first = true
        while (true) {
            withFrameNanos { }
            if (!following && !first) break
            val tok = hl()
            val para = content.paragraphOf(tok)
            val info = state.layoutInfo
            val viewport = info.viewportEndOffset - info.viewportStartOffset
            if (viewport <= 0) continue
            val target = viewport * 0.4f
            val item = info.visibleItemsInfo.firstOrNull { it.index == para }
            if (item == null) {
                state.scrollToItem(para, -(target.toInt()))
                continue
            }
            val layout = layouts[para]
            val local = content.starts[tok] - content.starts[content.paraStarts[para]]
            val lineTop = layout?.let {
                if (local in 0 until it.layoutInput.text.length) it.getLineTop(it.getLineForOffset(local)) else 0f
            } ?: 0f
            val y = item.offset + lineTop
            val d = y - target
            if (first) {
                state.scrollBy(d); first = false
            } else if (abs(d) > 0.5f) {
                state.scrollBy(d * 0.2f)
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            userScrollEnabled = !follow,
            contentPadding = PaddingValues(start = 26.dp, end = 26.dp, top = topPadding + 40.dp, bottom = 160.dp),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // The text dissolves into the edges instead of being cut.
                    val fade = 72.dp.toPx()
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent, (topPadding.toPx() + fade) / size.height to Color.Black,
                            1f - fade / size.height to Color.Black, 1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            items(content.paragraphCount, key = { it }) { para ->
                val range = remember(para) { content.paragraphRange(para) }
                val base = content.starts[range.first]
                val text = remember(para) { content.text.substring(base, content.ends[range.last]) }
                val heading = content.flag(range.first, TokenFlags.HEADING)
                val mine by remember(para) { derivedStateOf { hl().let { if (it in range) it else -1 } } }
                val ann = remember(text, mine, p.text) {
                    if (mine < 0) AnnotatedString(text)
                    else AnnotatedString(
                        text,
                        listOf(AnnotatedString.Range(SpanStyle(color = if (p.light) p.text else p.bg), content.starts[mine] - base, content.ends[mine] - base)),
                    )
                }
                val pick by rememberUpdatedState(onPick)
                Text(
                    ann,
                    color = if (heading) p.text else p.text.copy(alpha = 0.86f),
                    fontFamily = family,
                    fontSize = if (heading) 22.sp else 18.sp,
                    lineHeight = if (heading) 30.sp else 28.sp,
                    fontWeight = if (heading) FontWeight.SemiBold else FontWeight.Normal,
                    onTextLayout = { layouts[para] = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (heading) 26.dp else 0.dp, bottom = if (heading) 14.dp else 14.dp)
                        .drawBehind {
                            if (mine < 0) return@drawBehind
                            val l = layouts[para] ?: return@drawBehind
                            val s = (content.starts[mine] - base).coerceIn(0, l.layoutInput.text.length - 1)
                            val e = (content.ends[mine] - base - 1).coerceIn(s, l.layoutInput.text.length - 1)
                            val a = l.getBoundingBox(s)
                            val b = l.getBoundingBox(e)
                            val sameLine = l.getLineForOffset(s) == l.getLineForOffset(e)
                            val left = if (sameLine) minOf(a.left, b.left) else a.left
                            val right = if (sameLine) maxOf(a.right, b.right) else a.right
                            val pad = 3.dp.toPx()
                            drawRoundRect(
                                p.orp,
                                Offset(left - pad, a.top),
                                Size(right - left + pad * 2, a.bottom - a.top),
                                CornerRadius(6.dp.toPx()),
                            )
                        }
                        .then(
                            if (onPick != null) Modifier.pointerInput(para) {
                                detectTapGestures { pos ->
                                    val l = layouts[para] ?: return@detectTapGestures
                                    val off = l.getOffsetForPosition(pos)
                                    pick?.invoke(content.tokenAtChar(base + off))
                                }
                            } else Modifier
                        ),
                )
            }
        }
    }
}
