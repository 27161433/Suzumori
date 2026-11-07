package com.nakisuzu.suzumori.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import com.nakisuzu.suzumori.core.config.ConfigStore

/**
 * 用 allow-while-idle 精確鬧鐘叫醒手機補送解凍與重連（見 [AlarmReceiver]）。
 *
 * 兩個鬧鐘：
 * - 網路變化後延遲 25 秒的那一次。用鬧鐘而不是協程延遲，手機在這 25 秒內睡著也會準時觸發；
 *   同一個 PendingIntent 重排會取代前一個，所以連續的變化只留最後一次。
 * - 螢幕關閉時的定期喚醒，間隔由設定決定（[com.nakisuzu.suzumori.core.config.SleepKeepAlive]）。
 *   每次觸發後由 [AlarmReceiver] 再呼叫 [rearmSleep] 排下一次。
 *
 * 鬧鐘以廣播送達，程序被殺掉也還在，系統會重新建立程序送來。本 App 沒有 WAKE_LOCK 權限，
 * 靠系統在廣播處理期間持有的 wake lock 完成工作。targetSdk 22 不需要 SCHEDULE_EXACT_ALARM。
 * 深度休眠時系統對這類鬧鐘有每小時的配額，間隔太短會被延後。
 */
class KeepAliveAlarms(private val context: Context, private val configs: ConfigStore) {
    private val manager = context.getSystemService(AlarmManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)

    fun scheduleNetwork() = set(ACTION_NETWORK, NETWORK_DELAY_MILLIS)

    fun cancelNetwork() = cancel(ACTION_NETWORK)

    /** 依目前設定與螢幕狀態排下一次睡眠喚醒；螢幕亮著、設定關閉或自動守護關閉時取消。可以重複呼叫。 */
    suspend fun rearmSleep() {
        val config = configs.current()
        val interval = config.sleepKeepAlive.intervalMillis
        if (config.autoGuard && interval != null && !power.isInteractive) set(ACTION_SLEEP, interval) else cancel(ACTION_SLEEP)
    }

    private fun set(action: String, delayMillis: Long) {
        manager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + delayMillis,
            pendingIntent(action),
        )
        Diag.i("alarm ${action.substringAfterLast('.')} in $delayMillis ms")
    }

    private fun cancel(action: String) = manager.cancel(pendingIntent(action))

    private fun pendingIntent(action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, AlarmReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_NETWORK = "com.nakisuzu.suzumori.action.NETWORK_NUDGE"
        const val ACTION_SLEEP = "com.nakisuzu.suzumori.action.SLEEP_NUDGE"

        /** 網路變化後等多久才補送：GMS 自己的重連鬧鐘約 11 秒後響，先讓它跑。 */
        const val NETWORK_DELAY_MILLIS = 25_000L
    }
}
