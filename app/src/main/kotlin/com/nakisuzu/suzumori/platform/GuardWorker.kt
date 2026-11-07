package com.nakisuzu.suzumori.platform

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nakisuzu.suzumori.SuzumoriApp
import com.nakisuzu.suzumori.core.guard.Trigger

/**
 * 每 15 分鐘的保底檢查，也順便把被系統停掉的常駐服務拉回來，並補送一次解凍與重連。
 *
 * 補送是為了 FCM 斷線後 GMS 自己重連不了的情況（見 [com.nakisuzu.suzumori.core.guard.GuardController.nudge]）。
 * 實測這個工作在 SSRU 預算用完、GMS 的鬧鐘全被延後時仍照常執行。
 */
class GuardWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Diag.i("GuardWorker doWork")
        val container = (applicationContext as SuzumoriApp).container
        val config = container.configs.current()
        if (config.autoGuard) {
            container.scheduler.apply(config)
            container.controller.nudge(Trigger.PERIODIC)
        }
        return Result.success()
    }
}
