package com.nakisuzu.suzumori.ui.diagnostic

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 把診斷狀態整理成純文字，方便用分享選單傳出來回報。 */
object DiagnosticReport {
    private val dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
    private val time = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    fun formatDateTime(millis: Long): String = dateTime.format(Instant.ofEpochMilli(millis))

    fun formatTime(millis: Long): String = time.format(Instant.ofEpochMilli(millis))

    /** 用引號標出字串邊界，讓前後空白、空字串和 null 看得出差別。 */
    fun quote(value: String?, limit: Int = Int.MAX_VALUE): String = when {
        value == null -> "（null，key 不存在）"
        value.isEmpty() -> "\"\"（空字串）"
        value.length > limit -> "\"${value.take(limit)}…\"（共 ${value.length} 字，已截斷）"
        else -> "\"$value\""
    }

    fun yesNo(value: Boolean) = if (value) "是" else "否"

    fun format(state: DiagnosticUiState, nowMillis: Long): String = buildString {
        appendLine("Suzumori 第 0 階段診斷報告")
        appendLine("產生時間：${formatDateTime(nowMillis)}")
        appendLine()

        appendLine("[裝置]")
        state.device?.let { d ->
            appendLine("型號：${d.model}")
            appendLine("Android：${d.androidVersion}")
            appendLine("HyperOS：${d.hyperOsVersion ?: "未知"}（${d.hyperOsIncremental ?: "未知"}）")
            appendLine("本 App targetSdk：${d.targetSdk}")
            appendLine("使用者 ID：${d.userId}")
            appendLine("BOOT_COUNT：${d.bootCount ?: "讀不到"}")
            appendLine("忽略電池最佳化（無限制的輔助指標）：${yesNo(d.ignoringBatteryOptimizations)}")
        } ?: appendLine("（尚未讀取）")
        appendLine()

        appendLine("[授權]")
        appendLine("canWrite()：${state.canWrite}")
        appendLine()

        for (key in listOf(state.millet, state.game)) {
            appendLine("[${key.key}]")
            appendLine("GMS 在名單中：${yesNo(key.gmsPresent)}")
            appendLine("本 App 在名單中：${yesNo(key.selfPresent)}")
            appendLine("項目數：${key.entries.size}")
            appendLine("App 開著期間觀察到變更：${key.observedChanges} 次")
            appendLine("原始值：${quote(key.raw)}")
            appendLine()
        }

        appendLine("[遊戲白名單清理基準]")
        state.baseline?.let { b ->
            appendLine("記錄時間：${formatDateTime(b.recordedAtMillis)}")
            appendLine("當時 GMS 在名單中：${yesNo(b.gmsWasPresent)}")
            appendLine("當時本 App 在名單中：${yesNo(b.selfWasPresent)}")
        } ?: appendLine("（沒有基準，本 App 沒有寫入過遊戲白名單）")
        appendLine()

        appendLine("[廣播的靜態接收端]")
        for (check in state.receivers) {
            appendLine(check.label)
            if (check.receivers.isEmpty()) appendLine("  （查不到靜態接收端）")
            for (r in check.receivers) {
                appendLine("  ${r.component}")
                appendLine("    exported=${r.exported}，權限=${r.permission ?: "無"}，本 App 持有=${yesNo(r.permissionHeld)}")
            }
        }
        appendLine()

        appendLine("[紀錄]（舊到新）")
        for (entry in state.log.asReversed()) {
            appendLine("${formatDateTime(entry.timeMillis)}　${entry.title}")
            if (entry.detail.isNotEmpty()) entry.detail.lines().forEach { appendLine("  $it") }
        }
    }
}
