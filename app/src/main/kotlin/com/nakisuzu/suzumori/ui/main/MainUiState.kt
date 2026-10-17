package com.nakisuzu.suzumori.ui.main

import com.nakisuzu.suzumori.core.config.GuardConfig
import com.nakisuzu.suzumori.core.guard.ActionRecord
import com.nakisuzu.suzumori.core.log.EventEntry

data class MainUiState(
    val loaded: Boolean = false,
    val canWrite: Boolean = true,
    val milletHasGms: Boolean = false,
    val gameHasGms: Boolean = false,
    /**
     * 使用者已把省電策略設為「無限制」。
     *
     * 小米設為「無限制」時會把 App 加入 DeviceIdle 使用者白名單，以 `isIgnoringBatteryOptimizations` 判斷。
     * 不能再看 MILLET 名單，因為本 App 會自己維持在 MILLET 中。
     */
    val selfNoRestrict: Boolean = false,
    /** `ro.mi.os.version.name` 與組建版本；讀不到時為 null。 */
    val systemVersion: String? = null,
    val config: GuardConfig = GuardConfig(),
    val lastPatch: ActionRecord? = null,
    val lastUnfreeze: ActionRecord? = null,
    /** 新的在前。 */
    val events: List<EventEntry> = emptyList(),
    val busy: Boolean = false,
    val dialog: MainDialog? = null,
    val dialogVisible: Boolean = false,
)

sealed interface MainDialog {
    data class Message(val title: String, val text: String) : MainDialog

    data object ConfirmCleanup : MainDialog

    data class CleanupFinished(val text: String, val canUninstall: Boolean) : MainDialog

    data object ConfirmClearLog : MainDialog
}
