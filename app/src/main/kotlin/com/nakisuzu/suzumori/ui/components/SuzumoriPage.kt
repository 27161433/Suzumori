package com.nakisuzu.suzumori.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.nakisuzu.suzumori.ui.components.glass.LiquidGlassNavigationBarHeight
import com.nakisuzu.suzumori.ui.components.glass.liquidGlassNavigationBarBottomPadding
import com.nakisuzu.suzumori.ui.theme.SuzumoriTheme
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 單頁框架：彈性回彈捲動、捲到底的觸感回饋，捲到狀態列底下的內容會被毛玻璃蓋住。
 * 底部預留浮動導覽列的空間。
 *
 * 頁面內容放在 [content] 的 LazyColumn 中，[overlay] 用來放浮層。
 */
@Composable
fun SuzumoriPage(
    listState: LazyListState = rememberLazyListState(),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(28.dp),
    overlay: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val colors = SuzumoriTheme.colors
    val backdrop = rememberPageBackdrop(colors.background)
    val insets = WindowInsets.safeDrawing.asPaddingValues()

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .overScrollVertical(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = insets.calculateTopPadding() + 12.dp,
                    end = 20.dp,
                    // 底部浮動導覽列蓋住的範圍，再多留一點空間。
                    bottom = liquidGlassNavigationBarBottomPadding() + LiquidGlassNavigationBarHeight + 28.dp,
                ),
                verticalArrangement = verticalArrangement,
                content = content,
            )
        }
        StatusBarScrim(backdrop)
        overlay()
    }
}

/** 頁面內容的擷取層，給狀態列做模糊。裝置不支援 RuntimeShader 時回傳 null，退回半透明底色。 */
@Composable
private fun rememberPageBackdrop(color: Color): LayerBackdrop? {
    if (!isRuntimeShaderSupported()) return null
    return rememberLayerBackdrop {
        drawRect(color)
        drawContent()
    }
}

/**
 * 狀態列背後的毛玻璃，避免捲上去的內容和狀態列圖示疊在一起。
 *
 * 只佔狀態列的高度，由上往下漸淡；深色模式疊黑色壓暗，淺色模式疊白色。
 */
@Composable
private fun StatusBarScrim(backdrop: LayerBackdrop?) {
    val tint = if (isSystemInDarkTheme()) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.7f)
    val bar = Modifier
        .fillMaxWidth()
        .windowInsetsTopHeight(WindowInsets.statusBars)
        // 先畫出整片模糊，再用漸層遮罩讓下緣淡出。
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(ScrimFade, blendMode = BlendMode.DstIn)
        }
    Box(
        if (backdrop != null) {
            bar.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 25f,
                colors = BlurDefaults.blurColors(blendColors = listOf(BlendColorEntry(color = tint))),
            )
        } else {
            bar.background(tint)
        },
    )
}

private val ScrimFade = Brush.verticalGradient(
    0f to Color.Black,
    0.55f to Color.Black.copy(alpha = 0.8f),
    1f to Color.Transparent,
)
