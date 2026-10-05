package com.brandmauer.abplayer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/** Цветовые токены прототипа (--bg, --surface, --accent …) для тёмной и светлой темы. */
@Immutable
class AbColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val accent: Color,
    val accent2: Color,
    val accentSoft: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    // заголовки разделов и имена папок: в светлой теме заметно темнее обычного «бледного» текста
    val header: Color,
    // имена папок в плейлистах: в светлой теме ещё темнее заголовков разделов
    val folder: Color,
    val border: Color,
    val dark: Boolean,
    // цвет текста/значков НА акцентной заливке: белый, а если акцент светлый (яркость высокая, насыщенность низкая) — тёмный
    val onAccent: Color = Color.White
)

// непрозрачность фонов (surface…surface3): чем «выше» слой, тем плотнее
private const val GLASS_DARK = 0.86f
private const val GLASS_DARK_TOP = 0.9f
private const val GLASS_LIGHT = 0.9f
private const val GLASS_LIGHT_TOP = 0.92f

val DarkColors = AbColors(
    bg = Color(0xFF0A0B12),
    // Фоны карточек, шторок, кнопок и полей чуть прозрачны: сквозь них проглядывает общий фон,
    // и контраст не «рвётся» на разных цветах подложки.
    surface = Color(0xFF151725).copy(alpha = GLASS_DARK),
    surface2 = Color(0xFF1B1E30).copy(alpha = GLASS_DARK),
    surface3 = Color(0xFF22253A).copy(alpha = GLASS_DARK_TOP),
    accent = Color(0xFF6575EE),
    accent2 = Color(0xFF8B96FF),
    accentSoft = Color(0x296674F4),
    text = Color(0xFFF2F3F8),
    // приглушённый текст на тёмной теме слегка осветлён (было 9AA0BA / 666C88)
    textDim = Color(0xFFA9AFC8),
    textFaint = Color(0xFF7C829F),
    header = Color(0xFF7C829F),
    folder = Color(0xFF7C829F),
    border = Color(0x12FFFFFF),
    dark = true
)

val LightColors = AbColors(
    bg = Color(0xFFEEF0F7),
    surface = Color(0xFFFFFFFF).copy(alpha = GLASS_LIGHT),
    surface2 = Color(0xFFF4F5FB).copy(alpha = GLASS_LIGHT),
    surface3 = Color(0xFFE9EBF5).copy(alpha = GLASS_LIGHT_TOP),
    accent = Color(0xFF5B6EEA),
    accent2 = Color(0xFF4756C9),
    accentSoft = Color(0x1F5B6EEA),
    text = Color(0xFF171A2B),
    textDim = Color(0xFF666C88),
    // Было 0x9A9FB8 — контраст к белому фону ~2.6:1, самый бледный текст (подписи EQ,
    // номера треков, время) читался как светлый шрифт на светлом фоне. Новое значение даёт ~4:1.
    textFaint = Color(0xFF7A8099),
    header = Color(0xFF3A3F58),
    folder = Color(0xFF1C2033),
    border = Color(0x14141428),
    dark = false
)

// static: при смене темы пересоздаётся всё содержимое под провайдером, ни одна строка списка
// или экран не может «пропустить» перерисовку и остаться со старым (белым) цветом текста
val LocalColors = staticCompositionLocalOf { DarkColors }

private fun scaleValue(argb: Int, factor: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    hsv[2] = (hsv[2] * factor).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

// Опорные значения штатного индиго #6575EE: при нём палитра темы остаётся ровно такой, как задумана
private const val REF_SAT = 0.576f
private const val REF_VAL = 0.933f
// доля цветности общего фона, которая остаётся у шторок, блоков настроек, кнопок и полей
private const val SURFACE_CHROMA = 0.5f
private const val MIN_VAL = 0.3f // нижний предел ползунка яркости акцента

/**
 * Перекрашивает нейтральный токен темы: оттенок берётся у акцента, насыщенность исходного цвета масштабируется
 * отношением «насыщенность акцента / насыщенность индиго», яркость — множителем vFactor. Прозрачность сохраняется.
 * Серый (насыщенность 0) остаётся серым — поэтому ноль на ползунке насыщенности убирает цвет отовсюду.
 */
private fun retint(base: Color, hue: Float, satRatio: Float, vFactor: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(base.toArgb(), hsv)
    hsv[0] = hue
    hsv[1] = (hsv[1] * satRatio).coerceIn(0f, 0.9f)
    hsv[2] = (hsv[2] * vFactor).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(Math.round(base.alpha * 255f), hsv))
}

/**
 * Глубина тона фонов от яркости акцента. Штатная яркость — без изменений; ниже — фоны темнеют (тёмная тема — до −35%,
 * светлая — до −12%, чтобы текст не терял контраст); выше — чуть светлеют.
 */
private fun surfaceDepth(accentVal: Float, dark: Boolean): Float {
    val v = accentVal.coerceIn(MIN_VAL, 1f)
    return if (v < REF_VAL) {
        val k = if (dark) 0.35f else 0.12f
        1f - k * (1f - (v - MIN_VAL) / (REF_VAL - MIN_VAL))
    } else {
        val k = if (dark) 0.15f else 0.03f
        1f + k * (v - REF_VAL) / (1f - REF_VAL)
    }
}

/**
 * Весь цвет интерфейса выводится из одного акцента (оттенок, насыщенность, яркость):
 *  • оттенок → оттенок фона, шторок, кнопок, полей, текста и разделителей;
 *  • насыщенность → сила этой подкраски (0 — приложение полностью серое);
 *  • яркость → глубина тона фонов (ниже — темнее) и яркость самого акцента.
 * Для штатного акцента (null) тема не меняется.
 */
fun applyAccentOverride(base: AbColors, accentArgb: Long?): AbColors {
    if (accentArgb == null) return base
    val argb = accentArgb.toInt()
    val accent = Color(argb)
    val accent2 = if (base.dark) scaleValue(argb, 1.18f) else scaleValue(argb, 0.8f)
    val soft = accent.copy(alpha = if (base.dark) 0.16f else 0.12f)

    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    val hue = hsv[0]
    val satRatio = hsv[1] / REF_SAT
    val depth = surfaceDepth(hsv[2], base.dark)
    fun tone(c: Color) = retint(c, hue, satRatio, 1f) // текст, разделители: только цвет
    fun surf(c: Color) = retint(c, hue, satRatio, depth) // общий фон: цвет и глубина
    // шторки, блоки настроек, кнопки и поля — заметно спокойнее по цвету, чем общий фон
    fun card(c: Color) = retint(c, hue, satRatio * SURFACE_CHROMA, depth)

    return AbColors(
        bg = surf(base.bg), surface = card(base.surface), surface2 = card(base.surface2), surface3 = card(base.surface3),
        accent = accent, accent2 = accent2, accentSoft = soft,
        text = tone(base.text), textDim = tone(base.textDim), textFaint = tone(base.textFaint),
        header = tone(base.header), folder = tone(base.folder), border = tone(base.border),
        dark = base.dark,
        onAccent = if (accent.luminance() > 0.55f) Color(0xFF171A2B) else Color.White
    )
}

@Composable
fun AbTheme(dark: Boolean, accentOverride: Long? = null, content: @Composable () -> Unit) {
    // remember: один и тот же объект цветов, пока не сменились тема/акцент — иначе при каждой
    // перерисовке корня создавался новый AbColors, и ленивые списки получали «скачущие» цвета
    val colors = remember(dark, accentOverride) { applyAccentOverride(if (dark) DarkColors else LightColors, accentOverride) }
    val scheme = if (dark) {
        darkColorScheme(primary = colors.accent, background = colors.bg, surface = colors.surface.copy(alpha = 1f), onSurface = colors.text)
    } else {
        lightColorScheme(primary = colors.accent, background = colors.bg, surface = colors.surface.copy(alpha = 1f), onSurface = colors.text)
    }
    CompositionLocalProvider(LocalColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
