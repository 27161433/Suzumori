package com.nakisuzu.suzumori.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.nakisuzu.suzumori.ui.components.Motion
import com.nakisuzu.suzumori.ui.theme.SuzumoriTheme
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.cos
import kotlin.math.sin

/** 夜間防線的時段：23:00 到 07:59。 */
fun isNightWindow(time: LocalTime): Boolean = time.hour >= 23 || time.hour < 8

/** 每到整分鐘更新一次的現在時間。 */
@Composable
fun rememberMinuteClock(): State<LocalTime> = produceState(LocalTime.now().truncatedTo(ChronoUnit.MINUTES)) {
    while (true) {
        delay(60_000 - System.currentTimeMillis() % 60_000)
        value = LocalTime.now().truncatedTo(ChronoUnit.MINUTES)
    }
}

/**
 * 24 小時守護圓環：白天防線（08:00–22:59）與夜間防線（23:00–07:59）兩段弧，加上現在時間的標記。
 *
 * 尺寸以 300 為設計基準等比例縮放。[playIntro] 時依序描出兩段弧再彈出標記；
 * [scanning] 時有一道光沿著圓環繞行。
 */
@Composable
fun GuardDial(
    now: LocalTime,
    nightEnabled: Boolean,
    scanning: Boolean,
    playIntro: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = SuzumoriTheme.colors
    val day = remember { Animatable(if (playIntro) 0f else 1f) }
    val night = remember { Animatable(if (playIntro) 0f else 1f) }
    val marker = remember { Animatable(if (playIntro) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (marker.value >= 1f) return@LaunchedEffect
        delay(200)
        day.animateTo(1f, tween(1_100, easing = Motion.Draw))
        night.animateTo(1f, tween(800, easing = Motion.Draw))
        marker.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
    }

    val rotation = remember { Animatable(0f) }
    LaunchedEffect(scanning) {
        while (scanning) rotation.animateTo(rotation.value + 360f, tween(1_400, easing = LinearEasing))
    }
    val scanAlpha by animateFloatAsState(if (scanning) 1f else 0f, tween(400), label = "scan")
    val nightColor by animateColorAsState(
        if (nightEnabled) colors.night else colors.onSurfaceMuted.copy(alpha = 0.35f),
        tween(450),
        label = "nightArc",
    )

    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = colors.onSurfaceMuted)
    val labels = remember(measurer, labelStyle) { listOf("00", "06", "12", "18").map { measurer.measure(it, labelStyle) } }

    Canvas(modifier.fillMaxSize()) {
        val unit = size.minDimension / 300f
        val c = center
        val ringRadius = 124f * unit
        val arcTopLeft = Offset(c.x - ringRadius, c.y - ringRadius)
        val arcSize = Size(ringRadius * 2, ringRadius * 2)
        val arcStroke = Stroke(width = 12f * unit, cap = StrokeCap.Round)

        drawCircle(colors.ringTrack, radius = ringRadius, center = c, style = Stroke(width = 14f * unit))

        // 每小時一格刻度。
        for (hour in 0 until 24) {
            val angle = Math.toRadians(hour * 15.0 - 90)
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            drawLine(
                color = colors.onSurfaceMuted.copy(alpha = 0.5f),
                start = Offset(c.x + dx * 103.5f * unit, c.y + dy * 103.5f * unit),
                end = Offset(c.x + dx * 108.5f * unit, c.y + dy * 108.5f * unit),
                strokeWidth = 1.4f * unit,
                cap = StrokeCap.Round,
            )
        }

        // 角度從 3 點鐘方向順時針算，00:00 在正上方，每小時 15 度；兩段弧之間留一點空隙。
        if (day.value > 0f) {
            drawArc(colors.day, 33.75f, 217.5f * day.value, false, arcTopLeft, arcSize, style = arcStroke)
        }
        if (night.value > 0f) {
            drawArc(nightColor, 258.75f, 127.5f * night.value, false, arcTopLeft, arcSize, style = arcStroke)
        }
        if (scanAlpha > 0f) {
            drawArc(
                colors.onSurface.copy(alpha = 0.35f * scanAlpha),
                rotation.value - 90f,
                36f,
                false,
                arcTopLeft,
                arcSize,
                style = arcStroke,
            )
        }

        if (marker.value > 0f) {
            val angle = Math.toRadians((now.hour * 60 + now.minute) / 1440.0 * 360 - 90)
            val point = Offset(c.x + cos(angle).toFloat() * ringRadius, c.y + sin(angle).toFloat() * ringRadius)
            val radius = 10f * unit * marker.value
            drawCircle(colors.background, radius = radius, center = point)
            drawCircle(colors.onSurface, radius = radius, center = point, style = Stroke(width = 4f * unit * marker.value))
        }

        val labelRadius = 92f * unit
        val positions = listOf(
            Offset(c.x, c.y - labelRadius),
            Offset(c.x + labelRadius, c.y),
            Offset(c.x, c.y + labelRadius),
            Offset(c.x - labelRadius, c.y),
        )
        labels.forEachIndexed { index, layout ->
            val p = positions[index]
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
        }
    }
}
