package com.nakisuzu.suzumori.core.log

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 把事件紀錄轉成匯出用的純文字。 */
object EventLogText {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

    fun formatTime(millis: Long): String = formatter.format(Instant.ofEpochMilli(millis))

    /** [entries] 舊到新，輸出也是舊到新，方便從頭閱讀一整晚的變化。 */
    fun format(entries: List<EventEntry>, header: String): String = buildString {
        appendLine(header)
        appendLine("共 ${entries.size} 筆")
        appendLine()
        for (entry in entries) {
            appendLine("${formatTime(entry.timeMillis)}　[${entry.trigger}]${entry.key?.let { " $it" }.orEmpty()}")
            appendLine("  動作：${entry.action}")
            appendLine("  結果：${entry.result}")
            entry.before?.let { appendLine("  修改前：\"$it\"") }
            entry.after?.let { appendLine("  修改後：\"$it\"") }
        }
    }
}
