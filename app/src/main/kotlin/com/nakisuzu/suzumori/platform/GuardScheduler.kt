package com.nakisuzu.suzumori.platform

import android.content.Context
import android.content.Intent
import android.os.UserManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nakisuzu.suzumori.core.config.GuardConfig
import java.util.concurrent.TimeUnit

/** 依照設定啟動或停止常駐服務與定期檢查。可以重複呼叫。 */
class GuardScheduler(private val context: Context) {

    fun apply(config: GuardConfig) {
        val service = Intent(context, GuardService::class.java)
        if (config.autoGuard && config.persistentNotification) {
            context.startForegroundService(service)
        } else {
            context.stopService(service)
        }

        // WorkManager 的資料庫在一般儲存區，解鎖前無法使用；解鎖後的 BOOT_COMPLETED 會再呼叫一次。
        if (!context.getSystemService(UserManager::class.java).isUserUnlocked) return
        val workManager = WorkManager.getInstance(context)
        if (config.autoGuard) {
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<GuardWorker>(15, TimeUnit.MINUTES).build(),
            )
        } else {
            workManager.cancelUniqueWork(PERIODIC_WORK)
        }
    }

    private companion object {
        const val PERIODIC_WORK = "periodic-check"
    }
}
