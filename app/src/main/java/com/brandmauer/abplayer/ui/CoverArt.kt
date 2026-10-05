package com.brandmauer.abplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import kotlin.math.cos
import kotlin.math.sin

// толщины в сетке 512×512 (совпадают с design/abplayer_cover*.svg)
private const val ARC_W = 20f            // синие дуги (было 26)
private const val ARROW_OUTLINE_W = 6f   // скругление/обводка наконечников стрелок (было 10)
private const val TRACK_W = 16f          // серая круговая линия под стрелками (было 10)
private const val DOT_R = 16f            // точки A и B (было 17)

// доля поля обложки, занимаемая рисунком (раньше везде 0.74)
// +20% к исходным 0.70 / 0.78, затем ещё +10% (0.84 / 0.936 → 0.924 / 1.0296)
private const val COVER_SHARE_DARK = 0.924f
private const val COVER_SHARE_LIGHT = 1.0296f

/**
 * Рисунок стандартной обложки (когда у трека нет своей): круговая «петля повтора» из двух дуг со стрелками,
 * две точки A–B и кнопка play на диске. Геометрия — в сетке 512×512 и совпадает с файлом design/abplayer_cover.svg:
 * дуги и точки лежат на одной окружности (R = 156), всё симметрично относительно поворота на 180°, стрелки — ровные
 * треугольники со скруглёнными углами. Цвета берутся из темы (акцент), поэтому рисунок подходит и светлой, и тёмной теме.
 *
 * [fieldSize] — сторона поля обложки. Доля поля, которую занимает рисунок, зависит от темы: светлый рисунок на тёмном
 * фоне «светится» и кажется крупнее, тёмный на светлом — мельче, поэтому на тёмной теме рисунок чуть меньше, а на светлой
 * чуть больше — на глаз получается одинаково.
 */
@Composable
fun DefaultCoverArt(fieldSize: Dp, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val share = if (c.dark) COVER_SHARE_DARK else COVER_SHARE_LIGHT
    // requiredSize: на светлой теме холст чуть больше поля (рисунок внутри него занимает ~77%), size() обрезал бы его до поля
    Canvas(modifier.requiredSize(fieldSize * share)) {
        val k = this.size.minDimension / 512f
        val cc = 256f * k
        val r = 156f * k
        val center = Offset(cc, cc)
        fun pt(deg: Float): Offset {
            val a = Math.toRadians(deg.toDouble())
            return Offset(cc + r * cos(a).toFloat(), cc + r * sin(a).toFloat())
        }

        val ring = if (c.dark) c.accent2 else c.accent
        val ringBrush = Brush.linearGradient(
            listOf(lerp(ring, Color.White, 0.18f), ring),
            start = Offset(96f * k, 96f * k), end = Offset(416f * k, 416f * k)
        )
        val playBrush = Brush.verticalGradient(
            listOf(Color.White, Color(0xFFE4E7FF)), startY = 212f * k, endY = 300f * k
        )
        val disc = if (c.dark) lerp(c.surface3, c.accent2, 0.14f) else c.accent

        // «дорожка» кольца под дугами (серая линия: толще синих дуг не бывает, но заметно шире прежних 10)
        drawCircle(
            (if (c.dark) Color.White else c.text).copy(alpha = if (c.dark) 0.10f else 0.11f),
            radius = r, center = center, style = Stroke(TRACK_W * k)
        )
        // диск и кнопка play (треугольник со скруглёнными углами; центр масс — в центре диска)
        drawCircle(disc, radius = 104f * k, center = center)
        val tri = Path().apply {
            moveTo(cc - 26f * k, cc - 44f * k)
            lineTo(cc - 26f * k, cc + 44f * k)
            lineTo(cc + 52f * k, cc)
            close()
        }
        drawPath(tri, playBrush)
        drawPath(tri, playBrush, style = Stroke(16f * k, join = StrokeJoin.Round))

        // две дуги (вторая — первая, повёрнутая на 180°) и стрелки на их концах
        val arcStroke = Stroke(ARC_W * k, cap = StrokeCap.Round)
        fun arc(start: Float) = drawArc(
            brush = ringBrush, startAngle = start, sweepAngle = 114f, useCenter = false,
            topLeft = Offset(cc - r, cc - r), size = Size(2f * r, 2f * r), style = arcStroke
        )
        arc(-100f); arc(80f)
        arrowHead(14f, ::pt, ringBrush, k)
        arrowHead(194f, ::pt, ringBrush, k)

        // точки A и B — посередине между стрелкой и началом следующей дуги
        drawCircle(ringBrush, radius = DOT_R * k, center = pt(54f))
        drawCircle(ringBrush, radius = DOT_R * k, center = pt(234f))
    }
}

/** Стрелка на конце дуги: основание поперёк дуги в точке angleDeg, остриё — дальше по окружности (по часовой). */
private fun DrawScope.arrowHead(angleDeg: Float, pt: (Float) -> Offset, brush: Brush, k: Float) {
    val a = Math.toRadians(angleDeg.toDouble())
    val base = pt(angleDeg)
    val nx = cos(a).toFloat()
    val ny = sin(a).toFloat()
    val half = 34f * k
    val path = Path().apply {
        moveTo(base.x + half * nx, base.y + half * ny)
        val tip = pt(angleDeg + 18.4f)
        lineTo(tip.x, tip.y)
        lineTo(base.x - half * nx, base.y - half * ny)
        close()
    }
    drawPath(path, brush)
    drawPath(path, brush, style = Stroke(ARROW_OUTLINE_W * k, join = StrokeJoin.Round))
}
