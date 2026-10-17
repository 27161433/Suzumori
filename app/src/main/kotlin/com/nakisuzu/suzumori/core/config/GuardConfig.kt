package com.nakisuzu.suzumori.core.config

import kotlinx.coroutines.flow.Flow

/** 使用者可以調整的開關。 */
data class GuardConfig(
    /** 事件驅動的自動守護：監聽名單變化、螢幕開關、開機與定期檢查。 */
    val autoGuard: Boolean = true,
    /** HyperOS 4 夜間保護：維護遊戲白名單中的 GMS。 */
    val nightProtection: Boolean = true,
    /** 本 App 也加入遊戲白名單，避免夜間自己被凍結而無法補回名單。 */
    val selfInGameAllowlist: Boolean = true,
    /** 以前景服務常駐並顯示通知。關閉後背景反應會變慢。 */
    val persistentNotification: Boolean = true,
)

interface ConfigStore {
    val config: Flow<GuardConfig>

    suspend fun current(): GuardConfig

    /** 套用 [transform] 並回傳新的設定。 */
    suspend fun update(transform: (GuardConfig) -> GuardConfig): GuardConfig
}
