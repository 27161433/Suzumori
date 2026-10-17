package com.nakisuzu.suzumori.core.baseline

/**
 * 第一次寫入遊戲白名單之前，GMS 與本 App 是否已經在名單中。
 * 清理時只移除當時不在名單、由本 App 加入的項目。
 */
data class GameAllowlistBaseline(
    val gmsWasPresent: Boolean,
    val selfWasPresent: Boolean,
    val recordedAtMillis: Long,
)

interface BaselineStore {
    suspend fun get(): GameAllowlistBaseline?

    /** 只在尚未有基準時寫入，回傳最終保存的基準。 */
    suspend fun recordIfAbsent(baseline: GameAllowlistBaseline): GameAllowlistBaseline

    suspend fun clear()
}
