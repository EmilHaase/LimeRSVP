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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import android.content.Intent
import android.net.Uri
import com.limersvp.ui.components.Choices
import com.limersvp.ui.components.GrowPanel
import com.limersvp.ui.components.SettingRow
import com.limersvp.ui.components.Toggle
import com.limersvp.ui.library.LimeMark
import com.limersvp.ui.library.SAMPLE_NAME
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
    val word = stringResource(R.string.settings_sample_word)
    val sample = remember(word) { WordFrame(word, Orp.range(word).first, Orp.range(word).last + 1) }

    var licenses by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 120.dp),
    ) {
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineLarge, color = p.text, modifier = Modifier.padding(start = 18.dp, top = 18.dp, bottom = 6.dp))

        Section(stringResource(R.string.settings_reading)) {
            Label(stringResource(R.string.settings_start_by))
            Choices(PlayMode.entries, s.playMode, { stringResource(if (it == PlayMode.HOLD) R.string.play_hold else R.string.play_tap) }, { v -> set { it.copy(playMode = v) } }, pad())
            Label(stringResource(R.string.opt_ramp))
            Choices(RampLevel.entries, s.ramp, { rampName(it) }, { v -> set { it.copy(ramp = v) } }, pad())
            Spacer(Modifier.height(6.dp))
            Switch(stringResource(R.string.settings_rewind), s.rewind) { set { it.copy(rewind = !it.rewind) } }
            Switch(stringResource(R.string.settings_stop_chapter), s.stopAtChapter) { set { it.copy(stopAtChapter = !it.stopAtChapter) } }
            Switch(stringResource(R.string.settings_skip_front), s.skipFrontMatter) { set { it.copy(skipFrontMatter = !it.skipFrontMatter) } }
            // Replays on the sample story, which opens straight away.
            SettingRow(stringResource(R.string.settings_tutorial), onClick = {
                set { it.copy(tutorialDone = false) }
                c.importer.importResource(R.raw.sample, SAMPLE_NAME, open = true)
            })
        }

        Section(stringResource(R.string.settings_timing)) {
            Switch(stringResource(R.string.settings_smart_timing), s.smartTiming) { set { it.copy(smartTiming = !it.smartTiming) } }
            Label(stringResource(R.string.opt_pauses))
            Choices(PauseLevel.entries, s.pauses, { pauseName(it) }, { v -> set { it.copy(pauses = v) } }, pad())
            Spacer(Modifier.height(6.dp))
            Switch(stringResource(R.string.settings_split_long), s.splitLong) { set { it.copy(splitLong = !it.splitLong) } }
        }

        Section(stringResource(R.string.settings_look)) {
            WordStage({ sample }, s, Modifier.fillMaxWidth().height(110.dp))
            Label(stringResource(R.string.opt_theme))
            ThemeDots(s.theme) { t -> set { it.copy(theme = t) } }
            if (s.theme != ReaderTheme.RED && s.theme != ReaderTheme.AMBER) {
                Label(stringResource(R.string.opt_highlight))
                HighlightDots(s) { h -> set { it.copy(highlight = h) } }
            }
            Label(stringResource(R.string.opt_font))
            Choices(WordFont.entries, s.font, { fontName(it) }, { f -> set { it.copy(font = f) } }, pad())
            Label(stringResource(R.string.opt_size))
            Slider(
                value = s.wordSize.toFloat(), onValueChange = { v -> set { it.copy(wordSize = v.toInt()) } },
                valueRange = 26f..72f,
                colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.track),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Switch(stringResource(R.string.opt_bold), s.bold) { set { it.copy(bold = !it.bold) } }
            Switch(stringResource(R.string.opt_guide), s.guide) { set { it.copy(guide = !it.guide) } }
            Switch(stringResource(R.string.opt_context), s.context) { set { it.copy(context = !it.context) } }
        }

        Section(stringResource(R.string.settings_controls)) {
            Label(stringResource(R.string.settings_volume_keys))
            Choices(VolumeKeys.entries, s.volumeKeys, {
                stringResource(when (it) { VolumeKeys.OFF -> R.string.volume_keys_volume; VolumeKeys.SPEED -> R.string.speed; VolumeKeys.READ -> R.string.volume_keys_read })
            }, { v -> set { it.copy(volumeKeys = v) } }, pad())
            Label(stringResource(R.string.settings_vibration))
            Choices(HapticLevel.entries, s.haptics, {
                stringResource(when (it) { HapticLevel.OFF -> R.string.off; HapticLevel.SOFT -> R.string.vibration_soft; HapticLevel.STRONG -> R.string.vibration_strong })
            }, { v ->
                set { it.copy(haptics = v) }
                // Feel the choice at once.
                haptics.level = v
                haptics.test()
            }, pad())
            Spacer(Modifier.height(6.dp))
            Switch(stringResource(R.string.settings_keep_screen_on), s.keepScreenOn) { set { it.copy(keepScreenOn = !it.keepScreenOn) } }
        }

        Section(stringResource(R.string.settings_daily_goal)) {
            Choices(listOf(0, 10, 20, 30, 60), s.dailyGoal, { if (it == 0) stringResource(R.string.goal_off) else stringResource(R.string.fmt_min, it) }, { v -> set { it.copy(dailyGoal = v) } }, pad())
            Spacer(Modifier.height(8.dp))
        }

        About(c, onLicenses = { licenses = true })
    }
    GrowPanel(
        licenses, TransformOrigin(0.6f, 0.95f), { licenses = false },
        Modifier.statusBarsPadding().padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 96.dp).fillMaxSize(),
    ) { Licenses(Modifier.fillMaxSize()) }
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
private fun About(c: Container, onLicenses: () -> Unit) {
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
            Text(stringResource(R.string.version_line, version()), color = p.faint, fontSize = 13.sp)
        }
        Text(stringResource(R.string.about_copyright), color = p.faint, fontSize = 12.sp)
        Text(
            stringResource(R.string.about_license), color = p.faint, fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
        )
        val context = LocalContext.current
        val linkShaker = rememberShaker()
        Row(Modifier.padding(top = 4.dp)) {
            TextLink(stringResource(R.string.about_source), Modifier.shake(linkShaker)) {
                val ok = runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)))
                }.isSuccess
                if (!ok) linkShaker.shake()
            }
            TextLink(stringResource(R.string.about_licenses), onClick = onLicenses)
        }
    }
}

/** Where the source lives; also part of the attribution every fork must keep. */
const val SOURCE_URL = "https://github.com/EmilHaase/LimeRSVP"

@Composable
private fun version(): String {
    val context = LocalContext.current
    return remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
}

@Composable
private fun TextLink(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text, color = p.dim, fontSize = 13.sp)
    }
}
