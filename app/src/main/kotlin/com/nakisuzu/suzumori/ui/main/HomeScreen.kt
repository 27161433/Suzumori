package com.nakisuzu.suzumori.ui.main

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.nakisuzu.suzumori.R
import com.nakisuzu.suzumori.platform.SystemPages
import com.nakisuzu.suzumori.ui.components.AnimatedText
import com.nakisuzu.suzumori.ui.components.BusyIcon
import com.nakisuzu.suzumori.ui.components.Entrance
import com.nakisuzu.suzumori.ui.components.Footnote
import com.nakisuzu.suzumori.ui.components.IconBadge
import com.nakisuzu.suzumori.ui.components.Motion
import com.nakisuzu.suzumori.ui.components.Panel
import com.nakisuzu.suzumori.ui.components.SquareIconButton
import com.nakisuzu.suzumori.ui.components.StatusChip
import com.nakisuzu.suzumori.ui.components.SuzumoriPage
import com.nakisuzu.suzumori.ui.components.SzIcons
import com.nakisuzu.suzumori.ui.theme.SuzumoriTheme
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import java.time.LocalTime

/**
 * 首頁：守護狀態、目前生效的防線、最近動態與操作。
 *
 * [playIntro] 為 true 時播放進場動畫（只在開啟 App 時播一次），播完呼叫 [onIntroShown]。
 * [openPage] 開啟系統設定頁，失敗時由呼叫端顯示訊息；[onOpenLog] 切到事件紀錄分頁。
 */
@Composable
fun HomeScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    playIntro: Boolean,
    onIntroShown: () -> Unit,
    openPage: ((Context) -> String?) -> Unit,
    onOpenLog: () -> Unit,
) {
    val now by rememberMinuteClock()
    LaunchedEffect(Unit) {
        if (playIntro) {
            delay(1_500)
            onIntroShown()
        }
    }

    SuzumoriPage {
        item(key = "header") {
            Entrance(0, playIntro) { Header(onFcmDiagnostics = { openPage(SystemPages::openFcmDiagnostics) }) }
        }
        item(key = "hero") {
            HeroSection(
                state = state,
                now = now,
                playIntro = playIntro,
                onOpenWriteSettings = { openPage(SystemPages::openWriteSettings) },
                onOpenPowerPolicy = { openPage(SystemPages::openPowerPolicy) },
                onOpenAppDetails = { openPage(SystemPages::openAppDetails) },
                onAutoGuardChange = viewModel::setAutoGuard,
            )
        }
        item(key = "status") { Entrance(2, playIntro) { StatusTiles(state, now, onOpenLog) } }
        item(key = "actions") {
            Entrance(3, playIntro) {
                ActionSection(state = state, onCheck = viewModel::checkNow, onUnfreeze = viewModel::unfreezeNow)
            }
        }
    }
}

@Composable
private fun Header(onFcmDiagnostics: () -> Unit) {
    val colors = SuzumoriTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconBadge(size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = "Suzumori", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface, maxLines = 1)
            Text(text = "鈴守・守護 FCM 推播", fontSize = 12.sp, color = colors.onSurfaceMuted, maxLines = 1)
        }
        SquareIconButton(icon = SzIcons.Pulse, contentDescription = "FCM 診斷頁面", onClick = onFcmDiagnostics)
    }
}

/**
 * 和桌面圖示一樣的組合：淺藍背景加上插畫前景。
 * 自適應圖示的圖層是 108dp、只顯示中間 72dp，所以前景放大 1.5 倍再裁切，看到的範圍和桌面一致。
 */
@Composable
private fun AppIconBadge(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(colorResource(R.color.ic_launcher_background)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * 1.5f),
        )
    }
}

// region 守護狀態

private enum class HeroKind { LOADING, GUARDING, PAUSED, ATTENTION }

private data class Hero(val kind: HeroKind, val title: String, val line1: String, val line2: String, val line2Color: Color)

/** 會讓守護失效的問題，依嚴重程度排列。 */
private fun problems(state: MainUiState): List<String> = buildList {
    if (!state.canWrite) add("寫入權限未開啟")
    if (!state.selfNoRestrict) add("省電策略未設為「無限制」")
    if (state.selfAutoStart == false) add("自啟動未開啟")
    if (!state.milletHasGms) add("GMS 不在 MILLET 名單")
    if (state.config.nightProtection && !state.gameHasGms) add("GMS 不在遊戲白名單")
}

private val DAY_START: LocalTime = LocalTime.of(8, 0)
private val NIGHT_START: LocalTime = LocalTime.of(23, 0)

@Composable
private fun rememberHero(state: MainUiState, now: LocalTime): Hero {
    val colors = SuzumoriTheme.colors
    val problems = problems(state)
    val nightOn = state.config.nightProtection
    val busyLine = when (state.busyAction) {
        BusyAction.CHECK -> "正在檢查名單…"
        BusyAction.UNFREEZE -> "正在送出解凍與重連…"
        BusyAction.CLEANUP -> "正在清理…"
        null -> null
    }
    return when {
        !state.loaded -> Hero(HeroKind.LOADING, "讀取中", "", "", colors.onSurfaceMuted)
        problems.isNotEmpty() -> Hero(
            HeroKind.ATTENTION,
            "需要處理",
            busyLine ?: "${problems.size} 個問題讓守護失效",
            problems.first(),
            colors.warn,
        )
        !state.config.autoGuard -> Hero(
            HeroKind.PAUSED,
            "已暫停",
            busyLine ?: "自動守護已關閉",
            "名單被改寫時不會自動補回",
            colors.onSurfaceMuted,
        )
        isNightWindow(now) -> Hero(
            HeroKind.GUARDING,
            "守護中",
            busyLine ?: if (nightOn) "夜間防線生效中" else "夜間守護已關閉",
            "距白天防線 ${TimeText.until(now, DAY_START)}",
            colors.day,
        )
        else -> Hero(
            HeroKind.GUARDING,
            "守護中",
            busyLine ?: "白天防線生效中",
            if (nightOn) "距夜間防線 ${TimeText.until(now, NIGHT_START)}" else "夜間守護已關閉",
            if (nightOn) colors.night else colors.onSurfaceMuted,
        )
    }
}

private val revealIn = expandVertically(tween(450, easing = Motion.Emphasized), expandFrom = Alignment.Top) +
    fadeIn(tween(300, delayMillis = 120))
private val revealOut = shrinkVertically(tween(400, easing = Motion.Emphasized), shrinkTowards = Alignment.Top) +
    fadeOut(tween(200))

@Composable
private fun HeroSection(
    state: MainUiState,
    now: LocalTime,
    playIntro: Boolean,
    onOpenWriteSettings: () -> Unit,
    onOpenPowerPolicy: () -> Unit,
    onOpenAppDetails: () -> Unit,
    onAutoGuardChange: (Boolean) -> Unit,
) {
    val colors = SuzumoriTheme.colors
    val hero = rememberHero(state, now)
    val glow by animateColorAsState(
        when (hero.kind) {
            HeroKind.GUARDING -> colors.glow
            HeroKind.ATTENTION -> colors.warnGlow
            else -> Color.Transparent
        },
        tween(600),
        label = "glow",
    )
    val scanning = state.busyAction == BusyAction.CHECK || state.busyAction == BusyAction.UNFREEZE

    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val radius = size.width * 0.75f
                    drawCircle(Brush.radialGradient(listOf(glow, Color.Transparent), center, radius), radius, center)
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(min(maxWidth * 0.8f, 300.dp)), contentAlignment = Alignment.Center) {
                GuardDial(now = now, nightEnabled = state.config.nightProtection, scanning = scanning, playIntro = playIntro)
                Entrance(index = 7, play = playIntro) {
                    HeroCenter(
                        hero = hero,
                        autoGuard = state.config.autoGuard,
                        autoGuardEnabled = state.loaded,
                        onAutoGuardChange = onAutoGuardChange,
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Entrance(index = 12, play = playIntro) { Legend() }
        AnimatedVisibility(visible = state.loaded && !state.canWrite, enter = revealIn, exit = revealOut) {
            AttentionCard(
                icon = SzIcons.Key,
                title = "寫入權限未開啟",
                text = "沒有「修改系統設定」權限就無法補回名單。開啟後回到 App 會自動重新檢查。",
                button = "前往開啟",
                onClick = onOpenWriteSettings,
            )
        }
        AnimatedVisibility(visible = state.loaded && !state.selfNoRestrict, enter = revealIn, exit = revealOut) {
            AttentionCard(
                icon = SzIcons.Battery,
                title = "省電策略未設為「無限制」",
                text = "把「省電策略」設為「無限制」，否則 Suzumori 在背景會被凍結。",
                button = "前往設定",
                onClick = onOpenPowerPolicy,
            )
        }
        AnimatedVisibility(visible = state.loaded && state.selfAutoStart == false, enter = revealIn, exit = revealOut) {
            AttentionCard(
                icon = SzIcons.Power,
                title = "自啟動未開啟",
                text = "在應用管理開啟「自啟動」。沒有自啟動時，小米不會在開機時通知本 App，被結束後也可能無法自動重新啟動。",
                button = "前往開啟",
                onClick = onOpenAppDetails,
            )
        }
    }
}

/**
 * 圓環中央：狀態圖示與自動守護開關，下方是狀態文字。
 *
 * 開關不放在淡換的區塊裡，切換時才不會跟著淡出淡入、打斷開關本身的滑動動畫。
 */
@Composable
private fun HeroCenter(hero: Hero, autoGuard: Boolean, autoGuardEnabled: Boolean, onAutoGuardChange: (Boolean) -> Unit) {
    val colors = SuzumoriTheme.colors
    val enter = fadeIn(tween(400, delayMillis = 100)) + scaleIn(tween(550, easing = Motion.Emphasized), initialScale = 0.92f)
    val exit = fadeOut(tween(200)) + scaleOut(tween(250), targetScale = 0.92f)
    val (tint, background) = when (hero.kind) {
        HeroKind.GUARDING -> colors.primary to colors.goodContainer
        HeroKind.ATTENTION -> colors.warn to colors.warnContainer
        else -> colors.onSurfaceMuted to colors.surfaceVariant
    }
    val animatedTint by animateColorAsState(tint, tween(400), label = "heroTint")
    val animatedBackground by animateColorAsState(background, tween(400), label = "heroBackground")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // 狀態圖示的底色延伸成一顆膠囊，把開關一起包進去。
        Row(
            modifier = Modifier
                .height(52.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(animatedBackground)
                .padding(start = 12.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedContent(
                targetState = hero.kind,
                transitionSpec = { enter togetherWith exit },
                contentAlignment = Alignment.Center,
                label = "heroIcon",
            ) { kind ->
                val icon = when (kind) {
                    HeroKind.GUARDING -> SzIcons.ShieldCheck
                    HeroKind.ATTENTION -> SzIcons.Alert
                    else -> SzIcons.Shield
                }
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = animatedTint)
            }
            Switch(
                checked = autoGuard,
                onCheckedChange = onAutoGuardChange,
                enabled = autoGuardEnabled,
                modifier = Modifier.semantics { contentDescription = "自動守護" },
            )
        }
        Spacer(Modifier.height(12.dp))
        AnimatedContent(
            targetState = hero,
            contentKey = { it.kind },
            transitionSpec = { enter togetherWith exit },
            contentAlignment = Alignment.Center,
            label = "heroText",
        ) { current ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = current.title,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                AnimatedText(text = current.line1, color = colors.onSurfaceMuted, fontSize = 13)
                Spacer(Modifier.height(4.dp))
                AnimatedText(text = current.line2, color = current.line2Color, fontSize = 12, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun Legend() {
    val colors = SuzumoriTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
    ) {
        LegendItem(colors.day, "白天 08:00–22:59")
        LegendItem(colors.night, "夜間 23:00–07:59")
    }
}

@Composable
private fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(text = text, fontSize = 12.sp, color = SuzumoriTheme.colors.onSurfaceMuted)
    }
}

@Composable
private fun AttentionCard(icon: ImageVector, title: String, text: String, button: String, onClick: () -> Unit) {
    val colors = SuzumoriTheme.colors
    Panel(
        modifier = Modifier
            .padding(top = 24.dp)
            .fillMaxWidth(),
        color = colors.warnContainer,
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBadge(icon = icon, tint = colors.warn, background = colors.surface)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.warn)
                Text(text = text, fontSize = 13.sp, lineHeight = 21.sp, color = colors.onSurface)
            }
        }
        Panel(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            color = colors.warn,
            shape = RoundedCornerShape(16.dp),
            verticalArrangement = Arrangement.Center,
            onClick = onClick,
        ) {
            Text(
                text = button,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onWarn,
            )
        }
    }
}

// endregion

// region 防線與最近動態

private data class Chip(val text: String, val color: Color, val container: Color)

/** 目前生效的防線，白天顯示 MILLET、夜間顯示遊戲白名單。 */
private data class Line(
    val night: Boolean,
    val icon: ImageVector,
    val accent: Color,
    val label: String,
    val title: String,
    val badge: String,
    val chip: Chip,
)

/** 左邊是目前生效的防線，右邊是濃縮的最近動態，兩張一樣高。 */
@Composable
private fun StatusTiles(state: MainUiState, now: LocalTime, onOpenLog: () -> Unit) {
    val colors = SuzumoriTheme.colors
    val night = isNightWindow(now)
    val nightOn = state.config.nightProtection
    val loading = Chip("讀取中", colors.onSurfaceMuted, colors.surfaceVariant)
    val inList = Chip("GMS 已在名單", colors.good, colors.goodContainer)
    val missing = Chip("GMS 不在名單", colors.warn, colors.warnContainer)
    val line = if (night) {
        Line(
            night = true,
            icon = SzIcons.Moon,
            accent = colors.night,
            label = "夜間防線・至 07:59",
            title = "遊戲白名單",
            badge = if (nightOn) "生效中" else "已關閉",
            chip = when {
                !state.loaded -> loading
                !nightOn -> Chip("夜間守護已關閉", colors.onSurfaceMuted, colors.surfaceVariant)
                state.gameHasGms -> inList
                else -> missing
            },
        )
    } else {
        Line(
            night = false,
            icon = SzIcons.Sun,
            accent = colors.day,
            label = "白天防線・至 22:59",
            title = "MILLET 名單",
            badge = "生效中",
            chip = when {
                !state.loaded -> loading
                state.milletHasGms -> inList
                else -> missing
            },
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Panel(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // 23:00 與 08:00 切換時，整張淡換成另一道防線；同一道防線的狀態變化只換標籤。
            AnimatedContent(
                targetState = line,
                contentKey = { it.night },
                transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(300)) },
                label = "line",
            ) { current ->
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(icon = current.icon, tint = current.accent, iconSize = 22.dp)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = current.badge,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (current.badge == "生效中") current.accent else colors.onSurfaceMuted,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = current.label, fontSize = 12.sp, color = colors.onSurfaceMuted)
                        Text(text = current.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                    }
                    StatusChip(text = current.chip.text, color = current.chip.color, container = current.chip.container)
                }
            }
        }
        RecentTile(
            state = state,
            onClick = onOpenLog,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

/** 最近一次修補、解凍與本 App 運作狀況的濃縮版，點擊切到事件紀錄。 */
@Composable
private fun RecentTile(state: MainUiState, onClick: () -> Unit, modifier: Modifier) {
    val colors = SuzumoriTheme.colors
    val issue = state.lastSelfIssue
    Panel(
        modifier = modifier,
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        onClick = onClick,
    ) {
        RecentLine(label = "最近修補", millis = state.lastPatch?.atMillis)
        RecentLine(label = "最近解凍", millis = state.lastUnfreeze?.atMillis)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = "運作狀況", fontSize = 12.sp, color = colors.onSurfaceMuted)
            if (issue == null) {
                Text(text = "正常", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.good)
            } else {
                Text(
                    text = "${TimeText.clock(issue.atMillis)} 曾被結束",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.warn,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RecentLine(label: String, millis: Long?) {
    val colors = SuzumoriTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, fontSize = 12.sp, color = colors.onSurfaceMuted)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = millis?.let(TimeText::clock) ?: "尚無",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (millis == null) colors.onSurfaceMuted else colors.onSurface,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
            if (millis != null) {
                Text(text = TimeText.shortDay(millis), fontSize = 11.sp, color = colors.onSurfaceMuted)
            }
        }
    }
}

// endregion

// region 操作

@Composable
private fun ActionSection(state: MainUiState, onCheck: () -> Unit, onUnfreeze: () -> Unit) {
    val running = state.busyAction
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ActionTile(
                modifier = Modifier.weight(1f),
                icon = SzIcons.Refresh,
                title = "立即檢查並修補",
                busyTitle = "檢查中…",
                summary = "名單正常時只讀取，不寫入也不送廣播",
                filled = true,
                busy = running == BusyAction.CHECK,
                dimmed = running != null && running != BusyAction.CHECK,
                enabled = !state.busy,
                onClick = onCheck,
            )
            ActionTile(
                modifier = Modifier.weight(1f),
                icon = SzIcons.Signal,
                title = "立即解凍並重連",
                busyTitle = "送出中…",
                summary = "先確保名單正常，再送出解凍與重連",
                filled = false,
                busy = running == BusyAction.UNFREEZE,
                dimmed = running != null && running != BusyAction.UNFREEZE,
                enabled = !state.busy,
                onClick = onUnfreeze,
            )
        }
        Footnote("廣播送出後只能確認「已送出」，無法確認 GMS 有收到。是否真的連上，請點右上角開啟 FCM 診斷頁面確認。")
    }
}

@Composable
private fun ActionTile(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    busyTitle: String,
    summary: String,
    filled: Boolean,
    busy: Boolean,
    dimmed: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = SuzumoriTheme.colors
    val alpha by animateFloatAsState(if (dimmed) 0.5f else 1f, tween(300), label = "dim")
    val content = if (filled) colors.onPrimaryContainer else colors.onSurface
    Panel(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer { this.alpha = alpha },
        color = if (filled) colors.primaryContainer else colors.surface,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        enabled = enabled,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BusyIcon(icon = icon, busy = busy, tint = if (filled) colors.onPrimaryContainer else colors.primary, size = 20.dp)
            AnimatedText(text = if (busy) busyTitle else title, color = content, fontSize = 15, fontWeight = FontWeight.SemiBold)
        }
        Text(
            text = summary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = if (filled) colors.onPrimaryContainer else colors.onSurfaceMuted,
        )
    }
}

// endregion
