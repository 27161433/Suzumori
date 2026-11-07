package com.nakisuzu.suzumori.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nakisuzu.suzumori.R
import com.nakisuzu.suzumori.core.config.SleepKeepAlive
import com.nakisuzu.suzumori.platform.AppLanguage
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
import com.nakisuzu.suzumori.ui.components.SegmentedSelector
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
        item(key = "title") { PageTitle(stringResource(R.string.tab_settings)) }
        item(key = "appearance") {
            Entrance(index = 1, play = true) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle(stringResource(R.string.settings_appearance))
                    Panel(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_language),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.onSurface,
                        )
                        SegmentedSelector(
                            options = AppLanguage.entries,
                            selected = state.language,
                            label = { it.label() },
                            onSelect = viewModel::setLanguage,
                        )
                        Divider()
                        Text(
                            text = stringResource(R.string.settings_theme),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.onSurface,
                        )
                        SegmentedSelector(
                            options = ThemeMode.entries,
                            selected = state.themeMode,
                            label = { it.label() },
                            onSelect = viewModel::setThemeMode,
                        )
                        Divider()
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.persistent_notification),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.onSurface,
                            )
                            AnimatedText(
                                text = stringResource(
                                    when (state.notificationStyle) {
                                        NotificationStyle.SYSTEM -> R.string.notification_style_system_desc
                                        NotificationStyle.POSITIONED -> R.string.notification_style_positioned_desc
                                        NotificationStyle.FOLDABLE -> R.string.notification_style_foldable_desc
                                    },
                                ),
                                color = colors.onSurfaceMuted,
                                fontSize = 12,
                                lineHeight = 20,
                            )
                        }
                        SegmentedSelector(
                            options = NotificationStyle.entries,
                            selected = state.notificationStyle,
                            label = { it.label() },
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
                                label = { it.label() },
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
                    SectionTitle(stringResource(R.string.settings_guard))
                    Panel(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
                        // 自動守護的總開關在首頁。
                        SwitchRow(
                            title = stringResource(R.string.night_guard),
                            summary = stringResource(R.string.night_guard_summary),
                            checked = config.nightProtection,
                            enabled = true,
                            locked = state.busy,
                            onCheckedChange = viewModel::setNightProtection,
                            showDivider = true,
                        )
                        SwitchRow(
                            title = stringResource(R.string.self_in_game_allowlist),
                            summary = stringResource(R.string.self_in_game_allowlist_summary),
                            checked = config.selfInGameAllowlist,
                            enabled = config.nightProtection,
                            locked = state.busy,
                            onCheckedChange = viewModel::setSelfInGameAllowlist,
                            showDivider = true,
                        )
                        SwitchRow(
                            title = stringResource(R.string.persistent_notification),
                            summary = stringResource(R.string.persistent_notification_summary),
                            checked = config.persistentNotification,
                            enabled = config.autoGuard,
                            locked = state.busy,
                            onCheckedChange = viewModel::setPersistentNotification,
                            showDivider = false,
                        )
                    }
                    Panel(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.sleep_keep_alive),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.onSurface,
                            )
                            AnimatedText(
                                text = if (config.sleepKeepAlive == SleepKeepAlive.OFF) {
                                    stringResource(R.string.sleep_keep_alive_desc_off)
                                } else {
                                    stringResource(R.string.sleep_keep_alive_desc_on, config.sleepKeepAlive.label())
                                },
                                color = colors.onSurfaceMuted,
                                fontSize = 12,
                                lineHeight = 20,
                            )
                        }
                        SegmentedSelector(
                            options = SleepKeepAlive.entries,
                            selected = config.sleepKeepAlive,
                            label = { it.label() },
                            onSelect = viewModel::setSleepKeepAlive,
                        )
                    }
                }
            }
        }
        item(key = "updates") {
            Entrance(index = 3, play = true) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle(stringResource(R.string.update_title))
                    Panel(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
                        SwitchRow(
                            title = stringResource(R.string.update_auto_check),
                            summary = stringResource(R.string.update_auto_check_summary),
                            checked = state.autoCheckUpdates,
                            enabled = true,
                            onCheckedChange = viewModel::setAutoCheckUpdates,
                            showDivider = false,
                        )
                    }
                }
            }
        }
        item(key = "cleanup") {
            Entrance(index = 4, play = true) {
                CleanupSection(cleaning = cleaning, enabled = canCleanup, onCleanup = onCleanup)
            }
        }
    }
}

@Composable
private fun CleanupSection(cleaning: Boolean, enabled: Boolean, onCleanup: () -> Unit) {
    val colors = SuzumoriTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.settings_uninstall))
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
                        text = stringResource(if (cleaning) R.string.cleanup_running else R.string.cleanup_title),
                        color = colors.warn,
                        fontSize = 15,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stringResource(R.string.cleanup_summary),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = colors.onSurfaceMuted,
                    )
                }
                Chevron()
            }
        }
        Footnote(stringResource(R.string.cleanup_footnote))
    }
}

/** 語言選項以該語言本身的名稱顯示，切換前後都看得懂。 */
@Composable
private fun AppLanguage.label(): String = stringResource(
    when (this) {
        AppLanguage.SYSTEM -> R.string.follow_system
        AppLanguage.TRADITIONAL_CHINESE -> R.string.language_traditional_chinese
        AppLanguage.SIMPLIFIED_CHINESE -> R.string.language_simplified_chinese
        AppLanguage.ENGLISH -> R.string.language_english
    },
)

@Composable
private fun ThemeMode.label(): String = stringResource(
    when (this) {
        ThemeMode.SYSTEM -> R.string.follow_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Composable
private fun NotificationStyle.label(): String = stringResource(
    when (this) {
        NotificationStyle.SYSTEM -> R.string.notification_style_system
        NotificationStyle.POSITIONED -> R.string.notification_style_positioned
        NotificationStyle.FOLDABLE -> R.string.notification_style_foldable
    },
)

@Composable
private fun NotificationPosition.label(): String = stringResource(
    when (this) {
        NotificationPosition.TOP -> R.string.notification_position_top
        NotificationPosition.BOTTOM -> R.string.notification_position_bottom
    },
)

@Composable
private fun SleepKeepAlive.label(): String = stringResource(
    when (this) {
        SleepKeepAlive.OFF -> R.string.sleep_keep_alive_off
        SleepKeepAlive.ONE_MINUTE -> R.string.sleep_keep_alive_1m
        SleepKeepAlive.FIVE_MINUTES -> R.string.sleep_keep_alive_5m
        SleepKeepAlive.TEN_MINUTES -> R.string.sleep_keep_alive_10m
    },
)

