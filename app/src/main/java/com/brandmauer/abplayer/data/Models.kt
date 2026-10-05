package com.brandmauer.abplayer.data

/** Идентификаторы встроенных списков (как BUILTIN_LISTS в прототипе). */
const val FAVORITES = "favorites"
const val BOOKMARKS = "bookmarks"
const val RADIO = "radio"
val BUILTIN_LISTS = setOf(FAVORITES, BOOKMARKS, RADIO)

/** Режимы показа обложек (AppData.coverMode). */
const val COVER_OFF = "off"
const val COVER_NOTIFICATION = "notification"
const val COVER_PLAYER = "player"
const val COVER_ALL = "all"

fun coverInPlayer(mode: String): Boolean = mode == COVER_PLAYER || mode == COVER_ALL
fun coverInNotification(mode: String): Boolean = mode == COVER_NOTIFICATION || mode == COVER_ALL

/** Плейлист «Default»: сюда попадают треки, добавленные извне приложения (открыть в / поделиться). */
const val DEFAULT_PLAYLIST_ID = "default"
const val DEFAULT_PLAYLIST_NAME = "Default"

/**
 * Трек существует в приложении, только пока он в «Избранном» или в каком-либо плейлисте.
 * Радиостанция — такой же Track с isRadio=true, durationMs=0 (прямой эфир) и playlists=[RADIO].
 */
data class Track(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val folder: String,          // "" = «Без папки»
    val durationMs: Long,
    val size: Long,
    val favorite: Boolean = false,
    val playlists: List<String> = emptyList(),
    val order: Long = 0L,        // порядок добавления
    val isRadio: Boolean = false,
    val hasArt: Boolean = false  // есть сохранённая обложка в filesDir/covers/<id>.jpg
)

data class Playlist(val id: String, val name: String)

/** mode: custom | name | duration | size;  dir: asc | desc. Группировка по папкам/типу — отдельно, см. GroupSpec. */
data class SortSpec(val mode: String = "custom", val dir: String = "asc")

/** mode: none | folder | type */
data class GroupSpec(val mode: String = "none")

data class EqPreset(val id: String, val name: String, val bands: List<Int>?)

fun defaultPresets(): List<EqPreset> = listOf(
    EqPreset("manual", "Ручной", null),
    EqPreset("flat", "Плоский", listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
    EqPreset("bass", "Бас", listOf(7, 6, 5, 3, 1, 0, -1, -1, 0, 0)),
    EqPreset("rock", "Рок", listOf(4, 3, 1, -1, -2, 0, 2, 4, 5, 5))
)

val EQ_FREQS = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
val EQ_LABELS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

/** Устройства вывода, для которых эквалайзер запоминается отдельно. Bluetooth — по адресу ("bt:<адрес>"). */
const val OUT_SPEAKER = "speaker"
const val OUT_WIRED = "wired"
const val OUT_BT_PREFIX = "bt:"

/** Сохранённые настройки эквалайзера одного устройства вывода. */
data class EqProfile(
    val enabled: Boolean,
    val preset: String,
    val bands: List<Int>
)

data class SoundState(
    // eqEnabled/eqPreset/eqBands — «живое» состояние эквалайзера для устройства eqDevice;
    // остальные устройства хранятся в eqProfiles и подставляются при смене вывода
    val eqDevice: String = OUT_SPEAKER,
    val eqDeviceName: String = "",
    val eqProfiles: Map<String, EqProfile> = emptyMap(),
    val eqEnabled: Boolean = false,
    val eqPreset: String = "manual",
    val eqBands: List<Int> = List(10) { 0 },
    val presets: List<EqPreset> = defaultPresets(),
    val presetOrder: List<String> = defaultPresets().map { it.id },
    val soundEnabled: Boolean = false,
    val tone: Float = 1f,           // высота тона (тембр) без изменения скорости: множитель 0.5..1.5
    val speed: Float = 1f,         // скорость без изменения тона: множитель 0.5..3
    val balance: Int = 0,           // -100..100
    val preamp: Int = 0,            // дБ -12..12
    val volumeFineEnabled: Boolean = false,
    val volumeTrim: Int = 0,        // проценты -100..100, шаг 1
    // какие эффекты были включены в момент «быстрого отключения» долгим нажатием на значок звука:
    // битовая маска (1 — эквалайзер, 2 — звук, 4 — точная громкость); 0 — нечего восстанавливать
    val fxBackup: Int = 0
)

/**
 * Энергосбережение. profile: "max" (максимальное энергосбережение) | "save" (энергосбережение) |
 * "balanced" (баланс) | "quality" (качество) | "manual" (ручной — использует поля ниже как есть).
 * Остальные поля при выборе не-ручного профиля выставляются автоматически (см. Hub.applyPowerProfile),
 * но хранятся, чтобы «Ручной» режим стартовал с последних использованных значений.
 * Уровни (cpu/screen/network/background): 0 — минимум/экономия, 1 — обычный, 2 — максимум/качество.
 */
data class PowerSettings(
    val profile: String = "balanced",
    val cpu: Int = 1,
    val screen: Int = 1,
    val network: Int = 1,
    val background: Int = 1,
    val autoBattery: Boolean = false,
    val autoBatteryPercent: Int = 20,
    val autoSystemSaver: Boolean = false,
    // аппаратное декодирование звука (audio offload, Android 10+): сжатый звук обрабатывает DSP, процессор спит.
    // Работает только при выключенных эффектах звука (эквалайзер, «Звук», «Точная громкость») и выключенном повторе A–B
    val offload: Boolean = false
)

data class AppData(
    val tracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val listOrder: List<String> = emptyList(),
    val nameFavorites: String = "Избранное",
    val nameBookmarks: String = "Закладки",
    val nameRadio: String = "Радио",
    val sort: Map<String, SortSpec> = emptyMap(),
    val group: Map<String, GroupSpec> = emptyMap(),
    val bookmarks: Map<String, Long> = emptyMap(),   // trackId -> мс
    val restorePosition: Boolean = true,
    val rewindAfterPause: Boolean = false,             // «Откат 20с после паузы»: после паузы дольше 5 минут продолжать на 20 секунд раньше
    val seekStepSec: Int = 10,
    val theme: String = "",                            // "" = как в системе, "dark", "light"
    val accentColor: Long? = null,                      // ARGB, null = цвет темы по умолчанию
    val language: String = "en",                        // по умолчанию английский (не «как в системе»)
    val coverMode: String = COVER_ALL,                 // где показывать обложки: off | notification | player | all
    val knockSensitivity: Int = 82,                    // чувствительность постукивания (продление таймера сна): 0 — мин … 100 — макс
    val coverHarmonize: Boolean = true,                // «Гармонизировать цвета»: приглушение цвета/контраста обложки + мягкий градиент
    val fxLevel: Float = 0.5f,                         // «Сила эффектов» (визуальные): 0 — выключены … 1 — максимум
    val fxSound: Float = 0.5f,                         // громкость звуков интерфейса (клики): 0 — откл … 1 — макс
    val fxHaptic: Float = 0.5f,                        // сила тактильного отклика (вибрация): 0 — откл … 1 — макс
    val fxBackdropBlur: Float = 0.8f,                  // размытие фона из обложки: 0 — без размытия … 1 — макс
    val fxBackdropAlpha: Float = 0.2f,                 // непрозрачность фона из обложки: 0 — не виден … 1 — макс
    val defaultSeeded: Boolean = false,                // плейлист Default уже создан при первом запуске
    val activePlaylist: String = "",                   // последний проигрываемый список ("" = не выбран)
    val shownHints: Set<String> = emptySet(),          // разовые пояснения, которые уже показывались
    val radioSeeded: Boolean = false,                  // стартовый набор радиостанций уже добавлен
    val sound: SoundState = SoundState(),
    val power: PowerSettings = PowerSettings(),
    val orderCounter: Long = 0L
)

/** Состояние точек A–B. Значения в миллисекундах. */
data class AbState(
    val aMs: Long? = null,
    val bMs: Long? = null,
    val active: Boolean = false,   // «Зациклить»
    val zoom: Boolean = false,     // «Увеличить»
    val aAuto: Boolean = false,    // true — точка A выставлена автоматически кнопкой play, а не вручную
    val bAuto: Boolean = false     // true — точка B выставлена автоматически при остановке таймера сна
)

data class SleepState(
    val mode: String? = null,      // minutes | endTrack | endPlaylist
    val preset: String? = null,
    val remaining: Int = 0,        // секунды
    val addMinutes: Int = 15,
    val active: Boolean = false,
    val knockExtend: Boolean = true // продлевать таймер постукиванием по устройству во время затухания громкости
)

data class ViewState(
    val search: String = "",
    val searchOpen: Boolean = false,
    val selectMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val collapsed: Set<String> = emptySet() // свёрнутые папки (по названию заголовка группы)
)
