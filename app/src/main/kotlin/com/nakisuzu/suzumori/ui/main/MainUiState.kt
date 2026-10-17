package com.nakisuzu.suzumori.ui.main

import com.nakisuzu.suzumori.core.config.GuardConfig
import com.nakisuzu.suzumori.core.guard.ActionRecord
import com.nakisuzu.suzumori.core.log.EventEntry

data class MainUiState(
    val loaded: Boolean = false,
    val canWrite: Boolean = true,
    val milletHasGms: Boolean = false,
    val gameHasGms: Boolean = false,
    /** 本 App 在 MILLET 名單中，代表使用者已把省電策略設為「無限制」。 */
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
