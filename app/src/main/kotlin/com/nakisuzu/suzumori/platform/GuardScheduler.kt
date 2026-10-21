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

    /**
     * 自動守護開啟時一律啟動服務監聽；「常駐通知」只決定以前景服務（顯示通知）或一般服務執行。
     * targetSdk 22 不受背景啟動服務的限制，一般服務也能在背景啟動。
     */
    fun apply(config: GuardConfig) {
        val service = Intent(context, GuardService::class.java)
        if (config.autoGuard) {
            service.putExtra(GuardService.EXTRA_FOREGROUND, config.persistentNotification)
            try {
                if (config.persistentNotification) context.startForegroundService(service) else context.startService(service)
            } catch (e: IllegalStateException) {
                // 使用者在系統設定限制了本 App 的背景活動時，系統可能拒絕在背景啟動服務；交給下一次定期檢查重試。
                Diag.i("GuardService start refused: $e")
            }
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
