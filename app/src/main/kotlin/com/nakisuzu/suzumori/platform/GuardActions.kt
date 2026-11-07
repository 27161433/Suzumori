package com.nakisuzu.suzumori.platform

import com.nakisuzu.suzumori.core.Packages
import com.nakisuzu.suzumori.core.config.ConfigStore
import com.nakisuzu.suzumori.core.config.SleepKeepAlive
import com.nakisuzu.suzumori.core.guard.CleanupResult
import com.nakisuzu.suzumori.core.guard.GuardController
import com.nakisuzu.suzumori.core.guard.Trigger

/** 使用者切換開關時，同時更新設定、名單與背景排程。 */
class GuardActions(
    private val controller: GuardController,
    private val configs: ConfigStore,
    private val scheduler: GuardScheduler,
    private val selfPackage: String,
) {
    suspend fun setAutoGuard(enabled: Boolean) {
        scheduler.apply(configs.update { it.copy(autoGuard = enabled) })
        if (enabled) controller.ensure(Trigger.SETTINGS_CHANGED)
    }

    /** 關閉時只移除本 App 加入的項目並清除基準；不刪除 key。 */
    suspend fun setNightProtection(enabled: Boolean) {
        configs.update { it.copy(nightProtection = enabled) }
        if (enabled) {
            controller.ensure(Trigger.SETTINGS_CHANGED)
        } else {
            controller.removeOwnedFromGameAllowlist(Trigger.SETTINGS_CHANGED, setOf(Packages.GMS, selfPackage), clearBaseline = true)
        }
    }

    suspend fun setSelfInGameAllowlist(enabled: Boolean) {
        configs.update { it.copy(selfInGameAllowlist = enabled) }
        if (enabled) {
            controller.ensure(Trigger.SETTINGS_CHANGED)
        } else {
            controller.removeOwnedFromGameAllowlist(Trigger.SETTINGS_CHANGED, setOf(selfPackage), clearBaseline = false)
        }
    }

    suspend fun setPersistentNotification(enabled: Boolean) {
        scheduler.apply(configs.update { it.copy(persistentNotification = enabled) })
    }

    /** 常駐服務監聽設定，會立刻依新的間隔重排或取消睡眠喚醒鬧鐘（見 KeepAliveAlarms.rearmSleep）。 */
    suspend fun setSleepKeepAlive(interval: SleepKeepAlive) {
        configs.update { it.copy(sleepKeepAlive = interval) }
    }

    suspend fun cleanupForUninstall(): CleanupResult {
        val result = controller.prepareForUninstall()
        scheduler.apply(configs.current())
        return result
    }

    /** 依目前設定啟動服務與排程，例如開啟 App 時。 */
    suspend fun applySchedule() {
        scheduler.apply(configs.current())
    }
}
