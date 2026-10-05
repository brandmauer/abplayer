package com.brandmauer.abplayer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.brandmauer.abplayer.Hub
import com.brandmauer.abplayer.data.COVER_ALL
import com.brandmauer.abplayer.data.COVER_NOTIFICATION
import com.brandmauer.abplayer.data.COVER_PLAYER
import com.brandmauer.abplayer.Sheet
import com.brandmauer.abplayer.data.BOOKMARKS
import com.brandmauer.abplayer.data.RADIO
import com.brandmauer.abplayer.i18n.I18n
import com.brandmauer.abplayer.util.fmtDurLong
import com.brandmauer.abplayer.util.bookmarkWord
import com.brandmauer.abplayer.util.trackWord
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SectionLabel(text: String, trailing: (@Composable () -> Unit)? = null) {
    val c = LocalColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Txt(text, 11f, FontWeight.Bold, c.header, letterSpacing = 1f)
        if (trailing != null) trailing()
    }
}

/** Левый экран: «Плейлисты и Настройки». */
@Composable
fun SettingsScreen(hub: Hub, dark: Boolean, onOpenMenu: () -> Unit) {
    val c = LocalColors.current
    val data = hub.data
    val order = hub.orderedListIds()
    val orderNow by rememberUpdatedState(order)
    val density = LocalDensity.current
    val gapPx = with(density) { 8.dp.toPx() }
    val scope = rememberCoroutineScope()

    // состояние перетаскивания карточек плейлистов (долгое нажатие + перемещение)
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var localOrder by remember { mutableStateOf<List<String>?>(null) }
    var itemHeightPx by remember { mutableFloatStateOf(0f) }
    var justDragged by remember { mutableStateOf(false) }
    val shown = localOrder ?: order

    Column(
        modifier = Modifier
            .fillMaxSize()
            .rubberBand()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 28.dp)
    ) {
        // ---- заголовок
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Txt(HEADER_TITLE, 19f, FontWeight.ExtraBold, c.text, letterSpacing = -0.2f)
                Txt("Плейлисты и Настройки", 12f, color = c.textDim, modifier = Modifier.padding(top = 2.dp))
            }
        }

        // ---- плейлисты
        SectionLabel("ПЛЕЙЛИСТЫ") {
            // правее нельзя: «+» стоит строго над значками ⋮ в строках плейлистов (рамка 1dp + отступ 14dp + половина кнопки)
            Box(Modifier.padding(end = 16.dp)) {
                IconBtn(Ic.plus, { hub.newPlaylist(emptyList()) }, size = 26.dp, iconSize = 20.dp, description = "Новый плейлист")
            }
        }
        // все плейлисты — в одном общем поле (как пункты настроек); порядок меняется перетаскиванием
        SettingsBlock {
            shown.forEachIndexed { index, id ->
                key(id) {
                    val list = hub.playlistTracks(id)
                    val isDragging = draggingId == id
                    val fxl = LocalFxLevel.current
                    // схваченная строка «приподнимается» (растёт и получает тень), при отпускании пружинит обратно
                    val lift by animateFloatAsState(
                        if (isDragging) 1f else 0f,
                        animationSpec = spring(dampingRatio = 0.4f, stiffness = 380f),
                        label = "dragLift"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffset else 0f
                                val k = 1f + 0.05f * fxl * lift
                                scaleX = k
                                scaleY = k
                            }
                            // схваченный текущий плейлист подсвечивается так же, как проигрываемый/выбранный файл (мягкая акцентная подложка)
                            .then(
                                if (isDragging) {
                                    val isCur = id == hub.currentPlaylistId
                                    val dragShape = if (isCur) RoundedCornerShape(9.dp) else RoundedCornerShape(0.dp)
                                    Modifier
                                        .shadow((10f + 14f * fxl).dp, dragShape)
                                        .background(if (isCur) c.accentSoft else c.surface2, dragShape)
                                } else Modifier
                            )
                            .onGloballyPositioned { itemHeightPx = it.size.height.toFloat() }
                            .pointerInput(id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingId = id
                                        dragOffset = 0f
                                        localOrder = orderNow
                                        justDragged = true
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val slot = itemHeightPx
                                        var cur = (localOrder ?: orderNow).toMutableList()
                                        var guard = 0
                                        while (guard++ < 10) {
                                            val idx = cur.indexOf(id)
                                            if (dragOffset > slot / 2 && idx < cur.size - 1) {
                                                cur.removeAt(idx)
                                                cur.add(idx + 1, id)
                                                dragOffset -= slot
                                            } else if (dragOffset < -slot / 2 && idx > 0) {
                                                cur.removeAt(idx)
                                                cur.add(idx - 1, id)
                                                dragOffset += slot
                                            } else break
                                        }
                                        localOrder = cur
                                    },
                                    onDragEnd = {
                                        localOrder?.let { hub.reorderLists(it) }
                                        draggingId = null
                                        dragOffset = 0f
                                        localOrder = null
                                        scope.launch {
                                            delay(300)
                                            justDragged = false
                                        }
                                    },
                                    onDragCancel = {
                                        draggingId = null
                                        dragOffset = 0f
                                        localOrder = null
                                        justDragged = false
                                    }
                                )
                            }
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Pressable(
                                modifier = Modifier.fillMaxWidth(),
                                pressed = c.surface2,
                                onClick = { if (!justDragged) hub.openPlaylist(id) }
                            ) {
                                val isCurrentList = id == hub.currentPlaylistId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Txt(hub.listName(id), 14f, FontWeight.Bold, if (isCurrentList) c.accent2 else c.text, maxLines = 1, lineHeight = 18f)
                                        Txt(
                                            if (id == RADIO) "${list.size} станций"
                                            else if (id == BOOKMARKS) "${list.size} ${bookmarkWord(list.size)}"
                                            else "${list.size} ${trackWord(list.size)} · ${fmtDurLong(list.sumOf { it.durationMs })}",
                                            12f, color = c.textDim, lineHeight = 15f
                                        )
                                    }
                                    IconBtn(Ic.dots, { hub.openSheet(Sheet.PlaylistMenu(id, null)) }, size = 28.dp, description = "Меню плейлиста")
                                }
                            }
                            if (index != shown.size - 1) Divider1()
                        }
                    }
                }
            }
        }

        // ---- настройки
        SectionLabel("НАСТРОЙКИ")
        SettingsBlock {
            SettingRow(divider = true) {
                Txt("Восстанавливать позицию", 14f, color = c.text)
                AbSwitch(data.restorePosition) { hub.setRestorePosition(it) }
            }
            SettingRow(divider = true) {
                Txt("Откат 20с после паузы", 14f, color = c.text)
                AbSwitch(data.rewindAfterPause) { hub.setRewindAfterPause(it) }
            }
            SettingRow(divider = true, onClick = { hub.openSheet(Sheet.SeekStep) }) {
                Txt("Шаг перемотки", 14f, color = c.text)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Txt("${data.seekStepSec} с", 13f, color = c.textDim)
                    AbIcon(Ic.chevron, c.textFaint, 16.dp)
                }
            }
            // «Таймер сна»: открывает ту же шторку, что и кнопка на главном экране (значение «Добавлять к таймеру» — внутри неё)
            SettingRow(divider = true, onClick = { hub.openSheet(Sheet.Sleep) }) {
                Txt("Таймер сна", 14f, color = c.text)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (hub.sleep.active) Txt(fmtDurLong(hub.sleep.remaining * 1000L), 13f, color = c.accent2)
                    AbIcon(Ic.chevron, c.textFaint, 16.dp)
                }
            }
            SettingRow(divider = true, onClick = { hub.openSheet(Sheet.Sound) }) {
                Txt("Звук и эффекты", 14f, color = c.text)
                AbIcon(Ic.chevron, c.textFaint, 16.dp)
            }
            SettingRow(divider = false, onClick = { hub.openSheet(Sheet.Power) }) {
                Txt("Энергосбережение", 14f, color = c.text)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Txt(powerProfileLabel(hub.data.power.profile), 13f, color = c.textDim)
                    AbIcon(Ic.chevron, c.textFaint, 16.dp)
                }
            }
        }

        SectionLabel("ИНТЕРФЕЙС")
        SettingsBlock {
            SettingRow(divider = true, onClick = { hub.cycleTheme(dark) }) {
                Txt("Тема", 14f, color = c.text)
                Txt(if (dark) "Тёмная тема" else "Светлая тема", 13f, color = c.textDim)
            }
            SettingRow(divider = true, onClick = { hub.openSheet(Sheet.AccentColor) }) {
                Txt("Цветовой акцент", 14f, color = c.text)
                // Кружок диаметром с высоту переключателя (AbSwitch — 26dp), вместо
                // скруглённого квадрата со стрелкой.
                Box(Modifier.size(26.dp).background(c.accent, CircleShape))
            }
            SettingRow(divider = true, onClick = { hub.openSheet(Sheet.Covers) }) {
                Txt("Обложки", 14f, color = c.text)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Txt(
                        when (data.coverMode) {
                            COVER_NOTIFICATION -> "В уведомлении"
                            COVER_PLAYER -> "В плеере"
                            COVER_ALL -> "Везде"
                            else -> "Не показывать"
                        },
                        13f, color = c.textDim
                    )
                    AbIcon(Ic.chevron, c.textFaint, 16.dp)
                }
            }
            // «Визуальные эффекты»: тап открывает шторку с ползунками (визуальные эффекты, звуки, вибрация, фон-обложка)
            SettingRow(divider = true, onClick = { hub.openSheet(Sheet.Effects) }) {
                Txt("Визуальные эффекты", 14f, color = c.text)
                AbIcon(Ic.chevron, c.textFaint, 16.dp)
            }
            SettingRow(divider = false, onClick = {
                // переключатель Русский ↔ English; при первом нажатии из «как в системе» выбираем другой язык
                hub.setLanguage(if (I18n.lang == "ru") "en" else "ru")
            }) {
                Txt("Язык", 14f, color = c.text)
                Txt(if (I18n.lang == "ru") "Русский" else "English", 13f, color = c.textDim)
            }
        }

        SectionLabel("ПРОЧЕЕ")
        SettingsBlock {
            SettingRow(divider = true, onClick = { hub.requestExportSettings() }) {
                Txt("Экспортировать настройки", 14f, color = c.text)
                AbIcon(Ic.chevron, c.textFaint, 16.dp)
            }
            SettingRow(divider = true, onClick = { hub.requestImportSettings() }) {
                Txt("Импортировать настройки", 14f, color = c.text)
                AbIcon(Ic.chevron, c.textFaint, 16.dp)
            }
            SettingRow(divider = true, onClick = { hub.openSheet(Sheet.Help) }) {
                Txt("Помощь", 14f, color = c.text)
                AbIcon(Ic.chevron, c.textFaint, 16.dp)
            }
            SettingRow(divider = false, onClick = { hub.openSheet(Sheet.About) }) {
                Txt("О приложении", 14f, color = c.text)
                AbIcon(Ic.chevron, c.textFaint, 16.dp)
            }
        }
        Box(Modifier.height(20.dp))
    }
}

@Composable
fun SettingsBlock(content: @Composable () -> Unit) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, c.border, shape)
            .clip(shape)
            // surface уже чуть прозрачный: сквозь блок проглядывает фон из обложки
            .background(c.surface)
    ) { content() }
}

/** Строка настроек: все строки одной высоты (52dp). */
@Composable
fun SettingRow(divider: Boolean, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth()) {
        Pressable(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            pressed = if (onClick != null) c.surface2 else Color0,
            onClick = onClick
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) { content() }
        }
        if (divider) Divider1()
    }
}

private val Color0 = androidx.compose.ui.graphics.Color.Transparent
