package com.nakisuzu.suzumori.core.log

import com.nakisuzu.suzumori.core.log.LogKey.ADDED_MEMBERS
import com.nakisuzu.suzumori.core.log.LogKey.APPEND
import com.nakisuzu.suzumori.core.log.LogKey.EXCEPTION
import com.nakisuzu.suzumori.core.log.LogKey.EXIT_ACTION
import com.nakisuzu.suzumori.core.log.LogKey.EXIT_FORCE_STOPPED_BY
import com.nakisuzu.suzumori.core.log.LogKey.EXIT_UNKNOWN
import com.nakisuzu.suzumori.core.log.LogKey.EXIT_WITH_DESCRIPTION
import com.nakisuzu.suzumori.core.log.LogKey.EXTERNAL_CHANGE
import com.nakisuzu.suzumori.core.log.LogKey.GAME_ALLOWLIST_RESTORED
import com.nakisuzu.suzumori.core.log.LogKey.KILLED_SUMMARY
import com.nakisuzu.suzumori.core.log.LogKey.MEMBERS_UNCHANGED
import com.nakisuzu.suzumori.core.log.LogKey.MILLET_RESTORED
import com.nakisuzu.suzumori.core.log.LogKey.PAUSED_RESULT
import com.nakisuzu.suzumori.core.log.LogKey.PAUSED_SUMMARY
import com.nakisuzu.suzumori.core.log.LogKey.RATE_LIMITED
import com.nakisuzu.suzumori.core.log.LogKey.REMOVE
import com.nakisuzu.suzumori.core.log.LogKey.REMOVED_MEMBERS
import com.nakisuzu.suzumori.core.log.LogKey.RETRY
import com.nakisuzu.suzumori.core.log.LogKey.SELF_APP
import com.nakisuzu.suzumori.core.log.LogKey.SEND_EXCEPTION
import com.nakisuzu.suzumori.core.log.LogKey.SENT
import com.nakisuzu.suzumori.core.log.LogKey.SINCE_LAST_EXIT
import com.nakisuzu.suzumori.core.log.LogKey.START_OTHER

/**
 * 把舊版紀錄（紀錄檔第 1、2 版，以繁體中文句子保存）轉成 [LogText]，讓舊紀錄也能以目前的語言顯示。
 *
 * 只認得當時寫入的繁體中文句型；認不出來的整句保留原文。
 */
object LegacyLogText {
    fun parse(text: String): LogText = parseOrNull(text) ?: raw(text)

    /** 舊版截斷值的結尾「…（共 300 字）」換成現在的「…(300)」。 */
    fun value(value: String?): String? = value?.replace(LEGACY_TRUNCATED, "…($1)")

    private fun parseOrNull(text: String): LogText? {
        EXACT[text]?.let { return msg(it) }
        for ((regex, build) in PATTERNS) {
            val match = regex.matchEntire(text) ?: continue
            build(match.groupValues)?.let { return it }
        }
        // 多個短句（修補摘要、廣播結果）以「；」接成一句，每一句都認得才轉換。
        if ("；" in text) {
            val parts = text.split("；").map { parseOrNull(it) ?: return null }
            return clauses(parts)
        }
        return null
    }

    private val LEGACY_TRUNCATED = Regex("…（共 (\\d+) 字）$")

    /** 「GMS、本 App」 */
    private fun names(text: String): LogText = list(text.split("、").map { if (it == "本 App") msg(SELF_APP) else raw(it) })

    /** 「移除 A、B；新增 C」或「成員不變」 */
    private fun diff(text: String): LogText? {
        if (text == "成員不變") return msg(MEMBERS_UNCHANGED)
        val parts = text.split("；").map { part ->
            when {
                part.startsWith("移除 ") -> msg(REMOVED_MEMBERS, names(part.removePrefix("移除 ")))
                part.startsWith("新增 ") -> msg(ADDED_MEMBERS, names(part.removePrefix("新增 ")))
                else -> return null
            }
        }
        return clauses(parts)
    }

    /** 「3 秒」「12 分」「2 小時 5 分」「3 天」 */
    private fun duration(text: String): LogText? {
        Regex("(\\d+) 秒").matchEntire(text)?.let { return msg(LogKey.DURATION_SECONDS, raw(it.groupValues[1])) }
        Regex("(\\d+) 分").matchEntire(text)?.let { return msg(LogKey.DURATION_MINUTES, raw(it.groupValues[1])) }
        Regex("(\\d+) 小時 (\\d+) 分").matchEntire(text)?.let {
            return msg(LogKey.DURATION_HOURS_MINUTES, raw(it.groupValues[1]), raw(it.groupValues[2]))
        }
        Regex("(\\d+) 天").matchEntire(text)?.let { return msg(LogKey.DURATION_DAYS, raw(it.groupValues[1])) }
        return null
    }

    private fun packageName(text: String): LogText = when (text) {
        "手機管家" -> msg(LogKey.PACKAGE_SECURITY_CENTER)
        "系統設定" -> msg(LogKey.PACKAGE_SETTINGS)
        else -> raw(text)
    }

    /** 程序結束原因的舊文字。 */
    private val EXIT_REASONS = mapOf(
        "自行結束" to LogKey.EXIT_SELF,
        "被訊號終止（常見於系統或省電機制殺掉程序）" to LogKey.EXIT_SIGNALED,
        "記憶體不足" to LogKey.EXIT_LOW_MEMORY,
        "閃退" to LogKey.EXIT_CRASH,
        "原生程式閃退" to LogKey.EXIT_CRASH_NATIVE,
        "沒有回應（ANR）" to LogKey.EXIT_ANR,
        "初始化失敗" to LogKey.EXIT_INITIALIZATION_FAILURE,
        "權限變更" to LogKey.EXIT_PERMISSION_CHANGE,
        "資源使用過量" to LogKey.EXIT_EXCESSIVE_RESOURCE_USAGE,
        "使用者或系統要求停止（含強制停止）" to LogKey.EXIT_USER_REQUESTED,
        "使用者帳號停止" to LogKey.EXIT_USER_STOPPED,
        "依賴的程序結束" to LogKey.EXIT_DEPENDENCY_DIED,
        "系統其他原因" to LogKey.EXIT_OTHER,
        "凍結期間被結束" to LogKey.EXIT_FREEZER,
        "App 狀態變更" to LogKey.EXIT_PACKAGE_STATE_CHANGE,
        "App 更新" to LogKey.EXIT_PACKAGE_UPDATED,
    )

    private val EXACT: Map<String, LogKey> = EXIT_REASONS + mapOf(
        "略過修補" to LogKey.SKIP_PATCH,
        "修補次數超過上限，暫停中" to LogKey.PAUSED_BY_RATE_LIMIT,
        "結尾底線切換（開機後重新載入）" to LogKey.TOGGLE_SEPARATOR,
        "遊戲白名單重新載入" to LogKey.GAME_ALLOWLIST_RELOADED,
        "清理遊戲白名單" to LogKey.CLEANUP_GAME_ALLOWLIST,
        "沒有基準紀錄，本 App 沒有寫入過遊戲白名單" to LogKey.NO_BASELINE,
        "名單中沒有本 App 加入的項目" to LogKey.NOTHING_OWNED,
        "沒有「修改系統設定」權限，無法寫入" to LogKey.NO_WRITE_PERMISSION,
        "成功" to LogKey.SUCCESS,
        "驗證失敗" to LogKey.VERIFY_FAILED,
        "GMS 在名單中" to LogKey.GMS_IN_LIST,
        "GMS 不在名單中" to LogKey.GMS_NOT_IN_LIST,
        "送出解凍廣播" to LogKey.SEND_UNFREEZE,
        "送出重連廣播" to LogKey.SEND_RECONNECT,
        "已送出解凍與重連" to LogKey.UNFREEZE_SUMMARY,
        "程序自動重新啟動" to LogKey.AUTO_RESTARTED,
        "程序由使用者開啟" to LogKey.USER_OPENED,
        "程序已啟動" to LogKey.STARTED,
        "啟動原因不明" to LogKey.START_UNKNOWN,
        "本 App 曾被暫停" to LogKey.PAUSED_ACTION,
        "系統重新啟動背景服務" to LogKey.START_SERVICE,
        "開機後自動啟動" to LogKey.START_BOOT,
        "App 更新後重新啟動" to LogKey.START_PACKAGE_REPLACED,
        "收到系統廣播後啟動" to LogKey.START_BROADCAST,
        "定期檢查排程喚醒" to LogKey.START_JOB,
        "鬧鐘排程喚醒" to LogKey.START_ALARM,
        "推播喚醒" to LogKey.START_PUSH,
        "被其他程式查詢資料時啟動" to LogKey.START_CONTENT_PROVIDER,
        "系統備份時啟動" to LogKey.START_BACKUP,
        "從桌面開啟" to LogKey.START_LAUNCHER,
        "從多工畫面開啟" to LogKey.START_RECENTS,
        "開啟 App 畫面" to LogKey.START_ACTIVITY,
        "從多工畫面滑掉" to LogKey.CLEAN_SWIPE_UP,
        "在多工畫面一鍵清理" to LogKey.CLEAN_ONE_KEY,
        "被系統強制清理" to LogKey.CLEAN_FORCE,
        "鎖屏後被清理" to LogKey.CLEAN_LOCK_SCREEN,
        "被垃圾清理結束" to LogKey.CLEAN_GARBAGE,
        "被手機管家最佳化清理" to LogKey.CLEAN_OPTIMIZATION,
        "遊戲加速時被清理" to LogKey.CLEAN_GAME,
        "被省電機制結束" to LogKey.CLEAN_AUTO_POWER,
        "過熱保護時被結束" to LogKey.CLEAN_AUTO_THERMAL,
        "閒置時被系統結束" to LogKey.CLEAN_AUTO_IDLE,
        "睡眠時被自動清理" to LogKey.CLEAN_AUTO_SLEEP,
        "鎖屏後被自動清理" to LogKey.CLEAN_AUTO_LOCK_OFF,
        "系統異常時被自動清理" to LogKey.CLEAN_AUTO_SYSTEM_ABNORMAL,
    )

    /** 依序比對；回傳 null 表示句型相符但內容認不出來，繼續比對下一個。 */
    private val PATTERNS: List<Pair<Regex, (List<String>) -> LogText?>> = listOf(
        Regex("(.+)（第 (\\d+) 次重試）") to { g: List<String> -> parseOrNull(g[1])?.let { msg(RETRY, it, raw(g[2])) } },
        Regex("追加 (.+)") to { g: List<String> -> msg(APPEND, names(g[1])) },
        Regex("移除 (.+)") to { g: List<String> -> msg(REMOVE, names(g[1])) },
        Regex("MILLET 補回 ([^；]+)") to { g: List<String> -> msg(MILLET_RESTORED, names(g[1])) },
        Regex("遊戲白名單補回 ([^；]+)") to { g: List<String> -> msg(GAME_ALLOWLIST_RESTORED, names(g[1])) },
        Regex("每分鐘修補超過 (\\d+) 次，暫停 (\\d+) 分鐘") to { g: List<String> -> msg(RATE_LIMITED, raw(g[1]), raw(g[2])) },
        Regex("例外：(.*)", RegexOption.DOT_MATCHES_ALL) to { g: List<String> -> msg(EXCEPTION, raw(g[1])) },
        Regex("偵測到外部變更（(.*)）") to { g: List<String> -> diff(g[1])?.let { msg(EXTERNAL_CHANGE, it) } },
        Regex("程序曾被結束（(.+)）") to { g: List<String> -> msg(EXIT_ACTION, raw(g[1])) },
        Regex("程序被結束：(.+)") to { g: List<String> -> msg(KILLED_SUMMARY, parse(g[1])) },
        Regex("被(.+)強制停止") to { g: List<String> -> msg(EXIT_FORCE_STOPPED_BY, packageName(g[1])) },
        Regex("未知原因（(-?\\d+)）") to { g: List<String> -> msg(EXIT_UNKNOWN, raw(g[1])) },
        Regex("其他原因（(-?\\d+)）") to { g: List<String> -> msg(START_OTHER, raw(g[1])) },
        Regex("(.+)・距離上次被結束 (.+)") to { g: List<String> ->
            val reason = parseOrNull(g[1])
            val since = duration(g[2])
            if (reason != null && since != null) msg(SINCE_LAST_EXIT, reason, since) else null
        },
        Regex("約 (\\d+) 秒（期間 CPU 醒著，但本 App 沒有執行）") to { g: List<String> -> msg(PAUSED_RESULT, raw(g[1])) },
        Regex("被暫停約 (\\d+) 秒") to { g: List<String> -> msg(PAUSED_SUMMARY, raw(g[1])) },
        // 「系統其他原因：SwipeUpClean」：說明現在認得時直接換成說明，否則保留原文說明。
        Regex("([^：]+)：(.+)", RegexOption.DOT_MATCHES_ALL) to { g: List<String> ->
            EXIT_REASONS[g[1]]?.let { reason ->
                ExitReasons.parse(g[2])?.let(ExitReasons::toLogText) ?: msg(EXIT_WITH_DESCRIPTION, msg(reason), raw(g[2]))
            }
        },
        // 廣播結果「GCM_RECONNECT → com.google.android.gms：已送出」
        Regex("([^；]+)：已送出") to { g: List<String> -> msg(SENT, raw(g[1])) },
        Regex("([^；]+)：例外 ([^；]*)") to { g: List<String> -> msg(SEND_EXCEPTION, raw(g[1]), raw(g[2])) },
    )
}
