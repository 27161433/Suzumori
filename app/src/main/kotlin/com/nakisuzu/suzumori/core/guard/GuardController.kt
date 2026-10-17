package com.nakisuzu.suzumori.core.guard

import com.nakisuzu.suzumori.core.Packages
import com.nakisuzu.suzumori.core.baseline.BaselineStore
import com.nakisuzu.suzumori.core.baseline.GameAllowlistBaseline
import com.nakisuzu.suzumori.core.config.ConfigStore
import com.nakisuzu.suzumori.core.config.GuardConfig
import com.nakisuzu.suzumori.core.list.GameAllowlist
import com.nakisuzu.suzumori.core.list.MilletList
import com.nakisuzu.suzumori.core.log.EventEntry
import com.nakisuzu.suzumori.core.log.EventLog
import com.nakisuzu.suzumori.core.log.truncateForLog
import com.nakisuzu.suzumori.core.settings.SettingKeys
import com.nakisuzu.suzumori.core.settings.SettingsStore
import com.nakisuzu.suzumori.core.signal.GmsSignals
import com.nakisuzu.suzumori.core.signal.SendResult
import com.nakisuzu.suzumori.core.util.describe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 守護核心：確保 GMS 在兩份豁免名單中，必要時解凍並重連。
 *
 * 所有操作都用同一個 [Mutex] 序列化，避免自己的操作互相衝突。
 * 自己的寫入也會觸發 observer，但下一次 [ensure] 會讀到正常狀態而直接結束，不會形成迴圈。
 */
class GuardController(
    private val settings: SettingsStore,
    private val signals: GmsSignals,
    private val baselines: BaselineStore,
    private val configs: ConfigStore,
    private val states: GuardStateStore,
    private val bootCounter: BootCounter,
    private val log: EventLog,
    private val selfPackage: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val policy: GuardPolicy = GuardPolicy(),
) {
    private val mutex = Mutex()
    private val rateLimiter = RateLimiter(policy.maxWritesPerMinute, windowMillis = 60_000L, policy.pauseMillis)

    /** 最近一次看到的值，用來偵測其他程式（PowerKeeper、Joyose 等）的改寫。只在 [mutex] 內存取。 */
    private val lastSeen = mutableMapOf<String, String?>()

    /** 檢查並修補名單。名單都正常時只讀取，不寫入、不送廣播（開機後的第一次除外）。 */
    suspend fun ensure(trigger: Trigger): EnsureResult = mutex.withLock { ensureLocked(trigger, forceUnfreeze = false) }

    /**
     * 先確保名單正常，再無條件解凍並重連。
     * MILLET 沒有 GMS 時，GMS 解凍後幾秒內就會被 GMS 限制器重新凍結，所以不單獨解凍。
     */
    suspend fun ensureAndUnfreeze(trigger: Trigger): EnsureResult = mutex.withLock { ensureLocked(trigger, forceUnfreeze = true) }

    /** 從遊戲白名單移除 [packages] 中由本 App 加入的項目，用於關閉夜間保護或關閉「本 App 加入遊戲白名單」。 */
    suspend fun removeOwnedFromGameAllowlist(trigger: Trigger, packages: Set<String>, clearBaseline: Boolean): CleanupResult =
        mutex.withLock { removeOwnedLocked(trigger, packages, clearBaseline) }

    /**
     * 解除安裝前清理：停止自動守護與夜間保護，只移除本 App 當初加入遊戲白名單的項目。
     * MILLET 不處理，PowerKeeper 下次重建時會自然移除 GMS。
     */
    suspend fun prepareForUninstall(): CleanupResult = mutex.withLock {
        configs.update { it.copy(autoGuard = false, nightProtection = false) }
        removeOwnedLocked(Trigger.UNINSTALL_CLEANUP, setOf(Packages.GMS, selfPackage), clearBaseline = true)
    }

    private suspend fun ensureLocked(trigger: Trigger, forceUnfreeze: Boolean): EnsureResult {
        val config = configs.current()
        if (trigger.isAutomatic && !config.autoGuard) return EnsureResult.SKIPPED

        val bootCount = bootCounter.current()
        val state = states.current()
        val firstSinceBoot = bootCount != null && state.lastEnsuredBootCount != bootCount

        if (rateLimiter.isPaused(clock())) {
            record(trigger, null, null, null, "略過修補", "修補次數超過上限，暫停中")
            if (forceUnfreeze) unfreezeLocked(trigger)
            return EnsureResult(patched = false, failed = false, unfroze = forceUnfreeze, paused = true)
        }

        val patches = mutableListOf<String>()
        var failed = false
        fun collect(outcome: PatchOutcome, summary: String) {
            when (outcome) {
                PatchOutcome.WRITTEN -> patches += summary
                PatchOutcome.FAILED, PatchOutcome.RATE_LIMITED -> failed = true
                PatchOutcome.ALREADY_OK -> Unit
            }
        }

        collect(ensureMillet(trigger), "MILLET 補回 GMS")
        if (config.nightProtection) {
            val (outcome, summary) = ensureGameAllowlist(trigger, config, bootCount, state)
            collect(outcome, summary)
        }

        if (patches.isNotEmpty()) {
            states.update { it.copy(lastPatch = ActionRecord(clock(), trigger, patches.joinToString("；"))) }
        }
        val unfreeze = patches.isNotEmpty() || firstSinceBoot || forceUnfreeze
        if (unfreeze) unfreezeLocked(trigger)
        if (firstSinceBoot) states.update { it.copy(lastEnsuredBootCount = bootCount) }

        return EnsureResult(
            patched = patches.isNotEmpty(),
            failed = failed,
            unfroze = unfreeze,
            paused = rateLimiter.isPaused(clock()),
        )
    }

    private suspend fun ensureMillet(trigger: Trigger): PatchOutcome {
        val key = SettingKeys.MILLET_NO_RESTRICT_APP
        val current = observe(trigger, key)
        if (MilletList.contains(current, Packages.GMS)) return PatchOutcome.ALREADY_OK
        return patch(
            trigger = trigger,
            key = key,
            action = "追加 GMS",
            compute = { latest -> if (MilletList.contains(latest, Packages.GMS)) null else MilletList.add(latest, Packages.GMS) },
            verify = { readBack, _ -> MilletList.contains(readBack, Packages.GMS) },
        )
    }

    /**
     * 確保 GMS（與本 App）在遊戲白名單中。
     *
     * 系統開機時不會主動讀取這個值，只在值改變時載入；寫入相同的值也不會觸發通知。
     * 所以每次開機後至少要讓字串改變一次：有缺項目時追加本身就會改變字串，
     * 否則做一次結尾底線切換。
     */
    private suspend fun ensureGameAllowlist(
        trigger: Trigger,
        config: GuardConfig,
        bootCount: Int?,
        state: GuardState,
    ): Pair<PatchOutcome, String> {
        val key = SettingKeys.GAME_ALLOWLIST
        val required = buildSet {
            add(Packages.GMS)
            if (config.selfInGameAllowlist) add(selfPackage)
        }
        val needsReload = bootCount != null && state.lastReloadedBootCount != bootCount
        val current = observe(trigger, key)
        val missing = required.filterNot { GameAllowlist.contains(current, it) }
        if (missing.isEmpty() && !needsReload) return PatchOutcome.ALREADY_OK to ""

        // 第一次寫入之前記錄基準，清理時只移除本 App 加入的項目。
        baselines.recordIfAbsent(
            GameAllowlistBaseline(
                gmsWasPresent = GameAllowlist.contains(current, Packages.GMS),
                selfWasPresent = GameAllowlist.contains(current, selfPackage),
                recordedAtMillis = clock(),
            ),
        )

        val reloadOnly = missing.isEmpty()
        val action = if (reloadOnly) "結尾底線切換（開機後重新載入）" else "追加 ${missing.joinToString("、") { displayName(it) }}"
        val outcome = patch(
            trigger = trigger,
            key = key,
            action = action,
            compute = { latest ->
                val stillMissing = required.filterNot { GameAllowlist.contains(latest, it) }
                when {
                    stillMissing.isNotEmpty() -> stillMissing.fold(latest) { acc, pkg -> GameAllowlist.add(acc, pkg) }
                    needsReload -> GameAllowlist.toggleTrailingSeparator(latest)
                    else -> null
                }
            },
            verify = { readBack, written ->
                required.all { GameAllowlist.contains(readBack, it) } && (!needsReload || readBack == written)
            },
        )
        if (outcome == PatchOutcome.WRITTEN && bootCount != null) {
            states.update { it.copy(lastReloadedBootCount = bootCount) }
        }
        val summary = if (reloadOnly) "遊戲白名單重新載入" else "遊戲白名單補回 ${missing.joinToString("、") { displayName(it) }}"
        return outcome to summary
    }

    private suspend fun removeOwnedLocked(trigger: Trigger, packages: Set<String>, clearBaseline: Boolean): CleanupResult {
        val key = SettingKeys.GAME_ALLOWLIST
        val current = observe(trigger, key)
        val baseline = baselines.get()
        if (baseline == null) {
            record(trigger, key, null, null, "清理遊戲白名單", "沒有基準紀錄，本 App 沒有寫入過遊戲白名單")
            return CleanupResult.NOTHING_TO_DO
        }

        val owned = buildSet {
            if (!baseline.gmsWasPresent) add(Packages.GMS)
            if (!baseline.selfWasPresent) add(selfPackage)
        }.intersect(packages)
        val present = owned.filterTo(mutableSetOf()) { GameAllowlist.contains(current, it) }
        if (present.isEmpty()) {
            if (clearBaseline) baselines.clear()
            record(trigger, key, null, null, "清理遊戲白名單", "名單中沒有本 App 加入的項目")
            return CleanupResult.NOTHING_TO_DO
        }

        val outcome = patch(
            trigger = trigger,
            key = key,
            action = "移除 ${present.joinToString("、") { displayName(it) }}",
            compute = { latest ->
                val toRemove = present.filterTo(mutableSetOf()) { GameAllowlist.contains(latest, it) }
                // 沒有剩下任何項目時是空字串；不刪除 key，否則系統不會清除快取。
                if (toRemove.isEmpty()) null else GameAllowlist.remove(latest, toRemove)
            },
            verify = { readBack, _ -> readBack != null && present.none { GameAllowlist.contains(readBack, it) } },
            rateLimited = false,
        )
        return when (outcome) {
            PatchOutcome.WRITTEN, PatchOutcome.ALREADY_OK -> {
                if (clearBaseline) baselines.clear()
                CleanupResult.REMOVED
            }
            PatchOutcome.FAILED, PatchOutcome.RATE_LIMITED -> CleanupResult.FAILED
        }
    }

    /**
     * 寫入前再讀一次最新值，計算要寫入的值，寫入後讀回驗證。
     * 驗證失敗時以退避間隔重試，最多 [GuardPolicy.maxRetries] 次。
     *
     * @param compute 由最新值算出要寫入的值；回傳 null 表示已經不需要寫入（例如被別人補回了）。
     */
    private suspend fun patch(
        trigger: Trigger,
        key: String,
        action: String,
        compute: (String?) -> String?,
        verify: (readBack: String?, written: String) -> Boolean,
        rateLimited: Boolean = true,
    ): PatchOutcome {
        if (!settings.canWrite()) {
            record(trigger, key, null, null, action, "沒有「修改系統設定」權限，無法寫入")
            return PatchOutcome.FAILED
        }
        for (attempt in 0..policy.maxRetries) {
            if (attempt > 0) delay(policy.backoffBeforeRetry(attempt))
            val latest = settings.read(key)
            val target = compute(latest) ?: return PatchOutcome.ALREADY_OK
            val label = if (attempt == 0) action else "$action（第 $attempt 次重試）"
            if (rateLimited && !rateLimiter.tryAcquire(clock())) {
                record(
                    trigger, key, latest, null, label,
                    "每分鐘修補超過 ${policy.maxWritesPerMinute} 次，暫停 ${policy.pauseMillis / 60_000} 分鐘",
                )
                return PatchOutcome.RATE_LIMITED
            }
            val error = try {
                settings.write(key, target)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                describe(e)
            }
            val readBack = settings.read(key)
            lastSeen[key] = readBack
            val ok = error == null && verify(readBack, target)
            record(
                trigger, key, latest, readBack, label,
                when {
                    error != null -> "例外：$error"
                    ok -> "成功"
                    else -> "驗證失敗"
                },
            )
            if (ok) return PatchOutcome.WRITTEN
        }
        return PatchOutcome.FAILED
    }

    /** 讀取目前的值；和上次看到的不同時，記錄一筆外部變更，方便觀察 PowerKeeper 與 Joyose 改寫的頻率。 */
    private suspend fun observe(trigger: Trigger, key: String): String? {
        val current = settings.read(key)
        if (lastSeen.containsKey(key)) {
            val previous = lastSeen[key]
            if (previous != current) {
                record(trigger, key, previous, current, "偵測到外部變更（${diff(key, previous, current)}）", membership(key, current))
            }
        }
        lastSeen[key] = current
        return current
    }

    private suspend fun unfreezeLocked(trigger: Trigger) {
        record(trigger, null, null, null, "送出解凍廣播", summarize(signals.sendUnfreeze()))
        delay(policy.reconnectDelayMillis)
        record(trigger, null, null, null, "送出重連廣播", summarize(signals.sendReconnect()))
        states.update { it.copy(lastUnfreeze = ActionRecord(clock(), trigger, "已送出解凍與重連")) }
    }

    /** 寫入事件紀錄。紀錄失敗（例如儲存空間錯誤）不應該中斷守護，所以吞掉例外。 */
    private suspend fun record(trigger: Trigger, key: String?, before: String?, after: String?, action: String, result: String) {
        val entry = EventEntry(
            timeMillis = clock(),
            trigger = trigger.label,
            key = key,
            before = truncateForLog(before),
            after = truncateForLog(after),
            action = action,
            result = result,
        )
        try {
            log.append(entry)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private fun entries(key: String, value: String?): List<String> =
        if (key == SettingKeys.MILLET_NO_RESTRICT_APP) MilletList.entries(value) else GameAllowlist.entries(value).filter { it.isNotEmpty() }

    private fun diff(key: String, before: String?, after: String?): String {
        val old = entries(key, before).toSet()
        val new = entries(key, after).toSet()
        val added = new - old
        val removed = old - new
        if (added.isEmpty() && removed.isEmpty()) return "成員不變"
        return buildList {
            if (removed.isNotEmpty()) add("移除 ${removed.joinToString("、")}")
            if (added.isNotEmpty()) add("新增 ${added.joinToString("、")}")
        }.joinToString("；")
    }

    private fun membership(key: String, value: String?): String {
        val gms = if (key == SettingKeys.MILLET_NO_RESTRICT_APP) {
            MilletList.contains(value, Packages.GMS)
        } else {
            GameAllowlist.contains(value, Packages.GMS)
        }
        return if (gms) "GMS 在名單中" else "GMS 不在名單中"
    }

    private fun displayName(pkg: String): String = when (pkg) {
        Packages.GMS -> "GMS"
        selfPackage -> "本 App"
        else -> pkg
    }

    private fun summarize(results: List<SendResult>): String =
        results.joinToString("；") { "${it.label}：${it.error?.let { e -> "例外 $e" } ?: "已送出"}" }
}

enum class PatchOutcome { ALREADY_OK, WRITTEN, FAILED, RATE_LIMITED }

enum class CleanupResult { REMOVED, NOTHING_TO_DO, FAILED }

data class EnsureResult(
    /** 這次有任何寫入成功。 */
    val patched: Boolean,
    /** 有修補在重試後仍失敗，或被限流擋下。 */
    val failed: Boolean,
    /** 這次送出了解凍與重連。 */
    val unfroze: Boolean,
    /** 目前處於限流暫停中。 */
    val paused: Boolean,
) {
    companion object {
        /** 自動守護關閉時，自動觸發的檢查直接略過。 */
        val SKIPPED = EnsureResult(patched = false, failed = false, unfroze = false, paused = false)
    }
}

/** 使用者主動操作以外的觸發，在自動守護關閉時略過。 */
val Trigger.isAutomatic: Boolean
    get() = when (this) {
        Trigger.MANUAL_CHECK,
        Trigger.MANUAL_UNFREEZE,
        Trigger.TILE,
        Trigger.SETTINGS_CHANGED,
        Trigger.UNINSTALL_CLEANUP,
        -> false
        else -> true
    }
