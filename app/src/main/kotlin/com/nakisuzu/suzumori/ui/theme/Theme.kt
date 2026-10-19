package com.nakisuzu.suzumori.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * Suzumori 的頁面配色：底色、卡片、文字與狀態色。
 *
 * miuix 的元件（開關、對話框）仍使用 miuix 的 HyperOS 預設藍。
 */
@Immutable
data class SuzumoriColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val divider: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    /** 文字與圖示用的主色。 */
    val primary: Color,
    /** 實心按鈕的底色，上面放白字。 */
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    /** 白天防線（MILLET）。 */
    val day: Color,
    /** 夜間防線（遊戲白名單）。 */
    val night: Color,
    val good: Color,
    val goodContainer: Color,
    val warn: Color,
    val warnContainer: Color,
    val onWarn: Color,
    /** 圓環背後的光暈。 */
    val glow: Color,
    val warnGlow: Color,
    val ringTrack: Color,
)

private val LightColors = SuzumoriColors(
    background = Color(0xFFEEF1F6),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F4F9),
    divider = Color(0x140C1628),
    onSurface = Color(0xFF0D1320),
    onSurfaceMuted = Color(0xFF596375),
    primary = Color(0xFF1F63E0),
    primaryContainer = Color(0xFF2A6CE8),
    onPrimaryContainer = Color(0xFFFFFFFF),
    day = Color(0xFF0A76B8),
    night = Color(0xFF5A44DE),
    good = Color(0xFF1F63E0),
    goodContainer = Color(0x1A1F63E0),
    warn = Color(0xFFA84A06),
    warnContainer = Color(0x1FD66E14),
    onWarn = Color(0xFFFFFFFF),
    glow = Color(0x293482FF),
    warnGlow = Color(0x24D66E14),
    ringTrack = Color(0x120C1628),
)

private val DarkColors = SuzumoriColors(
    background = Color(0xFF090D14),
    surface = Color(0xFF111824),
    surfaceVariant = Color(0xFF1A2333),
    divider = Color(0x12FFFFFF),
    onSurface = Color(0xFFEAF0F8),
    onSurfaceMuted = Color(0xFF8E9AAD),
    primary = Color(0xFF6EA4FF),
    primaryContainer = Color(0xFF2A6CE8),
    onPrimaryContainer = Color(0xFFFFFFFF),
    day = Color(0xFF6CC5FF),
    night = Color(0xFF9A8CFF),
    good = Color(0xFF6EA4FF),
    goodContainer = Color(0x246EA4FF),
    warn = Color(0xFFFFA24C),
    warnContainer = Color(0x21FFA24C),
    onWarn = Color(0xFF1F1203),
    glow = Color(0x383482FF),
    warnGlow = Color(0x2EFFA24C),
    ringTrack = Color(0x0FFFFFFF),
)

private val LocalSuzumoriColors = staticCompositionLocalOf { LightColors }

/**
 * HyperOS 預設藍為主色。深淺色依目前的設定（跟隨系統或 App 自訂，由系統套用到 configuration）。
 *
 * 切換深淺色時頁面不重建，配色在這裡漸變過去。
 */
@Composable
fun SuzumoriTheme(content: @Composable () -> Unit) {
    val controller = remember { ThemeController(ColorSchemeMode.System) }
    val colors = animateColors(if (isSystemInDarkTheme()) DarkColors else LightColors)
    MiuixTheme(controller = controller) {
        CompositionLocalProvider(LocalSuzumoriColors provides colors, content = content)
    }
}

@Composable
private fun animateColors(target: SuzumoriColors): SuzumoriColors {
    @Composable
    fun Color.animated(label: String): Color = animateColorAsState(this, tween(450), label = label).value
    return SuzumoriColors(
        background = target.background.animated("background"),
        surface = target.surface.animated("surface"),
        surfaceVariant = target.surfaceVariant.animated("surfaceVariant"),
        divider = target.divider.animated("divider"),
        onSurface = target.onSurface.animated("onSurface"),
        onSurfaceMuted = target.onSurfaceMuted.animated("onSurfaceMuted"),
        primary = target.primary.animated("primary"),
        primaryContainer = target.primaryContainer.animated("primaryContainer"),
        onPrimaryContainer = target.onPrimaryContainer.animated("onPrimaryContainer"),
        day = target.day.animated("day"),
        night = target.night.animated("night"),
        good = target.good.animated("good"),
        goodContainer = target.goodContainer.animated("goodContainer"),
        warn = target.warn.animated("warn"),
        warnContainer = target.warnContainer.animated("warnContainer"),
        onWarn = target.onWarn.animated("onWarn"),
        glow = target.glow.animated("glow"),
        warnGlow = target.warnGlow.animated("warnGlow"),
        ringTrack = target.ringTrack.animated("ringTrack"),
    )
}

object SuzumoriTheme {
    val colors: SuzumoriColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSuzumoriColors.current
}
