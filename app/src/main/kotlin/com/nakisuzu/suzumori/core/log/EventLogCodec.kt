package com.nakisuzu.suzumori.core.log

/**
 * 事件紀錄的檔案格式：一行一筆，欄位以 Tab 分隔。
 *
 * 欄位內的反斜線、Tab、換行會被跳脫，null 以 `\N` 表示，所以每一行都能獨立解析，
 * 寫到一半斷電也只會損失最後一行。
 */
object EventLogCodec {
    private const val VERSION = "1"
    private const val NULL = "\\N"
    private const val FIELD_COUNT = 8

    fun encode(entry: EventEntry): String = listOf(
        VERSION,
        entry.timeMillis.toString(),
        entry.trigger,
        entry.key,
        entry.before,
        entry.after,
        entry.action,
        entry.result,
    ).joinToString("\t") { escape(it) }

    /** 無法解析的行回傳 null。 */
    fun decode(line: String): EventEntry? {
        val fields = line.split('\t')
        if (fields.size != FIELD_COUNT || fields[0] != VERSION) return null
        val values = fields.map(::unescape)
        return EventEntry(
            timeMillis = values[1]?.toLongOrNull() ?: return null,
            trigger = values[2] ?: return null,
            key = values[3],
            before = values[4],
            after = values[5],
            action = values[6] ?: return null,
            result = values[7] ?: return null,
        )
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
