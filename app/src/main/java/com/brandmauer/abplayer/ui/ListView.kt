package com.brandmauer.abplayer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brandmauer.abplayer.Hub
import com.brandmauer.abplayer.Sheet
import com.brandmauer.abplayer.data.BOOKMARKS
import com.brandmauer.abplayer.data.FAVORITES
import com.brandmauer.abplayer.data.RADIO
import com.brandmauer.abplayer.i18n.I18n
import com.brandmauer.abplayer.data.Track
import com.brandmauer.abplayer.util.fmtDurLong
import com.brandmauer.abplayer.util.fmtSize
import com.brandmauer.abplayer.util.bookmarkWord
import com.brandmauer.abplayer.util.trackWord

private sealed class Entry {
    class Head(val name: String, val count: Int) : Entry()
    class Row(val track: Track, val index: Int) : Entry()
}

private fun buildEntries(hub: Hub, view: String, list: List<Track>): List<Entry> {
    val mode = hub.getGroup(hub.viewPlid(view)).mode
    if (mode == "none") {
        return list.mapIndexed { i, t -> Entry.Row(t, i) }
    }
    fun nameOf(t: Track) = if (mode == "type") hub.typeOf(t) else (if (t.folder.isEmpty()) "Без папки" else t.folder)
    val counts = HashMap<String, Int>()
    list.forEach { counts[nameOf(it)] = (counts[nameOf(it)] ?: 0) + 1 }
    val out = ArrayList<Entry>()
    var prev: String? = null
    list.forEachIndexed { i, t ->
        val k = nameOf(t)
        if (k != prev) out.add(Entry.Head(k, counts[k] ?: 0))
        prev = k
        out.add(Entry.Row(t, i))
    }
    return out
}

/**
 * Экран списка треков. Один и тот же вид используется для плейлиста (страница справа),
 * «Избранного» и «Закладок» (шторки): заголовок, число файлов, +/поиск/⋮, одинаковое меню.
 */
@Composable
fun ListView(hub: Hub, view: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val data = hub.data
    val plid = hub.viewPlid(view)
    if (!hub.listExists(plid)) {
        Box(modifier)
        return
    }
    val vs = hub.viewState(view)
    val all = hub.playlistTracks(plid)
    val list = hub.viewList(view)
    val allEntries = buildEntries(hub, view, list)
    val folderNames = allEntries.filterIsInstance<Entry.Head>().map { it.name }
    // при поиске свёрнутость игнорируем, чтобы найденные треки не прятались
    val searching = vs.search.trim().isNotEmpty()
    val entries = if (searching || vs.collapsed.isEmpty()) allEntries else {
        val out = ArrayList<Entry>()
        var hidden = false
        for (e in allEntries) {
            when (e) {
                is Entry.Head -> { hidden = vs.collapsed.contains(e.name); out.add(e) }
                is Entry.Row -> if (!hidden) out.add(e)
            }
        }
        out
    }
    val playingId = hub.currentTrackId
    val groupByFolder = hub.getGroup(plid).mode == "folder"
    val isBm = plid == BOOKMARKS
    val isRadioView = plid == RADIO

    Column(modifier.fillMaxSize()) {
        // ---- заголовок: название, число файлов, действия
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onBack != null) BackBtn(onBack)
                Column(Modifier.weight(1f)) {
                    // название + треугольник справа: выпадающий список всех плейлистов
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Txt(
                            hub.listName(plid), 19f, FontWeight.ExtraBold, c.text, maxLines = 1, letterSpacing = -0.2f,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        PlaylistPicker(hub, view, plid)
                    }
                    Txt(
                        if (isBm) "${all.size} ${bookmarkWord(all.size)}"
                        else "${all.size} ${trackWord(all.size)} · ${fmtDurLong(all.sumOf { it.durationMs })}",
                        12f, color = c.textDim, modifier = Modifier.padding(top = 2.dp), maxLines = 1
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconBtn(Ic.plus, {
                    when {
                        isBm -> hub.addBookmark()
                        plid == RADIO -> hub.requestRadioAdd()
                        else -> hub.requestAdd(view)
                    }
                })
                IconBtn(Ic.search, { hub.toggleSearch(view) }, active = vs.searchOpen, stateText = if (vs.searchOpen) "Включено" else null)
                IconBtn(Ic.dots, { hub.openSheet(Sheet.PlaylistMenu(plid, view)) })
            }
        }

        // ---- прогресс добавления файлов: тонкая полоска под заголовком, чтобы не казалось, что всё зависло
        val progress = hub.importProgress
        if (progress != null && progress.second > 0) {
            val frac = (progress.first.toFloat() / progress.second).coerceIn(0f, 1f)
            Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(1.5.dp)).background(c.surface3)) {
                    Box(Modifier.fillMaxWidth(frac).height(4.dp).background(c.accent))
                }
                Txt(
                    "${progress.first} / ${progress.second}", 11f, color = c.textFaint,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // ---- поиск
        if (vs.searchOpen) {
            val focus = remember { FocusRequester() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .height(42.dp)
                    .border(1.dp, c.border, RoundedCornerShape(9.dp))
                    .clip(RoundedCornerShape(9.dp))
                    .background(c.surface2)
                    .padding(start = 14.dp, end = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        BasicTextField(
                            value = vs.search,
                            onValueChange = { hub.setSearch(view, it) },
                            singleLine = true,
                            textStyle = TextStyle(color = c.text, fontSize = 14.sp),
                            cursorBrush = SolidColor(c.accent),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus)
                        )
                        if (vs.search.isEmpty()) Txt("Поиск треков…", 14f, color = c.textFaint)
                    }
                    // иконка очистки поля поиска
                    if (vs.search.isNotEmpty()) {
                        IconBtn(Ic.close, { hub.setSearch(view, "") }, size = 34.dp, iconSize = 18.dp, description = "Очистить поиск")
                    }
                }
            }
            LaunchedEffect(Unit) { focus.requestFocus() }
        }

        // ---- панель выбора
        if (vs.selectMode) {
            val n = vs.selectedIds.size
            val cnt = if (n > 0) " ($n)" else ""
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BulkBtn(Ic.check2, "Все") { hub.selectAll(view) }
                // как в меню файла: «Удалить из плейлиста» с крестиком (файлы с диска не удаляются)
                when {
                    isBm -> BulkBtn(Ic.close, "Удалить закладки$cnt") { hub.bulkDelete(view) }
                    isRadioView -> BulkBtn(Ic.trash, "Удалить$cnt") { hub.bulkDelete(view) }
                    plid == FAVORITES -> BulkBtn(Ic.close, "Убрать из избранного$cnt") { hub.bulkRemoveFromFavorites(view) }
                    else -> BulkBtn(Ic.close, "Удалить из плейлиста$cnt") { hub.bulkRemoveFromPlaylist(view) }
                }
                BulkBtn(Ic.back, "Отмена") { hub.exitSelect(view) }
                // «Удалить с диска» — для обычных плейлистов и «Избранного» (в закладках и радио такой кнопки нет)
                if (!isBm && !isRadioView) BulkBtn(Ic.trash, "Удалить с диска$cnt") { hub.bulkDelete(view) }
                BulkBtn(Ic.folder, "В плейлист$cnt") { hub.bulkAddToPlaylist(view) }
            }
        }

        // ---- список
        if (entries.isEmpty()) {
            EmptyState(
                icon = if (isBm) Ic.bookmark else if (isRadioView) Ic.volume else Ic.music,
                text = if (vs.search.trim().isNotEmpty()) "Ничего не найдено"
                else if (isBm) "Закладок пока нет. Нажмите «+», чтобы добавить закладку на текущем месте трека."
                else if (isRadioView) "Станций пока нет. Нажмите «+», чтобы добавить по ссылке или файлом m3u/m3u8."
                else "Пока нет треков. Нажмите «+», чтобы добавить файлы."
            )
        } else {
            // список сжат слева и справа на толщину полоски выделения (3dp)
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 3.dp).rubberBand()) {
                items(
                    entries,
                    key = { e ->
                        when (e) {
                            is Entry.Head -> "h:" + e.name
                            is Entry.Row -> "r:" + e.track.id
                        }
                    }
                ) { e ->
                    when (e) {
                        is Entry.Head -> FolderHead(
                            c = c, name = e.name, count = e.count,
                            isPath = groupByFolder && e.name != "Без папки",
                            collapsed = !searching && vs.collapsed.contains(e.name),
                            onToggle = { hub.toggleFolder(view, e.name) },
                            onToggleAll = { hub.toggleAllFolders(view, folderNames) }
                        )
                        is Entry.Row -> {
                            val t = e.track
                            // размер файла теперь справа (под временем), в подписи слева — только автор
                            val sub = if (isBm) {
                                t.artist
                            } else if (t.isRadio) {
                                t.uri
                            } else {
                                t.artist
                            }
                            TrackRow(
                                c = c, hub = hub, t = t, index = e.index, view = view, sub = sub,
                                playing = t.id == playingId,
                                selectMode = vs.selectMode,
                                checked = vs.selectedIds.contains(t.id),
                                anySelected = vs.selectMode && vs.selectedIds.isNotEmpty()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BulkBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    val c = LocalColors.current
    Pressable(
        modifier = Modifier.height(38.dp),
        normal = c.surface2,
        pressed = c.surface3,
        shape = RoundedCornerShape(9.dp),
        contentAlignment = Alignment.Center,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // чуть приглушены, как значки верхнего ряда
            AbIcon(icon, c.textDim, 18.dp)
            Txt(text, 12f, FontWeight.SemiBold, c.textDim, maxLines = 1)
        }
    }
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val c = LocalColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AbIcon(icon, c.textFaint, 48.dp, Modifier.alpha(0.5f))
        Box(Modifier.height(10.dp))
        Txt(text, 13f, color = c.textFaint, textAlign = TextAlign.Center)
    }
}

/**
 * Подбирает видимую часть пути папки: последний уровень (сама папка) остаётся всегда, слева от него отбрасываются
 * самые верхние уровни, пока строка не поместится в [maxPx]. [measure] — ширина строки в пикселях.
 */
private fun fitFolderPath(full: String, maxPx: Float, measure: (String) -> Float): String {
    val parts = full.split('/').filter { it.isNotEmpty() }
    if (parts.size <= 1) return full
    var from = 0
    // небольшой запас на разницу шрифтов при измерении
    val limit = maxPx - 6f
    while (from < parts.size - 1 && measure(parts.drop(from).joinToString("/")) > limit) from++
    return parts.drop(from).joinToString("/")
}

/** Треугольная кнопка справа от названия списка: выпадающий список всех плейлистов для быстрого перехода. */
@Composable
private fun PlaylistPicker(hub: Hub, view: String, plid: String) {
    val c = LocalColors.current
    var open by remember { mutableStateOf(false) }
    val angle by animateFloatAsState(if (open) 180f else 0f, label = "pickerArrow")
    val density = LocalDensity.current
    Box {
        Pressable(
            modifier = Modifier.size(width = 34.dp, height = 34.dp),
            contentAlignment = Alignment.Center,
            onClick = { open = true },
            description = "Выбрать плейлист"
        ) {
            val tint = c.textDim
            Canvas(Modifier.size(width = 11.dp, height = 7.dp).rotate(angle)) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
                drawPath(path, tint)
            }
        }
        if (open) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, with(density) { 36.dp.roundToPx() }),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true)
            ) {
                val shape = RoundedCornerShape(12.dp)
                Column(
                    Modifier
                        .width(250.dp)
                        .heightIn(max = 340.dp)
                        .clip(shape)
                        .border(1.dp, c.border, shape)
                        .background(c.surface2)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 6.dp)
                ) {
                    hub.orderedListIds().forEach { id ->
                        val current = id == plid
                        Pressable(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp),
                            pressed = c.surface3,
                            contentAlignment = Alignment.CenterStart,
                            onClick = {
                                open = false
                                hub.switchViewTo(view, id)
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Txt(
                                    hub.listName(id), 14f,
                                    if (current) FontWeight.Bold else FontWeight.Normal,
                                    if (current) c.accent2 else c.text,
                                    modifier = Modifier.weight(1f), maxLines = 1
                                )
                                if (current) AbIcon(Ic.check, c.accent2, 16.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderHead(
    c: AbColors, name: String, count: Int, isPath: Boolean, collapsed: Boolean,
    onToggle: () -> Unit, onToggleAll: () -> Unit
) {
    // стрелка: вправо — список свёрнут, вниз — развёрнут
    val angle by animateFloatAsState(if (collapsed) 0f else 90f, label = "folderArrow")
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 9.dp, end = 2.dp, top = 8.dp, bottom = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // долгое нажатие по имени папки — свернуть/развернуть все папки
        Pressable(
            modifier = Modifier.weight(1f).heightIn(min = 36.dp),
            contentAlignment = Alignment.CenterStart,
            onLongClick = onToggleAll
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AbIcon(Ic.folder, c.textFaint, 16.dp)
                if (isPath) {
                    // Путь папки: последняя папка всегда видна целиком, перед ней — столько уровней, сколько помещается
                    // в строке (но не выше добавленной папки: путь и так начинается с неё)
                    val measurer = rememberTextMeasurer()
                    BoxWithConstraints(Modifier.weight(1f)) {
                        val maxPx = constraints.maxWidth.toFloat()
                        val style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                        val shown = remember(name, maxPx) {
                            fitFolderPath(name.uppercase(), maxPx) { str ->
                                measurer.measure(text = str, style = style, softWrap = false, maxLines = 1).size.width.toFloat()
                            }
                        }
                        Txt(shown, 11f, FontWeight.Bold, c.folder, maxLines = 1, letterSpacing = 0.6f)
                    }
                } else {
                    Txt(I18n.t(name).uppercase(), 11f, FontWeight.Bold, c.folder, modifier = Modifier.weight(1f), maxLines = 1, letterSpacing = 0.6f)
                }
                Txt(count.toString(), 11f, FontWeight.SemiBold, c.textFaint)
            }
        }
        // стрелка справа — свернуть/развернуть только эту папку
        Pressable(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center,
            onClick = onToggle,
            description = "Свернуть или развернуть папку"
        ) {
            AbIcon(Ic.chevron, c.textFaint, 18.dp, Modifier.rotate(angle))
        }
    }
}

@Composable
private fun TrackRow(
    c: AbColors, hub: Hub, t: Track, index: Int, view: String, sub: String,
    playing: Boolean, selectMode: Boolean, checked: Boolean, anySelected: Boolean
) {
    val isBm = hub.viewPlid(view) == BOOKMARKS
    // подложка: у выбранного трека — всегда (как у проигрываемого); у проигрываемого — только пока ничего не выбрано
    // (пока есть выделенные, фон с проигрываемого снимается; выделили его самого — фон как у выделенного)
    val showBg = checked || (playing && !anySelected)
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    // время и размер показываем у обычных треков (не у радио); в закладках справа — время закладки, под ним длительность трека
    val showMeta = !t.isRadio
    val topMeta = if (isBm) hub.data.bookmarks[t.id] ?: 0L else t.durationMs
    val bottomMeta = if (isBm) (if (t.durationMs > 0) fmtDurLong(t.durationMs) else "") else fmtSize(t.size)
    // время/размер стоят у правого края колонки текста, вплотную к значку ⋮ (как и раньше:
    // 6dp промежуток + 6.8dp сдвиг правого блока)
    val metaShift = 9.8.dp
    Column(Modifier.fillMaxWidth()) {
        Pressable(
            // подсветка шире строки на 2dp слева и справа (содержимое остаётся на месте — см. отступы Row ниже)
            modifier = Modifier.fillMaxWidth().layout { measurable, constraints ->
                val extra = 4.dp.roundToPx()
                val p = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra))
                layout(constraints.maxWidth, p.height) { p.place(-extra / 2, 0) }
            },
            // выбранный трек (как и проигрываемый) — на мягкой акцентной подложке со скруглением
            normal = if (showBg) c.accentSoft else Color.Transparent,
            pressed = c.surface2,
            shape = if (showBg) RoundedCornerShape(9.dp) else RoundedCornerShape(0.dp),
            stateText = if (playing) "Воспроизводится" else if (checked) "Выбрано" else null,
            onClick = {
                // тап по найденному треку при открытой клавиатуре: сначала убираем фокус поиска и клавиатуру, иначе окно
                // меняет размер прямо во время перехода на главный экран (раскладка «прыгает», тап и запуск могут сорваться)
                if (!selectMode) {
                    focusManager.clearFocus()
                    keyboard?.hide()
                }
                hub.trackRowClick(view, t.id)
            },
            // долгое нажатие — войти в режим выбора и сразу отметить этот трек
            onLongClick = { hub.longPressSelect(view, t.id) }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // выбранный в режиме выбора трек — вертикальная полоска акцентного цвета слева
                    // (скруглённая, около 60% высоты строки, по центру)
                    .drawBehind {
                        if (checked) {
                            val h = size.height * 0.6f
                            drawRoundRect(
                                c.accent,
                                topLeft = Offset(3.dp.toPx(), (size.height - h) / 2f),
                                size = Size(3.dp.toPx(), h),
                                cornerRadius = CornerRadius(1.5.dp.toPx())
                            )
                        }
                    }
                    .heightIn(min = 52.dp).padding(start = 11.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // слева: «1. Название» и под ним автор. Время стоит в строке названия, размер файла —
                // в той же строке, что и автор (тот же кегль и высота строки), оба по правому краю
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Txt(
                            "${index + 1}.", 13f, if (checked) FontWeight.ExtraBold else FontWeight.SemiBold,
                            // у выбранного трека акцент с номера и названия убирается — вместо него полоска слева
                            if (checked) c.text else if (playing) c.accent2 else c.textFaint,
                            modifier = Modifier.padding(end = 5.dp), maxLines = 1, lineHeight = 17f
                        )
                        Txt(
                            t.title, 13f, FontWeight.SemiBold, if (!checked && playing) c.accent2 else c.text,
                            modifier = Modifier.weight(1f), maxLines = 1, lineHeight = 17f
                        )
                        if (showMeta && (topMeta > 0 || isBm)) {
                            Txt(
                                fmtDurLong(topMeta), 11f, color = c.textFaint, maxLines = 1, lineHeight = 17f,
                                modifier = Modifier.padding(start = 8.dp).offset(x = metaShift)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Txt(sub, 11f, color = c.textFaint, maxLines = 1, lineHeight = 14f, modifier = Modifier.weight(1f))
                        if (showMeta && bottomMeta.isNotEmpty()) {
                            // размер (в закладках — длительность трека) чуть приглушён относительно автора
                            Txt(
                                bottomMeta, 11f, color = c.textFaint.copy(alpha = 0.7f), maxLines = 1, lineHeight = 14f,
                                modifier = Modifier.padding(start = 8.dp).offset(x = metaShift)
                            )
                        }
                    }
                }
                // правый блок сдвинут за край контента на 6.8dp: значок ⋮ нарисован с полями внутри
                // своей области, поэтому видимые точки оказываются ровно в 24dp от края экрана —
                // столько же, сколько слева до номера трека (18dp контейнер + 6dp строка)
                Row(
                    modifier = Modifier.offset(x = 3.8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // закладка убирается через меню трека (⋮) или в режиме выбора; избранное — тоже через меню трека
                    Pressable(
                        modifier = Modifier.size(width = 28.dp, height = 36.dp),
                        contentAlignment = Alignment.Center,
                        onClick = { hub.openSheet(Sheet.TrackMenu(t.id, view)) },
                        description = "Меню трека"
                    ) {
                        AbIcon(Ic.dots, c.textFaint, 18.dp)
                    }
                }
            }
        }
        if (showBg) Box(Modifier.height(1.dp)) else {
            // в режиме выделения разделители ещё прозрачнее; концы — по левому тексту и правому меню ⋮
            val scale by animateFloatAsState(if (selectMode) 0.25f else 0.55f, label = "dividerAlpha")
            SoftDivider(scale, Modifier.padding(start = 9.dp, end = 9.dp))
        }
    }
}
