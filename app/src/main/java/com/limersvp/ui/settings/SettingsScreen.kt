package com.limersvp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.limersvp.Container
import com.limersvp.R
import com.limersvp.core.Orp
import com.limersvp.core.WordFrame
import com.limersvp.data.HapticLevel
import com.limersvp.data.PauseLevel
import com.limersvp.data.PlayMode
import com.limersvp.data.RampLevel
import com.limersvp.data.ReaderTheme
import com.limersvp.data.Settings
import com.limersvp.data.VolumeKeys
import com.limersvp.data.WordFont
import com.limersvp.feel.LocalHaptics
import com.limersvp.feel.rememberShaker
import com.limersvp.feel.shake
import com.limersvp.ui.components.Choices
import com.limersvp.ui.components.SettingRow
import com.limersvp.ui.components.Toggle
import com.limersvp.ui.library.LimeMark
import com.limersvp.ui.reader.HighlightDots
import com.limersvp.ui.reader.ThemeDots
import com.limersvp.ui.reader.WordStage
import com.limersvp.ui.reader.fontName
import com.limersvp.ui.reader.pauseName
import com.limersvp.ui.reader.rampName
import com.limersvp.ui.theme.LocalPalette

@Composable
fun SettingsScreen(c: Container, onOpen: (String) -> Unit) {
    val p = LocalPalette.current
    val s by c.settings.state.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    fun set(block: (Settings) -> Settings) = c.settings.update(block)
    val sample = remember { WordFrame("Lime", Orp.range("Lime").first, Orp.range("Lime").last + 1) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 120.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge, color = p.text, modifier = Modifier.padding(start = 18.dp, top = 18.dp, bottom = 6.dp))

        Section("Reading") {
            Label("Start reading by")
            Choices(PlayMode.entries, s.playMode, { if (it == PlayMode.HOLD) "Holding" else "Tapping" }, { v -> set { it.copy(playMode = v) } }, pad())
            Label("Speed-up when you start")
            Choices(RampLevel.entries, s.ramp, ::rampName, { v -> set { it.copy(ramp = v) } }, pad())
            Spacer(Modifier.height(6.dp))
            Switch("Back to the sentence start after a break", s.rewind) { set { it.copy(rewind = !it.rewind) } }
            Switch("Stop at the end of each chapter", s.stopAtChapter) { set { it.copy(stopAtChapter = !it.stopAtChapter) } }
            Switch("Open new books at the first chapter", s.skipFrontMatter) { set { it.copy(skipFrontMatter = !it.skipFrontMatter) } }
        }

        Section("Timing") {
            Switch("Longer words stay longer", s.smartTiming) { set { it.copy(smartTiming = !it.smartTiming) } }
            Label("Pause after sentences")
            Choices(PauseLevel.entries, s.pauses, ::pauseName, { v -> set { it.copy(pauses = v) } }, pad())
            Spacer(Modifier.height(6.dp))
            Switch("Split very long words", s.splitLong) { set { it.copy(splitLong = !it.splitLong) } }
        }

        Section("Look") {
            WordStage({ sample }, s, Modifier.fillMaxWidth().height(110.dp))
            Label("Theme")
            ThemeDots(s.theme) { t -> set { it.copy(theme = t) } }
            if (s.theme != ReaderTheme.RED && s.theme != ReaderTheme.AMBER) {
                Label("Highlight")
                HighlightDots(s) { h -> set { it.copy(highlight = h) } }
            }
            Label("Font")
            Choices(WordFont.entries, s.font, ::fontName, { f -> set { it.copy(font = f) } }, pad())
            Label("Size")
            Slider(
                value = s.wordSize.toFloat(), onValueChange = { v -> set { it.copy(wordSize = v.toInt()) } },
                valueRange = 26f..72f,
                colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.track),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Switch("Bold", s.bold) { set { it.copy(bold = !it.bold) } }
            Switch("Focus guide", s.guide) { set { it.copy(guide = !it.guide) } }
            Switch("Sentence when stopped", s.context) { set { it.copy(context = !it.context) } }
        }

        Section("Controls") {
            Label("Volume buttons")
            Choices(VolumeKeys.entries, s.volumeKeys, {
                when (it) { VolumeKeys.OFF -> "Volume"; VolumeKeys.SPEED -> "Speed"; VolumeKeys.READ -> "Hold to read" }
            }, { v -> set { it.copy(volumeKeys = v) } }, pad())
            Label("Vibration")
            Choices(HapticLevel.entries, s.haptics, {
                when (it) { HapticLevel.OFF -> "Off"; HapticLevel.SOFT -> "Soft"; HapticLevel.STRONG -> "Strong" }
            }, { v ->
                set { it.copy(haptics = v) }
                // Feel the choice at once.
                haptics.level = v
                haptics.test()
            }, pad())
            Spacer(Modifier.height(6.dp))
            Switch("Keep the screen on while reading", s.keepScreenOn) { set { it.copy(keepScreenOn = !it.keepScreenOn) } }
        }

        Section("Daily goal") {
            Choices(listOf(0, 10, 20, 30, 60), s.dailyGoal, { if (it == 0) "Off" else "$it min" }, { v -> set { it.copy(dailyGoal = v) } }, pad())
            Spacer(Modifier.height(8.dp))
        }

        About(c, onOpen)
    }
}

@Composable
private fun pad() = Modifier.padding(horizontal = 20.dp)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(p.surface)
            .padding(vertical = 10.dp)
    ) {
        Text(title, color = p.accent, fontSize = 13.sp, letterSpacing = 1.sp, modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 2.dp))
        content()
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = LocalPalette.current.dim, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp))
}

@Composable
private fun Switch(title: String, on: Boolean, onToggle: () -> Unit) {
    SettingRow(title, onClick = onToggle) { Toggle(on) }
}

/** The version line. Some say it hides something. */
@Composable
private fun About(c: Container, onOpen: (String) -> Unit) {
    val p = LocalPalette.current
    val shaker = rememberShaker()
    var taps by remember { mutableIntStateOf(0) }
    var last by remember { mutableLongStateOf(0L) }
    Column(
        Modifier.fillMaxWidth().padding(top = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LimeMark(Modifier.size(40.dp))
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .shake(shaker)
                .clickable(remember { MutableInteractionSource() }, null) {
                    val now = System.currentTimeMillis()
                    taps = if (now - last < 700) taps + 1 else 1
                    last = now
                    if (taps >= 7) {
                        taps = 0
                        c.importer.importResource(R.raw.lime, "A lime.html", open = true)
                    } else shaker.shake()
                }
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text("LimeRSVP 1.0", color = p.faint, fontSize = 13.sp)
        }
    }
}
