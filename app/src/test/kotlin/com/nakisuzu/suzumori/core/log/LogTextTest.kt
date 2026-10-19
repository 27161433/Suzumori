package com.nakisuzu.suzumori.core.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogTextTest {
    private fun roundTrip(text: LogText) = assertEquals(text, LogTextCodec.decode(LogTextCodec.encode(text)))

    @Test
    fun `巢狀訊息與特殊字元都能還原`() {
        roundTrip(msg(LogKey.SUCCESS))
        roundTrip(raw(""))
        roundTrip(raw("引號 \" 反斜線 \\ 括號 (a,b) 逗號,"))
        roundTrip(
            msg(
                LogKey.RETRY,
                msg(LogKey.APPEND, list(listOf(raw("GMS"), msg(LogKey.SELF_APP)))),
                raw(2),
            ),
        )
        roundTrip(clauses(listOf(msg(LogKey.SENT, raw("GCM_RECONNECT → com.google.android.gms")))))
    }

    @Test
    fun `編碼是英文代碼`() {
        val text = msg(LogKey.APPEND, list(listOf(raw("GMS"), msg(LogKey.SELF_APP))))
        assertEquals("APPEND(LIST(\"GMS\",SELF_APP))", LogTextCodec.encode(text))
    }

    @Test
    fun `舊版的句子與不完整的編碼無法解析`() {
        assertNull(LogTextCodec.decode("追加 GMS"))
        assertNull(LogTextCodec.decode("成功"))
        assertNull(LogTextCodec.decode("APPEND("))
        assertNull(LogTextCodec.decode("APPEND(\"GMS\""))
        assertNull(LogTextCodec.decode("\"沒有結束"))
        assertNull(LogTextCodec.decode("NOT_A_KEY"))
        assertNull(LogTextCodec.decode("SUCCESS 多餘"))
    }

    @Test
    fun `時間長度依長短選擇單位`() {
        assertEquals(msg(LogKey.DURATION_SECONDS, raw(3)), duration(3_000))
        assertEquals(msg(LogKey.DURATION_MINUTES, raw(12)), duration(12 * 60_000L))
        assertEquals(msg(LogKey.DURATION_HOURS_MINUTES, raw(2), raw(5)), duration((2 * 60 + 5) * 60_000L))
        assertEquals(msg(LogKey.DURATION_DAYS, raw(3)), duration(3 * 86_400_000L))
    }
}
