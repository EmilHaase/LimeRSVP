package com.limersvp.feel

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.limersvp.data.HapticLevel

/**
 * One vibration per moment, tuned per motor.
 *
 * Why vibrations "did not work" on many phones:
 *  - View.performHapticFeedback is silenced by the system "touch feedback" switch and by OEMs.
 *  - VibrationEffect predefined effects need Android 10; on 9 they do not exist.
 *  - A 10 ms pulse on a cheap ERM motor never spins it up, so nothing is felt.
 *  - Firing a new vibration every few ms cancels the previous one before it is felt.
 * So this talks to the Vibrator directly, picks primitives / predefined effects / timed pulses
 * by what the motor can do, and rate-limits detent ticks.
 *
 * Detent ticks (the knob) play only on motors that can do a crisp tick: on a buzzer they would
 * be a buzz running through the gesture, and silence is better.
 */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    var level: HapticLevel = HapticLevel.SOFT

    private val has = vibrator?.hasVibrator() == true
    private val amplitude = has && vibrator!!.hasAmplitudeControl()

    private val primitives = Build.VERSION.SDK_INT >= 30 && has && vibrator!!.areAllPrimitivesSupported(
        VibrationEffect.Composition.PRIMITIVE_TICK,
        VibrationEffect.Composition.PRIMITIVE_CLICK,
    )

    /** A motor that can tick crisply. */
    val fine: Boolean = primitives || (Build.VERSION.SDK_INT >= 29 && amplitude)

    private var lastTick = 0L

    /** A detent passing under the finger. Fine motors only, at most every 28 ms. */
    fun tick() {
        if (!fine) return
        val now = SystemClock.uptimeMillis()
        if (now - lastTick < 28) return
        lastTick = now
        play(Kind.TICK)
    }

    /** Something armed or chosen: an axis locked, the home detent, a chapter crossed. */
    fun click() = play(Kind.CLICK)

    /** A dead end: the end of the knob, the start of the book. */
    fun wall() = play(Kind.WALL)

    /** The end of a book. */
    fun success() = play(Kind.SUCCESS)

    fun test() = play(Kind.CLICK)

    private enum class Kind { TICK, CLICK, WALL, SUCCESS }

    private fun play(kind: Kind) {
        if (!has || level == HapticLevel.OFF) return
        val v = vibrator ?: return
        val strong = level == HapticLevel.STRONG
        val effect = try {
            when {
                primitives -> composition(kind, strong)
                Build.VERSION.SDK_INT >= 29 && amplitude -> predefined(kind, strong)
                else -> timed(kind, strong)
            }
        } catch (_: Exception) {
            timed(kind, strong)
        } ?: return
        try {
            v.vibrate(effect)
        } catch (_: Exception) {
        }
    }

    private fun composition(kind: Kind, strong: Boolean): VibrationEffect? {
        if (Build.VERSION.SDK_INT < 30) return null
        val s = if (strong) 1f else 0.6f
        val c = VibrationEffect.startComposition()
        when (kind) {
            Kind.TICK -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f * s)
            Kind.CLICK -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f * s)
            Kind.WALL -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f * s)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.4f * s, 40)

            Kind.SUCCESS -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f * s)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f * s, 90)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f * s, 140)
        }
        return c.compose()
    }

    private fun predefined(kind: Kind, strong: Boolean): VibrationEffect? {
        if (Build.VERSION.SDK_INT < 29) return null
        return when (kind) {
            Kind.TICK -> if (strong) VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            else VibrationEffect.createOneShot(6, 70)

            Kind.CLICK -> VibrationEffect.createPredefined(if (strong) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_TICK)
            Kind.WALL -> VibrationEffect.createPredefined(if (strong) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_CLICK)
            Kind.SUCCESS -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
        }
    }

    /** Android 9 and phones without amplitude control: pulses long enough for the motor to spin up. */
    private fun timed(kind: Kind, strong: Boolean): VibrationEffect? {
        val a = if (amplitude) (if (strong) 255 else 150) else VibrationEffect.DEFAULT_AMPLITUDE
        return when (kind) {
            Kind.TICK -> if (amplitude) VibrationEffect.createOneShot(8, a) else null
            Kind.CLICK -> VibrationEffect.createOneShot(if (amplitude) 14 else if (strong) 30 else 22, a)
            Kind.WALL -> VibrationEffect.createOneShot(if (amplitude) 24 else if (strong) 45 else 35, a)
            Kind.SUCCESS -> if (amplitude) VibrationEffect.createWaveform(longArrayOf(0, 18, 80, 26), intArrayOf(0, a, 0, a), -1)
            else VibrationEffect.createWaveform(longArrayOf(0, 25, 90, 35), -1)
        }
    }
}
