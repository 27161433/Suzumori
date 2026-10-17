package com.nakisuzu.suzumori.core.settings

import kotlinx.coroutines.flow.Flow

/**
 * `Settings.System` 讀寫的抽象，讓核心邏輯可以用假實作測試。
 *
 * 刻意不提供刪除 key 的方法：遊戲白名單的 key 被刪除時，系統讀到 null 會直接返回、
 * 不會清掉快取，被移除的項目會繼續受到保護。要清空必須寫入空字串。
 */
interface SettingsStore {
    /** 是否已取得寫入系統設定的授權（`Settings.System.canWrite()`）。 */
    fun canWrite(): Boolean

    suspend fun read(key: String): String?

    /**
     * 寫入 [value]，回傳系統回報的結果。
     *
     * 沒有授權或被系統拒絕時會拋出例外（例如 `SecurityException`、`IllegalArgumentException`），
     * 呼叫端負責捕捉並記錄。
     */
    suspend fun write(key: String, value: String): Boolean

    /** [key] 每次被任何程式改動時發出一個事件。 */
    fun changes(key: String): Flow<Unit>
}
