package com.nakisuzu.suzumori.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nakisuzu.suzumori.SuzumoriApp
import com.nakisuzu.suzumori.core.guard.Trigger
import kotlinx.coroutines.launch

/**
 * [KeepAliveAlarms] 的鬧鐘送達這裡。
 *
 * 解凍與重連中間要等 1.5 秒，所以用 [goAsync]：系統在 [android.content.BroadcastReceiver.PendingResult.finish]
 * 之前持有 wake lock，手機不會在半途睡著。
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Diag.i("AlarmReceiver ${action.substringAfterLast('.')}")
        val container = (context.applicationContext as SuzumoriApp).container
        val result = goAsync()
        container.appScope.launch {
            try {
                when (action) {
                    KeepAliveAlarms.ACTION_NETWORK -> container.controller.nudge(Trigger.NETWORK_CHANGED)
                    KeepAliveAlarms.ACTION_SLEEP -> {
                        container.controller.nudge(Trigger.SLEEP_KEEPALIVE)
                        container.keepAlive.rearmSleep()
                    }
                }
            } finally {
                result.finish()
            }
        }
    }
}
