package com.nakisuzu.suzumori.platform

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.os.IBinder
import android.os.SystemClock
import com.nakisuzu.suzumori.SuzumoriApp
import com.nakisuzu.suzumori.core.guard.Trigger
import com.nakisuzu.suzumori.core.settings.SettingKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * 常駐服務：監聽兩個名單 key 與螢幕開關，有變化就立即檢查。自動守護開啟時一律執行。
 *
 * 「常駐通知」只決定這個服務是前景服務（顯示通知）還是一般的背景服務，不影響有沒有監聽。
 * 沒有任何服務時，App 在背景會被系統暫停，observer 事件會延後送達（實測延後約 10 秒），
 * 而最短的延遲凍結只有約 5 秒，所以一定要有服務常駐。前景服務比一般服務更不容易被系統暫停或結束。
 * 不持有 wake lock，也不使用精確鬧鐘。
 */
class GuardService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching: Job? = null

    /** 目前顯示中的通知（已套用使用者選的樣式與最近修補）。重新進入前景時沿用它。 */
    private var notification: Notification? = null

    /** 目前是不是前景服務（有顯示常駐通知）。 */
    private var foreground = false

    /** 語言或其他設定改變時加一，讓通知用新的語言重建。 */
    private val configChanges = MutableStateFlow(0)

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 在 onCreate 就開始守護，不等 onStartCommand。
     *
     * 實測從多工畫面滑掉後，HyperOS 會在約 1 秒內重新建立服務，但只呼叫 onCreate 與 onTaskRemoved，
     * 不呼叫 onStartCommand。如果只在 onStartCommand 開始監聽，服務程序雖然在，卻要等到下一次
     * 定期檢查（最長 15 分鐘以上）才會真正開始守護。
     */
    override fun onCreate() {
        super.onCreate()
        Diag.i("GuardService onCreate")
        startWatching()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // intent 為 null 代表程序被殺後由系統依 START_STICKY 重新啟動。
        Diag.i("GuardService onStartCommand startId=$startId stickyRestart=${intent == null} watching=${watching != null}")
        // GuardScheduler 啟動時會帶上要不要進入前景；以 startForegroundService 啟動的必須馬上進入前景。
        // 系統重新啟動（intent 為 null）或只重建服務時沒有這個資訊，交給 watch() 裡的設定監聽決定，
        // 避免關閉常駐通知時還閃一下通知。
        if (intent?.hasExtra(EXTRA_FOREGROUND) == true) setForeground(intent.getBooleanExtra(EXTRA_FOREGROUND, true))
        startWatching()
        return START_STICKY
    }

    /** 開始監聽；可以重複呼叫。 */
    private fun startWatching() {
        if (watching == null) watching = scope.launch { watch() }
    }

    /**
     * 切換前景服務（顯示常駐通知）或一般服務，監聽不受影響；可以重複呼叫。
     *
     * 進入前景時沿用已經更新成使用者所選樣式的通知；用預設樣式會把它蓋掉，
     * 而設定沒有變化，之後也不會再更新回來。
     */
    private fun setForeground(enabled: Boolean) {
        if (enabled) {
            GuardNotifications.ensureChannel(this)
            startForeground(GuardNotifications.ID, notification ?: GuardNotifications.build(this, null))
        } else if (foreground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        if (foreground != enabled) Diag.i("GuardService foreground=$enabled")
        foreground = enabled
    }

    @OptIn(FlowPreview::class)
    private fun CoroutineScope.watch() {
        Diag.i("watch start")
        val container = (application as SuzumoriApp).container
        val controller = container.controller

        suspend fun ensure(trigger: Trigger) {
            val start = SystemClock.uptimeMillis()
            Diag.i("ensure ${trigger.name} start")
            val result = controller.ensure(trigger)
            Diag.i("ensure ${trigger.name} done in ${SystemClock.uptimeMillis() - start} ms: $result")
        }

        // 名單變化：去抖動約 300 ms，合併成一次檢查。自己的寫入也會觸發，但下一次檢查會讀到正常狀態而結束。
        launch {
            merge(
                container.settings.changes(SettingKeys.MILLET_NO_RESTRICT_APP).map { Trigger.MILLET_CHANGED },
                container.settings.changes(SettingKeys.GAME_ALLOWLIST).map { Trigger.GAME_ALLOWLIST_CHANGED },
            ).debounce(OBSERVER_DEBOUNCE_MILLIS).collect { ensure(it) }
        }
        launch { screenEvents(this@GuardService).collect { ensure(it) } }
        launch {
            combine(
                container.guardState.state.map { it.lastPatch }.distinctUntilChanged(),
                container.appearance.notificationStyle,
                container.appearance.notificationPosition,
                configChanges,
            ) { lastPatch, style, position, _ -> Triple(lastPatch, style, position) }.collect { (lastPatch, style, position) ->
                // 不是前景服務時只準備好通知內容，不顯示；之後開啟常駐通知時直接沿用。
                Diag.i("notification update style=$style position=$position foreground=$foreground")
                notification = GuardNotifications.build(this@GuardService, lastPatch, style, position).also {
                    if (foreground) GuardNotifications.post(this@GuardService, it)
                }
            }
        }
        launch {
            container.configs.config.collect { config ->
                if (config.autoGuard) setForeground(config.persistentNotification) else stopSelf()
            }
        }
        launch { ensure(Trigger.SERVICE_START) }

        // 自我監測：上次程序為什麼被結束、這次怎麼啟動的，以及服務存活期間有沒有被凍結。
        launch {
            container.selfMonitor.reportPreviousExits()
            container.selfMonitor.reportProcessStart()
        }
        launch { container.selfMonitor.watchForPauses() }
        Diag.i("watch launched")
    }

    /** 切換 App 語言時，通知頻道名稱與通知內容都要換成新的語言。 */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        GuardNotifications.ensureChannel(this)
        configChanges.value++
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Diag.i("GuardService onTaskRemoved")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Diag.i("GuardService onDestroy")
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** 啟動服務時帶的 extra：是否進入前景（顯示常駐通知）。 */
        const val EXTRA_FOREGROUND = "com.nakisuzu.suzumori.extra.FOREGROUND"

        private const val OBSERVER_DEBOUNCE_MILLIS = 300L
    }
}
