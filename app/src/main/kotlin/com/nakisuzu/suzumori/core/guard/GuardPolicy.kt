package com.nakisuzu.suzumori.core.guard

/** 重試、限流與等待時間的參數。 */
data class GuardPolicy(
    /** 驗證失敗時最多重試幾次（不含第一次）。 */
    val maxRetries: Int = 3,
    /** 第 n 次重試前等待的時間；次數超過清單長度時沿用最後一個值。 */
    val retryBackoffMillis: List<Long> = listOf(500, 1_000, 2_000),
    /** 每分鐘最多寫入幾次。 */
    val maxWritesPerMinute: Int = 10,
    /** 超過上限後暫停多久。 */
    val pauseMillis: Long = 5 * 60_000L,
    /** 送出解凍後，等多久再送重連。 */
    val reconnectDelayMillis: Long = 1_500,
) {
    fun backoffBeforeRetry(retry: Int): Long = retryBackoffMillis[(retry - 1).coerceIn(0, retryBackoffMillis.lastIndex)]
}
