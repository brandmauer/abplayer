package com.brandmauer.abplayer.util

import java.util.Locale

/** m:ss (как fmtTime в прототипе). */
fun fmtTime(ms: Long): String {
    val sec = if (ms < 0) 0L else ms / 1000
    return "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"
}

/** m:ss или h:mm:ss (fmtDurLong). Принимает секунды. */
fun fmtDurLongSec(secIn: Long): String {
    val sec = if (secIn < 0) 0L else secIn
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "$m:${s.toString().padStart(2, '0')}"
}

fun fmtDurLong(ms: Long): String = fmtDurLongSec(ms / 1000)

/** Остаток в формате «-m:ss» (или «-ч:мм:сс» при часе и более) с округлением вверх. */
fun fmtRemaining(curMs: Long, durMs: Long): String {
    val remainMs = if (durMs - curMs < 0) 0L else durMs - curMs
    val sec = Math.ceil(remainMs / 1000.0 - 0.001).toLong()
    return "-" + fmtDurLongSec(sec)
}

fun fmtSize(bytes: Long): String {
    return if (bytes < 1024L * 1024L) "${Math.round(bytes / 1024.0)} КБ"
    else String.format(Locale.US, "%.1f МБ", bytes / 1024.0 / 1024.0)
}

fun trackWord(nIn: Int): String {
    val n = Math.abs(nIn) % 100
    val n1 = n % 10
    if (n in 11..19) return "треков"
    if (n1 in 2..4) return "трека"
    if (n1 == 1) return "трек"
    return "треков"
}

fun signed(v: Int): String = if (v > 0) "+$v" else "$v"

/** «закладка / закладки / закладок» по числу. */
fun bookmarkWord(nIn: Int): String {
    val n = Math.abs(nIn) % 100
    val n1 = n % 10
    if (n in 11..19) return "закладок"
    if (n1 in 2..4) return "закладки"
    if (n1 == 1) return "закладка"
    return "закладок"
}
