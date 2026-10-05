package com.brandmauer.abplayer.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import kotlin.math.exp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick as semOnClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.brandmauer.abplayer.i18n.I18n
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.brandmauer.abplayer.Hub
import com.brandmauer.abplayer.Sheet
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.brandmauer.abplayer.data.BOOKMARKS
import com.brandmauer.abplayer.data.coverInPlayer
import com.brandmauer.abplayer.data.FAVORITES
import com.brandmauer.abplayer.data.Importer
import com.brandmauer.abplayer.util.fmtDurLong
import com.brandmauer.abplayer.util.fmtRemaining

const val HEADER_TITLE = "ABPlayer"

/** Расстояние от дорожки ползунка, после которого касание считается случайным и перемотка отменяется (≈2 см: 1 dp = 1/160 дюйма). */
val SEEK_CANCEL_DISTANCE: Dp = 125.dp

/**
 * Пауза между шагами перемотки при удержании кнопки (мс) в зависимости от времени удержания.
 * Плавная кривая: старт 450 мс, затем экспоненциально быстрее (постоянная времени 2.2 с) до предела 130 мс,
 * который достигается примерно за 5–6 секунд. При шаге 10 с это от ~22 с/с в начале до ~75 с/с в конце —
 * заметное ускорение, но без «пролёта» мимо нужного места.
 */
fun holdSeekIntervalMs(heldMs: Long): Long = (130.0 + 320.0 * exp(-heldMs / 2200.0)).toLong()

/**
 * Кнопка перемотки: дуга со стрелкой и числом секунд. Тап — один шаг; удержание — повторные шаги
 * с ускорением (onHold возвращает false, когда дошли до края и повторять нечего).
 */
@Composable
fun SeekBtn(forward: Boolean, seconds: Int, onClick: () -> Unit, onHold: () -> Boolean) {
    val c = LocalColors.current
    var down by remember { mutableStateOf(false) }
    val clickState = rememberUpdatedState(onClick)
    val holdState = rememberUpdatedState(onHold)
    val haptic = LocalHapticFeedback.current
    val fx = LocalFx.current
    val view = LocalView.current
    val press = rememberPressSpring(down, fx)
    Box(
        modifier = Modifier
            .pressScale(press)
            .size(46.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (down) c.surface2 else Color.Transparent)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        down = true
                        coroutineScope {
                            val job = launch {
                                delay(viewConfiguration.longPressTimeoutMillis)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                var held = 0L
                                while (holdState.value()) {
                                    val gap = holdSeekIntervalMs(held)
                                    delay(gap)
                                    held += gap
                                }
                            }
                            tryAwaitRelease()
                            job.cancel()
                        }
                        down = false
                    },
                    // длинное нажатие обрабатывает цикл выше; тап после него не должен перематывать ещё раз
                    onLongPress = { },
                    onTap = {
                        fxFeedback(view)
                        clickState.value()
                    }
                )
            }
            .clearAndSetSemantics {
                // TalkBack: «Назад на 10 с» / «Вперёд на 10 с», двойное нажатие — один шаг
                contentDescription = I18n.t((if (forward) "Вперёд на " else "Назад на ") + seconds + " с")
                role = Role.Button
                semOnClick(label = null) { clickState.value(); true }
            },
        contentAlignment = Alignment.Center
    ) {
        // число секунд из настроек — по центру самой окружности (её центр совпадает с центром иконки)
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            AbIcon(if (forward) Ic.forwardArc else Ic.rewindArc, c.textDim, 24.dp)
            val label = seconds.toString()
            CenteredNum(label, if (label.length >= 3) 5.5f else 7f, c.textDim)
        }
    }
}

@Composable
fun TransportBtn(icon: ImageVector, onClick: () -> Unit) {
    IconBtn(icon, onClick, size = 46.dp, iconSize = 22.dp)
}

@Composable
fun MainScreen(hub: Hub, onOpenMenu: () -> Unit) {
    val c = LocalColors.current
    val t = hub.trackById(hub.currentTrackId)
    val playing = t != null && hub.isPlaying
    val data = hub.data
    val ab = hub.ab
    val sleep = hub.sleep
    val step = data.seekStepSec
    val pos = hub.seekPreviewMs ?: hub.positionMs
    val dur = if (t != null) hub.durationMs else 0L
    val fav = t != null && t.favorite
    val bookmarked = t != null && data.bookmarks.containsKey(t.id)
    val isRadioTrack = t?.isRadio == true

    // Адаптация под невысокие экраны (например Redmi 4X): вместо того чтобы часть кнопок
    // управления уезжала за пределы экрана, уменьшаем обложку и отступы, когда места мало.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 700.dp
        val coverSize = if (compact) 164.dp else 210.dp
        val coverPadTop = if (compact) 2.dp else 6.dp
        // на невысоких экранах строки «Трек N из M» нет, и над обложкой остаётся пустота — поднимаем ТОЛЬКО обложку
        // (всё остальное остаётся на месте)
        val coverLift = if (compact) (-14).dp else 0.dp
        val coverPadBottom = if (compact) 10.dp else 20.dp
        val headerPadBottom = if (compact) 8.dp else 16.dp
        val iconsRowPadTop = if (compact) 10.dp else 18.dp
        val transportPadTop = if (compact) 14.dp else 26.dp

        // обложка и фон неподвижны: ни наклона, ни «дыхания», ни параллакса при прокрутке
        val fx = LocalFx.current
        val scroll = rememberScrollState()
        val enter = rememberEntrance(fx)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .rubberBand()
            .verticalScroll(scroll)
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 28.dp)
    ) {
        // ---- заголовок
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = headerPadBottom),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Txt(HEADER_TITLE, 19f, FontWeight.ExtraBold, c.text, letterSpacing = -0.2f)
                // на невысоких экранах строка «Трек N из M» скрыта — освободившееся место отдано обложке
                if (!compact) Txt(hub.mainInfoText(), 12f, color = c.textDim, modifier = Modifier.padding(top = 2.dp))
            }
            IconBtn(Ic.dots, onOpenMenu)
        }

        // ---- обложка (со всем, что ниже, поднимаем на несколько dp вверх — компактнее макет)
        Column(Modifier.offset(y = (-8).dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fxEnter(enter[0], 18.dp)
                .offset(y = coverLift)
                .padding(top = coverPadTop, bottom = coverPadBottom),
            contentAlignment = Alignment.Center
        ) {
            val hasArt = t != null && t.hasArt && coverInPlayer(data.coverMode)
            Box(
                modifier = Modifier
                    .size(coverSize)
                    .layeredShadow(fx, c.dark, RoundedCornerShape(20.dp), 18.dp)
                    .clip(RoundedCornerShape(20.dp))
                    // стандартная обложка: лёгкий градиент — верхний угол чуть светлее, нижний (c.surface) светлее фона приложения
                    .background(Brush.linearGradient(placeholderCoverColors(c)))
                    .border(1.dp, (if (c.dark) c.accent2 else c.accent).copy(alpha = if (c.dark) 0.10f else 0.16f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (hasArt) {
                    AsyncImage(
                        model = Importer.coverFile(LocalContext.current, t!!.id), // hasArt гарантирует t != null
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        // гармонизация: чуть меньше насыщенность и контраст, чтобы яркая обложка не била по интерфейсу
                        colorFilter = if (data.coverHarmonize) HarmonizeFilter else null,
                        // небольшая прозрачность: смягчает контраст обложки на светлой и тёмной теме
                        modifier = Modifier.fillMaxSize().alpha(0.88f)
                    )
                } else {
                    // стандартная обложка: аккуратно нарисованный значок приложения (см. CoverArt.kt / design/abplayer_cover.svg)
                    DefaultCoverArt(coverSize)
                }
                if (data.coverHarmonize) {
                    // мягкий градиент: лёгкий акцентный оттенок сверху-слева и затемнение к низу-справа
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.linearGradient(
                                colorStops = arrayOf(
                                    0f to c.accent.copy(alpha = 0.13f),
                                    0.55f to Color.Transparent,
                                    1f to c.bg.copy(alpha = 0.32f)
                                )
                            )
                        )
                    )
                }
            }
        }

        // ---- статус, название, исполнитель
        Column(
            Modifier
                .fillMaxWidth()
                .fxEnter(enter[1])
        ) {
        // на невысоких экранах строка состояния скрыта (как и «Трек N из M» в заголовке)
        if (!compact) Txt(
            if (t == null) "ГОТОВ К ВОСПРОИЗВЕДЕНИЮ" else if (playing) "ВОСПРОИЗВЕДЕНИЕ" else "ПАУЗА",
            11f, FontWeight.Bold, c.accent2, letterSpacing = 0.6f
        )
        // если название/исполнитель не помещаются — один раз прокручиваются: при начале проигрывания нового файла
        // и по тапу на соответствующую строку (на помещающихся строках ничего не происходит)
        var titleKick by remember { mutableStateOf(0) }
        var subKick by remember { mutableStateOf(0) }
        var kickDelay by remember { mutableStateOf(1200L) }
        var kickedId by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(t?.id, playing) {
            if (t != null && playing && kickedId != t.id) {
                kickedId = t.id
                kickDelay = 1200L
                titleKick += 1
                subKick += 1
            }
        }
        Txt(
            t?.title ?: "Выберите трек", 21f, FontWeight.ExtraBold, c.text,
            modifier = Modifier
                .padding(top = if (compact) 0.dp else 6.dp)
                .pointerInput(Unit) { detectTapGestures { kickDelay = 300L; titleKick += 1 } }
                .semantics { semOnClick(label = I18n.t("Прокрутка названия")) { kickDelay = 300L; titleKick += 1; true } },
            maxLines = 1, lineHeight = 26f, scrollOnce = titleKick, scrollDelayMs = kickDelay
        )
        // у радио под названием станции — композиция из потока (если станция её передаёт), с прокруткой
        val streamTitle = if (isRadioTrack) hub.streamTitle else null
        if (streamTitle != null) {
            Txt(
                streamTitle, 14f, color = c.textDim,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .fillMaxWidth(),
                maxLines = 1, marquee = true
            )
        } else {
            Txt(
                t?.artist ?: HEADER_TITLE, 14f, color = c.textDim,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .pointerInput(Unit) { detectTapGestures { kickDelay = 300L; subKick += 1 } }
                    .semantics { semOnClick(label = I18n.t("Прокрутка названия")) { kickDelay = 300L; subKick += 1; true } },
                maxLines = 1, scrollOnce = subKick, scrollDelayMs = kickDelay
            )
        }

        }

        // ---- таймер сна и точки A/B
        Row(
            modifier = Modifier.fillMaxWidth().fxEnter(enter[2]).padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val pillShape = RoundedCornerShape(9.dp)
            val fg = if (sleep.active) c.text else c.textDim // активный таймер — цвет заголовка трека, без акцента
            val idleBorder = idleButtonBorder(c)
            // пока таймер идёт — слева от него кнопка «остановить» (только иконка)
            if (sleep.active) {
                Pressable(
                    modifier = Modifier.size(40.dp).border(1.dp, idleBorder, pillShape),
                    normal = timerButtonBg(c),
                    pressed = c.surface3,
                    shape = pillShape,
                    contentAlignment = Alignment.Center,
                    onClick = { hub.stopSleep(true) },
                    description = "Остановить таймер сна"
                ) {
                    AbIcon(Ic.timerOff, c.textDim, 20.dp)
                }
            }
            Pressable(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .border(1.dp, idleBorder, pillShape), // обводка не меняется при активном таймере
                normal = timerButtonBg(c),
                pressed = c.surface3,
                shape = pillShape,
                contentAlignment = Alignment.Center,
                onClick = { hub.onSleepPillTap() },
                // долгое нажатие по остальному полю кнопки — настройки таймера
                onLongClick = { hub.openSheet(Sheet.Sleep) }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    // иконка без собственного действия: тап проходит на всю кнопку таймера
                    Box(Modifier.size(width = 34.dp, height = 40.dp), contentAlignment = Alignment.Center) {
                        // цвет иконки не меняется при активном таймере; меняется только сам значок (часы → часы с плюсом)
                        AbIcon(if (sleep.active) Ic.clockPlus else Ic.clock, c.textDim, 20.dp)
                    }
                    Txt(hub.sleepLabel(), 13f, FontWeight.SemiBold, fg, maxLines = 1)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DashBox("A", ab.aMs) { hub.quickSetAb(true) }
                DashBox("B", ab.bMs) { hub.quickSetAb(false) }
            }
        }

        // ---- ползунок трека
        Column(Modifier.fillMaxWidth().fxEnter(enter[2]).padding(top = 20.dp)) {
            ThinSlider(
                value = if (dur > 0) pos.toFloat().coerceIn(0f, dur.toFloat()) else 0f,
                onValueChange = { v -> if (t != null) hub.seekPreviewMs = v.toLong() },
                min = 0f,
                max = if (dur > 0) dur.toFloat() else 100f,
                modifier = Modifier.fillMaxWidth().height(30.dp),
                description = "Позиция в треке",
                valueText = fmtDurLong(if (t != null) pos else 0L) + " из " + fmtDurLong(dur),
                filled = true,
                ring = true,
                rubber = true,
                bubbleText = { v -> fmtDurLong(v.toLong()) },
                // случайное касание: если палец, не отрываясь, ушёл от ползунка примерно на 2 см — перемотка отменяется
                cancelDistance = SEEK_CANCEL_DISTANCE,
                onCancel = { hub.seekPreviewMs = null },
                onFinished = {
                    val p = hub.seekPreviewMs
                    if (p != null) hub.seekTo(p)
                    hub.seekPreviewMs = null
                }
            )
            // время под ползунком — и всё, что ниже, — приподнято ещё немного вверх
            Column(Modifier.offset(y = (-8).dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Txt(fmtDurLong(if (t != null) pos else 0L), 11f, color = c.textFaint, modifier = Modifier.padding(top = 6.dp))
                Pressable(
                    modifier = Modifier,
                    pressed = Color.Transparent,
                    onClick = { hub.showRemaining = !hub.showRemaining }
                ) {
                    Txt(
                        if (hub.showRemaining) fmtRemaining(pos, dur) else fmtDurLong(dur),
                        11f, color = c.textFaint,
                        modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp)
                    )
                }
            }

        // ---- ряд иконок
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = iconsRowPadTop, start = 2.dp, end = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // в ряду иконок активное состояние — только контур акцентного цвета, без фона и заливки
            IconBtn(
                Ic.shuffle, { hub.toggleShuffle() }, tint = if (hub.shuffle) c.accent else null,
                stateText = if (hub.shuffle) "Включено" else "Выключено"
            )
            Pressable(
                modifier = Modifier.size(40.dp),
                normal = Color.Transparent,
                pressed = c.surface2,
                shape = RoundedCornerShape(9.dp),
                contentAlignment = Alignment.Center,
                onClick = { hub.toggleRepeat() },
                description = "Повтор",
                stateText = listOf("Повтор выключен", "Повтор плейлиста", "Повтор трека")[hub.repeatMode]
            ) {
                AbIcon(
                    if (hub.repeatMode == 2) Ic.repeatOne else Ic.repeat,
                    if (hub.repeatMode != 0) c.accent else c.textDim,
                    20.dp
                )
            }
            IconBtn(
                Ic.heart,
                { hub.toggleFavoriteCurrent() },
                tint = if (fav) c.accent else null,
                stateText = if (fav) "В избранном" else "Не в избранном",
                onLongClick = { hub.openSheet(Sheet.ListSheet(FAVORITES)) }
            )
            // активная закладка — акцентный контур
            IconBtn(
                Ic.bookmark,
                { if (isRadioTrack) hub.toast("Для радиопотоков закладки недоступны") else hub.toggleBookmark() },
                tint = if (bookmarked) c.accent else null,
                stateText = if (bookmarked) "Закладка есть" else "Закладки нет",
                onLongClick = { hub.openSheet(Sheet.ListSheet(BOOKMARKS)) }
            )
            Pressable(
                modifier = Modifier.size(40.dp),
                normal = Color.Transparent,
                pressed = c.surface2,
                shape = RoundedCornerShape(9.dp),
                contentAlignment = Alignment.Center,
                onClick = {
                    if (isRadioTrack) hub.toast("Для радио точки A и B недоступны") else hub.openSheet(Sheet.Ab)
                },
                // долгое нажатие — шторка A–B
                onLongClick = {
                    if (isRadioTrack) hub.toast("Для радио точки A и B недоступны") else hub.openSheet(Sheet.Ab)
                },
                description = "Повтор фрагмента A–B",
                stateText = if (ab.aMs != null && ab.bMs != null) "Точки заданы" else null
            ) {
                // обе точки A и B заданы — контур значка акцентный (независимо от того, включён ли повтор)
                AbIcon(Ic.ab, if (ab.aMs != null && ab.bMs != null) c.accent else c.textDim, 22.dp)
            }
            // «активна» при любом включённом звуковом эффекте: эквалайзер, звук (тон/скорость/баланс/предусиление), точная громкость
            val soundOn = data.sound.eqEnabled || data.sound.soundEnabled || data.sound.volumeFineEnabled
            IconBtn(
                Ic.volume, { hub.openSheet(Sheet.Sound) }, iconSize = 26.dp,
                tint = if (soundOn) c.accent else null,
                // долгое нажатие: выключить все эффекты / вернуть прежнее состояние
                onLongClick = { hub.toggleSoundEffectsQuick() }
            )
        }

        // ---- управление воспроизведением
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = transportPadTop),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SeekBtn(false, step, { hub.rewind() }, { hub.holdSeek(false) })
            TransportBtn(Ic.prev) { hub.playPrev() }
            // анимация на кнопке — только если загрузка затянулась (не мигает на быстрых локальных треках)
            var showSpinner by remember { mutableStateOf(false) }
            LaunchedEffect(hub.isLoading) {
                if (hub.isLoading) {
                    delay(450)
                    showSpinner = true
                } else {
                    showSpinner = false
                }
            }
            val spin = rememberInfiniteTransition(label = "loading")
            val angle by spin.animateFloat(
                initialValue = 0f, targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle"
            )
            var playDown by remember { mutableStateOf(false) }
            val playPress = rememberPressSpring(playDown, fx)
            val playView = LocalView.current
            Box(
                modifier = Modifier
                    .pressScale(playPress)
                    .size(66.dp)
                    .then(
                        if (fx) Modifier.layeredShadow(true, c.dark, CircleShape, 12.dp, farTint = c.accent)
                        else Modifier.shadow(12.dp, CircleShape, ambientColor = c.accent.copy(alpha = 0.4f), spotColor = c.accent.copy(alpha = 0.4f))
                    )
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(c.accent2, c.accent)))
                    .pointerInput(fx) {
                        detectTapGestures(
                            onPress = {
                                playDown = true
                                tryAwaitRelease()
                                playDown = false
                            },
                            onTap = {
                                fxFeedback(playView)
                                hub.onPlayPause()
                            }
                        )
                    }
                    .clearAndSetSemantics {
                        contentDescription = I18n.t(if (playing) "Пауза" else "Воспроизвести")
                        role = Role.Button
                        semOnClick(label = null) { hub.onPlayPause(); true }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (fx) PlayPauseIcon(playing, c.onAccent, 26.dp, Modifier.alpha(if (showSpinner) 0.55f else 1f))
                else AbIcon(if (playing) Ic.pause else Ic.play, c.onAccent, 26.dp, Modifier.alpha(if (showSpinner) 0.55f else 1f))
                if (showSpinner) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 3.dp.toPx()
                        val inset = 5.dp.toPx() + stroke / 2
                        drawArc(
                            color = c.onAccent.copy(alpha = 0.25f), startAngle = 0f, sweepAngle = 360f, useCenter = false,
                            topLeft = Offset(inset, inset), size = Size(size.width - inset * 2, size.height - inset * 2),
                            style = Stroke(width = stroke)
                        )
                        drawArc(
                            color = c.onAccent, startAngle = angle - 90f, sweepAngle = 95f, useCenter = false,
                            topLeft = Offset(inset, inset), size = Size(size.width - inset * 2, size.height - inset * 2),
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                }
            }
            TransportBtn(Ic.next) { hub.playNext(false) }
            SeekBtn(true, step, { hub.forward() }, { hub.holdSeek(true) })
        }

        // ---- «Увеличить A ↔ B»: ползунок фрагмента под кнопками управления, тоже чуть приподнят
        val a = ab.aMs
        val b = ab.bMs
        if (ab.zoom && a != null && b != null) {
            val len = maxOf(100L, b - a)
            var preview by remember { mutableStateOf<Long?>(null) }
            val rel = preview ?: (pos - a).coerceIn(0L, len)
            Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
                ThinSlider(
                    value = rel.toFloat(),
                    onValueChange = { v -> preview = v.toLong() },
                    min = 0f,
                    max = len.toFloat(),
                    modifier = Modifier.fillMaxWidth().height(30.dp),
                    description = "Позиция внутри фрагмента A–B",
                    valueText = fmtDurLong(rel),
                    filled = true,
                    ring = true,
                    rubber = true,
                    bubbleText = { v -> fmtDurLong(v.toLong()) },
                    cancelDistance = SEEK_CANCEL_DISTANCE,
                    onCancel = { preview = null },
                    onFinished = {
                        val p = preview
                        if (p != null) hub.seekAbZoom(p)
                        preview = null
                    }
                )
                // подписи: время точки A слева и точки B справа; отступ от ползунка такой же, как у основного
                Row(Modifier.fillMaxWidth().offset(y = (-8).dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Txt(fmtDurLong(a), 11f, color = c.textFaint, modifier = Modifier.padding(top = 6.dp))
                    Txt(fmtDurLong(b), 11f, color = c.textFaint, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        } // конец блока «время под ползунком трека и всё, что ниже»
        } // закрывает Column «ползунок трека» (открыт выше, до вложенного блока со временем)
        } // конец блока «обложка и всё, что ниже»
    }
    }
}

/** Фильтр «гармонизации» обложки: насыщенность ×0.60 и контраст ×0.90 (к среднему серому). */
private val HarmonizeFilter: ColorFilter by lazy {
    val m = ColorMatrix().apply { setToSaturation(0.60f) }
    val k = 0.90f
    val t = (1f - k) * 128f
    m.timesAssign(
        ColorMatrix(
            floatArrayOf(
                k, 0f, 0f, 0f, t,
                0f, k, 0f, 0f, t,
                0f, 0f, k, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
    ColorFilter.colorMatrix(m)
}

/** Кнопка-«окошко» точки A или B (dash-box). Значение — время точки или «—». */
/** Обводка неактивных кнопок таймера сна и A/B: полупрозрачная, с лёгким акцентным оттенком (менее серая, чем общая c.border). */
/** Фон кнопок ряда таймера сна: тот же полупрозрачный фон, что у блоков настроек. */
fun timerButtonBg(c: AbColors): Color = c.surface.copy(alpha = if (c.dark) 0.5f else 0.55f)

/** Обводка кнопок ряда таймера: почти без цвета (акцент сильно приглушён серым) и заметно прозрачнее прежней. */
fun idleButtonBorder(c: AbColors): Color =
    androidx.compose.ui.graphics.lerp(c.accent, c.textDim, 0.85f).copy(alpha = if (c.dark) 0.10f else 0.09f)

/** Обводка включённой кнопки (таймер идёт / точка задана): акцент, но мягче прежнего. */
fun activeButtonBorder(c: AbColors): Color =
    androidx.compose.ui.graphics.lerp(c.accent, c.textDim, 0.35f).copy(alpha = 0.4f)

/** Заливка заглушки обложки: на светлой теме — очень лёгкий акцентный градиент, на тёмной — как раньше. */
fun placeholderCoverColors(c: AbColors): List<Color> =
    if (c.dark) listOf(androidx.compose.ui.graphics.lerp(c.surface3, c.accent2, 0.10f), c.surface)
    else listOf(
        androidx.compose.ui.graphics.lerp(c.surface3, c.accent, 0.09f),
        androidx.compose.ui.graphics.lerp(c.surface, c.accent, 0.025f)
    )

@Composable
fun DashBox(label: String, valueMs: Long?, onClick: () -> Unit) {
    val c = LocalColors.current
    val set = valueMs != null
    val shape = RoundedCornerShape(9.dp)
    Pressable(
        // высота как у кнопки таймера сна (40dp)
        modifier = Modifier.width(56.dp).height(40.dp).border(1.dp, idleButtonBorder(c), shape), // обводка не меняется, когда точка задана
        normal = timerButtonBg(c),
        pressed = c.surface3,
        shape = shape,
        contentAlignment = Alignment.TopCenter,
        onClick = onClick,
        description = "Точка $label: " + (if (valueMs != null) fmtDurLong(valueMs) else "не задана")
    ) {
        // Название — вверху с отступом, время — под ним с зазором; снизу остаётся свободное место,
        // поэтому время не липнет ни к нижнему краю, ни к названию.
        Column(
            modifier = Modifier.padding(start = 3.dp, end = 3.dp, top = 5.5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Txt(label, 8.5f, FontWeight.Bold, c.textFaint, letterSpacing = 0.5f, lineHeight = 10f)
            Box(Modifier.height(3.dp))
            Txt(
                if (valueMs != null) fmtDurLong(valueMs) else "—",
                12f, FontWeight.Bold, c.textFaint, maxLines = 1, lineHeight = 14f
            )
        }
    }
}
