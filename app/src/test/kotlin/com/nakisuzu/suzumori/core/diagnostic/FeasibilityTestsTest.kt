package com.nakisuzu.suzumori.core.diagnostic

import com.nakisuzu.suzumori.core.baseline.GameAllowlistBaseline
import com.nakisuzu.suzumori.core.settings.SettingKeys.GAME_ALLOWLIST
import com.nakisuzu.suzumori.core.settings.SettingKeys.MILLET_NO_RESTRICT_APP
import com.nakisuzu.suzumori.fakes.FakeBaselineStore
import com.nakisuzu.suzumori.fakes.FakeSettingsStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeasibilityTestsTest {
    private val gms = "com.google.android.gms"
    private val self = "com.nakisuzu.suzumori"

    private fun subject(settings: FakeSettingsStore, baselines: FakeBaselineStore = FakeBaselineStore()) =
        FeasibilityTests(settings, baselines, self, clock = { 1000L })

    @Test
    fun `MILLET 沒有 GMS 時追加並讀回驗證`() = runTest {
        val settings = FakeSettingsStore(mapOf(MILLET_NO_RESTRICT_APP to "com.a, com.b"))
        val report = subject(settings).testMilletWrite()

        assertEquals(WriteAction.APPEND_GMS, report.action)
        assertEquals("com.a, com.b,$gms", settings.values[MILLET_NO_RESTRICT_APP])
        assertTrue(report.readBackMatches)
        assertNull(report.error)
    }

    @Test
    fun `MILLET 已有 GMS 時寫回相同的值`() = runTest {
        val raw = "com.a, $gms "
        val settings = FakeSettingsStore(mapOf(MILLET_NO_RESTRICT_APP to raw))
        val report = subject(settings).testMilletWrite()

        assertEquals(WriteAction.REWRITE_SAME, report.action)
        assertEquals(listOf(MILLET_NO_RESTRICT_APP to raw), settings.writes)
        assertTrue(report.readBackMatches)
    }

    @Test
    fun `寫入後被其他程式立刻覆寫時，讀回驗證不通過`() = runTest {
        val settings = FakeSettingsStore(mapOf(MILLET_NO_RESTRICT_APP to "com.a"))
        settings.afterWrite = { key, _ -> settings.externalWrite(key, "com.a") }
        val report = subject(settings).testMilletWrite()

        assertFalse(report.readBackMatches)
        assertEquals("com.a", report.readBack)
    }

    @Test
    fun `寫入拋出例外時回報例外內容，不會讓呼叫端崩潰`() = runTest {
        val settings = FakeSettingsStore()
        settings.failure = IllegalArgumentException("You cannot change private secure settings.")
        val report = subject(settings).testMilletWrite()

        assertEquals(
            "java.lang.IllegalArgumentException: You cannot change private secure settings.",
            report.error,
        )
        assertNull(report.putResult)
        assertFalse(report.readBackMatches)
    }

    @Test
    fun `遊戲白名單沒有 GMS 時先記錄基準再追加`() = runTest {
        val settings = FakeSettingsStore(mapOf(GAME_ALLOWLIST to "com.game"))
        val baselines = FakeBaselineStore()
        var baselineAtWrite: GameAllowlistBaseline? = null
        settings.afterWrite = { _, _ -> baselineAtWrite = baselines.baseline }

        val report = subject(settings, baselines).testGameAllowlistWrite()

        assertEquals(WriteAction.APPEND_GMS, report.action)
        assertEquals("com.game_$gms", settings.values[GAME_ALLOWLIST])
        assertEquals(GameAllowlistBaseline(gmsWasPresent = false, selfWasPresent = false, recordedAtMillis = 1000L), baselineAtWrite)
        assertTrue(report.readBackMatches)
    }

    @Test
    fun `遊戲白名單 key 不存在時也能追加`() = runTest {
        val settings = FakeSettingsStore()
        subject(settings).testGameAllowlistWrite()
        assertEquals(gms, settings.values[GAME_ALLOWLIST])
    }

    @Test
    fun `遊戲白名單已有 GMS 時做結尾底線切換，成員不變`() = runTest {
        val settings = FakeSettingsStore(mapOf(GAME_ALLOWLIST to "com.game_$gms"))
        val baselines = FakeBaselineStore()
        val tests = subject(settings, baselines)

        val first = tests.testGameAllowlistWrite()
        assertEquals(WriteAction.TOGGLE_TRAILING_SEPARATOR, first.action)
        assertEquals("com.game_${gms}_", settings.values[GAME_ALLOWLIST])

        tests.testGameAllowlistWrite()
        assertEquals("com.game_$gms", settings.values[GAME_ALLOWLIST])
        assertTrue(baselines.baseline!!.gmsWasPresent)
    }

    @Test
    fun `寫入前重新讀取，保留兩次讀取之間其他程式加入的項目`() = runTest {
        val settings = FakeSettingsStore(mapOf(GAME_ALLOWLIST to "com.game"))
        var reads = 0
        settings.beforeRead = { key ->
            if (key == GAME_ALLOWLIST && ++reads == 2) settings.externalWrite(key, "com.game_com.joyose.task")
        }
        subject(settings).testGameAllowlistWrite()
        assertEquals("com.game_com.joyose.task_$gms", settings.values[GAME_ALLOWLIST])
    }

    @Test
    fun `基準只在第一次寫入前記錄，之後不覆蓋`() = runTest {
        val settings = FakeSettingsStore()
        val baselines = FakeBaselineStore()
        val tests = subject(settings, baselines)
        tests.testGameAllowlistWrite()
        tests.testGameAllowlistWrite()
        assertFalse(baselines.baseline!!.gmsWasPresent)
    }

    @Test
    fun `清理只移除本 App 加入的項目，保留其他人的項目`() = runTest {
        val settings = FakeSettingsStore(mapOf(GAME_ALLOWLIST to "com.game"))
        val baselines = FakeBaselineStore()
        val tests = subject(settings, baselines)
        tests.testGameAllowlistWrite()
        settings.externalWrite(GAME_ALLOWLIST, "com.game_${gms}_com.joyose.task")

        val report = tests.cleanupTestWrites()

        assertEquals(WriteAction.REMOVE_OWN_ENTRIES, report.action)
        assertEquals("com.game_com.joyose.task", settings.values[GAME_ALLOWLIST])
        assertTrue(report.readBackMatches)
        assertNull(baselines.baseline)
    }

    @Test
    fun `GMS 原本就在名單中時，清理不移除它`() = runTest {
        val settings = FakeSettingsStore(mapOf(GAME_ALLOWLIST to "${gms}_com.game"))
        val baselines = FakeBaselineStore()
        val tests = subject(settings, baselines)
        tests.testGameAllowlistWrite()
        val writesBeforeCleanup = settings.writes.size

        val report = tests.cleanupTestWrites()

        assertEquals(WriteAction.NOTHING_TO_DO, report.action)
        assertEquals(writesBeforeCleanup, settings.writes.size)
        assertTrue(settings.values[GAME_ALLOWLIST]!!.startsWith("${gms}_com.game"))
    }

    @Test
    fun `清理後沒有剩下項目時寫入空字串，key 仍然存在`() = runTest {
        val settings = FakeSettingsStore()
        val tests = subject(settings)
        tests.testGameAllowlistWrite()

        val report = tests.cleanupTestWrites()

        assertEquals("", report.written)
        assertTrue(GAME_ALLOWLIST in settings.values)
        assertEquals("", settings.values[GAME_ALLOWLIST])
    }

    @Test
    fun `沒有基準紀錄時清理什麼都不寫`() = runTest {
        val settings = FakeSettingsStore(mapOf(GAME_ALLOWLIST to "${gms}_com.game"))
        val report = subject(settings).cleanupTestWrites()

        assertEquals(WriteAction.NOTHING_TO_DO, report.action)
        assertTrue(settings.writes.isEmpty())
        assertNotNull(report.note)
    }

    @Test
    fun `清理寫入失敗時保留基準，之後可以重試`() = runTest {
        val settings = FakeSettingsStore()
        val baselines = FakeBaselineStore()
        val tests = subject(settings, baselines)
        tests.testGameAllowlistWrite()
        settings.failure = SecurityException("denied")

        val report = tests.cleanupTestWrites()

        assertNotNull(report.error)
        assertNotNull(baselines.baseline)
    }

    @Test
    fun `清理不會動到 MILLET`() = runTest {
        val settings = FakeSettingsStore(mapOf(MILLET_NO_RESTRICT_APP to "com.a"))
        val tests = subject(settings)
        tests.testMilletWrite()
        tests.testGameAllowlistWrite()
        tests.cleanupTestWrites()
        assertEquals("com.a,$gms", settings.values[MILLET_NO_RESTRICT_APP])
    }
}
