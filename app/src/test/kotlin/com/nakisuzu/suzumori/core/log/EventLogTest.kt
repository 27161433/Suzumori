package com.nakisuzu.suzumori.core.log

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EventLogTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun entry(time: Long, result: String = "成功") = EventEntry(
        timeMillis = time,
        trigger = "MILLET_CHANGED",
        key = "MILLET_NO_RESTRICT_APP",
        before = "com.a, com.b",
        after = "com.a, com.b,com.google.android.gms",
        action = "追加 GMS",
        result = result,
        level = EventLevel.SUCCESS,
    )

    @Test
    fun `編碼後解碼得到相同內容，特殊字元與 null 都能保留`() {
        val tricky = EventEntry(
            timeMillis = 1_760_000_000_000,
            trigger = "觸發\t含 Tab",
            key = null,
            before = "第一行\n第二行\r\n反斜線 \\ 結尾\\",
            after = "",
            action = "\\N 不是 null",
            result = "\\t 字面",
        )
        assertEquals(tricky, EventLogCodec.decode(EventLogCodec.encode(tricky)))
        assertEquals(entry(1), EventLogCodec.decode(EventLogCodec.encode(entry(1))))
    }

    @Test
    fun `編碼結果只有一行`() {
        val encoded = EventLogCodec.encode(entry(1).copy(before = "a\nb\nc"))
        assertTrue('\n' !in encoded)
    }

    @Test
    fun `無法解析的行回傳 null`() {
        assertNull(EventLogCodec.decode(""))
        assertNull(EventLogCodec.decode("garbage"))
        assertNull(EventLogCodec.decode("9\t1\ta\tb\tc\td\te\tf"))
        assertNull(EventLogCodec.decode("1\tnot-a-number\ta\tb\tc\td\te\tf"))
    }

    @Test
    fun `檔案紀錄只保留最近的筆數，重新開啟後內容相同`() = runTest {
        val file = folder.root.resolve("events.log")
        val io = StandardTestDispatcher(testScheduler)
        val log = FileEventLog(file, capacity = 5, slack = 2, io = io)

        repeat(12) { log.append(entry(it.toLong())) }

        assertEquals((7L..11L).toList(), log.entries.first().map { it.timeMillis })
        val reopened = FileEventLog(file, capacity = 5, slack = 2, io = io)
        assertEquals((7L..11L).toList(), reopened.entries.first().map { it.timeMillis })
        assertTrue("檔案行數應該被壓縮", file.readLines().size <= 5 + 2)
    }

    @Test
    fun `檔案中損壞的行會被略過`() = runTest {
        val file = folder.root.resolve("events.log")
        file.writeText(EventLogCodec.encode(entry(1)) + "\n半行壞掉的資料\n" + EventLogCodec.encode(entry(2)) + "\n")
        val log = FileEventLog(file, io = StandardTestDispatcher(testScheduler))

        assertEquals(listOf(1L, 2L), log.entries.first().map { it.timeMillis })
    }

    @Test
    fun `清除後檔案也是空的`() = runTest {
        val file = folder.root.resolve("events.log")
        val log = FileEventLog(file, io = StandardTestDispatcher(testScheduler))
        log.append(entry(1))

        log.clear()

        assertTrue(log.entries.first().isEmpty())
        assertTrue(file.readText().isEmpty())
    }

    @Test
    fun `截斷過長的值並標示原本長度`() {
        val suffix = { total: Int -> "…（共 $total 字）" }
        assertEquals("abc", truncateForLog("abc", suffix, limit = 5))
        assertEquals("abcde…（共 7 字）", truncateForLog("abcdefg", suffix, limit = 5))
        assertNull(truncateForLog(null, suffix))
    }

    @Test
    fun `第 1 版的紀錄仍可讀取，觸發原因對回名稱，結果的性質從文字推斷`() {
        val ok = EventLogCodec.decode("1\t5\tMILLET 名單變更\tMILLET_NO_RESTRICT_APP\t\\N\t\\N\t追加 GMS\t成功")
        assertEquals("MILLET_CHANGED", ok?.trigger)
        assertEquals(EventLevel.SUCCESS, ok?.level)

        val failed = EventLogCodec.decode("1\t6\t開機（解鎖前）\t\\N\t\\N\t\\N\t追加 GMS\t驗證失敗")
        assertEquals("LOCKED_BOOT", failed?.trigger)
        assertEquals(EventLevel.WARNING, failed?.level)

        val unknown = EventLogCodec.decode("1\t7\t未來的觸發\t\\N\t\\N\t\\N\t送出解凍廣播\tGCM_RECONNECT：已送出")
        assertEquals("未來的觸發", unknown?.trigger)
        assertEquals(EventLevel.INFO, unknown?.level)
    }

    @Test
    fun `重新寫入時改用第 2 版`() {
        val legacy = EventLogCodec.decode("1\t5\t自我監測\t\\N\t\\N\t\\N\t本 App 曾被暫停\t約 12 秒")!!
        val encoded = EventLogCodec.encode(legacy)
        assertTrue(encoded.startsWith("2\t5\tSELF_MONITOR\t"))
        assertEquals(legacy, EventLogCodec.decode(encoded))
    }
}
