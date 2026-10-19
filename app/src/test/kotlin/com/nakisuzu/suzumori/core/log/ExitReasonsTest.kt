package com.nakisuzu.suzumori.core.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExitReasonsTest {
    @Test
    fun `實機記錄到的說明都能轉成中文`() {
        assertEquals("從多工畫面滑掉", ExitReasons.describe("SwipeUpClean"))
        assertEquals("App 更新", ExitReasons.describe("stop com.nakisuzu.suzumori due to killDueToPackageUpdate"))
        assertEquals(
            "被手機管家強制停止",
            ExitReasons.describe("stop com.nakisuzu.suzumori due to from process:com.miui.securitycenter"),
        )
    }

    @Test
    fun `不認得的說明回傳 null`() {
        assertNull(ExitReasons.describe(null))
        assertNull(ExitReasons.describe(""))
        assertNull(ExitReasons.describe("something new"))
    }

    @Test
    fun `其他程式強制停止時顯示套件名稱`() {
        assertEquals("被com.example.cleaner強制停止", ExitReasons.describe("stop x due to from process:com.example.cleaner"))
    }

    @Test
    fun `舊紀錄的結果只換掉已知的說明`() {
        assertEquals("從多工畫面滑掉", ExitReasons.localizeResult("系統其他原因：SwipeUpClean"))
        assertEquals("系統其他原因：something new", ExitReasons.localizeResult("系統其他原因：something new"))
        assertEquals("記憶體不足", ExitReasons.localizeResult("記憶體不足"))
    }
}
