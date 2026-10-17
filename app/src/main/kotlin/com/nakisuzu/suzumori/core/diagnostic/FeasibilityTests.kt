package com.nakisuzu.suzumori.core.diagnostic

import com.nakisuzu.suzumori.core.Packages
import com.nakisuzu.suzumori.core.baseline.BaselineStore
import com.nakisuzu.suzumori.core.baseline.GameAllowlistBaseline
import com.nakisuzu.suzumori.core.list.GameAllowlist
import com.nakisuzu.suzumori.core.list.MilletList
import com.nakisuzu.suzumori.core.settings.SettingKeys
import com.nakisuzu.suzumori.core.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 第 0 階段的可行性測試：確認一般 App 身分能不能寫入兩個 key，以及能不能清理自己的寫入。
 *
 * 所有操作用同一個 [Mutex] 序列化，避免自己的寫入互相衝突。
 */
class FeasibilityTests(
    private val settings: SettingsStore,
    private val baselines: BaselineStore,
    private val selfPackage: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    /** GMS 不在名單時追加；已在名單時寫回相同的值，確認寫入不會拋出例外。 */
    suspend fun testMilletWrite(): WriteReport = mutex.withLock {
        val key = SettingKeys.MILLET_NO_RESTRICT_APP
        val before = settings.read(key)
        if (MilletList.contains(before, Packages.GMS)) {
            writeAndVerify(key, WriteAction.REWRITE_SAME, before, before.orEmpty())
        } else {
            writeAndVerify(key, WriteAction.APPEND_GMS, before, MilletList.add(before, Packages.GMS))
        }
    }

    /**
     * GMS 不在名單時追加；已在名單時做一次結尾底線切換。
     * 第一次寫入之前先記錄基準，之後清理時才知道哪些項目是本 App 加入的。
     */
    suspend fun testGameAllowlistWrite(): WriteReport = mutex.withLock {
        val key = SettingKeys.GAME_ALLOWLIST
        val initial = settings.read(key)
        baselines.recordIfAbsent(
            GameAllowlistBaseline(
                gmsWasPresent = GameAllowlist.contains(initial, Packages.GMS),
                selfWasPresent = GameAllowlist.contains(initial, selfPackage),
                recordedAtMillis = clock(),
            ),
        )
        // 寫入前再讀一次最新值，縮小與安全中心、Joyose 競爭的時間窗。
        val before = settings.read(key)
        if (GameAllowlist.contains(before, Packages.GMS)) {
            writeAndVerify(key, WriteAction.TOGGLE_TRAILING_SEPARATOR, before, GameAllowlist.toggleTrailingSeparator(before))
        } else {
            writeAndVerify(key, WriteAction.APPEND_GMS, before, GameAllowlist.add(before, Packages.GMS))
        }
    }

    /**
     * 只移除本 App 加入的遊戲白名單項目，寫入剩下的名單（沒有剩下就寫入空字串），不刪除 key。
     *
     * `MILLET_NO_RESTRICT_APP` 不清理：它由 PowerKeeper 擁有，下次重建時自然會移除 GMS。
     */
    suspend fun cleanupTestWrites(): WriteReport = mutex.withLock {
        val key = SettingKeys.GAME_ALLOWLIST
        val before = settings.read(key)
        val baseline = baselines.get()
            ?: return@withLock WriteReport.nothingToDo(key, before, "沒有基準紀錄，代表本 App 沒有寫入過遊戲白名單")

        val owned = buildSet {
            if (!baseline.gmsWasPresent) add(Packages.GMS)
            if (!baseline.selfWasPresent) add(selfPackage)
        }
        val toRemove = owned.filterTo(mutableSetOf()) { GameAllowlist.contains(before, it) }
        if (toRemove.isEmpty()) {
            baselines.clear()
            return@withLock WriteReport.nothingToDo(key, before, "名單中沒有本 App 加入的項目")
        }

        val report = writeAndVerify(key, WriteAction.REMOVE_OWN_ENTRIES, before, GameAllowlist.remove(before, toRemove))
        val stillPresent = toRemove.filter { GameAllowlist.contains(report.readBack, it) }
        if (report.error == null && report.readBackMatches && stillPresent.isEmpty()) baselines.clear()
        report.copy(note = "移除：${toRemove.joinToString()}")
    }

    private suspend fun writeAndVerify(key: String, action: WriteAction, before: String?, value: String): WriteReport {
        val putResult = try {
            settings.write(key, value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return WriteReport(
                key = key,
                action = action,
                before = before,
                written = value,
                putResult = null,
                readBack = settings.read(key),
                error = describe(e),
            )
        }
        return WriteReport(
            key = key,
            action = action,
            before = before,
            written = value,
            putResult = putResult,
            readBack = settings.read(key),
            error = null,
        )
    }
}

enum class WriteAction(val label: String) {
    APPEND_GMS("追加 GMS"),
    REWRITE_SAME("寫回相同的值"),
    TOGGLE_TRAILING_SEPARATOR("結尾底線切換"),
    REMOVE_OWN_ENTRIES("移除本 App 加入的項目"),
    NOTHING_TO_DO("不需要寫入"),
}

data class WriteReport(
    val key: String,
    val action: WriteAction,
    val before: String?,
    /** 嘗試寫入的值；沒有寫入時為 null。 */
    val written: String?,
    /** `Settings.System.putString` 的回傳值；拋出例外或沒有寫入時為 null。 */
    val putResult: Boolean?,
    val readBack: String?,
    /** 拋出的例外描述；null 表示沒有例外。 */
    val error: String?,
    val note: String? = null,
) {
    val readBackMatches: Boolean get() = written != null && readBack == written

    companion object {
        fun nothingToDo(key: String, current: String?, note: String) = WriteReport(
            key = key,
            action = WriteAction.NOTHING_TO_DO,
            before = current,
            written = null,
            putResult = null,
            readBack = current,
            error = null,
            note = note,
        )
    }
}

fun describe(e: Throwable): String = buildString {
    append(e.javaClass.name)
    e.message?.let { append(": ").append(it) }
}
