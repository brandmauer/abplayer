package com.brandmauer.abplayer.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import com.brandmauer.abplayer.data.EQ_FREQS
import com.brandmauer.abplayer.data.SoundState
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Параметры обработки, пересчитанные из SoundState (то же, что applySoundSettings в прототипе). */
class SoundParams(
    val bandsDb: FloatArray,   // 10 полос эквалайзера, дБ
    val preampGain: Float,     // линейное усиление предусилителя
    val pan: Float,            // -1..1
    val trimGain: Float        // линейное усиление «тонкой подстройки громкости»
) {
    companion object {
        val NEUTRAL = SoundParams(FloatArray(10), 1f, 0f, 1f)
    }
}

/**
 * Общий доступ к процессору из UI и сервиса воспроизведения (один процесс).
 * Скорость (playbackSpeed) и тон (playbackPitch) применяются независимо через PlaybackParameters плеера.
 */
object SoundEngine {
    val processor = SoundProcessor()

    fun paramsFor(s: SoundState): SoundParams {
        val bands = FloatArray(10) { i -> if (s.eqEnabled) s.eqBands[i].toFloat() else 0f }
        val preamp = if (s.soundEnabled) 10.0.pow(s.preamp / 20.0).toFloat() else 1f
        val pan = if (s.soundEnabled) (s.balance / 100f).coerceIn(-1f, 1f) else 0f
        val trim = if (s.volumeFineEnabled) (1f + s.volumeTrim / 100f).coerceAtLeast(0f) else 1f
        return SoundParams(bands, preamp, pan, trim)
    }

    /**
     * Скорость воспроизведения (темп) без изменения высоты голоса: ×0.5…×3.
     * Высоту тона не затрагивает.
     */
    fun playbackSpeed(s: SoundState): Float {
        if (!s.soundEnabled) return 1f
        return s.speed.coerceIn(0.5f, 3f)
    }

    /**
     * Высота тона (тембр голоса) без изменения скорости: ×0.5…×1.5.
     * Ниже 1 — голос становится ниже, «мужским»; выше 1 — выше, «женским».
     * Сдвиг делает Sonic из ExoPlayer независимо от темпа.
     */
    fun playbackPitch(s: SoundState): Float {
        if (!s.soundEnabled) return 1f
        return s.tone.coerceIn(0.5f, 1.5f)
    }

    fun apply(s: SoundState) {
        processor.setParams(paramsFor(s))
    }
}

/**
 * 10-полосный эквалайзер (пиковые биквад-фильтры, Q = 1.1) + предусилитель + баланс + подстройка громкости.
 * Работает с 16-битным PCM (моно/стерео); другие форматы пропускаются без обработки.
 */
class SoundProcessor : BaseAudioProcessor() {

    @Volatile private var params: SoundParams = SoundParams.NEUTRAL
    @Volatile private var version = 0
    private var appliedVersion = -1

    private var sampleRate = 0
    private var channels = 2

    // Коэффициенты активных полос: [b0, b1, b2, a1, a2]
    private var coefs: Array<FloatArray> = emptyArray()
    private var z1 = Array(2) { FloatArray(10) }
    private var z2 = Array(2) { FloatArray(10) }
    private var activeCount = 0
    private var activeIdx = IntArray(10)
    private var preamp = 1f
    private var pan = 0f
    private var trim = 1f
    private var bypass = true

    fun setParams(p: SoundParams) {
        params = p
        version++
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.channelCount < 1 || inputAudioFormat.channelCount > 2
        ) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        appliedVersion = -1
        return inputAudioFormat
    }

    override fun onFlush() {
        for (c in 0 until 2) {
            java.util.Arrays.fill(z1[c], 0f)
            java.util.Arrays.fill(z2[c], 0f)
        }
    }

    override fun onReset() {
        appliedVersion = -1
    }

    private fun refresh() {
        val v = version
        val p = params
        val list = ArrayList<FloatArray>()
        val idx = ArrayList<Int>()
        for (i in 0 until 10) {
            val db = p.bandsDb[i]
            val f0 = EQ_FREQS[i].toDouble()
            if (abs(db) < 0.01f) continue
            if (f0 >= sampleRate * 0.48) continue       // выше частоты Найквиста — пропускаем
            val a = 10.0.pow(db / 40.0)
            val w0 = 2.0 * PI * f0 / sampleRate
            val alpha = sin(w0) / (2.0 * 1.1)
            val cw = cos(w0)
            val b0 = 1.0 + alpha * a
            val b1 = -2.0 * cw
            val b2 = 1.0 - alpha * a
            val a0 = 1.0 + alpha / a
            val a1 = -2.0 * cw
            val a2 = 1.0 - alpha / a
            list.add(
                floatArrayOf(
                    (b0 / a0).toFloat(), (b1 / a0).toFloat(), (b2 / a0).toFloat(),
                    (a1 / a0).toFloat(), (a2 / a0).toFloat()
                )
            )
            idx.add(i)
        }
        coefs = list.toTypedArray()
        activeCount = list.size
        activeIdx = IntArray(list.size) { idx[it] }
        preamp = p.preampGain
        pan = if (channels == 2) p.pan else 0f
        trim = p.trimGain
        bypass = activeCount == 0 && abs(preamp - 1f) < 0.0005f && pan == 0f && abs(trim - 1f) < 0.0005f
        appliedVersion = v
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        if (appliedVersion != version) refresh()
        val out = replaceOutputBuffer(size)
        if (bypass) {
            out.put(inputBuffer)
            out.flip()
            return
        }
        val ch = channels
        val frames = size / (2 * ch)
        val tmp = FloatArray(2)
        // Равномощная панорама для стерео (как StereoPannerNode в Web Audio)
        var gl = 0f
        var gr = 0f
        val panPos = pan
        if (panPos != 0f) {
            val x = if (panPos <= 0f) panPos + 1f else panPos
            gl = cos(x * PI / 2.0).toFloat()
            gr = sin(x * PI / 2.0).toFloat()
        }
        for (f in 0 until frames) {
            for (c in 0 until ch) {
                var x = inputBuffer.short / 32768f * preamp
                for (k in 0 until activeCount) {
                    val b = activeIdx[k]
                    val co = coefs[k]
                    val y = co[0] * x + z1[c][b]
                    z1[c][b] = co[1] * x - co[3] * y + z2[c][b]
                    z2[c][b] = co[2] * x - co[4] * y
                    x = y
                }
                tmp[c] = x
            }
            if (ch == 2 && panPos != 0f) {
                val l = tmp[0]
                val r = tmp[1]
                if (panPos <= 0f) {
                    tmp[0] = l + r * gl
                    tmp[1] = r * gr
                } else {
                    tmp[0] = l * gl
                    tmp[1] = r + l * gr
                }
            }
            for (c in 0 until ch) {
                var y = tmp[c] * trim
                if (y > 1f) y = 1f else if (y < -1f) y = -1f
                out.putShort((y * 32767f).toInt().toShort())
            }
        }
        // неполный кадр в конце буфера отбрасываем
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }
}
