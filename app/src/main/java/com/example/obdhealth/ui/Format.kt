package com.example.obdhealth.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 148320 -> «148 320» */
fun fmtInt(n: Int): String = String.format(Locale.US, "%,d", n).replace(',', ' ')

fun fmtDate(ms: Long, lang: String): String =
    SimpleDateFormat("d MMM, HH:mm", Locale(lang)).format(Date(ms))

fun fmtClock(ms: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))

fun fmtKm(v: Double): String = String.format(Locale.US, "%.1f", v)

/** 125 -> «02:05» */
fun fmtMmSs(sec: Long): String = String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)

fun fmtDuration(sec: Long, lang: String): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    val hh = if (lang == "en") "h" else "год"
    val mm = if (lang == "en") "min" else "хв"
    val ss = if (lang == "en") "s" else "с"
    return when {
        h > 0 -> "$h $hh $m $mm"
        m > 0 -> "$m $mm $s $ss"
        else -> "$s $ss"
    }
}
