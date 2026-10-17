package com.nakisuzu.suzumori.core.list

/**
 * `Settings.System` 的 `MILLET_NO_RESTRICT_APP`：以逗號分隔的套件名稱。
 *
 * 系統解析時會去除每個項目前後的空白，所以成員判斷也去除空白後精確比對。
 * 這份名單由 PowerKeeper 擁有並會整串覆寫，本 App 只追加、不移除。
 */
object MilletList {
    const val SEPARATOR = ','

    /** 去除空白後的所有非空項目，保留原本的順序與重複項目。 */
    fun entries(raw: String?): List<String> =
        raw.orEmpty().split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }

    fun contains(raw: String?, pkg: String): Boolean = entries(raw).any { it == pkg }

    /**
     * 回傳追加 [pkg] 之後的值。已經在名單內時原樣回傳 [raw]。
     *
     * 既有項目一字不改地保留，只在結尾接上新項目，避免動到 PowerKeeper 的資料。
     */
    fun add(raw: String?, pkg: String): String {
        if (contains(raw, pkg)) return raw.orEmpty()
        val base = raw.orEmpty().trimEnd()
        return when {
            base.isEmpty() -> pkg
            base.endsWith(SEPARATOR) -> base + pkg
            else -> base + SEPARATOR + pkg
        }
    }
}
