package com.nakisuzu.suzumori

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.nakisuzu.suzumori.core.baseline.BaselineStore
import com.nakisuzu.suzumori.core.diagnostic.FeasibilityTests
import com.nakisuzu.suzumori.core.settings.SettingsStore
import com.nakisuzu.suzumori.core.signal.GmsSignals
import com.nakisuzu.suzumori.platform.BroadcastGmsSignals
import com.nakisuzu.suzumori.platform.DataStoreBaselineStore
import com.nakisuzu.suzumori.platform.SystemSettingsStore

private val Context.baselineDataStore by preferencesDataStore(name = "baseline")

/** 手動建構的依賴。專案規模小，不需要 DI 框架。 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settings: SettingsStore = SystemSettingsStore(appContext)
    val baselines: BaselineStore = DataStoreBaselineStore(appContext.baselineDataStore)
    val signals: GmsSignals = BroadcastGmsSignals(appContext)
    val feasibilityTests = FeasibilityTests(settings, baselines, appContext.packageName)
}
