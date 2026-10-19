package com.nakisuzu.suzumori.core.log

/**
 * 事件紀錄的檔案格式：一行一筆，欄位以 Tab 分隔。
 *
 * 欄位內的反斜線、Tab、換行會被跳脫，null 以 `\N` 表示，所以每一行都能獨立解析，
 * 寫到一半斷電也只會損失最後一行。
 *
 * 第 2 版多了結果的性質（[EventLevel]），觸發原因改存 `Trigger` 的名稱。
 * 第 1 版的紀錄仍可讀取：觸發原因從當時的中文標籤對回名稱，結果的性質從當時的中文文字推斷。
 */
object EventLogCodec {
    private const val VERSION = "2"
    private const val LEGACY_VERSION = "1"
    private const val NULL = "\\N"
    private const val FIELD_COUNT = 9
    private const val LEGACY_FIELD_COUNT = 8

    fun encode(entry: EventEntry): String = listOf(
        VERSION,
        entry.timeMillis.toString(),
        entry.trigger,
        entry.key,
        entry.before,
        entry.after,
        entry.action,
        entry.result,
        entry.level.name,
    ).joinToString("\t") { escape(it) }

    /** 無法解析的行回傳 null。 */
    fun decode(line: String): EventEntry? {
        val fields = line.split('\t')
        val legacy = when {
            fields.size == FIELD_COUNT && fields[0] == VERSION -> false
            fields.size == LEGACY_FIELD_COUNT && fields[0] == LEGACY_VERSION -> true
            else -> return null
        }
        val values = fields.map(::unescape)
        val trigger = values[2] ?: return null
        val result = values[7] ?: return null
        return EventEntry(
            timeMillis = values[1]?.toLongOrNull() ?: return null,
            trigger = if (legacy) LEGACY_TRIGGERS[trigger] ?: trigger else trigger,
            key = values[3],
            before = values[4],
            after = values[5],
            action = values[6] ?: return null,
            result = result,
            level = if (legacy) legacyLevel(result) else EventLevel.entries.firstOrNull { it.name == values[8] } ?: EventLevel.INFO,
        )
    }

    /** 第 1 版存的是觸發原因的中文標籤。 */
    private val LEGACY_TRIGGERS = mapOf(
        "開啟 App" to "APP_OPEN",
        "手動檢查" to "MANUAL_CHECK",
        "手動解凍" to "MANUAL_UNFREEZE",
        "快速設定磚" to "TILE",
        "MILLET 名單變更" to "MILLET_CHANGED",
        "遊戲白名單變更" to "GAME_ALLOWLIST_CHANGED",
        "螢幕開啟" to "SCREEN_ON",
        "螢幕關閉" to "SCREEN_OFF",
        "開機（解鎖前）" to "LOCKED_BOOT",
        "開機" to "BOOT",
        "App 更新" to "PACKAGE_REPLACED",
        "定期檢查" to "PERIODIC",
        "服務啟動" to "SERVICE_START",
        "設定變更" to "SETTINGS_CHANGED",
        "解除安裝前清理" to "UNINSTALL_CLEANUP",
        "自我監測" to "SELF_MONITOR",
    )

    /** 第 1 版沒有存結果的性質，用當時畫面上判斷顏色的規則推斷。 */
    private fun legacyLevel(result: String): EventLevel = when {
        result.startsWith("例外") || result.startsWith("驗證失敗") || result.contains("暫停") || result.startsWith("沒有「修改系統設定」") ->
            EventLevel.WARNING
        result == "成功" -> EventLevel.SUCCESS
        else -> EventLevel.INFO
    }

    private fun escape(value: String?): String {
        if (value == null) return NULL
        return buildString(value.length) {
            for (c in value) {
                when (c) {
                    '\\' -> append("\\\\")
                    '\t' -> append("\\t")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    else -> append(c)
                }
            }
        }
    }

    private fun unescape(field: String): String? {
        if (field == NULL) return null
        return buildString(field.length) {
            var i = 0
            while (i < field.length) {
                val c = field[i]
                if (c == '\\' && i + 1 < field.length) {
                    when (field[i + 1]) {
                        '\\' -> append('\\')
                        't' -> append('\t')
                        'n' -> append('\n')
                        'r' -> append('\r')
                        else -> append(c).append(field[i + 1])
                    }
                    i += 2
                } else {
                    append(c)
                    i++
                }
            }
        }
    }
}
