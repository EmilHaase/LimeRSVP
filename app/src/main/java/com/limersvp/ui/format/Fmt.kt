package com.limersvp.ui.format

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import com.limersvp.R
import java.text.DecimalFormatSymbols
import kotlin.math.roundToLong

/** Unknown stays unknown ("—"). Estimates wear a "~"; measured times do not. */
class Fmt(private val res: Resources) {
    /** A measured duration: "2 h 05 min", "12 min", "40 s". */
    fun spent(ms: Long): String {
        val s = ms / 1000
        return when {
            s < 60 -> res.getString(R.string.fmt_s, s)
            s < 3600 -> res.getString(R.string.fmt_min, s / 60)
            else -> res.getString(R.string.fmt_h_min, s / 3600, (s % 3600) / 60)
        }
    }

    /** An estimate: "~2 h 05 min", "~12 min", "~1 min". */
    fun estimate(ms: Long?): String {
        if (ms == null || ms < 0) return UNKNOWN
        val min = (ms / 60_000.0).roundToLong().coerceAtLeast(1)
        return about(if (min < 60) res.getString(R.string.fmt_min, min) else res.getString(R.string.fmt_h_min, min / 60, min % 60))
    }

    /** Compact estimate for lists: "~12 min", "~2 h". */
    fun minutes(ms: Long?): String {
        if (ms == null || ms < 0) return UNKNOWN
        val min = (ms / 60_000.0).roundToLong().coerceAtLeast(1)
        return about(
            if (min < 60) res.getString(R.string.fmt_min, min)
            else res.getString(R.string.fmt_h, if (min < 600) one(min / 60.0) else "${min / 60}")
        )
    }

    fun percent(f: Float): String = res.getString(R.string.fmt_percent, (f * 100).toInt().coerceIn(0, 100))

    fun count(n: Long): String = when {
        n < 10_000 -> "%,d".format(n)
        n < 1_000_000 -> res.getString(R.string.fmt_thousands, one(n / 1000.0))
        else -> res.getString(R.string.fmt_millions, one(n / 1_000_000.0))
    }

    private fun about(s: String) = res.getString(R.string.fmt_about, s)

    /** One decimal, dropped when it is zero: 1.5, 2 (1,5 where the comma is the decimal mark). */
    private fun one(v: Double): String {
        val t = (v * 10).roundToLong()
        return if (t % 10 == 0L) "${t / 10}" else "${t / 10}${DecimalFormatSymbols.getInstance().decimalSeparator}${t % 10}"
    }

    companion object {
        const val UNKNOWN = "—"
    }
}

/** Units in the language of the phone. */
@Composable
fun rememberFmt(): Fmt {
    val res = LocalResources.current
    return remember(res) { Fmt(res) }
}
