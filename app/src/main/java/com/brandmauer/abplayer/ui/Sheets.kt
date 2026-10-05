package com.brandmauer.abplayer.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.brandmauer.abplayer.APP_DEVELOPER
import com.brandmauer.abplayer.AddKind
import com.brandmauer.abplayer.APP_NAME
import com.brandmauer.abplayer.APP_VERSION
import com.brandmauer.abplayer.i18n.I18n
import com.brandmauer.abplayer.i18n.LegalText
import com.brandmauer.abplayer.Hub
import com.brandmauer.abplayer.Sheet
import com.brandmauer.abplayer.VIEW_PLAYLIST
import com.brandmauer.abplayer.data.BOOKMARKS
import com.brandmauer.abplayer.data.COVER_ALL
import com.brandmauer.abplayer.data.COVER_NOTIFICATION
import com.brandmauer.abplayer.data.COVER_OFF
import com.brandmauer.abplayer.data.COVER_PLAYER
import com.brandmauer.abplayer.data.EQ_LABELS
import com.brandmauer.abplayer.data.FAVORITES
import com.brandmauer.abplayer.data.Importer
import com.brandmauer.abplayer.data.RADIO
import com.brandmauer.abplayer.util.fmtDurLong
import com.brandmauer.abplayer.util.signed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ============================================================ таймер сна

@Composable
fun SleepSheet(hub: Hub) {
    val c = LocalColors.current
    val s = hub.sleep
    SheetHandle()
    SheetHeader("Таймер сна", hub.sleepStatusText())
    Column(Modifier.padding(top = 10.dp)) {
        OptionRow(
            "Дождаться конца трека",
            {
                hub.startSleepMode("endTrack")
                hub.toast("До конца текущего трека")
                hub.closeSheet()
            },
            icon = Ic.play2, selected = s.active && s.mode == "endTrack"
        )
        OptionRow(
            "Дождаться конца плейлиста",
            {
                hub.startSleepMode("endPlaylist")
                hub.toast("До конца плейлиста")
                hub.closeSheet()
            },
            icon = Ic.list, selected = s.active && s.mode == "endPlaylist"
        )
        listOf(15 to "15 минут", 30 to "30 минут", 60 to "1 час").forEach { (m, label) ->
            OptionRow(
                label,
                {
                    hub.startSleepMinutes(m, m.toString())
                    hub.toast("Таймер сна: $m мин")
                    hub.closeSheet()
                },
                radio = s.active && s.preset == m.toString()
            )
        }
        OptionRow(
            "Задать время…",
            {
                hub.askText("Через сколько минут остановить?", "20", "Минут", "Запустить", true) { v ->
                    val n = v.trim().toIntOrNull()
                    if (n == null || n < 1 || n > 1440) {
                        hub.toast("Введите число от 1 до 1440")
                        false
                    } else {
                        hub.startSleepMinutes(n, "custom")
                        hub.toast("Таймер сна: $n мин")
                        if (hub.topSheet() is Sheet.Sleep) hub.closeSheet()
                        true
                    }
                }
            },
            radio = s.active && s.preset == "custom", divider = false
        )
    }
    // «Добавлять к таймеру»: значение, на которое тап по кнопке запущенного таймера продлевает его
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Txt("Добавлять к таймеру", 14f, color = c.text)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepBtn("–") { hub.setSleepAddMinutes(s.addMinutes - 5) }
            Txt("${s.addMinutes} минут", 13f, color = c.text, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 52.dp))
            StepBtn("+") { hub.setSleepAddMinutes(s.addMinutes + 5) }
        }
    }
    // продление постукиванием по устройству (в последнюю минуту, пока громкость плавно снижается)
    ToggleRow("Продлевать постукиванием", s.knockExtend, { hub.setSleepKnockExtend(it) })
    Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp).padding(top = 14.dp, bottom = 8.dp).alpha(if (s.knockExtend) 1f else 0.5f)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Txt("Чувствительность", 13f, color = c.textFaint)
            Txt("Макс", 13f, color = c.textFaint)
        }
        ThinSlider(
            hub.data.knockSensitivity.toFloat(),
            {
                hub.setKnockSensitivity(Math.round(it))
                if (!s.knockExtend) hub.setSleepKnockExtend(true)
            },
            0f, 100f,
            Modifier.fillMaxWidth().height(30.dp), step = 1f,
            description = "Чувствительность", valueText = "${hub.data.knockSensitivity}%"
        )
    }
    if (s.active) {
        PrimaryButton("Отключить таймер", { hub.stopSleep(true); hub.closeSheet() }, Modifier.padding(top = 10.dp), secondary = true)
    }
}

@Composable
fun StepBtn(text: String, onClick: () -> Unit) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(9.dp)
    Pressable(
        modifier = Modifier.size(30.dp).border(1.dp, c.border, shape),
        normal = c.surface2,
        pressed = c.surface3,
        shape = shape,
        contentAlignment = Alignment.Center,
        onClick = onClick,
        description = when (text) { "–", "-" -> "Уменьшить"; "+" -> "Увеличить"; else -> null }
    ) {
        Txt(text, 16f, color = c.textDim)
    }
}

// ============================================================ звук и громкость

@Composable
fun SoundSheet(hub: Hub) {
    val c = LocalColors.current
    val s = hub.data.sound
    SheetHeader("Звук и эффекты", null, onBack = { hub.closeSheet() })
    Column(Modifier.padding(top = 14.dp)) {
        ToggleRow("Точная громкость", s.volumeFineEnabled, { hub.setFineEnabled(it) }, bold = true)
        CompositionLocalProvider(LocalControlsEnabled provides s.volumeFineEnabled) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 12.dp).alpha(if (s.volumeFineEnabled) 1f else 0.5f)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // кнопки без подложки и фона; тап меняет громкость на 1%, тап по значению — сброс к 0%
                TrimBtn("-1%") { hub.setTrim(s.volumeTrim - 1) }
                Pressable(onClick = { hub.setTrim(0) }) {
                    Txt(signed(s.volumeTrim) + "%", 13f, FontWeight.Bold, c.textDim, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
                TrimBtn("+1%") { hub.setTrim(s.volumeTrim + 1) }
            }
            ThinSlider(
                s.volumeTrim.toFloat(), { hub.setTrim(Math.round(it)) }, -100f, 100f,
                Modifier.fillMaxWidth().height(30.dp), step = 1f,
                description = "Точная громкость", valueText = signed(s.volumeTrim) + "%"
            )
        }
        }

        Box(Modifier.height(8.dp))
        ToggleRow("Звук", s.soundEnabled, { hub.setSoundEnabled(it) }, bold = true)
        CompositionLocalProvider(LocalControlsEnabled provides s.soundEnabled) {
        Column(Modifier.alpha(if (s.soundEnabled) 1f else 0.5f)) {
            // тап по значению справа сбрасывает параметр к значению по умолчанию
            SoundRow("Предусиление", signed(s.preamp) + " " + I18n.t("дБ"), { hub.setPreamp(0) }) {
                ThinSlider(
                    s.preamp.toFloat(), { hub.setPreamp(Math.round(it)) }, -12f, 12f,
                    Modifier.fillMaxWidth().height(30.dp), step = 1f
                )
            }
            SoundRow("Скорость", String.format(java.util.Locale.US, "%.2f", s.speed) + "×", { hub.setSpeed(1f) }) {
                // Логарифмическая шкала (2^t): ×1.00 — «нулевая» точка, границы ×0.5 и ×3
                val t = (Math.log(s.speed.toDouble()) / Math.log(2.0)).toFloat()
                ThinSlider(
                    t, { v -> hub.setSpeed(Math.pow(2.0, v.toDouble()).toFloat()) }, -1f, (Math.log(3.0) / Math.log(2.0)).toFloat(),
                    Modifier.fillMaxWidth().height(30.dp), step = 0.01f
                )
            }
            SoundRow("Тон", String.format(java.util.Locale.US, "%.2f", s.tone) + "×", { hub.setTone(1f) }) {
                // Логарифмическая шкала (2^t): ×1.00 — «нулевая» точка, границы ×0.5 и ×1.5
                val t = (Math.log(s.tone.toDouble()) / Math.log(2.0)).toFloat()
                ThinSlider(
                    t, { v -> hub.setTone(Math.pow(2.0, v.toDouble()).toFloat()) }, -1f, (Math.log(1.5) / Math.log(2.0)).toFloat(),
                    Modifier.fillMaxWidth().height(30.dp), step = 0.01f
                )
            }
            BalanceRow(s.balance, { hub.setBalance(it) }, { hub.setBalance(0) })
        }
        }


        Box(Modifier.height(8.dp))
        ToggleRow("Эквалайзер", s.eqEnabled, { hub.setEqEnabled(it) }, bold = true)
        CompositionLocalProvider(LocalControlsEnabled provides s.eqEnabled) {
        // настройки эквалайзера запоминаются отдельно для каждого устройства вывода
        Txt(
            I18n.t("Профиль:") + " " + I18n.t(hub.eqDeviceLabel()), 11f, color = c.textFaint,
            modifier = Modifier.padding(start = 2.dp, bottom = 10.dp), maxLines = 1
        )

        // пресеты: тап — применить, долгое нажатие — меню, зажать и потянуть влево/вправо — переставить местами
        val order = hub.orderedPresets()
        var dragId by remember { mutableStateOf<String?>(null) }
        var dragOffset by remember { mutableFloatStateOf(0f) }
        var localOrder by remember { mutableStateOf<List<String>?>(null) }
        var chipWidthPx by remember { mutableStateOf(mutableMapOf<String, Float>()) }
        val shownIds = localOrder ?: order.map { it.id }
        val gapPx = with(LocalDensity.current) { 8.dp.toPx() }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (id in shownIds) {
                val p = order.firstOrNull { it.id == id } ?: continue
                val active = s.eqPreset == p.id
                val isDragging = dragId == p.id
                val shape = RoundedCornerShape(9.dp)
                val fxl = LocalFxLevel.current
                val lift by androidx.compose.animation.core.animateFloatAsState(
                    if (isDragging) 1f else 0f,
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.4f, stiffness = 380f),
                    label = "chipLift"
                )
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = if (isDragging) dragOffset else 0f
                            val k = 1f + 0.12f * fxl * lift
                            scaleX = k
                            scaleY = k
                        }
                        .zIndex(if (isDragging) 1f else 0f)
                ) {
                    Pressable(
                        modifier = Modifier
                            .height(40.dp)
                            .border(1.dp, if (active) c.accent else c.border, shape)
                            .onGloballyPositioned { chipWidthPx[p.id] = it.size.width.toFloat() }
                            .pointerInput(p.id, shownIds, s.eqEnabled) {
                                if (!s.eqEnabled) return@pointerInput
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragId = p.id; dragOffset = 0f; localOrder = shownIds },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.x
                                        val w = (chipWidthPx[p.id] ?: 80f) + gapPx
                                        var cur = (localOrder ?: shownIds).toMutableList()
                                        val idx = cur.indexOf(p.id)
                                        if (dragOffset > w / 2 && idx < cur.size - 1) {
                                            cur.removeAt(idx); cur.add(idx + 1, p.id); dragOffset -= w
                                        } else if (dragOffset < -w / 2 && idx > 0) {
                                            cur.removeAt(idx); cur.add(idx - 1, p.id); dragOffset += w
                                        }
                                        localOrder = cur
                                    },
                                    onDragEnd = {
                                        localOrder?.let { hub.reorderEqPresets(it) }
                                        dragId = null; dragOffset = 0f; localOrder = null
                                    },
                                    onDragCancel = { dragId = null; dragOffset = 0f; localOrder = null }
                                )
                            },
                        normal = c.surface2,
                        pressed = c.surface3,
                        shape = shape,
                        contentAlignment = Alignment.Center,
                        onClick = { hub.applyEqPreset(p.id) },
                        onLongClick = { hub.openEqMenu(p.id) }
                    ) {
                        Txt(
                            p.name, 13f, FontWeight.SemiBold, if (active) c.accent2 else c.textDim,
                            modifier = Modifier.padding(horizontal = 14.dp), maxLines = 1
                        )
                    }
                }
            }
            val shape = RoundedCornerShape(9.dp)
            Pressable(
                modifier = Modifier.height(40.dp).border(1.dp, c.border, shape),
                normal = c.surface2,
                pressed = c.surface3,
                shape = shape,
                contentAlignment = Alignment.Center,
                onClick = {
                    val n = s.presets.count { it.id.startsWith("eq") } + 1
                    hub.askText("Новый эквалайзер", "Эквалайзер $n", "Название", "Добавить") { v ->
                        val name = v.trim()
                        if (name.isEmpty()) {
                            hub.toast("Введите название")
                            false
                        } else {
                            hub.addEqPreset(name)
                            true
                        }
                    }
                },
                description = "Добавить эквалайзер"
            ) {
                AbIcon(Ic.plus, c.textDim, 20.dp, Modifier.padding(horizontal = 14.dp))
            }
        }

        // 10 вертикальных ползунков: длина 194dp (в 1,5 раза длиннее прежних), высота блока прежняя
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp).alpha(if (s.eqEnabled) 1f else 0.5f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            for (i in 0 until 10) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.height(204.dp).size(width = 30.dp, height = 204.dp), contentAlignment = Alignment.Center) {
                        ThinSlider(
                            value = s.eqBands[i].toFloat(),
                            onValueChange = { v -> hub.setEqBand(i, Math.round(v)) },
                            min = -12f, max = 12f, step = 1f, vertical = true,
                            modifier = Modifier.size(width = 30.dp, height = 194.dp),
                            description = "Полоса ${EQ_LABELS[i]}", valueText = signed(s.eqBands[i]) + " дБ"
                        )
                    }
                    Txt(EQ_LABELS[i], 9.5f, color = c.textFaint)
                }
            }
        }
        }
    }
}

@Composable
private fun TrimBtn(text: String, onClick: () -> Unit) {
    val c = LocalColors.current
    Pressable(
        modifier = Modifier.widthIn(min = 58.dp).height(30.dp),
        contentAlignment = Alignment.Center,
        onClick = onClick
    ) {
        Txt(text, 13f, FontWeight.Bold, c.textDim, modifier = Modifier.padding(horizontal = 6.dp))
    }
}

/** Строка Preamp/Speed/Tone: тап по значению справа сбрасывает параметр к значению по умолчанию. */
@Composable
private fun SoundRow(left: String, right: String, onResetRight: () -> Unit, slider: @Composable () -> Unit) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Txt(left, 13f, color = c.text)
            Pressable(onClick = onResetRight, description = "Сбросить: $left") {
                Txt(right, 13f, color = c.textDim, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        // подпись и значение для TalkBack берёт сам ползунок
        CompositionLocalProvider(LocalSliderInfo provides SliderInfo(left, right)) { slider() }
    }
}

/** Строка Left ↔ Right: текущее значение баланса показано по центру, тап по нему — сброс к центру. */
@Composable
private fun BalanceRow(value: Int, onChange: (Int) -> Unit, onReset: () -> Unit) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Txt("Левый", 13f, color = c.text)
            Pressable(onClick = onReset, description = "Сбросить: Баланс") {
                Txt(
                    if (value == 0) "по центру" else signed(value), 13f, FontWeight.Bold, c.textDim,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Txt("Правый", 13f, color = c.text)
        }
        ThinSlider(
            value.toFloat(), { onChange(Math.round(it)) }, -100f, 100f,
            Modifier.fillMaxWidth().height(30.dp), step = 1f,
            description = "Баланс", valueText = if (value == 0) "по центру" else signed(value)
        )
    }
}

// ============================================================ эффекты интерфейса

/** Шторка «Эффекты»: ползунки от «Откл» до «Макс» (оформление как у шторки звуковых эффектов). Тап по значению — сброс. */
@Composable
fun EffectsSheet(hub: Hub) {
    val d = hub.data
    val view = androidx.compose.ui.platform.LocalView.current
    SheetHeader("Эффекты", null, onBack = { hub.closeSheet() })
    Column(Modifier.padding(top = 14.dp)) {
        // общая сила визуальных эффектов: тени, зерно, пружины, резиновый скролл; 0 — все визуальные эффекты выключены
        FxRow("Зернистость фона", d.fxLevel, { FxRuntime.level = effectiveFx(0.5f); hub.setFxLevel(0.5f) }) {
            FxRuntime.level = effectiveFx(it / 100f); hub.setFxLevel(it / 100f)
        }
        // отпустили — пробный «клик» / вибрация новой силы
        FxRow("Звуки интерфейса", d.fxSound, { FxRuntime.sound = 0.5f; hub.setFxSound(0.5f) }, onFinished = { fxFeedback(view) }) {
            FxRuntime.sound = it / 100f; hub.setFxSound(it / 100f)
        }
        FxRow("Тактильный отклик", d.fxHaptic, { FxRuntime.haptic = 0.5f; hub.setFxHaptic(0.5f) }, onFinished = { fxFeedback(view, sound = false) }) {
            FxRuntime.haptic = it / 100f; hub.setFxHaptic(it / 100f)
        }
        // фон из обложки: действует и на стандартный фон-свечение, когда обложки выключены или у трека нет картинки
        FxRow("Размытие фона обложки", d.fxBackdropBlur, { hub.setFxBackdropBlur(0.8f) }) { hub.setFxBackdropBlur(it / 100f) }
        // ползунок показывает ПРОЗРАЧНОСТЬ: влево 0% (фон виден), вправо 100% (фона не видно); в данных хранится непрозрачность
        FxRow(
            "Прозрачность фона обложки", 1f - d.fxBackdropAlpha, { hub.setFxBackdropAlpha(0.2f) },
            percent = true
        ) { hub.setFxBackdropAlpha(1f - it / 100f) }
    }
}

/**
 * Строка шторки «Эффекты»: ровно та же строка и тот же ползунок, что в шторке звука (SoundRow + ThinSlider без особых параметров).
 * Как и выключенные разделы звука, строка приглушается (прозрачность 50%), когда значение «Откл», — но ползунок остаётся рабочим.
 */
@Composable
private fun FxRow(
    label: String, value: Float, onReset: () -> Unit, onFinished: () -> Unit = {},
    percent: Boolean = false, onChange: (Float) -> Unit
) {
    // percent = true: подпись всегда «N%» и строка не приглушается на нуле (0% прозрачности — это обычное значение)
    Column(Modifier.alpha(if (percent || value > 0f) 1f else 0.5f)) {
        SoundRow(label, if (percent) "${Math.round(value * 100f)}%" else fxLevelLabel(value), onReset) {
            ThinSlider(
                value * 100f, onChange, 0f, 100f,
                Modifier.fillMaxWidth().height(30.dp), step = 5f,
                onFinished = onFinished
            )
        }
    }
}

// ============================================================ A ↔ B
// ============================================================ A ↔ B

@Composable
fun AbSheet(hub: Hub) {
    val c = LocalColors.current
    val ab = hub.ab
    SheetHandle()
    SheetHeader("Точки A ↔ B", "Повтор фрагмента трека")
    Column(Modifier.padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // тап по карточке ставит точку на текущем месте трека (отдельных кнопок «Установить» больше нет)
            AbValueBox("УСТАНОВИТЬ A", ab.aMs, Modifier.weight(1f)) { hub.abSetA() }
            AbValueBox("УСТАНОВИТЬ B", ab.bMs, Modifier.weight(1f)) { hub.abSetB() }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            ChipButton("Очистить", { hub.abClear() }, Modifier.weight(1f))
        }
        AbToggleRow("Увеличить A ↔ B", ab.zoom, Modifier.padding(top = 14.dp)) { hub.abToggle(false, it) }
        AbToggleRow("Зациклить A ↔ B", ab.active, Modifier.padding(top = 10.dp)) { hub.abToggle(true, it) }
    }
}

@Composable
private fun AbValueBox(label: String, valueMs: Long?, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(14.dp)
    Pressable(
        modifier = modifier.border(1.dp, c.border, shape),
        normal = c.surface2,
        pressed = c.accentSoft,
        shape = shape,
        contentAlignment = Alignment.Center,
        onClick = onClick
    ) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(label, 11f, FontWeight.Bold, c.header, letterSpacing = 0.5f)
            Txt(
                if (valueMs != null) fmtDurLong(valueMs) else "—", 19f, FontWeight.ExtraBold, c.text,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** Строка-переключатель: нажатие на любое место строки (не только на сам переключатель) срабатывает. */
@Composable
private fun AbToggleRow(label: String, checked: Boolean, modifier: Modifier, onChange: (Boolean) -> Unit) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(14.dp)
    Pressable(
        modifier = modifier.fillMaxWidth().border(1.dp, c.border, shape),
        normal = c.surface2,
        pressed = c.accentSoft,
        shape = shape,
        onClick = { onChange(!checked) },
        checked = checked
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Txt(label, 14f, color = c.text)
            AbSwitch(checked, onChange)
        }
    }
}

// ============================================================ сортировка

@Composable
fun SortSheet(hub: Hub, plid: String) {
    val c = LocalColors.current
    val cur = hub.getSort(plid)
    val curGroup = hub.getGroup(plid)
    val modes = listOf(
        "custom" to "По порядку добавления", "name" to "По названию",
        "duration" to "По длительности", "size" to "По размеру"
    )
    val groups = listOf("none" to "Без группировки", "folder" to "По папкам", "type" to "По типу")
    SheetHandle()
    SheetHeader("Сортировка")
    Column(Modifier.padding(top = 8.dp)) {
        modes.forEachIndexed { i, (k, label) ->
            OptionRow(label, { hub.setSort(plid, k, null) }, radio = cur.mode == k, divider = i != modes.size - 1)
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChipButton("По возрастанию", { hub.setSort(plid, null, "asc") }, Modifier.weight(1f), highlighted = cur.dir == "asc")
        ChipButton("По убыванию", { hub.setSort(plid, null, "desc") }, Modifier.weight(1f), highlighted = cur.dir == "desc")
    }
    Txt("ГРУППИРОВКА", 11f, FontWeight.Bold, c.header, letterSpacing = 1f, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
    Column {
        groups.forEachIndexed { i, (k, label) ->
            OptionRow(label, { hub.setGroup(plid, k) }, radio = curGroup.mode == k, divider = i != groups.size - 1)
        }
    }
}

// ============================================================ меню трека / плейлиста

@Composable
fun TrackMenuSheet(hub: Hub, id: String, view: String) {
    val t = hub.trackById(id)
    if (t == null) {
        Box(Modifier.height(1.dp))
        return
    }
    val plid = hub.viewPlid(view)
    SheetHandle()
    SheetHeader(t.title, t.artist)
    Column(Modifier.padding(top = 8.dp)) {
        OptionRow("Воспроизвести", { hub.playTrackFromView(view, id) }, icon = Ic.play2)
        if (t.isRadio) {
            OptionRow("Изменить", { hub.replaceSheet(Sheet.RadioEdit(id)) }, icon = Ic.edit)
        }
        OptionRow(
            if (t.favorite) "Убрать из избранного" else "Добавить в избранное",
            { hub.menuToggleFavorite(id) },
            icon = if (t.favorite) Ic.heartFill else Ic.heart
        )
        OptionRow("Добавить в плейлист…", { hub.replaceSheet(Sheet.AddToPlaylist(listOf(id))) }, icon = Ic.folder)
        OptionRow("Информация о треке", { hub.replaceSheet(Sheet.TrackInfo(id)) }, icon = Ic.info)
        if (plid == BOOKMARKS) {
            OptionRow("Удалить закладку", { hub.menuRemoveBookmark(id) }, icon = Ic.close)
        } else if (plid != FAVORITES && plid != RADIO && plid != BOOKMARKS) {
            OptionRow("Удалить из плейлиста", { hub.menuRemoveFromPlaylist(id, plid) }, icon = Ic.close)
        }
        OptionRow(
            if (t.isRadio) "Удалить станцию" else "Удалить файл с диска",
            { hub.menuDelete(id) }, icon = Ic.trash, divider = false
        )
    }
}

@Composable
fun AddToPlaylistSheet(hub: Hub, idsIn: List<String>) {
    val c = LocalColors.current
    val ids = idsIn.filter { hub.trackById(it) != null }
    val playlists = hub.data.playlists
    SheetHandle()
    SheetHeader("Добавить в плейлист")
    Column(Modifier.padding(top = 8.dp)) {
        if (playlists.isEmpty()) {
            Txt("Плейлистов пока нет — создайте первый.", 13f, color = c.textFaint, modifier = Modifier.padding(horizontal = 2.dp, vertical = 14.dp))
        }
        playlists.forEachIndexed { i, p ->
            val allIn = ids.isNotEmpty() && ids.all { hub.trackById(it)?.playlists?.contains(p.id) == true }
            Column(Modifier.fillMaxWidth()) {
                Pressable(
                    modifier = Modifier.fillMaxWidth(),
                    pressed = c.surface2,
                    onClick = { hub.toggleMembership(p.id, ids) },
                    stateText = if (allIn) "Выбрано" else "Не выбрано"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Txt(p.name, 14f, color = c.text, modifier = Modifier.weight(1f), maxLines = 1)
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .border(1.5.dp, if (allIn) c.accent else c.textFaint, RoundedCornerShape(6.dp))
                                .background(if (allIn) c.accent else Color.Transparent, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (allIn) AbIcon(Ic.check, c.onAccent, 12.dp)
                        }
                    }
                }
                if (i != playlists.size - 1) Divider1()
            }
        }
    }
    PrimaryButton("Создать новый плейлист", { hub.newPlaylist(ids) }, Modifier.padding(top = 14.dp))
}

@Composable
fun PlaylistMenuSheet(hub: Hub, plid: String, view: String?) {
    if (!hub.listExists(plid)) {
        Box(Modifier.height(1.dp))
        return
    }
    SheetHandle()
    SheetHeader(hub.listName(plid))
    Column(Modifier.padding(top = 8.dp)) {
        OptionRow("Воспроизвести", { hub.playPlaylist(plid, false) }, icon = Ic.play2)
        OptionRow("Перемешать и играть", { hub.playPlaylist(plid, true) }, icon = Ic.shuffle)
        if (plid != BOOKMARKS && plid != RADIO) {
            OptionRow("Добавить файл", { hub.addToPlaylistDirect(AddKind.FILES, plid) }, icon = Ic.music)
            OptionRow("Добавить папку", { hub.addToPlaylistDirect(AddKind.FOLDER, plid) }, icon = Ic.folder)
        }
        if (plid != BOOKMARKS) {
            OptionRow("Сортировка и группировка", { hub.replaceSheet(Sheet.Sort(plid)) }, icon = Ic.sort)
        }
        if (plid != RADIO) {
            OptionRow("Обновить плейлист", { hub.refreshPlaylist(plid) }, icon = Ic.repeat)
        }
        OptionRow("Добавить радиопоток", { hub.closeSheet(); hub.addRadioToPlaylistPrompt(plid) }, icon = Ic.volume)
        OptionRow("Переименовать", { hub.closeSheet(); hub.renamePlaylist(plid) }, icon = Ic.edit)
        OptionRow("Удалить", { hub.closeSheet(); hub.deletePlaylist(plid) }, icon = Ic.trash, divider = false)
    }
}

// ============================================================ шаг перемотки, о приложении, меню

@Composable
fun SeekStepSheet(hub: Hub) {
    val cur = hub.data.seekStepSec
    val steps = listOf(5, 10, 15, 30, 60)
    val custom = !steps.contains(cur)
    SheetHandle()
    SheetHeader("Шаг перемотки", "Для кнопок «назад» и «вперёд» на главном экране")
    Column(Modifier.padding(top = 8.dp)) {
        steps.forEach { n ->
            OptionRow("$n секунд", { hub.setSeekStep(n); hub.closeSheet() }, radio = cur == n)
        }
        OptionRow(
            if (custom) "Другое · $cur с" else "Другое…",
            {
                hub.askText("Шаг перемотки, секунд", cur.toString(), "От 1 до 300", "Сохранить", true) { v ->
                    val n = v.trim().toIntOrNull()
                    if (n == null || n < 1 || n > 300) {
                        hub.toast("Введите число от 1 до 300")
                        false
                    } else {
                        hub.setSeekStep(n)
                        if (hub.topSheet() is Sheet.SeekStep) hub.closeSheet()
                        true
                    }
                }
            },
            radio = custom, divider = false
        )
    }
}

// ============================================================ цветовой акцент

/**
 * Приглушённая палитра: все цвета близки по насыщенности и яркости и «дружат» с индиго #6575EE.
 * null в argb — цвет темы по умолчанию (индиго).
 */
private class AccentPreset(val name: String, val argb: Long?, val color: Color)

private val ACCENT_PRESETS = listOf(
    AccentPreset("Индиго (по умолчанию)", null, Color(0xFF6575EE)),
    AccentPreset("Фиолетовый", 0xFF9B7BD9, Color(0xFF9B7BD9)),
    AccentPreset("Розовый", 0xFFD17AA3, Color(0xFFD17AA3)),
    AccentPreset("Оранжевый", 0xFFD69272, Color(0xFFD69272)),
    AccentPreset("Янтарный", 0xFFC7AA6A, Color(0xFFC7AA6A)),
    AccentPreset("Изумрудный", 0xFF6BBB98, Color(0xFF6BBB98)),
    AccentPreset("Голубой", 0xFF6CAECB, Color(0xFF6CAECB))
)

// Пределы ползунка яркости: совсем чёрный акцент на тёмной теме не виден, поэтому снизу ограничение.
private const val ACCENT_VAL_MIN = 0.3f

// Цвет темы по умолчанию (индиго) — от него стартуют ползунки, пока свой цвет не выбран
private const val ACCENT_DEFAULT_ARGB = 0xFF6575EEL

private fun hsvArgbInt(hue: Float, sat: Float, value: Float): Int =
    android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))

private fun hsvColor(hue: Float, sat: Float, value: Float): Color = Color(hsvArgbInt(hue, sat, value))

/** ARGB (как Int) в Long без знакового расширения — так храним в AppData.accentColor. */
private fun hsvArgbLong(hue: Float, sat: Float, value: Float): Long =
    hsvArgbInt(hue, sat, value).toLong() and 0xFFFFFFFFL

private fun accentHsv(argb: Long?): Triple<Float, Float, Float> {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV((argb ?: ACCENT_DEFAULT_ARGB).toInt(), hsv)
    return Triple(hsv[0], hsv[1], hsv[2].coerceAtLeast(ACCENT_VAL_MIN))
}

@Composable
fun AccentColorSheet(hub: Hub) {
    val c = LocalColors.current
    val current = hub.data.accentColor
    // оттенок, насыщенность и яркость держим отдельно: у серого цвета оттенок иначе «теряется» при перетаскивании
    var hsv by remember { mutableStateOf(accentHsv(current)) }
    var lastApplied by remember { mutableStateOf(current) }
    // цвет сменили снаружи (кружок-пресет) — подтягиваем ползунки к нему
    LaunchedEffect(current) {
        if (current != lastApplied) {
            lastApplied = current
            hsv = accentHsv(current)
        }
    }
    fun apply(h: Float, sat: Float, v: Float) {
        hsv = Triple(h, sat, v)
        val argb = hsvArgbLong(h, sat, v)
        lastApplied = argb
        hub.setAccentColor(argb) // применяется сразу, без кнопки «Готово»
    }
    val (hue, sat, value) = hsv

    SheetHandle()
    SheetHeader("Цветовой акцент", "Оттенок, насыщенность и яркость всего интерфейса")
    Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        // 7 кружков в одну строку: делят всю ширину поровну, как и полосы подбора ниже
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ACCENT_PRESETS.forEach { preset ->
                val selected = if (preset.argb == null) current == null else current == preset.argb
                Pressable(
                    modifier = Modifier
                        .size(38.dp)
                        .border(2.dp, if (selected) c.text else Color.Transparent, CircleShape)
                        .padding(3.dp)
                        .background(preset.color, CircleShape),
                    shape = CircleShape,
                    contentAlignment = Alignment.Center,
                    onClick = { hub.setAccentColor(preset.argb) },
                    description = preset.name,
                    stateText = if (selected) "Выбрано" else null
                ) {
                    if (selected) AbIcon(Ic.check, Color.White, 16.dp)
                }
            }
        }

        Txt("Или подберите свой оттенок", 12f, color = c.textDim, modifier = Modifier.padding(bottom = 10.dp))
        GradientSlider(
            frac = hue / 360f,
            onFrac = { f -> apply(f * 359.9f, sat, value) },
            stops = remember(sat, value) { (0..12).map { hsvColor(it * 30f, sat, value) } },
            thumbColor = hsvColor(hue, sat, value),
            description = "Оттенок",
            valueText = "${Math.round(hue)}°"
        )

        Txt("Насыщенность", 12f, color = c.textDim, modifier = Modifier.padding(top = 16.dp, bottom = 10.dp))
        GradientSlider(
            frac = sat,
            onFrac = { f -> apply(hue, f, value) },
            stops = remember(hue, value) { listOf(hsvColor(hue, 0f, value), hsvColor(hue, 1f, value)) },
            thumbColor = hsvColor(hue, sat, value),
            description = "Насыщенность",
            valueText = "${Math.round(sat * 100f)}%"
        )

        Txt("Яркость", 12f, color = c.textDim, modifier = Modifier.padding(top = 16.dp, bottom = 10.dp))
        GradientSlider(
            frac = ((value - ACCENT_VAL_MIN) / (1f - ACCENT_VAL_MIN)).coerceIn(0f, 1f),
            onFrac = { f -> apply(hue, sat, ACCENT_VAL_MIN + f * (1f - ACCENT_VAL_MIN)) },
            stops = remember(hue, sat) { listOf(hsvColor(hue, sat, ACCENT_VAL_MIN), hsvColor(hue, sat, 1f)) },
            thumbColor = hsvColor(hue, sat, value),
            description = "Яркость",
            valueText = "${Math.round(value * 100f)}%"
        )
    }
}

/** Горизонтальная полоса с градиентом и цветным бегунком: тап/протяжка сразу задаёт долю 0..1. */
@Composable
private fun GradientSlider(
    frac: Float,
    onFrac: (Float) -> Unit,
    stops: List<Color>,
    thumbColor: Color,
    description: String,
    valueText: String
) {
    val onChange = rememberUpdatedState(onFrac)
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableStateOf(frac) }
    val shown = (if (dragging) local else frac).coerceIn(0f, 1f)
    val thumb = 28.dp
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(thumb)
            .semantics {
                contentDescription = I18n.t(description)
                stateDescription = valueText
                progressBarRangeInfo = ProgressBarRangeInfo(frac.coerceIn(0f, 1f), 0f..1f, 19)
                setProgress { target -> onChange.value(target.coerceIn(0f, 1f)); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val thumbPx = thumb.toPx()
                    fun report(x: Float) {
                        val span = (size.width - thumbPx).coerceAtLeast(1f)
                        val f = ((x - thumbPx / 2f) / span).coerceIn(0f, 1f)
                        local = f
                        onChange.value(f)
                    }
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging = true
                    report(down.position.x)
                    down.consume()
                    drag(down.id) { change ->
                        report(change.position.x)
                        change.consume()
                    }
                    dragging = false
                }
            }
    ) {
        // Дорожка тянется между центрами крайних положений бегунка, поэтому на краях бегунок
        // не «вылезает»: он цветной и с белым кантом.
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = thumb / 2)
                .height(14.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.horizontalGradient(stops))
        )
        val thumbPx = with(LocalDensity.current) { thumb.roundToPx() }
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((shown * (constraints.maxWidth - thumbPx)).roundToInt(), 0) }
                .size(thumb)
                .background(Color.White, CircleShape)
                .padding(3.dp)
                .background(thumbColor, CircleShape)
        )
    }
}

@Composable
fun AboutSheet(hub: Hub) {
    val c = LocalColors.current
    SheetHandle()
    // Тот же вид, что у обложки-заглушки на главном экране (без своей обложки трека).
    Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(placeholderCoverColors(c))),
            contentAlignment = Alignment.Center
        ) {
            AbIcon(Ic.music, c.accent2.copy(alpha = 0.85f), 34.dp)
        }
    }
    Txt("О приложении", 16f, FontWeight.ExtraBold, c.text, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), textAlign = TextAlign.Center)
    Column {
        AboutRow("Название", APP_NAME, true)
        AboutRow("Версия", APP_VERSION, true)
        AboutRow("Разработчик", APP_DEVELOPER, true)
        AboutLinkRow("Написать разработчику", ABOUT_BOT_URL, true)
        AboutLinkRow("Поддержать проект", ABOUT_SITE_URL, true)
        AboutNavRow(LegalText.PRIVACY_TITLE, true) { hub.openSheet(Sheet.Privacy) }
        AboutNavRow(LegalText.LICENSES_TITLE, false) { hub.openSheet(Sheet.Licenses) }
    }
    PrimaryButton("Закрыть", { hub.closeSheet() }, Modifier.padding(top = 12.dp), secondary = true)
}

private const val ABOUT_BOT_URL = "https://t.me/abplayerbot"
private const val ABOUT_SITE_URL = "https://brandmauer.github.io"

/** Нажимаемая строка «О приложении»: открывает ссылку во внешнем приложении или браузере. */
@Composable
private fun AboutLinkRow(label: String, url: String, divider: Boolean) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        Pressable(
            modifier = Modifier.fillMaxWidth(),
            pressed = c.surface2,
            onClick = {
                try {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {
                    // нет приложения/браузера для открытия ссылки
                }
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Txt(label, 14f, color = c.textDim)
                AbIcon(Ic.chevron, c.textFaint, 16.dp)
            }
        }
        if (divider) Divider1()
    }
}

/** Строка «О приложении», открывающая вложенную шторку (политика конфиденциальности, лицензии). */
@Composable
private fun AboutNavRow(label: String, divider: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth()) {
        Pressable(modifier = Modifier.fillMaxWidth(), pressed = c.surface2, onClick = onClick) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Txt(label, 14f, color = c.textDim, modifier = Modifier.weight(1f))
                AbIcon(Ic.chevron, c.textFaint, 16.dp)
            }
        }
        if (divider) Divider1()
    }
}

@Composable
private fun AboutRow(label: String, value: String, divider: Boolean) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Txt(label, 14f, color = c.textDim)
            Txt(value, 14f, FontWeight.Bold, c.text)
        }
        if (divider) Divider1()
    }
}

@Composable
fun MenuSheet(hub: Hub, onSettingsScreen: Boolean, dark: Boolean) {
    SheetHandle()
    SheetHeader("ABPlayer")
    Column(Modifier.padding(top = 8.dp)) {
        if (!onSettingsScreen) {
            OptionRow("Плейлисты и Настройки", { hub.closeAllSheets(); hub.navigate(0) }, icon = Ic.list)
        }
        OptionRow("Таймер сна", { hub.replaceSheet(Sheet.Sleep) }, icon = Ic.clock)
        OptionRow("Звук и эффекты", { hub.replaceSheet(Sheet.Sound) }, icon = Ic.volume)
        OptionRow(hub.data.nameFavorites, { hub.replaceSheet(Sheet.ListSheet(FAVORITES)) }, icon = Ic.heart)
        OptionRow(hub.data.nameBookmarks, { hub.replaceSheet(Sheet.ListSheet(BOOKMARKS)) }, icon = Ic.bookmark)
        OptionRow("Помощь", { hub.replaceSheet(Sheet.Help) }, icon = Ic.help)
        OptionRow("О приложении", { hub.replaceSheet(Sheet.About) }, icon = Ic.info, divider = false)
    }
}

// ============================================================ редактирование радиостанции

@Composable
fun RadioEditSheet(hub: Hub, id: String) {
    val t = hub.trackById(id)
    if (t == null) {
        Box(Modifier.height(1.dp))
        return
    }
    SheetHandle()
    SheetHeader("Изменить станцию", t.title)
    Column(Modifier.padding(top = 8.dp)) {
        OptionRow(
            "Изменить название",
            {
                hub.askText("Название станции", t.title, "Название", "Сохранить") { v ->
                    val name = v.trim()
                    if (name.isEmpty()) {
                        hub.toast("Введите название")
                        false
                    } else {
                        hub.renameRadioTrack(id, name)
                        true
                    }
                }
            },
            icon = Ic.edit
        )
        OptionRow(
            "Адрес потока",
            {
                hub.askText("Адрес потока", t.uri, "https://...", "Сохранить") { v ->
                    val url = v.trim()
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        hub.toast("Введите ссылку, начинающуюся с http:// или https://")
                        false
                    } else {
                        hub.setRadioTrackUrl(id, url)
                        true
                    }
                }
            },
            icon = Ic.volume
        )
        OptionRow("Указать обложку", { hub.requestCoverPick(id) }, icon = Ic.music, divider = false)
    }
}

// ============================================================ энергосбережение

fun powerProfileLabel(p: String): String = when (p) {
    "max" -> "Максимальное"
    "save" -> "Энергосбережение"
    "balanced" -> "Баланс"
    "quality" -> "Качество"
    else -> "Ручной режим"
}

@Composable
fun PowerSheet(hub: Hub) {
    val c = LocalColors.current
    val p = hub.data.power
    SheetHandle()
    SheetHeader(
        "Энергосбережение",
        if (hub.powerAutoActive()) "Сейчас активно по автоматике: " + powerProfileLabel("save")
        else "Профиль: " + powerProfileLabel(p.profile)
    )
    Column(Modifier.padding(top = 6.dp)) {
        val profiles = listOf(
            "max" to "Максимальное",
            "save" to "Энергосбережение",
            "balanced" to "Баланс",
            "quality" to "Качество",
            "manual" to "Ручной режим"
        )
        profiles.forEachIndexed { i, (id, label) ->
            OptionRow(label, { hub.setPowerProfile(id) }, radio = p.profile == id, divider = i != profiles.lastIndex)
        }
    }
    if (p.profile == "manual") {
        Box(Modifier.padding(top = 14.dp, bottom = 4.dp)) {
            Txt("РУЧНЫЕ ПАРАМЕТРЫ", 12f, FontWeight.Bold, c.header, letterSpacing = 0.5f)
        }
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().a11yRow().padding(vertical = 9.dp).alpha(if (hub.offloadSupported()) 1f else 0.5f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Txt("Аппаратное декодирование", 14f, color = c.text)
                AbSwitch(p.offload && hub.offloadSupported()) { hub.setPowerOffload(it) }
            }
            PowerLevelRow("Нагрузка CPU", p.cpu, listOf("Мин", "Обычно", "Макс")) { hub.setPowerCpu(it) }
            PowerLevelRow("Экран", p.screen, listOf("Эконом", "Обычно", "Не гаснет")) { hub.setPowerScreen(it) }
            PowerLevelRow("Сеть и радио", p.network, listOf("Мин", "Обычно", "Макс")) { hub.setPowerNetwork(it) }
            PowerLevelRow("Фоновые процессы", p.background, listOf("Редко", "Обычно", "Часто")) { hub.setPowerBackground(it) }
        }
    }
    Box(Modifier.padding(top = 14.dp, bottom = 4.dp)) {
        Txt("АВТОМАТИКА", 12f, FontWeight.Bold, c.header, letterSpacing = 0.5f)
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().a11yRow().padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Txt("По заряду батареи", 14f, color = c.text, modifier = Modifier.weight(1f).padding(end = 12.dp))
            AbSwitch(p.autoBattery) { hub.setPowerAutoBattery(it) }
        }
        if (p.autoBattery) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Txt("Порог", 13f, color = c.textDim)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepBtn("–") { hub.setPowerAutoBatteryPercent(p.autoBatteryPercent - 5) }
                    Txt("${p.autoBatteryPercent}%", 13f, color = c.text, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 44.dp))
                    StepBtn("+") { hub.setPowerAutoBatteryPercent(p.autoBatteryPercent + 5) }
                }
            }
        }
        Divider1()
        Row(
            modifier = Modifier.fillMaxWidth().a11yRow().padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Txt("По системному режиму энергосбережения", 14f, color = c.text, modifier = Modifier.weight(1f).padding(end = 12.dp))
            AbSwitch(p.autoSystemSaver) { hub.setPowerAutoSystemSaver(it) }
        }
    }
}

@Composable
private fun PowerLevelRow(label: String, value: Int, labels: List<String>, onChange: (Int) -> Unit) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Txt(label, 14f, color = c.text)
            Txt(labels.getOrElse(value) { "" }, 13f, FontWeight.Bold, c.textDim)
        }
        ThinSlider(
            value.toFloat(), { onChange(Math.round(it).coerceIn(0, labels.lastIndex)) },
            0f, labels.lastIndex.toFloat(),
            Modifier.fillMaxWidth().height(30.dp), step = 1f,
            description = label, valueText = labels.getOrElse(value) { "" }
        )
    }
}

// ============================================================ помощь

@Composable
fun HelpSheet(hub: Hub) {
    val c = LocalColors.current
    SheetHandle()
    SheetHeader("Помощь", "Основные функции ABPlayer")
    Column(Modifier.padding(top = 6.dp).padding(bottom = 6.dp)) {
        val topics = helpTopics()
        topics.forEachIndexed { i, (title, body) ->
            Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                Txt(title, 13f, FontWeight.Bold, c.text)
                Txt(body, 12f, color = c.textDim, modifier = Modifier.padding(top = 3.dp), lineHeight = 17f)
            }
            if (i != topics.lastIndex) Divider1()
        }
    }
}

private fun helpTopics(): List<Pair<String, String>> = listOf(
    "Воспроизведение" to "Тап по треку — воспроизвести. Кнопки внизу главного экрана — пауза/воспроизведение, следующий/предыдущий трек, перемотка на несколько секунд назад/вперёд (шаг настраивается в «Плейлисты и Настройки»).",
    "Плейлисты и вкладки" to "Свайп влево/вправо на главном экране переключает «Плейлисты и Настройки» ↔ Главный ↔ текущий плейлист (по кругу). «+» добавляет файлы, папку или радиопоток; долгое нажатие на трек — выбор нескольких. При группировке по папкам стрелка справа от имени папки сворачивает её список, а долгое нажатие на имя папки сворачивает/разворачивает все папки.",
    "Избранное и закладки" to "Значок сердца на главном экране и пункт «Добавить в избранное» в меню трека (⋮) добавляют/убирают трек из избранного. Значок закладки сохраняет текущую позицию в треке и позволяет вернуться к ней позже; для радиопотоков закладки недоступны, так как это прямой эфир без фиксированной позиции.",
    "Повтор и перемешивание" to "Значок повтора переключает: без повтора → повтор плейлиста → повтор одного трека. Значок перемешивания включает случайный порядок воспроизведения.",
    "A–B и увеличение" to "Значок «AB» открывает точки повтора отрезка: A и B задают начало и конец фрагмента, который зацикливается; «Увеличить» показывает отдельный ползунок только для этого отрезка. Недоступно для радио.",
    "Звук и эффекты" to "Значок с динамиком открывает эквалайзер, тон, скорость, баланс, предусиление и тонкую подстройку громкости. Там же кнопки физической громкости можно переключить на управление этой подстройкой (см. включатель «Точная громкость»).",
    "Таймер сна" to "«Таймер сна» останавливает воспроизведение через заданное время, до конца трека или до конца плейлиста. За минуту до остановки громкость плавно снижается: сначала быстро, затем медленнее до тишины. В это время постучите по устройству дважды («тук-тук»), чтобы продлить таймер на выбранное в шторке время; чувствительность настраивается ползунком Мин–Макс, постукивание можно отключить. Для радио таймер ещё и закрывает соединение со станцией — утром кнопка запуска подключится заново.",
    "Радио" to "Раздел «Радио» — интернет-радиостанции. В меню трека для станции есть пункт «Изменить»: своё название, адрес потока и обложка. Добавить новую станцию можно кнопкой «+» или через «Добавить радиопоток» в меню плейлиста.",
    "Информация о треке" to "В меню трека (три точки) пункт «Информация о треке» показывает название, исполнителя, длительность, кодек, битрейт, путь к файлу и другие данные; долгое нажатие на значение копирует его.",
    "Энергосбережение" to "Профиль регулирует частоту обновления интерфейса, поведение экрана, сети и фоновых задач. «Максимальное» — самый экономный (редкие обновления, увеличенные буферы сети, аппаратное декодирование), «Энергосбережение» — умеренная экономия, «Баланс» — рекомендуемый, «Качество» — максимум плавности. «Ручной режим» открывает отдельные ползунки: «Нагрузка CPU» — как часто обновляется позиция на экране (реже — меньше работы процессора); «Экран» — гаснет ли экран по правилам системы или остаётся включённым; «Сеть и радио» — размер буфера потока (больше буфер — реже обращения к сети и модему; смена применяется сразу, радио при этом переподключается на секунду); «Фоновые процессы» — как часто в фоне сохраняется позиция. «Аппаратное декодирование» (Android 10+) передаёт сжатый звук DSP устройства, пока процессор спит; оно отключается при включённых эффектах звука (эквалайзер, «Звук», «Точная громкость») и повторе A–B, а при его включении в профиле показывается подсказка. Автоматика переключает на «Энергосбережение» при заряде ниже порога или при включении системного режима экономии.",
    "Как реже заряжать устройство" to "Больше всего заряда расходуют: радио по мобильной сети (4G) — модем всё время на связи, это в несколько раз дороже, чем Wi-Fi; включённый экран — слушайте с погасшим экраном; эффекты звука и повтор A–B — они заставляют декодировать звук на процессоре вместо аппаратного декодирования. Локальные файлы экономичнее радио. На ночь используйте таймер сна, а для долгого прослушивания выберите профиль «Максимальное» или «Энергосбережение» и выключите эффекты звука.",
    "Оформление" to "Тема (тёмная/светлая), язык интерфейса и цветовой акцент настраиваются в «Плейлисты и Настройки». Обложки альбомов можно включить или выключить там же.",
    "Резервная копия" to "«Экспортировать настройки» сохраняет плейлисты и настройки в файл, «Импортировать настройки» — восстанавливает их на этом или другом устройстве."
)

// ============================================================ информация о треке

@Composable
fun TrackInfoSheet(hub: Hub, id: String) {
    val t = hub.trackById(id)
    if (t == null) {
        Box(Modifier.height(1.dp))
        return
    }
    val c = LocalColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var rows by remember(id) { mutableStateOf<List<Pair<String, String>>?>(null) }
    LaunchedEffect(id) {
        rows = withContext(Dispatchers.IO) { Importer.trackInfo(context, t) }
    }
    SheetHandle()
    SheetHeader("Информация о треке", "Долгое нажатие на значение — скопировать")
    Column(Modifier.padding(top = 8.dp)) {
        val list = rows
        if (list == null) {
            Txt("Загрузка…", 13f, color = c.textFaint, modifier = Modifier.padding(vertical = 20.dp))
        } else {
            list.forEachIndexed { i, (label, value) ->
                Column(Modifier.fillMaxWidth()) {
                    Pressable(
                        modifier = Modifier.fillMaxWidth(),
                        pressed = c.surface2,
                        onClick = {},
                        onLongClick = {
                            clipboard.setText(AnnotatedString(value))
                            hub.toast("Скопировано")
                        }
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 9.dp)) {
                            Txt(label, 11f, FontWeight.Bold, c.header, letterSpacing = 0.4f)
                            Txt(value, 13f, color = c.text, modifier = Modifier.padding(top = 2.dp), maxLines = 3)
                        }
                    }
                    if (i != list.size - 1) Divider1()
                }
            }
        }
    }
}

// ============================================================ обложки

@Composable
fun CoversSheet(hub: Hub) {
    val cur = hub.data.coverMode
    SheetHandle()
    SheetHeader("Обложки", "Где показывать обложку альбома")
    Column(Modifier.padding(top = 8.dp)) {
        OptionRow("В уведомлении", { hub.setCoverMode(COVER_NOTIFICATION); hub.closeSheet() }, radio = cur == COVER_NOTIFICATION)
        OptionRow("В плеере", { hub.setCoverMode(COVER_PLAYER); hub.closeSheet() }, radio = cur == COVER_PLAYER)
        OptionRow("Везде", { hub.setCoverMode(COVER_ALL); hub.closeSheet() }, radio = cur == COVER_ALL)
        OptionRow("Не показывать", { hub.setCoverMode(COVER_OFF); hub.closeSheet() }, radio = cur == COVER_OFF)
        Divider1()
        Column(Modifier.padding(top = 10.dp)) {
            ToggleRow("Гармонизировать цвета", hub.data.coverHarmonize, { hub.setCoverHarmonize(it) }, bold = true)
        }
    }
}
