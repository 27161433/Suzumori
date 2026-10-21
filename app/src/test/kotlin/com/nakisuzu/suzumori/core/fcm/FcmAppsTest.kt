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

    @Test
    fun `省電策略讀不到時是無法判斷`() {
        assertEquals(PermissionState.ALLOWED, FcmApps.noRestrictions(true))
        assertEquals(PermissionState.DENIED, FcmApps.noRestrictions(false))
        assertEquals(PermissionState.UNKNOWN, FcmApps.noRestrictions(null))
    }
}
