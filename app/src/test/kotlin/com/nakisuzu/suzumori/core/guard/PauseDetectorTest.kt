package com.nakisuzu.suzumori.core.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PauseDetectorTest {
    private fun detector() = PauseDetector(intervalMillis = 30_000, thresholdMillis = 10_000)

    @Test
    fun `第一次 tick 只記錄時間`() {
        assertNull(detector().onTick(1_000))
    }

    @Test
    fun `準時或稍微延遲都不算被暫停`() {
        val d = detector()
        d.onTick(0)
        assertNull(d.onTick(30_000))
        assertNull(d.onTick(60_000 + 9_999))
    }

    @Test
    fun `延遲超過門檻時回傳被暫停的時間`() {
        val d = detector()
        d.onTick(0)
        assertEquals(90_000L, d.onTick(120_000))
    }

    @Test
    fun `每次都以上一次 tick 為基準`() {
        val d = detector()
        d.onTick(0)
        d.onTick(120_000)
        assertNull(d.onTick(150_000))
    }
}
