package com.nakisuzu.suzumori.core.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExitReasonsTest {
    @Test
    fun `實機記錄到的說明都能辨識`() {
        assertEquals(ExitDescription.Clean(MiuiClean.SWIPE_UP), ExitReasons.parse("SwipeUpClean"))
        assertEquals(ExitDescription.PackageUpdate, ExitReasons.parse("stop com.nakisuzu.suzumori due to killDueToPackageUpdate"))
        assertEquals(
            ExitDescription.ForceStopped("com.miui.securitycenter"),
            ExitReasons.parse("stop com.nakisuzu.suzumori due to from process:com.miui.securitycenter"),
        )
    }

    @Test
    fun `小米的清理原因不分大小寫`() {
        assertEquals(ExitDescription.Clean(MiuiClean.AUTO_THERMAL), ExitReasons.parse("autothermalkill"))
        assertEquals(ExitDescription.Clean(MiuiClean.SWIPE_UP), ExitReasons.parse("remove task"))
    }

    @Test
    fun `不認得的說明回傳 null`() {
        assertNull(ExitReasons.parse(null))
        assertNull(ExitReasons.parse(""))
        assertNull(ExitReasons.parse("something new"))
    }

    @Test
    fun `原因加系統說明的結果，只辨識冒號後面的說明`() {
        assertEquals(ExitDescription.Clean(MiuiClean.SWIPE_UP), ExitReasons.parseResult("系統其他原因：SwipeUpClean"))
        assertEquals(ExitDescription.Clean(MiuiClean.ONE_KEY), ExitReasons.parseResult("Other system reason: OneKeyClean"))
        assertNull(ExitReasons.parseResult("系統其他原因：something new"))
        assertNull(ExitReasons.parseResult("記憶體不足"))
    }
}
