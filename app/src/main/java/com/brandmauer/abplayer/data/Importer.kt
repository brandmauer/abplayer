package com.brandmauer.abplayer.data

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContract
import androidx.documentfile.provider.DocumentFile
import com.brandmauer.abplayer.util.TextFix
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Выбор нескольких аудиофайлов (с правом на запись, чтобы работало «Удалить файл с диска»). */
class OpenAudioDocuments : ActivityResultContract<Unit, List<Uri>>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (resultCode != Activity.RESULT_OK || intent == null) return emptyList()
        val out = mutableListOf<Uri>()
        val clip = intent.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { out += it }
        } else {
            intent.data?.let { out += it }
        }
        return out
    }
}

/** Выбор одного изображения — для собственной обложки радиостанции/трека. */
class OpenSingleImageDocument : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

/** Выбор одного файла плейлиста m3u/m3u8 (mime у провайдеров ненадёжен, поэтому берём любой тип). */
class OpenPlaylistDocument : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("audio/x-mpegurl", "audio/mpegurl", "application/vnd.apple.mpegurl", "application/x-mpegurl", "text/plain")
            )
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

/** Выбор папки с музыкой (обход вложенных папок). */
class OpenAudioFolder : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

object Importer {

    private val AUDIO_EXT = Regex(".*\\.(mp3|m4a|m4b|aac|wav|ogg|oga|opus|flac|wma|amr|mka)$", RegexOption.IGNORE_CASE)

    /** Сохраняем доступ к файлу/папке между запусками. */
    fun persist(context: Context, uri: Uri) {
        val cr = context.contentResolver
        try {
            cr.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            try {
                cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e2: Exception) {
                // провайдер не поддерживает постоянный доступ — файл будет доступен до перезапуска
            }
        }
    }

    // ------------------------------------------------------------------ пути и ключи файлов

    /**
     * Ключ файла для поиска дублей: один и тот же файл, добавленный по-разному (как файл и как
     * часть папки), имеет разные строки uri, но одинаковые провайдер + идентификатор документа.
     */
    fun keyOf(uri: Uri): String = try {
        uri.authority.orEmpty() + "|" + DocumentsContract.getDocumentId(uri)
    } catch (e: Exception) {
        uri.toString()
    }

    /** Убирает корень хранилища: /storage/emulated/0/Music/a.mp3 → Music/a.mp3, /storage/1A2B-3C4D/x → SD-карта/x. */
    private fun prettyFsPath(path: String): String {
        val p = path.trim()
        val prefixes = listOf("/storage/emulated/0/", "/storage/self/primary/", "/sdcard/", "/mnt/sdcard/")
        for (pre in prefixes) if (p.startsWith(pre)) return p.removePrefix(pre)
        if (p.startsWith("/storage/")) {
            val rest = p.removePrefix("/storage/")
            val vol = rest.substringBefore('/')
            val tail = rest.substringAfter('/', "")
            if (vol.isNotEmpty() && vol != "emulated") return if (tail.isEmpty()) "SD-карта" else "SD-карта/$tail"
        }
        return p.trimStart('/')
    }

    private fun mediaStorePath(context: Context, uri: Uri): String? {
        try {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val d = c.getString(0)
                    if (!d.isNullOrBlank()) return prettyFsPath(d)
                }
            }
        } catch (e: Exception) {
        }
        try {
            context.contentResolver.query(
                uri, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DISPLAY_NAME), null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val rel = c.getString(0).orEmpty()
                    val name = c.getString(1).orEmpty()
                    if (name.isNotEmpty()) return rel.trimEnd('/').let { if (it.isEmpty()) name else "$it/$name" }
                }
            }
        } catch (e: Exception) {
        }
        return null
    }

    /**
     * Понятный человеку путь к файлу без кодировки и служебных префиксов:
     * «Music/Books/глава 1.mp3», для карты памяти — «SD-карта/Music/…», для потоков — декодированная ссылка.
     */
    fun readablePath(context: Context, uriString: String): String {
        val uri = try { Uri.parse(uriString) } catch (e: Exception) { return uriString }
        when (uri.scheme) {
            "file" -> return prettyFsPath(uri.path ?: Uri.decode(uriString))
            "content" -> Unit
            else -> return Uri.decode(uriString)
        }
        try {
            val id = DocumentsContract.getDocumentId(uri)
            when (uri.authority) {
                "com.android.externalstorage.documents" -> {
                    val vol = id.substringBefore(':', "")
                    val rel = id.substringAfter(':', "")
                    return when {
                        rel.isEmpty() -> id
                        vol == "primary" -> rel
                        else -> "SD-карта/$rel"
                    }
                }
                "com.android.providers.downloads.documents" -> {
                    if (id.startsWith("raw:")) return prettyFsPath(id.removePrefix("raw:"))
                }
                "com.android.providers.media.documents" -> {
                    val num = id.substringAfter(':', "").toLongOrNull()
                    if (num != null) {
                        val mu = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, num)
                        mediaStorePath(context, mu)?.let { return it }
                    }
                }
            }
        } catch (e: Exception) {
        }
        mediaStorePath(context, uri)?.let { return it }
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getString(0)
                    if (!n.isNullOrBlank()) return n
                }
            }
        } catch (e: Exception) {
        }
        return Uri.decode(uri.lastPathSegment ?: uriString).substringAfterLast('/')
    }

    /** Папка файла для группировки «по папкам»: родительская часть читаемого пути. */
    fun folderFromUri(context: Context, uri: Uri): String {
        return try {
            val full = readablePath(context, uri.toString())
            if (full.contains('/')) full.substringBeforeLast('/') else ""
        } catch (e: Exception) {
            ""
        }
    }

    /** Естественная сортировка имён: «track2» раньше «track10». */
    private fun naturalCompare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var ei = i
                while (ei < a.length && a[ei].isDigit()) ei++
                var ej = j
                while (ej < b.length && b[ej].isDigit()) ej++
                val na = a.substring(i, ei).trimStart('0')
                val nb = b.substring(j, ej).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val c = na.compareTo(nb)
                if (c != 0) return c
                i = ei
                j = ej
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return c
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }

    data class FastMeta(val title: String, val artist: String, val durationMs: Long, val size: Long)
    data class ScannedFile(val uri: Uri, val relativeFolder: String, val name: String, val size: Long)

    /** Быстрое чтение метаданных всех аудиофайлов из MediaStore за 1 SQL-запрос. */
    fun queryMediaStoreMap(context: Context): Map<String, FastMeta> {
        val map = HashMap<String, FastMeta>()
        try {
            val proj = arrayOf(
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION
            )
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                proj, null, null, null
            )?.use { c ->
                val nameIdx = c.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val titleIdx = c.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistIdx = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val durIdx = c.getColumnIndex(MediaStore.Audio.Media.DURATION)
                while (c.moveToNext()) {
                    val name = if (nameIdx >= 0) c.getString(nameIdx).orEmpty() else ""
                    val size = if (sizeIdx >= 0 && !c.isNull(sizeIdx)) c.getLong(sizeIdx) else 0L
                    val title = if (titleIdx >= 0) c.getString(titleIdx).orEmpty() else ""
                    val artist = if (artistIdx >= 0) c.getString(artistIdx).orEmpty() else ""
                    val dur = if (durIdx >= 0 && !c.isNull(durIdx)) c.getLong(durIdx) else 0L
                    if (name.isNotEmpty()) {
                        val meta = FastMeta(
                            title = TextFix.fixMojibake(title.trim()),
                            artist = TextFix.fixMojibake(artist.trim()),
                            durationMs = dur,
                            size = size
                        )
                        map["${name.lowercase()}_$size"] = meta
                        map[name.lowercase()] = meta
                    }
                }
            }
        } catch (e: Exception) {
        }
        return map
    }

    /**
     * Рекурсивный обход папки с извлечением имён и размеров прямо из каталога.
     */
    fun scanTree(
        context: Context,
        treeUri: Uri,
        shouldStop: () -> Boolean = { false },
        onFound: (Int) -> Unit = {}
    ): List<ScannedFile> {
        val rootId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            return emptyList()
        }
        val rootName = try {
            DocumentFile.fromTreeUri(context, treeUri)?.name
        } catch (e: Exception) {
            null
        } ?: ""
        val cr = context.contentResolver
        val proj = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        val out = mutableListOf<ScannedFile>()

        fun walk(docId: String, path: String) {
            if (shouldStop()) return
            val dirs = ArrayList<Triple<String, String, String>>()   // (имя, id, путь)
            val files = ArrayList<Triple<String, String, Long>>()    // (имя, id, размер)
            try {
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                cr.query(children, proj, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        if (shouldStop()) break
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: ""
                        val mime = c.getString(2) ?: ""
                        val size = if (c.isNull(3)) 0L else c.getLong(3)
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            dirs += Triple(name, id, if (path.isEmpty()) name else "$path/$name")
                        } else if (mime.startsWith("audio/") || AUDIO_EXT.matches(name)) {
                            files += Triple(name, id, size)
                        }
                    }
                }
            } catch (e: Exception) {
            }
            files.sortWith(Comparator { x, y -> naturalCompare(x.first, y.first) })
            for ((name, id, size) in files) {
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                out += ScannedFile(docUri, path, name, size)
            }
            onFound(out.size)
            dirs.sortWith(Comparator { x, y -> naturalCompare(x.first, y.first) })
            for ((_, id, p) in dirs) {
                if (shouldStop()) return
                walk(id, p)
            }
        }
        walk(rootId, rootName)
        return out
    }

    /** Читает название, исполнителя, длительность и размер. null — если это не аудио. */
    fun describe(
        context: Context, uri: Uri, folder: String, id: String, order: Long,
        knownName: String = "",
        knownSize: Long = 0L,
        artCache: ConcurrentHashMap<Long, String>? = null,
        mediaStoreMap: Map<String, FastMeta>? = null
    ): Track? {
        var name = knownName
        var size = knownSize

        // Извлекаем имя из URI без обращения к ContentResolver
        if (name.isEmpty()) {
            try {
                val docId = DocumentsContract.getDocumentId(uri)
                val rawName = docId.substringAfterLast('/').substringAfterLast(':')
                if (rawName.isNotBlank() && rawName.contains('.')) name = rawName
            } catch (e: Exception) {
            }
            if (name.isEmpty()) {
                name = Uri.decode(uri.lastPathSegment ?: "").substringAfterLast('/')
            }
        }

        val fallbackTitle = name.replace(Regex("\\.[^.]+$"), "").ifEmpty { "Без названия" }

        // Мгновенная проверка в MediaStore (0 запросов к ContentResolver!)
        if (name.isNotEmpty() && mediaStoreMap != null) {
            val cached = mediaStoreMap["${name.lowercase()}_$size"] ?: mediaStoreMap[name.lowercase()]
            if (cached != null) {
                return Track(
                    id = id,
                    uri = uri.toString(),
                    title = cached.title.ifEmpty { fallbackTitle },
                    artist = cached.artist.ifEmpty { "Локальный файл" },
                    folder = folder,
                    durationMs = cached.durationMs,
                    size = if (cached.size > 0) cached.size else size,
                    order = order,
                    hasArt = false
                )
            }
        }

        // Запрос к ContentResolver ТОЛЬКО если имя или размер всё ещё не известны
        if (size == 0L || name.isEmpty()) {
            try {
                context.contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val si = c.getColumnIndex(OpenableColumns.SIZE)
                        if (ni >= 0 && name.isEmpty()) name = c.getString(ni) ?: ""
                        if (si >= 0 && !c.isNull(si) && size == 0L) size = c.getLong(si)
                    }
                }
            } catch (e: Exception) {
            }
        }

        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, uri)
            val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim().orEmpty()
                .let { TextFix.fixMojibake(it) }
            val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim().orEmpty()
                .let { TextFix.fixMojibake(it) }
            val hasArt = saveEmbeddedArt(context, mmr, id, artCache)
            return Track(
                id = id,
                uri = uri.toString(),
                title = title.ifEmpty { fallbackTitle },
                artist = artist.ifEmpty { "Локальный файл" },
                folder = folder,
                durationMs = dur,
                size = size,
                order = order,
                hasArt = hasArt
            )
        } catch (e: Exception) {
            return null
        } finally {
            try {
                mmr.release()
            } catch (e: Exception) {
            }
        }
    }

    /** Каталог с сохранёнными обложками альбомов, извлечёнными из файлов при добавлении. */
    fun coversDir(context: Context): File {
        val dir = File(context.filesDir, "covers")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun coverFile(context: Context, trackId: String): File = File(coversDir(context), "$trackId.jpg")

    /** Обложка нужна только для показа на экране — уменьшаем крупные изображения при декодировании,
     *  а не после (иначе декодирование огромной встроенной картинки может съесть всю память
     *  и завершиться ошибкой — из-за этого у части треков обложка не показывалась). */
    private const val COVER_MAX_SIDE = 640

    private fun sampleSizeFor(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= maxSide || h / 2 >= maxSide) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    private fun decodeScaledBytes(bytes: ByteArray): android.graphics.Bitmap? {
        return try {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val opts = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, COVER_MAX_SIDE)
            }
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
            if (bmp.width > COVER_MAX_SIDE || bmp.height > COVER_MAX_SIDE) {
                val ratio = minOf(COVER_MAX_SIDE.toFloat() / bmp.width, COVER_MAX_SIDE.toFloat() / bmp.height)
                android.graphics.Bitmap.createScaledBitmap(
                    bmp, (bmp.width * ratio).toInt().coerceAtLeast(1), (bmp.height * ratio).toInt().coerceAtLeast(1), true
                )
            } else bmp
        } catch (t: Throwable) {
            null
        }
    }

    private fun saveBitmap(context: Context, bmp: android.graphics.Bitmap, id: String): Boolean {
        return try {
            coverFile(context, id).outputStream().use { out ->
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 87, out)
            }
            true
        } catch (t: Throwable) {
            false
        }
    }

    /** Извлекает встроенную обложку и сохраняет её рядом с данными приложения. true — обложка есть. */
    private fun saveEmbeddedArt(
        context: Context, mmr: MediaMetadataRetriever, id: String,
        artCache: java.util.concurrent.ConcurrentHashMap<Long, String>? = null
    ): Boolean {
        val bytes = try {
            mmr.embeddedPicture
        } catch (t: Throwable) {
            null
        } ?: return false
        if (artCache != null) {
            // Одна и та же обложка у десятков глав книги: декодируем и сжимаем её один раз,
            // остальным трекам просто копируем уже готовый файл.
            val crc = java.util.zip.CRC32().also { it.update(bytes) }.value
            val key = (bytes.size.toLong() shl 32) xor crc
            val srcId = artCache[key]
            if (srcId != null) {
                val src = coverFile(context, srcId)
                if (src.exists() && src.length() > 0L) {
                    try {
                        src.copyTo(coverFile(context, id), overwrite = true)
                        return true
                    } catch (t: Throwable) {
                        // не вышло скопировать — обработаем обычным путём
                    }
                }
            }
            val bmp = decodeScaledBytes(bytes) ?: return false
            val ok = saveBitmap(context, bmp, id)
            if (ok) artCache.putIfAbsent(key, id)
            return ok
        }
        val bmp = decodeScaledBytes(bytes) ?: return false
        return saveBitmap(context, bmp, id)
    }

    /** Пробует достать встроенную обложку у уже добавленного трека (если при добавлении не вышло). true — сохранена. */
    fun ensureArt(context: Context, uriString: String, id: String): Boolean {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, Uri.parse(uriString))
            saveEmbeddedArt(context, mmr, id)
        } catch (e: Exception) {
            false
        } finally {
            try {
                mmr.release()
            } catch (e: Exception) {
            }
        }
    }

    /** Сохраняет изображение, выбранное пользователем системным пикером, как обложку трека/станции. */
    fun saveCoverFromUri(context: Context, uri: Uri, id: String): Boolean {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (t: Throwable) {
            null
        } ?: return false
        val bmp = decodeScaledBytes(bytes) ?: return false
        return saveBitmap(context, bmp, id)
    }

    /** Сохраняет обложку из байтов (встроенная картинка, которую нашёл сам плеер). true — сохранено. */
    fun saveCoverFromBytes(context: Context, bytes: ByteArray, id: String): Boolean {
        val bmp = decodeScaledBytes(bytes) ?: return false
        return saveBitmap(context, bmp, id)
    }

    /** Удаляет сохранённую обложку трека (при удалении самого трека из библиотеки). */
    fun deleteArt(context: Context, trackId: String) {
        try {
            coverFile(context, trackId).delete()
        } catch (e: Exception) {
        }
    }

    /** content:// ссылка на сохранённую обложку (для уведомления воспроизведения). */
    fun artworkContentUri(context: Context, trackId: String, hasArt: Boolean): Uri? {
        if (!hasArt) return null
        val f = coverFile(context, trackId)
        if (!f.exists()) return null
        return try {
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
        } catch (t: Throwable) {
            null
        }
    }

    /** Подробная информация о треке для шторки «Информация о треке» (метка → значение). */
    fun trackInfo(context: Context, t: Track): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        out += "Название" to t.title
        out += "Исполнитель" to t.artist
        if (t.isRadio) {
            out += "Тип" to "Радиопоток"
            out += "Адрес" to t.uri
            return out
        }
        var codec = "—"
        var trackNum: String? = null   // из тега CD_TRACK_NUMBER (ID3 TRCK / Vorbis TRACKNUMBER / MP4 trkn)
        var sampleRate: String? = null   // из заголовка потока (MediaMetadataRetriever.SAMPLERATE с API 31, ниже — MediaExtractor)
        var bitrate = "—"
        try {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(context, Uri.parse(t.uri))
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.let { if (it.isNotBlank()) trackNum = it }
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.let { if (it.isNotBlank()) codec = it }
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.let { if (it.isNotBlank()) bitrate = "${it.toLongOrNull()?.div(1000) ?: it} кбит/с" }
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.let { if (it.isNotBlank()) sampleRate = "$it Гц" }
                }
            } finally {
                try { mmr.release() } catch (e: Exception) {}
            }
        } catch (t2: Throwable) {
        }
        if (sampleRate == null) {
            try {
                val ex = android.media.MediaExtractor()
                try {
                    ex.setDataSource(context, Uri.parse(t.uri), null)
                    for (i in 0 until ex.trackCount) {
                        val f = ex.getTrackFormat(i)
                        if ((f.getString(android.media.MediaFormat.KEY_MIME) ?: "").startsWith("audio/") &&
                            f.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE)
                        ) {
                            sampleRate = "${f.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)} Гц"
                            break
                        }
                    }
                } finally {
                    try { ex.release() } catch (e: Exception) {}
                }
            } catch (t3: Throwable) {
            }
        }
        val ext = t.uri.substringBefore('?').substringAfterLast('/').substringAfterLast('.', "").trim()
        // строки «Номер» и «Дискретизация» показываем только если значение найдено
        trackNum?.let { out += "Номер" to it }
        out += "Тип файла" to (if (ext.isNotEmpty() && ext.length <= 5) ext.uppercase() else "—")
        out += "Кодек" to codec
        out += "Продолжительность" to com.brandmauer.abplayer.util.fmtDurLong(t.durationMs)
        out += "Битрейт" to bitrate
        sampleRate?.let { out += "Дискретизация" to it }
        out += "Размер" to com.brandmauer.abplayer.util.fmtSize(t.size)
        out += "Обложка" to (if (t.hasArt) "Есть" else "Нет")
        out += "Путь к файлу" to readablePath(context, t.uri)
        return out
    }

    /** Текст локального файла (для чтения m3u/m3u8, выбранных через системный пикер). */
    fun readText(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return ""
        return com.brandmauer.abplayer.util.TextFix.decodeBytes(bytes)
    }

    /**
     * Файл на месте? true — да; false — точно удалён или перемещён; null — проверить не удалось
     * (нет доступа, карта памяти не подключена и т. п.): такие файлы не считаем потерянными.
     */
    fun exists(context: Context, uri: Uri): Boolean? {
        return try {
            when (uri.scheme) {
                "file" -> uri.path?.let { java.io.File(it).exists() }
                "content" -> {
                    val cr = context.contentResolver
                    var answer: Boolean? = null
                    try {
                        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                            answer = c.moveToFirst()
                        }
                    } catch (e: SecurityException) {
                        return null
                    } catch (e: Exception) {
                    }
                    if (answer != null) return answer
                    // поставщик не вернул курсор — окончательно проверяем попыткой открыть файл
                    try {
                        cr.openAssetFileDescriptor(uri, "r")?.use { return true }
                        null
                    } catch (e: java.io.FileNotFoundException) {
                        false
                    } catch (e: Exception) {
                        null
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Удаление файла с диска. true — файл действительно удалён. */
    fun deleteFromDisk(context: Context, uri: Uri): Boolean {
        return try {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (e: Exception) {
            try {
                context.contentResolver.delete(uri, null, null) > 0
            } catch (e2: Exception) {
                false
            }
        }
    }
}
