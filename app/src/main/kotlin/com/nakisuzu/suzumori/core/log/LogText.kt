package com.nakisuzu.suzumori.core.log

/**
 * 事件紀錄與狀態摘要中的文字，與語言無關：訊息種類加上參數，顯示時才依目前的 App 語言組成句子。
 *
 * 紀錄檔裡存的是 [LogTextCodec] 編碼後的英文代碼，例如 `APPEND(LIST("GMS",SELF_APP))`，
 * 所以切換語言後，所有紀錄（包括切換前寫入的）都會以新的語言顯示。
 */
sealed interface LogText {
    /** 不翻譯的原文：套件名稱、廣播名稱、例外訊息、系統說明、數字與時間。 */
    data class Raw(val text: String) : LogText

    /** 一則訊息，[args] 依序填進對應語言的句子。 */
    data class Message(val key: LogKey, val args: List<LogText> = emptyList()) : LogText
}

fun raw(text: String): LogText = LogText.Raw(text)

fun raw(number: Number): LogText = LogText.Raw(number.toString())

fun msg(key: LogKey, vararg args: LogText): LogText = LogText.Message(key, args.toList())

/** 把多個項目接成一串，例如「GMS、本 App」。 */
fun list(items: Collection<LogText>): LogText = LogText.Message(LogKey.LIST, items.toList())

/** 把多個短句接成一句，例如「移除 A；新增 B」。 */
fun clauses(items: List<LogText>): LogText = LogText.Message(LogKey.CLAUSES, items)

/** 訊息的種類；原文時為 null。 */
val LogText.key: LogKey?
    get() = (this as? LogText.Message)?.key

/** 時間長度：「3 秒」「12 分」「2 小時 5 分」「3 天」。 */
fun duration(millis: Long): LogText {
    val seconds = (millis / 1000).coerceAtLeast(0)
    return when {
        seconds < 60 -> msg(LogKey.DURATION_SECONDS, raw(seconds))
        seconds < 3_600 -> msg(LogKey.DURATION_MINUTES, raw(seconds / 60))
        seconds < 86_400 -> msg(LogKey.DURATION_HOURS_MINUTES, raw(seconds / 3_600), raw(seconds % 3_600 / 60))
        else -> msg(LogKey.DURATION_DAYS, raw(seconds / 86_400))
    }
}

/**
 * 訊息的種類。名稱會寫進紀錄檔，**改名或刪除會讓舊紀錄無法顯示**，只能新增。
 *
 * 每一種對應一個字串資源（見 `platform/LogTexts`），參數以 `%1$s`、`%2$s` 填入。
 */
enum class LogKey {
    // 組合
    LIST,
    CLAUSES,

    // 守護核心
    SELF_APP,
    SKIP_PATCH,
    PAUSED_BY_RATE_LIMIT,
    APPEND,
    REMOVE,
    TOGGLE_SEPARATOR,
    MILLET_RESTORED,
    GAME_ALLOWLIST_RELOADED,
    GAME_ALLOWLIST_RESTORED,
    CLEANUP_GAME_ALLOWLIST,
    NO_BASELINE,
    NOTHING_OWNED,
    NO_WRITE_PERMISSION,
    RETRY,
    RATE_LIMITED,
    EXCEPTION,
    SUCCESS,
    VERIFY_FAILED,
    EXTERNAL_CHANGE,
    MEMBERS_UNCHANGED,
    REMOVED_MEMBERS,
    ADDED_MEMBERS,
    GMS_IN_LIST,
    GMS_NOT_IN_LIST,
    SEND_UNFREEZE,
    SEND_RECONNECT,
    UNFREEZE_SUMMARY,
    SENT,
    SEND_EXCEPTION,

    // 自我監測
    EXIT_ACTION,
    EXIT_WITH_DESCRIPTION,
    KILLED_SUMMARY,
    AUTO_RESTARTED,
    USER_OPENED,
    STARTED,
    START_UNKNOWN,
    SINCE_LAST_EXIT,
    PAUSED_ACTION,
    PAUSED_RESULT,
    PAUSED_SUMMARY,

    // 程序啟動原因
    START_SERVICE,
    START_BOOT,
    START_PACKAGE_REPLACED,
    START_BROADCAST,
    START_JOB,
    START_ALARM,
    START_PUSH,
    START_CONTENT_PROVIDER,
    START_BACKUP,
    START_LAUNCHER,
    START_RECENTS,
    START_ACTIVITY,
    START_OTHER,

    // 程序結束原因
    EXIT_SELF,
    EXIT_SIGNALED,
    EXIT_LOW_MEMORY,
    EXIT_CRASH,
    EXIT_CRASH_NATIVE,
    EXIT_ANR,
    EXIT_INITIALIZATION_FAILURE,
    EXIT_PERMISSION_CHANGE,
    EXIT_EXCESSIVE_RESOURCE_USAGE,
    EXIT_USER_REQUESTED,
    EXIT_USER_STOPPED,
    EXIT_DEPENDENCY_DIED,
    EXIT_OTHER,
    EXIT_FREEZER,
    EXIT_PACKAGE_STATE_CHANGE,
    EXIT_PACKAGE_UPDATED,
    EXIT_UNKNOWN,
    EXIT_FORCE_STOPPED_BY,

    // 小米的清理原因
    CLEAN_SWIPE_UP,
    CLEAN_ONE_KEY,
    CLEAN_FORCE,
    CLEAN_LOCK_SCREEN,
    CLEAN_GARBAGE,
    CLEAN_OPTIMIZATION,
    CLEAN_GAME,
    CLEAN_AUTO_POWER,
    CLEAN_AUTO_THERMAL,
    CLEAN_AUTO_IDLE,
    CLEAN_AUTO_SLEEP,
    CLEAN_AUTO_LOCK_OFF,
    CLEAN_AUTO_SYSTEM_ABNORMAL,

    // 套件名稱
    PACKAGE_SECURITY_CENTER,
    PACKAGE_SETTINGS,

    // 時間長度
    DURATION_SECONDS,
    DURATION_MINUTES,
    DURATION_HOURS_MINUTES,
    DURATION_DAYS,
}

/**
 * [LogText] 的文字格式：原文以雙引號包住（內部的 `"` 與 `\` 以反斜線跳脫），
 * 訊息寫成 `KEY` 或 `KEY(參數,參數)`，參數本身也可以是訊息。
 */
object LogTextCodec {
    fun encode(text: LogText): String = buildString { write(text) }

    /** 無法解析（例如舊版的純文字，或之後版本新增的種類）時回傳 null。 */
    fun decode(encoded: String): LogText? = try {
        Parser(encoded).parseAll()
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun StringBuilder.write(text: LogText) {
        when (text) {
            is LogText.Raw -> {
                append('"')
                for (c in text.text) {
                    if (c == '"' || c == '\\') append('\\')
                    append(c)
                }
                append('"')
            }
            is LogText.Message -> {
                append(text.key.name)
                if (text.args.isNotEmpty()) {
                    append('(')
                    text.args.forEachIndexed { index, arg ->
                        if (index > 0) append(',')
                        write(arg)
                    }
                    append(')')
                }
            }
        }
    }

    private class Parser(private val s: String) {
        private var pos = 0

        fun parseAll(): LogText {
            val text = parseValue()
            require(pos == s.length) { "多餘的字元" }
            return text
        }

        private fun parseValue(): LogText {
            require(pos < s.length) { "結尾不完整" }
            return if (s[pos] == '"') parseRaw() else parseMessage()
        }

        private fun parseRaw(): LogText {
            pos++
            val out = StringBuilder()
            while (true) {
                require(pos < s.length) { "原文沒有結束" }
                val c = s[pos++]
                when (c) {
                    '"' -> return LogText.Raw(out.toString())
                    '\\' -> {
                        require(pos < s.length) { "跳脫字元不完整" }
                        out.append(s[pos++])
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun parseMessage(): LogText {
            val start = pos
            while (pos < s.length && (s[pos].isUpperCase() || s[pos].isDigit() || s[pos] == '_')) pos++
            val name = s.substring(start, pos)
            val key = LogKey.entries.firstOrNull { it.name == name }
            requireNotNull(key) { "不認得的種類：$name" }
            if (pos >= s.length || s[pos] != '(') return LogText.Message(key)
            pos++
            val args = mutableListOf<LogText>()
            while (true) {
                args += parseValue()
                require(pos < s.length) { "參數沒有結束" }
                when (s[pos++]) {
                    ',' -> continue
                    ')' -> return LogText.Message(key, args)
                    else -> throw IllegalArgumentException("參數之間應為逗號")
                }
            }
        }
    }
}
