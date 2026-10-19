package com.limersvp.ui.format

import kotlin.math.roundToLong

/** Unknown stays unknown ("—"). Estimates wear a "~"; measured times do not. */
object Fmt {
    const val UNKNOWN = "—"

    /** A measured duration: "2 h 05 min", "12 min", "40 s". */
    fun spent(ms: Long): String {
        val s = ms / 1000
        return when {
            s < 60 -> "$s s"
            s < 3600 -> "${s / 60} min"
            else -> "${s / 3600} h ${"%02d".format((s % 3600) / 60)} min"
        }
    }

    /** An estimate: "~2 h 05 min", "~12 min", "~1 min". */
    fun estimate(ms: Long?): String {
        if (ms == null || ms < 0) return UNKNOWN
        val min = (ms / 60_000.0).roundToLong().coerceAtLeast(1)
        return if (min < 60) "~$min min" else "~${min / 60} h ${"%02d".format(min % 60)} min"
    }

    /** Compact estimate for lists: "~12 min", "~2 h". */
    fun minutes(ms: Long?): String {
        if (ms == null || ms < 0) return UNKNOWN
        val min = (ms / 60_000.0).roundToLong().coerceAtLeast(1)
        return if (min < 60) "~$min min" else if (min < 600) "~${one(min / 60.0)} h" else "~${min / 60} h"
    }

    fun percent(f: Float): String = "${(f * 100).toInt().coerceIn(0, 100)}%"

    fun count(n: Long): String = when {
        n < 10_000 -> "%,d".format(n)
        n < 1_000_000 -> "${one(n / 1000.0)}k"
        else -> "${one(n / 1_000_000.0)}M"
    }

    /** One decimal, dropped when it is zero: 1.5, 2. */
    private fun one(v: Double): String {
        val t = (v * 10).roundToLong()
        return if (t % 10 == 0L) "${t / 10}" else "${t / 10}.${t % 10}"
    }
}
