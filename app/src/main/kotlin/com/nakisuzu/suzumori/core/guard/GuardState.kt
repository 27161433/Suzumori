package com.nakisuzu.suzumori.core.guard

import kotlinx.coroutines.flow.Flow

/** 一次修補或解凍的摘要，顯示在狀態頁與通知。 */
data class ActionRecord(
    val atMillis: Long,
    val trigger: Trigger,
    val summary: String,
)

/** 跨程序重啟需要保存的守護狀態。 */
data class GuardState(
    /** 最近一次完成 ensure 的開機次數，用來判斷「開機後的第一次 ensure」。 */
    val lastEnsuredBootCount: Int? = null,
    /** 最近一次完成遊戲白名單重新載入（寫入或底線切換）的開機次數。 */
    val lastReloadedBootCount: Int? = null,
    val lastPatch: ActionRecord? = null,
    val lastUnfreeze: ActionRecord? = null,
    /** 本 App 最近一次被暫停（凍結）或程序被結束。 */
    val lastSelfIssue: ActionRecord? = null,
    /** 已經記錄過的程序結束紀錄中最新的時間，避免重複記錄。 */
    val lastExitReportedAtMillis: Long? = null,
)

interface GuardStateStore {
    val state: Flow<GuardState>

    suspend fun current(): GuardState

    suspend fun update(transform: (GuardState) -> GuardState)
}

/** 目前的開機次數（`Settings.Global.BOOT_COUNT`），讀不到時為 null。 */
fun interface BootCounter {
    suspend fun current(): Int?
}
