package com.brandmauer.abplayer.ui

import com.brandmauer.abplayer.i18n.I18n

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick as semOnClick
import androidx.compose.ui.semantics.onLongClick as semOnLongClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================ текст и иконки

@Composable
fun Txt(
    text: String,
    size: Float = 14f,
    weight: FontWeight = FontWeight.Normal,
    color: Color = LocalColors.current.text,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
    letterSpacing: Float = 0f,
    lineHeight: Float? = null,
    marquee: Boolean = false,
    /** > 0 — однократная прокрутка строки, если она не помещается; каждое новое значение запускает прокрутку заново. */
    scrollOnce: Int = 0,
    /** Пауза перед началом однократной прокрутки (мс). */
    scrollDelayMs: Long = 600L
) {
    // и. при включённых эффектах и наличии переменного шрифта вес плавно «набирает» +90 при нажатии на родительскую кнопку
    var family: androidx.compose.ui.text.font.FontFamily? = null
    var effWeight = weight
    if (LocalFx.current) {
        VarFont.ensure(LocalContext.current)
        if (VarFont.ready) {
            val press = LocalPressProgress.current?.invoke() ?: 0f
            val w = (weight.weight + 160f * press).toInt().coerceIn(100, 900)
            family = VarFont.family(w)
            if (family != null) effWeight = FontWeight(w)
        }
    }
    if (scrollOnce > 0) {
        ScrollOnceText(
            text = I18n.t(text), trigger = scrollOnce, delayMs = scrollDelayMs, modifier = modifier,
            color = color, fontSize = size.sp, fontFamily = family, fontWeight = effWeight,
            letterSpacing = letterSpacing.sp,
            lineHeight = if (lineHeight != null) lineHeight.sp else TextUnit.Unspecified
        )
        return
    }
    val mod = if (marquee) modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1500, velocity = 30.dp) else modifier
    Text(
        text = I18n.t(text),
        modifier = mod,
        color = color,
        fontSize = size.sp,
        fontFamily = family,
        fontWeight = effWeight,
        maxLines = maxLines,
        overflow = if (marquee) TextOverflow.Clip else TextOverflow.Ellipsis,
        textAlign = textAlign,
        letterSpacing = letterSpacing.sp,
        lineHeight = if (lineHeight != null) lineHeight.sp else TextUnit.Unspecified
    )
}

/**
 * Однократная прокрутка длинной строки. В покое — обычный текст с многоточием. По каждому новому [trigger]
 * (после паузы [delayMs]), если строка не помещается, она плавно уезжает влево до конца (≈30 dp/с, как у бегущей
 * строки), на секунду замирает и быстро возвращается на место; дальше снова многоточие. Если строка помещается — ничего не происходит.
 */
@Composable
private fun ScrollOnceText(
    text: String, trigger: Int, delayMs: Long, modifier: Modifier,
    color: Color, fontSize: TextUnit, fontFamily: androidx.compose.ui.text.font.FontFamily?,
    fontWeight: FontWeight, letterSpacing: TextUnit, lineHeight: TextUnit
) {
    val density = LocalDensity.current
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val style = androidx.compose.ui.text.TextStyle(
        fontSize = fontSize, fontFamily = fontFamily, fontWeight = fontWeight,
        letterSpacing = letterSpacing, lineHeight = lineHeight
    )
    var boxW by remember { mutableStateOf(0) }
    var running by remember { mutableStateOf(false) }
    val offsetPx = remember { androidx.compose.animation.core.Animatable(0f) }

    LaunchedEffect(trigger, text) {
        // новый запуск (тап во время прокрутки) или смена текста: сначала вернуть всё на место
        running = false
        offsetPx.snapTo(0f)
        if (trigger <= 0) return@LaunchedEffect
        kotlinx.coroutines.delay(delayMs)
        val fullW = measurer.measure(
            text = androidx.compose.ui.text.AnnotatedString(text), style = style, maxLines = 1, softWrap = false,
            constraints = androidx.compose.ui.unit.Constraints()
        ).size.width
        val dist = (fullW - boxW).toFloat()
        if (boxW <= 0 || dist <= 1f) return@LaunchedEffect // помещается — прокручивать нечего
        running = true
        // медленно (≈ 20 dp/с) и с плавным разгоном/торможением (ease-in-out), а не линейным «рывком»
        val forwardMs = (dist / (with(density) { 20.dp.toPx() }) * 1000f).toInt().coerceIn(1400, 30_000)
        val smooth = androidx.compose.animation.core.CubicBezierEasing(0.45f, 0f, 0.55f, 1f)
        offsetPx.animateTo(dist, androidx.compose.animation.core.tween(forwardMs, easing = smooth))
        kotlinx.coroutines.delay(1400)
        // обратно — тоже плавно и заметно медленнее прежних 0.35 с (≈ 70 dp/с, но не быстрее 0.9 с и не дольше 3 с)
        val backMs = (dist / (with(density) { 70.dp.toPx() }) * 1000f).toInt().coerceIn(900, 3_000)
        offsetPx.animateTo(0f, androidx.compose.animation.core.tween(backMs, easing = smooth))
        running = false
    }

    Box(modifier.clipToBounds().onSizeChanged { boxW = it.width }) {
        if (running) {
            Text(
                text = text, color = color, fontSize = fontSize, fontFamily = fontFamily, fontWeight = fontWeight,
                letterSpacing = letterSpacing, lineHeight = lineHeight, maxLines = 1, softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    // сдвиг через graphicsLayer — дробный, без перерасчёта разметки на каждом кадре (раньше: целые пиксели → «дёрганье»)
                    .graphicsLayer { translationX = -offsetPx.value }
            )
        } else {
            Text(
                text = text, color = color, fontSize = fontSize, fontFamily = fontFamily, fontWeight = fontWeight,
                letterSpacing = letterSpacing, lineHeight = lineHeight, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Небольшое число точно по центру области (без «шрифтовых» отступов сверху/снизу) — для значков перемотки. */
@Composable
fun CenteredNum(text: String, size: Float, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.Center,
        style = TextStyle(
            lineHeight = size.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both
            )
        )
    )
}

@Composable
fun AbIcon(icon: ImageVector, tint: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
}

// ============================================================ нажатия

/** Подпись и значение ползунка для TalkBack, задаваемые строкой-обёрткой (см. SoundRow). */
class SliderInfo(val label: String, val value: String?)
val LocalSliderInfo = compositionLocalOf<SliderInfo?> { null }

/** false — элементы управления внутри (ползунки, кнопки с нажатием) не реагируют: «выключенный» раздел. */
val LocalControlsEnabled = compositionLocalOf { true }

/**
 * Область с нажатием, долгим нажатием и подсветкой в момент касания (аналог :active в CSS).
 * Ripple не используется — как в прототипе.
 */
@Composable
fun Pressable(
    modifier: Modifier = Modifier,
    normal: Color = Color.Transparent,
    pressed: Color = Color.Transparent,
    shape: Shape = RoundedCornerShape(0.dp),
    contentAlignment: Alignment = Alignment.TopStart,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    /** Подпись для TalkBack; если null — читается текст внутри (элементы объединяются). */
    description: String? = null,
    /** Состояние для TalkBack («Включено», «В избранном» и т. п.). */
    stateText: String? = null,
    /** Не null — элемент читается как переключатель с таким состоянием. */
    checked: Boolean? = null,
    semanticRole: Role? = Role.Button,
    content: @Composable BoxScope.() -> Unit
) {
    var down by remember { mutableStateOf(false) }
    val enabledState = rememberUpdatedState(LocalControlsEnabled.current)
    val clickState = rememberUpdatedState(onClick)
    val longState = rememberUpdatedState(onLongClick)
    val hasLong = onLongClick != null
    val fx = LocalFx.current
    val fxState = rememberUpdatedState(fx)
    val view = LocalView.current
    val press = rememberPressSpring(down, fx)
    val pressFn = remember(press) { { press.value } }
    val touchState = remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .pressScale(press, touchState)
            .then(modifier)
            .clip(shape)
            .background(if (down) pressed else normal)
            .pointerInput(hasLong) {
                detectTapGestures(
                    onPress = { off ->
                        // точка касания внутри элемента (-1..1): элемент наклоняется в её сторону
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        touchState.value = Offset((off.x / w * 2f - 1f).coerceIn(-1f, 1f), (off.y / h * 2f - 1f).coerceIn(-1f, 1f))
                        down = true
                        tryAwaitRelease()
                        down = false
                    },
                    onTap = {
                        if (enabledState.value) {
                            if (clickState.value != null) fxFeedback(view)
                            clickState.value?.invoke()
                        }
                    },
                    onLongPress = if (hasLong) ({ _: Offset ->
                        if (enabledState.value) {
                            fxFeedback(view, strong = true)
                            longState.value?.invoke()
                        }
                    }) else null
                )
            }
            .then(
                if (onClick != null || onLongClick != null) {
                    // TalkBack не видит pointerInput: описываем нажатие и долгое нажатие как действия доступности.
                    // Есть подпись — читается она (вложенный текст скрыт, чтобы не читать дважды),
                    // иначе элементы внутри объединяются и читаются как один.
                    val sem: SemanticsPropertyReceiver.() -> Unit = {
                        if (description != null) contentDescription = I18n.t(description)
                        if (stateText != null) stateDescription = I18n.t(stateText)
                        if (checked != null) {
                            toggleableState = ToggleableState(checked)
                            role = Role.Switch
                        } else if (semanticRole != null) {
                            role = semanticRole
                        }
                        if (!enabledState.value) disabled()
                        if (onClick != null) {
                            semOnClick(label = null) {
                                if (enabledState.value) { clickState.value?.invoke(); true } else false
                            }
                        }
                        if (hasLong) {
                            semOnLongClick(label = null) {
                                if (enabledState.value) { longState.value?.invoke(); true } else false
                            }
                        }
                    }
                    if (description != null) Modifier.clearAndSetSemantics(sem)
                    else Modifier.semantics(mergeDescendants = true, properties = sem)
                } else Modifier
            ),
        contentAlignment = contentAlignment
    ) {
        CompositionLocalProvider(LocalPressProgress provides pressFn) { content() }
    }
}

/** Объединяет содержимое строки в один элемент TalkBack (подпись + переключатель читаются вместе). */
fun Modifier.a11yRow(): Modifier = this.semantics(mergeDescendants = true) { }

/** Русские подписи значков для TalkBack (по имени ImageVector из Ic). */
fun iconLabel(icon: ImageVector): String? = when (icon.name) {
    "music" -> "Музыка"
    "dots" -> "Меню"
    "search" -> "Поиск"
    "plus" -> "Добавить"
    "shuffle" -> "Перемешать"
    "repeat" -> "Повтор"
    "repeatOne" -> "Повтор одного трека"
    "heart", "heartFill" -> "Избранное"
    "bookmark", "bookmarkFill" -> "Закладка"
    "list" -> "Список"
    "prev" -> "Предыдущий трек"
    "next" -> "Следующий трек"
    "play", "play2" -> "Воспроизвести"
    "pause" -> "Пауза"
    "sort" -> "Сортировка"
    "check", "check2" -> "Выбрано"
    "back" -> "Назад"
    "chevron" -> "Далее"
    "close" -> "Закрыть"
    "trash" -> "Удалить"
    "folder" -> "Папка"
    "clock" -> "Таймер сна"
    "timerOff" -> "Остановить таймер сна"
    "ab" -> "Повтор фрагмента A–B"
    "moon" -> "Тёмная тема"
    "sun" -> "Светлая тема"
    "volume" -> "Звук"
    "info" -> "Информация"
    "help" -> "Справка"
    "bolt" -> "Энергосбережение"
    "edit" -> "Изменить"
    "apply" -> "Применить"
    "rewindArc" -> "Назад"
    "forwardArc" -> "Вперёд"
    else -> null
}

/** Подсветка включённого значка: тот же цвет, что у контура неактивного значка, но полупрозрачный. */
fun activeIconBg(c: AbColors): Color = c.textDim.copy(alpha = 0.22f)

@Composable
fun IconBtn(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    onLongClick: (() -> Unit)? = null,
    tint: Color? = null,
    description: String? = null,
    stateText: String? = null
) {
    val c = LocalColors.current
    Pressable(
        modifier = modifier.size(size),
        // значки серые, без акцента; включённое состояние — подсветка и заливка цветом контура
        // неактивного значка (textDim), а не белым/чёрным
        normal = if (active) activeIconBg(c) else Color.Transparent,
        pressed = c.surface2,
        shape = RoundedCornerShape(9.dp),
        contentAlignment = Alignment.Center,
        onClick = onClick,
        onLongClick = onLongClick,
        description = description ?: iconLabel(icon),
        stateText = stateText
    ) {
        AbIcon(icon, tint ?: c.textDim, iconSize)
    }
}

// ============================================================ переключатель

@Composable
fun AbSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalColors.current
    val fx = LocalFx.current
    val l = LocalFxLevel.current
    val view = LocalView.current
    var down by remember { mutableStateOf(false) }
    val shift by animateDpAsState(
        if (checked) 18.dp else 0.dp,
        // е. пружина: бегунок переключателя «перелетает» и успокаивается (чем сильнее эффекты — тем дольше качается)
        animationSpec = if (fx) spring(dampingRatio = 0.55f - 0.27f * l, stiffness = 380f) else spring(),
        label = "switch"
    )
    // пока switch удерживают, бегунок вытягивается в «таблетку» (как капля), на отпускании пружинит обратно
    val stretch by animateDpAsState(
        if (down && fx) 9.dp * l else 0.dp,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 500f),
        label = "switchStretch"
    )
    Box(
        modifier = Modifier
            .size(44.dp, 26.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (checked) c.accent else c.surface3)
            .pointerInput(checked, fx) {
                detectTapGestures(
                    onPress = {
                        down = true
                        tryAwaitRelease()
                        down = false
                    },
                    onTap = { fxFeedback(view); onChange(!checked) }
                )
            }
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
                semOnClick(label = null) { onChange(!checked); true }
            }
    ) {
        Box(
            modifier = Modifier
                .offset(x = 3.dp + shift - (if (checked) stretch else 0.dp), y = 3.dp)
                .size(20.dp + stretch, 20.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}

// ============================================================ ползунок

/** Длина фрагмента (мс), в пределах которой «резиновая» перемотка работает 1:1; для более длинных — замедляется. */
private const val RUBBER_FREE_MS = 20 * 60 * 1000f

/**
 * Во сколько раз бегунок «отстаёт» от пальца при перемотке. Для коротких треков (до 20 минут) — 1:1,
 * дальше коэффициент падает как (20 мин / длина)^0.18: час — ×0.82, десять часов — ×0.54, не ниже ×0.3
 * (раньше: 5 минут, степень 0.4: час — ×0.37, десять часов — ×0.14, не ниже ×0.06).
 */
private fun rubberScale(spanMs: Float): Float =
    if (spanMs <= RUBBER_FREE_MS) 1f else Math.pow((RUBBER_FREE_MS / spanMs).toDouble(), 0.18).toFloat().coerceIn(0.3f, 1f)

/**
 * Тонкий ползунок как input[type=range] прототипа: дорожка 3px, бегунок 15px.
 * vertical = true — вертикальный (полосы эквалайзера), значение растёт вверх.
 * filled = true — закрашенная часть слева (ползунок трека и фрагмента A–B).
 */
@Composable
fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    min: Float,
    max: Float,
    modifier: Modifier = Modifier,
    step: Float = 0f,
    filled: Boolean = false,
    vertical: Boolean = false,
    /**
     * Если задано: когда палец, не отрываясь, уходит от ползунка дальше этого расстояния (поперёк дорожки),
     * изменение отменяется — вызывается onCancel, а onFinished при отпускании не вызывается.
     * Если палец вернуться ближе — ползунок снова следует за ним.
     */
    cancelDistance: Dp = Dp.Unspecified,
    onCancel: () -> Unit = {},
    onFinished: () -> Unit = {},
    /** Подпись и читаемое значение для TalkBack; если не заданы — берутся из LocalSliderInfo (строки звука). */
    description: String? = null,
    valueText: String? = null,
    /** Главный экран: маленький бегунок и полупрозрачное кольцо вокруг него, которое растёт при удержании. */
    ring: Boolean = false,
    /** Если задано — пока ползунок удерживают, над бегунком показывается «тост» с этим текстом для текущего значения. */
    bubbleText: ((Float) -> String)? = null,
    /**
     * «Резиновая» перемотка (горизонтальный ползунок со значением в мс): бегунок следует за пальцем не 1:1, а с коэффициентом,
     * зависящим от длины (см. rubberScale), а чем дальше палец от дорожки по вертикали — тем точнее шаг.
     */
    rubber: Boolean = false
) {
    val c = LocalColors.current
    val enabled = LocalControlsEnabled.current
    val info = LocalSliderInfo.current
    val a11yLabel = description ?: info?.label
    val a11yValue = valueText ?: info?.value
    val onChange = rememberUpdatedState(onValueChange)
    val onDone = rememberUpdatedState(onFinished)
    val onCancelState = rememberUpdatedState(onCancel)
    val valueState = rememberUpdatedState(value)
    val frac = if (max > min) ((value - min) / (max - min)).coerceIn(0f, 1f) else 0f
    // тёмная тема: неактивная шкала светлее (между цветом иконок и фоном), чтобы не сливалась с подложкой
    // светлая тема: дорожка заметно темнее (раньше почти сливалась с белой подложкой)
    val trackColor = if (c.dark) androidx.compose.ui.graphics.lerp(c.textDim, c.bg, 0.28f)
    else androidx.compose.ui.graphics.lerp(c.textDim, c.surface, 0.5f)
    val fillColor = c.accent
    val thumbColor = c.accent2
    var pressedNow by remember { mutableStateOf(false) }
    var widthPx by remember { mutableStateOf(0) }
    val fx = LocalFx.current
    val fxLevel = LocalFxLevel.current
    val fxState = rememberUpdatedState(fx)
    val viewState = rememberUpdatedState(LocalView.current)
    // е. пружина: кольцо и бегунок при нажатии «пружинят»
    val ringAnim by animateFloatAsState(
        if (pressedNow) 1f else 0f,
        animationSpec = if (fx) spring(dampingRatio = 0.5f - 0.22f * fxLevel, stiffness = Spring.StiffnessMediumLow) else spring(),
        label = "ring"
    )

    Box(
        modifier = modifier.onSizeChanged { widthPx = it.width }.semantics {
            if (a11yLabel != null) contentDescription = I18n.t(a11yLabel)
            if (a11yValue != null) stateDescription = I18n.t(a11yValue)
            val steps = if (step > 0f && max > min) (Math.round((max - min) / step) - 1).coerceAtLeast(0) else 0
            progressBarRangeInfo = ProgressBarRangeInfo(valueState.value.coerceIn(min, max), min..max, steps)
            if (!enabled) disabled()
            // действия «увеличить/уменьшить» из TalkBack
            setProgress { target ->
                if (!enabled) return@setProgress false
                var v = target
                if (step > 0f) v = min + Math.round((v - min) / step) * step
                onChange.value(v.coerceIn(min, max))
                onDone.value()
                true
            }
        }.pointerInput(min, max, step, vertical, cancelDistance, enabled, ring, rubber) {
            // выключенный раздел — ползунок не реагирует на касания
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (ring) pressedNow = true
                try {
                val r = (if (ring) 5.5.dp else 7.5.dp).toPx()
                val len = (if (vertical) size.height else size.width).toFloat()
                val usable = (len - 2 * r).coerceAtLeast(1f)
                fun toValue(p: Offset): Float {
                    val pos = if (vertical) (len - r - p.y) else (p.x - r)
                    val f = (pos / usable).coerceIn(0f, 1f)
                    var v = min + f * (max - min)
                    if (step > 0f) v = min + Math.round((v - min) / step) * step
                    return v.coerceIn(min, max)
                }
                val cancelPx = if (cancelDistance == Dp.Unspecified) Float.MAX_VALUE else cancelDistance.toPx()
                fun farAway(p: Offset): Boolean {
                    val off = if (vertical) kotlin.math.abs(p.x - size.width / 2f) else kotlin.math.abs(p.y - size.height / 2f)
                    return off > cancelPx
                }
                // Значение больше не меняется в момент касания: ползунок «включается», только когда палец
                // явно потянул вдоль дорожки. Вертикальный свайп шторки, начатый на ползунке, ему не передаётся.
                val first: PointerInputChange = if (vertical) {
                    // вертикальные ползунки (эквалайзер): тянуть можно только «за кружок» — свайп по дорожке в стороне
                    // от него остаётся жестом шторки
                    val f0 = if (max > min) ((valueState.value - min) / (max - min)).coerceIn(0f, 1f) else 0f
                    val thumbY = len - r - usable * f0
                    if (kotlin.math.abs(down.position.y - thumbY) > 30.dp.toPx()) return@awaitEachGesture
                    awaitVerticalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        ?: return@awaitEachGesture
                } else {
                    val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                    if (slop == null) {
                        // это не перетаскивание: обычный тап (отпустили почти на месте) задаёт значение по месту касания
                        val ch = currentEvent.changes.firstOrNull { it.id == down.id }
                        if (ch != null && !ch.pressed && !ch.isConsumed &&
                            (ch.position - down.position).getDistance() <= viewConfiguration.touchSlop
                        ) {
                            fxFeedback(viewState.value, sound = false)
                            onChange.value(toValue(ch.position))
                            onDone.value()
                        }
                        return@awaitEachGesture
                    }
                    slop
                }
                fxFeedback(viewState.value, sound = false)
                var cancelled = false
                val rubberOn = rubber && !vertical
                val span = max - min
                val scale = if (rubberOn) rubberScale(span) else 1f
                var cur: Float
                if (rubberOn) {
                    // схватили бегунок — тянем относительно него; схватили дорожку в стороне — бегунок сначала
                    // перескакивает под палец (как при тапе), дальше «резинка»
                    val f0 = if (span > 0f) ((valueState.value - min) / span).coerceIn(0f, 1f) else 0f
                    val thumbX = r + usable * f0
                    cur = if (kotlin.math.abs(first.position.x - thumbX) <= 28.dp.toPx()) valueState.value else toValue(first.position)
                } else {
                    cur = toValue(first.position)
                }
                onChange.value(cur)
                val fineRef = 60.dp.toPx()
                var lastTickV = cur
                drag(first.id) { change ->
                    if (rubberOn) {
                        val dx = change.position.x - change.previousPosition.x
                        val dy = kotlin.math.abs(change.position.y - size.height / 2f)
                        val fine = 1f / (1f + 1.2f * dy / fineRef)
                        cur = (cur + dx / usable * span * scale * fine).coerceIn(min, max)
                    }
                    if (farAway(change.position)) {
                        // палец далеко от ползунка — значение возвращаем к исходному и не применяем
                        if (!cancelled) {
                            cancelled = true
                            onCancelState.value()
                        }
                    } else {
                        cancelled = false
                        val nv = if (rubberOn) cur else toValue(change.position)
                        onChange.value(nv)
                        // «щелчки» по шагам: каждый новый шаг ползунка даёт лёгкий тик
                        if (step > 0f && nv != lastTickV) {
                            lastTickV = nv
                            fxTick(viewState.value)
                        }
                    }
                    change.consume()
                }
                if (!cancelled) {
                    fxFeedback(viewState.value, sound = false)
                    onDone.value()
                }
                } finally {
                    pressedNow = false
                }
            }
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = (if (ring) 5.5.dp else 7.5.dp).toPx()
            // при удержании дорожка слегка утолщается (с силой эффектов — до 3 → 4dp), с пружинным перелётом
            val th = (3.dp + (if (fx && ring) 2.dp * ringAnim else 0.dp)).toPx().coerceIn(1f, 5.dp.toPx())
            val corner = CornerRadius(th / 2f)
            if (!vertical) {
                val cy = size.height / 2f
                val usable = size.width - 2 * r
                drawRoundRect(trackColor, topLeft = Offset(0f, cy - th / 2), size = Size(size.width, th), cornerRadius = corner)
                if (filled) {
                    drawRoundRect(fillColor, topLeft = Offset(0f, cy - th / 2), size = Size(size.width * frac, th), cornerRadius = corner)
                }
                val cx = r + usable * frac
                if (ring) {
                    // полупрозрачное кольцо вокруг бегунка; при удержании увеличивается и становится заметнее
                    // (размеры — как в версии 1.0.7: от ползунка эффектов берём «сырое» значение, без потолка)
                    val raw = if (fx) (fxLevel / FX_LEVEL_CAP).coerceIn(0f, 1f) else 0f
                    val rr = 10.5.dp.toPx() + (2.5.dp.toPx() + 7.dp.toPx() * raw) * ringAnim
                    drawCircle(thumbColor.copy(alpha = (0.16f + (0.14f + 0.2f * raw) * ringAnim).coerceIn(0f, 1f)), radius = rr.coerceAtLeast(1f), center = Offset(cx, cy))
                }
                val rt = if (fx && ring) r * (1f + (0.16f + 0.5f * fxLevel) * ringAnim) else r
                drawCircle(Color.Black.copy(alpha = 0.25f), radius = rt, center = Offset(cx, cy + 2.dp.toPx()))
                drawCircle(thumbColor, radius = rt, center = Offset(cx, cy))
            } else {
                val cx = size.width / 2f
                val usable = size.height - 2 * r
                drawRoundRect(trackColor, topLeft = Offset(cx - th / 2, 0f), size = Size(th, size.height), cornerRadius = corner)
                val cy = size.height - r - usable * frac
                drawCircle(Color.Black.copy(alpha = 0.25f), radius = r, center = Offset(cx, cy + 2.dp.toPx()))
                drawCircle(thumbColor, radius = r, center = Offset(cx, cy))
            }
        }
        // «тост» с текущим значением над бегунком — пока ползунок удерживают
        if (bubbleText != null && !vertical && pressedNow && widthPx > 0) {
            val bubbleShape = RoundedCornerShape(8.dp)
            Box(
                Modifier
                    .layout { measurable, _ ->
                        val pl = measurable.measure(Constraints())
                        val rr = (if (ring) 5.5.dp else 7.5.dp).toPx()
                        val usableW = (widthPx - 2 * rr).coerceAtLeast(1f)
                        val cx = rr + usableW * frac
                        val x = (cx - pl.width / 2f).coerceIn(-8.dp.toPx(), maxOf(-8.dp.toPx(), widthPx - pl.width + 8.dp.toPx()))
                        layout(0, 0) { pl.place(x.roundToInt(), (-pl.height - 6.dp.toPx()).roundToInt()) }
                    }
                    .background(c.surface3, bubbleShape)
                    .border(1.dp, c.border, bubbleShape)
            ) {
                Txt(
                    bubbleText(value), 13f, FontWeight.Bold, c.text,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), maxLines = 1
                )
            }
        }
    }
}

// ============================================================ строки, кнопки

@Composable
fun RadioDot(on: Boolean) {
    val c = LocalColors.current
    Box(
        modifier = Modifier
            .size(19.dp)
            .border(1.5.dp, if (on) c.accent else c.textFaint, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (on) Box(Modifier.size(10.dp).background(c.accent, CircleShape))
    }
}

/** Разделитель файлов: прозрачнее обычного (множитель alphaScale к цвету границы), укорочен отступами по тексту. */
@Composable
fun SoftDivider(alphaScale: Float, modifier: Modifier = Modifier) {
    val b = LocalColors.current.border
    Box(modifier.fillMaxWidth().height(1.dp).background(b.copy(alpha = b.alpha * alphaScale)))
}

@Composable
fun Divider1(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(LocalColors.current.border))
}

/** Строка меню шторки (.option-row). radio != null — слева кружок выбора. */
@Composable
fun OptionRow(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    radio: Boolean? = null,
    selected: Boolean = false,
    divider: Boolean = true,
    onLongClick: (() -> Unit)? = null
) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth()) {
        Pressable(
            modifier = Modifier.fillMaxWidth(),
            pressed = c.surface2,
            onClick = onClick,
            onLongClick = onLongClick
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (icon != null) AbIcon(icon, c.text, 20.dp)
                if (radio != null) RadioDot(radio)
                Txt(
                    label, 14f,
                    weight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) c.accent2 else c.text,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (divider) Divider1()
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, secondary: Boolean = false) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(14.dp)
    Pressable(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .then(if (secondary) Modifier.border(1.dp, c.border, shape) else Modifier),
        normal = if (secondary) c.surface2 else c.accent,
        pressed = if (secondary) c.surface3 else c.accent.copy(alpha = 0.85f),
        shape = shape,
        contentAlignment = Alignment.Center,
        onClick = onClick
    ) {
        Txt(text, 14f, FontWeight.Bold, if (secondary) c.text else c.onAccent)
    }
}

@Composable
fun ChipButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    highlighted: Boolean = false
) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(9.dp)
    val borderColor = if (primary || highlighted) c.accent else c.border
    Pressable(
        modifier = modifier
            .height(42.dp)
            .border(1.dp, borderColor, shape),
        normal = if (primary) c.accent else c.surface2,
        pressed = if (primary) c.accent.copy(alpha = 0.85f) else c.surface3,
        shape = shape,
        contentAlignment = Alignment.Center,
        onClick = onClick
    ) {
        Txt(text, 13f, FontWeight.SemiBold, if (primary) c.onAccent else if (highlighted) c.accent2 else c.text)
    }
}

@Composable
fun BackBtn(onClick: () -> Unit) {
    val c = LocalColors.current
    Pressable(
        modifier = Modifier.size(36.dp),
        normal = c.surface2,
        pressed = c.surface3,
        shape = RoundedCornerShape(9.dp),
        contentAlignment = Alignment.Center,
        onClick = onClick
    ) {
        AbIcon(Ic.back, c.text, 20.dp)
    }
}

/** Заголовок шторки: (кнопка назад) + название + подзаголовок. */
@Composable
fun SheetHeader(title: String, sub: String? = null, onBack: (() -> Unit)? = null) {
    val c = LocalColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (onBack != null) BackBtn(onBack)
        Column {
            Txt(title, 16f, FontWeight.ExtraBold, c.text, maxLines = 1)
            if (sub != null) Txt(sub, 12f, color = c.textDim, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
fun SheetHandle() {
    Box(Modifier.fillMaxWidth().padding(bottom = 14.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.width(36.dp).height(4.dp).background(LocalColors.current.border, RoundedCornerShape(3.dp)))
    }
}

/** Блок-настройка «Заголовок + переключатель». */
@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, bold: Boolean = false) {
    val c = LocalColors.current
    Row(
        modifier = Modifier.fillMaxWidth().a11yRow().padding(start = 2.dp, end = 2.dp, top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Txt(label, 14f, if (bold) FontWeight.ExtraBold else FontWeight.Normal, c.text)
        AbSwitch(checked, onChange)
    }
}
