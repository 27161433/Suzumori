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
import com.nakisuzu.suzumori.core.log.LogKey
import com.nakisuzu.suzumori.core.log.LogText
import com.nakisuzu.suzumori.core.log.duration
import com.nakisuzu.suzumori.core.log.msg
import com.nakisuzu.suzumori.core.log.raw
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
                action = msg(LogKey.EXIT_ACTION, raw(EventLogText.formatTime(exit.timestamp))),
                // 認得的系統說明直接換成對應的訊息；不認得的保留原因與原文，方便追查。
                result = describe(exit) ?: run {
                    val description = truncateForLog(exit.description?.takeIf { it.isNotBlank() }, limit = 120)
                    if (description == null) reasonLabel(exit.reason) else msg(LogKey.EXIT_WITH_DESCRIPTION, reasonLabel(exit.reason), raw(description))
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
                    val summary = describe(notable) ?: reasonLabel(notable.reason)
                    ActionRecord(notable.timestamp, Trigger.SELF_MONITOR, msg(LogKey.KILLED_SUMMARY, summary))
                }
                // 最近一次被結束後很快就恢復，表示現在運作正常，之前留下的「被結束」狀態可以清掉；被暫停的紀錄保留。
                recovered && state.lastSelfIssue?.let { isExitIssue(it, state.lastExitReportedAtMillis) } == true -> null
                else -> state.lastSelfIssue
            }
            state.copy(lastExitReportedAtMillis = latest.timestamp, lastSelfIssue = issue)
        }
    }

    private fun describe(exit: ApplicationExitInfo): LogText? = ExitReasons.parse(exit.description)?.let(ExitReasons::toLogText)

    /**
     * [issue] 是不是「被結束」的狀態（而不是「被暫停」）。
     *
     * 被結束的狀態用的是那次結束的時間，一定不晚於已經記錄過的最新結束時間 [lastExitReportedAtMillis]；
     * 被暫停是在之後的程序存活期間偵測到的，時間一定比較晚。
     */
    private fun isExitIssue(issue: ActionRecord, lastExitReportedAtMillis: Long?): Boolean =
        lastExitReportedAtMillis != null && issue.atMillis <= lastExitReportedAtMillis

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
        val reasonText = reason?.first ?: msg(LogKey.START_UNKNOWN)
        record(
            action = msg(
                when (reason?.second) {
                    true -> LogKey.AUTO_RESTARTED
                    false -> LogKey.USER_OPENED
                    null -> LogKey.STARTED
                },
            ),
            result = lastExit?.let { msg(LogKey.SINCE_LAST_EXIT, reasonText, duration(startedAt - it.timestamp)) } ?: reasonText,
        )
    }

    /** 啟動原因，以及是不是自動啟動（不是使用者開啟畫面）。Android 15 以前讀不到，回傳 null。 */
    private fun startReason(activityManager: ActivityManager): Pair<LogText, Boolean>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null
        // 清單新的在前，也包含程序已存在時開啟畫面（HOT）的紀錄，要找的是建立這個程序的冷啟動。
        // 這時啟動流程可能還沒走完，系統尚未填入 pid（為 0），所以 0 也算。
        val info = runCatching { activityManager.getHistoricalProcessStartReasons(MAX_STARTS) }
            .getOrDefault(emptyList())
            .firstOrNull { it.startType == ApplicationStartInfo.START_TYPE_COLD && it.pid in setOf(0, Process.myPid()) }
            ?: return null
        return when (info.reason) {
            ApplicationStartInfo.START_REASON_SERVICE -> msg(LogKey.START_SERVICE) to true
            ApplicationStartInfo.START_REASON_BOOT_COMPLETE -> msg(LogKey.START_BOOT) to true
            ApplicationStartInfo.START_REASON_BROADCAST -> when (info.intent?.action) {
                Intent.ACTION_MY_PACKAGE_REPLACED -> msg(LogKey.START_PACKAGE_REPLACED) to true
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> msg(LogKey.START_BOOT) to true
                else -> msg(LogKey.START_BROADCAST) to true
            }
            ApplicationStartInfo.START_REASON_JOB -> msg(LogKey.START_JOB) to true
            ApplicationStartInfo.START_REASON_ALARM -> msg(LogKey.START_ALARM) to true
            ApplicationStartInfo.START_REASON_PUSH -> msg(LogKey.START_PUSH) to true
            ApplicationStartInfo.START_REASON_CONTENT_PROVIDER -> msg(LogKey.START_CONTENT_PROVIDER) to true
            ApplicationStartInfo.START_REASON_BACKUP -> msg(LogKey.START_BACKUP) to true
            ApplicationStartInfo.START_REASON_LAUNCHER -> msg(LogKey.START_LAUNCHER) to false
            ApplicationStartInfo.START_REASON_LAUNCHER_RECENTS -> msg(LogKey.START_RECENTS) to false
            ApplicationStartInfo.START_REASON_START_ACTIVITY -> msg(LogKey.START_ACTIVITY) to false
            else -> msg(LogKey.START_OTHER, raw(info.reason)) to true
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
            record(action = msg(LogKey.PAUSED_ACTION), result = msg(LogKey.PAUSED_RESULT, raw(seconds)))
            states.update {
                it.copy(lastSelfIssue = ActionRecord(System.currentTimeMillis(), Trigger.SELF_MONITOR, msg(LogKey.PAUSED_SUMMARY, raw(seconds))))
            }
        }
    }

    private suspend fun record(action: LogText, result: LogText) {
        log.append(
            EventEntry(
                timeMillis = System.currentTimeMillis(),
                trigger = Trigger.SELF_MONITOR.name,
                key = null,
                before = null,
                after = null,
                action = action,
                result = result,
            ),
        )
    }

    private fun reasonLabel(reason: Int): LogText = when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF -> msg(LogKey.EXIT_SELF)
        ApplicationExitInfo.REASON_SIGNALED -> msg(LogKey.EXIT_SIGNALED)
        ApplicationExitInfo.REASON_LOW_MEMORY -> msg(LogKey.EXIT_LOW_MEMORY)
        ApplicationExitInfo.REASON_CRASH -> msg(LogKey.EXIT_CRASH)
        ApplicationExitInfo.REASON_CRASH_NATIVE -> msg(LogKey.EXIT_CRASH_NATIVE)
        ApplicationExitInfo.REASON_ANR -> msg(LogKey.EXIT_ANR)
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> msg(LogKey.EXIT_INITIALIZATION_FAILURE)
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> msg(LogKey.EXIT_PERMISSION_CHANGE)
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> msg(LogKey.EXIT_EXCESSIVE_RESOURCE_USAGE)
        ApplicationExitInfo.REASON_USER_REQUESTED -> msg(LogKey.EXIT_USER_REQUESTED)
        ApplicationExitInfo.REASON_USER_STOPPED -> msg(LogKey.EXIT_USER_STOPPED)
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> msg(LogKey.EXIT_DEPENDENCY_DIED)
        ApplicationExitInfo.REASON_OTHER -> msg(LogKey.EXIT_OTHER)
        REASON_FREEZER -> msg(LogKey.EXIT_FREEZER)
        REASON_PACKAGE_STATE_CHANGE -> msg(LogKey.EXIT_PACKAGE_STATE_CHANGE)
        REASON_PACKAGE_UPDATED -> msg(LogKey.EXIT_PACKAGE_UPDATED)
        else -> msg(LogKey.EXIT_UNKNOWN, raw(reason))
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
