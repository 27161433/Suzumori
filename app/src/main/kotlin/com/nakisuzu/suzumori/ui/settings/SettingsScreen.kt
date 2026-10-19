package com.nakisuzu.suzumori.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nakisuzu.suzumori.platform.NotificationPosition
import com.nakisuzu.suzumori.platform.NotificationStyle
import com.nakisuzu.suzumori.platform.ThemeMode
import com.nakisuzu.suzumori.ui.components.AnimatedText
import com.nakisuzu.suzumori.ui.components.BusyIcon
import com.nakisuzu.suzumori.ui.components.Chevron
import com.nakisuzu.suzumori.ui.components.Divider
import com.nakisuzu.suzumori.ui.components.Entrance
import com.nakisuzu.suzumori.ui.components.Footnote
import com.nakisuzu.suzumori.ui.components.Motion
import com.nakisuzu.suzumori.ui.components.PageTitle
import com.nakisuzu.suzumori.ui.components.Panel
import com.nakisuzu.suzumori.ui.components.SectionTitle
import com.nakisuzu.suzumori.ui.components.SuzumoriPage
import com.nakisuzu.suzumori.ui.components.SwitchRow
import com.nakisuzu.suzumori.ui.components.SzIcons
import com.nakisuzu.suzumori.ui.theme.SuzumoriTheme
import top.yukonga.miuix.kmp.basic.Text

/**
 * 設定頁。解除安裝前清理由主畫面的 ViewModel 執行，[cleaning] 為清理進行中、[canCleanup] 為目前可以開始清理。
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    cleaning: Boolean,
    canCleanup: Boolean,
    onCleanup: () -> Unit,
) {
    val colors = SuzumoriTheme.colors
    val config = state.config

    SuzumoriPage {
        item(key = "title") { PageTitle("設定") }
        item(key = "appearance") {
            Entrance(index = 1, play = true) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("外觀")
                    Panel(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(text = "App 主題", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
                        SegmentedSelector(
                            options = ThemeMode.entries,
                            selected = state.themeMode,
                            label = { it.label },
                            onSelect = viewModel::setThemeMode,
                        )
                        Divider()
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "常駐通知", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
                            AnimatedText(
                                text = when (state.notificationStyle) {
                                    NotificationStyle.SYSTEM -> "系統標準樣式，位置由系統決定，內容更新時可能跳到最上方。"
                                    NotificationStyle.POSITIONED -> "系統標準樣式，固定在通知欄最上方，或排在其他較新的通知下方。"
                                    NotificationStyle.FOLDABLE -> "長按通知可以收納到「更多通知」。"
                                },
                                color = colors.onSurfaceMuted,
                                fontSize = 12,
                                lineHeight = 20,
                            )
                        }
                        SegmentedSelector(
                            options = NotificationStyle.entries,
                            selected = state.notificationStyle,
                            label = { it.label },
                            onSelect = viewModel::setNotificationStyle,
                        )
                        AnimatedVisibility(
                            visible = state.notificationStyle == NotificationStyle.POSITIONED,
                            enter = expandVertically(tween(400, easing = Motion.Emphasized)) + fadeIn(tween(250, delayMillis = 100)),
                            exit = shrinkVertically(tween(350, easing = Motion.Emphasized)) + fadeOut(tween(150)),
                        ) {
                            SegmentedSelector(
                                options = NotificationPosition.entries,
                                selected = state.notificationPosition,
                                label = { it.label },
                                onSelect = viewModel::setNotificationPosition,
                            )
                        }
                    }
                }
            }
        }
        item(key = "guard") {
            Entrance(index = 2, play = true) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("守護")
                    Panel(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
                        // 自動守護的總開關在首頁。
                        SwitchRow(
                            title = "夜間守護",
                            summary = "把 GMS 加入遊戲白名單，避免夜間被凍結",
                            checked = config.nightProtection,
                            enabled = true,
                            locked = state.busy,
                            onCheckedChange = viewModel::setNightProtection,
                            showDivider = true,
                        )
                        SwitchRow(
                            title = "本 App 也加入遊戲白名單",
                            summary = "把自己加入遊戲白名單",
                            checked = config.selfInGameAllowlist,
                            enabled = config.nightProtection,
                            locked = state.busy,
                            onCheckedChange = viewModel::setSelfInGameAllowlist,
                            showDivider = true,
                        )
                        SwitchRow(
                            title = "常駐通知",
                            summary = "以前景服務常駐，即時監聽名單變化。關閉後在背景的反應可能會明顯變慢。",
                            checked = config.persistentNotification,
                            enabled = config.autoGuard,
                            locked = state.busy,
                            onCheckedChange = viewModel::setPersistentNotification,
                            showDivider = false,
                        )
                    }
                }
            }
        }
        item(key = "cleanup") {
            Entrance(index = 3, play = true) {
                CleanupSection(cleaning = cleaning, enabled = canCleanup, onCleanup = onCleanup)
            }
        }
    }
}

@Composable
private fun CleanupSection(cleaning: Boolean, enabled: Boolean, onCleanup: () -> Unit) {
    val colors = SuzumoriTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("解除安裝")
        Panel(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 18.dp),
            enabled = enabled,
            onClick = onCleanup,
        ) {
            Row(
                modifier = Modifier.heightIn(min = 76.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.warnContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    BusyIcon(icon = SzIcons.Trash, busy = cleaning, tint = colors.warn, size = 20.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    AnimatedText(
                        text = if (cleaning) "清理中…" else "解除安裝前清理",
                        color = colors.warn,
                        fontSize = 15,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "只移除本 App 加入遊戲白名單的項目，並停止自動守護",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = colors.onSurfaceMuted,
                    )
                }
                Chevron()
            }
        }
        Footnote("解除安裝前請先清理。解除安裝後 App 就沒有機會再執行清理，留在遊戲白名單中的項目會一直生效。")
    }
}

/** 分段選擇器，選中的底色滑到點選的位置。 */
@Composable
private fun <T> SegmentedSelector(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val colors = SuzumoriTheme.colors
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceVariant)
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(
            targetValue = segment * options.indexOf(selected),
            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "indicator",
        )
        Box(
            Modifier
                .offset { IntOffset(offset.roundToPx(), 0) }
                .width(segment)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.primaryContainer),
        )
        Row(
            Modifier
                .fillMaxSize()
                .selectableGroup(),
        ) {
            for (mode in options) {
                val isSelected = mode == selected
                val textColor by animateColorAsState(
                    if (isSelected) colors.onPrimaryContainer else colors.onSurfaceMuted,
                    tween(250),
                    label = "segmentText",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(mode) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(mode),
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = textColor,
                    )
                }
            }
        }
    }
}
