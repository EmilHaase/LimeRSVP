package com.limersvp.ui.reader

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import com.limersvp.R
import com.limersvp.core.Frame
import com.limersvp.core.HeadingFrame
import com.limersvp.core.WordFrame
import com.limersvp.data.Settings
import com.limersvp.data.WordFont
import com.limersvp.ui.theme.LocalPalette
import com.limersvp.ui.theme.Palette

/** Where the eye rests, as a share of the width. Words are placed so their ORP sits here. */
const val FOCAL_X = 0.42f

/**
 * [light] is the background: light text on black looks heavier than dark text on white, so the
 * variable font takes a little weight off in the dark themes.
 */
fun familyOf(font: WordFont, light: Boolean = false): FontFamily = when (font) {
    WordFont.CLEAR -> if (light) clearOnLight else clearOnDark
    WordFont.SANS -> FontFamily.SansSerif
    WordFont.SERIF -> FontFamily.Serif
    WordFont.MONO -> FontFamily.Monospace
}

private fun clear(regular: Int) = FontFamily(
    Font(R.font.atkinson_next, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(regular))),
    Font(R.font.atkinson_next, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(620))),
)

private val clearOnDark = clear(380)
private val clearOnLight = clear(420)

/**
 * Atkinson Hyperlegible Next covers Latin (all of Czech, Polish, German, French...) but not
 * Cyrillic, Greek or Asian scripts. A word with such letters is drawn whole in the system font,
 * never as a mix of two typefaces.
 */
fun coveredByClear(text: String): Boolean {
    for (c in text) {
        val ok = c.code < 0x0250 || c.code in 0x2000..0x206F || c.code in 0x20A0..0x20CF
        if (!ok) return false
    }
    return true
}

/**
 * The word, drawn where the eye already is. Reads the engine's position only inside the draw
 * cache, so a new word costs a redraw, never a recomposition.
 */
@Composable
fun WordStage(frame: () -> Frame, settings: Settings, modifier: Modifier = Modifier, sizeSp: Int = settings.wordSize) {
    val p = LocalPalette.current
    val measurer = rememberTextMeasurer(cacheSize = 24)
    val style = remember(settings.font, settings.bold, sizeSp, p.text, p.light) {
        TextStyle(
            fontSize = sizeSp.sp,
            fontFamily = familyOf(settings.font, p.light),
            fontWeight = if (settings.bold) FontWeight.SemiBold else FontWeight.Normal,
            color = p.text,
        )
    }
    val guide = settings.guide
    Spacer(
        modifier.drawWithCache {
            val f = frame()
            val fontPx = sizeSp.sp.toPx()
            val focal = size.width * FOCAL_X
            val baseline = size.height / 2f + fontPx * 0.36f
            val margin = 14.sp.toPx()
            when (f) {
                is WordFrame -> {
                    val placed = placeWord(measurer, f, style, p, focal, size.width, margin)
                    onDrawBehind {
                        if (guide) drawGuide(p, focal, baseline, fontPx)
                        placed?.let { (layout, x) ->
                            drawText(layout, topLeft = Offset(x, baseline - layout.firstBaseline))
                        }
                    }
                }

                is HeadingFrame -> {
                    val hs = style.copy(fontSize = (sizeSp * 0.58f).sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, lineHeight = (sizeSp * 0.78f).sp)
                    val text = buildAnnotatedString {
                        f.lines.forEachIndexed { i, l ->
                            if (i > 0) append('\n')
                            if (i == 0 && f.lines.size > 1) withStyleDim(l, p) else append(l)
                        }
                    }
                    val w = (size.width - margin * 4).toInt().coerceAtLeast(1)
                    val layout = measurer.measure(text, hs, constraints = Constraints(maxWidth = w), maxLines = 6)
                    onDrawBehind {
                        val top = size.height / 2f - layout.size.height / 2f
                        drawLine(p.orp, Offset(size.width / 2 - 14.sp.toPx(), top - 18.sp.toPx()), Offset(size.width / 2 + 14.sp.toPx(), top - 18.sp.toPx()), 2.5f.sp.toPx(), StrokeCap.Round)
                        drawText(layout, topLeft = Offset((size.width - layout.size.width) / 2f, top))
                    }
                }
            }
        }
    )
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.withStyleDim(text: String, p: Palette) {
    val s = length
    append(text.uppercase())
    // No letter spacing here: a spacing span makes Android wrap a line that fits.
    addStyle(SpanStyle(color = p.dim), s, length)
}

/**
 * Lays the word out with its ORP letter coloured, shrinking only a word that would not fit,
 * so the ORP always lands on the focal point. Returns the layout and its left x.
 */
fun placeWord(
    measurer: TextMeasurer,
    f: WordFrame,
    style: TextStyle,
    p: Palette,
    focal: Float,
    width: Float,
    margin: Float,
): Pair<TextLayoutResult, Float>? {
    if (f.text.isEmpty()) return null
    val ann = if (f.orpEnd > f.orpStart && f.orpEnd <= f.text.length) {
        AnnotatedString(f.text, listOf(AnnotatedString.Range(SpanStyle(color = p.orp), f.orpStart, f.orpEnd)))
    } else AnnotatedString(f.text)
    val wordStyle = if ((style.fontFamily == familyOf(WordFont.CLEAR, false) || style.fontFamily == familyOf(WordFont.CLEAR, true)) &&
        !coveredByClear(f.text)
    ) style.copy(fontFamily = FontFamily.SansSerif) else style
    var layout = measurer.measure(ann, wordStyle, maxLines = 1, softWrap = false)
    var cx = orpCenter(layout, f)
    val left = cx
    val right = layout.size.width - cx
    val scale = minOf(1f, (focal - margin) / left.coerceAtLeast(1f), (width - focal - margin) / right.coerceAtLeast(1f))
    if (scale < 0.999f) {
        // Never below 60%: under the critical print size reading speed falls off sharply.
        layout = measurer.measure(ann, wordStyle.copy(fontSize = wordStyle.fontSize * scale.coerceAtLeast(0.6f)), maxLines = 1, softWrap = false)
        cx = orpCenter(layout, f)
    }
    return layout to (focal - cx)
}

private fun orpCenter(layout: TextLayoutResult, f: WordFrame): Float {
    val n = layout.layoutInput.text.length
    if (n == 0) return 0f
    val a = layout.getBoundingBox(f.orpStart.coerceIn(0, n - 1))
    val b = layout.getBoundingBox((f.orpEnd - 1).coerceIn(0, n - 1))
    return (minOf(a.left, b.left) + maxOf(a.right, b.right)) / 2f
}

/** Two faint rails with a notch above and below the focal point. */
private fun DrawScope.drawGuide(p: Palette, focal: Float, baseline: Float, fontPx: Float) {
    val top = baseline - fontPx * 1.18f
    val bottom = baseline + fontPx * 0.5f
    val inset = fontPx * 0.6f
    val notch = fontPx * 0.22f
    val w = 1.2f * density
    drawLine(p.track, Offset(inset, top), Offset(size.width - inset, top), w)
    drawLine(p.track, Offset(inset, bottom), Offset(size.width - inset, bottom), w)
    drawLine(p.faint, Offset(focal, top), Offset(focal, top + notch), w * 1.6f, StrokeCap.Round)
    drawLine(p.faint, Offset(focal, bottom), Offset(focal, bottom - notch), w * 1.6f, StrokeCap.Round)
}
