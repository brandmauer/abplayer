package com.brandmauer.abplayer.ui

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.Job
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.brandmauer.abplayer.data.Importer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * «Эффекты» — визуальные и тактильные эффекты интерфейса. Сила задаётся ползунком в настройках
 * (AppData.fxLevel): 0 — всё выключено, 1 — максимум. Остальные значения плавно масштабируют каждый эффект.
 */
val LocalFx = compositionLocalOf { false }

/** Текущая сила эффектов 0..1 (для композиции). */
val LocalFxLevel = compositionLocalOf { 0f }

/** Та же сила для кода вне композиции (вибрация, звук, лямбды рисования). Обновляется из AppRoot. */
object FxRuntime {
    /** Общая сила визуальных эффектов (0..1). */
    @Volatile
    var level: Float = 0f
    /** Громкость звуков интерфейса (0..1) — независима от визуальных эффектов. */
    @Volatile
    var sound: Float = 0f
    /** Сила тактильного отклика (0..1) — независима от визуальных эффектов. */
    @Volatile
    var haptic: Float = 0f
}

/** Потолок визуальных эффектов: ползунок 100% даёт лишь эту долю прежнего максимума (эффекты стали заметно мягче). */
const val FX_LEVEL_CAP = 0.7f

/** Рабочий уровень визуальных эффектов (0..FX_LEVEL_CAP) по значению ползунка 0..1. */
fun effectiveFx(sliderLevel: Float): Float = sliderLevel.coerceIn(0f, 1f) * FX_LEVEL_CAP

/** Подпись ползунка эффектов: «Откл», «35%», «Макс». */
fun fxLevelLabel(level: Float): String {
    val pct = Math.round(level * 100f)
    return if (pct <= 0) "Откл" else if (pct >= 100) "Макс" else "$pct%"
}

/** Значения эффектов при силе l (0..1). Максимум (l = 1) — самые заметные значения. */
object FxLook {
    fun grainAlpha(dark: Boolean, l: Float) = (if (dark) 0.15f else 0.12f) * l
    /**
     * Непрозрачность фона из обложки по ползунку a (0..1). Границы сдвинуты: прежний МИНИМАЛЬНЫЙ уровень
     * (0.35 от 0.40 / 0.30) теперь максимальный, а ноль — фон не виден.
     */
    fun backdropAlpha(dark: Boolean, a: Float) = (if (dark) 0.55f else 0.45f) * a
    /** Радиус размытия фона по ползунку b (0..1), dp: прежний минимум (48) теперь максимум, ноль — без размытия. */
    fun backdropBlurDp(b: Float) = 90f * b
    /** Свечение вместо обложки (обложки выключены или у трека нет картинки) — на той же шкале, что и обложка, чуть заметнее. */
    fun glowAlpha(dark: Boolean, a: Float) = (backdropAlpha(dark, a) * 1.2f).coerceIn(0f, 1f)
    fun farShadowAlpha(dark: Boolean, l: Float) = (if (dark) 0.95f else 0.55f) * l
    fun nearShadowAlpha(dark: Boolean, l: Float) = (if (dark) 0.85f else 0.50f) * l
}

// ============================================================ а. haptic и звук

/** Вибромотор напрямую (не зависит от системной настройки «вибрация при касании»). */
private object FxHaptics {
    private var vib: Vibrator? = null

    private fun get(ctx: Context): Vibrator? {
        vib?.let { return it }
        val app = ctx.applicationContext
        val v: Vibrator? = try {
            if (Build.VERSION.SDK_INT >= 31) app.getSystemService(VibratorManager::class.java)?.defaultVibrator
            else app.getSystemService(Vibrator::class.java)
        } catch (_: Throwable) {
            null
        }
        vib = v
        return v
    }

    /** kind: 0 — лёгкий «тик» (ползунок), 1 — обычный клик, 2 — сильный (долгое нажатие). */
    fun pulse(ctx: Context, l: Float, kind: Int) {
        val v = get(ctx) ?: return
        try {
            if (!v.hasVibrator()) return
            val ms = when (kind) {
                0 -> 10f + 20f * l
                1 -> 20f + 36f * l
                else -> 36f + 60f * l
            }.toLong()
            val amp = when (kind) {
                0 -> 100f + 70f * l
                1 -> 120f + 60f * l
                else -> 160f + 50f * l
            }.toInt().coerceIn(1, 255)
            if (Build.VERSION.SDK_INT >= 26) {
                val fx = if (v.hasAmplitudeControl()) VibrationEffect.createOneShot(ms, amp)
                else VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
                v.vibrate(fx)
            } else {
                @Suppress("DEPRECATION") v.vibrate(ms)
            }
        } catch (_: Throwable) {
        }
    }
}

/** Короткий «клик» собственной генерации (не зависит от системного «звука касаний»; громкость — от силы эффектов). */
private object FxSound {
    private const val RATE = 44100
    private var light: AudioTrack? = null
    private var deep: AudioTrack? = null
    private val exec = java.util.concurrent.Executors.newSingleThreadExecutor()

    private fun build(freq: Double, ms: Int): AudioTrack? {
        return try {
            val n = RATE * ms / 1000
            val pcm = ShortArray(n)
            for (i in 0 until n) {
                val t = i / RATE.toDouble()
                val env = Math.exp(-t / (ms / 1000.0 / 5.0))
                pcm[i] = (Math.sin(2.0 * Math.PI * freq * t) * env * 0.45 * Short.MAX_VALUE).toInt().toShort()
            }
            val tr = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(n * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            tr.write(pcm, 0, n)
            tr
        } catch (_: Throwable) {
            null
        }
    }

    fun click(l: Float, strong: Boolean) {
        exec.execute {
            try {
                val tr = if (strong) (deep ?: build(700.0, 45).also { deep = it }) else (light ?: build(2100.0, 28).also { light = it })
                if (tr == null) return@execute
                tr.stop()
                tr.reloadStaticData()
                tr.setVolume((0.02f + 0.12f * l).coerceIn(0f, 1f))
                tr.play()
            } catch (_: Throwable) {
            }
        }
    }
}

/**
 * Вибрация и «клик». Сила — FxRuntime.level (0 — ничего). strong — долгое нажатие,
 * sound = false — только вибрация (ползунки).
 */
fun fxFeedback(view: View, strong: Boolean = false, sound: Boolean = true) {
    val h = FxRuntime.haptic
    val s = FxRuntime.sound
    if (h > 0f) FxHaptics.pulse(view.context, h, if (strong) 2 else 1)
    if (sound && s > 0f) FxSound.click(s, strong)
}

/** Очень лёгкий «тик» без звука — по шагам ползунка. */
fun fxTick(view: View) {
    val h = FxRuntime.haptic
    if (h <= 0f) return
    FxHaptics.pulse(view.context, h, 0)
}

// ============================================================ б. зерно

private fun grainBitmap(dark: Boolean): androidx.compose.ui.graphics.ImageBitmap {
    val n = 128
    val px = IntArray(n * n)
    val rnd = java.util.Random(7L)
    val rgb = if (dark) 0xFFFFFF else 0x000000
    for (i in px.indices) px[i] = (rnd.nextInt(256) shl 24) or rgb
    return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Тонкое зерно (noise) поверх фона: белое на тёмной теме, чёрное на светлой. */
@Composable
fun GrainLayer(dark: Boolean, level: Float, modifier: Modifier = Modifier) {
    val brush = remember(dark) { ShaderBrush(ImageShader(grainBitmap(dark), TileMode.Repeated, TileMode.Repeated)) }
    val a = FxLook.grainAlpha(dark, level)
    Canvas(modifier) { drawRect(brush = brush, alpha = a) }
}

// ============================================================ е. пружина

/**
 * Пружинное «нажатие» 0..level (с заметным перелётом — на отпускании кнопка «отскакивает»).
 * При выключенных эффектах просто 0.
 */
@Composable
fun rememberPressSpring(down: Boolean, fx: Boolean): State<Float> {
    val l = LocalFxLevel.current
    return animateFloatAsState(
        targetValue = if (down && fx) l else 0f,
        animationSpec = spring(dampingRatio = 0.62f - 0.28f * l, stiffness = 420f),
        label = "pressSpring"
    )
}

/**
 * Сжатие при нажатии: маленькие кнопки — до 17%, широкие строки — до 4%. Если передан touch (-1..1 внутри элемента) —
 * элемент ещё и наклоняется в сторону пальца (до 16° у кнопок, 2.5° у строк), как продавленная пластина.
 */
fun Modifier.pressScale(press: State<Float>, touch: State<Offset>? = null): Modifier = graphicsLayer {
    val wide = size.width > 200.dp.toPx()
    val amount = if (wide) 0.04f else 0.17f
    val k = 1f - amount * press.value
    scaleX = k
    scaleY = k
    if (touch != null) {
        val deg = if (wide) 2.5f else 16f
        cameraDistance = 14f * density
        rotationX = -touch.value.y * deg * press.value
        rotationY = touch.value.x * deg * press.value
    }
}

/** 0..1 — насколько сейчас нажат ближайший Pressable; нужен для изменения веса шрифта (см. Txt). */
val LocalPressProgress = compositionLocalOf<(() -> Float)?> { null }

// ============================================================ ж. морфинг play ⇄ pause

/** Иконка play/pause: две пары вершин плавно перетекают друг в друга (progress 0 — play, 1 — pause). */
@Composable
fun PlayPauseIcon(playing: Boolean, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    val fl = LocalFxLevel.current
    val p by animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f - 0.35f * fl, stiffness = 380f),
        label = "playPause"
    )
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        fun l(a: Float, b: Float) = a + (b - a) * p
        // левая половинка: play (7,4)(13,7.43)(13,16.57)(7,20) → pause (6,4)(10.5,4)(10.5,20)(6,20)
        val left = Path().apply {
            moveTo(l(7f, 6f) * s, l(4f, 4f) * s)
            lineTo(l(13f, 10.5f) * s, l(7.43f, 4f) * s)
            lineTo(l(13f, 10.5f) * s, l(16.57f, 20f) * s)
            lineTo(l(7f, 6f) * s, l(20f, 20f) * s)
            close()
        }
        // правая половинка: play (13,7.43)(21,12)(21,12)(13,16.57) → pause (13.5,4)(18,4)(18,20)(13.5,20)
        val right = Path().apply {
            moveTo(l(13f, 13.5f) * s, l(7.43f, 4f) * s)
            lineTo(l(21f, 18f) * s, l(12f, 4f) * s)
            lineTo(l(21f, 18f) * s, l(12f, 20f) * s)
            lineTo(l(13f, 13.5f) * s, l(16.57f, 20f) * s)
            close()
        }
        val round = Stroke(width = 1.2f * s, join = StrokeJoin.Round)
        drawPath(left, tint, style = Fill)
        drawPath(left, tint, style = round)
        drawPath(right, tint, style = Fill)
        drawPath(right, tint, style = round)
    }
}

// ============================================================ з. оркестрованный вход

/**
 * Появления экрана при запуске больше нет: все блоки сразу полностью видны (значение 1).
 * Функция и параметры оставлены, чтобы не менять вызовы в MainScreen.
 */
@Composable
fun rememberEntrance(fx: Boolean, count: Int = 3): List<Animatable<Float, AnimationVector1D>> =
    remember { List(count) { Animatable(1f) } }

/** Проявление из размытия: размытие (Android 12+) растворяется, прозрачность растёт, элемент чуть «наезжает» с 94% до 100%. */
fun Modifier.fxEnter(a: Animatable<Float, AnimationVector1D>, rise: Dp = 14.dp): Modifier = graphicsLayer {
    val v = a.value
    if (v >= 1f) return@graphicsLayer
    val l = FxRuntime.level
    alpha = (v * 1.25f).coerceIn(0f, 1f)
    val s = 1f - (1f - v) * 0.06f * l
    scaleX = s
    scaleY = s
    renderEffect = if (Build.VERSION.SDK_INT >= 31 && v < 0.995f && l > 0f) {
        val r = (1f - v) * (14f + 30f * l) * density
        BlurEffect(r.coerceAtLeast(0.01f), r.coerceAtLeast(0.01f), TileMode.Decal)
    } else null
}

// ============================================================ к. многослойные тени

/**
 * Две тени: ближняя резкая и дальняя размытая; с силой эффектов растут и темнеют. Без эффектов — одна обычная тень [legacy].
 * farTint — цвет дальней тени (например, акцентный у кнопки play).
 */
@Composable
fun Modifier.layeredShadow(
    fx: Boolean,
    dark: Boolean,
    shape: Shape,
    legacy: Dp,
    farTint: Color = Color.Black
): Modifier {
    if (!fx) return this.shadow(legacy, shape)
    val l = LocalFxLevel.current
    val far = FxLook.farShadowAlpha(dark, l).coerceIn(0f, 1f)
    val near = FxLook.nearShadowAlpha(dark, l).coerceIn(0f, 1f)
    return this
        .shadow(
            elevation = legacy * (1f + 1.4f * l), shape = shape, clip = false,
            ambientColor = farTint.copy(alpha = far * 0.5f),
            spotColor = farTint.copy(alpha = far)
        )
        .shadow(
            elevation = 2.5.dp + 2.dp * l, shape = shape,
            ambientColor = Color.Black.copy(alpha = near * 0.5f),
            spotColor = Color.Black.copy(alpha = near)
        )
}

// ============================================================ г. размытый фон из обложки

/** Размер (px) стороны, до которого декодируется обложка для фона: до Android 12 это и есть размытие, с 12 — размытие делает blur. */
private fun backdropDecodePx(blur: Float): Int =
    if (Build.VERSION.SDK_INT >= 31) 512 else (8f + 504f * (1f - blur) * (1f - blur)).toInt()

/**
 * Размытая увеличенная обложка на ВЕСЬ экран, под всеми страницами (настройки, плеер, список) — поэтому она одна и та же
 * на левом и правом экранах. Неподвижна (без параллакса). Без обложки — мягкое
 * акцентное свечение. Нижний край чуть затемняется цветом фона (без резкой границы).
 */
@Composable
fun CoverBackdrop(trackId: String?, showArt: Boolean, blur: Float, alpha: Float, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    // blur, alpha — ползунки «Размытие» и «Прозрачность» из шторки «Эффекты» (0..1); действуют и на свечение без обложки
    Box(modifier.fillMaxSize().clipToBounds()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.3f
                    scaleY = 1.3f
                }
        ) {
            if (showArt && trackId != null) {
                AsyncImage(
                    // мелкий размер при декодировании + масштабирование = размытие на любом Android; на 12+ добавляем сильный blur
                    model = ImageRequest.Builder(ctx).data(Importer.coverFile(ctx, trackId))
                        // До Android 12 размытие — это уменьшение картинки: чем сильнее ползунок, тем мельче декодируем.
                        // Precision.EXACT и свой ключ кэша обязательны: иначе Coil отдавал уже загруженную полноразмерную
                        // обложку с главного экрана (она больше запрошенного размера) — фон оставался чётким, а ползунок не действовал.
                        .size(backdropDecodePx(blur))
                        .precision(Precision.EXACT)
                        .memoryCacheKey("backdrop:$trackId:${backdropDecodePx(blur)}")
                        .crossfade(300).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (Build.VERSION.SDK_INT >= 31 && blur > 0f) Modifier.blur(FxLook.backdropBlurDp(blur).dp, BlurredEdgeTreatment.Unbounded) else Modifier)
                        .alpha(FxLook.backdropAlpha(c.dark, alpha))
                )
            } else {
                val glow = FxLook.glowAlpha(c.dark, alpha)
                // Без обложки ползунок размытия меняет мягкость пятна: при нуле — чёткий круг с резким краем,
                // при максимуме — плавное свечение без видимой границы.
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val core = (0.55f * (1f - blur)).coerceIn(0f, 0.55f)
                            drawRect(
                                Brush.radialGradient(
                                    colorStops = arrayOf(
                                        0f to c.accent.copy(alpha = glow),
                                        core.coerceAtLeast(0.001f) to c.accent.copy(alpha = glow),
                                        1f to Color.Transparent
                                    ),
                                    center = Offset(size.width / 2f, size.height * 0.3f),
                                    radius = size.width * 0.8f
                                )
                            )
                        }
                )
            }
        }
        // лёгкое затемнение низа, чтобы нижние кнопки читались, — без полного «обрезания» фона
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to c.bg.copy(alpha = 0.45f)
                    )
                )
            }
        )
    }
}

// ============================================================ и. вес шрифта (variable font)

/**
 * Переменный шрифт для плавного изменения веса при нажатии. Берётся из assets/fonts/ab_variable.ttf (например, Roboto
 * variable с осью wght); если файла нет или Android ниже 8.0 (API 26) — работает обычный шрифт, вес не меняется.
 */
object VarFont {
    private const val PATH = "fonts/ab_variable.ttf"
    private var checked = false
    var ready = false
        private set
    private var assets: AssetManager? = null
    private val cache = HashMap<Int, FontFamily>()

    fun ensure(ctx: Context) {
        if (checked) return
        checked = true
        if (Build.VERSION.SDK_INT < 26) return
        try {
            val am = ctx.applicationContext.assets
            if (am.list("fonts")?.contains("ab_variable.ttf") == true) {
                assets = am
                ready = true
            }
        } catch (_: Throwable) {
        }
    }

    /** Семейство для веса w (округляем до 20, чтобы кэш был небольшим, а переход оставался плавным). */
    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    fun family(w: Int): FontFamily? {
        val am = assets ?: return null
        val q = ((w + 10) / 20 * 20).coerceIn(100, 900)
        return cache.getOrPut(q) {
            FontFamily(
                Font(
                    assetManager = am,
                    path = PATH,
                    weight = FontWeight(q),
                    variationSettings = FontVariation.Settings(FontVariation.weight(q))
                )
            )
        }
    }
}

// ============================================================ л. резиновый скролл

private class RubberState {
    var offset by mutableFloatStateOf(0f)
    var job: Job? = null
    var down = false
}

/**
 * «Резиновый» край прокрутки для списков и экранов (LazyColumn, verticalScroll): у верхнего и нижнего края содержимое
 * тянется за пальцем с нарастающим сопротивлением (до ~160dp при максимуме), а при отпускании возвращается пружиной
 * с отскоком. Если щелчок-флик упёрся в край — содержимое «ударяется» и отскакивает. Ставится ПЕРЕД скроллом:
 * `Modifier.rubberBand().verticalScroll(...)`. Системное растяжение/свечение при этом отключается (см. AppRoot).
 */
@Composable
fun Modifier.rubberBand(): Modifier {
    val level = LocalFxLevel.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val levelState = rememberUpdatedState(level)
    val state = remember { RubberState() }
    val conn = remember(state) {
        fun settle(velocity: Float) {
            state.job?.cancel()
            if (state.offset == 0f && velocity == 0f) return
            state.job = scope.launch {
                val l = levelState.value
                animate(
                    initialValue = state.offset, targetValue = 0f, initialVelocity = velocity,
                    animationSpec = spring(dampingRatio = 0.8f - 0.3f * l, stiffness = 230f)
                ) { v, _ -> state.offset = v }
            }
        }
        object : NestedScrollConnection {
            private fun maxPx(): Float = with(density) { (60f + 100f * levelState.value).dp.toPx() }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val o = state.offset
                val dy = available.y
                if (o == 0f || dy == 0f) return Offset.Zero
                val towardZero = (o > 0f && dy < 0f) || (o < 0f && dy > 0f)
                if (!towardZero) return Offset.Zero
                state.job?.cancel()
                val target = o + dy
                val newO = if (o > 0f) maxOf(0f, target) else minOf(0f, target)
                state.offset = newO
                return Offset(0f, newO - o)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy == 0f) return Offset.Zero
                state.job?.cancel()
                val m = maxPx()
                val o = state.offset
                // чем сильнее оттянули, тем тяжелее тянуть; от «броска» (палец уже отпущен) тянется заметно слабее
                var resist = (1f - kotlin.math.abs(o) / m).coerceIn(0.03f, 1f) * 0.65f
                if (!state.down) resist *= 0.3f
                state.offset = (o + dy * resist).coerceIn(-m, m)
                return Offset(0f, dy)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (state.offset == 0f) return Velocity.Zero
                settle(0f)
                return available
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val v = (available.y * 0.12f).coerceIn(-1500f, 1500f)
                if (state.offset == 0f && v == 0f) return Velocity.Zero
                settle(v)
                return available
            }
        }
    }
    if (level <= 0f) return this
    return this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    state.down = ev.changes.any { it.pressed }
                }
            }
        }
        .nestedScroll(conn)
        .graphicsLayer { translationY = state.offset }
}
