package com.nakisuzu.suzumori.core.guard

/**
 * 限制一段時間內的寫入次數，防止和其他程式互相覆寫時無限重寫。
 * 超過上限時進入暫停，暫停期間一律拒絕。
 */
class RateLimiter(
    private val maxEvents: Int,
    private val windowMillis: Long,
    private val pauseMillis: Long,
) {
    private val events = ArrayDeque<Long>()

    var pausedUntilMillis: Long = 0
        private set

    fun isPaused(nowMillis: Long): Boolean = nowMillis < pausedUntilMillis

    /** 取得一次寫入的額度。超過上限時開始暫停並回傳 false。 */
    fun tryAcquire(nowMillis: Long): Boolean {
        if (isPaused(nowMillis)) return false
        while (events.isNotEmpty() && nowMillis - events.first() >= windowMillis) events.removeFirst()
        if (events.size >= maxEvents) {
            pausedUntilMillis = nowMillis + pauseMillis
            events.clear()
            return false
        }
        events.addLast(nowMillis)
        return true
    }
}
