package com.redline.app.ui.components

import java.util.Locale
import kotlin.math.roundToLong

/** 0:42, 21:14 or 1:05:09. */
fun formatClock(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Workout length for people, not clocks: "<1 min", "42 min", "1 h 5 min". */
fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    return when {
        minutes < 1 -> "<1 min"
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}

/** 185 for a whole number, 182.5 otherwise. */
fun Float.cleanNum(): String {
    val tenths = (this * 10).roundToLong()
    return if (tenths % 10 == 0L) (tenths / 10).toString() else "%.1f".format(Locale.US, tenths / 10f)
}

/** 8,980 */
fun Long.grouped(): String = "%,d".format(this)

/** 8,980 below ten thousand, 45.9k above. */
fun Long.compact(): String =
    if (this < 10_000) grouped() else "%.1fk".format(Locale.US, this / 1000f)
