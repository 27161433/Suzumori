package com.nakisuzu.suzumori.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    private fun v(text: String) = AppVersion.parse(text)!!

    @Test
    fun `標籤與 versionName 都能解析`() {
        assertEquals(AppVersion(listOf(1, 0, 1)), v("v1.0.1"))
        assertEquals(AppVersion(listOf(1, 0, 1)), v("1.0.1"))
        assertEquals(AppVersion(listOf(2, 3)), v(" V2.3 "))
        assertEquals(AppVersion(listOf(1, 2, 0)), v("1.2.0-beta+5"))
    }

    @Test
    fun `無法解析的版本回傳 null`() {
        assertNull(AppVersion.parse(null))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("v"))
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse("1..2"))
        assertNull(AppVersion.parse("1.x"))
    }

    @Test
    fun `以數字比較，不是字串比較`() {
        assertTrue(v("1.0.10") > v("1.0.9"))
        assertTrue(v("1.1.0") > v("1.0.99"))
        assertTrue(v("2.0") > v("1.9.9"))
        assertTrue(v("1.0.1") > v("1.0.0"))
    }

    @Test
    fun `少的位數視為 0`() {
        assertEquals(v("1.1"), v("1.1.0"))
        assertEquals(v("1.1").hashCode(), v("1.1.0").hashCode())
        assertEquals(0, v("1.0").compareTo(v("1.0.0.0")))
        assertEquals("1.1.0", v("v1.1.0").toString())
    }
}
