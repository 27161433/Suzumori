package com.nakisuzu.suzumori.core.guard

/**
 * 偵測本 App 是否曾被暫停（凍結）。
 *
 * 以固定間隔呼叫 [onTick]，傳入 `SystemClock.uptimeMillis()`。uptime 在 CPU 休眠時不會前進，
 * 所以螢幕關閉、裝置休眠不會造成誤判；但本 App 被凍結時 CPU 仍然醒著，uptime 照樣前進，
 * 解凍後這次 tick 就會比預期晚很多。
 */
class PauseDetector(
    private val intervalMillis: Long,
    private val thresholdMillis: Long,
) {
    private var lastTick: Long? = null

    /** 回傳這次 tick 之前被暫停的毫秒數；沒有異常時回傳 null。 */
    fun onTick(uptimeMillis: Long): Long? {
        val last = lastTick
        lastTick = uptimeMillis
        if (last == null) return null
        val overdue = uptimeMillis - last - intervalMillis
        return overdue.takeIf { it >= thresholdMillis }
    }
}
