package com.nakisuzu.suzumori.core.log

import org.junit.Assert.assertEquals
import org.junit.Test

/** 舊版（1.0.0 以前）以繁體中文寫入的紀錄，都要能轉成代碼，切換語言後才能翻譯。 */
class LegacyLogTextTest {
    private fun names(vararg items: LogText) = list(items.toList())

    @Test
    fun `守護核心的句子`() {
        assertEquals(msg(LogKey.APPEND, names(raw("GMS"), msg(LogKey.SELF_APP))), LegacyLogText.parse("追加 GMS、本 App"))
        assertEquals(
            msg(LogKey.RETRY, msg(LogKey.APPEND, names(raw("GMS"))), raw("2")),
            LegacyLogText.parse("追加 GMS（第 2 次重試）"),
        )
        assertEquals(msg(LogKey.RATE_LIMITED, raw("10"), raw("5")), LegacyLogText.parse("每分鐘修補超過 10 次，暫停 5 分鐘"))
        assertEquals(msg(LogKey.EXCEPTION, raw("java.lang.SecurityException: x")), LegacyLogText.parse("例外：java.lang.SecurityException: x"))
        assertEquals(msg(LogKey.NO_WRITE_PERMISSION), LegacyLogText.parse("沒有「修改系統設定」權限，無法寫入"))
        assertEquals(msg(LogKey.TOGGLE_SEPARATOR), LegacyLogText.parse("結尾底線切換（開機後重新載入）"))
    }

    @Test
    fun `外部變更與廣播結果`() {
        assertEquals(
            msg(
                LogKey.EXTERNAL_CHANGE,
                clauses(
                    listOf(
                        msg(LogKey.REMOVED_MEMBERS, names(raw("com.google.android.gms"))),
                        msg(LogKey.ADDED_MEMBERS, names(raw("com.a"), raw("com.b"))),
                    ),
                ),
            ),
            LegacyLogText.parse("偵測到外部變更（移除 com.google.android.gms；新增 com.a、com.b）"),
        )
        assertEquals(msg(LogKey.EXTERNAL_CHANGE, msg(LogKey.MEMBERS_UNCHANGED)), LegacyLogText.parse("偵測到外部變更（成員不變）"))
        assertEquals(
            clauses(
                listOf(
                    msg(LogKey.SENT, raw("GCM_RECONNECT → com.google.android.gms")),
                    msg(LogKey.SEND_EXCEPTION, raw("MCS_HEARTBEAT → com.google.android.gms"), raw("java.lang.SecurityException")),
                ),
            ),
            LegacyLogText.parse("GCM_RECONNECT → com.google.android.gms：已送出；MCS_HEARTBEAT → com.google.android.gms：例外 java.lang.SecurityException"),
        )
        assertEquals(
            clauses(listOf(msg(LogKey.MILLET_RESTORED, names(raw("GMS"))), msg(LogKey.GAME_ALLOWLIST_RESTORED, names(msg(LogKey.SELF_APP))))),
            LegacyLogText.parse("MILLET 補回 GMS；遊戲白名單補回 本 App"),
        )
    }

    @Test
    fun `自我監測的句子`() {
        assertEquals(msg(LogKey.EXIT_ACTION, raw("2026-10-19 12:03:21")), LegacyLogText.parse("程序曾被結束（2026-10-19 12:03:21）"))
        assertEquals(msg(LogKey.EXIT_PACKAGE_UPDATED), LegacyLogText.parse("App 更新"))
        assertEquals(msg(LogKey.CLEAN_SWIPE_UP), LegacyLogText.parse("從多工畫面滑掉"))
        assertEquals(
            msg(LogKey.EXIT_FORCE_STOPPED_BY, msg(LogKey.PACKAGE_SECURITY_CENTER)),
            LegacyLogText.parse("被手機管家強制停止"),
        )
        assertEquals(
            msg(LogKey.SINCE_LAST_EXIT, msg(LogKey.START_PACKAGE_REPLACED), msg(LogKey.DURATION_SECONDS, raw("0"))),
            LegacyLogText.parse("App 更新後重新啟動・距離上次被結束 0 秒"),
        )
        assertEquals(
            msg(LogKey.SINCE_LAST_EXIT, msg(LogKey.START_LAUNCHER), msg(LogKey.DURATION_HOURS_MINUTES, raw("2"), raw("5"))),
            LegacyLogText.parse("從桌面開啟・距離上次被結束 2 小時 5 分"),
        )
    }

    @Test
    fun `原因加系統說明：說明認得時換成說明，否則保留原文`() {
        assertEquals(msg(LogKey.CLEAN_SWIPE_UP), LegacyLogText.parse("系統其他原因：SwipeUpClean"))
        assertEquals(
            msg(LogKey.EXIT_WITH_DESCRIPTION, msg(LogKey.EXIT_SIGNALED), raw("something new")),
            LegacyLogText.parse("被訊號終止（常見於系統或省電機制殺掉程序）：something new"),
        )
    }

    @Test
    fun `認不出來的句子保留原文`() {
        assertEquals(raw("第 0 階段的舊訊息"), LegacyLogText.parse("第 0 階段的舊訊息"))
        assertEquals(raw("說明：沒有對應的原因"), LegacyLogText.parse("說明：沒有對應的原因"))
    }
}
