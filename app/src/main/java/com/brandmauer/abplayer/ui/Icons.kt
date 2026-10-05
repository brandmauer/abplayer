package com.brandmauer.abplayer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Иконки прототипа ABP.html (те же SVG-контуры, viewBox 24x24).
 * Файл сгенерирован из словаря ICONS прототипа; цвет задаётся через tint в Icon().
 */
object Ic {
    private fun build(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()

    private fun ImageVector.Builder.p(
        d: String,
        fill: Boolean,
        stroke: Boolean,
        width: Float,
        cap: StrokeCap,
        join: StrokeJoin
    ): ImageVector.Builder = addPath(
        pathData = PathParser().parsePathString(d).toNodes(),
        fill = if (fill) SolidColor(Color.Black) else null,
        stroke = if (stroke) SolidColor(Color.Black) else null,
        strokeLineWidth = width,
        strokeLineCap = cap,
        strokeLineJoin = join
    )

    val music: ImageVector by lazy {
        build("music") {
        p("M9 18V5l12-2v13", false, true, 1.6f, StrokeCap.Round, StrokeJoin.Round)
        p("M3,18a3,3 0 1,0 6,0a3,3 0 1,0 -6,0z", false, true, 1.6f, StrokeCap.Round, StrokeJoin.Round)
        p("M15,16a3,3 0 1,0 6,0a3,3 0 1,0 -6,0z", false, true, 1.6f, StrokeCap.Round, StrokeJoin.Round)
        }
    }

    val dots: ImageVector by lazy {
        build("dots") {
        p("M10.4,5a1.6,1.6 0 1,0 3.2,0a1.6,1.6 0 1,0 -3.2,0z", true, false, 1f, StrokeCap.Butt, StrokeJoin.Miter)
        p("M10.4,12a1.6,1.6 0 1,0 3.2,0a1.6,1.6 0 1,0 -3.2,0z", true, false, 1f, StrokeCap.Butt, StrokeJoin.Miter)
        p("M10.4,19a1.6,1.6 0 1,0 3.2,0a1.6,1.6 0 1,0 -3.2,0z", true, false, 1f, StrokeCap.Butt, StrokeJoin.Miter)
        }
    }

    val search: ImageVector by lazy {
        build("search") {
        p("M4,11a7,7 0 1,0 14,0a7,7 0 1,0 -14,0z", false, true, 1.8f, StrokeCap.Round, StrokeJoin.Miter)
        p("M21 21l-4.3-4.3", false, true, 1.8f, StrokeCap.Round, StrokeJoin.Miter)
        }
    }

    val plus: ImageVector by lazy {
        build("plus") {
        p("M12 5v14M5 12h14", false, true, 2f, StrokeCap.Round, StrokeJoin.Miter)
        }
    }

    val shuffle: ImageVector by lazy {
        build("shuffle") {
        p("M3 6h3.5c2 0 3 1 4.3 2.7M3 18h3.5c2 0 3-1 4.3-2.7M21 6h-4l-2 2.3M21 18h-4l-2-2.3M18 3l3 3-3 3M18 15l3 3-3 3", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
        }
    }

    val repeat: ImageVector by lazy {
        build("repeat") {
        p("M17 2l4 4-4 4", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
        p("M3 11V9a4 4 0 0 1 4-4h14", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
        p("M7 22l-4-4 4-4", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
        p("M21 13v2a4 4 0 0 1-4 4H3", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
        }
    }

    val heart: ImageVector by lazy {
        build("heart") {
        p("M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.6l-1-1a5.5 5.5 0 1 0-7.8 7.8l1 1L12 21l7.8-7.6 1-1a5.5 5.5 0 0 0 0-7.8z", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
        }
    }

    val heartFill: ImageVector by lazy {
        build("heartFill") {
        p("M12 21s-7.5-4.7-10.2-9.1C.4 9.2 1.6 5.4 5 4.3c2.2-.7 4.4.1 5.7 1.9L12 7.8l1.3-1.6c1.3-1.8 3.5-2.6 5.7-1.9 3.4 1.1 4.6 4.9 3.2 7.6C19.5 16.3 12 21 12 21z", true, false, 1f, StrokeCap.Butt, StrokeJoin.Miter)
        }
    }

    val bookmark: ImageVector by lazy {
        build("bookmark") {
        p("M6 3h12v18l-6-4-6 4z", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
        }
    }

    val bookmarkFill: ImageVector by lazy {
        build("bookmarkFill") {
        p("M6 3h12v18l-6-4-6 4z", true, false, 1f, StrokeCap.Butt, StrokeJoin.Miter)
        }
    }

    val list: ImageVector by lazy {
        build("list") {
        p("M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Miter)
        }
    }

    val prev: ImageVector by lazy {
        build("prev") {
        p("M6 5h2v14H6zM20 5v14L9 12z", true, false, 1f, StrokeCap.Butt, StrokeJoin.Miter)
        }
    }

    val next: ImageVector by lazy {
        build("next") {
        p("M16 5h2v14h-2zM4 5v14l11-7z", true, false, 1f, StrokeCap.Butt, StrokeJoin.Miter)
        }
    }

    val play: ImageVector by lazy {
        build("play") {
        p("M7 4l14 8-14 8z", true, false, 1f, StrokeCap.Butt, StrokeJoin.Miter)
        }
    }

    val pause: ImageVector by lazy {
        build("pause") {
        p("M7.2,4h2.1a1.2,1.2 0 0 1 1.2,1.2v13.6a1.2,1.2 0 0 1 -1.2,1.2h-2.1a1.2,1.2 0 0 1 -1.2,-1.2v-13.6a1.2,1.2 0 0 1 1.2,-1.2z", true, false, 1f, StrokeCap.Butt, StrokeJoin.Miter)
        p("M14.7,4h2.1a1.2,1.2 0 0 1 1.2,1.2v13.6a1.2,1.2 0 0 1 -1.2,1.2h-2.1a1.2,1.2 0 0 1 -1.2,-1.2v-13.6a1.2,1.2 0 0 1 1.2,-1.2z", true, false, 1f, StrokeCap.Butt, StrokeJoin.Miter)
        }
    }

    val sort: ImageVector by lazy {
        build("sort") {
        p("M4 6h11M4 12h7M4 18h4M17 4v16M17 4l3.5 3.5M17 20l-3.5-3.5", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Miter)
        }
    }

    val check2: ImageVector by lazy {
        build("check2") {
        p("M9 11l3 3L22 4", false, true, 2f, StrokeCap.Round, StrokeJoin.Round)
        p("M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11", false, true, 2f, StrokeCap.Round, StrokeJoin.Round)
        }
    }

    val check: ImageVector by lazy {
        build("check") {
        p("M4 12l5 5L20 6", false, true, 3f, StrokeCap.Round, StrokeJoin.Round)
        }
    }

    val back: ImageVector by lazy {
        build("back") {
        p("M15 18l-6-6 6-6", false, true, 2f, StrokeCap.Round, StrokeJoin.Round)
        }
    }

    val chevron: ImageVector by lazy {
        build("chevron") {
        p("M9 6l6 6-6 6", false, true, 2f, StrokeCap.Round, StrokeJoin.Round)
        }
    }

    val close: ImageVector by lazy {
        build("close") {
        p("M6 6l12 12M18 6L6 18", false, true, 2f, StrokeCap.Round, StrokeJoin.Miter)
        }
    }

    val trash: ImageVector by lazy {
        build("trash") {
        p("M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
        }
    }

    val folder: ImageVector by lazy {
        build("folder") {
        p("M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
        }
    }

    val clock: ImageVector by lazy {
        build("clock") {
        p("M3,12a9,9 0 1,0 18,0a9,9 0 1,0 -18,0z", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
        p("M12 7v5l3 3", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
        }
    }

    /** Таймер сна запущен: часы с плюсом (lucide clock-plus, геометрия сжата ×0.9 до круга r=9 как у Ic.clock, обводка 1.7). */
    val clockPlus: ImageVector by lazy {
        build("clockPlus") {
        p("M12 6.6V12l3.28 1.64", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
        p("M15.6 18.3H21", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
        p("M18.3 15.6V21", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
        p("M20.928 13.14a9 9 0 1 0-7.788 7.788", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
        }
    }

    /** Таймер сна: остановить (круг как у часов со знаком паузы внутри). */
    val timerOff: ImageVector by lazy {
        build("timerOff") {
        p("M3,12a9,9 0 1,0 18,0a9,9 0 1,0 -18,0z", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
        p("M10 9v6M14 9v6", false, true, 1.8f, StrokeCap.Round, StrokeJoin.Round)
        }
    }

    /**
     * Значок «AB»: высокие линейные буквы. Выводится в 22dp (закладка — в 20dp), поэтому обводка 1.55 вместо 1.7:
     * на экране толщина линии (≈1.42dp) та же, что у закладки.
     */
    val ab: ImageVector by lazy {
        build("ab") {
        p("M2,18.5L6,5.5L10,18.5M3.2,14.5H8.8", false, true, 1.55f, StrokeCap.Round, StrokeJoin.Round)
        p("M15.6,5.5V18.5M15.6,5.5H18A3.25,3.25 0 0 1 18,12H15.6M15.6,12H18.7A3.25,3.25 0 0 1 18.7,18.5H15.6", false, true, 1.55f, StrokeCap.Round, StrokeJoin.Round)
        }
    }

    val moon: ImageVector by lazy {
        build("moon") {
        p("M21 12.8A9 9 0 1 1 11.2 3 7 7 0 0 0 21 12.8z", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
        }
    }

    val sun: ImageVector by lazy {
        build("sun") {
        p("M7.5,12a4.5,4.5 0 1,0 9,0a4.5,4.5 0 1,0 -9,0z", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Miter)
        p("M12 2v2M12 20v2M4.2 4.2l1.4 1.4M18.4 18.4l1.4 1.4M2 12h2M20 12h2M4.2 19.8l1.4-1.4M18.4 5.6l1.4-1.4", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Miter)
        }
    }

    // значок рисуется в 26dp (соседние — в 20dp): обводка 1.31 даёт на экране ту же толщину линии (≈1.42dp), что у соседей
    val volume: ImageVector by lazy {
        build("volume") {
        p("M11 5L6 9H3v6h3l5 4z", false, true, 1.31f, StrokeCap.Round, StrokeJoin.Round)
        p("M15.5 8.5a5 5 0 0 1 0 7", false, true, 1.31f, StrokeCap.Round, StrokeJoin.Round)
        p("M18.5 5.5a9 9 0 0 1 0 13", false, true, 1.31f, StrokeCap.Round, StrokeJoin.Round)
        }
    }

    val info: ImageVector by lazy {
        build("info") {
        p("M3,12a9,9 0 1,0 18,0a9,9 0 1,0 -18,0z", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
        p("M12 11v5M12 8h.01", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
        }
    }

    val help: ImageVector by lazy {
        build("help") {
        p("M3,12a9,9 0 1,0 18,0a9,9 0 1,0 -18,0z", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
        p("M9.5 9a2.5 2.5 0 0 1 4.9.8c0 1.7-2.4 2-2.4 3.4", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
        p("M12 17h.01", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
        }
    }

    val bolt: ImageVector by lazy {
        build("bolt") {
        p("M13 2L4.5 13.5H11L10.5 22 19.5 10H13z", true, false, 1f, StrokeCap.Round, StrokeJoin.Round)
        }
    }

    val edit: ImageVector by lazy {
        build("edit") {
        p("M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
        }
    }

    val play2: ImageVector by lazy {
        build("play2") {
        p("M3,12a9,9 0 1,0 18,0a9,9 0 1,0 -18,0z", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
        p("M10 8l6 4-6 4z", true, false, 1.7f, StrokeCap.Round, StrokeJoin.Round)
        }
    }

    val apply: ImageVector by lazy {
        build("apply") {
        p("M3,12a9,9 0 1,0 18,0a9,9 0 1,0 -18,0z", false, true, 1.8f, StrokeCap.Round, StrokeJoin.Round)
        p("M8 12.5l2.7 2.7L16 9.5", false, true, 1.8f, StrokeCap.Round, StrokeJoin.Round)
        }
    }

    /** Дуга кнопок перемотки: окружность с разрывом и аккуратной стрелкой по касательной (вторая — зеркальная копия). */
    val rewindArc: ImageVector by lazy {
        build("rewindArc") {
            p("M6.84 19.37A9 9 0 1 0 3.14 10.44", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
            p("M6.4 8.12L3.14 10.44L1.24 7.18", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
        }
    }
    val forwardArc: ImageVector by lazy {
        build("forwardArc") {
            p("M17.16 19.37A9 9 0 1 1 20.86 10.44", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
            p("M17.6 8.12L20.86 10.44L22.76 7.18", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
        }
    }

    /** Повтор одного трека: значок повтора с чёрточкой внутри. */
    val repeatOne: ImageVector by lazy {
        build("repeatOne") {
            p("M17 2l4 4-4 4", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
            p("M3 11V9a4 4 0 0 1 4-4h14", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
            p("M7 22l-4-4 4-4", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
            p("M21 13v2a4 4 0 0 1-4 4H3", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
            p("M12 10.85v2.3", false, true, 1.7f, StrokeCap.Round, StrokeJoin.Round)
        }
    }
}
