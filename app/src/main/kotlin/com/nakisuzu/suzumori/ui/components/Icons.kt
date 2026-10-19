package com.nakisuzu.suzumori.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** 介面用的線條圖示：24×24、圓頭圓角線條，顏色由 Icon 的 tint 決定。 */
object SzIcons {
    val Bell by lazy { stroke("M6 8a6 6 0 0 1 12 0c0 7 3 8 3 8H3s3-1 3-8", "M10.3 21a1.94 1.94 0 0 0 3.4 0") }
    val List by lazy { stroke("M9 6h12M9 12h12M9 18h12", "M4 6h.01M4 12h.01M4 18h.01") }
    val Shield by lazy { stroke(SHIELD) }
    val ShieldCheck by lazy { stroke(SHIELD, "M9 12l2 2 4-4") }
    val Alert by lazy {
        stroke("M10.3 3.9L1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z", "M12 9v4", "M12 17h.01")
    }
    val Sun by lazy {
        stroke(
            "M8 12a4 4 0 1 0 8 0a4 4 0 1 0-8 0",
            "M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4",
        )
    }
    val Moon by lazy { stroke("M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z") }
    val Refresh by lazy { stroke("M21 12a9 9 0 1 1-3-6.7", "M21 4v5h-5") }
    val Signal by lazy {
        stroke(
            "M4.9 19.1a10 10 0 0 1 0-14.2M19.1 4.9a10 10 0 0 1 0 14.2M7.8 16.2a6 6 0 0 1 0-8.4M16.2 7.8a6 6 0 0 1 0 8.4",
            "M10.5 12a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0",
        )
    }
    val Pulse by lazy { stroke("M3 12h4l3-8 4 16 3-8h4") }
    val External by lazy {
        stroke("M14 4h6v6", "M20 4l-9 9", "M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5")
    }
    val ChevronRight by lazy { stroke("M9 6l6 6-6 6") }
    val ChevronDown by lazy { stroke("M6 9l6 6 6-6") }
    val Back by lazy { stroke("M15 6l-6 6 6 6") }
    val Key by lazy { stroke("M4 15a4 4 0 1 0 8 0a4 4 0 1 0-8 0", "M10.8 12.2L20 3M17 6l3 3M14 9l2 2") }
    val Battery by lazy {
        stroke(
            "M4 7h13a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z",
            "M22 11v2",
            "M6 10v4M9.5 10v4M13 10v4",
        )
    }
    val Phone by lazy { stroke("M9 2h6a3 3 0 0 1 3 3v14a3 3 0 0 1-3 3H9a3 3 0 0 1-3-3V5a3 3 0 0 1 3-3z", "M11 18h2") }
    val Power by lazy { stroke("M12 2v10", "M5.6 6.6a8 8 0 1 0 12.8 0") }
    val Trash by lazy { stroke("M3 6h18", "M8 6V4h8v2", "M6 6l1 14h10l1-14") }
    val Share by lazy { stroke("M12 3v13", "M7 8l5-5 5 5", "M5 14v5a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-5") }
    val Home by lazy { stroke("M3 10.5L12 3l9 7.5", "M5 9.5V20a1 1 0 0 0 1 1h4v-6h4v6h4a1 1 0 0 0 1-1V9.5") }
    val Info by lazy { stroke("M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0", "M12 11v5", "M12 8h.01") }
    val Code by lazy { stroke("M8 7l-5 5 5 5", "M16 7l5 5-5 5", "M13.5 4l-3 16") }
    val Document by lazy { stroke("M6 3h8l4 4v14H6z", "M14 3v4h4", "M9 12h6M9 16h6") }
    val Sliders by lazy {
        stroke(
            "M4 7h10M18 7h2M4 12h2M10 12h10M4 17h8M16 17h4",
            "M14 7a2 2 0 1 0 4 0a2 2 0 1 0-4 0",
            "M6 12a2 2 0 1 0 4 0a2 2 0 1 0-4 0",
            "M12 17a2 2 0 1 0 4 0a2 2 0 1 0-4 0",
        )
    }
}

private const val SHIELD = "M12 3l7 3v6c0 4.5-3 7.7-7 9-4-1.3-7-4.5-7-9V6l7-3z"

private fun stroke(vararg paths: String): ImageVector =
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            for (path in paths) {
                addPath(
                    pathData = addPathNodes(path),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
        .build()
