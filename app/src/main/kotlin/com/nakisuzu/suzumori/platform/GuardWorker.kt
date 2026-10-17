package com.nakisuzu.suzumori.platform

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nakisuzu.suzumori.SuzumoriApp
import com.nakisuzu.suzumori.core.guard.Trigger

/** 每 15 分鐘的保底檢查，也順便把被系統停掉的常駐服務拉回來。 */
class GuardWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as SuzumoriApp).container
        val config = container.configs.current()
        if (config.autoGuard) {
            container.scheduler.apply(config)
            container.controller.ensure(Trigger.PERIODIC)
        }
        return Result.success()
    }
}
