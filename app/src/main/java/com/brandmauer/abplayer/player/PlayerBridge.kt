package com.brandmauer.abplayer.player

import androidx.media3.common.Player

/**
 * Мост между системным уведомлением/кнопками гарнитуры и очередью приложения.
 * Очередь (перемешивание, повтор, таймер сна) живёт в Hub, плеер получает по одному треку.
 */
object PlayerBridge {
    @Volatile var onNext: (() -> Unit)? = null
    @Volatile var onPrev: (() -> Unit)? = null

    /** Название композиции из потока радио: (id трека, название или null). Вызывается в главном потоке. */
    @Volatile var onStreamTitle: ((String?, String?) -> Unit)? = null

    /** Обложка, которую плеер сам нашёл внутри файла: (id трека, байты картинки). Главный поток. */
    @Volatile var onEmbeddedArt: ((String?, ByteArray) -> Unit)? = null

    /** Показывать ли обложку в уведомлении/на экране блокировки (настройка «Обложки»). */
    @Volatile var coverInNotification: Boolean = false

    /**
     * Сам ExoPlayer (сервис и приложение живут в одном процессе). Перемотку внутри приложения
     * делаем через него: из команд для уведомления перемотка убрана, а MediaController без
     * неё перематывать бы не смог.
     */
    @Volatile var rawPlayer: Player? = null

    /** Уровень «Сеть и радио» (0/1/2), под который создан текущий плеер (размер буфера задаётся при создании). */
    @Volatile var networkLevel: Int = 1

    /** Пересоздать плеер под новый уровень сети с переносом состояния — смена профиля энергосбережения «на лету». */
    @Volatile var rebuildPlayer: ((Int) -> Unit)? = null

    /**
     * Полностью пересоздать ExoPlayer (новый экземпляр, пустая очередь) и подключить его к сессии. Нужен для самовосстановления,
     * если плеер «завис» после долгой паузы/ночи: приложение само запускает его заново и загружает трек с прежней позиции.
     */
    @Volatile var resetPlayer: (() -> Unit)? = null

    /** Включить/выключить аппаратный offload звука (Android 10+). */
    @Volatile var setOffload: ((Boolean) -> Unit)? = null
}
