package com.nakisuzu.suzumori.core.fcm

import org.junit.Assert.assertEquals
import org.junit.Test

class FcmAppsTest {
    private val self = "com.nakisuzu.suzumori"

    private fun app(pkg: String, enabled: Boolean = true, launcher: Boolean = true) = FcmAppCandidate(pkg, enabled, launcher)

    @Test
    fun `排除 GMS、Play 商店、本 App、停用的 App 與沒有啟動器入口的 App`() {
        val result = FcmApps.filter(
            listOf(
                app("com.discord"),
                app("com.google.android.gms"),
                app("com.android.vending"),
                app(self),
                app("com.disabled", enabled = false),
                app("com.plugin", launcher = false),
                app("org.telegram.messenger"),
            ),
            self,
        )
        assertEquals(listOf("com.discord", "org.telegram.messenger"), result)
    }

    @Test
    fun `兩種查詢都找到的 App 只列一次`() {
        val result = FcmApps.filter(listOf(app("com.discord"), app("com.whatsapp"), app("com.discord")), self)
        assertEquals(listOf("com.discord", "com.whatsapp"), result)
    }

    @Test
    fun `自啟動只有明確允許或拒絕才下結論`() {
        assertEquals(PermissionState.ALLOWED, FcmApps.autoStart(0))
        assertEquals(PermissionState.DENIED, FcmApps.autoStart(1))
        assertEquals(PermissionState.DENIED, FcmApps.autoStart(2))
    }

    @Test
    fun `自啟動讀不到或 mode 不明確時是無法判斷，不是關閉`() {
        assertEquals(PermissionState.UNKNOWN, FcmApps.autoStart(null))
        assertEquals(PermissionState.UNKNOWN, FcmApps.autoStart(3))
        assertEquals(PermissionState.UNKNOWN, FcmApps.autoStart(4))
        assertEquals(PermissionState.UNKNOWN, FcmApps.autoStart(-1))
    }

    private data class Entry(
        override val label: String,
        override val packageName: String,
        override val autoStart: PermissionState = PermissionState.UNKNOWN,
        override val noRestrictions: PermissionState = PermissionState.UNKNOWN,
    ) : FcmAppEntry

    private val apps = listOf(
        Entry("Telegram", "org.telegram.messenger", PermissionState.DENIED, PermissionState.ALLOWED),
        Entry("Discord", "com.discord", PermissionState.ALLOWED, PermissionState.ALLOWED),
        Entry("LINE", "jp.naver.line.android", PermissionState.UNKNOWN, PermissionState.DENIED),
        Entry("Gmail", "com.google.android.gm", PermissionState.ALLOWED, PermissionState.DENIED),
    )

    private fun arrange(query: String = "", sort: FcmAppSort = FcmAppSort.NAME) =
        FcmApps.arrange(apps, query, sort, String.CASE_INSENSITIVE_ORDER).map { it.label }

    @Test
    fun `依名稱排序`() {
        assertEquals(listOf("Discord", "Gmail", "LINE", "Telegram"), arrange())
    }

    @Test
    fun `依自啟動排序：已開啟、未開啟、無法判斷，同組依名稱`() {
        assertEquals(listOf("Discord", "Gmail", "Telegram", "LINE"), arrange(sort = FcmAppSort.AUTO_START))
    }

    @Test
    fun `依省電策略排序：無限制在前，同組依名稱`() {
        assertEquals(listOf("Discord", "Telegram", "Gmail", "LINE"), arrange(sort = FcmAppSort.NO_RESTRICTIONS))
    }

    @Test
    fun `搜尋比對名稱與套件名稱，不分大小寫並忽略前後空白`() {
        assertEquals(listOf("Discord"), arrange("disc"))
        assertEquals(listOf("LINE"), arrange(" naver "))
        assertEquals(listOf("Gmail"), arrange("GOOGLE"))
        assertEquals(emptyList<String>(), arrange("whatsapp"))
        assertEquals(4, arrange("   ").size)
    }

    @Test
    fun `搜尋後仍依指定方式排序`() {
        // 「e」符合 Telegram、LINE（名稱）與 Gmail（套件名稱的 google）。
        assertEquals(listOf("Gmail", "LINE", "Telegram"), arrange("e"))
        assertEquals(listOf("Telegram", "Gmail", "LINE"), arrange("e", FcmAppSort.NO_RESTRICTIONS))
    }

    @Test
    fun `省電策略讀不到時是無法判斷`() {
        assertEquals(PermissionState.ALLOWED, FcmApps.noRestrictions(true))
        assertEquals(PermissionState.DENIED, FcmApps.noRestrictions(false))
        assertEquals(PermissionState.UNKNOWN, FcmApps.noRestrictions(null))
    }
}
