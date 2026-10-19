package com.limersvp.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limersvp.core.BookContent
import com.limersvp.core.WordFrame
import com.limersvp.data.Highlight
import com.limersvp.data.PauseLevel
import com.limersvp.data.RampLevel
import com.limersvp.data.ReaderTheme
import com.limersvp.data.Settings
import com.limersvp.data.WordFont
import com.limersvp.ui.Glyphs
import com.limersvp.ui.components.Choices
import com.limersvp.ui.components.SettingRow
import com.limersvp.ui.components.Toggle
import com.limersvp.ui.format.Fmt
import com.limersvp.ui.theme.LocalPalette
import com.limersvp.ui.theme.Numbers
import com.limersvp.ui.theme.Palettes

/** Every chapter with how long it takes at the current speed; the current one is lit. */
@Composable
fun ChapterList(
    content: BookContent,
    position: Int,
    msOf: (Int, Int) -> Long?,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val current = content.chapterIndexOf(position)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (current - 2).coerceAtLeast(0))
    Column(modifier) {
        Text("Chapters", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 24.dp, top = 22.dp, bottom = 8.dp))
        if (content.chapters.isEmpty()) {
            Text("This book has no chapters.", color = p.dim, fontSize = 15.sp, modifier = Modifier.padding(24.dp))
            return@Column
        }
        LazyColumn(state = state, modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(content.chapters) { i, ch ->
                val start = ch.token
                val end = content.chapterEnd(i)
                val done = end <= position
                val isCurrent = i == current
                val ms = msOf(start, end)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable(remember { MutableInteractionSource() }, null) { onPick(i) }
                        .padding(start = (24 + 14 * ch.depth.coerceAtMost(3)).dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(26.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isCurrent) p.accent else Color.Transparent)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            ch.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 15.sp,
                            color = if (done && !isCurrent) p.dim else p.text,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        if (isCurrent && end > start) {
                            val f = ((position - start).toFloat() / (end - start)).coerceIn(0f, 1f)
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.fillMaxWidth(0.6f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(p.track)) {
                                Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(p.accent))
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    if (done && !isCurrent) {
                        Icon(Glyphs.Check, null, tint = p.dim, modifier = Modifier.size(18.dp))
                    } else {
                        val left = if (isCurrent) msOf(position, end) else ms
                        Text(Fmt.minutes(left), style = Numbers.copy(fontSize = 13.sp, color = p.dim))
                    }
                }
            }
        }
    }
}

/** What matters while reading, one tap from the word. The word above shows each change live. */
@Composable
fun ReaderOptions(
    settings: Settings,
    sample: WordFrame,
    onChange: ((Settings) -> Settings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        // The real word, at the real size, in the real colours.
        WordStage({ sample }, settings, Modifier.fillMaxWidth().height(120.dp))
        Text("Size", color = p.dim, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp))
        Slider(
            value = settings.wordSize.toFloat(),
            onValueChange = { v -> onChange { it.copy(wordSize = v.toInt()) } },
            valueRange = 26f..72f,
            colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.track),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Label("Theme")
        ThemeDots(settings.theme) { t -> onChange { it.copy(theme = t) } }
        if (settings.theme != ReaderTheme.RED && settings.theme != ReaderTheme.AMBER) {
            Label("Highlight")
            HighlightDots(settings) { h -> onChange { it.copy(highlight = h) } }
        }
        Label("Font")
        Choices(WordFont.entries, settings.font, { fontName(it) }, { f -> onChange { it.copy(font = f) } }, Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(8.dp))
        SettingRow("Bold", onClick = { onChange { it.copy(bold = !it.bold) } }) { Toggle(settings.bold) }
        SettingRow("Focus guide", onClick = { onChange { it.copy(guide = !it.guide) } }) { Toggle(settings.guide) }
        SettingRow("Sentence when stopped", onClick = { onChange { it.copy(context = !it.context) } }) { Toggle(settings.context) }
        Label("Pause after sentences")
        Choices(PauseLevel.entries, settings.pauses, { pauseName(it) }, { v -> onChange { it.copy(pauses = v) } }, Modifier.padding(horizontal = 20.dp))
        Label("Speed-up when you start")
        Choices(RampLevel.entries, settings.ramp, { rampName(it) }, { v -> onChange { it.copy(ramp = v) } }, Modifier.padding(horizontal = 20.dp))
        Label("Screen dimmer")
        Slider(
            value = settings.dim,
            onValueChange = { v -> onChange { it.copy(dim = v) } },
            valueRange = 0f..0.85f,
            colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.track),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = LocalPalette.current.dim, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 8.dp))
}

fun fontName(f: WordFont) = when (f) {
    WordFont.SANS -> "Sans"; WordFont.SERIF -> "Serif"; WordFont.MONO -> "Mono"; WordFont.CONDENSED -> "Narrow"
}

fun pauseName(v: PauseLevel) = when (v) {
    PauseLevel.NONE -> "None"; PauseLevel.LIGHT -> "Light"; PauseLevel.NORMAL -> "Normal"; PauseLevel.LONG -> "Long"
}

fun rampName(v: RampLevel) = when (v) {
    RampLevel.OFF -> "Off"; RampLevel.QUICK -> "Quick"; RampLevel.GENTLE -> "Gentle"; RampLevel.SLOW -> "Slow"
}

fun themeName(t: ReaderTheme) = when (t) {
    ReaderTheme.OLED -> "Black"; ReaderTheme.RED -> "Night red"; ReaderTheme.AMBER -> "Amber"
    ReaderTheme.DIM -> "Dim"; ReaderTheme.DAY -> "Day"
}

/** Each theme as a little swatch of itself: its background with its text and highlight. */
@Composable
fun ThemeDots(selected: ReaderTheme, onPick: (ReaderTheme) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ReaderTheme.entries.forEach { t ->
            val tp = Palettes.of(t, Highlight.LIME)
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(remember { MutableInteractionSource() }, null) { onPick(t) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (t == selected) p.accent else p.track)
                        .padding(2.5.dp)
                        .clip(CircleShape)
                        .background(tp.bg),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("a", color = tp.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("b", color = tp.orp, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(themeName(t), color = if (t == selected) p.text else p.dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun HighlightDots(settings: Settings, onPick: (Highlight) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Highlight.entries.forEach { h ->
            val c = Palettes.of(settings.theme, h).orp
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (h == settings.highlight) p.text else Color.Transparent)
                    .padding(2.5.dp)
                    .clip(CircleShape)
                    .background(p.bg)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(if (h == Highlight.NONE) p.track else c)
                    .clickable(remember { MutableInteractionSource() }, null) { onPick(h) },
            )
        }
    }
}
