package com.nakisuzu.suzumori.core.list

/**
 * `Settings.System` 的 `com.miui.game.allowlist`：以底線分隔的套件名稱。
 *
 * 系統端用 Java 的 `String.split("_")` 解析並精確比對，不去除空白，結尾的空項目會被丟棄。
 * Kotlin 的 `split` 不會丟棄結尾的空項目，所以這裡用 [javaSplit] 模擬系統端的語意。
 * 含有底線的套件名稱無法在這份名單中正確表示。
 */
object GameAllowlist {
    const val SEPARATOR = '_'

    /** 系統端看到的成員，與 Java `value.split("_")` 的結果相同。null 視為沒有成員。 */
    fun entries(raw: String?): List<String> = if (raw == null) emptyList() else javaSplit(raw, SEPARATOR)

    fun contains(raw: String?, pkg: String): Boolean = pkg in entries(raw)

    /**
     * 回傳追加 [pkg] 之後的值。已經在名單內時原樣回傳 [raw]。
     *
     * 其他程式（安全中心、Joyose）的項目一字不改地保留。
     */
    fun add(raw: String?, pkg: String): String {
        require(SEPARATOR !in pkg) { "含有底線的套件名稱無法放進遊戲白名單：$pkg" }
        if (contains(raw, pkg)) return raw.orEmpty()
        val base = raw.orEmpty()
        return when {
            base.isEmpty() -> pkg
            base.endsWith(SEPARATOR) -> base + pkg
            else -> base + SEPARATOR + pkg
        }
    }

    /**
     * 回傳移除 [packages] 之後的值，其他項目照原順序保留。
     *
     * 沒有剩下任何項目時回傳空字串。呼叫端必須寫入這個值，不能刪除 key：
     * 系統讀到 null 會直接返回而不清除快取，被移除的項目會繼續受到保護。
     */
    fun remove(raw: String?, packages: Set<String>): String =
        entries(raw).filterNot { it in packages }.joinToString(SEPARATOR.toString())

    /**
     * 在結尾加上或移除一個底線。成員不變，但字串改變，
     * 藉此觸發系統的 observer 重新載入名單（寫入相同的值不會觸發通知）。
     */
    fun toggleTrailingSeparator(raw: String?): String {
        val base = raw.orEmpty()
        return if (base.endsWith(SEPARATOR)) base.dropLast(1) else base + SEPARATOR
    }
}

/**
 * 模擬 Java `String.split(String)`（limit 為 0）在分隔符號為單一字元時的行為：
 * - 找不到分隔符號時回傳只含原字串的清單（空字串會得到 `[""]`）。
 * - 開頭的空項目保留，結尾連續的空項目全部丟棄。
 */
internal fun javaSplit(input: String, separator: Char): List<String> {
    if (separator !in input) return listOf(input)
    return input.split(separator).dropLastWhile { it.isEmpty() }
}
