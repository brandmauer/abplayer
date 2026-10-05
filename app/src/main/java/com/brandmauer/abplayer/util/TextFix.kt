package com.brandmauer.abplayer.util

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Исправление «сбитой» кодировки — в основном русского текста у интернет-радио.
 * Станции часто присылают название композиции (ICY) и имена в m3u в Windows-1251, а плеер
 * читает их как Latin-1 или UTF-8 — получается «Ïðèâåò» или «РџСЂРёРІРµС‚».
 */
object TextFix {
    private val CP1251: Charset = Charset.forName("windows-1251")
    private val LATIN1: Charset = Charsets.ISO_8859_1

    private fun cyrillicCount(s: String): Int = s.count { it in '\u0400'..'\u04FF' }

    /** Символы, типичные для кириллицы, прочитанной как Latin-1/cp1252: «Ð», «Ñ», «Ï», «ð» и т.п. */
    private fun looksLikeMojibake(s: String): Boolean =
        s.any { it in '\u00C0'..'\u00FF' || it in '\u0080'..'\u009F' }

    private fun decodeStrict(bytes: ByteArray, cs: Charset): String? = try {
        cs.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: CharacterCodingException) {
        null
    }

    /** Возвращает исправленную строку или исходную, если она выглядит нормально. */
    fun fixMojibake(input: String): String {
        if (input.isEmpty()) return input
        // уже нормальная кириллица — ничего не трогаем
        if (cyrillicCount(input) > 0) {
            // «РџСЂРёРІРµС‚»: UTF-8, прочитанный как cp1251 — вернуть можно обратным перекодированием
            val back = try { String(input.toByteArray(CP1251), Charsets.UTF_8) } catch (e: Exception) { input }
            return if (back != input && cyrillicCount(back) >= cyrillicCount(input) / 3 && !back.contains('\uFFFD') &&
                input.count { it == '\u0420' || it == '\u0421' } * 3 >= input.length
            ) back else input
        }
        if (input.any { it.code > 0xFF }) return input          // уже юникод (не Latin-1), не мешаем
        if (!looksLikeMojibake(input)) return input

        val raw = input.toByteArray(LATIN1)
        // 1) UTF-8, прочитанный как Latin-1 («Ð\u009fÑ\u0080...»)
        decodeStrict(raw, Charsets.UTF_8)?.let { if (cyrillicCount(it) > 0) return it }
        // 2) Windows-1251, прочитанный как Latin-1 («Ïðèâåò»)
        val cp = String(raw, CP1251)
        if (cyrillicCount(cp) > 0 && cyrillicCount(cp) * 2 >= cp.count { it.isLetter() }) return cp
        return input
    }

    /**
     * Текст из байтов: сначала строгий UTF-8, при ошибке — Windows-1251 (так пишут многие
     * русские m3u-плейлисты). [headerCharset] — кодировка из HTTP-заголовка, если сервер её указал.
     */
    fun decodeBytes(bytes: ByteArray, headerCharset: String? = null): String {
        var data = bytes
        // BOM UTF-8
        if (data.size >= 3 && data[0] == 0xEF.toByte() && data[1] == 0xBB.toByte() && data[2] == 0xBF.toByte()) {
            data = data.copyOfRange(3, data.size)
        }
        decodeStrict(data, Charsets.UTF_8)?.let { return it }
        if (!headerCharset.isNullOrBlank()) {
            try {
                val cs = Charset.forName(headerCharset)
                if (cs != Charsets.UTF_8) return String(data, cs)
            } catch (e: Exception) {
            }
        }
        return String(data, CP1251)
    }
}
