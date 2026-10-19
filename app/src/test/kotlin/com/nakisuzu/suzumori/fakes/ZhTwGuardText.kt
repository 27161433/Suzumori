package com.nakisuzu.suzumori.fakes

import com.nakisuzu.suzumori.core.guard.GuardText

/** 測試用的繁體中文文字，內容與 `values-zh-rTW/strings.xml` 相同，讓斷言可以直接比對紀錄文字。 */
object ZhTwGuardText : GuardText {
    override val selfApp = "本 App"

    override fun list(items: Collection<String>) = items.joinToString("、")

    override fun clauses(items: List<String>) = items.joinToString("；")

    override val skipPatch = "略過修補"
    override val pausedByRateLimit = "修補次數超過上限，暫停中"

    override fun append(names: String) = "追加 $names"

    override fun remove(names: String) = "移除 $names"

    override val toggleSeparator = "結尾底線切換（開機後重新載入）"

    override fun milletRestored(names: String) = "MILLET 補回 $names"

    override val gameAllowlistReloaded = "遊戲白名單重新載入"

    override fun gameAllowlistRestored(names: String) = "遊戲白名單補回 $names"

    override val cleanupGameAllowlist = "清理遊戲白名單"
    override val noBaseline = "沒有基準紀錄，本 App 沒有寫入過遊戲白名單"
    override val nothingOwned = "名單中沒有本 App 加入的項目"
    override val noWritePermission = "沒有「修改系統設定」權限，無法寫入"

    override fun retry(action: String, attempt: Int) = "$action（第 $attempt 次重試）"

    override fun rateLimited(maxPerMinute: Int, pauseMinutes: Long) = "每分鐘修補超過 $maxPerMinute 次，暫停 $pauseMinutes 分鐘"

    override fun exception(error: String) = "例外：$error"

    override val success = "成功"
    override val verifyFailed = "驗證失敗"

    override fun externalChange(diff: String) = "偵測到外部變更（$diff）"

    override val membersUnchanged = "成員不變"

    override fun removedMembers(items: String) = "移除 $items"

    override fun addedMembers(items: String) = "新增 $items"

    override fun gmsInList(present: Boolean) = if (present) "GMS 在名單中" else "GMS 不在名單中"

    override val sendUnfreeze = "送出解凍廣播"
    override val sendReconnect = "送出重連廣播"
    override val unfreezeSummary = "已送出解凍與重連"

    override fun sendResult(label: String, error: String?) = if (error == null) "$label：已送出" else "$label：例外 $error"

    override fun truncated(total: Int) = "…（共 $total 字）"
}
