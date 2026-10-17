package com.nakisuzu.suzumori.ui.diagnostic

import com.nakisuzu.suzumori.core.baseline.GameAllowlistBaseline
import com.nakisuzu.suzumori.core.settings.SettingKeys
import com.nakisuzu.suzumori.platform.DeviceInfo
import com.nakisuzu.suzumori.platform.ReceiverInspector

data class DiagnosticUiState(
    val canWrite: Boolean = false,
    val millet: KeyState = KeyState(SettingKeys.MILLET_NO_RESTRICT_APP),
    val game: KeyState = KeyState(SettingKeys.GAME_ALLOWLIST),
    val baseline: GameAllowlistBaseline? = null,
    val device: DeviceInfo? = null,
    val receivers: List<ReceiverInspector.Check> = emptyList(),
    val busy: Boolean = false,
    /** 延遲解凍測試已排定、尚未送出。 */
    val pendingDelayedSend: Boolean = false,
    /** 新的在前。 */
    val log: List<LogEntry> = emptyList(),
    /** 剛完成的操作結果，以對話框顯示；null 表示沒有要顯示的結果。 */
    val lastResult: LogEntry? = null,
    /** 結果對話框是否展開。關閉動畫結束後才清除 [lastResult]。 */
    val resultVisible: Boolean = false,
)

data class KeyState(
    val key: String,
    val loaded: Boolean = false,
    val raw: String? = null,
    val entries: List<String> = emptyList(),
    val gmsPresent: Boolean = false,
    val selfPresent: Boolean = false,
    /** App 開著的期間，ContentObserver 收到這個 key 變更的次數（包含自己的寫入）。 */
    val observedChanges: Int = 0,
    val lastObservedAtMillis: Long? = null,
)

data class LogEntry(val id: Long, val timeMillis: Long, val title: String, val detail: String = "")
