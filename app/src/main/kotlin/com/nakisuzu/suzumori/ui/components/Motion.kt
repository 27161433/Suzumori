package com.nakisuzu.suzumori.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon

/** 介面動畫共用的曲線。 */
object Motion {
    /** 快進慢停，用在進場、展開與狀態切換。 */
    val Emphasized = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

    /** 慢進慢出，用在圓環描繪。 */
    val Draw = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
}

/**
 * 進場動畫：由下往上淡入，依 [index] 錯開時間。
 *
 * [play] 為 false 時直接顯示，例如從事件紀錄返回主畫面時不重播。
 */
@Composable
fun Entrance(index: Int, play: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val progress = remember { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(70L * index)
            progress.animateTo(1f, tween(600, easing = Motion.Emphasized))
        }
    }
    Box(
        modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 16.dp.toPx()
        },
    ) {
        content()
    }
}

/** 處理中的轉圈。 */
@Composable
fun Spinner(color: Color, modifier: Modifier = Modifier, strokeWidth: Dp = 2.2.dp) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing)),
        label = "angle",
    )
    Canvas(modifier) {
        val width = strokeWidth.toPx()
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = 260f,
            useCenter = false,
            topLeft = Offset(width / 2, width / 2),
            size = Size(size.width - width, size.height - width),
            style = Stroke(width, cap = StrokeCap.Round),
        )
    }
}

/** 平常顯示圖示，[busy] 時淡換成轉圈。 */
@Composable
fun BusyIcon(icon: ImageVector, busy: Boolean, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    AnimatedContent(
        targetState = busy,
        transitionSpec = {
            (fadeIn(tween(220)) + scaleIn(tween(300, easing = Motion.Emphasized), initialScale = 0.6f)) togetherWith
                (fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.6f))
        },
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size),
        label = "busyIcon",
    ) { isBusy ->
        if (isBusy) {
            Spinner(tint, Modifier.size(size * 0.8f))
        } else {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(size), tint = tint)
        }
    }
}
