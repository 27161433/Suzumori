package com.nakisuzu.suzumori.core.fcm

import com.nakisuzu.suzumori.core.Packages

/**
 * 「FCM App 檢查」的判斷規則：哪些 App 要列出來、系統回報的狀態代表什麼，以及搜尋與排序。
 *
 * 只做判斷，不讀取也不修改任何設定；讀取在 `platform/FcmAppScanner`。
 */
object FcmApps {
    /**
     * 依 [query] 篩選並依 [sort] 排序。
     *
     * 搜尋不分大小寫，比對 App 名稱與套件名稱，前後空白會忽略；空字串時列出全部。
     * 依自啟動或省電策略排序時，「已開啟／無限制」在前、「未開啟」其次、「無法判斷」最後，同一組內依名稱。
     */
    fun <T : FcmAppEntry> arrange(apps: List<T>, query: String, sort: FcmAppSort, labelOrder: Comparator<in String>): List<T> {
        val keyword = query.trim()
        val matched = if (keyword.isEmpty()) {
            apps
        } else {
            apps.filter { it.label.contains(keyword, ignoreCase = true) || it.packageName.contains(keyword, ignoreCase = true) }
        }
        val byName = compareBy(labelOrder) { app: T -> app.label }
        val order = when (sort) {
            FcmAppSort.NAME -> byName
            FcmAppSort.AUTO_START -> compareBy<T> { rank(it.autoStart) }.then(byName)
            FcmAppSort.NO_RESTRICTIONS -> compareBy<T> { rank(it.noRestrictions) }.then(byName)
        }
        return matched.sortedWith(order)
    }

    private fun rank(state: PermissionState) = when (state) {
        PermissionState.ALLOWED -> 0
        PermissionState.DENIED -> 1
        PermissionState.UNKNOWN -> 2
    }

    /** Play 商店：本身就靠 GMS 運作，不需要使用者調整。 */
    const val PLAY_STORE = "com.android.vending"

    /** 掃描到的 App 中，要列出來的套件名稱，已去除重複並保持原本的順序。 */
    fun filter(candidates: Iterable<FcmAppCandidate>, selfPackage: String): List<String> =
        candidates
            .filter { it.enabled && it.hasLauncher && it.packageName !in setOf(Packages.GMS, PLAY_STORE, selfPackage) }
            .map { it.packageName }
            .distinct()

    /**
     * 小米「自啟動」的 AppOps mode（代碼 10008）對應的狀態。
     *
     * 只有明確的允許（`MODE_ALLOWED`）與拒絕（`MODE_IGNORED`、`MODE_ERRORED`）才下結論；
     * 讀不到（null）、`MODE_DEFAULT` 或其他 mode 一律是 [PermissionState.UNKNOWN]，絕不當成關閉。
     */
    fun autoStart(mode: Int?): PermissionState = when (mode) {
        MODE_ALLOWED -> PermissionState.ALLOWED
        MODE_IGNORED, MODE_ERRORED -> PermissionState.DENIED
        else -> PermissionState.UNKNOWN
    }

    /** 省電策略是否為「無限制」（`isIgnoringBatteryOptimizations`）；讀不到時為 null。 */
    fun noRestrictions(ignoringBatteryOptimizations: Boolean?): PermissionState = when (ignoringBatteryOptimizations) {
        true -> PermissionState.ALLOWED
        false -> PermissionState.DENIED
        null -> PermissionState.UNKNOWN
    }

    // android.app.AppOpsManager 的 mode 數值；core 不依賴 Android，直接寫數值。
    private const val MODE_ALLOWED = 0
    private const val MODE_IGNORED = 1
    private const val MODE_ERRORED = 2
}

/** 系統查到的、可能使用 FCM 的 App。 */
data class FcmAppCandidate(
    val packageName: String,
    /** App 沒有被停用。 */
    val enabled: Boolean,
    /** 有桌面啟動器入口；沒有入口的多半是系統元件或外掛，使用者也無從調整。 */
    val hasLauncher: Boolean,
)

/** 檢查頁上的一個 App：搜尋與排序需要的欄位。 */
interface FcmAppEntry {
    val packageName: String
    val label: String
    val autoStart: PermissionState
    val noRestrictions: PermissionState
}

enum class FcmAppSort {
    NAME,

    /** 自啟動已開啟的在前。 */
    AUTO_START,

    /** 省電策略是「無限制」的在前。 */
    NO_RESTRICTIONS,
}

enum class PermissionState {
    ALLOWED,
    DENIED,

    /** 讀不到或無法判斷。畫面上顯示「無法判斷」，不能當成關閉。 */
    UNKNOWN,
}
