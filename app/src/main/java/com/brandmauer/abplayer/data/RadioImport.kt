package com.brandmauer.abplayer.data

import com.brandmauer.abplayer.util.TextFix
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Один найденный пункт плейлиста m3u/m3u8: название и адрес потока. */
data class M3uEntry(val name: String, val url: String)

object RadioImport {

    /** true, если текст — это манифест HLS (сегментированный поток), а не просто список станций. */
    fun isHlsManifest(text: String): Boolean {
        val head = text.take(4000)
        return head.contains("#EXT-X-STREAM-INF") || head.contains("#EXT-X-TARGETDURATION") ||
            head.contains("#EXT-X-MEDIA-SEQUENCE") || head.contains("#EXT-X-VERSION")
    }

    /** Разбор простого m3u/m3u8: список ссылок на станции, опционально с именами из #EXTINF. */
    fun parseSimple(text: String, fallbackName: String): List<M3uEntry> {
        val out = mutableListOf<M3uEntry>()
        var pendingName: String? = null
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            if (line.startsWith("#EXTINF")) {
                val comma = line.indexOf(',')
                pendingName = if (comma >= 0 && comma + 1 < line.length) line.substring(comma + 1).trim() else null
            } else if (line.startsWith("#")) {
                // прочие теги (#EXTM3U и т.п.) — пропускаем
            } else if (line.startsWith("http://") || line.startsWith("https://")) {
                out += M3uEntry(pendingName?.takeIf { it.isNotEmpty() } ?: fallbackName, line)
                pendingName = null
            }
        }
        return out
    }

    /** Читает не больше 64 КБ: если по «.m3u»-ссылке отдаётся сам поток, чтение не должно длиться вечно. */
    private fun readAllBytes(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        input.use { ins ->
            while (out.size() < 65536) {
                val n = ins.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
        }
        return out.toByteArray()
    }

    /** Кодировка из заголовка Content-Type ("audio/x-mpegurl; charset=windows-1251"), если указана. */
    private fun charsetOf(contentType: String?): String? {
        val ct = contentType ?: return null
        val i = ct.lowercase().indexOf("charset=")
        if (i < 0) return null
        return ct.substring(i + 8).substringBefore(';').trim().trim('"').ifEmpty { null }
    }

    /** Скачивает текст m3u/m3u8 по прямой ссылке. Вызывать из фонового потока. */
    fun fetchText(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.instanceFollowRedirects = true
        conn.requestMethod = "GET"
        try {
            val bytes = readAllBytes(conn.inputStream)
            // m3u бывают в UTF-8 и в Windows-1251 — иначе русские названия станций превращаются в «кракозябры»
            return TextFix.decodeBytes(bytes, charsetOf(conn.contentType))
        } finally {
            conn.disconnect()
        }
    }

    fun looksLikePlaylistUrl(url: String): Boolean {
        val u = url.substringBefore('?').lowercase()
        return u.endsWith(".m3u") || u.endsWith(".m3u8")
    }
}
