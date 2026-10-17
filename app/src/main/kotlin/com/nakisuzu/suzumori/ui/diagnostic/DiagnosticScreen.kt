package com.nakisuzu.suzumori.ui.diagnostic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nakisuzu.suzumori.core.baseline.GameAllowlistBaseline
import com.nakisuzu.suzumori.platform.DeviceInfo
import com.nakisuzu.suzumori.platform.ReceiverInspector
import com.nakisuzu.suzumori.platform.SystemPages
import com.nakisuzu.suzumori.ui.components.ActionRow
import com.nakisuzu.suzumori.ui.components.SectionCard
import com.nakisuzu.suzumori.ui.components.SectionFooter
import com.nakisuzu.suzumori.ui.components.SectionTitle
import com.nakisuzu.suzumori.ui.components.SuzumoriPage
import com.nakisuzu.suzumori.ui.components.Tone
import com.nakisuzu.suzumori.ui.components.ValueRow
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DiagnosticScreen(viewModel: DiagnosticViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 從授權頁返回時重新讀取。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    DiagnosticContent(
        state = state,
        actions = DiagnosticActions(
            openWriteSettings = {
                SystemPages.openWriteSettings(context)?.let { viewModel.log("開啟授權頁失敗", it) }
            },
            testMillet = viewModel::testMilletWrite,
            testGame = viewModel::testGameAllowlistWrite,
            cleanup = viewModel::cleanupTestWrites,
            sendUnfreeze = viewModel::sendUnfreeze,
            sendReconnect = viewModel::sendReconnect,
            toggleDelayedTest = viewModel::toggleDelayedTest,
            openFcmDiagnostics = {
                val error = SystemPages.openFcmDiagnostics(context)
                viewModel.log(if (error == null) "已開啟 FCM 診斷" else "開啟 FCM 診斷失敗", error.orEmpty())
            },
            share = {
                val report = DiagnosticReport.format(state, System.currentTimeMillis())
                SystemPages.shareText(context, "Suzumori 診斷報告", report)?.let { viewModel.log("分享失敗", it) }
            },
            clearLog = viewModel::clearLog,
            dismissResult = viewModel::dismissResult,
            clearResult = viewModel::clearResult,
        ),
    )
}

class DiagnosticActions(
    val openWriteSettings: () -> Unit,
    val testMillet: () -> Unit,
    val testGame: () -> Unit,
    val cleanup: () -> Unit,
    val sendUnfreeze: () -> Unit,
    val sendReconnect: () -> Unit,
    val toggleDelayedTest: () -> Unit,
    val openFcmDiagnostics: () -> Unit,
    val share: () -> Unit,
    val clearLog: () -> Unit,
    val dismissResult: () -> Unit,
    val clearResult: () -> Unit,
)

@Composable
private fun DiagnosticContent(state: DiagnosticUiState, actions: DiagnosticActions) {
    SuzumoriPage(
        title = "Suzumori",
        subtitle = "第 0 階段・可行性診斷",
        overlay = {
            ResultDialog(
                result = state.lastResult,
                visible = state.resultVisible,
                onDismiss = actions.dismissResult,
                onDismissFinished = actions.clearResult,
            )
        },
    ) {
        if (!state.canWrite) {
            item(key = "permission") { PermissionCard(actions.openWriteSettings) }
        }
        item(key = "millet") {
            Column { KeySection("MILLET_NO_RESTRICT_APP・白天防線", state.millet, selfMeaning = "代表已設為省電「無限制」") }
        }
        item(key = "game") { Column { KeySection("com.miui.game.allowlist・夜間防線", state.game, selfMeaning = null) } }
        item(key = "writeTests") { Column { WriteTestSection(state.busy, state.baseline, actions) } }
        item(key = "broadcasts") {
            Column { BroadcastSection(state.busy, state.pendingDelayedSend, state.delayedRound, actions) }
        }
        item(key = "receivers") { Column { ReceiverSection(state.receivers) } }
        item(key = "device") { Column { DeviceSection(state.canWrite, state.device) } }
        item(key = "logHeader") { Column { LogHeader(actions) } }
        items(state.log, key = { it.id }) { LogItem(it) }
    }
}

@Composable
private fun PermissionCard(openWriteSettings: () -> Unit) {
    SectionCard(
        modifier = Modifier.padding(top = 12.dp),
        color = MiuixTheme.colorScheme.errorContainer,
    ) {
        ArrowPreference(
            title = "寫入權限未開啟",
            titleColor = BasicComponentDefaults.titleColor(color = MiuixTheme.colorScheme.error),
            summary = "點此前往「修改系統設定」開啟。回到 App 時會自動重新檢查。",
            onClick = openWriteSettings,
        )
    }
}

@Composable
private fun KeySection(title: String, key: KeyState, selfMeaning: String?) {
    SectionTitle(title)
    SectionCard {
        if (!key.loaded) {
            BasicComponent(title = "讀取中…")
            return@SectionCard
        }
        ValueRow(
            title = "GMS",
            value = if (key.gmsPresent) "在名單中" else "不在名單中",
            tone = if (key.gmsPresent) Tone.Good else Tone.Bad,
        )
        ValueRow(
            title = "本 App",
            value = if (key.selfPresent) "在名單中" else "不在名單中",
            summary = selfMeaning?.let { "在名單中$it" },
        )
        ValueRow(title = "項目數", value = key.entries.size.toString())
        ValueRow(
            title = "觀察到變更",
            value = key.lastObservedAtMillis
                ?.let { "${key.observedChanges} 次・${DiagnosticReport.formatTime(it)}" }
                ?: "0 次",
            summary = "App 開著期間收到的通知次數，包含自己的寫入",
        )
        RawValueRow(key.raw)
    }
}

@Composable
private fun RawValueRow(raw: String?) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val expandable = (raw?.length ?: 0) > 80
    BasicComponent(
        onClick = if (expandable) ({ expanded = !expanded }) else null,
    ) {
        Text(text = "原始值", style = MiuixTheme.textStyles.headline1)
        Spacer(Modifier.height(4.dp))
        SelectionContainer {
            Text(
                text = DiagnosticReport.quote(raw),
                style = MiuixTheme.textStyles.footnote1,
                fontFamily = FontFamily.Monospace,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (expandable) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (expanded) "點一下收合" else "點一下展開全部",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun WriteTestSection(busy: Boolean, baseline: GameAllowlistBaseline?, actions: DiagnosticActions) {
    SectionTitle("測試寫入")
    SectionCard {
        ActionRow(
            title = "測試寫入 MILLET",
            summary = "GMS 不在名單時追加；已在名單時寫回相同的值。寫完會讀回驗證。",
            enabled = !busy,
            onClick = actions.testMillet,
        )
        ActionRow(
            title = "測試寫入遊戲白名單",
            summary = "GMS 不在名單時追加；已在名單時做一次結尾底線切換。第一次寫入前會記錄清理基準。",
            enabled = !busy,
            onClick = actions.testGame,
        )
        ActionRow(
            title = "清理測試寫入",
            summary = "只移除遊戲白名單中本 App 加入的項目，沒有剩下項目時寫入空字串，不刪除 key。",
            enabled = !busy,
            onClick = actions.cleanup,
        )
        if (baseline == null) {
            ValueRow(title = "清理基準", value = "尚未記錄")
        } else {
            ValueRow(
                title = "清理基準",
                value = "已記錄",
                tone = Tone.Good,
                summary = "${DiagnosticReport.formatDateTime(baseline.recordedAtMillis)} 記錄・" +
                    "當時 GMS ${if (baseline.gmsWasPresent) "已" else "不"}在名單・" +
                    "本 App ${if (baseline.selfWasPresent) "已" else "不"}在名單",
            )
        }
    }
    SectionFooter("MILLET 不需要清理，PowerKeeper 下次重建名單時會自然移除 GMS。")
}

@Composable
private fun BroadcastSection(busy: Boolean, pendingDelayedSend: Boolean, delayedRound: Int, actions: DiagnosticActions) {
    SectionTitle("解凍與重連")
    SectionCard {
        ActionRow(
            title = "送出解凍廣播",
            summary = "REFRESH_WHITE_LIST_ACTION → android",
            enabled = !busy,
            onClick = actions.sendUnfreeze,
        )
        ActionRow(
            title = "送出重連廣播",
            summary = "GCM_RECONNECT 與 MCS／GTALK 心跳 → GMS、GSF",
            enabled = !busy,
            onClick = actions.sendReconnect,
        )
        ActionRow(
            title = if (pendingDelayedSend) "延遲測試進行中（$delayedRound／10）・點一下取消" else "延遲測試：鎖螢幕 5 分鐘",
            summary = if (pendingDelayedSend) {
                "請保持螢幕關閉。每 30 秒送出解凍，2 秒後送出重連，共 10 次。"
            } else {
                "先把本 App 設為省電「無限制」。按下後立刻鎖螢幕，每 30 秒送出解凍並重連，共 10 次（約 5 分鐘）。"
            },
            onClick = actions.toggleDelayedTest,
        )
        ArrowPreference(
            title = "開啟 FCM 診斷",
            summary = "查看 GMS 的 FCM 連線狀態",
            onClick = actions.openFcmDiagnostics,
        )
    }
    SectionFooter(
        "送出後只能確認「已送出」，無法確認對方有收到。亮螢幕本身就會解凍 GMS，" +
            "所以要用延遲測試在螢幕關閉時送出，才能判斷解凍廣播是否有效。",
    )
}

@Composable
private fun ReceiverSection(receivers: List<ReceiverInspector.Check>) {
    SectionTitle("靜態接收端（輔助資訊）")
    SectionCard {
        for (check in receivers) {
            BasicComponent(
                title = check.label,
                summary = if (check.receivers.isEmpty()) {
                    "查不到"
                } else {
                    check.receivers.joinToString("\n") { r ->
                        "${r.component.substringAfterLast('.')}：權限 ${r.permission ?: "無"}" +
                            if (r.permission != null) "（本 App ${if (r.permissionHeld) "持有" else "未持有"}）" else ""
                    }
                },
            )
        }
    }
    SectionFooter(
        "只查得到 manifest 宣告的接收端。系統的解凍接收端、GMS 的重連與心跳接收端都是執行時動態註冊的，查不到是正常的。",
    )
}

@Composable
private fun DeviceSection(canWrite: Boolean, device: DeviceInfo?) {
    SectionTitle("裝置與權限")
    SectionCard {
        ValueRow(
            title = "修改系統設定",
            value = if (canWrite) "已開啟" else "未開啟",
            tone = if (canWrite) Tone.Good else Tone.Bad,
        )
        if (device == null) {
            BasicComponent(title = "讀取中…")
            return@SectionCard
        }
        ValueRow(title = "型號", value = device.model)
        ValueRow(title = "Android", value = device.androidVersion)
        ValueRow(title = "HyperOS", value = device.hyperOsVersion ?: "未知")
        ValueRow(title = "HyperOS 組建", value = device.hyperOsIncremental ?: "未知")
        ValueRow(
            title = "本 App targetSdk",
            value = device.targetSdk.toString(),
            tone = if (device.targetSdk == 22) Tone.Good else Tone.Bad,
        )
        ValueRow(
            title = "使用者 ID",
            value = device.userId.toString(),
            tone = if (device.userId == 0) Tone.Good else Tone.Bad,
            summary = if (device.userId != 0) "不在主使用者下執行。系統只看主使用者的設定值，請在主空間安裝。" else null,
        )
        ValueRow(
            title = "BOOT_COUNT",
            value = device.bootCount?.toString() ?: "讀不到",
            tone = if (device.bootCount != null) Tone.Good else Tone.Bad,
        )
        ValueRow(
            title = "忽略電池最佳化",
            value = DiagnosticReport.yesNo(device.ignoringBatteryOptimizations),
            summary = "設為省電「無限制」時通常會變成「是」",
        )
    }
}

@Composable
private fun LogHeader(actions: DiagnosticActions) {
    SectionTitle("紀錄")
    SectionCard {
        ArrowPreference(
            title = "分享診斷報告",
            summary = "用系統分享選單傳出整份純文字報告",
            onClick = actions.share,
        )
        ActionRow(title = "清除紀錄", onClick = actions.clearLog)
    }
}

@Composable
private fun LogItem(entry: LogEntry) {
    SectionCard {
        BasicComponent {
            Text(
                text = "${DiagnosticReport.formatTime(entry.timeMillis)}　${entry.title}",
                style = MiuixTheme.textStyles.headline1,
            )
            if (entry.detail.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text = entry.detail,
                        style = MiuixTheme.textStyles.footnote1,
                        fontFamily = FontFamily.Monospace,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

/**
 * 操作完成後立即顯示結果；內容同時記在紀錄裡。
 * 關閉時先收起對話框，動畫結束後才清除內容，避免關閉途中內容消失。
 */
@Composable
private fun ResultDialog(result: LogEntry?, visible: Boolean, onDismiss: () -> Unit, onDismissFinished: () -> Unit) {
    val entry = result ?: return

    OverlayDialog(
        show = visible,
        title = entry.title,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        Column {
            SelectionContainer {
                Text(
                    text = entry.detail,
                    style = MiuixTheme.textStyles.footnote1,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
            Spacer(Modifier.height(16.dp))
            TextButton(
                text = "確定",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
