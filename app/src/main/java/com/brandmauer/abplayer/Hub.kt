package com.brandmauer.abplayer

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.brandmauer.abplayer.data.AbState
import com.brandmauer.abplayer.data.AppData
import com.brandmauer.abplayer.data.BOOKMARKS
import com.brandmauer.abplayer.data.BUILTIN_LISTS
import com.brandmauer.abplayer.data.COVER_ALL
import com.brandmauer.abplayer.data.COVER_NOTIFICATION
import com.brandmauer.abplayer.data.COVER_PLAYER
import com.brandmauer.abplayer.data.COVER_ALL
import com.brandmauer.abplayer.data.COVER_NOTIFICATION
import com.brandmauer.abplayer.data.COVER_PLAYER
import com.brandmauer.abplayer.data.coverInNotification
import com.brandmauer.abplayer.data.DEFAULT_PLAYLIST_ID
import com.brandmauer.abplayer.data.DEFAULT_PLAYLIST_NAME
import com.brandmauer.abplayer.data.EqPreset
import com.brandmauer.abplayer.data.FAVORITES
import com.brandmauer.abplayer.data.GroupSpec
import com.brandmauer.abplayer.data.Importer
import com.brandmauer.abplayer.data.M3uEntry
import com.brandmauer.abplayer.data.Playlist
import com.brandmauer.abplayer.data.PowerSettings
import com.brandmauer.abplayer.data.RADIO
import com.brandmauer.abplayer.data.RadioImport
import com.brandmauer.abplayer.data.SleepState
import com.brandmauer.abplayer.data.SortSpec
import com.brandmauer.abplayer.data.SoundState
import com.brandmauer.abplayer.data.Storage
import com.brandmauer.abplayer.data.Track
import com.brandmauer.abplayer.data.EqProfile
import com.brandmauer.abplayer.data.OUT_BT_PREFIX
import com.brandmauer.abplayer.data.OUT_SPEAKER
import com.brandmauer.abplayer.data.OUT_WIRED
import com.brandmauer.abplayer.data.ViewState
import com.brandmauer.abplayer.i18n.I18n
import com.brandmauer.abplayer.player.PlaybackService
import com.brandmauer.abplayer.player.PlayerBridge
import com.brandmauer.abplayer.player.SoundEngine
import com.brandmauer.abplayer.util.fmtDurLongSec
import com.brandmauer.abplayer.util.fmtTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

// ============================================================ модели интерфейса

sealed class Sheet {
    object Sleep : Sheet()
    object Sound : Sheet()
    data class ListSheet(val view: String) : Sheet()             // favorites | bookmarks
    object Ab : Sheet()
    data class Sort(val plid: String) : Sheet()
    data class TrackMenu(val id: String, val view: String) : Sheet()
    data class AddToPlaylist(val ids: List<String>) : Sheet()
    data class PlaylistMenu(val plid: String, val view: String?) : Sheet()
    object SeekStep : Sheet()
    object About : Sheet()
    object Privacy : Sheet()
    object Licenses : Sheet()
    object AccentColor : Sheet()
    object Covers : Sheet()
    object Effects : Sheet()
    data class Menu(val onSettingsScreen: Boolean) : Sheet()
    data class RadioEdit(val id: String) : Sheet()
    data class TrackInfo(val id: String) : Sheet()
    object Power : Sheet()
    object Help : Sheet()
}

sealed class Popup {
    class TextInput(
        val title: String,
        val initial: String,
        val placeholder: String,
        val confirm: String,
        val number: Boolean,
        val onOk: (String) -> Boolean          // true — закрыть диалог, false — оставить (ошибка ввода)
    ) : Popup()

    class Confirm(val title: String, val text: String, val confirm: String, val onOk: () -> Unit) : Popup()
    class EqMenu(val presetId: String) : Popup()
    class AddSource(val view: String) : Popup()
    class RadioSource : Popup()
}

enum class AddKind { FILES, FOLDER, RADIO_URL, RADIO_FILE, COVER_IMAGE }
data class AddRequest(val kind: AddKind, val view: String, val nonce: Long)
data class NavRequest(val screen: Int, val nonce: Long)
data class ToastMsg(val text: String, val nonce: Long, val durationMs: Long = 1900L)

const val VIEW_PLAYLIST = "playlist"
const val APP_NAME = "ABPlayer"
const val APP_VERSION = "1.0.16"
const val APP_DEVELOPER = "Brandmauer"

/** Сколько ждём соединения с радиостанцией, прежде чем переключиться на следующую. */
const val RADIO_CONNECT_TIMEOUT_MS = 15_000L

/** Пауза радио дольше этого времени: при возобновлении станция подключается заново (буфер и соединение устарели). */
const val RADIO_STALE_PAUSE_MS = 20_000L
/** Сколько раз подряд приложение молча переподключается к станции после ошибки, прежде чем сообщить о сбое. */
const val RADIO_MAX_RETRIES = 3

/** Через сколько разобранная ссылка .m3u станции считается устаревшей и берётся заново. */
const val RADIO_RESOLVE_TTL_MS = 10 * 60 * 1000L

/** Откат после паузы: пауза дольше этого времени — продолжаем на REWIND_AFTER_PAUSE_MS раньше. */
const val REWIND_PAUSE_MIN_MS = 5 * 60 * 1000L
const val REWIND_AFTER_PAUSE_MS = 20_000L

/** Файл (не радио) не начал играть за это время после нажатия play — считаем, что плеер завис, и перезапускаем его. */
const val FILE_START_TIMEOUT_MS = 8_000L

/** Автоматическая точка B при остановке таймера сна — только у треков длиннее этого порога. */
const val AUTO_B_MIN_MS = 60 * 60 * 1000L

/** Сколько суток хранится сохранённая позиция файла без изменений (см. Hub.sweepPositions). */
const val POSITION_TTL_DAYS = 60

/** Как часто (в сутках) выполняется автоочистка позиций. */
const val POSITION_SWEEP_INTERVAL_DAYS = 30

/** Автоточка A ставится только у треков длиннее этого порога (аудиокниги, лекции и т.п.). */
const val AUTO_A_MIN_MS = 15 * 60 * 1000L

/** Стартовый набор радиостанций (добавляется один раз при первом запуске этой версии). */
private val DEFAULT_RADIO = listOf(
    M3uEntry("Модель для сборки", "http://mds-station.com/mds.m3u"),
    M3uEntry("Радио Фантастики", "http://fantasyradioru.no-ip.biz:8002/live"),
    M3uEntry("Старое радио", "https://staroeradio.ru/ices128.m3u"),
    M3uEntry("Радио Книга", "http://94.181.45.104:8005"),
    M3uEntry("PulseEDM", "http://pulseedm.cdnstream1.com:8124/1373_128"),
    M3uEntry("SomaFM", "https://ice1.somafm.com/groovesalad-128-mp3"),
    M3uEntry("Radio Paradise", "https://stream.radioparadise.com/mp3-128")
)

/**
 * Вся логика приложения: библиотека, очередь, воспроизведение, таймер сна, A–B, эквалайзер.
 * Живёт на уровне процесса (создаётся в Application), поэтому очередь продолжает работать,
 * даже если Activity закрыта, а музыка играет в фоне.
 */
class Hub(private val app: Application) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ВАЖНО: эти поля должны быть объявлены ДО блока init (он запускает startLoops, а Kotlin инициализирует свойства
    // по порядку текста) — иначе в init они ещё null и приложение падает при запуске.
    /** Будит основной цикл: он спит, пока интерфейс скрыт и ничего не играет. */
    private val loopWake = Channel<Unit>(Channel.CONFLATED)
    /** Будит цикл таймера сна (продление, возвращение интерфейса на экран). */
    private val sleepWake = Channel<Unit>(Channel.CONFLATED)
    /** Сообщение плеера «дошли до точки B» для повтора A–B и ключ, для которого оно поставлено. */
    private var abMessage: androidx.media3.exoplayer.PlayerMessage? = null
    private var abKey: String? = null
    /** Момент окончания таймера сна по SystemClock.elapsedRealtime(). */
    private var sleepEndAt = 0L
    private var sleepWasFading = false
    private val collator: Collator = Collator.getInstance(Locale.forLanguageTag("ru"))

    // ------------------------------------------------------------ постоянные данные
    var data by mutableStateOf(AppData())
        private set

    /** Библиотека прочитана с диска и интерфейс может работать (читается из Compose — до этого показывается заставка). */
    var ready by mutableStateOf(false)
        private set
    /** То же без Compose: пока false, на диск ничего не пишем — иначе пустые данные затёрли бы библиотеку. */
    @Volatile private var dataLoaded = false
    /** Ждут действия, пришедшие раньше, чем загрузилась библиотека (открытие файла из другой программы и т.п.). */
    private val loadedSignal = CompletableDeferred<Unit>()
    private val bootPrefs by lazy { app.getSharedPreferences("abp_boot", Context.MODE_PRIVATE) }
    private var bootTheme: String? = null

    /** Тема для заставки при запуске: хранится отдельно, чтобы не ждать чтения всей библиотеки. */
    fun bootDark(): Boolean = bootPrefs.getString("theme", "dark") != "light"

    private fun mirrorBoot() {
        val t = data.theme
        if (t == bootTheme) return
        bootTheme = t
        bootPrefs.edit().putString("theme", t).apply()
    }
    private val positions = HashMap<String, Long>()
    /** День (эпоха, сутки) последнего изменения сохранённой позиции — для автоочистки устаревших позиций. */
    private val positionDays = HashMap<String, Int>()
    /** День последней очистки позиций; хранится в SharedPreferences, чтобы месячный интервал переживал перезапуск приложения. */
    private val maintPrefs by lazy { app.getSharedPreferences("abp_maint", Context.MODE_PRIVATE) }
    private var lastPosSweepDay: Int? = null
    private var positionsDirty = false
    private var saveJob: Job? = null

    // ------------------------------------------------------------ состояние сессии
    var currentTrackId by mutableStateOf<String?>(null)
        private set
    var currentPlaylistId by mutableStateOf(DEFAULT_PLAYLIST_ID)
        private set
    var viewedPlaylistId by mutableStateOf(DEFAULT_PLAYLIST_ID)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    /** Идёт загрузка (буферизация) трека или потока — кнопка play показывает анимацию. */
    var isLoading by mutableStateOf(false)
        private set
    /** Название композиции, которое радиостанция передаёт в потоке (ICY); null — не передаёт. */
    var streamTitle by mutableStateOf<String?>(null)
        private set
    var positionMs by mutableStateOf(0L)
        private set
    var durationMs by mutableStateOf(0L)
        private set
    var seekPreviewMs by mutableStateOf<Long?>(null)
    var shuffle by mutableStateOf(false)
        private set
    var repeatMode by mutableStateOf(0)                 // 0 выкл, 1 плейлист, 2 трек
        private set
    var showRemaining by mutableStateOf(false)
    private var abState by mutableStateOf(AbState())
    var ab: AbState
        get() = abState
        private set(v) { abState = v; syncAbMessage() }
    var sleep by mutableStateOf(SleepState())
        private set

    val views = mutableStateMapOf(
        VIEW_PLAYLIST to ViewState(),
        FAVORITES to ViewState(),
        BOOKMARKS to ViewState()
    )
    val sheets = mutableStateListOf<Sheet>()
    var popup by mutableStateOf<Popup?>(null)
        private set
    var toastMsg by mutableStateOf<ToastMsg?>(null)
        private set
    var navRequest by mutableStateOf<NavRequest?>(null)
    var addRequest by mutableStateOf<AddRequest?>(null)

    // ------------------------------------------------------------ плеер
    private var controller: MediaController? = null
    private var pendingAction: (() -> Unit)? = null
    private var lastStartMs = 0L
    private var shuffleOrder: List<String> = emptyList()
    private var sleepJob: Job? = null
    private var nonce = 0L

    // ------------------------------------------------------------ энергосбережение
    /** Заряд батареи в % и системный режим энергосбережения — для автоматики профиля. */
    var batteryLevelPct by mutableStateOf(100)
        private set
    var systemPowerSaveOn by mutableStateOf(false)
        private set
    private var powerReceiver: BroadcastReceiver? = null

    // ------------------------------------------------------------ радио: таймаут и повторы
    private var radioWatchdog: Job? = null
    /** Сторож запуска обычного файла: если за FILE_START_TIMEOUT_MS плеер так и не заиграл — перезапускаем его (см. recoverPlayback). */
    private var fileWatchdog: Job? = null
    /** Сколько раз подряд приложение уже перезапускало «зависший» плеер для текущей попытки запуска. */
    private var recoveryCount = 0
    /** Идёт подключение MediaController к сервису воспроизведения (объявлено до блока init, иначе инициализатор сбросил бы флаг). */
    private var connecting = false
    // «Откат 20с после паузы»: когда и на каком треке поставили на паузу (хранится на диске — переживает перезапуск процесса)
    private val pausePrefs by lazy { app.getSharedPreferences("abp_pause", Context.MODE_PRIVATE) }
    private var pauseWallMs = 0L
    private var pauseTrackId: String? = null
    private var timeoutChain = 0
    /** Сколько раз подряд плеер сам (молча) переподключался к станции после ошибки. */
    private var radioRetryCount = 0
    private var pausedAtMs = 0L
    private val resolvedStreams = HashMap<String, Resolved>()
    private val artTried = HashSet<String>()

    // ------------------------------------------------------------ добавление файлов и сканирование
    @Volatile private var stopRequested = false
    /** null — ничего не идёт; "scan" — обход папки; "import" — чтение и добавление файлов. */
    var busyKind by mutableStateOf<String?>(null)
        private set
    /** Сколько файлов найдено при обходе папки (обновляется по мере сканирования). */
    var scanFound by mutableStateOf(0)
        private set

    // ------------------------------------------------------------ таймер сна: постукивание
    private var sensorManager: SensorManager? = null
    private var knockListener: SensorEventListener? = null
    private var lastKnockAt = 0L
    // фильтрованное (без гравитации) ускорение и «спокойствие» перед ударом — для распознавания лёгкого касания
    private var kgx = 0f
    private var kgy = 0f
    private var kgz = 0f
    private var kInit = false
    private var kLastTs = 0L
    private var kCalm = 0f

    /** Виден ли интерфейс (приложение на экране). В фоне лишние обновления интерфейса не нужны — экономим заряд. */
    @Volatile var uiVisible = true

    /** Интерфейс снова на экране: сразу подтягиваем актуальную позицию, не дожидаясь очередного опроса. */
    fun onUiShown() {
        uiVisible = true
        if (!dataLoaded) return   // подключение к сервису и циклы запустятся сразу после загрузки библиотеки
        wakeLoop()
        wakeSleep()
        // сервис воспроизведения мог быть остановлен системой, пока приложение было свёрнуто: контроллер «мёртв» — подключаемся заново
        val cc = controller
        if (cc == null || !cc.isConnected) { reconnectController(); return }
        val c = cc
        if (seekPreviewMs == null && currentTrackId != null) positionMs = c.currentPosition
    }

    /** Интерфейс скрыт (экран выключен / приложение свёрнуто): сохраняем всё и даём фоновым циклам уснуть. */
    fun onUiHidden() {
        uiVisible = false
        saveNow()
        wakeSleep()
    }

    /**
     * Порог «касания» (м/с², модуль ускорения после удаления гравитации и медленных движений) по уровню
     * чувствительности. Раньше был жёсткий порог 13 м/с² — это именно удар. Лёгкое постукивание пальцем
     * по корпусу даёт пик ~1–4 м/с²; медленные движения (поворот, дыхание, дрожь руки) этим фильтром отсекаются.
     */
    private fun knockThreshold(): Float {
        // 100 («макс») → 4.0 м/с² — прежний уровень «Низкая», теперь это самая высокая чувствительность;
        // 0 («мин») → 20 м/с² (только очень сильный стук). Между ними — плавная (экспоненциальная) шкала.
        val v = data.knockSensitivity.coerceIn(0, 100) / 100.0
        return (20.0 * Math.pow(0.2, v)).toFloat()
    }

    // состояние распознавания двойного стука «тук-тук»
    private var kAbove = false
    private var kFirstTs = 0L
    private var kValley = false

    init {
        // Библиотеку читаем в фоне: интерфейс (заставка) появляется сразу, а не после разбора всего library.json.
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { Storage.load(app) }
            data = loaded.data
            dataLoaded = true
            I18n.lang = I18n.resolve(data.language)
            positions.putAll(loaded.positions)
            positionDays.putAll(loaded.posDays)
            // у радио нет «позиции»: старые записи (они и ломали запуск станций) выбрасываем
            data.tracks.filter { it.isRadio }.forEach { positions.remove(it.id) }
            // позиции из прежних версий без даты считаем «обновлёнными сегодня»: у них начинается свои 60 дней
            val today0 = epochDay()
            positions.keys.forEach { if (!positionDays.containsKey(it)) positionDays[it] = today0 }
            sweepPositions()
            // переход со старого формата (позиции внутри library.json): один раз выносим их в отдельный файл
            if (!Storage.hasPositionsFile(app) && positions.isNotEmpty()) positionsDirty = true
            flushPositions()
            PlayerBridge.onNext = { playNext(false) }
            PlayerBridge.onPrev = { playPrev() }
            PlayerBridge.onStreamTitle = { id, title -> onStreamTitle(id, title) }
            PlayerBridge.onEmbeddedArt = { id, bytes -> onEmbeddedArt(id, bytes) }
            PlayerBridge.coverInNotification = coverInNotification(data.coverMode)
            SoundEngine.apply(data.sound)
            seedDefaultPlaylist()
            seedDefaultRadio()
            pauseWallMs = pausePrefs.getLong("at", 0L)
            pauseTrackId = pausePrefs.getString("track", null)
            mirrorBoot()
            connectController()
            startLoops()
            startPowerObservers()
            startVolumeWatch()
            startOutputObserver()
            ready = true
            loadedSignal.complete(Unit)
        }
    }

    /**
     * Первый запуск: создаёт пустой плейлист «Default» и делает его активным. Для тех, у кого уже
     * есть библиотека, плейлист просто появляется в списке, а активный список не меняется.
     * Дальше запоминается последний проигрываемый список.
     */
    private fun seedDefaultPlaylist() {
        if (!data.defaultSeeded) {
            val fresh = data.tracks.isEmpty() && data.playlists.isEmpty()
            update { d ->
                val pls = if (d.playlists.any { it.id == DEFAULT_PLAYLIST_ID }) d.playlists
                else d.playlists + Playlist(DEFAULT_PLAYLIST_ID, DEFAULT_PLAYLIST_NAME)
                d.copy(playlists = pls, defaultSeeded = true, activePlaylist = if (fresh) DEFAULT_PLAYLIST_ID else d.activePlaylist)
            }
        }
        // активный список по умолчанию — «Default» (пока пользователь не начал играть из другого)
        val ap = data.activePlaylist.takeIf { it.isNotEmpty() && listExists(it) } ?: DEFAULT_PLAYLIST_ID
        currentPlaylistId = ap
        viewedPlaylistId = ap
    }

    /** Запоминает, из какого списка сейчас играет плеер. */
    private fun setActivePlaylist(plid: String) {
        currentPlaylistId = plid
        if (data.activePlaylist != plid) update { it.copy(activePlaylist = plid) }
    }

    /** Один раз добавляет стартовые станции в «Радио» (уже имеющиеся по ссылке не дублируются). */
    private fun seedDefaultRadio() {
        if (data.radioSeeded) return
        val known = data.tracks.filter { it.isRadio }.map { it.uri }.toSet()
        var counter = data.orderCounter
        val stamp = System.currentTimeMillis()
        val fresh = DEFAULT_RADIO.filter { !known.contains(it.url) }.mapIndexed { i, e ->
            counter += 1
            Track(
                id = "rs${stamp}_$i", uri = e.url, title = e.name, artist = "Онлайн радио",
                folder = "", durationMs = 0L, size = 0L, playlists = listOf(RADIO),
                order = counter, isRadio = true
            )
        }
        update { it.copy(tracks = it.tracks + fresh, orderCounter = counter, radioSeeded = true) }
    }

    private fun nextNonce(): Long {
        nonce += 1
        return nonce
    }

    // ============================================================ сохранение

    private fun persist() {
        if (!dataLoaded) return
        saveJob?.cancel()
        saveJob = scope.launch {
            // «Фоновые процессы» в энергосбережении: реже сохраняем на диск при экономии,
            // чаще — в режиме «Качество» (меньше риск потерять недавние изменения).
            delay(persistDelayMs())
            val d = data
            withContext(Dispatchers.IO) { Storage.save(app, d) }
        }
    }

    // Библиотека меняется только по действиям пользователя, так что откладываем запись подольше (при уходе в фон
    // всё равно пишем сразу — см. saveNow). Позиции пишутся отдельно и не зависят от этой задержки.
    private fun persistDelayMs(): Long = when (effectivePower().background) {
        0 -> 5000L
        2 -> 500L
        else -> 2000L
    }

    /** Немедленное сохранение (вызывается при уходе приложения в фон). */
    fun saveNow() {
        if (!dataLoaded) return
        saveJob?.cancel()
        capturePosition()
        sweepPositions()
        val d = data
        val p = HashMap(positions)
        val pd = HashMap(positionDays)
        positionsDirty = false
        scope.launch(Dispatchers.IO) {
            Storage.save(app, d)
            Storage.savePositions(app, p, pd)
        }
    }

    private fun update(block: (AppData) -> AppData) {
        data = block(data)
        if (dataLoaded) mirrorBoot()
        persist()
        syncPowerRuntime()
    }

    /** Изменение библиотеки + удаление «осиротевших» треков (refreshAll → gcOrphans в прототипе). */
    private fun commit(block: (AppData) -> AppData) {
        update(block)
        gcOrphans()
    }

    private fun epochDay(): Int = (System.currentTimeMillis() / 86_400_000L).toInt()

    /**
     * Автоочистка сохранённых позиций. Позиция живёт 60 дней с момента ПОСЛЕДНЕГО изменения (а не с создания:
     * иначе у книги, которую слушают третий месяц, пропала бы закладка места). Заодно удаляются позиции треков, которых
     * в библиотеке уже нет. Проход выполняется не чаще раза в месяц (POSITION_SWEEP_INTERVAL_DAYS): при запуске
     * приложения проверяется дата прошлой очистки (одно число в SharedPreferences, читается один раз).
     * Сам проход — по словарю в памяти; на диск пишем, только если что-то удалено.
     */
    private fun sweepPositions() {
        val today = epochDay()
        val last = lastPosSweepDay ?: maintPrefs.getInt("posSweepDay", 0).also { lastPosSweepDay = it }
        if (last != 0 && today - last < POSITION_SWEEP_INTERVAL_DAYS) return
        lastPosSweepDay = today
        maintPrefs.edit().putInt("posSweepDay", today).apply()
        val ids = data.tracks.mapTo(HashSet()) { it.id }
        val before = positions.size
        val it = positions.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            val day = positionDays[e.key] ?: today
            val stale = today - day > POSITION_TTL_DAYS && e.key != currentTrackId
            if (stale || !ids.contains(e.key)) {
                it.remove()
                positionDays.remove(e.key)
            }
        }
        positionDays.keys.retainAll(positions.keys)
        if (positions.size != before) positionsDirty = true
    }

    /** Запись только позиций (маленький файл) — библиотека при этом не перезаписывается. */
    private fun flushPositions() {
        sweepPositions()
        if (positionsDirty) {
            positionsDirty = false
            val p = HashMap(positions)
            val pd = HashMap(positionDays)
            scope.launch(Dispatchers.IO) { Storage.savePositions(app, p, pd) }
        }
    }

    // ============================================================ уведомления/навигация

    fun toast(text: String, durationMs: Long = 1900L) {
        toastMsg = ToastMsg(text, nextNonce(), durationMs)
    }

    /**
     * Разовое пояснение: показывается один раз, когда пользователь включает функцию,
     * и больше не повторяется (вместо постоянного мелкого текста в настройках).
     */
    fun hintOnce(key: String, text: String) {
        if (data.shownHints.contains(key)) return
        update { it.copy(shownHints = it.shownHints + key) }
        toast(text, 6000L)
    }

    fun navigate(screen: Int) {
        navRequest = NavRequest(screen, nextNonce())
    }

    // ============================================================ шторки и диалоги

    fun openSheet(s: Sheet) {
        if (sheets.contains(s)) return
        sheets.add(s)
    }

    fun closeSheet() {
        if (sheets.isNotEmpty()) sheets.removeAt(sheets.size - 1)
    }

    fun closeAllSheets() {
        sheets.clear()
    }

    fun replaceSheet(s: Sheet) {
        closeSheet()
        openSheet(s)
    }

    fun topSheet(): Sheet? = sheets.lastOrNull()

    fun closePopup() {
        popup = null
    }

    fun askText(
        title: String, initial: String = "", placeholder: String = "", confirm: String = "Готово",
        number: Boolean = false, onOk: (String) -> Boolean
    ) {
        popup = Popup.TextInput(title, initial, placeholder, confirm, number, onOk)
    }

    fun askConfirm(title: String, text: String, confirm: String, onOk: () -> Unit) {
        popup = Popup.Confirm(title, text, confirm, onOk)
    }

    fun openEqMenu(id: String) {
        popup = Popup.EqMenu(id)
    }

    fun requestAdd(view: String) {
        popup = Popup.AddSource(view)
    }

    /** «Добавить файл / папку» из меню плейлиста: сразу открываем системный выбор и добавляем в этот плейлист. */
    fun addToPlaylistDirect(kind: AddKind, plid: String) {
        closeSheet()
        popup = null
        addRequest = AddRequest(kind, "pl:$plid", nextNonce())
    }

    fun chooseAdd(kind: AddKind, view: String) {
        popup = null
        addRequest = AddRequest(kind, view, nextNonce())
    }

    // ============================================================ библиотека: чтение

    fun trackById(id: String?): Track? = if (id == null) null else data.tracks.firstOrNull { it.id == id }

    fun listName(plid: String): String = when (plid) {
        FAVORITES -> data.nameFavorites
        BOOKMARKS -> data.nameBookmarks
        RADIO -> data.nameRadio
        else -> data.playlists.firstOrNull { it.id == plid }?.name ?: ""
    }

    fun listExists(plid: String): Boolean =
        BUILTIN_LISTS.contains(plid) || data.playlists.any { it.id == plid }

    fun getSort(plid: String): SortSpec = data.sort[plid] ?: SortSpec()
    /**
     * Группировка по умолчанию: «по папкам» во всех плейлистах (в том числе новых), кроме закладок, избранного и радио.
     * Явный выбор пользователя (хранится в data.group) всегда важнее умолчания.
     */
    fun getGroup(plid: String): GroupSpec =
        data.group[plid] ?: if (BUILTIN_LISTS.contains(plid)) GroupSpec() else GroupSpec("folder")

    /** Тип файла для группировки «по типу»: расширение имени/ссылки, либо «Радио» для потоков. */
    fun typeOf(t: Track): String {
        if (t.isRadio) return "Радио"
        val src = t.title.ifEmpty { t.uri }
        val ext = src.substringAfterLast('.', "").substringBefore('?').trim()
        return if (ext.isNotEmpty() && ext.length <= 5 && ext.all { it.isLetterOrDigit() }) ext.uppercase() else "Без типа"
    }

    fun playlistTracks(plid: String): List<Track> {
        val base = when (plid) {
            FAVORITES -> data.tracks.filter { it.favorite }
            BOOKMARKS -> data.tracks.filter { data.bookmarks.containsKey(it.id) }
            RADIO -> data.tracks.filter { it.isRadio }
            else -> data.tracks.filter { it.playlists.contains(plid) }
        }
        val s = getSort(plid)
        val d = if (s.dir == "asc") 1 else -1
        val cmp: Comparator<Track> = when (s.mode) {
            "name" -> Comparator<Track> { a, b -> d * collator.compare(a.title, b.title) }
            "duration" -> Comparator<Track> { a, b -> d * a.durationMs.compareTo(b.durationMs) }
            "size" -> Comparator<Track> { a, b -> d * a.size.compareTo(b.size) }
            else -> Comparator<Track> { a, b -> d * a.order.compareTo(b.order) }
        }
        val sorted = base.sortedWith(cmp)
        // Группировка — отдельно от сортировки: кластеризуем, сохраняя порядок сортировки внутри группы
        return when (getGroup(plid).mode) {
            "folder" -> sorted.sortedBy { if (it.folder.isEmpty()) "\uffff" else it.folder.lowercase() }
            "type" -> sorted.sortedBy { typeOf(it).lowercase() }
            else -> sorted
        }
    }

    fun setGroup(plid: String, mode: String) {
        update { it.copy(group = it.group + (plid to GroupSpec(mode))) }
    }

    fun viewPlid(v: String): String = when {
        v == VIEW_PLAYLIST -> viewedPlaylistId
        v.startsWith("pl:") -> v.removePrefix("pl:")
        else -> v
    }

    fun viewState(v: String): ViewState = views[v] ?: ViewState()

    private fun setView(v: String, f: (ViewState) -> ViewState) {
        views[v] = f(viewState(v))
    }

    fun viewList(v: String): List<Track> {
        var list = playlistTracks(viewPlid(v))
        val q = viewState(v).search.trim().lowercase()
        if (q.isNotEmpty()) {
            list = list.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
        }
        return list
    }

    /** Подпись под названием списка: «N треков · 1:23». */
    fun activeList(): List<Track> = playlistTracks(currentPlaylistId)

    fun mainInfoText(): String {
        val cur = trackById(currentTrackId)
        if (cur != null && cur.isRadio) return "Онлайн радио"
        val list = activeList()
        val idx = list.indexOfFirst { it.id == currentTrackId }
        return if (idx < 0) "Файлов: ${list.size}" else "Трек ${idx + 1} из ${list.size}"
    }

    /** Порядок карточек на экране «Плейлисты и Настройки» (включая «Избранное» и «Радио»). */
    fun orderedListIds(): List<String> {
        val valid = listOf(FAVORITES, BOOKMARKS, RADIO) + data.playlists.map { it.id }
        val ord = data.listOrder.filter { valid.contains(it) }.toMutableList()
        // «Закладки» теперь тоже карточка-плейлист: если её ещё нет в сохранённом порядке — ставим сразу после «Избранного»
        if (!ord.contains(BOOKMARKS)) {
            val fi = ord.indexOf(FAVORITES)
            ord.add(if (fi >= 0) fi + 1 else 0, BOOKMARKS)
        }
        valid.forEach { if (!ord.contains(it)) ord.add(it) }
        return ord
    }

    fun reorderLists(ids: List<String>) {
        update { d ->
            d.copy(
                listOrder = ids,
                playlists = d.playlists.sortedBy { p -> ids.indexOf(p.id) }
            )
        }
    }

    // ============================================================ подключение плеера

    private fun connectController() {
        if (connecting) return
        connecting = true
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token)
            .setListener(object : MediaController.Listener {
                // Сервис воспроизведения остановился (система, нехватка памяти, смахивание приложения на паузе): раньше контроллер
                // оставался «мёртвым» — команды play молча терялись, а на кнопке бесконечно крутилась анимация загрузки.
                override fun onDisconnected(c: MediaController) {
                    if (controller === c) controller = null
                    radioWatchdog?.cancel()
                    radioWatchdog = null
                    fileWatchdog?.cancel()
                    fileWatchdog = null
                    isPlaying = false
                    isLoading = false
                    loadRequested = false
                    resolvingStream = false
                    scope.launch {
                        delay(300)
                        if (controller == null) connectController()
                    }
                }
            })
            .buildAsync()
        future.addListener({
            connecting = false
            try {
                val c = future.get()
                controller = c
                c.addListener(playerListener)
                applyPlaybackParams()
                val act = pendingAction
                pendingAction = null
                act?.invoke()
            } catch (e: Exception) {
                toast("Не удалось запустить плеер")
            }
        }, ContextCompat.getMainExecutor(app))
    }

    /** Выбрасывает «мёртвый» контроллер и подключается к сервису заново (сервис при этом запускается, если он был остановлен). */
    private fun reconnectController() {
        val old = controller
        controller = null
        connecting = false
        try { old?.release() } catch (e: Exception) { }
        connectController()
    }

    /** Контроллер подключён к живому сервису? */
    private fun controllerAlive(): Boolean = controller?.isConnected == true

    private val playerListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            syncPlaying()
            val t = trackById(currentTrackId)
            if (playWhenReady) {
                // «Откат 20с после паузы»: возобновление после долгой паузы (кнопка, уведомление, гарнитура)
                if (t != null) maybeRewindAfterPause(t)
                // любой запуск воспроизведения (уведомление, гарнитура, кнопка) активирует точку A
                if (t != null) autoSetA(t, controller?.currentPosition ?: lastStartMs)
                if (t != null && !t.isRadio && controller?.playbackState != Player.STATE_READY) armFileWatchdog(t)
                if (t != null && t.isRadio) {
                    val c = controller
                    if (c != null && radioNeedsReconnect(c, t)) {
                        // после долгой паузы/остановки/ошибки соединение со станцией мертво — подключаемся заново
                        loadAndPlay(t, 0L)
                    } else {
                        pausedAtMs = 0
                        if (controller?.playbackState != Player.STATE_READY) armRadioWatchdog(t)
                    }
                }
            } else {
                radioWatchdog?.cancel()
                radioWatchdog = null
                fileWatchdog?.cancel()
                fileWatchdog = null
                if (t != null && t.isRadio) pausedAtMs = System.currentTimeMillis()
                else if (t != null && controller?.playbackState != Player.STATE_ENDED) markPaused(t)
            }
        }

        // системная громкость музыки (кнопки громкости) дошла до нуля — ставим на паузу
        override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
            onSystemVolume(volume)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncPlaying()
            if (playbackState == Player.STATE_ENDED) onTrackEnded()
        }

        override fun onIsPlayingChanged(isPlayingNow: Boolean) {
            syncPlaying()
            if (isPlayingNow) wakeLoop()
            else {
                // пауза: точная позиция сразу на диск, дальше основной цикл (если экран выключен) засыпает
                capturePosition()
                flushPositions()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val t = trackById(currentTrackId)
            if (t != null && t.isRadio) {
                val c = controller
                if (c != null && !c.playWhenReady) {
                    // ошибка пришла, пока воспроизведение на паузе (соединение умерло за ночь): ничего не запускаем
                    // сами, просто помечаем, что при следующем запуске нужно подключиться к станции заново
                    pausedAtMs = 1L
                    radioWatchdog?.cancel()
                    radioWatchdog = null
                    isPlaying = false
                    loadRequested = false
                    resolvingStream = false
                    isLoading = false
                    return
                }
                if (radioRetryCount < RADIO_MAX_RETRIES) {
                    // тихие повторные попытки с нарастающей паузой: сразу после пробуждения сеть бывает ещё не готова
                    radioRetryCount += 1
                    val n = radioRetryCount
                    resolvedStreams.remove(t.uri)
                    radioWatchdog?.cancel()
                    radioWatchdog = null
                    loadRequested = true
                    isLoading = true
                    scope.launch {
                        delay(if (n == 1) 400L else 2000L * n)
                        val c2 = controller
                        if (currentTrackId == t.id && c2 != null && c2.playWhenReady) {
                            loadAndPlay(t, 0L, retry = true)
                        } else {
                            loadRequested = false
                            syncPlaying()
                        }
                    }
                    return
                }
            }
            // ошибка воспроизведения файла при запущенном play (например, после долгой паузы звуковое устройство «протухло»):
            // один раз полностью пересоздаём плеер и грузим трек заново, и только потом сообщаем о сбое
            if (t != null && !t.isRadio && recoveryCount < 1 && controller?.playWhenReady == true) {
                recoverPlayback(t)
                return
            }
            radioWatchdog?.cancel()
            radioWatchdog = null
            isPlaying = false
            loadRequested = false
            resolvingStream = false
            isLoading = false
            val failed = t
            scope.launch {
                val gone = failed != null && !failed.isRadio &&
                    withContext(Dispatchers.IO) { Importer.exists(app, Uri.parse(failed.uri)) == false }
                toast(if (gone) "Файл не найден — выберите «Обновить плейлист» в меню плейлиста" else "Не удалось воспроизвести файл")
            }
        }
    }

    /** true с момента запроса загрузки трека/потока и до готовности (или ошибки/паузы). */
    private var loadRequested = false
    /** Идёт разбор ссылки .m3u станции (до старта ExoPlayer) — это тоже «загрузка». */
    private var resolvingStream = false

    private fun syncPlaying() {
        val c = controller ?: return
        isPlaying = c.playWhenReady &&
            c.playbackState != Player.STATE_ENDED &&
            c.playbackState != Player.STATE_IDLE
        if (isPlaying) wakeLoop()
        // плеер мог быть пересоздан (смена профиля, самовосстановление) — переустанавливаем сообщение для A–B
        syncAbMessage()
        // акселерометр для «тук-тук» нужен, только пока звук идёт
        if (sleep.active) applySleepFade()
        if (c.playbackState == Player.STATE_READY || c.playbackState == Player.STATE_ENDED) {
            loadRequested = false
        }
        if (c.playbackState == Player.STATE_READY) {
            // станция ответила: сбрасываем таймаут и счётчики переключений
            radioWatchdog?.cancel()
            radioWatchdog = null
            fileWatchdog?.cancel()
            fileWatchdog = null
            recoveryCount = 0
            timeoutChain = 0
            radioRetryCount = 0
        }
        // Если звук реально идёт (READY + play), загрузка закончена — кружок на кнопке не показываем,
        // даже если какой-то флаг ожидания не успел сброситься.
        isLoading = !c.isPlaying && (resolvingStream ||
            (c.playWhenReady && (loadRequested || c.playbackState == Player.STATE_BUFFERING)))
    }

    /** Активная сеть — мобильная (а не Wi-Fi)? Потоковое радио по 4G держит модем «на связи» постоянно и разряжает батарею в разы быстрее. */
    private fun isOnCellular(): Boolean = try {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        caps != null && caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) &&
            !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    } catch (e: Exception) {
        false
    }

    /** Название композиции из потока радио (ICY/ID3), присланное сервисом воспроизведения. */
    private fun onStreamTitle(mediaId: String?, title: String?) {
        val t = trackById(currentTrackId)
        if (t == null || !t.isRadio || mediaId != t.id) return
        streamTitle = title?.let { com.brandmauer.abplayer.util.TextFix.fixMojibake(it) }?.trim()?.takeIf { it.isNotEmpty() && !it.equals(t.title.trim(), ignoreCase = true) }
    }

    /** Будит основной цикл: он спит, пока интерфейс скрыт и ничего не играет. */
    private fun wakeLoop() { loopWake.trySend(Unit) }

    /** Запоминает текущую позицию трека (в памяти; на диск — flushPositions). */
    private fun capturePosition() {
        val c = controller ?: return
        val id = currentTrackId ?: return
        val pos = c.currentPosition
        if (seekPreviewMs == null) positionMs = pos
        val d = c.duration
        durationMs = if (d != C.TIME_UNSET && d > 0) d else (trackById(id)?.durationMs ?: 0L)
        val st = c.playbackState
        if (data.restorePosition && st != Player.STATE_ENDED && st != Player.STATE_IDLE &&
            trackById(id)?.isRadio != true
        ) {
            if (pos < 3_000L) {
                // в самом начале трека позиция не нужна — запись только раздувает базу
                if (positions.remove(id) != null) { positionDays.remove(id); positionsDirty = true }
            } else if (positions[id] != pos) {
                positions[id] = pos
                positionDays[id] = epochDay()
                positionsDirty = true
            }
        }
    }

    /**
     * Период фонового учёта позиции и записи её на диск при скрытом интерфейсе:
     * «Максимальное энергосбережение» — 90 с, «Качество» — 30 с, остальные профили — 60 с.
     */
    private fun hiddenTickMs(p: PowerSettings): Long = when {
        p.profile == "max" -> 90_000L
        p.background >= 2 -> 30_000L
        else -> 60_000L
    }

    private fun startLoops() {
        // Единственный фоновый цикл: позиция для интерфейса + запоминание позиции трека.
        //  • интерфейс скрыт и ничего не играет — цикл вообще спит (до нажатия play или открытия приложения);
        //  • интерфейс скрыт, звук идёт — просыпается раз в 30–90 секунд (90 — в максимальной экономии): позиция нужна только для запоминания,
        //    а точное значение всё равно записывается при паузе, смене трека и сворачивании;
        //  • экран включён — цикл обновляет цифры и ползунок (раз в секунду в «Экономии»), на паузе — раз в 3 секунды.
        // Запись позиций на диск — отдельный маленький файл, с тем же периодом (60 с; 90 — в максимальной экономии; 30 — в «Качестве»).
        scope.launch {
            var lastFlush = SystemClock.elapsedRealtime()
            while (true) {
                if (!uiVisible && !isPlaying) {
                    loopWake.receive()
                    continue
                }
                val power = effectivePower()
                val interval = when {
                    !uiVisible -> hiddenTickMs(power)
                    !isPlaying -> 3_000L
                    else -> when (power.cpu) {
                        0 -> 1_000L
                        2 -> 250L
                        else -> 500L
                    }
                }
                // ждём интервал, но просыпаемся сразу, если приложение открыли или началось воспроизведение
                withTimeoutOrNull(interval) { loopWake.receive() }
                capturePosition()
                syncPowerRuntime()
                val now = SystemClock.elapsedRealtime()
                if (now - lastFlush >= hiddenTickMs(power) - 500L) {
                    lastFlush = now
                    flushPositions()
                }
            }
        }
    }

    // ------------------------------------------------------------ повтор A–B без опроса позиции


    /**
     * Повтор A–B: вместо опроса позиции (раньше — 16 раз в секунду) просим сам плеер прислать сообщение,
     * когда воспроизведение дойдёт до точки B (PlayerMessage). Плеер просыпается ровно в нужный момент,
     * в остальное время ни приложение, ни процессор ничего не делают. Заодно граница B срабатывает точнее.
     */
    private fun syncAbMessage() {
        val exo = PlayerBridge.rawPlayer as? androidx.media3.exoplayer.ExoPlayer
        val cur = abState
        val a = cur.aMs
        val b = cur.bMs
        val key = if (exo != null && cur.active && a != null && b != null) "${System.identityHashCode(exo)}:$a:$b" else null
        if (key == abKey) return
        abMessage?.cancel()
        abMessage = null
        abKey = key
        if (key == null || exo == null || b == null) return
        abMessage = exo.createMessage { _, _ ->
            val s = abState
            val a2 = s.aMs
            if (s.active && a2 != null) seekPlayer(a2)
        }
            .setLooper(Looper.getMainLooper())
            .setPosition(exo.currentMediaItemIndex, b)
            .setDeleteAfterDelivery(false)
            .also { it.send() }
        // точку B поставили позади текущей позиции — «дойти» до неё уже нельзя, переходим к A сразу
        if (exo.isPlaying && exo.currentPosition >= b && a != null) seekPlayer(a)
    }

    // ============================================================ воспроизведение

    private fun mediaItemOf(t: Track, uri: String = t.uri): MediaItem {
        // Маленькая обложка (та же, что на главном экране) — для компактного уведомления
        // воспроизведения: не увеличивает его высоту и не меняет цвет уведомления.
        val art = if (coverInNotification(data.coverMode)) Importer.artworkContentUri(app, t.id, t.hasArt) else null
        val metaBuilder = MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist)
        if (art != null) metaBuilder.setArtworkUri(art)
        return MediaItem.Builder()
            .setMediaId(t.id)
            .setUri(Uri.parse(uri))
            .setMediaMetadata(metaBuilder.build())
            .build()
    }

    fun startPosFor(t: Track): Long? =
        if (currentPlaylistId == BOOKMARKS) data.bookmarks[t.id] else null

    private class Resolved(val url: String, val at: Long)

    /** Ссылки на .m3u-плейлисты станций, уже превращённые в адрес самого потока (кэш на 10 минут). */

    private fun cachedStream(url: String): String? {
        val r = resolvedStreams[url] ?: return null
        if (System.currentTimeMillis() - r.at > RADIO_RESOLVE_TTL_MS) {
            resolvedStreams.remove(url)
            return null
        }
        return r.url
    }

    /**
     * Перемотка напрямую в ExoPlayer: из команд для уведомления перемотка убрана, поэтому
     * через MediaController приложение само перематывать не смогло бы.
     */
    private fun seekPlayer(ms: Long) {
        // пользователь сам выбрал позицию — откат после паузы не нужен
        clearPauseMarker()
        val p = PlayerBridge.rawPlayer
        if (p != null) p.seekTo(ms) else controller?.seekTo(ms)
    }

    // ------------------------------------------------------------ откат после паузы, самовосстановление плеера

    private fun markPaused(t: Track) {
        if (t.isRadio) return
        pauseWallMs = System.currentTimeMillis()
        pauseTrackId = t.id
        pausePrefs.edit().putLong("at", pauseWallMs).putString("track", t.id).apply()
    }

    private fun clearPauseMarker() {
        if (pauseWallMs == 0L && pauseTrackId == null) return
        pauseWallMs = 0L
        pauseTrackId = null
        pausePrefs.edit().clear().apply()
    }

    /** Нужно ли «откатить» воспроизведение: включено в настройках, это тот же трек, пауза была дольше 5 минут. */
    private fun rewindDue(t: Track): Boolean =
        data.rewindAfterPause && !t.isRadio && pauseTrackId == t.id && pauseWallMs > 0L &&
            System.currentTimeMillis() - pauseWallMs > REWIND_PAUSE_MIN_MS

    /** Возобновление «из паузы» (кнопка, уведомление, гарнитура): после долгой паузы отматываем на 20 секунд назад. */
    private fun maybeRewindAfterPause(t: Track) {
        if (pauseWallMs == 0L) return
        val due = rewindDue(t)
        clearPauseMarker()
        if (!due) return
        val c = controller ?: return
        val pos = c.currentPosition
        if (pos <= 0L) return
        val np = maxOf(0L, pos - REWIND_AFTER_PAUSE_MS)
        seekPlayer(np)
        positionMs = np
    }

    private fun armFileWatchdog(t: Track) {
        fileWatchdog?.cancel()
        fileWatchdog = null
        if (t.isRadio) return
        val id = t.id
        val timeout = if (t.uri.startsWith("http", ignoreCase = true)) 20_000L else FILE_START_TIMEOUT_MS
        fileWatchdog = scope.launch {
            delay(timeout)
            if (currentTrackId != id) return@launch
            fileWatchdog = null
            val c = controller
            val healthy = c != null && c.isConnected &&
                (!c.playWhenReady || c.playbackState == Player.STATE_READY || c.playbackState == Player.STATE_ENDED)
            if (!healthy && (isLoading || (c != null && c.playWhenReady) || c == null)) recoverPlayback(t)
        }
    }

    /**
     * Плеер не запускается (после долгой паузы/ночи): пересоздаём сам ExoPlayer — а если сервис был остановлен,
     * подключаемся к нему заново — и загружаем тот же трек с прежней позиции. Не более двух попыток подряд.
     */
    private fun recoverPlayback(t: Track) {
        if (recoveryCount >= 2) {
            loadRequested = false
            resolvingStream = false
            isLoading = false
            isPlaying = false
            try { controller?.pause() } catch (e: Exception) { }
            toast("Не удалось запустить воспроизведение — нажмите play ещё раз")
            return
        }
        recoveryCount += 1
        val resume = if (t.isRadio) 0L else positionMs.coerceAtLeast(0L)
        loadRequested = true
        isLoading = true
        hardResetPlayer { if (currentTrackId == t.id) loadAndPlay(t, resume, retry = true) }
    }

    private fun hardResetPlayer(then: () -> Unit) {
        val reset = PlayerBridge.resetPlayer
        val c = controller
        var done = false
        if (reset != null && c != null && c.isConnected) {
            try {
                reset()
                done = true
            } catch (e: Exception) {
            }
        }
        if (done) {
            // даём сессии время подхватить новый плеер
            scope.launch {
                delay(250)
                then()
            }
        } else {
            pendingAction = then
            reconnectController()
        }
    }

    // ------------------------------------------------------------ радио: таймаут соединения

    private fun armRadioWatchdog(t: Track) {
        radioWatchdog?.cancel()
        radioWatchdog = null
        if (!t.isRadio) return
        val id = t.id
        radioWatchdog = scope.launch {
            delay(RADIO_CONNECT_TIMEOUT_MS)
            if (currentTrackId != id) return@launch
            val c = controller
            val connected = c != null && (c.playbackState == Player.STATE_READY || c.isPlaying) && !resolvingStream
            if (!connected) onRadioTimeout(t)
        }
    }

    /** Станция не отвечает слишком долго — переключаемся на следующую (с уведомлением). */
    private fun onRadioTimeout(t: Track) {
        radioWatchdog = null
        resolvingStream = false
        // сначала — полный перезапуск плеера и повторное подключение к той же станции: «зависнуть» мог сам плеер, а не станция
        if (recoveryCount < 1) {
            recoveryCount += 1
            loadRequested = true
            isLoading = true
            hardResetPlayer { if (currentTrackId == t.id) loadAndPlay(t, 0L, retry = true) }
            return
        }
        loadRequested = false
        val list = activeList()
        if (list.size > 1 && timeoutChain < list.size - 1) {
            timeoutChain += 1
            toast("Станция «${t.title}» не отвечает — включаю следующую")
            playNext(false)
        } else {
            timeoutChain = 0
            controller?.stop()
            isPlaying = false
            isLoading = false
            toast(if (list.size > 1) "Ни одна станция не отвечает" else "Станция «${t.title}» не отвечает")
        }
    }

    /** Обложка, найденная плеером внутри файла: сохраняем как обложку трека, чтобы она была и на главном экране. */

    private fun onEmbeddedArt(mediaId: String?, bytes: ByteArray) {
        val id = mediaId ?: return
        val t = trackById(id) ?: return
        if (t.isRadio || t.hasArt || !artTried.add(id)) return
        scope.launch {
            val ok = withContext(Dispatchers.IO) { Importer.saveCoverFromBytes(app, bytes, id) }
            if (ok) update { d -> d.copy(tracks = d.tracks.map { if (it.id == id) it.copy(hasArt = true) else it }) }
        }
    }

    /** Трек, для которого автоматически ставится точка A: не радио, не поток и длиннее 15 минут. */
    private fun autoAEligible(t: Track?): Boolean =
        t != null && !t.isRadio && !t.uri.startsWith("http", ignoreCase = true) && t.durationMs > AUTO_A_MIN_MS

    /** Точка A на позиции запуска — если пользователь не ставил её вручную. */
    private fun autoSetA(t: Track, pos: Long) {
        if (!autoAEligible(t)) return
        val b = ab.bMs
        if ((ab.aMs == null || ab.aAuto) && (b == null || pos < b)) ab = ab.copy(aMs = pos, aAuto = true)
    }

    private fun startMedia(t: Track, uri: String, start: Long) {
        val c = controller ?: return
        c.setMediaItem(mediaItemOf(t, uri), start)
        c.prepare()
        c.play()
        applyPlaybackParams()
    }

    /** Адрес самого потока для станции, заданной ссылкой на .m3u (ExoPlayer сам такие списки не читает). */
    private suspend fun resolveStream(url: String): String {
        cachedStream(url)?.let { return it }
        val real = withContext(Dispatchers.IO) {
            try {
                val text = RadioImport.fetchText(url)
                if (RadioImport.isHlsManifest(text)) url
                else RadioImport.parseSimple(text, "").firstOrNull()?.url ?: url
            } catch (e: Exception) {
                null
            }
        }
        if (real == null) return url
        resolvedStreams[url] = Resolved(real, System.currentTimeMillis())
        return real
    }

    /**
     * У радио нет «продолжить»: после паузы, остановки или ошибки соединение со станцией мертво
     * (сервер давно закрыл его), а в буфере — устаревший эфир. Тогда запуск = новое подключение.
     */
    private fun radioNeedsReconnect(c: Player, t: Track): Boolean {
        if (resolvingStream) return false
        if (c.mediaItemCount == 0 || c.currentMediaItem?.mediaId != t.id) return true
        val st = c.playbackState
        if (st == Player.STATE_IDLE || st == Player.STATE_ENDED) return true
        return pausedAtMs > 0 && System.currentTimeMillis() - pausedAtMs > RADIO_STALE_PAUSE_MS
    }

    fun loadAndPlay(t: Track, startAt: Long?, retry: Boolean = false) {
        val act: () -> Unit = {
            val c = controller
            if (c != null) {
                if (!retry) { radioRetryCount = 0; recoveryCount = 0 }
                if (t.isRadio && !retry && isOnCellular()) {
                    hintOnce("radioCellular", "Радио по мобильной сети (4G) расходует заряд заметно быстрее, чем по Wi-Fi")
                }
                if (currentTrackId != t.id) {
                    capturePosition()
                    flushPositions()
                    ab = AbState()
                }
                // у радио позиции нет: прежняя «позиция» огромна, и станция не запускалась (плеер «перематывал» эфир)
                var start = if (t.isRadio) 0L
                else startAt ?: (if (data.restorePosition) positions[t.id] else null) ?: 0L
                if (t.durationMs > 0 && start >= t.durationMs - 500) start = 0L
                // «Откат 20с после паузы» при запуске трека «с запомненной позиции» (например, после перезапуска приложения)
                if (startAt == null && start > 0L && rewindDue(t)) start = maxOf(0L, start - REWIND_AFTER_PAUSE_MS)
                clearPauseMarker()
                currentTrackId = t.id
                streamTitle = null
                loadRequested = true
                isLoading = true
                lastStartMs = start
                positionMs = start
                durationMs = t.durationMs
                autoSetA(t, start)
                val cached = cachedStream(t.uri)
                pausedAtMs = 0
                armRadioWatchdog(t)
                if (t.isRadio && cached == null && RadioImport.looksLikePlaylistUrl(t.uri)) {
                    c.stop()
                    c.clearMediaItems()
                    resolvingStream = true
                    isLoading = true
                    scope.launch {
                        val real = resolveStream(t.uri)
                        resolvingStream = false
                        if (currentTrackId == t.id) startMedia(t, real, start) else syncPlaying()
                    }
                } else {
                    startMedia(t, cached ?: t.uri, start)
                }
                armFileWatchdog(t)
                gcOrphans()
                // обложка есть в файле, но при добавлении не сохранилась — достаём сейчас (видна и в плеере, и в уведомлении)
                if (!t.isRadio && !t.hasArt && artTried.add(t.id)) {
                    val tid = t.id
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { Importer.ensureArt(app, t.uri, tid) }
                        if (ok) update { d -> d.copy(tracks = d.tracks.map { if (it.id == tid) it.copy(hasArt = true) else it }) }
                    }
                }
            }
        }
        if (!controllerAlive()) {
            // контроллера ещё нет или он «мёртв» (сервис остановлен): подключаемся и выполняем запуск после подключения
            pendingAction = act
            if (controller != null) reconnectController() else connectController()
            toast("Плеер запускается…")
        } else {
            act()
        }
    }

    fun pausePlayback() {
        controller?.pause()
    }

    private fun togglePlay() {
        if (currentTrackId == null) {
            val list = playlistTracks(currentPlaylistId)
            if (list.isNotEmpty()) {
                loadAndPlay(list[0], startPosFor(list[0]))
            } else {
                // в активном списке (по умолчанию «Default») нет треков — запускаем первую доступную радиостанцию
                val station = playlistTracks(RADIO).firstOrNull()
                if (station != null) {
                    setActivePlaylist(RADIO)
                    loadAndPlay(station, null)
                } else toast("В списке нет треков")
            }
            return
        }
        if (!controllerAlive()) {
            // сервис воспроизведения был остановлен (долгая пауза, ночь): подключаемся заново и повторяем нажатие
            pendingAction = { togglePlay() }
            reconnectController()
            return
        }
        val c = controller ?: return
        if (c.playWhenReady && c.playbackState != Player.STATE_ENDED && c.playbackState != Player.STATE_IDLE) {
            c.pause()
        } else {
            val t = trackById(currentTrackId)
            if (t != null && t.isRadio && radioNeedsReconnect(c, t)) {
                // утром после таймера сна / долгой паузы / ошибки: просто prepare()+play() не помогают — подключаемся заново
                loadAndPlay(t, 0L)
                return
            }
            if (t != null && !t.isRadio && (c.mediaItemCount == 0 || c.currentMediaItem?.mediaId != t.id)) {
                // плеер пуст (сервис был перезапущен): простой play() ничего не запустит — загружаем трек заново с запомненной позиции
                loadAndPlay(t, null)
                return
            }
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) seekPlayer(0)
            c.play()
            if (t != null && !t.isRadio) armFileWatchdog(t)
        }
    }

    /**
     * Кнопка play/pause. Пока пользователь не задал точку A вручную («Установить A» / тап по
     * окошку A), она переставляется на текущую позицию при каждом запуске воспроизведения —
     * в том числе при возобновлении из паузы (а при запуске трека/плейлиста — см. loadAndPlay).
     * Только для треков длиннее 15 минут; для радио и потоковой музыки автоточка A не ставится.
     */
    fun onPlayPause() {
        recoveryCount = 0
        val willPlay = currentTrackId == null || !isPlaying
        togglePlay()
        val t = trackById(currentTrackId)
        if (willPlay && autoAEligible(t) && (ab.aMs == null || ab.aAuto)) {
            val pos = controller?.currentPosition ?: lastStartMs
            val b = ab.bMs
            if (b == null || pos < b) ab = ab.copy(aMs = pos, aAuto = true)
        }
    }

    fun seekTo(ms: Long) {
        seekPlayer(ms)
        positionMs = ms
    }

    fun rewind() {
        if (currentTrackId == null) { toast("Сначала выберите трек"); return }
        val c = controller ?: return
        val np = maxOf(0L, c.currentPosition - data.seekStepSec * 1000L)
        seekPlayer(np)
        positionMs = np
    }

    /**
     * Один шаг перемотки при удержании кнопки (на шаг из настроек). false — достигнут край трека
     * (или перематывать нечего), повтор нужно прекратить. До самого конца не доматываем, чтобы удержание
     * не перескакивало на следующий трек.
     */
    fun holdSeek(forward: Boolean): Boolean {
        val t = trackById(currentTrackId) ?: return false
        if (t.isRadio) return false
        val c = controller ?: return false
        val raw = PlayerBridge.rawPlayer
        val cur = raw?.currentPosition ?: c.currentPosition
        val d = raw?.duration ?: c.duration
        val hasDur = d != C.TIME_UNSET && d > 0
        val delta = data.seekStepSec * 1000L
        val target = if (forward) cur + delta else cur - delta
        val clamped = when {
            target <= 0L -> 0L
            hasDur && target >= d - 1000L -> maxOf(0L, d - 1000L)
            else -> target
        }
        seekPlayer(clamped)
        positionMs = clamped
        return if (forward) !(hasDur && target >= d - 1000L) else target > 0L
    }

    fun forward() {
        if (currentTrackId == null) { toast("Сначала выберите трек"); return }
        val c = controller ?: return
        val d = c.duration
        val np = c.currentPosition + data.seekStepSec * 1000L
        val target = if (d != C.TIME_UNSET && d > 0) minOf(d, np) else np
        seekPlayer(target)
        positionMs = target
    }

    fun toggleShuffle() {
        shuffle = !shuffle
        if (shuffle) buildShuffle(activeList(), currentTrackId)
        toast(if (shuffle) "Перемешивание включено" else "Перемешивание выключено")
    }

    fun toggleRepeat() {
        repeatMode = (repeatMode + 1) % 3
        toast(listOf("Повтор выключен", "Повтор плейлиста", "Повтор трека")[repeatMode])
    }

    // ------------------------------------------------------------ очередь

    private fun buildShuffle(list: List<Track>, firstId: String? = null) {
        val ids = list.map { it.id }.filter { it != firstId }.shuffled()
        shuffleOrder = if (firstId != null && list.any { it.id == firstId }) listOf(firstId) + ids else ids
    }

    private fun ensureShuffle(list: List<Track>, firstId: String?) {
        val ok = shuffleOrder.size == list.size && list.all { shuffleOrder.contains(it.id) }
        if (!ok) buildShuffle(list, firstId)
    }

    private class Step(val id: String?, val wrapped: Boolean)

    private fun stepFrom(list: List<Track>, dir: Int): Step {
        val cur = currentTrackId
        if (shuffle) ensureShuffle(list, cur)
        val order = if (shuffle) shuffleOrder else list.map { it.id }
        if (order.isEmpty()) return Step(null, false)
        val pos = order.indexOf(cur ?: "")
        if (pos < 0) return Step(if (dir > 0) order.first() else order.last(), false)
        var n = pos + dir
        var wrapped = false
        if (n < 0) {
            n = order.size - 1
            wrapped = true
        } else if (n >= order.size) {
            wrapped = true
            if (shuffle) {
                buildShuffle(list)
                if (shuffleOrder.first() == cur && list.size > 1) {
                    shuffleOrder = shuffleOrder.drop(1) + shuffleOrder.first()
                }
                return Step(shuffleOrder.first(), true)
            }
            n = 0
        }
        return Step(order[n], wrapped)
    }

    fun playNext(auto: Boolean) {
        val list = activeList()
        if (list.isEmpty()) return
        val cur = trackById(currentTrackId)
        if (auto && repeatMode == 2 && cur != null) {
            seekPlayer(0)
            controller?.play()
            return
        }
        val st = stepFrom(list, 1)
        if (auto && st.wrapped && (repeatMode == 0 || sleep.mode == "endPlaylist")) {
            handlePlaylistEnded()
            return
        }
        val t = trackById(st.id) ?: return
        loadAndPlay(t, startPosFor(t))
    }

    fun playPrev() {
        val list = activeList()
        if (list.isEmpty()) return
        val c = controller
        if (currentTrackId != null && c != null && c.currentPosition > 4000) {
            seekPlayer(0)
            positionMs = 0
            return
        }
        val st = stepFrom(list, -1)
        val t = trackById(st.id) ?: return
        loadAndPlay(t, startPosFor(t))
    }

    private fun handlePlaylistEnded() {
        controller?.pause()
        seekPlayer(0)
        currentTrackId?.let { if (positions.remove(it) != null) positionsDirty = true; positionDays.remove(it) }
        flushPositions()
        positionMs = 0
        if (sleep.mode == "endPlaylist") {
            stopSleep(false)
            toast("Таймер сна: воспроизведение остановлено")
        }
    }

    private fun onTrackEnded() {
        val liveTrack = trackById(currentTrackId)
        if (liveTrack != null && liveTrack.isRadio) {
            // У живого эфира «конца» нет: поток оборвала станция или сеть. Переподключаемся к ТОЙ ЖЕ станции,
            // а не листаем на следующую (раньше обрыв читался как «трек закончился» и включалась следующая станция).
            if (radioRetryCount < RADIO_MAX_RETRIES) {
                radioRetryCount += 1
                val n = radioRetryCount
                resolvedStreams.remove(liveTrack.uri)
                radioWatchdog?.cancel()
                radioWatchdog = null
                loadRequested = true
                isLoading = true
                scope.launch {
                    delay(if (n == 1) 400L else 2000L * n)
                    val c2 = controller
                    if (currentTrackId == liveTrack.id && c2 != null && c2.playWhenReady) {
                        loadAndPlay(liveTrack, 0L, retry = true)
                    } else {
                        loadRequested = false
                        syncPlaying()
                    }
                }
            } else {
                loadRequested = false
                resolvingStream = false
                isLoading = false
                try { controller?.pause() } catch (e: Exception) { }
                toast("Станция «${liveTrack.title}» оборвала поток")
            }
            return
        }
        currentTrackId?.let { if (positions.remove(it) != null) positionsDirty = true; positionDays.remove(it) }
        flushPositions()
        if (sleep.mode == "endTrack") {
            stopSleep(false)
            pausePlayback()
            toast("Таймер сна: воспроизведение остановлено")
            return
        }
        playNext(true)
    }

    fun playPlaylist(plid: String, shuffleFirst: Boolean) {
        val list = playlistTracks(plid)
        if (list.isEmpty()) { toast("В списке нет треков"); return }
        setActivePlaylist(plid)
        val start: Track
        if (shuffleFirst) {
            shuffle = true
            buildShuffle(list)
            start = trackById(shuffleOrder[0]) ?: list[0]
        } else {
            start = list[0]
            if (shuffle) buildShuffle(list, start.id)
        }
        loadAndPlay(start, startPosFor(start))
        closeAllSheets()
        navigate(1)
    }

    fun playTrackFromView(v: String, id: String) {
        val t = trackById(id)
        if (t == null) {
            // трек мог исчезнуть из библиотеки (например, убран как «осиротевший») — не молчим, а сообщаем
            toast("Трек не найден")
            return
        }
        setActivePlaylist(viewPlid(v))
        if (shuffle) buildShuffle(activeList(), id)
        loadAndPlay(t, startPosFor(t))
        closeAllSheets()
        navigate(1)
    }

    // ------------------------------------------------------------ звук

    private fun applyPlaybackParams() {
        // скорость и высота тона — независимые параметры (Sonic в ExoPlayer)
        // Живой эфир нельзя ускорять: плеер выбирает буфер быстрее, чем станция его присылает, —
        // звук рвётся, на кнопке бесконечно крутится загрузка, а сторож переключает станцию. Для радио скорость всегда 1×.
        val onRadio = trackById(currentTrackId)?.isRadio == true
        val speed = if (onRadio) 1f else SoundEngine.playbackSpeed(data.sound)
        val pitch = SoundEngine.playbackPitch(data.sound)
        controller?.setPlaybackParameters(PlaybackParameters(speed, pitch))
    }

    private fun updateSound(f: (SoundState) -> SoundState) {
        update { it.copy(sound = f(it.sound)) }
        SoundEngine.apply(data.sound)
        applyPlaybackParams()
    }

    // ------------------------------------------------------------ устройство вывода и эквалайзер

    private class OutputInfo(val key: String, val name: String)

    private var outputCallback: AudioDeviceCallback? = null

    /** Куда сейчас идёт звук: динамик, проводные наушники/USB или конкретное Bluetooth-устройство. */
    private fun detectOutput(): OutputInfo {
        return try {
            val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val devs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            // типы AudioDeviceInfo: 8 A2DP, 23 слуховой аппарат, 26/27/30 BLE Audio
            val bt = devs.firstOrNull { it.type == 8 || it.type == 23 || it.type == 26 || it.type == 27 || it.type == 30 }
            // 3 гарнитура, 4 наушники, 22 USB-гарнитура, 11 USB-устройство, 5/6 линейный выход
            val wired = devs.firstOrNull { it.type == 3 || it.type == 4 || it.type == 22 || it.type == 11 || it.type == 5 || it.type == 6 }
            @Suppress("DEPRECATION")
            val useBt = when {
                bt != null && wired != null -> am.isBluetoothA2dpOn
                else -> bt != null
            }
            when {
                useBt && bt != null -> {
                    val id = if (android.os.Build.VERSION.SDK_INT >= 28 && bt.address.isNotBlank()) bt.address
                    else bt.productName?.toString().orEmpty().ifBlank { "bluetooth" }
                    OutputInfo(OUT_BT_PREFIX + id, bt.productName?.toString().orEmpty())
                }
                wired != null -> OutputInfo(OUT_WIRED, "")
                else -> OutputInfo(OUT_SPEAKER, "")
            }
        } catch (e: Exception) {
            OutputInfo(OUT_SPEAKER, "")
        }
    }

    /**
     * Подстраивает эквалайзер под текущее устройство вывода: настройки прежнего устройства
     * откладываются в его профиль, а для нового берётся его сохранённый профиль. Для устройства,
     * которого ещё не было, остаются текущие настройки — дальше они меняются независимо.
     */
    fun syncOutputDevice() {
        val out = detectOutput()
        val cur = data.sound
        if (out.key == cur.eqDevice) {
            if (out.name != cur.eqDeviceName && out.key.startsWith(OUT_BT_PREFIX)) updateSound { it.copy(eqDeviceName = out.name) }
            return
        }
        updateSound { s ->
            val saved = s.eqProfiles + (s.eqDevice to EqProfile(s.eqEnabled, s.eqPreset, s.eqBands))
            val next = saved[out.key] ?: EqProfile(s.eqEnabled, s.eqPreset, s.eqBands)
            // профиль мог ссылаться на удалённый пресет
            val preset = if (next.preset == "manual" || s.presets.any { it.id == next.preset }) next.preset else "manual"
            s.copy(
                eqProfiles = saved + (out.key to next),
                eqDevice = out.key, eqDeviceName = out.name,
                eqEnabled = next.enabled, eqPreset = preset, eqBands = next.bands
            )
        }
    }

    /** Подпись профиля эквалайзера в шторке звука. */
    fun eqDeviceLabel(): String {
        val s = data.sound
        return when {
            s.eqDevice == OUT_SPEAKER -> "Динамик"
            s.eqDevice == OUT_WIRED -> "Наушники"
            else -> s.eqDeviceName.ifBlank { "Bluetooth" }
        }
    }

    private fun startOutputObserver() {
        try {
            val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val cb = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = outputChanged()
                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = outputChanged()
            }
            outputCallback = cb
            am.registerAudioDeviceCallback(cb, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
        }
        syncOutputDevice()
    }

    private fun outputChanged() {
        syncOutputDevice()
        // Bluetooth докладывает о маршруте с задержкой — проверяем ещё раз, когда он устоялся
        scope.launch {
            delay(1200)
            syncOutputDevice()
        }
    }

    /** Включён ли хотя бы один звуковой эффект (эквалайзер, звук, точная громкость). */
    fun soundEffectsOn(): Boolean {
        val s = data.sound
        return s.eqEnabled || s.soundEnabled || s.volumeFineEnabled
    }

    /**
     * Долгое нажатие на значок звука: если эффекты включены — выключает все (запомнив, какие были включены),
     * если выключены — возвращает прежнее состояние.
     */
    fun toggleSoundEffectsQuick() {
        val s = data.sound
        if (soundEffectsOn()) {
            val mask = (if (s.eqEnabled) 1 else 0) or (if (s.soundEnabled) 2 else 0) or (if (s.volumeFineEnabled) 4 else 0)
            updateSound { it.copy(eqEnabled = false, soundEnabled = false, volumeFineEnabled = false, fxBackup = mask) }
            toast("Звуковые эффекты выключены")
        } else {
            val m = s.fxBackup
            if (m == 0) {
                toast("Нет сохранённого состояния эффектов")
                return
            }
            updateSound { it.copy(eqEnabled = (m and 1) != 0, soundEnabled = (m and 2) != 0, volumeFineEnabled = (m and 4) != 0) }
            toast("Звуковые эффекты восстановлены")
        }
    }

    fun setEqEnabled(v: Boolean) = updateSound { it.copy(eqEnabled = v) }
    fun setSoundEnabled(v: Boolean) = updateSound { it.copy(soundEnabled = v) }
    fun setFineEnabled(v: Boolean) = updateSound { it.copy(volumeFineEnabled = v) }

    fun setEqBand(i: Int, v: Int) = updateSound { s ->
        val b = s.eqBands.toMutableList()
        b[i] = v
        s.copy(eqBands = b, eqPreset = "manual")
    }

    fun setPreamp(v: Int) = updateSound { it.copy(preamp = v.coerceIn(-12, 12)) }
    fun setSpeed(v: Float) = updateSound { it.copy(speed = (Math.round(v * 100f) / 100f).coerceIn(0.5f, 3f)) }
    fun setBalance(v: Int) = updateSound { it.copy(balance = v.coerceIn(-100, 100)) }
    fun setTone(v: Float) = updateSound { it.copy(tone = (Math.round(v * 100f) / 100f).coerceIn(0.5f, 1.5f)) }
    fun setTrim(v: Int) = updateSound { it.copy(volumeTrim = v.coerceIn(-100, 100)) }

    fun applyEqPreset(id: String) {
        val p = data.sound.presets.firstOrNull { it.id == id } ?: return
        updateSound { s ->
            s.copy(eqPreset = id, eqBands = p.bands ?: s.eqBands)
        }
    }

    fun addEqPreset(name: String) {
        val id = "eq" + System.currentTimeMillis()
        updateSound { s ->
            s.copy(presets = s.presets + EqPreset(id, name, s.eqBands), presetOrder = s.presetOrder + id, eqPreset = id)
        }
        toast("Эквалайзер «$name» добавлен")
    }

    fun renameEq(id: String, name: String) {
        updateSound { s -> s.copy(presets = s.presets.map { if (it.id == id) it.copy(name = name) else it }) }
        toast("Переименовано")
    }

    /** Пресеты эквалайзера в порядке, который можно менять перетаскиванием чипов влево-вправо. */
    fun orderedPresets(): List<EqPreset> {
        val s = data.sound
        val byId = s.presets.associateBy { it.id }
        val ordered = s.presetOrder.mapNotNull { byId[it] }
        val missing = s.presets.filter { !s.presetOrder.contains(it.id) }
        return ordered + missing
    }

    fun reorderEqPresets(ids: List<String>) = updateSound { it.copy(presetOrder = ids) }

    fun deleteEq(id: String) {
        updateSound { s ->
            s.copy(
                presets = s.presets.filter { it.id != id },
                presetOrder = s.presetOrder.filter { it != id },
                eqPreset = if (s.eqPreset == id) "manual" else s.eqPreset
            )
        }
        toast("Эквалайзер удалён")
    }

    // ------------------------------------------------------------ настройки

    fun setRestorePosition(v: Boolean) = update { it.copy(restorePosition = v) }

    fun setRewindAfterPause(v: Boolean) {
        update { it.copy(rewindAfterPause = v) }
        if (v) hintOnce("rewindPause", "Если пауза длилась больше 5 минут, воспроизведение продолжится на 20 секунд раньше")
        else clearPauseMarker()
    }

    fun setSeekStep(n: Int) {
        val v = n.coerceIn(1, 300)
        update { it.copy(seekStepSec = v) }
        toast("Шаг перемотки: $v с")
    }

    fun setTheme(theme: String) = update { it.copy(theme = theme) }

    fun setCoverHarmonize(v: Boolean) = update { it.copy(coverHarmonize = v) }

    /** Ползунок «Сила эффектов» в настройках: 0 — выключены … 1 — максимум. */
    fun setFxLevel(v: Float) = update { it.copy(fxLevel = v.coerceIn(0f, 1f)) }

    // шторка «Эффекты»: отдельные ползунки (0 — откл … 1 — макс)
    fun setFxSound(v: Float) = update { it.copy(fxSound = v.coerceIn(0f, 1f)) }
    fun setFxHaptic(v: Float) = update { it.copy(fxHaptic = v.coerceIn(0f, 1f)) }
    fun setFxBackdropBlur(v: Float) = update { it.copy(fxBackdropBlur = v.coerceIn(0f, 1f)) }
    fun setFxBackdropAlpha(v: Float) = update { it.copy(fxBackdropAlpha = v.coerceIn(0f, 1f)) }

    fun setCoverMode(mode: String) {
        update { it.copy(coverMode = mode) }
        PlayerBridge.coverInNotification = coverInNotification(mode)
    }

    fun coverModeLabel(mode: String): String = when (mode) {
        COVER_NOTIFICATION -> "В уведомлении"
        COVER_PLAYER -> "В плеере"
        COVER_ALL -> "Везде"
        else -> "Не показывать"
    }

    // ------------------------------------------------------------ энергосбережение

    private fun presetFor(profile: String): PowerSettings? = when (profile) {
        "max" -> PowerSettings(profile = "max", cpu = 0, screen = 0, network = 0, background = 0, offload = true)
        "save" -> PowerSettings(profile = "save", cpu = 0, screen = 1, network = 0, background = 1)
        "balanced" -> PowerSettings(profile = "balanced", cpu = 1, screen = 1, network = 1, background = 1)
        "quality" -> PowerSettings(profile = "quality", cpu = 2, screen = 2, network = 2, background = 2)
        else -> null // "manual" — значения не переопределяем, оставляем как есть
    }

    /**
     * Действующие настройки энергосбережения — с учётом автоматики: если включено переключение
     * по разряду батареи или по системному режиму энергосбережения и условие выполняется, то
     * независимо от выбранного профиля действует профиль «Энергосбережение».
     */
    fun effectivePower(): PowerSettings {
        val p = data.power
        val triggered = (p.autoBattery && batteryLevelPct <= p.autoBatteryPercent) ||
            (p.autoSystemSaver && systemPowerSaveOn)
        return if (triggered) (presetFor("save") ?: p) else p
    }

    /** true, если сейчас действует профиль экономии из-за автоматики (для подсказки в интерфейсе). */
    fun powerAutoActive(): Boolean {
        val p = data.power
        return (p.autoBattery && batteryLevelPct <= p.autoBatteryPercent) ||
            (p.autoSystemSaver && systemPowerSaveOn)
    }

    fun setPowerProfile(profile: String) {
        val preset = presetFor(profile)
        update { d ->
            val cur = d.power
            val base = preset ?: cur
            d.copy(power = base.copy(profile = profile, autoBattery = cur.autoBattery, autoBatteryPercent = cur.autoBatteryPercent, autoSystemSaver = cur.autoSystemSaver))
        }
        if (profile != "manual" && data.power.offload) announceOffload()
    }

    /**
     * Уровень буфера плеера: 0 — экономия, 1 — обычно, 2 — качество, 3 — буфер, ограниченный по размеру (13 МБ),
     * а не по времени (только в профиле «Максимальное энергосбережение»; в ручной настройке «Сеть и радио» остаются 3 уровня).
     */
    fun effectiveNetworkLevel(): Int {
        val p = effectivePower()
        return if (p.profile == "max") 3 else p.network
    }

    /** Аппаратное декодирование звука (offload) доступно на Android 10 и новее. */
    fun offloadSupported(): Boolean = android.os.Build.VERSION.SDK_INT >= 29

    fun setPowerOffload(v: Boolean) {
        if (v && !offloadSupported()) {
            toast("Аппаратное декодирование звука требует Android 10 или новее")
            return
        }
        update { d -> d.copy(power = d.power.copy(profile = "manual", offload = v)) }
        if (v) announceOffload() else toast("Аппаратное декодирование выключено")
    }

    /** Всплывающее сообщение при включении offload: что он делает и когда реально работает. */
    private fun announceOffload() {
        if (!offloadSupported()) return
        toast(
            if (soundEffectsOn()) "Аппаратное декодирование включено. Сейчас включены эффекты звука — offload подключится, когда вы их выключите"
            else "Аппаратное декодирование включено: звук обрабатывает DSP устройства, процессор спит. Эффекты звука и повтор A–B его отключают",
            6000L
        )
    }

    /** Нужен ли сейчас offload: включён в энергосбережении, поддерживается и ничего его не блокирует. */
    fun offloadWanted(): Boolean =
        effectivePower().offload && offloadSupported() && !soundEffectsOn() && !ab.active

    private var lastOffload: Boolean? = null
    private var rebuildJob: kotlinx.coroutines.Job? = null

    /**
     * Применяет изменения профиля к уже работающему плееру — без перезапуска приложения:
     * offload включается/выключается сразу, а при смене уровня «Сеть и радио» (размер буфера) плеер
     * пересоздаётся с переносом состояния. Вызывается при любом изменении настроек и состояния батареи.
     */
    private fun syncPowerRuntime() {
        // пока сервис воспроизведения не запущен (в т.ч. во время инициализации Hub) применять нечего
        if (PlayerBridge.setOffload == null) return
        val want = offloadWanted()
        if (lastOffload != want) {
            lastOffload = want
            PlayerBridge.setOffload?.invoke(want)
        }
        val level = effectiveNetworkLevel()
        if (PlayerBridge.rebuildPlayer != null && level != PlayerBridge.networkLevel) {
            // небольшая задержка: при быстром переборе профилей пересоздаём плеер один раз
            rebuildJob?.cancel()
            rebuildJob = scope.launch {
                delay(700)
                val lvl = effectiveNetworkLevel()
                if (lvl != PlayerBridge.networkLevel) PlayerBridge.rebuildPlayer?.invoke(lvl)
            }
        }
    }

    fun setPowerCpu(v: Int) = update { d -> d.copy(power = d.power.copy(profile = "manual", cpu = v.coerceIn(0, 2))) }
    fun setPowerScreen(v: Int) = update { d -> d.copy(power = d.power.copy(profile = "manual", screen = v.coerceIn(0, 2))) }
    fun setPowerNetwork(v: Int) = update { d -> d.copy(power = d.power.copy(profile = "manual", network = v.coerceIn(0, 2))) }
    fun setPowerBackground(v: Int) = update { d -> d.copy(power = d.power.copy(profile = "manual", background = v.coerceIn(0, 2))) }

    fun setPowerAutoBattery(v: Boolean) {
        update { d -> d.copy(power = d.power.copy(autoBattery = v)) }
        if (v) hintOnce("autoBattery", "Включать «Энергосбережение», когда заряд ниже порога")
    }
    fun setPowerAutoBatteryPercent(v: Int) = update { d -> d.copy(power = d.power.copy(autoBatteryPercent = v.coerceIn(5, 80))) }
    fun setPowerAutoSystemSaver(v: Boolean) {
        update { d -> d.copy(power = d.power.copy(autoSystemSaver = v)) }
        if (v) hintOnce("autoSystem", "Включать, когда в системе включена экономия заряда")
    }

    /** Следим за уровнем батареи и системным режимом энергосбережения — для автоматики профиля. */
    private var volumePauseAt = 0L
    private var volumeReceiver: BroadcastReceiver? = null

    /** Громкость понижена до нуля во время воспроизведения — пауза (повышение громкости воспроизведение само не запускает). */
    private fun onSystemVolume(volume: Int) {
        if (volume > 0) return
        val c = controller ?: return
        if (!c.playWhenReady) return
        val now = SystemClock.elapsedRealtime()
        if (now - volumePauseAt < 1500L) return   // событие приходит с двух сторон — реагируем один раз
        volumePauseAt = now
        c.pause()
        toast("Громкость на нуле — пауза")
    }

    /** Запасной путь к тому же событию: широковещание об изменении громкости потока музыки. */
    private fun startVolumeWatch() {
        try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1) != AudioManager.STREAM_MUSIC) return
                    val v = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1)
                    if (v == 0) onSystemVolume(0)
                }
            }
            volumeReceiver = receiver
            ContextCompat.registerReceiver(app, receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), ContextCompat.RECEIVER_EXPORTED)
        } catch (e: Exception) {
        }
    }

    private fun startPowerObservers() {
        try {
            val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
            systemPowerSaveOn = pm?.isPowerSaveMode == true
        } catch (e: Exception) {
        }
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        Intent.ACTION_BATTERY_CHANGED -> {
                            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                            if (level >= 0 && scale > 0) batteryLevelPct = (level * 100) / scale
                        }
                        PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                            val p = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
                            systemPowerSaveOn = p?.isPowerSaveMode == true
                        }
                    }
                }
            }
            powerReceiver = receiver
            ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (e: Exception) {
        }
    }

    fun cycleTheme(currentlyDark: Boolean) {
        setTheme(if (currentlyDark) "light" else "dark")
        if (topSheet() is Sheet.Menu) closeSheet()
    }

    // ------------------------------------------------------------ таймер сна

    fun sleepLabel(): String {
        val s = sleep
        if (!s.active) return "Таймер сна"
        if (s.mode == "minutes") return fmtDurLongSec(s.remaining.toLong())
        if (s.mode == "endTrack") return "До конца трека"
        return "До конца плейлиста"
    }

    fun sleepStatusText(): String {
        val s = sleep
        if (!s.active) return "Таймер выключен"
        if (s.mode == "minutes") return "Осталось: " + fmtDurLongSec(s.remaining.toLong())
        if (s.mode == "endTrack") return "До конца текущего трека"
        return "До конца плейлиста"
    }

    /** Число секунд до конца таймера, в течение которых громкость плавно снижается до нуля. */
    private val SLEEP_FADE_SECONDS = 60

    /**
     * Громкость на затухании в зависимости от доли прошедшего времени (0 — начало минуты, 1 — остановка).
     * Кривая как на эскизе: сначала быстрое падение, затем долгая «полка» — звук остаётся слабо слышным почти до самого
     * конца — и в последней пятой плавный спад до нуля.
     *   полка:  base(p) = 0.28 + 0.72·e^(−6p)   (в начале 100%, через четверть ≈ 43%, дальше ≈ 28–32%)
     *   спад:   end(p)  = 1 − smoothstep((p − 0.8) / 0.2)   (от p = 0.8 до 1 множитель уходит к нулю)
     */
    private fun sleepFadeVolume(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        val base = 0.28f + 0.72f * exp(-6f * p)
        val t = ((p - 0.8f) / 0.2f).coerceIn(0f, 1f)
        val end = 1f - t * t * (3f - 2f * t)
        return (base * end).coerceIn(0f, 1f)
    }

    /**
     * Таймер сна остановил воспроизведение длинного файла (не радио, дольше часа): ставим точку B на позиции остановки,
     * чтобы утром было видно, где остановились. Заданная вручную точка B затирается; включённый повтор A–B не трогается.
     */
    private fun autoSetBOnSleepStop() {
        val t = trackById(currentTrackId) ?: return
        if (t.isRadio) return
        val dur = maxOf(t.durationMs, durationMs)
        if (dur <= AUTO_B_MIN_MS) return
        if (ab.active) return
        val pos = controller?.currentPosition ?: positionMs
        if (pos <= 0L) return
        var a = ab.aMs
        var aAuto = ab.aAuto
        if (a != null && pos <= a) { a = null; aAuto = false }
        ab = ab.copy(aMs = a, aAuto = aAuto, bMs = pos, bAuto = true)
        normalizeAb()
    }

    /** Момент окончания таймера по SystemClock.elapsedRealtime(): не зависит от «тиков», поэтому их можно делать редкими. */
    private fun wakeSleep() { sleepWake.trySend(Unit) }

    fun startSleepMinutes(min: Int, preset: String?) {
        stopSleep(false)
        sleepEndAt = SystemClock.elapsedRealtime() + min * 60_000L
        sleepWasFading = false
        sleep = sleep.copy(mode = "minutes", preset = preset ?: "custom", remaining = min * 60, active = true)
        applySleepFade()
        sleepJob = scope.launch {
            // Раньше таймер тикал раз в секунду всю ночь. Теперь:
            //  • интерфейс скрыт — один сон до начала затухания (за минуту до конца), без единого пробуждения;
            //  • экран включён — раз в секунду, потому что на экране идёт обратный отсчёт с секундами;
            //  • последняя минута (плавное затухание громкости) — раз в 2 секунды при выключенном экране.
            while (true) {
                val leftMs = sleepEndAt - SystemClock.elapsedRealtime()
                if (leftMs <= 0L) {
                    stopSleep(false)
                    autoSetBOnSleepStop()
                    pausePlayback()
                    val cur = trackById(currentTrackId)
                    if (cur != null && cur.isRadio) {
                        // радиопоток на ночь не держим: закрываем соединение; утром кнопка запуска подключится к станции заново
                        pausedAtMs = 1L
                        controller?.stop()
                    }
                    toast("Таймер сна: воспроизведение остановлено")
                    break
                }
                val leftSec = (leftMs + 999L) / 1000L
                if (sleep.remaining.toLong() != leftSec) sleep = sleep.copy(remaining = leftSec.toInt())
                val inFade = leftSec <= SLEEP_FADE_SECONDS
                if (inFade || sleepWasFading) {
                    sleepWasFading = inFade
                    applySleepFade()
                }
                val fadeStartMs = leftMs - SLEEP_FADE_SECONDS * 1000L
                val waitMs = when {
                    uiVisible -> 1000L
                    inFade -> 2000L
                    else -> fadeStartMs
                }
                withTimeoutOrNull(minOf(waitMs, leftMs).coerceAtLeast(50L)) { sleepWake.receive() }
            }
        }
    }


    fun startSleepMode(mode: String) {
        stopSleep(false)
        sleep = sleep.copy(mode = mode, preset = mode, active = true)
    }

    fun extendSleep(min: Int) {
        if (sleep.mode == "minutes" && sleep.active) {
            sleepEndAt += min * 60_000L
            sleep = sleep.copy(remaining = sleep.remaining + min * 60, preset = null)
            applySleepFade()
            wakeSleep()
        } else {
            startSleepMinutes(min, "custom")
        }
    }

    fun stopSleep(feedback: Boolean = true) {
        sleepJob?.cancel()
        sleepJob = null
        sleep = SleepState(addMinutes = sleep.addMinutes, knockExtend = sleep.knockExtend)
        controller?.volume = 1f
        stopKnockWatch()
        if (feedback) toast("Таймер сна выключен")
    }

    fun setSleepAddMinutes(v: Int) {
        sleep = sleep.copy(addMinutes = v.coerceIn(5, 60))
    }

    fun setKnockSensitivity(level: Int) {
        update { it.copy(knockSensitivity = level.coerceIn(0, 100)) }
    }

    fun setSleepKnockExtend(v: Boolean) {
        sleep = sleep.copy(knockExtend = v)
        if (v) hintOnce("knock", "За минуту до остановки громкость плавно снижается — постучите по устройству дважды («тук-тук»), чтобы продлить таймер")
        if (!v) stopKnockWatch() else applySleepFade()
    }

    /**
     * За последнюю минуту перед остановкой громкость плавно снижается до нуля (плавное
     * «затухание»); всё это время слушаем акселерометр — постукивание по корпусу устройства
     * продлевает таймер на значение «Добавлять к таймеру» (та же настройка, что у ручной кнопки
     * продления), один удар — одно продление, с защитой от «дребезга» одного и того же удара.
     */
    private fun applySleepFade() {
        val s = sleep
        val fading = s.active && s.mode == "minutes" && s.remaining in 1..SLEEP_FADE_SECONDS
        if (fading) {
            val progress = 1f - (s.remaining.toFloat() / SLEEP_FADE_SECONDS).coerceIn(0f, 1f)
            controller?.volume = sleepFadeVolume(progress)
            if (s.knockExtend && isPlaying) startKnockWatch() else stopKnockWatch()
        } else {
            controller?.volume = 1f
            stopKnockWatch()
        }
    }

    private fun startKnockWatch() {
        if (knockListener != null) return
        val sm = (sensorManager ?: (app.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)) ?: return
        sensorManager = sm
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        kInit = false
        kCalm = 0f
        kAbove = false
        kFirstTs = 0L
        kValley = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                if (!kInit) {
                    kgx = x; kgy = y; kgz = z; kInit = true; kLastTs = event.timestamp
                    return
                }
                val dt = ((event.timestamp - kLastTs) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
                kLastTs = event.timestamp
                // фильтр высоких частот: медленная составляющая (гравитация, наклон, плавные движения) вычитается
                val a = exp(-dt / 0.30f)
                kgx = a * kgx + (1 - a) * x
                kgy = a * kgy + (1 - a) * y
                kgz = a * kgz + (1 - a) * z
                val lx = x - kgx
                val ly = y - kgy
                val lz = z - kgz
                val m = sqrt((lx * lx + ly * ly + lz * lz).toDouble()).toFloat()
                val thr = knockThreshold()
                val ts = event.timestamp
                val above = m > thr
                // двойной стук «тук-тук»: два отдельных пика подряд. Одиночный удар, звон от него и случайные
                // толчки (подъём телефона, переворот на другой бок) продление не вызывают.
                if (above && !kAbove) {
                    val gapMs = (ts - kFirstTs) / 1_000_000L
                    if (kFirstTs != 0L && kValley && gapMs in 90L..650L) {
                        // второй пик после «затишья» между ударами
                        kFirstTs = 0L
                        kValley = false
                        val now = System.currentTimeMillis()
                        if (now - lastKnockAt > 1200) {
                            lastKnockAt = now
                            onKnockDetected()
                        }
                    } else if (kCalm < thr * 0.5f) {
                        // первый пик — на фоне покоя
                        kFirstTs = ts
                        kValley = false
                    } else {
                        kFirstTs = 0L
                        kValley = false
                    }
                }
                if (kFirstTs != 0L) {
                    // между ударами сигнал должен успокоиться; если второго удара нет за 650 мс — начинаем сначала
                    if (m < thr * 0.35f) kValley = true
                    if ((ts - kFirstTs) / 1_000_000L > 650L) {
                        kFirstTs = 0L
                        kValley = false
                    }
                }
                kAbove = above
                kCalm = a * kCalm + (1 - a) * m
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        knockListener = listener
        // 10 мс между отсчётами: короткий пик лёгкого касания при 50 Гц легко «проскакивает»; включено только в последнюю минуту.
        // Пакетная доставка (до 0,5 с): датчик копит отсчёты в своей памяти, а процессор просыпается 2 раза в секунду,
        // а не 100. Продление таймера от этого срабатывает на доли секунды позже — для минуты затухания незаметно.
        // Время каждого отсчёта берётся из event.timestamp, поэтому распознавание «тук-тук» не меняется.
        sm.registerListener(listener, sensor, 10_000, 500_000)
    }

    private fun stopKnockWatch() {
        val l = knockListener ?: return
        sensorManager?.unregisterListener(l)
        knockListener = null
    }

    private fun onKnockDetected() {
        extendSleep(sleep.addMinutes)
        toast("Таймер сна продлён на ${sleep.addMinutes} мин (постукивание)")
    }

    /**
     * Тап по кнопке таймера (кроме иконки): если таймер уже идёт по минутам — добавляет
     * заданное время; если выключен — сразу запускает его на это же время. Для режимов
     * «до конца трека/плейлиста» тап открывает настройки, добавлять тут нечего.
     */
    fun onSleepPillTap() {
        if (sleep.active && sleep.mode != "minutes") {
            openSheet(Sheet.Sleep)
            return
        }
        val wasActive = sleep.active
        extendSleep(sleep.addMinutes)
        toast(if (wasActive) "Добавлено ${sleep.addMinutes} мин" else "Таймер сна: ${sleep.addMinutes} мин")
    }

    // ------------------------------------------------------------ A–B

    /** Если одна из точек снята — «зациклить» и ползунок фрагмента выключаются; когда B только что задана при активной A — ползунок появляется. */
    private fun normalizeAb(bJustSet: Boolean = false) {
        if (ab.aMs == null || ab.bMs == null) ab = ab.copy(active = false, zoom = false)
        else if (bJustSet) ab = ab.copy(zoom = true)
    }

    /** Точки A/B недоступны для прямых радиоэфиров — у них нет фиксированной длительности. */
    private fun abBlockedForCurrent(): Boolean {
        val t = trackById(currentTrackId)
        if (t == null) { toast("Сначала выберите трек"); return true }
        if (t.isRadio) { toast("Для радио точки A и B недоступны"); return true }
        return false
    }

    fun abSetA() {
        if (abBlockedForCurrent()) return
        val pos = controller?.currentPosition ?: 0L
        var b = ab.bMs
        if (b != null && pos >= b) b = null
        ab = ab.copy(aMs = pos, bMs = b, aAuto = false)
        normalizeAb()
    }

    fun abSetB() {
        if (abBlockedForCurrent()) return
        val pos = controller?.currentPosition ?: 0L
        var a = ab.aMs
        if (a != null && pos <= a) a = null
        ab = ab.copy(aMs = a, bMs = pos, bAuto = false)
        normalizeAb(bJustSet = true)
    }

    fun abClear() {
        ab = AbState()
        if (topSheet() is Sheet.Ab) closeSheet()
    }

    /** Переключатели «Зациклить» / «Увеличить» в шторке A–B. Возвращает false, если точки не заданы. */
    fun abToggle(loop: Boolean, checked: Boolean): Boolean {
        if (checked && (ab.aMs == null || ab.bMs == null)) {
            toast("Сначала задайте точки A и B")
            return false
        }
        ab = if (loop) ab.copy(active = checked) else ab.copy(zoom = checked)
        if (topSheet() is Sheet.Ab) closeSheet()
        return true
    }

    fun quickSetAb(isA: Boolean) {
        if (abBlockedForCurrent()) return
        val pos = controller?.currentPosition ?: 0L
        var a = ab.aMs
        var b = ab.bMs
        if (isA) {
            a = if (a != null) null else pos
        } else {
            b = if (b != null) null else pos
        }
        if (a != null && b != null && a >= b) {
            if (isA) b = null else a = null
        }
        ab = ab.copy(aMs = a, bMs = b, aAuto = if (isA) false else ab.aAuto, bAuto = if (isA) ab.bAuto else false)
        normalizeAb(bJustSet = !isA && b != null)
    }

    fun seekAbZoom(relMs: Long) {
        val a = ab.aMs ?: return
        seekTo(a + relMs)
    }

    // ============================================================ избранное, закладки

    fun toggleFavorite(id: String) {
        commit { d -> d.copy(tracks = d.tracks.map { if (it.id == id) it.copy(favorite = !it.favorite) else it }) }
    }

    fun toggleFavoriteCurrent() {
        val t = trackById(currentTrackId)
        if (t == null) { toast("Сначала выберите трек"); return }
        toggleFavorite(t.id)
    }

    fun toggleBookmark() {
        val t = trackById(currentTrackId)
        if (t == null) { toast("Сначала выберите трек"); return }
        if (t.isRadio) { toast("Для радиопотоков закладки недоступны"); return }
        val saved = data.bookmarks[t.id]
        if (saved != null) {
            // тап по активной закладке — снять её (переход к закладкам — долгое нажатие / список)
            removeBookmark(t.id)
            toast("Закладка убрана")
        } else {
            val pos = controller?.currentPosition ?: 0L
            update { it.copy(bookmarks = it.bookmarks + (t.id to pos)) }
            toast("Закладка сохранена: " + fmtTime(pos))
        }
    }

    fun addBookmark() {
        val t = trackById(currentTrackId)
        if (t == null) { toast("Сначала выберите трек"); return }
        if (t.isRadio) { toast("Для радиопотоков закладки недоступны"); return }
        val had = data.bookmarks.containsKey(t.id)
        val pos = controller?.currentPosition ?: 0L
        update { it.copy(bookmarks = it.bookmarks + (t.id to pos)) }
        toast((if (had) "Закладка обновлена: " else "Закладка сохранена: ") + fmtTime(pos))
    }

    fun removeBookmark(id: String) {
        commit { it.copy(bookmarks = it.bookmarks - id) }
    }

    // ============================================================ списки/плейлисты

    private fun createPlaylist(name: String, ids: List<String>): String {
        val id = "pl" + System.currentTimeMillis()
        commit { d ->
            d.copy(
                playlists = d.playlists + Playlist(id, name),
                tracks = d.tracks.map { t ->
                    if (ids.contains(t.id) && !t.playlists.contains(id)) t.copy(playlists = t.playlists + id) else t
                }
            )
        }
        return id
    }

    fun newPlaylist(ids: List<String>) {
        askText("Новый плейлист", "", "Название плейлиста", "Создать") { value ->
            val name = value.trim()
            if (name.isEmpty()) {
                toast("Введите название")
                false
            } else {
                createPlaylist(name, ids)
                toast("Плейлист создан")
                true
            }
        }
    }

    fun openPlaylist(plid: String) {
        viewedPlaylistId = plid
        views[VIEW_PLAYLIST] = ViewState()
        navigate(2)
    }

    /**
     * Выбор плейлиста в выпадающем списке рядом с названием: страница плейлиста просто показывает другой список,
     * шторка «Избранное»/«Закладки» — переключается на выбранный (обычный плейлист открывается на своей странице).
     */
    fun switchViewTo(view: String, plid: String) {
        if (!listExists(plid) || viewPlid(view) == plid) return
        if (view == VIEW_PLAYLIST) {
            viewedPlaylistId = plid
            views[VIEW_PLAYLIST] = ViewState()
        } else if (plid == FAVORITES || plid == BOOKMARKS) {
            replaceSheet(Sheet.ListSheet(plid))
        } else {
            closeAllSheets()
            openPlaylist(plid)
        }
    }

    /**
     * «Обновить плейлист»: проверяем, что файлы плейлиста ещё на месте. Удалённые или перемещённые в файловом менеджере
     * файлы иначе остаются в списке «призраками» (играть нечего, а строка есть). Найденные пропажи убираем после подтверждения —
     * на случай, когда карта памяти просто не подключена.
     */
    fun refreshPlaylist(plid: String) {
        closeSheet()
        if (!listExists(plid) || plid == RADIO) return
        val tracks = playlistTracks(plid).filter { !it.isRadio }
        if (tracks.isEmpty()) { toast("В плейлисте нет файлов"); return }
        toast("Проверяю файлы…")
        scope.launch {
            val missing = withContext(Dispatchers.IO) {
                tracks.filter { Importer.exists(app, Uri.parse(it.uri)) == false }.map { it.id }
            }
            if (missing.isEmpty()) {
                toast("Плейлист актуален: все файлы на месте")
                return@launch
            }
            askConfirm(
                "Файлы не найдены: ${missing.size}",
                "Они удалены или перемещены (или карта памяти не подключена). Убрать их из приложения?",
                "Убрать"
            ) {
                missing.forEach { removeFromLibrary(it) }
                toast("Убрано файлов: ${missing.size}")
            }
        }
    }

    fun renamePlaylist(plid: String) {
        if (!listExists(plid)) return
        askText("Переименовать", listName(plid), "Название", "Сохранить") { value ->
            val name = value.trim()
            if (name.isEmpty()) {
                toast("Введите название")
                false
            } else {
                when (plid) {
                    FAVORITES -> update { it.copy(nameFavorites = name) }
                    BOOKMARKS -> update { it.copy(nameBookmarks = name) }
                    RADIO -> update { it.copy(nameRadio = name) }
                    else -> update { d ->
                        d.copy(playlists = d.playlists.map { if (it.id == plid) it.copy(name = name) else it })
                    }
                }
                toast("Переименовано")
                true
            }
        }
    }

    /** Куда переключаться, когда активный/просматриваемый список удалён: «Default», а если и его нет (удалён) — «Избранное». */
    private fun fallbackListId(): String =
        if (data.playlists.any { it.id == DEFAULT_PLAYLIST_ID }) DEFAULT_PLAYLIST_ID else FAVORITES

    fun deletePlaylist(plid: String) {
        if (!listExists(plid)) return
        val n = playlistTracks(plid).size
        val name = listName(plid)
        val title: String
        val text: String
        if (plid == BOOKMARKS) {
            title = "Удалить все закладки?"
            text = "Закладки ($n) будут удалены. Сами файлы останутся в приложении."
        } else if (plid == FAVORITES) {
            title = "Удалить всё из «$name»?"
            text = "Из списка будут убраны все файлы ($n). Файлы, которых нет в других плейлистах, будут убраны из приложения (на диске они останутся)."
        } else if (plid == RADIO) {
            title = "Удалить все радиостанции?"
            text = "Станции ($n) будут удалены из приложения."
        } else {
            title = "Удалить плейлист «$name»?"
            text = "Плейлист будет удалён. Файлы ($n), которых нет в избранном и других плейлистах, будут убраны из приложения (на диске они останутся)."
        }
        askConfirm(title, text, "Удалить") {
            if (plid == BOOKMARKS) {
                update { it.copy(bookmarks = emptyMap()) }
            } else if (plid == FAVORITES) {
                commit { d -> d.copy(tracks = d.tracks.map { it.copy(favorite = false) }) }
            } else if (plid == RADIO) {
                commit { d ->
                    d.copy(tracks = d.tracks.map {
                        if (it.isRadio) it.copy(isRadio = false, playlists = it.playlists - RADIO) else it
                    })
                }
                if (currentPlaylistId == RADIO) currentPlaylistId = fallbackListId()
            } else {
                commit { d ->
                    d.copy(
                        playlists = d.playlists.filter { it.id != plid },
                        listOrder = d.listOrder.filter { it != plid },
                        tracks = d.tracks.map { it.copy(playlists = it.playlists.filter { x -> x != plid }) }
                    )
                }
                if (viewedPlaylistId == plid) {
                    viewedPlaylistId = fallbackListId()
                    views[VIEW_PLAYLIST] = ViewState()
                    navigate(0)
                }
                if (currentPlaylistId == plid) currentPlaylistId = fallbackListId()
            }
            toast(
                when (plid) {
                    BOOKMARKS -> "Закладки удалены"
                    FAVORITES -> "Избранное очищено"
                    RADIO -> "Радиостанции удалены"
                    else -> "Плейлист удалён"
                }
            )
        }
    }

    fun setSort(plid: String, mode: String?, dir: String?) {
        val s = getSort(plid)
        update { it.copy(sort = it.sort + (plid to SortSpec(mode ?: s.mode, dir ?: s.dir))) }
    }

    fun toggleMembership(plid: String, ids: List<String>) {
        val existing = ids.mapNotNull { trackById(it) }
        if (existing.isEmpty()) return
        val allIn = existing.all { it.playlists.contains(plid) }
        if (allIn && existing.any { !it.favorite && it.playlists.size == 1 }) {
            toast("Файл должен остаться хотя бы в одном списке")
            return
        }
        val idSet = existing.map { it.id }.toSet()
        commit { d ->
            d.copy(tracks = d.tracks.map { t ->
                if (!idSet.contains(t.id)) t
                else if (allIn) t.copy(playlists = t.playlists.filter { it != plid })
                else if (!t.playlists.contains(plid)) t.copy(playlists = t.playlists + plid)
                else t
            })
        }
    }

    fun menuRemoveFromPlaylist(id: String, plid: String) {
        commit { d ->
            d.copy(tracks = d.tracks.map { if (it.id == id) it.copy(playlists = it.playlists.filter { x -> x != plid }) else it })
        }
        closeSheet()
        toast("Убрано из плейлиста")
    }

    fun menuToggleFavorite(id: String) {
        val t = trackById(id) ?: return
        toggleFavorite(id)
        closeSheet()
        toast(if (!t.favorite) "Добавлено в избранное" else "Убрано из избранного")
    }

    fun menuRemoveBookmark(id: String) {
        removeBookmark(id)
        closeSheet()
        toast("Закладка удалена")
    }

    fun menuDelete(id: String) {
        closeSheet()
        val t = trackById(id)
        if (t != null && t.isRadio) {
            removeFromLibrary(id)
            toast("Станция удалена")
        } else {
            deleteFiles(listOf(id), "Файл удалён")
        }
    }

    // ------------------------------------------------------------ выбор треков

    fun toggleTrackSelect(v: String, id: String) {
        setView(v) { vs ->
            vs.copy(selectedIds = if (vs.selectedIds.contains(id)) vs.selectedIds - id else vs.selectedIds + id)
        }
    }

    /**
     * Вид, в котором сейчас включён выбор файлов и который «назад» должно сначала разгрузить:
     * верхняя шторка-список (Избранное/Закладки) или страница плейлиста, если шторок нет.
     */
    fun selectingView(playlistPageShown: Boolean): String? {
        val top = topSheet()
        if (top is Sheet.ListSheet) return if (viewState(top.view).selectMode) top.view else null
        if (top == null && playlistPageShown && viewState(VIEW_PLAYLIST).selectMode) return VIEW_PLAYLIST
        return null
    }

    fun exitSelect(v: String) {
        setView(v) { it.copy(selectMode = false, selectedIds = emptySet()) }
    }

    fun toggleSelectMode(v: String) {
        setView(v) { it.copy(selectMode = !it.selectMode, selectedIds = emptySet()) }
    }

    /** Долгое нажатие на трек: включает режим выбора (если ещё выключен) и отмечает этот трек. */
    fun longPressSelect(v: String, id: String) {
        setView(v) { vs ->
            if (vs.selectMode) vs.copy(selectedIds = vs.selectedIds + id)
            else vs.copy(selectMode = true, selectedIds = setOf(id))
        }
    }

    fun selectAll(v: String) {
        val list = viewList(v)
        val vs = viewState(v)
        val all = list.isNotEmpty() && list.all { vs.selectedIds.contains(it.id) }
        setView(v) { it.copy(selectedIds = if (all) emptySet() else list.map { t -> t.id }.toSet()) }
    }

    /** Свернуть/развернуть список файлов одной папки (группы). */
    fun toggleFolder(v: String, name: String) {
        setView(v) { vs ->
            vs.copy(collapsed = if (vs.collapsed.contains(name)) vs.collapsed - name else vs.collapsed + name)
        }
    }

    /** Долгое нажатие на имя папки: если все папки свёрнуты — развернуть все, иначе свернуть все. */
    fun toggleAllFolders(v: String, names: List<String>) {
        setView(v) { vs ->
            val allCollapsed = names.isNotEmpty() && names.all { vs.collapsed.contains(it) }
            vs.copy(collapsed = if (allCollapsed) emptySet() else names.toSet())
        }
    }

    fun toggleSearch(v: String) {
        setView(v) { vs -> if (vs.searchOpen) vs.copy(searchOpen = false, search = "") else vs.copy(searchOpen = true) }
    }

    fun setSearch(v: String, text: String) {
        setView(v) { it.copy(search = text) }
    }

    fun trackRowClick(v: String, id: String) {
        if (viewState(v).selectMode) toggleTrackSelect(v, id) else playTrackFromView(v, id)
    }

    fun pmSelect(plid: String, view: String?) {
        val v: String
        val fromCard = view == null
        if (view == null) {
            v = VIEW_PLAYLIST
            if (viewedPlaylistId != plid) {
                viewedPlaylistId = plid
                views[VIEW_PLAYLIST] = ViewState()
            }
        } else {
            v = view
        }
        toggleSelectMode(v)
        closeSheet()
        if (fromCard) navigate(2)
    }

    fun bulkAddToPlaylist(v: String) {
        val ids = viewState(v).selectedIds.toList()
        if (ids.isEmpty()) { toast("Сначала выберите треки"); return }
        openSheet(Sheet.AddToPlaylist(ids))
    }

    /** «Удалить из плейлиста» для выбранных: файлы остаются на диске (как пункт «Удалить из плейлиста» в меню файла). */
    fun bulkRemoveFromPlaylist(v: String) {
        val ids = viewState(v).selectedIds
        if (ids.isEmpty()) { toast("Сначала выберите треки"); return }
        val plid = viewPlid(v)
        val set = ids.toSet()
        commit { d ->
            d.copy(tracks = d.tracks.map { if (it.id in set) it.copy(playlists = it.playlists.filter { x -> x != plid }) else it })
        }
        toast("Убрано из плейлиста")
        exitSelect(v)
    }

    /** Для выбранных в «Избранном»: убрать из избранного (сами файлы остаются). */
    fun bulkRemoveFromFavorites(v: String) {
        val ids = viewState(v).selectedIds
        if (ids.isEmpty()) { toast("Сначала выберите треки"); return }
        val set = ids.toSet()
        commit { d -> d.copy(tracks = d.tracks.map { if (it.id in set) it.copy(favorite = false) else it }) }
        toast("Убрано из избранного")
        exitSelect(v)
    }

    fun bulkDelete(v: String) {
        val ids = viewState(v).selectedIds.toList()
        if (ids.isEmpty()) { toast("Сначала выберите треки"); return }
        val isBm = viewPlid(v) == BOOKMARKS
        val isRadioView = viewPlid(v) == RADIO
        askConfirm(
            if (isBm) "Убрать закладки (${ids.size})?" else if (isRadioView) "Удалить станции (${ids.size})?" else "Удалить файлы (${ids.size})?",
            if (isBm) "Сами файлы останутся в приложении." else if (isRadioView) "" else "Файлы будут удалены с диска.",
            if (isBm) "Убрать" else "Удалить"
        ) {
            if (isBm) {
                commit { d -> d.copy(bookmarks = d.bookmarks - ids.toSet()) }
                toast("Закладки убраны")
            } else if (isRadioView) {
                ids.forEach { removeFromLibrary(it) }
                toast("Станции удалены")
            } else {
                deleteFiles(ids, "Файлы удалены")
            }
            exitSelect(v)
        }
    }

    // ============================================================ удаление и импорт файлов

    private fun removeFromLibrary(id: String) {
        if (currentTrackId == id) {
            controller?.let {
                it.stop()
                it.clearMediaItems()
            }
            currentTrackId = null
            ab = AbState()
            positionMs = 0
            durationMs = 0
            isPlaying = false
        }
        if (positions.remove(id) != null) positionsDirty = true
        positionDays.remove(id)
        listOf(VIEW_PLAYLIST, FAVORITES, BOOKMARKS).forEach { v ->
            val vs = viewState(v)
            if (vs.selectedIds.contains(id)) views[v] = vs.copy(selectedIds = vs.selectedIds - id)
        }
        val hadArt = trackById(id)?.hasArt == true
        update { d -> d.copy(tracks = d.tracks.filter { it.id != id }, bookmarks = d.bookmarks - id) }
        if (hadArt) scope.launch(Dispatchers.IO) { Importer.deleteArt(app, id) }
    }

    /** Трек без списков (не в избранном и не в плейлистах) больше нигде не виден — убираем. Играющий остаётся. */
    private fun gcOrphans() {
        data.tracks
            .filter { !it.favorite && it.playlists.isEmpty() && it.id != currentTrackId }
            .forEach { removeFromLibrary(it.id) }
    }

    private fun deleteFiles(ids: List<String>, okMessage: String) {
        val tracks = ids.mapNotNull { trackById(it) }
        scope.launch {
            var failed = 0
            withContext(Dispatchers.IO) {
                tracks.forEach { t ->
                    if (!Importer.deleteFromDisk(app, Uri.parse(t.uri))) failed++
                }
            }
            tracks.forEach { removeFromLibrary(it.id) }
            toast(if (failed == 0) okMessage else "Убрано из приложения; с диска не удалено: $failed")
        }
    }

    /** Права доступа и папки файлов — несколько файлов одновременно (на SD-карте и через SAF запросы медленные). */
    private suspend fun resolveFolders(uris: List<Uri>): List<Importer.ScannedFile> = coroutineScope {
        val sem = Semaphore(4)
        uris.map { u ->
            async(Dispatchers.IO) {
                sem.withPermit {
                    Importer.persist(app, u)
                    Importer.ScannedFile(u, Importer.folderFromUri(app, u), "", 0L)
                }
            }
        }.awaitAll()
    }

    fun onFilesPicked(view: String, uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            loadedSignal.await()
            val items = resolveFolders(uris)
            addUris(viewPlid(view), items)
        }
    }

    /**
     * Треки, пришедшие извне приложения (меню «Открыть в…» / «Поделиться» из файлового менеджера
     * и других программ): по умолчанию попадают в плейлист «Default» (создаётся при необходимости).
     * [play] — для «Открыть в…» сразу запускаем первый из открытых файлов.
     */
    fun onExternalUris(uris: List<Uri>, play: Boolean) {
        if (uris.isEmpty()) return
        scope.launch {
            loadedSignal.await()   // файл мог прийти при холодном старте — ждём загрузки библиотеки
            val items = resolveFolders(uris)
            ensureDefaultPlaylist()
            addUris(DEFAULT_PLAYLIST_ID, items)
            if (play) {
                val first = uris.firstNotNullOfOrNull { u -> data.tracks.firstOrNull { it.uri == u.toString() } }
                if (first != null) {
                    setActivePlaylist(DEFAULT_PLAYLIST_ID)
                    if (shuffle) buildShuffle(activeList(), first.id)
                    loadAndPlay(first, null)
                    navigate(1)
                }
            }
        }
    }

    private fun ensureDefaultPlaylist() {
        if (data.playlists.any { it.id == DEFAULT_PLAYLIST_ID }) return
        update { d -> d.copy(playlists = d.playlists + Playlist(DEFAULT_PLAYLIST_ID, DEFAULT_PLAYLIST_NAME)) }
    }

    fun onFolderPicked(view: String, tree: Uri) {
        scope.launch {
            loadedSignal.await()
            stopRequested = false
            busyKind = "scan"
            scanFound = 0
            val items = try {
                withContext(Dispatchers.IO) {
                    Importer.persist(app, tree)
                    Importer.scanTree(app, tree, shouldStop = { stopRequested }, onFound = { n ->
                        scanFound = n
                    })
                }
            } catch (e: Exception) {
                emptyList()
            }
            val stopped = stopRequested
            if (items.isEmpty()) {
                busyKind = null
                stopRequested = false
                toast(if (stopped) "Сканирование остановлено" else "В папке нет аудиофайлов")
                return@launch
            }
            if (stopped) toast("Сканирование остановлено — добавляю найденное (${items.size})")
            stopRequested = false
            val hasSub = items.any { it.relativeFolder.contains('/') }
            val fixed = if (hasSub) items.map { item ->
                item.copy(relativeFolder = if (item.relativeFolder.contains('/')) item.relativeFolder.substringAfter('/') else item.relativeFolder)
            } else items
            addUris(viewPlid(view), fixed)
        }
    }

    /** Кнопка «Остановить» при сканировании папки и добавлении файлов. */
    fun stopScan() {
        stopRequested = true
    }

    /** (сделано, всего) во время добавления файлов — шапка списка показывает по нему полоску прогресса. */
    var importProgress by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    private suspend fun addUris(plid: String, items: List<Importer.ScannedFile>) {
        if (items.isEmpty()) {
            busyKind = null
            return
        }
        val favFlag = plid == FAVORITES
        val plList: List<String> = if (plid == FAVORITES || plid == BOOKMARKS) emptyList() else listOf(plid)
        val stamp = System.currentTimeMillis()
        var addedCount = 0
        var existingCount = 0
        var skippedDup = 0
        var stopped = false
        busyKind = "import"
        importProgress = 0 to items.size
        try {
            // Быстрый индекс метаданных из MediaStore за 1 SQL-запрос
            val mediaStoreMap = withContext(Dispatchers.IO) { Importer.queryMediaStoreMap(app) }

            // Дубли: предварительно строим индекс всех файлов в библиотеке 1 раз
            val keyToTrack = HashMap<String, String>()
            data.tracks.filter { !it.isRadio }.forEach { keyToTrack[Importer.keyOf(Uri.parse(it.uri))] = it.id }
            val seenInBatch = HashSet<String>()

            // Чтение метаданных с использованием всех ядер процессора
            val batchSize = 128
            val threadCount = minOf(8, Runtime.getRuntime().availableProcessors().coerceAtLeast(4))
            val sem = Semaphore(threadCount)
            val artCache = ConcurrentHashMap<Long, String>()
            var doneSoFar = 0

            for (chunk in items.withIndex().chunked(batchSize)) {
                if (stopRequested) {
                    stopped = true
                    break
                }
                // дубли внутри набора и уже известные файлы отсеиваем сразу
                val jobs = ArrayList<Triple<Int, Importer.ScannedFile, String?>>()
                for ((i, file) in chunk) {
                    val key = Importer.keyOf(file.uri)
                    if (!seenInBatch.add(key)) continue
                    jobs += Triple(i, file, keyToTrack[key])
                }
                val described: List<Triple<Uri, String?, Track?>> = coroutineScope {
                    jobs.map { (i, file, knownId) ->
                        async<Triple<Uri, String?, Track?>>(Dispatchers.IO) {
                            if (knownId != null) Triple(file.uri, knownId, null)
                            else sem.withPermit {
                                Triple(
                                    file.uri, null,
                                    Importer.describe(
                                        app, file.uri, file.relativeFolder, "t${stamp}_$i", 0L,
                                        knownName = file.name, knownSize = file.size,
                                        artCache = artCache, mediaStoreMap = mediaStoreMap
                                    )
                                )
                            }
                        }
                    }.awaitAll()
                }
                var counter = data.orderCounter
                val newTracks = mutableListOf<Track>()
                val existingIds = mutableSetOf<String>()
                for ((u, knownId, t) in described) {
                    if (knownId != null) {
                        existingIds.add(knownId)
                    } else if (t != null) {
                        counter += 1
                        newTracks.add(t.copy(favorite = favFlag, playlists = plList, order = counter))
                        keyToTrack[Importer.keyOf(u)] = t.id
                    }
                }
                skippedDup += chunk.size - described.size
                var alreadyThere = 0
                if (newTracks.isNotEmpty() || existingIds.isNotEmpty()) {
                    update { d ->
                        val updated = if (existingIds.isEmpty()) d.tracks else d.tracks.map { t ->
                            if (existingIds.contains(t.id)) {
                                if ((favFlag && t.favorite) || (plList.isNotEmpty() && t.playlists.contains(plList[0]))) alreadyThere++
                                t.copy(
                                    favorite = t.favorite || favFlag,
                                    playlists = if (plList.isNotEmpty() && !t.playlists.contains(plList[0])) t.playlists + plList else t.playlists
                                )
                            } else t
                        }
                        d.copy(tracks = updated + newTracks, orderCounter = counter)
                    }
                }
                skippedDup += alreadyThere
                addedCount += newTracks.size
                existingCount += existingIds.size - alreadyThere
                doneSoFar += chunk.size
                importProgress = doneSoFar to items.size
            }
        } finally {
            importProgress = null
            busyKind = null
            stopRequested = false
        }
        val count = addedCount + existingCount
        val msg = when {
            count == 0 && skippedDup > 0 -> "Эти файлы уже есть в списке"
            count == 0 -> "Не удалось добавить файлы"
            stopped -> "Остановлено. Добавлено файлов: $count"
            else -> "Добавлено файлов: $count"
        }
        toast(msg)
    }

    // ============================================================ онлайн-радио

    fun requestRadioAdd() {
        popup = Popup.RadioSource()
    }

    /** Из шторки выбора источника радио: переход к вводу прямой ссылки. */
    fun askRadioUrl() {
        closePopup()
        askText(
            "Ссылка на радио", "", "https://... или .m3u/.m3u8", "Добавить"
        ) { raw ->
            val url = raw.trim()
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                toast("Введите ссылку, начинающуюся с http:// или https://")
                false
            } else {
                addRadioByUrl(url)
                true
            }
        }
    }

    private fun addRadioEntries(entries: List<M3uEntry>) {
        if (entries.isEmpty()) {
            toast("В плейлисте не нашлось ссылок на станции")
            return
        }
        val known = data.tracks.filter { it.isRadio }.map { it.uri }.toSet()
        var counter = data.orderCounter
        val stamp = System.currentTimeMillis()
        val newTracks = mutableListOf<Track>()
        var i = 0
        for (e in entries) {
            if (known.contains(e.url) || newTracks.any { it.uri == e.url }) continue
            counter += 1
            i += 1
            newTracks += Track(
                id = "r${stamp}_$i",
                uri = e.url,
                title = e.name.ifBlank { "Станция $i" },
                artist = "Онлайн радио",
                folder = "",
                durationMs = 0L,
                size = 0L,
                playlists = listOf(RADIO),
                order = counter,
                isRadio = true
            )
        }
        if (newTracks.isEmpty()) {
            toast("Эти станции уже добавлены")
            return
        }
        commit { d -> d.copy(tracks = d.tracks + newTracks, orderCounter = counter) }
        toast(if (newTracks.size == 1) "Станция добавлена" else "Добавлено станций: ${newTracks.size}")
    }

    /** Прямая ссылка на станцию либо на файл плейлиста m3u/m3u8 (тогда сначала скачиваем его текст). */
    fun addRadioByUrl(url: String, nameOverride: String? = null) {
        val host = runCatching { Uri.parse(url).host }.getOrNull() ?: "Станция"
        if (!RadioImport.looksLikePlaylistUrl(url)) {
            addRadioEntries(listOf(M3uEntry(nameOverride ?: host, url)))
            return
        }
        scope.launch {
            val text = try {
                withContext(Dispatchers.IO) { RadioImport.fetchText(url) }
            } catch (e: Exception) {
                toast("Не удалось загрузить плейлист")
                return@launch
            }
            if (RadioImport.isHlsManifest(text)) {
                // это не список станций, а сам HLS-манифест — добавляем ссылку как один поток
                addRadioEntries(listOf(M3uEntry(nameOverride ?: host, url)))
            } else {
                addRadioEntries(RadioImport.parseSimple(text, nameOverride ?: host))
            }
        }
    }

    /** «Добавить радиопоток» из шторки плейлиста / кнопки «+» плейлиста: запрашивает ссылку и
     *  создаёт станцию сразу в этом плейлисте (а не в общем списке «Радио»), затем открывает
     *  шторку редактирования станции — там можно сразу задать название и обложку. */
    fun addRadioToPlaylistPrompt(plid: String) {
        askText(
            "Ссылка на радио", "", "https://... или .m3u/.m3u8", "Добавить"
        ) { raw ->
            val url = raw.trim()
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                toast("Введите ссылку, начинающуюся с http:// или https://")
                false
            } else {
                createRadioInPlaylist(plid, url)
                true
            }
        }
    }

    private fun createRadioInPlaylist(plid: String, url: String) {
        val host = runCatching { Uri.parse(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Станция"
        val id = "r" + System.currentTimeMillis()
        val counter = data.orderCounter + 1
        val track = Track(
            id = id, uri = url, title = host, artist = "Онлайн радио", folder = "",
            durationMs = 0L, size = 0L,
            favorite = plid == FAVORITES,
            playlists = if (plid == FAVORITES) emptyList() else listOf(plid),
            order = counter, isRadio = true
        )
        commit { d -> d.copy(tracks = d.tracks + track, orderCounter = counter) }
        openSheet(Sheet.RadioEdit(id))
    }

    /** Переименование радиостанции из шторки «Изменить». */
    fun renameRadioTrack(id: String, name: String) {
        update { d -> d.copy(tracks = d.tracks.map { if (it.id == id) it.copy(title = name) else it }) }
        toast("Переименовано")
    }

    /** Смена адреса потока радиостанции из шторки «Изменить». */
    fun setRadioTrackUrl(id: String, url: String) {
        resolvedStreams.remove(trackById(id)?.uri)
        update { d -> d.copy(tracks = d.tracks.map { if (it.id == id) it.copy(uri = url) else it }) }
        if (currentTrackId == id) {
            // если этот поток сейчас играет — перезапускаем его по новому адресу
            trackById(id)?.let { loadAndPlay(it, null) }
        }
        toast("Адрес изменён")
    }

    /** Запросить выбор картинки для обложки: id == "draft" — для ещё не сохранённой станции. */
    fun requestCoverPick(targetId: String) {
        popup = null
        addRequest = AddRequest(AddKind.COVER_IMAGE, targetId, nextNonce())
    }

    /** Картинка выбрана системным пикером — сохраняем её как обложку трека [targetId]. */
    fun onCoverPicked(targetId: String, uri: Uri) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { Importer.saveCoverFromUri(app, uri, targetId) }
            if (ok) {
                update { d -> d.copy(tracks = d.tracks.map { if (it.id == targetId) it.copy(hasArt = true) else it }) }
                toast("Обложка сохранена")
            } else {
                toast("Не удалось сохранить обложку")
            }
        }
    }

    /** Локальный файл m3u/m3u8, выбранный через системный пикер. */
    fun onRadioFilePicked(uri: Uri) {
        scope.launch {
            val text = try {
                withContext(Dispatchers.IO) { Importer.readText(app, uri) }
            } catch (e: Exception) {
                toast("Не удалось прочитать файл")
                return@launch
            }
            if (text.isBlank()) {
                toast("Файл пуст")
                return@launch
            }
            addRadioEntries(RadioImport.parseSimple(text, "Станция"))
        }
    }

    // ============================================================ цветовой акцент

    fun setAccentColor(argb: Long?) = update { it.copy(accentColor = argb) }

    /** Язык интерфейса: "ru" или "en". Применяется сразу во всём приложении. */
    fun setLanguage(lang: String) {
        update { it.copy(language = lang) }
        I18n.lang = I18n.resolve(lang)
    }

    // ============================================================ экспорт/импорт настроек

    var exportRequest by mutableStateOf<Long?>(null)
    var importRequest by mutableStateOf<Long?>(null)

    fun requestExportSettings() { exportRequest = nextNonce() }
    fun requestImportSettings() { importRequest = nextNonce() }

    private fun exportSettingsJson(): String = Storage.toJson(data, emptyMap())

    /** Импорт файла настроек: переносятся только настройки, библиотека этого устройства не затрагивается. */
    private fun importSettingsJson(text: String): Boolean {
        return try {
            val loaded = Storage.fromJson(text)
            val l = loaded.data
            // Переносим только настройки; треки, плейлисты и закладки этого устройства остаются как есть.
            update {
                it.copy(
                    nameFavorites = l.nameFavorites, nameBookmarks = l.nameBookmarks, nameRadio = l.nameRadio,
                    sort = l.sort, group = l.group, restorePosition = l.restorePosition, rewindAfterPause = l.rewindAfterPause,
                    seekStepSec = l.seekStepSec, theme = l.theme, accentColor = l.accentColor,
                    language = l.language, coverMode = l.coverMode, coverHarmonize = l.coverHarmonize, fxLevel = l.fxLevel, fxSound = l.fxSound, fxHaptic = l.fxHaptic, fxBackdropBlur = l.fxBackdropBlur, fxBackdropAlpha = l.fxBackdropAlpha, knockSensitivity = l.knockSensitivity, sound = l.sound
                )
            }
            I18n.lang = I18n.resolve(data.language)
            SoundEngine.apply(data.sound)
            // профили эквалайзера пришли из файла — подстраиваем под устройство, на которое звук идёт сейчас
            syncOutputDevice()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun writeExportTo(uri: Uri) {
        scope.launch {
            val json = exportSettingsJson()
            val ok = withContext(Dispatchers.IO) {
                try {
                    app.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    true
                } catch (e: Exception) {
                    false
                }
            }
            toast(if (ok) "Настройки сохранены в файл" else "Не удалось сохранить файл")
        }
    }

    fun readImportFrom(uri: Uri) {
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    app.contentResolver.openInputStream(uri)?.use { it.bufferedReader(Charsets.UTF_8).readText() }
                } catch (e: Exception) {
                    null
                }
            }
            if (text == null) {
                toast("Не удалось прочитать файл")
                return@launch
            }
            toast(if (importSettingsJson(text)) "Настройки импортированы" else "Файл повреждён или не подходит")
        }
    }
}
