package com.nakisuzu.suzumori.ui.diagnostic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nakisuzu.suzumori.core.baseline.GameAllowlistBaseline
import com.nakisuzu.suzumori.platform.DeviceInfo
import com.nakisuzu.suzumori.platform.ReceiverInspector
import com.nakisuzu.suzumori.platform.SystemPages

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
            refresh = viewModel::refresh,
            testMillet = viewModel::testMilletWrite,
            testGame = viewModel::testGameAllowlistWrite,
            cleanup = viewModel::cleanupTestWrites,
            sendUnfreeze = viewModel::sendUnfreeze,
            sendReconnect = viewModel::sendReconnect,
            sendDelayed = viewModel::sendUnfreezeAndReconnectDelayed,
            openFcmDiagnostics = {
                val error = SystemPages.openFcmDiagnostics(context)
                viewModel.log(if (error == null) "已開啟 FCM 診斷" else "開啟 FCM 診斷失敗", error.orEmpty())
            },
            share = {
                val report = DiagnosticReport.format(state, System.currentTimeMillis())
                SystemPages.shareText(context, "Suzumori 診斷報告", report)?.let { viewModel.log("分享失敗", it) }
            },
            clearLog = viewModel::clearLog,
        ),
    )
}

class DiagnosticActions(
    val openWriteSettings: () -> Unit,
    val refresh: () -> Unit,
    val testMillet: () -> Unit,
    val testGame: () -> Unit,
    val cleanup: () -> Unit,
    val sendUnfreeze: () -> Unit,
    val sendReconnect: () -> Unit,
    val sendDelayed: () -> Unit,
    val openFcmDiagnostics: () -> Unit,
    val share: () -> Unit,
    val clearLog: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiagnosticContent(state: DiagnosticUiState, actions: DiagnosticActions) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Suzumori 可行性診斷") }) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "第 0 階段：確認一般 App 身分能不能寫入兩個名單、送出解凍與重連廣播。" +
                        "所有結果都會記在下方紀錄，可以用「分享診斷報告」整份傳出來。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item { PermissionSection(state.canWrite, actions) }
            item { KeySection("MILLET_NO_RESTRICT_APP（白天防線）", state.millet, actions.refresh) }
            item { KeySection("com.miui.game.allowlist（夜間防線）", state.game, actions.refresh) }
            item { WriteTestSection(state.busy, state.baseline, actions) }
            item { BroadcastSection(state.busy, state.pendingDelayedSend, state.receivers, actions) }
            item { DeviceSection(state.device) }
            item { LogHeader(actions) }
            if (state.log.isEmpty()) {
                item { Text("（沒有紀錄）", style = MaterialTheme.typography.bodySmall) }
            }
            items(state.log, key = { it.id }) { LogItem(it) }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, good: Boolean? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (good != null) FontWeight.SemiBold else FontWeight.Normal,
            color = when (good) {
                true -> MaterialTheme.colorScheme.primary
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PermissionSection(canWrite: Boolean, actions: DiagnosticActions) {
    Section("寫入權限") {
        StatusRow("Settings.System.canWrite()", if (canWrite) "已開啟" else "未開啟", canWrite)
        if (!canWrite) Hint("targetSdk 22 的 App 可能一安裝就有這個權限；如果顯示未開啟，請到授權頁開啟「修改系統設定」。")
        OutlinedButton(onClick = actions.openWriteSettings, modifier = Modifier.fillMaxWidth()) {
            Text("前往授權頁")
        }
    }
}

@Composable
private fun KeySection(title: String, key: KeyState, refresh: () -> Unit) {
    Section(title) {
        if (!key.loaded) {
            Text("讀取中…", style = MaterialTheme.typography.bodyMedium)
            return@Section
        }
        StatusRow("GMS 在名單中", DiagnosticReport.yesNo(key.gmsPresent), key.gmsPresent)
        StatusRow("本 App 在名單中", DiagnosticReport.yesNo(key.selfPresent))
        StatusRow("項目數", key.entries.size.toString())
        StatusRow(
            "觀察到變更（App 開著期間）",
            key.lastObservedAtMillis?.let { "${key.observedChanges} 次，最近 ${DiagnosticReport.formatTime(it)}" } ?: "0 次",
        )
        RawValue(key.raw)
        TextButton(onClick = refresh) { Text("重新讀取") }
    }
}

@Composable
private fun RawValue(raw: String?) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Text("原始值", style = MaterialTheme.typography.labelMedium)
    SelectionContainer {
        Text(
            DiagnosticReport.quote(raw),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if ((raw?.length ?: 0) > 120) {
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收合" else "展開全部") }
    }
}

@Composable
private fun WriteTestSection(busy: Boolean, baseline: GameAllowlistBaseline?, actions: DiagnosticActions) {
    Section("測試寫入") {
        Hint("MILLET：GMS 不在名單時追加；已在名單時寫回相同的值。寫完會讀回驗證。")
        Button(onClick = actions.testMillet, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("測試寫入 MILLET")
        }
        Hint("遊戲白名單：GMS 不在名單時追加；已在名單時做一次結尾底線切換。第一次寫入前會記錄基準。")
        Button(onClick = actions.testGame, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("測試寫入遊戲白名單")
        }
        HorizontalDivider()
        if (baseline == null) {
            StatusRow("清理基準", "無")
        } else {
            StatusRow("基準記錄時間", DiagnosticReport.formatDateTime(baseline.recordedAtMillis))
            StatusRow("當時 GMS 在遊戲白名單", DiagnosticReport.yesNo(baseline.gmsWasPresent))
            StatusRow("當時本 App 在遊戲白名單", DiagnosticReport.yesNo(baseline.selfWasPresent))
        }
        Hint("清理只移除遊戲白名單中本 App 加入的項目，沒有剩下項目時寫入空字串，不會刪除 key。MILLET 不清理，PowerKeeper 下次重建時會自然移除 GMS。")
        FilledTonalButton(onClick = actions.cleanup, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("清理測試寫入")
        }
    }
}

@Composable
private fun BroadcastSection(
    busy: Boolean,
    pendingDelayedSend: Boolean,
    receivers: List<ReceiverInspector.Check>,
    actions: DiagnosticActions,
) {
    Section("解凍與重連") {
        Hint("送出後只能確認「已送出」，無法確認對方有收到。建議先送解凍，等 1 到 2 秒再送重連，然後開 FCM 診斷確認連線。")
        Button(onClick = actions.sendUnfreeze, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("送出解凍廣播")
        }
        Button(onClick = actions.sendReconnect, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("送出重連廣播")
        }
        Hint(
            "亮螢幕本身就會解凍 GMS，所以上面兩個按鈕無法證明解凍廣播有效。" +
                "延遲測試：先把本 App 設為省電「無限制」，按下後立刻鎖螢幕，60 秒後會在螢幕關閉時送出解凍、等 2 秒再送重連。",
        )
        FilledTonalButton(
            onClick = actions.sendDelayed,
            enabled = !pendingDelayedSend,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (pendingDelayedSend) "延遲測試進行中…" else "60 秒後解凍並重連（鎖螢幕測試）")
        }
        OutlinedButton(onClick = actions.openFcmDiagnostics, modifier = Modifier.fillMaxWidth()) {
            Text("開啟 FCM 診斷")
        }
        HorizontalDivider()
        Text("靜態接收端（輔助資訊）", style = MaterialTheme.typography.labelMedium)
        Hint(
            "只查得到 manifest 宣告的接收端。系統的解凍接收端、GMS 的重連與心跳接收端都是執行時動態註冊的，" +
                "查不到是正常的。若查到的接收端要求本 App 沒有的權限，廣播會被默默丟棄。",
        )
        for (check in receivers) {
            Text(check.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            if (check.receivers.isEmpty()) {
                Text("　查不到", style = MaterialTheme.typography.bodySmall)
            }
            for (r in check.receivers) {
                Text(
                    "　${r.component.substringAfterLast('.')}：權限 ${r.permission ?: "無"}" +
                        if (r.permission != null) "（本 App ${if (r.permissionHeld) "持有" else "未持有"}）" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (r.permissionHeld) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun DeviceSection(device: DeviceInfo?) {
    Section("裝置資訊") {
        if (device == null) {
            Text("讀取中…", style = MaterialTheme.typography.bodyMedium)
            return@Section
        }
        StatusRow("型號", device.model)
        StatusRow("Android", device.androidVersion)
        StatusRow("HyperOS", device.hyperOsVersion ?: "未知")
        StatusRow("HyperOS 組建", device.hyperOsIncremental ?: "未知")
        StatusRow("本 App targetSdk", device.targetSdk.toString(), device.targetSdk == 22)
        StatusRow("使用者 ID", device.userId.toString(), device.userId == 0)
        if (device.userId != 0) Hint("目前不在主使用者下執行。系統只看主使用者的設定值，請在主空間安裝本 App。")
        StatusRow("BOOT_COUNT", device.bootCount?.toString() ?: "讀不到", device.bootCount != null)
        StatusRow("忽略電池最佳化", DiagnosticReport.yesNo(device.ignoringBatteryOptimizations))
    }
}

@Composable
private fun LogHeader(actions: DiagnosticActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("紀錄（新的在上）", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = actions.share, modifier = Modifier.weight(1f)) { Text("分享診斷報告") }
            OutlinedButton(onClick = actions.clearLog, modifier = Modifier.weight(1f)) { Text("清除紀錄") }
        }
    }
}

@Composable
private fun LogItem(entry: LogEntry) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "${DiagnosticReport.formatTime(entry.timeMillis)}　${entry.title}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (entry.detail.isNotEmpty()) {
            SelectionContainer {
                Text(entry.detail, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}
