package com.limersvp.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.limersvp.data.Highlight
import com.limersvp.data.ReaderTheme

@Immutable
data class Palette(
    val bg: Color,
    val surface: Color,
    val raised: Color,
    val text: Color,
    val dim: Color,
    val faint: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val track: Color,
    val orp: Color,
    val warn: Color,
    val light: Boolean,
)

object Palettes {
    val oled = Palette(
        bg = Color(0xFF000000), surface = Color(0xFF0C0E0B), raised = Color(0xFF161914),
        text = Color(0xFFEEF1EA), dim = Color(0xFF9AA196), faint = Color(0xFF4E544A),
        accent = Color(0xFFC4F43F), accentSoft = Color(0xFF34431A), onAccent = Color(0xFF0B1002),
        track = Color(0xFF22261F), orp = Color(0xFFC4F43F), warn = Color(0xFFFF8A65), light = false,
    )

    /** Night vision: no blue or green light anywhere. Meaning never rides on colour here. */
    val red = Palette(
        bg = Color(0xFF000000), surface = Color(0xFF0B0202), raised = Color(0xFF170404),
        text = Color(0xFFB82218), dim = Color(0xFF7C150F), faint = Color(0xFF3E0A07),
        accent = Color(0xFFE5321F), accentSoft = Color(0xFF3A0905), onAccent = Color(0xFF000000),
        track = Color(0xFF260604), orp = Color(0xFFFF4A2E), warn = Color(0xFFFF4A2E), light = false,
    )

    val amber = Palette(
        bg = Color(0xFF000000), surface = Color(0xFF0D0A05), raised = Color(0xFF19130A),
        text = Color(0xFFE9B56C), dim = Color(0xFF9C7542), faint = Color(0xFF4A3719),
        accent = Color(0xFFFFB648), accentSoft = Color(0xFF3D2A0E), onAccent = Color(0xFF140C00),
        track = Color(0xFF2A1F10), orp = Color(0xFFFFD27A), warn = Color(0xFFFF8A65), light = false,
    )

    val dim = Palette(
        bg = Color(0xFF000000), surface = Color(0xFF090A08), raised = Color(0xFF111310),
        text = Color(0xFF8B9087), dim = Color(0xFF5D6259), faint = Color(0xFF30342D),
        accent = Color(0xFF86A532), accentSoft = Color(0xFF1F2810), onAccent = Color(0xFF050700),
        track = Color(0xFF181B16), orp = Color(0xFFA6CB3C), warn = Color(0xFFB0705A), light = false,
    )

    val day = Palette(
        bg = Color(0xFFF4F2EB), surface = Color(0xFFFFFFFF), raised = Color(0xFFE9E6DC),
        text = Color(0xFF15170F), dim = Color(0xFF5A5F53), faint = Color(0xFFA9AC9F),
        accent = Color(0xFF4F7A00), accentSoft = Color(0xFFD6E6B0), onAccent = Color(0xFFFFFFFF),
        track = Color(0xFFDAD7CB), orp = Color(0xFFD02A37), warn = Color(0xFFC0392B), light = true,
    )

    fun of(theme: ReaderTheme, highlight: Highlight): Palette {
        val base = when (theme) {
            ReaderTheme.OLED -> oled
            ReaderTheme.RED -> red
            ReaderTheme.AMBER -> amber
            ReaderTheme.DIM -> dim
            ReaderTheme.DAY -> day
        }
        // Red and amber keep their own highlight: other colours would break the point of them.
        if (theme == ReaderTheme.RED || theme == ReaderTheme.AMBER) return base
        val orp = when (highlight) {
            Highlight.LIME -> if (base.light) Color(0xFF4F7A00) else if (theme == ReaderTheme.DIM) dim.orp else Color(0xFFC4F43F)
            Highlight.RED -> if (base.light) Color(0xFFD02A37) else Color(0xFFFF5147)
            Highlight.AMBER -> if (base.light) Color(0xFFB86E00) else Color(0xFFFFB648)
            Highlight.CYAN -> if (base.light) Color(0xFF00809E) else Color(0xFF52D8FF)
            Highlight.PINK -> if (base.light) Color(0xFFC2185B) else Color(0xFFFF73B9)
            Highlight.NONE -> base.text
        }
        return base.copy(orp = orp)
    }
}

val LocalPalette = staticCompositionLocalOf { Palettes.oled }

/** Tabular figures, so changing numbers never shift their neighbours. */
val Numbers = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun LimeTheme(theme: ReaderTheme, highlight: Highlight, content: @Composable () -> Unit) {
    val target = remember(theme, highlight) { Palettes.of(theme, highlight) }
    // Night red fades in slowly (the eyes are dark-adapted), and out a little faster.
    val ms = if (theme == ReaderTheme.RED) 900 else 420
    @Composable
    fun c(color: Color): Color = animateColorAsState(color, tween(ms), label = "palette").value

    val p = Palette(
        bg = c(target.bg), surface = c(target.surface), raised = c(target.raised), text = c(target.text),
        dim = c(target.dim), faint = c(target.faint), accent = c(target.accent), accentSoft = c(target.accentSoft),
        onAccent = c(target.onAccent), track = c(target.track), orp = c(target.orp), warn = c(target.warn),
        light = target.light,
    )
    val scheme = if (p.light) lightColorScheme(
        primary = p.accent, onPrimary = p.onAccent, background = p.bg, onBackground = p.text,
        surface = p.surface, onSurface = p.text, surfaceVariant = p.raised, onSurfaceVariant = p.dim,
        surfaceContainer = p.surface, surfaceContainerHigh = p.raised, outline = p.faint,
    ) else darkColorScheme(
        primary = p.accent, onPrimary = p.onAccent, background = p.bg, onBackground = p.text,
        surface = p.surface, onSurface = p.text, surfaceVariant = p.raised, onSurfaceVariant = p.dim,
        surfaceContainer = p.surface, surfaceContainerHigh = p.raised, outline = p.faint,
    )
    val type = MaterialTheme.typography.let { t ->
        t.copy(
            headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 30.sp, letterSpacing = (-0.5).sp),
            titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = type, content = content)
    }
}
