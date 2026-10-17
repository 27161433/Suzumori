package com.nakisuzu.suzumori.core.list

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameAllowlistTest {
    private val gms = "com.google.android.gms"
    private val self = "com.nakisuzu.suzumori"

    /** 系統端實際使用的 Java `String.split("_")`，當作比對基準。 */
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private fun systemSplit(value: String): List<String> = (value as java.lang.String).split("_").toList()

    private val corpus = listOf(
        "", "_", "__", "___", "a", "a_", "a__", "_a", "__a", "_a_", "a_b", "a__b", "a_b__",
        " a _b", "a_ ", "_ _", "___a___b___", "$gms", "${gms}_", "_$gms", "com.a_${gms}__com.b_",
    )

    @Test
    fun `javaSplit 與 Java String split 的結果完全相同`() {
        for (value in corpus) {
            assertEquals("輸入：\"$value\"", systemSplit(value), javaSplit(value, '_'))
        }
    }

    @Test
    fun `Kotlin split 會保留結尾空項目，與系統不同`() {
        // 這個測試說明為什麼不能直接用 Kotlin 的 split。
        assertEquals(listOf("a", ""), "a_".split('_'))
        assertEquals(listOf("a"), systemSplit("a_"))
    }

    @Test
    fun `null 與空字串沒有套件成員`() {
        assertEquals(emptyList<String>(), GameAllowlist.entries(null))
        assertFalse(GameAllowlist.contains(null, gms))
        assertFalse(GameAllowlist.contains("", gms))
        assertEquals(gms, GameAllowlist.add(null, gms))
        assertEquals(gms, GameAllowlist.add("", gms))
    }

    @Test
    fun `不去除空白，前後有空白的項目不算成員`() {
        assertFalse(GameAllowlist.contains(" $gms", gms))
        assertFalse(GameAllowlist.contains("com.a_$gms ", gms))
        assertTrue(GameAllowlist.contains("com.a_$gms", gms))
        assertEquals(" ${gms}_$gms", GameAllowlist.add(" $gms", gms))
    }

    @Test
    fun `重複項目都算成員，移除時全部移除`() {
        assertTrue(GameAllowlist.contains("${gms}_$gms", gms))
        assertEquals("com.a_${gms}_$gms", GameAllowlist.add("com.a_${gms}_$gms", gms))
        assertEquals("com.a_com.b", GameAllowlist.remove("com.a_${gms}_com.b_$gms", setOf(gms)))
    }

    @Test
    fun `結尾有分隔符號時成員不變，追加不會多出空項目`() {
        assertEquals(listOf("com.a", gms), GameAllowlist.entries("com.a_${gms}__"))
        assertTrue(GameAllowlist.contains("${gms}_", gms))
        assertEquals("com.a_$gms", GameAllowlist.add("com.a_", gms))
        assertEquals(listOf("com.a", gms), GameAllowlist.entries(GameAllowlist.add("com.a__", gms)).filter { it.isNotEmpty() })
    }

    @Test
    fun `底線切換會改變字串但成員不變`() {
        for (value in listOf(gms, "com.a_$gms", "com.a_${gms}_", "_${gms}__", " x_$gms")) {
            val toggled = GameAllowlist.toggleTrailingSeparator(value)
            assertNotEquals(value, toggled)
            assertEquals(GameAllowlist.entries(value), GameAllowlist.entries(toggled))
        }
        assertEquals("com.a_${gms}_", GameAllowlist.toggleTrailingSeparator("com.a_$gms"))
        assertEquals("com.a_$gms", GameAllowlist.toggleTrailingSeparator("com.a_${gms}_"))
    }

    @Test
    fun `連續切換兩次回到原值`() {
        for (value in listOf(gms, "com.a_$gms", "com.a_${gms}_")) {
            val twice = GameAllowlist.toggleTrailingSeparator(GameAllowlist.toggleTrailingSeparator(value))
            assertEquals(value, twice)
        }
    }

    @Test
    fun `移除項目時保留其他人的項目與順序`() {
        val raw = "com.joyose.a_${gms}_com.security.b_${self}_com.c"
        assertEquals("com.joyose.a_com.security.b_com.c", GameAllowlist.remove(raw, setOf(gms, self)))
        assertEquals("com.joyose.a_com.security.b_${self}_com.c", GameAllowlist.remove(raw, setOf(gms)))
    }

    @Test
    fun `移除時其他人的空白與空項目原樣保留`() {
        assertEquals("_ com.a", GameAllowlist.remove("_ com.a_$gms", setOf(gms)))
        assertEquals("com.a__com.b", GameAllowlist.remove("com.a__${gms}_com.b", setOf(gms)))
    }

    @Test
    fun `全部移除時得到空字串而不是 null`() {
        val result: String = GameAllowlist.remove("${gms}_${self}_", setOf(gms, self))
        assertEquals("", result)
        assertEquals("", GameAllowlist.remove(null, setOf(gms)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `含有底線的套件名稱拒絕加入`() {
        GameAllowlist.add("com.a", "com.bad_name")
    }
}
