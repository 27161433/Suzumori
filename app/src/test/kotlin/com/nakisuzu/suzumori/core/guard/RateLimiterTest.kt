package com.nakisuzu.suzumori.core.guard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {
    private fun limiter() = RateLimiter(maxEvents = 3, windowMillis = 60_000, pauseMillis = 300_000)

    @Test
    fun `上限內都允許`() {
        val limiter = limiter()
        assertTrue(limiter.tryAcquire(0))
        assertTrue(limiter.tryAcquire(1_000))
        assertTrue(limiter.tryAcquire(2_000))
    }

    @Test
    fun `超過上限時拒絕並開始暫停`() {
        val limiter = limiter()
        repeat(3) { limiter.tryAcquire(it * 1_000L) }

        assertFalse(limiter.tryAcquire(3_000))
        assertTrue(limiter.isPaused(3_000))
        assertFalse(limiter.tryAcquire(200_000))
    }

    @Test
    fun `暫停結束後恢復`() {
        val limiter = limiter()
        repeat(4) { limiter.tryAcquire(it * 1_000L) }

        assertFalse(limiter.isPaused(3_000 + 300_000))
        assertTrue(limiter.tryAcquire(3_000 + 300_000))
    }

    @Test
    fun `超過時間窗的舊紀錄不計入`() {
        val limiter = limiter()
        repeat(3) { limiter.tryAcquire(it * 1_000L) }

        assertTrue(limiter.tryAcquire(61_000))
        assertFalse(limiter.isPaused(61_000))
    }
}
