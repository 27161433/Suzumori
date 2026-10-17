package com.nakisuzu.suzumori.platform

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.SystemClock
import com.nakisuzu.suzumori.core.guard.ActionRecord
import com.nakisuzu.suzumori.core.guard.GuardStateStore
import com.nakisuzu.suzumori.core.guard.PauseDetector
import com.nakisuzu.suzumori.core.guard.Trigger
import com.nakisuzu.suzumori.core.log.EventEntry
import com.nakisuzu.suzumori.core.log.EventLog
import com.nakisuzu.suzumori.core.log.EventLogText
import com.nakisuzu.suzumori.core.log.truncateForLog
import kotlinx.coroutines.delay

/**
 * 監測本 App 自己有沒有被凍結或被結束，記到事件紀錄，用來驗證防護是否足夠。
 */
class SelfMonitor(
    private val context: Context,
    private val log: EventLog,
    private val states: GuardStateStore,
) {
    /**
     * 記錄上次以來程序被結束的原因（`getHistoricalProcessExitReasons`，不需要權限）。
     * 每個程序啟動時呼叫一次。
     */
    suspend fun reportPreviousExits() {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val since = states.current().lastExitReportedAtMillis ?: 0L
        val exits = runCatching { activityManager.getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXITS) }
            .getOrDefault(emptyList())
            .filter { it.timestamp > since }
            .sortedBy { it.timestamp }
        if (exits.isEmpty()) return

        for (exit in exits) {
            record(
                action = "程序曾被結束（${EventLogText.formatTime(exit.timestamp)}）",
                result = buildString {
                    append(reasonLabel(exit.reason))
                    exit.description?.takeIf { it.isNotBlank() }?.let { append("：").append(truncateForLog(it, 120)) }
                },
            )
        }
        val notable = exits.lastOrNull { it.reason !in EXPECTED_REASONS }
        states.update { state ->
            state.copy(
                lastExitReportedAtMillis = exits.last().timestamp,
                lastSelfIssue = notable?.let {
                    ActionRecord(it.timestamp, Trigger.SELF_MONITOR, "程序被結束：${reasonLabel(it.reason)}")
                } ?: state.lastSelfIssue,
            )
        }
    }

    /** 在服務存活期間持續偵測本 App 是否被暫停。必須在 Main 執行緒上執行（以 uptime 計時）。 */
    suspend fun watchForPauses(): Nothing {
        val detector = PauseDetector(TICK_MILLIS, PAUSE_THRESHOLD_MILLIS)
        detector.onTick(SystemClock.uptimeMillis())
        while (true) {
            delay(TICK_MILLIS)
            val paused = detector.onTick(SystemClock.uptimeMillis()) ?: continue
            val seconds = paused / 1000
            record(action = "本 App 曾被暫停", result = "約 $seconds 秒（期間 CPU 醒著，但本 App 沒有執行）")
            states.update {
                it.copy(lastSelfIssue = ActionRecord(System.currentTimeMillis(), Trigger.SELF_MONITOR, "被暫停約 $seconds 秒"))
            }
        }
    }

    private suspend fun record(action: String, result: String) {
        log.append(
            EventEntry(
                timeMillis = System.currentTimeMillis(),
                trigger = Trigger.SELF_MONITOR.label,
                key = null,
                before = null,
                after = null,
                action = action,
                result = result,
            ),
        )
    }

    private fun reasonLabel(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "自行結束"
        ApplicationExitInfo.REASON_SIGNALED -> "被訊號終止（常見於系統或省電機制殺掉程序）"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "記憶體不足"
        ApplicationExitInfo.REASON_CRASH -> "閃退"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "原生程式閃退"
        ApplicationExitInfo.REASON_ANR -> "沒有回應（ANR）"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "初始化失敗"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "權限變更"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "資源使用過量"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "使用者或系統要求停止（含強制停止）"
        ApplicationExitInfo.REASON_USER_STOPPED -> "使用者帳號停止"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "依賴的程序結束"
        ApplicationExitInfo.REASON_OTHER -> "系統其他原因"
        REASON_FREEZER -> "凍結期間被結束"
        REASON_PACKAGE_STATE_CHANGE -> "App 狀態變更"
        REASON_PACKAGE_UPDATED -> "App 更新"
        else -> "未知原因（$reason）"
    }

    private companion object {
        const val MAX_EXITS = 10
        const val TICK_MILLIS = 30_000L
        const val PAUSE_THRESHOLD_MILLIS = 10_000L

        // Android 14 新增的原因代碼；minSdk 33，直接用數值避免引用較新 API 的常數。
        const val REASON_FREEZER = 14
        const val REASON_PACKAGE_STATE_CHANGE = 15
        const val REASON_PACKAGE_UPDATED = 16

        /** 預期中的結束（例如安裝更新），不算異常。 */
        val EXPECTED_REASONS = setOf(REASON_PACKAGE_STATE_CHANGE, REASON_PACKAGE_UPDATED)
    }
}
