package com.brandmauer.abplayer.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.brandmauer.abplayer.MainActivity
import com.brandmauer.abplayer.util.TextFix

/** Обёртка: кнопки «следующий/предыдущий» в уведомлении управляют очередью приложения. */
class BridgePlayer(player: Player) : ForwardingPlayer(player) {
    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            // В уведомлении и на экране блокировки — только пауза и переключение треков:
            // без кнопок «назад/вперёд на N секунд» и без полосы перемотки.
            .remove(Player.COMMAND_SEEK_BACK)
            .remove(Player.COMMAND_SEEK_FORWARD)
            .remove(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .build()

    /** Обложка в уведомлении — только если это разрешено настройкой «Обложки». */
    override fun getMediaMetadata(): MediaMetadata {
        val md = super.getMediaMetadata()
        if (PlayerBridge.coverInNotification) return md
        if (md.artworkData == null && md.artworkUri == null) return md
        return md.buildUpon().setArtworkData(null, null).setArtworkUri(null).build()
    }

    override fun isCommandAvailable(command: Int): Boolean = getAvailableCommands().contains(command)

    override fun hasNextMediaItem(): Boolean = true
    override fun hasPreviousMediaItem(): Boolean = true

    override fun seekToNext() { PlayerBridge.onNext?.invoke() }
    override fun seekToNextMediaItem() { PlayerBridge.onNext?.invoke() }
    override fun seekToPrevious() { PlayerBridge.onPrev?.invoke() }
    override fun seekToPreviousMediaItem() { PlayerBridge.onPrev?.invoke() }
}

/** Размер буфера плеера в профиле «Максимальное энергосбережение»: 13 МБ (как стандартный лимит ExoPlayer для звука). */
private const val MAX_ECONOMY_BUFFER_BYTES = 13 * 1024 * 1024

class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    /** Нужен ли сейчас аппаратный offload (решает Hub: профиль, эффекты, A–B). Применяется и к заново созданному плееру. */
    @Volatile private var offloadEnabled = false

    override fun onCreate() {
        super.onCreate()

        val hub = try {
            (application as? com.brandmauer.abplayer.ABPlayerApp)?.hub
        } catch (e: Exception) {
            null
        }
        val level = try { hub?.effectiveNetworkLevel() ?: 1 } catch (e: Exception) { 1 }
        offloadEnabled = try { hub?.offloadWanted() ?: false } catch (e: Exception) { false }

        val player = buildPlayer(level)
        PlayerBridge.rawPlayer = player
        PlayerBridge.networkLevel = level
        PlayerBridge.setOffload = { on -> setOffload(on) }
        PlayerBridge.rebuildPlayer = { lvl -> rebuildPlayer(lvl) }
        PlayerBridge.resetPlayer = { resetPlayer() }

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, BridgePlayer(player))
            .setSessionActivity(openApp)
            .build()
    }

    /**
     * Создаёт ExoPlayer под заданный уровень «Сеть и радио» (размер буфера задаётся при создании и потом не меняется —
     * поэтому при смене профиля плеер пересоздаётся, см. rebuildPlayer).
     */
    private fun buildPlayer(networkLevel: Int): ExoPlayer {
        // Свой AudioSink с нашим процессором: эквалайзер, предусилитель, баланс, подстройка громкости.
        // DefaultAudioProcessorChain — стандартная цепочка ExoPlayer (пропуск тишины, изменение
        // скорости/тона средствами Sonic) плюс наш SoundProcessor, добавленный последним.
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(false)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessorChain(DefaultAudioSink.DefaultAudioProcessorChain(SoundEngine.processor))
                    .build()
            }
        }.setEnableDecoderFallback(true)

        // Быстрый старт потоков: короткие таймауты, редиректы http↔https и старт после ~0,8 с данных
        // (по умолчанию ExoPlayer ждёт 2,5 с буфера).
        // «Сеть и радио» в энергосбережении: более крупный буфер = реже радио-модуль просыпается
        // за новыми данными (экономия), но медленнее старт/перемотка; маленький буфер — наоборот,
        // быстрее реакция ценой более частой сетевой активности. Уровень читается один раз при
        // создании плеера (сервис обычно живёт всё время работы приложения).
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(6_000)
            .setReadTimeoutMs(8_000)
            .setUserAgent("ABPlayer/1.0")
        val loadControl = when (networkLevel) {
            3 -> DefaultLoadControl.Builder() // максимальная экономия: буфер ограничен РАЗМЕРОМ, а не временем
                // Сколько секунд уместится в буфер, зависит от битрейта (радио 128 кбит/с — около 14 минут, книга 64 кбит/с — около 27).
                // Время здесь — не лимит буфера, а лишь порог «пора докачивать»: когда в буфере осталась минута, плеер заполняет его
                // до размера целиком одним пакетом и снова засыпает (а не подкачивает по кусочку, будя модем и диск).
                .setBufferDurationsMs(
                    /* minBufferMs = */ 60_000,          // докачка начинается, когда осталась минута
                    /* maxBufferMs = */ 3_600_000,       // по времени не ограничиваем (час) — работает лимит по размеру
                    /* bufferForPlaybackMs = */ 2_500,
                    /* bufferForPlaybackAfterRebufferMs = */ 5_000
                )
                .setTargetBufferBytes(MAX_ECONOMY_BUFFER_BYTES)
                .setPrioritizeTimeOverSizeThresholds(false) // размер важнее времени
                .build()
            0 -> DefaultLoadControl.Builder() // экономия: крупный буфер, реже сетевая активность
                .setBufferDurationsMs(
                    /* minBufferMs = */ 60_000,
                    /* maxBufferMs = */ 120_000,
                    /* bufferForPlaybackMs = */ 2_000,
                    /* bufferForPlaybackAfterRebufferMs = */ 4_000
                )
                .build()
            2 -> DefaultLoadControl.Builder() // качество: меньший буфер, быстрее старт/перемотка
                .setBufferDurationsMs(
                    /* minBufferMs = */ 15_000,
                    /* maxBufferMs = */ 40_000,
                    /* bufferForPlaybackMs = */ 500,
                    /* bufferForPlaybackAfterRebufferMs = */ 1_500
                )
                .build()
            else -> DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 30_000,
                    /* maxBufferMs = */ 60_000,
                    /* bufferForPlaybackMs = */ 800,
                    /* bufferForPlaybackAfterRebufferMs = */ 2_000
                )
                .build()
        }

        val player = ExoPlayer.Builder(this, renderers)
            // DefaultDataSource сам выбирает источник по адресу: content:// и file:// — локальные файлы, http(s) — наш http
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(DefaultDataSource.Factory(this, http)))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            // По умолчанию LOCAL — держим только процессор. NETWORK (процессор + Wi-Fi в режиме высокой
            // производительности) включается лишь для сетевых потоков — см. applyWakeMode ниже: раньше он был
            // включён всегда и Wi-Fi не «засыпал» даже при прослушивании локальных файлов.
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        // Название композиции радио: ICY (Shoutcast/Icecast) и ID3 (HLS) приходят как Metadata потока
        player.addListener(object : Player.Listener {
            override fun onMetadata(metadata: Metadata) {
                var title: String? = null
                var artist: String? = null
                for (i in 0 until metadata.length()) {
                    when (val e = metadata[i]) {
                        is IcyInfo -> if (!e.title.isNullOrBlank()) title = e.title
                        is TextInformationFrame -> when (e.id) {
                            "TIT2" -> if (!e.values.firstOrNull().isNullOrBlank()) title = e.values.first()
                            "TPE1" -> if (!e.values.firstOrNull().isNullOrBlank()) artist = e.values.first()
                        }
                    }
                }
                if (title != null) {
                    // радиостанции часто шлют русский текст в Windows-1251 — чиним «кракозябры»
                    val fixedTitle = TextFix.fixMojibake(title)
                    val fixedArtist = artist?.let { TextFix.fixMojibake(it) }
                    val full = if (fixedArtist != null && !fixedTitle.contains(fixedArtist, ignoreCase = true)) "$fixedArtist - $fixedTitle" else fixedTitle
                    PlayerBridge.onStreamTitle?.invoke(player.currentMediaItem?.mediaId, full)
                }
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                // запасной путь: сводные метаданные, если название пришло не через onMetadata
                val own = player.currentMediaItem?.mediaMetadata?.title?.toString()
                val t = mediaMetadata.title?.toString()
                if (!t.isNullOrBlank() && t != own) {
                    PlayerBridge.onStreamTitle?.invoke(player.currentMediaItem?.mediaId, TextFix.fixMojibake(t))
                }
                // Обложка, найденная плеером внутри файла (её видно в уведомлении) — отдаём приложению,
                // чтобы она показывалась и на главном экране.
                val art = mediaMetadata.artworkData
                if (art != null && art.isNotEmpty()) {
                    PlayerBridge.onEmbeddedArt?.invoke(player.currentMediaItem?.mediaId, art)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                PlayerBridge.onStreamTitle?.invoke(mediaItem?.mediaId, null)
                applyWakeMode(player, mediaItem)
            }
        })

        applyOffload(player, offloadEnabled)
        return player
    }

    /**
     * Смена профиля энергосбережения «на лету»: заново создаём плеер с новым буфером и переносим в него всё состояние
     * (трек, позицию, паузу/игру, громкость, скорость). Для файлов пауза не заметна, радио переподключается на секунду.
     */
    private fun rebuildPlayer(level: Int) {
        val sess = session ?: return
        val old = PlayerBridge.rawPlayer ?: return
        if (level == PlayerBridge.networkLevel) return
        // Сначала создаём НОВЫЙ плеер и только потом освобождаем старый: если создание не удалось, прежний плеер остаётся
        // рабочим (раньше старый освобождался первым, а при ошибке в «каталоге» оставался мёртвый плеер — кнопка play
        // крутила анимацию и ничего не играло).
        val np = try { buildPlayer(level) } catch (e: Exception) { return }
        var items: List<MediaItem> = emptyList()
        var index = 0
        var pos = 0L
        var playWhenReady = false
        var params = old.playbackParameters
        var volume = 1f
        var repeat = Player.REPEAT_MODE_OFF
        var shuffle = false
        var wasIdle = true
        try {
            items = (0 until old.mediaItemCount).map { old.getMediaItemAt(it) }
            index = old.currentMediaItemIndex
            pos = old.currentPosition
            playWhenReady = old.playWhenReady
            params = old.playbackParameters
            volume = old.volume
            repeat = old.repeatMode
            shuffle = old.shuffleModeEnabled
            wasIdle = old.playbackState == Player.STATE_IDLE
            // у живого потока (радио) «позиция» — это время с момента подключения: перематывать на неё нельзя
            val scheme = old.currentMediaItem?.localConfiguration?.uri?.scheme?.lowercase()
            if ((scheme == "http" || scheme == "https") && (old.duration == C.TIME_UNSET || old.isCurrentMediaItemLive)) pos = 0L
        } catch (e: Exception) {
        }
        // Общий аудио-процессор (эквалайзер) не должен работать в двух плеерах одновременно.
        try { old.pause() } catch (e: Exception) { }
        try { old.release() } catch (e: Exception) { }
        installPlayer(sess, np, level)
        try {
            np.repeatMode = repeat
            np.shuffleModeEnabled = shuffle
            np.volume = volume
            np.playbackParameters = params
            if (items.isNotEmpty()) {
                np.setMediaItems(items, index.coerceIn(0, items.size - 1), pos)
                if (!wasIdle) np.prepare()
                np.playWhenReady = playWhenReady
            }
            applyWakeMode(np, np.currentMediaItem)
        } catch (e: Exception) {
            // состояние не перенеслось — плеер всё равно рабочий (пустой); приложение загрузит трек само
        }
    }

    /** Подключает уже созданный плеер к сессии и к «мосту» приложения. */
    private fun installPlayer(sess: MediaSession, np: ExoPlayer, level: Int) {
        PlayerBridge.rawPlayer = np
        PlayerBridge.networkLevel = level
        sess.player = BridgePlayer(np)
    }

    /**
     * Самовосстановление: выбрасываем текущий плеер (даже если он завис или уже освобождён) и ставим новый,
     * пустой, с тем же уровнем сети. Трек после этого загружает приложение (Hub.recoverPlayback).
     */
    private fun resetPlayer() {
        val sess = session ?: return
        val old = PlayerBridge.rawPlayer
        val level = PlayerBridge.networkLevel
        val volume = try { old?.volume ?: 1f } catch (e: Exception) { 1f }
        val params = try { old?.playbackParameters } catch (e: Exception) { null }
        try { old?.pause() } catch (e: Exception) { }
        try { old?.release() } catch (e: Exception) { }
        val np = buildPlayer(level)
        np.volume = volume
        if (params != null) np.playbackParameters = params
        installPlayer(sess, np, level)
    }

    /** Сетевой поток (радио, http/https) — держим Wi-Fi вместе с процессором; локальные файлы — только процессор. */
    private fun applyWakeMode(player: ExoPlayer, item: MediaItem?) {
        val scheme = item?.localConfiguration?.uri?.scheme?.lowercase()
        val remote = scheme == "http" || scheme == "https"
        player.setWakeMode(if (remote) C.WAKE_MODE_NETWORK else C.WAKE_MODE_LOCAL)
    }

    private fun setOffload(on: Boolean) {
        offloadEnabled = on
        val p = PlayerBridge.rawPlayer as? ExoPlayer ?: return
        applyOffload(p, on)
    }

    /**
     * Аппаратный offload: сжатый звук (MP3/AAC/FLAC…) отдаётся прямо в DSP устройства, процессор в это время «спит».
     * Нужен Android 10+. ExoPlayer сам включает его только если формат/устройство поддерживают, иначе обычное декодирование.
     * В Media3 1.4 «спящий» режим планировщика при offload включается самим плеером (отдельного вызова больше нет).
     * Wi-Fi держим включённым только для сетевых потоков (см. applyWakeMode).
     */
    private fun applyOffload(player: ExoPlayer, on: Boolean) {
        if (android.os.Build.VERSION.SDK_INT < 29) return
        try {
            val prefs = androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(
                    if (on) androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                    else androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                )
                // аудиокниги: короткий разрыв между файлами не страшен, зато offload работает на большем числе устройств
                .setIsGaplessSupportRequired(false)
                // при смене скорости без поддержки в DSP ExoPlayer сам вернётся на обычное декодирование
                .setIsSpeedChangeSupportRequired(true)
                .build()
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setAudioOffloadPreferences(prefs)
                .build()
        } catch (e: Exception) {
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        PlayerBridge.rawPlayer = null
        PlayerBridge.setOffload = null
        PlayerBridge.rebuildPlayer = null
        PlayerBridge.resetPlayer = null
        session?.let {
            it.player.release()
            it.release()
        }
        session = null
        super.onDestroy()
    }
}
