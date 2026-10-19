package com.nakisuzu.suzumori.core.log

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 事件紀錄中的時間格式。匯出的文字見 `platform/LogExporter`。 */
object EventLogText {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

    fun formatTime(millis: Long): String = formatter.format(Instant.ofEpochMilli(millis))
}
