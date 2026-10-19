package com.nakisuzu.suzumori

import android.content.Context
import com.nakisuzu.suzumori.core.baseline.BaselineStore
import com.nakisuzu.suzumori.core.config.ConfigStore
import com.nakisuzu.suzumori.core.guard.GuardController
import com.nakisuzu.suzumori.core.guard.GuardStateStore
import com.nakisuzu.suzumori.core.log.EventLog
import com.nakisuzu.suzumori.core.log.FileEventLog
import com.nakisuzu.suzumori.core.settings.SettingsStore
import com.nakisuzu.suzumori.core.signal.GmsSignals
import com.nakisuzu.suzumori.platform.AndroidBootCounter
import com.nakisuzu.suzumori.platform.AppearanceStore
import com.nakisuzu.suzumori.platform.BroadcastGmsSignals
import com.nakisuzu.suzumori.platform.DataStoreBaselineStore
import com.nakisuzu.suzumori.platform.DataStoreConfigStore
import com.nakisuzu.suzumori.platform.DataStoreGuardStateStore
import com.nakisuzu.suzumori.platform.DeviceStorage
import com.nakisuzu.suzumori.platform.GuardActions
import com.nakisuzu.suzumori.platform.GuardScheduler
import com.nakisuzu.suzumori.platform.SelfMonitor
import com.nakisuzu.suzumori.platform.SystemSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 手動建構的依賴。專案規模小，不需要 DI 框架。
 *
 * 可能在開機後、第一次解鎖前建立（LOCKED_BOOT_COMPLETED），所以只能使用裝置保護儲存區，
 * 也不能在這裡碰 WorkManager。
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val storage = DeviceStorage.context(appContext)

    /** 生命週期跟著程序的 scope，給接收器與快速設定磚執行較長的工作。 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: SettingsStore = SystemSettingsStore(appContext)
    val signals: GmsSignals = BroadcastGmsSignals(appContext)
    val baselines: BaselineStore
    val configs: ConfigStore = DataStoreConfigStore(DeviceStorage.dataStore(storage, "config"))
    val guardState: GuardStateStore = DataStoreGuardStateStore(DeviceStorage.dataStore(storage, "guard_state"))
    val eventLog: EventLog = FileEventLog(DeviceStorage.eventLogFile(storage))
    val appearance = AppearanceStore(appContext, DeviceStorage.dataStore(storage, "appearance"))

    init {
        DeviceStorage.migrateLegacyDataStore(appContext, storage, BASELINE)
        baselines = DataStoreBaselineStore(DeviceStorage.dataStore(storage, BASELINE))
    }

    val controller = GuardController(
        settings = settings,
        signals = signals,
        baselines = baselines,
        configs = configs,
        states = guardState,
        bootCounter = AndroidBootCounter(appContext.contentResolver),
        log = eventLog,
        selfPackage = appContext.packageName,
    )

    val scheduler = GuardScheduler(appContext)
    val actions = GuardActions(controller, configs, scheduler, appContext.packageName)
    val selfMonitor = SelfMonitor(appContext, eventLog, guardState)

    private companion object {
        /** 第 0 階段就使用的名稱，沿用以便搬移舊的清理基準。 */
        const val BASELINE = "baseline"
    }
}
