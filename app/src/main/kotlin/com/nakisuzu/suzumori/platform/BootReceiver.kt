package com.nakisuzu.suzumori.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nakisuzu.suzumori.SuzumoriApp
import com.nakisuzu.suzumori.core.guard.Trigger
import kotlinx.coroutines.launch

/**
 * 開機（含解鎖前）與 App 更新後啟動守護。
 *
 * 宣告為 directBootAware，第一次解鎖前就能補回名單；所有資料都在裝置保護儲存區。
 * 小米需要使用者開啟本 App 的「自啟動」，否則收不到這些廣播。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val trigger = when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> Trigger.LOCKED_BOOT
            Intent.ACTION_BOOT_COMPLETED -> Trigger.BOOT
            Intent.ACTION_MY_PACKAGE_REPLACED -> Trigger.PACKAGE_REPLACED
            else -> return
        }
        Diag.i("BootReceiver ${intent.action}")
        val container = (context.applicationContext as SuzumoriApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                val config = container.configs.current()
                if (config.autoGuard) {
                    container.scheduler.apply(config)
                    container.controller.ensure(trigger)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
