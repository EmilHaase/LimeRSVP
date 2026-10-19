package com.limersvp.data

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ReaderTheme { OLED, RED, AMBER, DIM, DAY }
/** CLEAR is Atkinson Hyperlegible Next: letters that cannot be mistaken for each other. */
enum class WordFont { CLEAR, SANS, SERIF, MONO }
enum class Highlight { LIME, RED, AMBER, CYAN, PINK, NONE }
enum class PauseLevel(val scale: Float) { NONE(0f), LIGHT(0.6f), NORMAL(1f), LONG(1.5f) }
enum class RampLevel(val millis: Long) { OFF(0), QUICK(900), GENTLE(2000), SLOW(3500) }
enum class PlayMode { HOLD, TAP }
enum class VolumeKeys { OFF, SPEED, READ }
enum class HapticLevel { OFF, SOFT, STRONG }

@Immutable
data class Settings(
    val wpm: Int = 600,
    val wordSize: Int = 44,
    val font: WordFont = WordFont.CLEAR,
    val bold: Boolean = false,
    val theme: ReaderTheme = ReaderTheme.OLED,
    val highlight: Highlight = Highlight.LIME,
    val guide: Boolean = true,
    val context: Boolean = true,
    val smartTiming: Boolean = true,
    val pauses: PauseLevel = PauseLevel.NORMAL,
    val splitLong: Boolean = true,
    val ramp: RampLevel = RampLevel.GENTLE,
    val rewind: Boolean = true,
    val playMode: PlayMode = PlayMode.HOLD,
    val volumeKeys: VolumeKeys = VolumeKeys.SPEED,
    val haptics: HapticLevel = HapticLevel.SOFT,
    val keepScreenOn: Boolean = true,
    val dim: Float = 0f,
    val stopAtChapter: Boolean = false,
    val skipFrontMatter: Boolean = true,
    val dailyGoal: Int = 0,
    /** Earned by finishing a book. Never announced. */
    val leaf: Boolean = false,
    val tutorialDone: Boolean = false,
)

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state.asStateFlow()
    val value: Settings get() = _state.value

    fun update(block: (Settings) -> Settings) {
        val next = block(_state.value)
        if (next == _state.value) return
        _state.value = next
        write(next)
    }

    private inline fun <reified E : Enum<E>> enumOf(key: String, def: E): E =
        prefs.getString(key, null)?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: def

    private fun read(): Settings {
        val d = Settings()
        return Settings(
            wpm = prefs.getInt("wpm", d.wpm),
            wordSize = prefs.getInt("wordSize", d.wordSize),
            font = enumOf("font", d.font),
            bold = prefs.getBoolean("bold", d.bold),
            theme = enumOf("theme", d.theme),
            highlight = enumOf("highlight", d.highlight),
            guide = prefs.getBoolean("guide", d.guide),
            context = prefs.getBoolean("context", d.context),
            smartTiming = prefs.getBoolean("smartTiming", d.smartTiming),
            pauses = enumOf("pauses", d.pauses),
            splitLong = prefs.getBoolean("splitLong", d.splitLong),
            ramp = enumOf("ramp", d.ramp),
            rewind = prefs.getBoolean("rewind", d.rewind),
            playMode = enumOf("playMode", d.playMode),
            volumeKeys = enumOf("volumeKeys", d.volumeKeys),
            haptics = enumOf("haptics", d.haptics),
            keepScreenOn = prefs.getBoolean("keepScreenOn", d.keepScreenOn),
            dim = prefs.getFloat("dim", d.dim),
            stopAtChapter = prefs.getBoolean("stopAtChapter", d.stopAtChapter),
            skipFrontMatter = prefs.getBoolean("skipFrontMatter", d.skipFrontMatter),
            dailyGoal = prefs.getInt("dailyGoal", d.dailyGoal),
            leaf = prefs.getBoolean("leaf", d.leaf),
            tutorialDone = prefs.getBoolean("tutorialDone", d.tutorialDone),
        )
    }

    private fun write(s: Settings) {
        prefs.edit()
            .putInt("wpm", s.wpm)
            .putInt("wordSize", s.wordSize)
            .putString("font", s.font.name)
            .putBoolean("bold", s.bold)
            .putString("theme", s.theme.name)
            .putString("highlight", s.highlight.name)
            .putBoolean("guide", s.guide)
            .putBoolean("context", s.context)
            .putBoolean("smartTiming", s.smartTiming)
            .putString("pauses", s.pauses.name)
            .putBoolean("splitLong", s.splitLong)
            .putString("ramp", s.ramp.name)
            .putBoolean("rewind", s.rewind)
            .putString("playMode", s.playMode.name)
            .putString("volumeKeys", s.volumeKeys.name)
            .putString("haptics", s.haptics.name)
            .putBoolean("keepScreenOn", s.keepScreenOn)
            .putFloat("dim", s.dim)
            .putBoolean("stopAtChapter", s.stopAtChapter)
            .putBoolean("skipFrontMatter", s.skipFrontMatter)
            .putInt("dailyGoal", s.dailyGoal)
            .putBoolean("leaf", s.leaf)
            .putBoolean("tutorialDone", s.tutorialDone)
            .apply()
    }
}
