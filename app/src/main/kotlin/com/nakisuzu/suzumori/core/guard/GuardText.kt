package com.nakisuzu.suzumori.core.guard

/**
 * 守護核心寫進事件紀錄與狀態摘要的文字。
 *
 * 核心不依賴 Android，文字由平台層依目前的 App 語言提供（見 `platform/AndroidGuardText`）。
 * 紀錄以寫入當下的語言保存，之後切換語言不會改變已經寫入的內容。
 */
interface GuardText {
    /** 本 App 在紀錄中的稱呼。 */
    val selfApp: String

    /** 把多個項目接成一串，例如「GMS、本 App」。 */
    fun list(items: Collection<String>): String

    /** 把多個短句接成一句，例如「移除 A；新增 B」。 */
    fun clauses(items: List<String>): String

    val skipPatch: String
    val pausedByRateLimit: String

    /** 動作：追加項目到名單。 */
    fun append(names: String): String

    /** 動作：從名單移除項目。 */
    fun remove(names: String): String

    /** 動作：開機後的結尾底線切換。 */
    val toggleSeparator: String

    fun milletRestored(names: String): String
    val gameAllowlistReloaded: String
    fun gameAllowlistRestored(names: String): String

    val cleanupGameAllowlist: String
    val noBaseline: String
    val nothingOwned: String

    val noWritePermission: String
    fun retry(action: String, attempt: Int): String
    fun rateLimited(maxPerMinute: Int, pauseMinutes: Long): String
    fun exception(error: String): String
    val success: String
    val verifyFailed: String

    fun externalChange(diff: String): String
    val membersUnchanged: String
    fun removedMembers(items: String): String
    fun addedMembers(items: String): String
    fun gmsInList(present: Boolean): String

    val sendUnfreeze: String
    val sendReconnect: String
    val unfreezeSummary: String

    /** 一則廣播的結果；[error] 為 null 表示已送出。 */
    fun sendResult(label: String, error: String?): String

    /** 截斷過長的值時接在後面，標示原本的長度。 */
    fun truncated(total: Int): String
}
