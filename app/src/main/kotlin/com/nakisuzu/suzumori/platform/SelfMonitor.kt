package com.nakisuzu.suzumori.platform

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.ApplicationStartInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.SystemClock
import com.nakisuzu.suzumori.core.guard.ActionRecord
import com.nakisuzu.suzumori.core.guard.GuardStateStore
import com.nakisuzu.suzumori.core.guard.PauseDetector
import com.nakisuzu.suzumori.core.guard.Trigger
import com.nakisuzu.suzumori.core.log.EventEntry
import com.nakisuzu.suzumori.core.log.EventLog
import com.nakisuzu.suzumori.core.log.EventLogText
import com.nakisuzu.suzumori.core.log.ExitReasons
import com.nakisuzu.suzumori.core.log.truncateForLog
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

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
        Diag.i("reportPreviousExits start")
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val since = states.current().lastExitReportedAtMillis ?: 0L
        val exits = runCatching { activityManager.getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXITS) }
            .getOrDefault(emptyList())
            .filter { it.timestamp > since }
            .sortedBy { it.timestamp }
        Diag.i("reportPreviousExits found ${exits.size} new")
        if (exits.isEmpty()) return

        for (exit in exits) {
            record(
                action = "程序曾被結束（${EventLogText.formatTime(exit.timestamp)}）",
                // 已知的系統說明直接顯示中文；不認得的保留原因與原文，方便追查。
                result = ExitReasons.describe(exit.description) ?: buildString {
                    append(reasonLabel(exit.reason))
                    exit.description?.takeIf { it.isNotBlank() }?.let { append("：").append(truncateForLog(it, 120)) }
                },
            )
        }
        // 被結束後 10 秒內就被拉起（例如從多工畫面滑掉後系統立刻重建服務），守護幾乎沒中斷，不算異常。
        // 只有最後一次結束能和這次啟動比對；同一批裡更早的結束不知道多久後才重新啟動，照樣算。
        val latest = exits.last()
        val recovered = processStartedAt - latest.timestamp in 0..QUICK_RESTART_MILLIS
        val notable = exits.lastOrNull { it.reason !in EXPECTED_REASONS && !(it === latest && recovered) }
        states.update { state ->
            val issue = when {
                notable != null -> {
                    val summary = ExitReasons.describe(notable.description) ?: reasonLabel(notable.reason)
                    ActionRecord(notable.timestamp, Trigger.SELF_MONITOR, "$EXIT_ISSUE_PREFIX$summary")
                }
                // 最近一次被結束後很快就恢復，表示現在運作正常，之前留下的「被結束」狀態可以清掉；被暫停的紀錄保留。
                recovered && state.lastSelfIssue?.summary?.startsWith(EXIT_ISSUE_PREFIX) == true -> null
                else -> state.lastSelfIssue
            }
            state.copy(lastExitReportedAtMillis = latest.timestamp, lastSelfIssue = issue)
        }
    }

    /**
     * 記錄這個程序是怎麼啟動的，以及距離上次被結束多久，用來確認被結束後有沒有自動重新啟動。
     * 服務可能在同一個程序內重建，每個程序只記一次。
     */
    suspend fun reportProcessStart() {
        if (!startReported.compareAndSet(false, true)) return
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val startedAt = processStartedAt
        val lastExit = runCatching { activityManager.getHistoricalProcessExitReasons(context.packageName, 0, 1) }
            .getOrDefault(emptyList())
            .firstOrNull { it.timestamp <= startedAt }
        val reason = startReason(activityManager)
        Diag.i("process start reason=${reason?.first} lastExit=${lastExit?.timestamp}")
        record(
            action = when (reason?.second) {
                true -> "程序自動重新啟動"
                false -> "程序由使用者開啟"
                null -> "程序已啟動"
            },
            result = buildString {
                append(reason?.first ?: "啟動原因不明")
                lastExit?.let { append("・距離上次被結束 ").append(durationText(startedAt - it.timestamp)) }
            },
        )
    }

    /** 啟動原因的中文說明，以及是不是自動啟動（不是使用者開啟畫面）。Android 15 以前讀不到，回傳 null。 */
    private fun startReason(activityManager: ActivityManager): Pair<String, Boolean>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null
        // 清單新的在前，也包含程序已存在時開啟畫面（HOT）的紀錄，要找的是建立這個程序的冷啟動。
        // 這時啟動流程可能還沒走完，系統尚未填入 pid（為 0），所以 0 也算。
        val info = runCatching { activityManager.getHistoricalProcessStartReasons(MAX_STARTS) }
            .getOrDefault(emptyList())
            .firstOrNull { it.startType == ApplicationStartInfo.START_TYPE_COLD && it.pid in setOf(0, Process.myPid()) }
            ?: return null
        return when (info.reason) {
            ApplicationStartInfo.START_REASON_SERVICE -> "系統重新啟動背景服務" to true
            ApplicationStartInfo.START_REASON_BOOT_COMPLETE -> "開機後自動啟動" to true
            ApplicationStartInfo.START_REASON_BROADCAST -> when (info.intent?.action) {
                Intent.ACTION_MY_PACKAGE_REPLACED -> "App 更新後重新啟動" to true
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> "開機後自動啟動" to true
                else -> "收到系統廣播後啟動" to true
            }
            ApplicationStartInfo.START_REASON_JOB -> "定期檢查排程喚醒" to true
            ApplicationStartInfo.START_REASON_ALARM -> "鬧鐘排程喚醒" to true
            ApplicationStartInfo.START_REASON_PUSH -> "推播喚醒" to true
            ApplicationStartInfo.START_REASON_CONTENT_PROVIDER -> "被其他程式查詢資料時啟動" to true
            ApplicationStartInfo.START_REASON_BACKUP -> "系統備份時啟動" to true
            ApplicationStartInfo.START_REASON_LAUNCHER -> "從桌面開啟" to false
            ApplicationStartInfo.START_REASON_LAUNCHER_RECENTS -> "從多工畫面開啟" to false
            ApplicationStartInfo.START_REASON_START_ACTIVITY -> "開啟 App 畫面" to false
            else -> "其他原因（${info.reason}）" to true
        }
    }

    /** 「3 秒」「12 分」「2 小時 5 分」「3 天」。 */
    private fun durationText(millis: Long): String {
        val seconds = (millis / 1000).coerceAtLeast(0)
        return when {
            seconds < 60 -> "$seconds 秒"
            seconds < 3_600 -> "${seconds / 60} 分"
            seconds < 86_400 -> "${seconds / 3_600} 小時 ${seconds % 3_600 / 60} 分"
            else -> "${seconds / 86_400} 天"
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
            Diag.i("paused for about $seconds s")
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

    private val startReported = AtomicBoolean(false)

    /** 這個程序建立的時間（牆上時間）。 */
    private val processStartedAt: Long
        get() = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime())

    private companion object {
        const val MAX_EXITS = 10
        const val MAX_STARTS = 5

        /** 被結束後在這段時間內重新啟動，視為正常。 */
        const val QUICK_RESTART_MILLIS = 10_000L

        const val EXIT_ISSUE_PREFIX = "程序被結束："
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
