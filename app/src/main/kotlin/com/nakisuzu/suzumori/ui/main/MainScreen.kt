package com.nakisuzu.suzumori.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nakisuzu.suzumori.core.config.GuardConfig
import com.nakisuzu.suzumori.core.guard.ActionRecord
import com.nakisuzu.suzumori.core.log.EventLogText
import com.nakisuzu.suzumori.platform.SystemPages
import com.nakisuzu.suzumori.ui.components.ActionRow
import com.nakisuzu.suzumori.ui.components.SectionCard
import com.nakisuzu.suzumori.ui.components.SectionFooter
import com.nakisuzu.suzumori.ui.components.SectionTitle
import com.nakisuzu.suzumori.ui.components.SuzumoriDialog
import com.nakisuzu.suzumori.ui.components.SuzumoriPage
import com.nakisuzu.suzumori.ui.components.Tone
import com.nakisuzu.suzumori.ui.components.ValueRow
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MainScreen(state: MainUiState, viewModel: MainViewModel, onOpenLog: () -> Unit) {
    val context = LocalContext.current
    fun open(page: (android.content.Context) -> String?) {
        page(context)?.let { viewModel.showMessage("無法開啟頁面", it) }
    }

    SuzumoriPage(
        title = "Suzumori",
        subtitle = "鈴守・守護 FCM 推播",
        overlay = { MainDialogs(state, viewModel, onUninstall = { open(SystemPages::requestUninstall) }) },
    ) {
        // 提示卡放在一直存在的 item 裡。若在載入後才插入新的 item，LazyColumn 會維持原本的捲動位置，
        // 新卡片會被推到標題列後面。
        item(key = "attention") {
            Column {
                if (state.loaded && !state.canWrite) {
                    AttentionCard(
                        title = "寫入權限未開啟",
                        summary = "點此前往「修改系統設定」開啟。回到 App 時會自動重新檢查。",
                        onClick = { open(SystemPages::openWriteSettings) },
                    )
                }
                if (state.loaded && !state.selfNoRestrict) {
                    AttentionCard(
                        title = "本 App 尚未設為省電「無限制」",
                        summary = "點此開啟本 App 的設定，把「省電策略」設為「無限制」並開啟「自啟動」，否則 Suzumori 在背景會被凍結。",
                        onClick = { open(SystemPages::openAppPowerSettings) },
                    )
                }
            }
        }
        item(key = "status") { Column { StatusSection(state) } }
        item(key = "actions") {
            Column {
                ActionSection(
                    busy = state.busy,
                    onCheck = viewModel::checkNow,
                    onUnfreeze = viewModel::unfreezeNow,
                    onFcmDiagnostics = { open(SystemPages::openFcmDiagnostics) },
                )
            }
        }
        item(key = "keepAlive") { Column { KeepAliveSection(onOpen = { open(SystemPages::openAppPowerSettings) }) } }
        item(key = "settings") { Column { SettingsSection(state.config, state.busy, viewModel) } }
        item(key = "log") {
            Column {
                SectionTitle("事件紀錄")
                SectionCard {
                    ArrowPreference(
                        title = "檢視事件紀錄",
                        summary = "共 ${state.events.size} 筆，可匯出成純文字",
                        onClick = onOpenLog,
                    )
                }
            }
        }
        item(key = "cleanup") {
            Column {
                SectionTitle("解除安裝")
                SectionCard {
                    ActionRow(
                        title = "解除安裝前清理",
                        summary = "只移除本 App 加入遊戲白名單的項目，並停止自動守護",
                        enabled = !state.busy,
                        onClick = viewModel::requestCleanup,
                    )
                }
                SectionFooter("解除安裝前請先清理。解除安裝後 App 就沒有機會再執行清理，留在遊戲白名單中的項目會一直生效。")
            }
        }
    }
}

@Composable
private fun AttentionCard(title: String, summary: String, onClick: () -> Unit) {
    SectionCard(modifier = Modifier.padding(top = 12.dp), color = MiuixTheme.colorScheme.errorContainer) {
        ArrowPreference(
            title = title,
            titleColor = BasicComponentDefaults.titleColor(color = MiuixTheme.colorScheme.error),
            summary = summary,
            onClick = onClick,
        )
    }
}

@Composable
private fun StatusSection(state: MainUiState) {
    SectionTitle("守護狀態")
    SectionCard {
        ValueRow(
            title = "寫入權限",
            value = if (state.canWrite) "已開啟" else "未開啟",
            tone = if (state.canWrite) Tone.Good else Tone.Bad,
        )
        ValueRow(
            title = "MILLET 名單",
            value = if (state.milletHasGms) "GMS 在名單中" else "GMS 不在名單中",
            tone = if (state.milletHasGms) Tone.Good else Tone.Bad,
            summary = "白天防線，擋下 GMS 限制器與背景凍結",
        )
        ValueRow(
            title = "遊戲白名單",
            value = if (state.gameHasGms) "GMS 在名單中" else "GMS 不在名單中",
            tone = when {
                state.gameHasGms -> Tone.Good
                state.config.nightProtection -> Tone.Bad
                else -> Tone.Neutral
            },
            summary = if (state.config.nightProtection) "夜間防線，擋下螢幕關閉後的凍結" else "夜間保護已關閉",
        )
        ValueRow(
            title = "本 App 省電策略",
            value = if (state.selfNoRestrict) "無限制" else "未設為無限制",
            tone = if (state.selfNoRestrict) Tone.Good else Tone.Bad,
        )
        ValueRow(title = "系統版本", value = state.systemVersion ?: "未知")
        ValueRow(title = "最近一次修補", value = recordText(state.lastPatch), summary = state.lastPatch?.summary)
        ValueRow(title = "最近一次解凍", value = recordText(state.lastUnfreeze))
    }
}

private fun recordText(record: ActionRecord?): String =
    record?.let { "${EventLogText.formatTime(it.atMillis).substring(5, 16)}・${it.trigger.label}" } ?: "尚無"

@Composable
private fun ActionSection(busy: Boolean, onCheck: () -> Unit, onUnfreeze: () -> Unit, onFcmDiagnostics: () -> Unit) {
    SectionTitle("操作")
    SectionCard {
        ActionRow(
            title = "立即檢查並修補",
            summary = "名單都正常時只讀取，不寫入也不送廣播",
            enabled = !busy,
            onClick = onCheck,
        )
        ActionRow(
            title = "解凍並重連 GMS",
            summary = "先確保名單正常，再送出解凍，等待後送出重連",
            enabled = !busy,
            onClick = onUnfreeze,
        )
        ArrowPreference(
            title = "開啟 FCM 診斷頁面",
            summary = "確認 GMS 與 FCM 伺服器的連線狀態",
            onClick = onFcmDiagnostics,
        )
    }
    SectionFooter("廣播送出後只能確認「已送出」，無法確認 GMS 有收到。是否真的連上，請以 FCM 診斷頁面為準。")
}

@Composable
private fun KeepAliveSection(onOpen: () -> Unit) {
    SectionTitle("讓 Suzumori 保持運作")
    SectionCard {
        ArrowPreference(
            title = "開啟本 App 的省電和自啟動設定",
            summary = "把「省電策略」設為「無限制」，並開啟「自啟動」",
            onClick = onOpen,
        )
    }
    SectionFooter("Suzumori 必須一直在背景才能即時補回名單。沒有「自啟動」時，小米不會在開機時通知本 App。")
}

@Composable
private fun SettingsSection(config: GuardConfig, busy: Boolean, viewModel: MainViewModel) {
    SectionTitle("設定")
    SectionCard {
        SwitchPreference(
            checked = config.autoGuard,
            onCheckedChange = viewModel::setAutoGuard,
            title = "自動守護",
            summary = "名單變化、螢幕開關、開機時立即檢查，另外每 15 分鐘保底檢查一次",
            enabled = !busy,
        )
        SwitchPreference(
            checked = config.nightProtection,
            onCheckedChange = viewModel::setNightProtection,
            title = "HyperOS 4 夜間保護",
            summary = "23:00 到 07:59 螢幕關閉時，「無限制」名單會失效。把 GMS 加入遊戲白名單，避免夜間被凍結。關閉時只移除本 App 加入的項目。",
            enabled = !busy,
        )
        SwitchPreference(
            checked = config.selfInGameAllowlist,
            onCheckedChange = viewModel::setSelfInGameAllowlist,
            title = "本 App 加入遊戲白名單",
            summary = "HyperOS 4 夜間也會凍結「無限制」的 App，本 App 也不例外；Joyose 可能在半夜清空遊戲白名單。" +
                "如果本 App 在夜間被凍結，就無法在名單被清空後補回。",
            enabled = !busy && config.nightProtection,
        )
        SwitchPreference(
            checked = config.persistentNotification,
            onCheckedChange = viewModel::setPersistentNotification,
            title = "常駐通知",
            summary = "以前景服務常駐，即時監聽名單變化。關閉後在背景的反應會明顯變慢。",
            enabled = !busy && config.autoGuard,
        )
    }
}

/** 主畫面與事件紀錄頁共用的對話框。 */
@Composable
internal fun MainDialogs(state: MainUiState, viewModel: MainViewModel, onUninstall: () -> Unit) {
    when (val dialog = state.dialog) {
        null -> Unit
        is MainDialog.Message -> SuzumoriDialog(
            visible = state.dialogVisible,
            title = dialog.title,
            text = dialog.text,
            confirmText = "確定",
            onConfirm = viewModel::dismissDialog,
            onDismiss = viewModel::dismissDialog,
            onDismissFinished = viewModel::clearDialog,
        )
        MainDialog.ConfirmCleanup -> SuzumoriDialog(
            visible = state.dialogVisible,
            title = "解除安裝前清理",
            text = "會停止自動守護與夜間保護，並從遊戲白名單移除本 App 當初加入的項目（其他程式的項目會保留）。" +
                "MILLET 名單不需要清理，PowerKeeper 下次重建時會自然移除 GMS。",
            confirmText = "清理",
            dismissText = "取消",
            onConfirm = viewModel::confirmCleanup,
            onDismiss = viewModel::dismissDialog,
            onDismissFinished = viewModel::clearDialog,
        )
        is MainDialog.CleanupFinished -> SuzumoriDialog(
            visible = state.dialogVisible,
            title = "清理完成",
            text = dialog.text,
            confirmText = if (dialog.canUninstall) "前往解除安裝" else "確定",
            dismissText = if (dialog.canUninstall) "稍後" else null,
            onConfirm = {
                viewModel.dismissDialog()
                if (dialog.canUninstall) onUninstall()
            },
            onDismiss = viewModel::dismissDialog,
            onDismissFinished = viewModel::clearDialog,
        )
        MainDialog.ConfirmClearLog -> SuzumoriDialog(
            visible = state.dialogVisible,
            title = "清除事件紀錄",
            text = "清除後無法復原。如果還要觀察名單的變化，建議先匯出。",
            confirmText = "清除",
            dismissText = "取消",
            onConfirm = viewModel::confirmClearLog,
            onDismiss = viewModel::dismissDialog,
            onDismissFinished = viewModel::clearDialog,
        )
    }
}
