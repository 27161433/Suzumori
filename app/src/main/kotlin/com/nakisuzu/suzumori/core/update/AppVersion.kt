package com.nakisuzu.suzumori.core.update

/**
 * 以數字比較的版本號，例如 `1.0.1`。GitHub Release 的標籤寫成 `v1.0.1`，App 的 versionName 寫成 `1.0.1`。
 *
 * 少的位數視為 0（`1.1` 等於 `1.1.0`）。`-` 或 `+` 之後的部分不參與比較。
 */
data class AppVersion(val parts: List<Int>) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        for (i in 0 until maxOf(parts.size, other.parts.size)) {
            val diff = parts.getOrElse(i) { 0 }.compareTo(other.parts.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    override fun equals(other: Any?): Boolean = other is AppVersion && compareTo(other) == 0

    override fun hashCode(): Int = parts.dropLastWhile { it == 0 }.hashCode()

    override fun toString(): String = parts.joinToString(".")

    companion object {
        /** 無法解析時回傳 null。 */
        fun parse(text: String?): AppVersion? {
            val core = text?.trim()?.removePrefix("v")?.removePrefix("V")?.substringBefore('-')?.substringBefore('+') ?: return null
            if (core.isEmpty()) return null
            val parts = core.split('.').map { it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return null }
            return AppVersion(parts)
        }
    }
}

/**
 * GitHub 上最新的 Release。
 *
 * @property apkUrl 附件中 APK 的下載網址；沒有 APK 附件時為 null，改開 [pageUrl]。
 * @property apkSha256 GitHub 為附件算出的 SHA-256；舊的 Release 可能沒有。
 */
data class ReleaseInfo(
    val version: AppVersion,
    val tag: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
    val apkSha256: String?,
    val publishedAtMillis: Long?,
)
