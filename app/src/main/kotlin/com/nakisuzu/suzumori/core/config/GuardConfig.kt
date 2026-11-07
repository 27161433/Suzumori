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
    /**
     * 螢幕關閉時每隔多久用鬧鐘叫醒手機補送一次解凍與重連。
     * 關閉時只在手機自己醒著時補送（常駐服務每 60 秒一次，加上每 15 分鐘的定期檢查）。
     */
    val sleepKeepAlive: SleepKeepAlive = SleepKeepAlive.OFF,
)

/** 螢幕關閉時定期叫醒手機補送的間隔。60 秒是 GMS 心跳節流的下限，更密沒有用；15 分鐘以上由定期檢查負責。 */
enum class SleepKeepAlive(val intervalMillis: Long?) {
    OFF(null),
    ONE_MINUTE(60_000L),
    FIVE_MINUTES(5 * 60_000L),
    TEN_MINUTES(10 * 60_000L),
    ;

    companion object {
        fun fromName(name: String?): SleepKeepAlive? = entries.firstOrNull { it.name == name }
    }
}

interface ConfigStore {
    val config: Flow<GuardConfig>

    suspend fun current(): GuardConfig

    /** 套用 [transform] 並回傳新的設定。 */
    suspend fun update(transform: (GuardConfig) -> GuardConfig): GuardConfig
}
