package com.nakisuzu.suzumori.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * HyperOS 風格的單頁框架：可收合的大標題、毛玻璃頂欄、彈性回彈捲動與捲到底的觸感回饋。
 *
 * 頁面內容放在 [content] 的 LazyColumn 中，[overlay] 用來放對話框等浮層。
 */
@Composable
fun SuzumoriPage(
    title: String,
    subtitle: String = "",
    overlay: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberPageBackdrop()

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    title = title,
                    subtitle = subtitle,
                    color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 12.dp,
                ),
                content = content,
            )
        }
        overlay()
    }
}

/** 頁面內容的擷取層，給頂欄做模糊。裝置不支援 RuntimeShader 時回傳 null，退回不透明頂欄。 */
@Composable
private fun rememberPageBackdrop(): LayerBackdrop? {
    if (!isRuntimeShaderSupported()) return null
    val surface = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surface)
        drawContent()
    }
}

/** 頂欄背後的毛玻璃：模糊捲到下方的內容，再疊一層半透明底色保持標題清晰。 */
@Composable
private fun BlurredBar(backdrop: LayerBackdrop?, content: @Composable () -> Unit) {
    Box(
        modifier = if (backdrop != null) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 25f,
                colors = BlurDefaults.blurColors(
                    blendColors = listOf(
                        BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(alpha = 0.8f)),
                    ),
                ),
            )
        } else {
            Modifier
        },
    ) {
        content()
    }
}
