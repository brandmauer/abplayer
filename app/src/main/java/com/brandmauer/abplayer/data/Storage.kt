package com.brandmauer.abplayer.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.StringReader
import java.io.StringWriter

/** Хранение библиотеки в одном JSON-файле (filesDir/library.json), с использованием быстрого потокового JsonReader/JsonWriter. */
object Storage {
    private const val FILE = "library.json"
    private const val POS_FILE = "positions.json"

    class Loaded(val data: AppData, val positions: MutableMap<String, Long>, val posDays: MutableMap<String, Int> = mutableMapOf())

    fun load(context: Context): Loaded {
        val f = File(context.filesDir, FILE)
        val base = if (!f.exists()) Loaded(AppData(), mutableMapOf())
        else loadFast(f) ?: tryLegacyLoad(f)

        val pf = File(context.filesDir, POS_FILE)
        if (pf.exists()) {
            loadPositionsFast(pf, base.positions, base.posDays)
        }
        return base
    }

    fun hasPositionsFile(context: Context): Boolean = File(context.filesDir, POS_FILE).exists()

    private fun writeAtomicStream(context: Context, name: String, block: (JsonWriter) -> Unit) {
        val tmp = File(context.filesDir, "$name.tmp")
        try {
            FileOutputStream(tmp).use { fos ->
                BufferedWriter(OutputStreamWriter(fos, Charsets.UTF_8), 32 * 1024).use { bw ->
                    JsonWriter(bw).use { writer ->
                        block(writer)
                    }
                }
            }
            val dst = File(context.filesDir, name)
            if (!tmp.renameTo(dst)) {
                tmp.copyTo(dst, overwrite = true)
                tmp.delete()
            }
        } catch (e: Exception) {
            tmp.delete()
        }
    }

    fun savePositions(context: Context, positions: Map<String, Long>, posDays: Map<String, Int>) {
        writeAtomicStream(context, POS_FILE) { writer ->
            writer.beginObject()
            writer.name("p")
            writer.beginObject()
            positions.forEach { (k, v) -> writer.name(k).value(v) }
            writer.endObject()

            writer.name("d")
            writer.beginObject()
            posDays.forEach { (k, v) -> if (positions.containsKey(k)) writer.name(k).value(v.toLong()) }
            writer.endObject()
            writer.endObject()
        }
    }

    fun save(context: Context, data: AppData) {
        writeAtomicStream(context, FILE) { writer ->
            writeFast(writer, data)
        }
    }

    fun toJson(d: AppData, positions: Map<String, Long>, posDays: Map<String, Int> = emptyMap()): String {
        val sw = StringWriter()
        JsonWriter(sw).use { writer ->
            writeFast(writer, d, positions, posDays)
        }
        return sw.toString()
    }

    fun fromJson(text: String): Loaded {
        val sr = StringReader(text)
        val reader = JsonReader(sr)
        return try {
            readFast(reader)
        } catch (e: Exception) {
            tryLegacyFromJson(text)
        }
    }

    // ------------------------------------------------------------------ Fast Streaming Writer

    private fun writeFast(
        writer: JsonWriter,
        d: AppData,
        positions: Map<String, Long> = emptyMap(),
        posDays: Map<String, Int> = emptyMap()
    ) {
        writer.beginObject()

        writer.name("tracks")
        writer.beginArray()
        d.tracks.forEach { t ->
            writer.beginObject()
            writer.name("id").value(t.id)
            writer.name("uri").value(t.uri)
            writer.name("title").value(t.title)
            writer.name("artist").value(t.artist)
            writer.name("folder").value(t.folder)
            writer.name("durationMs").value(t.durationMs)
            writer.name("size").value(t.size)
            writer.name("favorite").value(t.favorite)
            writer.name("playlists")
            writer.beginArray()
            t.playlists.forEach { writer.value(it) }
            writer.endArray()
            writer.name("order").value(t.order)
            writer.name("isRadio").value(t.isRadio)
            writer.name("hasArt").value(t.hasArt)
            writer.endObject()
        }
        writer.endArray()

        writer.name("playlists")
        writer.beginArray()
        d.playlists.forEach { p ->
            writer.beginObject()
            writer.name("id").value(p.id)
            writer.name("name").value(p.name)
            writer.endObject()
        }
        writer.endArray()

        writer.name("listOrder")
        writer.beginArray()
        d.listOrder.forEach { writer.value(it) }
        writer.endArray()

        writer.name("nameFavorites").value(d.nameFavorites)
        writer.name("nameBookmarks").value(d.nameBookmarks)
        writer.name("nameRadio").value(d.nameRadio)

        writer.name("sort")
        writer.beginObject()
        d.sort.forEach { (k, v) ->
            writer.name(k)
            writer.beginObject()
            writer.name("mode").value(v.mode)
            writer.name("dir").value(v.dir)
            writer.endObject()
        }
        writer.endObject()

        writer.name("group")
        writer.beginObject()
        d.group.forEach { (k, v) ->
            writer.name(k)
            writer.beginObject()
            writer.name("mode").value(v.mode)
            writer.endObject()
        }
        writer.endObject()

        writer.name("bookmarks")
        writer.beginObject()
        d.bookmarks.forEach { (k, v) -> writer.name(k).value(v) }
        writer.endObject()

        if (positions.isNotEmpty()) {
            writer.name("positions")
            writer.beginObject()
            positions.forEach { (k, v) -> writer.name(k).value(v) }
            writer.endObject()
        }
        if (posDays.isNotEmpty()) {
            writer.name("positionDays")
            writer.beginObject()
            posDays.forEach { (k, v) -> if (positions.containsKey(k)) writer.name(k).value(v.toLong()) }
            writer.endObject()
        }

        writer.name("restorePosition").value(d.restorePosition)
        writer.name("rewindAfterPause").value(d.rewindAfterPause)
        writer.name("seekStepSec").value(d.seekStepSec.toLong())
        writer.name("theme").value(d.theme)
        if (d.accentColor != null) writer.name("accentColor").value(d.accentColor)
        writer.name("language").value(d.language)
        writer.name("coverMode").value(d.coverMode)
        writer.name("coverHarmonize").value(d.coverHarmonize)
        writer.name("fxLevel").value(d.fxLevel.toDouble())
        writer.name("fxSound").value(d.fxSound.toDouble())
        writer.name("fxHaptic").value(d.fxHaptic.toDouble())
        writer.name("fxBackdropBlur").value(d.fxBackdropBlur.toDouble())
        writer.name("fxBackdropAlpha").value(d.fxBackdropAlpha.toDouble())
        writer.name("knockSens").value(d.knockSensitivity.toLong())
        writer.name("defaultSeeded").value(d.defaultSeeded)
        writer.name("activePlaylist").value(d.activePlaylist)

        writer.name("shownHints")
        writer.beginArray()
        d.shownHints.forEach { writer.value(it) }
        writer.endArray()

        writer.name("radioSeeded").value(d.radioSeeded)
        writer.name("orderCounter").value(d.orderCounter)

        // Sound
        writer.name("sound")
        val s = d.sound
        writer.beginObject()
        writer.name("eqEnabled").value(s.eqEnabled)
        writer.name("eqPreset").value(s.eqPreset)
        writer.name("eqBands")
        writer.beginArray()
        s.eqBands.forEach { writer.value(it.toLong()) }
        writer.endArray()

        writer.name("presetOrder")
        writer.beginArray()
        s.presetOrder.forEach { writer.value(it) }
        writer.endArray()

        writer.name("eqDevice").value(s.eqDevice)
        writer.name("eqDeviceName").value(s.eqDeviceName)

        writer.name("eqProfiles")
        writer.beginObject()
        s.eqProfiles.forEach { (k, v) ->
            writer.name(k)
            writer.beginObject()
            writer.name("enabled").value(v.enabled)
            writer.name("preset").value(v.preset)
            writer.name("bands")
            writer.beginArray()
            v.bands.forEach { writer.value(it.toLong()) }
            writer.endArray()
            writer.endObject()
        }
        writer.endObject()

        writer.name("presets")
        writer.beginArray()
        s.presets.forEach { p ->
            writer.beginObject()
            writer.name("id").value(p.id)
            writer.name("name").value(p.name)
            if (p.bands != null) {
                writer.name("bands")
                writer.beginArray()
                p.bands.forEach { writer.value(it.toLong()) }
                writer.endArray()
            }
            writer.endObject()
        }
        writer.endArray()

        writer.name("soundEnabled").value(s.soundEnabled)
        writer.name("tone").value(s.tone.toDouble())
        writer.name("speed").value(s.speed.toDouble())
        writer.name("balance").value(s.balance.toLong())
        writer.name("preamp").value(s.preamp.toLong())
        writer.name("volumeFineEnabled").value(s.volumeFineEnabled)
        writer.name("volumeTrim").value(s.volumeTrim.toLong())
        writer.name("fxBackup").value(s.fxBackup.toLong())
        writer.endObject()

        // Power
        writer.name("power")
        val pw = d.power
        writer.beginObject()
        writer.name("profile").value(pw.profile)
        writer.name("cpu").value(pw.cpu.toLong())
        writer.name("screen").value(pw.screen.toLong())
        writer.name("network").value(pw.network.toLong())
        writer.name("background").value(pw.background.toLong())
        writer.name("autoBattery").value(pw.autoBattery)
        writer.name("autoBatteryPercent").value(pw.autoBatteryPercent.toLong())
        writer.name("autoSystemSaver").value(pw.autoSystemSaver)
        writer.name("offload").value(pw.offload)
        writer.endObject()

        writer.endObject()
    }

    // ------------------------------------------------------------------ Fast Streaming Reader

    private fun loadFast(file: File): Loaded? {
        return try {
            FileInputStream(file).use { fis ->
                BufferedReader(InputStreamReader(fis, Charsets.UTF_8), 32 * 1024).use { br ->
                    readFast(JsonReader(br))
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun loadPositionsFast(file: File, outPos: MutableMap<String, Long>, outDays: MutableMap<String, Int>) {
        try {
            FileInputStream(file).use { fis ->
                BufferedReader(InputStreamReader(fis, Charsets.UTF_8), 8 * 1024).use { br ->
                    val reader = JsonReader(br)
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "p" -> {
                                outPos.clear()
                                reader.beginObject()
                                while (reader.hasNext()) outPos[reader.nextName()] = reader.nextLong()
                                reader.endObject()
                            }
                            "d" -> {
                                outDays.clear()
                                reader.beginObject()
                                while (reader.hasNext()) outDays[reader.nextName()] = reader.nextInt()
                                reader.endObject()
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
            }
        } catch (e: Exception) {
            try {
                val o = JSONObject(file.readText(Charsets.UTF_8))
                outPos.clear(); outDays.clear()
                o.optJSONObject("p")?.let { b -> b.keys().forEach { k -> outPos[k] = b.getLong(k) } }
                o.optJSONObject("d")?.let { b -> b.keys().forEach { k -> outDays[k] = b.optInt(k, 0) } }
            } catch (ignored: Exception) {
            }
        }
    }

    private fun readFast(reader: JsonReader): Loaded {
        var tracks = mutableListOf<Track>()
        var playlists = mutableListOf<Playlist>()
        var listOrder = mutableListOf<String>()
        var nameFavorites = "Избранное"
        var nameBookmarks = "Закладки"
        var nameRadio = "Радио"
        val sort = mutableMapOf<String, SortSpec>()
        val group = mutableMapOf<String, GroupSpec>()
        val bookmarks = mutableMapOf<String, Long>()
        val positions = mutableMapOf<String, Long>()
        val posDays = mutableMapOf<String, Int>()
        var restorePosition = true
        var rewindAfterPause = false
        var seekStepSec = 10
        var theme = ""
        var accentColor: Long? = null
        var language = "en"
        var coverMode = COVER_ALL
        var coverModeExplicit = false
        var showCoversExplicit = true
        var coverHarmonize = true
        var fxLevel = 0.5f
        var fxSound = 0.5f
        var fxHaptic = 0.5f
        var fxBackdropBlur = 0.8f
        var fxBackdropAlpha = 0.2f
        var knockSensitivity = 82
        var defaultSeeded = false
        var activePlaylist = ""
        var shownHints = mutableSetOf<String>()
        var radioSeeded = false
        var orderCounter = 0L
        var sound = SoundState()
        var power = PowerSettings()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "tracks" -> {
                    tracks = mutableListOf()
                    reader.beginArray()
                    while (reader.hasNext()) tracks.add(readTrack(reader))
                    reader.endArray()
                }
                "playlists" -> {
                    playlists = mutableListOf()
                    reader.beginArray()
                    while (reader.hasNext()) {
                        reader.beginObject()
                        var pid = ""; var pname = ""
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "id" -> pid = reader.nextString()
                                "name" -> pname = reader.nextString()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        if (pid.isNotEmpty()) playlists.add(Playlist(pid, pname))
                    }
                    reader.endArray()
                }
                "listOrder" -> {
                    listOrder = mutableListOf()
                    reader.beginArray()
                    while (reader.hasNext()) listOrder.add(reader.nextString())
                    reader.endArray()
                }
                "nameFavorites" -> nameFavorites = reader.nextString()
                "nameBookmarks" -> nameBookmarks = reader.nextString()
                "nameRadio" -> nameRadio = reader.nextString()
                "sort" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val key = reader.nextName()
                        reader.beginObject()
                        var smode = "custom"; var sdir = "asc"
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "mode" -> smode = reader.nextString()
                                "dir" -> sdir = reader.nextString()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        sort[key] = SortSpec(smode, sdir)
                    }
                    reader.endObject()
                }
                "group" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val key = reader.nextName()
                        reader.beginObject()
                        var gmode = "none"
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "mode" -> gmode = reader.nextString()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        group[key] = GroupSpec(gmode)
                    }
                    reader.endObject()
                }
                "bookmarks" -> {
                    reader.beginObject()
                    while (reader.hasNext()) bookmarks[reader.nextName()] = reader.nextLong()
                    reader.endObject()
                }
                "positions" -> {
                    reader.beginObject()
                    while (reader.hasNext()) positions[reader.nextName()] = reader.nextLong()
                    reader.endObject()
                }
                "positionDays" -> {
                    reader.beginObject()
                    while (reader.hasNext()) posDays[reader.nextName()] = reader.nextInt()
                    reader.endObject()
                }
                "restorePosition" -> restorePosition = reader.nextBoolean()
                "rewindAfterPause" -> rewindAfterPause = reader.nextBoolean()
                "seekStepSec" -> seekStepSec = reader.nextInt()
                "theme" -> theme = reader.nextString()
                "accentColor" -> accentColor = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextLong()
                "language" -> language = reader.nextString()
                "coverMode" -> { coverMode = reader.nextString(); coverModeExplicit = true }
                "showCovers" -> showCoversExplicit = reader.nextBoolean()
                "coverHarmonize" -> coverHarmonize = reader.nextBoolean()
                "fxLevel" -> fxLevel = reader.nextDouble().toFloat().coerceIn(0f, 1f)
                "fxSound" -> fxSound = reader.nextDouble().toFloat().coerceIn(0f, 1f)
                "fxHaptic" -> fxHaptic = reader.nextDouble().toFloat().coerceIn(0f, 1f)
                "fxBackdropBlur" -> fxBackdropBlur = reader.nextDouble().toFloat().coerceIn(0f, 1f)
                "fxBackdropAlpha" -> fxBackdropAlpha = reader.nextDouble().toFloat().coerceIn(0f, 1f)
                "knockSens" -> knockSensitivity = reader.nextInt().coerceIn(0, 100)
                "defaultSeeded" -> defaultSeeded = reader.nextBoolean()
                "activePlaylist" -> activePlaylist = reader.nextString()
                "shownHints" -> {
                    shownHints = mutableSetOf()
                    reader.beginArray()
                    while (reader.hasNext()) shownHints.add(reader.nextString())
                    reader.endArray()
                }
                "radioSeeded" -> radioSeeded = reader.nextBoolean()
                "orderCounter" -> orderCounter = reader.nextLong()
                "sound" -> sound = readSound(reader)
                "power" -> power = readPower(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val finalCoverMode = if (coverModeExplicit) coverMode
        else if (showCoversExplicit) COVER_ALL else COVER_OFF

        val data = AppData(
            tracks = tracks,
            playlists = playlists,
            listOrder = listOrder,
            nameFavorites = nameFavorites,
            nameBookmarks = nameBookmarks,
            nameRadio = nameRadio,
            sort = sort,
            group = group,
            bookmarks = bookmarks,
            restorePosition = restorePosition,
            rewindAfterPause = rewindAfterPause,
            seekStepSec = seekStepSec,
            theme = theme,
            accentColor = accentColor,
            language = language,
            coverMode = finalCoverMode,
            coverHarmonize = coverHarmonize,
            fxLevel = fxLevel,
            fxSound = fxSound,
            fxHaptic = fxHaptic,
            fxBackdropBlur = fxBackdropBlur,
            fxBackdropAlpha = fxBackdropAlpha,
            knockSensitivity = knockSensitivity,
            defaultSeeded = defaultSeeded,
            activePlaylist = activePlaylist,
            shownHints = shownHints,
            radioSeeded = radioSeeded,
            sound = sound,
            power = power,
            orderCounter = if (orderCounter != 0L) orderCounter else tracks.size.toLong()
        )
        return Loaded(data, positions, posDays)
    }

    private fun readTrack(reader: JsonReader): Track {
        var id = ""; var uri = ""; var title = ""; var artist = ""; var folder = ""
        var durationMs = 0L; var size = 0L; var favorite = false
        var playlists = mutableListOf<String>(); var order = 0L
        var isRadio = false; var hasArt = false

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextString()
                "uri" -> uri = reader.nextString()
                "title" -> title = reader.nextString()
                "artist" -> artist = reader.nextString()
                "folder" -> folder = reader.nextString()
                "durationMs" -> durationMs = reader.nextLong()
                "size" -> size = reader.nextLong()
                "favorite" -> favorite = reader.nextBoolean()
                "playlists" -> {
                    playlists = mutableListOf()
                    reader.beginArray()
                    while (reader.hasNext()) playlists.add(reader.nextString())
                    reader.endArray()
                }
                "order" -> order = reader.nextLong()
                "isRadio" -> isRadio = reader.nextBoolean()
                "hasArt" -> hasArt = reader.nextBoolean()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return Track(id, uri, title, artist, folder, durationMs, size, favorite, playlists.distinct(), order, isRadio, hasArt)
    }

    private fun readSound(reader: JsonReader): SoundState {
        var eqDevice = OUT_SPEAKER
        var eqDeviceName = ""
        val eqProfiles = mutableMapOf<String, EqProfile>()
        var eqEnabled = false
        var eqPreset = "manual"
        var eqBands = List(10) { 0 }
        var presets = mutableListOf<EqPreset>()
        var presetOrder = mutableListOf<String>()
        var soundEnabled = false
        var tone = 1f
        var speed = 1f
        var balance = 0
        var preamp = 0
        var volumeFineEnabled = false
        var volumeTrim = 0
        var fxBackup = 0

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "eqDevice" -> eqDevice = reader.nextString().ifBlank { OUT_SPEAKER }
                "eqDeviceName" -> eqDeviceName = reader.nextString()
                "eqProfiles" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val pk = reader.nextName()
                        reader.beginObject()
                        var penabled = false; var ppreset = "manual"; var pbands = List(10) { 0 }
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "enabled" -> penabled = reader.nextBoolean()
                                "preset" -> ppreset = reader.nextString()
                                "bands" -> {
                                    val b = mutableListOf<Int>()
                                    reader.beginArray()
                                    while (reader.hasNext()) b.add(reader.nextInt())
                                    reader.endArray()
                                    if (b.size == 10) pbands = b
                                }
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        eqProfiles[pk] = EqProfile(penabled, ppreset, pbands)
                    }
                    reader.endObject()
                }
                "eqEnabled" -> eqEnabled = reader.nextBoolean()
                "eqPreset" -> eqPreset = reader.nextString()
                "eqBands" -> {
                    val b = mutableListOf<Int>()
                    reader.beginArray()
                    while (reader.hasNext()) b.add(reader.nextInt())
                    reader.endArray()
                    if (b.size == 10) eqBands = b
                }
                "presets" -> {
                    presets = mutableListOf()
                    reader.beginArray()
                    while (reader.hasNext()) {
                        reader.beginObject()
                        var pid = ""; var pname = ""; var pbands: List<Int>? = null
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "id" -> pid = reader.nextString()
                                "name" -> pname = reader.nextString()
                                "bands" -> {
                                    val b = mutableListOf<Int>()
                                    reader.beginArray()
                                    while (reader.hasNext()) b.add(reader.nextInt())
                                    reader.endArray()
                                    pbands = b
                                }
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        if (pid.isNotEmpty()) presets.add(EqPreset(pid, pname, pbands))
                    }
                    reader.endArray()
                }
                "presetOrder" -> {
                    presetOrder = mutableListOf()
                    reader.beginArray()
                    while (reader.hasNext()) presetOrder.add(reader.nextString())
                    reader.endArray()
                }
                "soundEnabled" -> soundEnabled = reader.nextBoolean()
                "tone" -> tone = reader.nextDouble().toFloat().coerceIn(0.5f, 1.5f)
                "speed" -> speed = reader.nextDouble().toFloat().coerceIn(0.5f, 3f)
                "speedSt" -> {
                    val st = reader.nextInt()
                    speed = Math.pow(2.0, st / 24.0).toFloat().coerceIn(0.5f, 3f)
                }
                "balance" -> balance = reader.nextInt()
                "preamp" -> preamp = reader.nextInt()
                "volumeFineEnabled" -> volumeFineEnabled = reader.nextBoolean()
                "volumeTrim" -> volumeTrim = reader.nextInt()
                "fxBackup" -> fxBackup = reader.nextInt()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val finalPresets = if (presets.isEmpty()) defaultPresets() else presets
        val validOrder = presetOrder.filter { id -> finalPresets.any { it.id == id } }
        val fullOrder = if (validOrder.isEmpty()) finalPresets.map { it.id }
        else validOrder + finalPresets.map { it.id }.filter { !validOrder.contains(it) }

        return SoundState(
            eqDevice = eqDevice,
            eqDeviceName = eqDeviceName,
            eqProfiles = eqProfiles,
            eqEnabled = eqEnabled,
            eqPreset = eqPreset,
            eqBands = eqBands,
            presets = finalPresets,
            presetOrder = fullOrder,
            soundEnabled = soundEnabled,
            tone = tone,
            speed = speed,
            balance = balance,
            preamp = preamp,
            volumeFineEnabled = volumeFineEnabled,
            volumeTrim = volumeTrim,
            fxBackup = fxBackup
        )
    }

    private fun readPower(reader: JsonReader): PowerSettings {
        var profile = "balanced"; var cpu = 1; var screen = 1; var network = 1; var background = 1
        var autoBattery = false; var autoBatteryPercent = 20; var autoSystemSaver = false; var offload = false

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "profile" -> profile = reader.nextString()
                "cpu" -> cpu = reader.nextInt()
                "screen" -> screen = reader.nextInt()
                "network" -> network = reader.nextInt()
                "background" -> background = reader.nextInt()
                "autoBattery" -> autoBattery = reader.nextBoolean()
                "autoBatteryPercent" -> autoBatteryPercent = reader.nextInt()
                "autoSystemSaver" -> autoSystemSaver = reader.nextBoolean()
                "offload" -> offload = reader.nextBoolean()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return PowerSettings(profile, cpu, screen, network, background, autoBattery, autoBatteryPercent, autoSystemSaver, offload)
    }

    private fun tryLegacyLoad(f: File): Loaded {
        return try {
            fromJson(f.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            Loaded(AppData(), mutableMapOf())
        }
    }

    private fun tryLegacyFromJson(text: String): Loaded {
        return try {
            val o = JSONObject(text)
            val tracks = mutableListOf<Track>()
            o.optJSONArray("tracks")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val t = arr.getJSONObject(i)
                    tracks += Track(
                        id = t.getString("id"),
                        uri = t.getString("uri"),
                        title = t.optString("title"),
                        artist = t.optString("artist"),
                        folder = t.optString("folder"),
                        durationMs = t.optLong("durationMs"),
                        size = t.optLong("size"),
                        favorite = t.optBoolean("favorite"),
                        playlists = (t.optJSONArray("playlists")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()).distinct(),
                        order = t.optLong("order"),
                        isRadio = t.optBoolean("isRadio"),
                        hasArt = t.optBoolean("hasArt")
                    )
                }
            }
            Loaded(AppData(tracks = tracks), mutableMapOf())
        } catch (e: Exception) {
            Loaded(AppData(), mutableMapOf())
        }
    }
}
