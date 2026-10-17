package com.nakisuzu.suzumori.platform

import android.app.Service
import android.content.Intent
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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * 常駐的前景服務：監聽兩個名單 key 與螢幕開關，有變化就立即檢查。
 *
 * 沒有前景服務時，App 在背景會被系統暫停，observer 事件會延後送達（實測延後約 10 秒），
 * 而最短的延遲凍結只有約 5 秒，所以必須常駐。不持有 wake lock，也不使用精確鬧鐘。
 */
class GuardService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Diag.i("GuardService onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // intent 為 null 代表程序被殺後由系統依 START_STICKY 重新啟動。
        Diag.i("GuardService onStartCommand startId=$startId stickyRestart=${intent == null} watching=${watching != null}")
        GuardNotifications.ensureChannel(this)
        startForeground(GuardNotifications.ID, GuardNotifications.build(this, null))
        if (watching == null) watching = scope.launch { watch() }
        return START_STICKY
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
            container.guardState.state.map { it.lastPatch }.distinctUntilChanged().collect { lastPatch ->
                Diag.i("notification update")
                GuardNotifications.update(this@GuardService, lastPatch)
            }
        }
        launch {
            container.configs.config.collect { config ->
                if (!config.autoGuard || !config.persistentNotification) stopSelf()
            }
        }
        launch { ensure(Trigger.SERVICE_START) }

        // 自我監測：上次程序為什麼被結束，以及服務存活期間有沒有被凍結。
        launch { container.selfMonitor.reportPreviousExits() }
        launch { container.selfMonitor.watchForPauses() }
        Diag.i("watch launched")
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

    private companion object {
        const val OBSERVER_DEBOUNCE_MILLIS = 300L
    }
}
