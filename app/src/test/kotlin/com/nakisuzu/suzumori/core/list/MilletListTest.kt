package com.nakisuzu.suzumori.core.list

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MilletListTest {
    private val gms = "com.google.android.gms"

    @Test
    fun `null 與空字串沒有任何項目`() {
        assertEquals(emptyList<String>(), MilletList.entries(null))
        assertEquals(emptyList<String>(), MilletList.entries(""))
        assertEquals(emptyList<String>(), MilletList.entries(" , ,"))
        assertFalse(MilletList.contains(null, gms))
        assertFalse(MilletList.contains("", gms))
    }

    @Test
    fun `null 與空字串追加後只有新項目`() {
        assertEquals(gms, MilletList.add(null, gms))
        assertEquals(gms, MilletList.add("", gms))
        assertEquals(gms, MilletList.add("   ", gms))
    }

    @Test
    fun `項目前後的空白會被去除後比對`() {
        assertTrue(MilletList.contains("com.a, $gms ,com.b", gms))
        assertTrue(MilletList.contains("\t$gms\n", gms))
        assertEquals(listOf("com.a", gms, "com.b"), MilletList.entries(" com.a ,$gms,  com.b"))
    }

    @Test
    fun `已在名單內時原樣回傳`() {
        val raw = " com.a , $gms ,com.b,"
        assertEquals(raw, MilletList.add(raw, gms))
    }

    @Test
    fun `只做精確比對，不比對前綴或子字串`() {
        assertFalse(MilletList.contains("$gms.persistent,com.google.android", gms))
        assertFalse(MilletList.contains("x$gms", gms))
    }

    @Test
    fun `重複項目保留，不會被去重`() {
        assertEquals(listOf("com.a", "com.a", "com.b"), MilletList.entries("com.a,com.a,com.b"))
        assertEquals("com.a,com.a,com.b,$gms", MilletList.add("com.a,com.a,com.b", gms))
        assertEquals("$gms,$gms", MilletList.add("$gms,$gms", gms))
    }

    @Test
    fun `結尾有分隔符號時不會多出空項目`() {
        assertEquals(listOf("com.a", "com.b"), MilletList.entries("com.a,com.b,"))
        assertEquals("com.a,com.b,$gms", MilletList.add("com.a,com.b,", gms))
        assertEquals("com.a,com.b,$gms", MilletList.add("com.a,com.b, ", gms))
        assertEquals("com.a,$gms", MilletList.add("com.a ", gms))
    }

    @Test
    fun `追加時既有項目一字不改`() {
        val raw = " com.a ,,com.b"
        val added = MilletList.add(raw, gms)
        assertTrue(added.startsWith(raw))
        assertEquals(MilletList.entries(raw) + gms, MilletList.entries(added))
    }
}
