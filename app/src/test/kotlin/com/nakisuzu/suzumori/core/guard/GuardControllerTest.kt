package com.nakisuzu.suzumori.core.guard

import com.nakisuzu.suzumori.core.baseline.GameAllowlistBaseline
import com.nakisuzu.suzumori.core.config.GuardConfig
import com.nakisuzu.suzumori.core.settings.SettingKeys
import com.nakisuzu.suzumori.fakes.FakeBaselineStore
import com.nakisuzu.suzumori.fakes.FakeBootCounter
import com.nakisuzu.suzumori.fakes.FakeConfigStore
import com.nakisuzu.suzumori.fakes.FakeEventLog
import com.nakisuzu.suzumori.fakes.FakeGmsSignals
import com.nakisuzu.suzumori.fakes.FakeGuardStateStore
import com.nakisuzu.suzumori.fakes.FakeSettingsStore
import com.nakisuzu.suzumori.fakes.ZhTwGuardText
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardControllerTest {
    private val gms = "com.google.android.gms"
    private val self = "com.nakisuzu.suzumori"
    private val millet = SettingKeys.MILLET_NO_RESTRICT_APP
    private val game = SettingKeys.GAME_ALLOWLIST

    /** 這次開機已經處理過的狀態：不會觸發開機後的第一次解凍與底線切換。 */
    private val settledBoot = GuardState(lastEnsuredBootCount = 7, lastReloadedBootCount = 7)

    private inner class Harness(
        scope: TestScope,
        initial: Map<String, String?>,
        config: GuardConfig = GuardConfig(),
        state: GuardState = settledBoot,
        bootCount: Int? = 7,
        baseline: GameAllowlistBaseline? = null,
    ) {
        val timeline = mutableListOf<String>()
        val settings = FakeSettingsStore(initial, timeline)
        val signals = FakeGmsSignals(timeline)
        val baselines = FakeBaselineStore(baseline)
        val configs = FakeConfigStore(config)
        val states = FakeGuardStateStore(state)
        val boot = FakeBootCounter(bootCount)
        val log = FakeEventLog()
        val controller = GuardController(
            settings = settings,
            signals = signals,
            baselines = baselines,
            configs = configs,
            states = states,
            bootCounter = boot,
            log = log,
            text = ZhTwGuardText,
            selfPackage = self,
            clock = { scope.testScheduler.currentTime },
        )
    }

    private val healthy = mapOf(millet to "com.a, $gms, $self", game to "${gms}_$self")

    @Test
    fun `名單都正常時只讀取，不寫入也不送廣播`() = runTest {
        val h = Harness(this, healthy)

        val result = h.controller.ensure(Trigger.MILLET_CHANGED)

        assertTrue(h.settings.writes.isEmpty())
        assertEquals(0, h.signals.unfreezeCount)
        assertEquals(0, h.signals.reconnectCount)
        assertTrue(h.timeline.all { it.startsWith("read:") })
        assertFalse(result.patched)
        assertFalse(result.unfroze)
    }

    @Test
    fun `修補流程的順序是寫入、驗證、解凍、重連`() = runTest {
        val h = Harness(this, mapOf(millet to "com.a", game to "${gms}_$self"))

        val result = h.controller.ensure(Trigger.MILLET_CHANGED)

        val write = h.timeline.indexOf("write:$millet")
        val verify = h.timeline.subList(write + 1, h.timeline.size).indexOf("read:$millet") + write + 1
        val unfreeze = h.timeline.indexOf("unfreeze")
        val reconnect = h.timeline.indexOf("reconnect")
        assertTrue("時間軸：${h.timeline}", write in 0 until verify)
        assertTrue("時間軸：${h.timeline}", verify < unfreeze)
        assertTrue("時間軸：${h.timeline}", unfreeze < reconnect)
        assertEquals("com.a,$gms,$self", h.settings.values[millet])
        assertTrue(result.patched)
        assertNotNull(h.states.value.lastPatch)
    }

    @Test
    fun `解凍後等待一段時間才送重連`() = runTest {
        val h = Harness(this, healthy)
        var unfreezeAt = -1L
        var reconnectAt = -1L
        val signals = object : com.nakisuzu.suzumori.core.signal.GmsSignals {
            override fun sendUnfreeze() = h.signals.sendUnfreeze().also { unfreezeAt = testScheduler.currentTime }
            override fun sendReconnect() = h.signals.sendReconnect().also { reconnectAt = testScheduler.currentTime }
        }
        val controller = GuardController(
            h.settings, signals, h.baselines, h.configs, h.states, h.boot, h.log, ZhTwGuardText, self,
            clock = { testScheduler.currentTime },
        )

        controller.ensureAndUnfreeze(Trigger.MANUAL_UNFREEZE)

        assertEquals(GuardPolicy().reconnectDelayMillis, reconnectAt - unfreezeAt)
    }

    @Test
    fun `寫入前再讀一次最新值，保留其他程式剛加入的項目`() = runTest {
        val h = Harness(this, mapOf(millet to "com.a", game to "${gms}_$self"))
        var milletReads = 0
        h.settings.beforeRead = { key ->
            if (key == millet && ++milletReads == 2) h.settings.externalWrite(millet, "com.a, com.powerkeeper.new")
        }

        h.controller.ensure(Trigger.MILLET_CHANGED)

        assertEquals("com.a, com.powerkeeper.new,$gms,$self", h.settings.values[millet])
    }

    @Test
    fun `本 App 也維持在 MILLET 名單中，即使使用者的「無限制」被取消`() = runTest {
        val h = Harness(this, mapOf(millet to "com.a, $gms", game to "${gms}_$self"))

        val result = h.controller.ensure(Trigger.MILLET_CHANGED)

        assertEquals("com.a, $gms,$self", h.settings.values[millet])
        assertTrue(result.patched)
        assertEquals("MILLET 補回 本 App", h.states.value.lastPatch?.summary)
    }

    @Test
    fun `自己的寫入觸發的檢查不會再寫入，不形成迴圈`() = runTest {
        val h = Harness(this, mapOf(millet to "com.a", game to null))
        h.controller.ensure(Trigger.MILLET_CHANGED)
        val writes = h.settings.writes.size
        val unfreezes = h.signals.unfreezeCount

        h.controller.ensure(Trigger.MILLET_CHANGED)
        h.controller.ensure(Trigger.GAME_ALLOWLIST_CHANGED)

        assertEquals(writes, h.settings.writes.size)
        assertEquals(unfreezes, h.signals.unfreezeCount)
    }

    @Test
    fun `第一次寫入遊戲白名單之前記錄基準`() = runTest {
        val h = Harness(this, mapOf(millet to gms, game to "com.game"))
        var baselineAtWrite: GameAllowlistBaseline? = null
        h.settings.afterWrite = { key, _ -> if (key == game) baselineAtWrite = h.baselines.baseline }

        h.controller.ensure(Trigger.GAME_ALLOWLIST_CHANGED)

        assertEquals(GameAllowlistBaseline(gmsWasPresent = false, selfWasPresent = false, recordedAtMillis = 0), baselineAtWrite)
        assertEquals("com.game_${gms}_$self", h.settings.values[game])
    }

    @Test
    fun `底線切換每次開機只執行一次`() = runTest {
        val h = Harness(this, healthy, state = GuardState(), bootCount = 7)

        h.controller.ensure(Trigger.BOOT)
        h.controller.ensure(Trigger.SERVICE_START)
        h.controller.ensure(Trigger.SCREEN_ON)

        assertEquals(listOf("${gms}_${self}_"), h.settings.writesTo(game))
        assertEquals(7, h.states.value.lastReloadedBootCount)

        h.boot.count = 8
        h.controller.ensure(Trigger.BOOT)
        h.controller.ensure(Trigger.SCREEN_ON)

        assertEquals(listOf("${gms}_${self}_", "${gms}_$self"), h.settings.writesTo(game))
        assertTrue(h.settings.writesTo(millet).isEmpty())
    }

    @Test
    fun `開機後第一次檢查即使名單正常也會解凍，之後不再解凍`() = runTest {
        val h = Harness(this, healthy, state = GuardState(lastReloadedBootCount = 7), bootCount = 7)

        val first = h.controller.ensure(Trigger.BOOT)
        h.controller.ensure(Trigger.SCREEN_ON)

        assertTrue(first.unfroze)
        assertEquals(1, h.signals.unfreezeCount)
        assertEquals(1, h.signals.reconnectCount)
        assertTrue(h.settings.writes.isEmpty())
    }

    @Test
    fun `缺項目時追加本身就會改變字串，不另外做底線切換`() = runTest {
        val h = Harness(this, mapOf(millet to gms, game to "com.game"), state = GuardState(), bootCount = 7)

        h.controller.ensure(Trigger.BOOT)

        assertEquals(listOf("com.game_${gms}_$self"), h.settings.writesTo(game))
        assertEquals(7, h.states.value.lastReloadedBootCount)
    }

    @Test
    fun `驗證失敗時以退避間隔最多重試 3 次`() = runTest {
        val h = Harness(this, mapOf(millet to "com.a", game to "${gms}_$self"))
        h.settings.afterWrite = { key, _ -> if (key == millet) h.settings.externalWrite(millet, "com.a") }

        val result = h.controller.ensure(Trigger.MILLET_CHANGED)

        assertEquals(4, h.settings.writesTo(millet).size)
        assertTrue(result.failed)
        assertFalse(result.patched)
        assertEquals(0, h.signals.unfreezeCount)
        assertTrue(testScheduler.currentTime >= 500 + 1_000 + 2_000)
    }

    @Test
    fun `每分鐘修補次數超過上限時暫停，暫停結束後恢復`() = runTest {
        val h = Harness(this, mapOf(millet to "com.a", game to "${gms}_$self"))
        val limit = GuardPolicy().maxWritesPerMinute

        repeat(limit + 3) {
            h.settings.externalWrite(millet, "com.a")
            h.controller.ensure(Trigger.MILLET_CHANGED)
        }

        assertEquals(limit, h.settings.writesTo(millet).size)
        h.settings.externalWrite(millet, "com.a")
        assertTrue(h.controller.ensure(Trigger.MILLET_CHANGED).paused)
        assertEquals(limit, h.settings.writesTo(millet).size)
        assertTrue(h.log.all.any { it.result.contains("暫停") })

        advanceTimeBy(GuardPolicy().pauseMillis)
        h.controller.ensure(Trigger.MILLET_CHANGED)

        assertEquals(limit + 1, h.settings.writesTo(millet).size)
    }

    @Test
    fun `寫入拋出例外時記錄例外並重試`() = runTest {
        val h = Harness(this, mapOf(millet to "com.a", game to "${gms}_$self"))
        h.settings.failure = IllegalArgumentException("You cannot change private secure settings.")

        val result = h.controller.ensure(Trigger.MILLET_CHANGED)

        assertTrue(result.failed)
        assertEquals(4, h.log.all.count { it.result.startsWith("例外：java.lang.IllegalArgumentException") })
    }

    @Test
    fun `沒有寫入權限時不寫入，並記錄原因`() = runTest {
        val h = Harness(this, mapOf(millet to "com.a", game to null))
        h.settings.writable = false

        val result = h.controller.ensure(Trigger.MILLET_CHANGED)

        assertTrue(h.settings.writes.isEmpty())
        assertTrue(result.failed)
        assertTrue(h.log.all.any { it.result.contains("修改系統設定") })
    }

    @Test
    fun `夜間保護關閉時不碰遊戲白名單`() = runTest {
        val h = Harness(this, mapOf(millet to "com.a", game to null), config = GuardConfig(nightProtection = false))

        h.controller.ensure(Trigger.MILLET_CHANGED)

        assertTrue(h.settings.writesTo(game).isEmpty())
        assertNull(h.baselines.baseline)
    }

    @Test
    fun `不讓本 App 加入遊戲白名單時只確保 GMS`() = runTest {
        val h = Harness(this, mapOf(millet to gms, game to null), config = GuardConfig(selfInGameAllowlist = false))

        h.controller.ensure(Trigger.GAME_ALLOWLIST_CHANGED)

        assertEquals(gms, h.settings.values[game])
    }

    @Test
    fun `其他程式改寫名單時記錄差異`() = runTest {
        val h = Harness(this, healthy)
        h.controller.ensure(Trigger.SERVICE_START)

        h.settings.externalWrite(millet, "com.a, com.revanced")
        h.controller.ensure(Trigger.MILLET_CHANGED)

        val detected = h.log.all.single { it.action.startsWith("偵測到外部變更") }
        assertEquals("偵測到外部變更（移除 $gms、$self；新增 com.revanced）", detected.action)
        assertEquals("GMS 不在名單中", detected.result)
        assertEquals("com.a, $gms, $self", detected.before)
        assertEquals("com.a, com.revanced", detected.after)
    }

    @Test
    fun `自動守護關閉時略過自動觸發，手動檢查仍會執行`() = runTest {
        val h = Harness(this, mapOf(millet to "com.a", game to "${gms}_$self"), config = GuardConfig(autoGuard = false))

        h.controller.ensure(Trigger.SCREEN_OFF)
        h.controller.ensure(Trigger.PERIODIC)
        assertTrue(h.settings.writes.isEmpty())

        h.controller.ensure(Trigger.MANUAL_CHECK)
        assertEquals(1, h.settings.writesTo(millet).size)
    }

    @Test
    fun `手動解凍在名單正常時也會解凍並重連`() = runTest {
        val h = Harness(this, healthy)

        val result = h.controller.ensureAndUnfreeze(Trigger.MANUAL_UNFREEZE)

        assertTrue(h.settings.writes.isEmpty())
        assertTrue(result.unfroze)
        assertEquals(listOf("unfreeze", "reconnect"), h.timeline.filter { it == "unfreeze" || it == "reconnect" })
    }

    @Test
    fun `清理時只移除本 App 加入的項目，保留其他程式的項目`() = runTest {
        val h = Harness(this, mapOf(millet to "com.a", game to "com.joyose.task"))
        h.controller.ensure(Trigger.SERVICE_START)
        assertEquals("com.joyose.task_${gms}_$self", h.settings.values[game])
        h.settings.externalWrite(game, "com.joyose.task_${gms}_${self}_com.security.game")

        val result = h.controller.prepareForUninstall()

        assertEquals(CleanupResult.REMOVED, result)
        assertEquals("com.joyose.task_com.security.game", h.settings.values[game])
        assertNull(h.baselines.baseline)
        assertFalse(h.configs.current().autoGuard)
        assertFalse(h.configs.current().nightProtection)
        assertEquals("清理不碰 MILLET", "com.a,$gms,$self", h.settings.values[millet])
    }

    @Test
    fun `GMS 原本就在遊戲白名單時，清理不移除它`() = runTest {
        val h = Harness(this, mapOf(millet to gms, game to gms))
        h.controller.ensure(Trigger.SERVICE_START)
        assertEquals("${gms}_$self", h.settings.values[game])

        h.controller.prepareForUninstall()

        assertEquals(gms, h.settings.values[game])
    }

    @Test
    fun `清理後沒有剩下任何項目時寫入空字串，key 仍然存在`() = runTest {
        val h = Harness(this, mapOf(millet to gms, game to null))
        h.controller.ensure(Trigger.SERVICE_START)

        h.controller.prepareForUninstall()

        assertTrue(game in h.settings.values)
        assertEquals("", h.settings.values[game])
    }

    @Test
    fun `清理後不再維護遊戲白名單`() = runTest {
        val h = Harness(this, mapOf(millet to gms, game to null))
        h.controller.ensure(Trigger.SERVICE_START)
        h.controller.prepareForUninstall()
        val writes = h.settings.writes.size

        h.controller.ensure(Trigger.GAME_ALLOWLIST_CHANGED)
        h.controller.ensure(Trigger.MANUAL_CHECK)

        assertEquals("", h.settings.values[game])
        assertEquals(writes, h.settings.writes.size)
    }

    @Test
    fun `關閉「本 App 加入遊戲白名單」時只移除本 App`() = runTest {
        val h = Harness(this, mapOf(millet to gms, game to null))
        h.controller.ensure(Trigger.SERVICE_START)

        h.controller.removeOwnedFromGameAllowlist(Trigger.SETTINGS_CHANGED, setOf(self), clearBaseline = false)

        assertEquals(gms, h.settings.values[game])
        assertNotNull(h.baselines.baseline)
    }

    @Test
    fun `沒有基準時清理什麼都不寫`() = runTest {
        val h = Harness(this, mapOf(millet to gms, game to "${gms}_com.game"))

        val result = h.controller.removeOwnedFromGameAllowlist(Trigger.SETTINGS_CHANGED, setOf(gms, self), clearBaseline = true)

        assertEquals(CleanupResult.NOTHING_TO_DO, result)
        assertTrue(h.settings.writes.isEmpty())
    }
}
