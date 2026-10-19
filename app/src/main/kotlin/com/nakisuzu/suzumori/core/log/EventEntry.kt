package com.nakisuzu.suzumori.core.log

import kotlinx.coroutines.flow.Flow

/**
 * 一筆事件紀錄。
 *
 * @property trigger 觸發原因，新紀錄是 `Trigger` 的名稱（顯示時才翻譯）；舊版紀錄無法對應時保留原文。
 * @property key 涉及的設定 key；廣播類事件為 null。
 * @property before 修改前的值（已截斷）。
 * @property after 修改後或讀回的值（已截斷）。
 * @property action 執行的動作，例如「追加 GMS」「偵測到外部變更」「送出解凍廣播」，以寫入當下的語言保存。
 * @property result 結果，例如「成功」「驗證失敗」「已送出」或例外內容，以寫入當下的語言保存。
 * @property level 結果的性質，決定顯示的顏色，與語言無關。
 */
data class EventEntry(
    val timeMillis: Long,
    val trigger: String,
    val key: String?,
    val before: String?,
    val after: String?,
    val action: String,
    val result: String,
    val level: EventLevel = EventLevel.INFO,
)

enum class EventLevel {
    INFO,
    SUCCESS,
    WARNING,
}

interface EventLog {
    /** 舊到新排列的所有紀錄。 */
    val entries: Flow<List<EventEntry>>

    suspend fun append(entry: EventEntry)

    suspend fun clear()
}

/** 紀錄中保存的值最長幾個字，超過就截斷。 */
const val LOG_VALUE_LIMIT = 300

/** 超過 [limit] 時截斷，並接上 [suffix] 產生的文字（參數是原本的長度）。 */
fun truncateForLog(value: String?, suffix: (Int) -> String, limit: Int = LOG_VALUE_LIMIT): String? = when {
    value == null -> null
    value.length <= limit -> value
    else -> value.take(limit) + suffix(value.length)
}
