package com.nakisuzu.suzumori.platform

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.ApplicationStartInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.annotation.StringRes
import com.nakisuzu.suzumori.R
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
                action = s(R.string.self_exit_action, EventLogText.formatTime(exit.timestamp)),
                // 認得的系統說明直接換成文字；不認得的保留原因與原文，方便追查。
                result = describe(exit) ?: run {
                    val raw = truncateForLog(exit.description?.takeIf { it.isNotBlank() }, { s(R.string.log_truncated, it) }, limit = 120)
                    if (raw == null) s(reasonLabel(exit.reason), exit.reason) else s(R.string.self_exit_with_description, s(reasonLabel(exit.reason), exit.reason), raw)
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
                    val summary = describe(notable) ?: s(reasonLabel(notable.reason), notable.reason)
                    ActionRecord(notable.timestamp, Trigger.SELF_MONITOR, s(R.string.self_killed_summary, summary))
                }
                // 最近一次被結束後很快就恢復，表示現在運作正常，之前留下的「被結束」狀態可以清掉；被暫停的紀錄保留。
                recovered && state.lastSelfIssue?.let { isExitIssue(it, state.lastExitReportedAtMillis) } == true -> null
                else -> state.lastSelfIssue
            }
            state.copy(lastExitReportedAtMillis = latest.timestamp, lastSelfIssue = issue)
        }
    }

    private fun describe(exit: ApplicationExitInfo): String? = ExitReasons.parse(exit.description)?.let { Labels.exit(context, it) }

    /**
     * [issue] 是不是「被結束」的狀態（而不是「被暫停」）。
     *
     * 被結束的狀態用的是那次結束的時間，一定不晚於已經記錄過的最新結束時間 [lastExitReportedAtMillis]；
     * 被暫停是在之後的程序存活期間偵測到的，時間一定比較晚。用時間判斷，不依賴摘要文字的語言。
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
        val reasonText = reason?.first ?: s(R.string.self_start_unknown)
        record(
            action = s(
                when (reason?.second) {
                    true -> R.string.self_auto_restarted
                    false -> R.string.self_user_opened
                    null -> R.string.self_started
                },
            ),
            result = lastExit?.let { s(R.string.self_since_last_exit, reasonText, Labels.duration(context, startedAt - it.timestamp)) }
                ?: reasonText,
        )
    }

    /** 啟動原因的說明，以及是不是自動啟動（不是使用者開啟畫面）。Android 15 以前讀不到，回傳 null。 */
    private fun startReason(activityManager: ActivityManager): Pair<String, Boolean>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null
        // 清單新的在前，也包含程序已存在時開啟畫面（HOT）的紀錄，要找的是建立這個程序的冷啟動。
        // 這時啟動流程可能還沒走完，系統尚未填入 pid（為 0），所以 0 也算。
        val info = runCatching { activityManager.getHistoricalProcessStartReasons(MAX_STARTS) }
            .getOrDefault(emptyList())
            .firstOrNull { it.startType == ApplicationStartInfo.START_TYPE_COLD && it.pid in setOf(0, Process.myPid()) }
            ?: return null
        return when (info.reason) {
            ApplicationStartInfo.START_REASON_SERVICE -> s(R.string.start_service) to true
            ApplicationStartInfo.START_REASON_BOOT_COMPLETE -> s(R.string.start_boot) to true
            ApplicationStartInfo.START_REASON_BROADCAST -> when (info.intent?.action) {
                Intent.ACTION_MY_PACKAGE_REPLACED -> s(R.string.start_package_replaced) to true
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> s(R.string.start_boot) to true
                else -> s(R.string.start_broadcast) to true
            }
            ApplicationStartInfo.START_REASON_JOB -> s(R.string.start_job) to true
            ApplicationStartInfo.START_REASON_ALARM -> s(R.string.start_alarm) to true
            ApplicationStartInfo.START_REASON_PUSH -> s(R.string.start_push) to true
            ApplicationStartInfo.START_REASON_CONTENT_PROVIDER -> s(R.string.start_content_provider) to true
            ApplicationStartInfo.START_REASON_BACKUP -> s(R.string.start_backup) to true
            ApplicationStartInfo.START_REASON_LAUNCHER -> s(R.string.start_launcher) to false
            ApplicationStartInfo.START_REASON_LAUNCHER_RECENTS -> s(R.string.start_recents) to false
            ApplicationStartInfo.START_REASON_START_ACTIVITY -> s(R.string.start_activity) to false
            else -> s(R.string.start_other, info.reason) to true
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
            record(action = s(R.string.self_paused_action), result = s(R.string.self_paused_result, seconds.toInt()))
            states.update {
                it.copy(
                    lastSelfIssue = ActionRecord(
                        System.currentTimeMillis(),
                        Trigger.SELF_MONITOR,
                        s(R.string.self_paused_summary, seconds.toInt()),
                    ),
                )
            }
        }
    }

    private suspend fun record(action: String, result: String) {
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

    private fun s(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    /** 程序結束原因的字串資源。只有「未知原因」帶參數（原因代碼），其他字串會忽略多給的參數。 */
    @StringRes
    private fun reasonLabel(reason: Int): Int = when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF -> R.string.exit_self
        ApplicationExitInfo.REASON_SIGNALED -> R.string.exit_signaled
        ApplicationExitInfo.REASON_LOW_MEMORY -> R.string.exit_low_memory
        ApplicationExitInfo.REASON_CRASH -> R.string.exit_crash
        ApplicationExitInfo.REASON_CRASH_NATIVE -> R.string.exit_crash_native
        ApplicationExitInfo.REASON_ANR -> R.string.exit_anr
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> R.string.exit_initialization_failure
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> R.string.exit_permission_change
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> R.string.exit_excessive_resource_usage
        ApplicationExitInfo.REASON_USER_REQUESTED -> R.string.exit_user_requested
        ApplicationExitInfo.REASON_USER_STOPPED -> R.string.exit_user_stopped
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> R.string.exit_dependency_died
        ApplicationExitInfo.REASON_OTHER -> R.string.exit_other
        REASON_FREEZER -> R.string.exit_freezer
        REASON_PACKAGE_STATE_CHANGE -> R.string.exit_package_state_change
        REASON_PACKAGE_UPDATED -> R.string.exit_package_updated
        else -> R.string.exit_unknown
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
