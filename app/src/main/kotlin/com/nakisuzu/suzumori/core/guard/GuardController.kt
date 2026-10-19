package com.nakisuzu.suzumori.core.guard

import com.nakisuzu.suzumori.core.Packages
import com.nakisuzu.suzumori.core.baseline.BaselineStore
import com.nakisuzu.suzumori.core.baseline.GameAllowlistBaseline
import com.nakisuzu.suzumori.core.config.ConfigStore
import com.nakisuzu.suzumori.core.config.GuardConfig
import com.nakisuzu.suzumori.core.list.GameAllowlist
import com.nakisuzu.suzumori.core.list.MilletList
import com.nakisuzu.suzumori.core.log.EventEntry
import com.nakisuzu.suzumori.core.log.EventLevel
import com.nakisuzu.suzumori.core.log.EventLog
import com.nakisuzu.suzumori.core.log.LogKey
import com.nakisuzu.suzumori.core.log.LogText
import com.nakisuzu.suzumori.core.log.clauses
import com.nakisuzu.suzumori.core.log.list
import com.nakisuzu.suzumori.core.log.msg
import com.nakisuzu.suzumori.core.log.raw
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
            record(trigger, null, null, null, msg(LogKey.SKIP_PATCH), msg(LogKey.PAUSED_BY_RATE_LIMIT), EventLevel.WARNING)
            if (forceUnfreeze) unfreezeLocked(trigger)
            return EnsureResult(patched = false, failed = false, unfroze = forceUnfreeze, paused = true)
        }

        val patches = mutableListOf<LogText>()
        var failed = false
        fun collect(outcome: PatchOutcome, summary: LogText?) {
            when (outcome) {
                PatchOutcome.WRITTEN -> summary?.let { patches += it }
                PatchOutcome.FAILED, PatchOutcome.RATE_LIMITED -> failed = true
                PatchOutcome.ALREADY_OK -> Unit
            }
        }

        val (milletOutcome, milletSummary) = ensureMillet(trigger)
        collect(milletOutcome, milletSummary)
        if (config.nightProtection) {
            val (outcome, summary) = ensureGameAllowlist(trigger, config, bootCount, state)
            collect(outcome, summary)
        }

        if (patches.isNotEmpty()) {
            states.update { it.copy(lastPatch = ActionRecord(clock(), trigger, clauses(patches))) }
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

    /**
     * 確保 GMS 與本 App 都在 MILLET 名單中。
     *
     * 本 App 也要在名單中：即使小米自動取消了本 App 的「無限制」，Greezer 仍會豁免本 App，
     * 本 App 才能持續監聽並補回名單。
     */
    private suspend fun ensureMillet(trigger: Trigger): Pair<PatchOutcome, LogText?> {
        val key = SettingKeys.MILLET_NO_RESTRICT_APP
        val required = listOf(Packages.GMS, selfPackage)
        val current = observe(trigger, key)
        val missing = required.filterNot { MilletList.contains(current, it) }
        if (missing.isEmpty()) return PatchOutcome.ALREADY_OK to null
        val names = list(missing.map(::displayName))
        val outcome = patch(
            trigger = trigger,
            key = key,
            action = msg(LogKey.APPEND, names),
            compute = { latest ->
                val stillMissing = required.filterNot { MilletList.contains(latest, it) }
                if (stillMissing.isEmpty()) null else stillMissing.fold(latest) { acc, pkg -> MilletList.add(acc, pkg) }
            },
            verify = { readBack, _ -> required.all { MilletList.contains(readBack, it) } },
        )
        return outcome to msg(LogKey.MILLET_RESTORED, names)
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
    ): Pair<PatchOutcome, LogText?> {
        val key = SettingKeys.GAME_ALLOWLIST
        val required = buildSet {
            add(Packages.GMS)
            if (config.selfInGameAllowlist) add(selfPackage)
        }
        val needsReload = bootCount != null && state.lastReloadedBootCount != bootCount
        val current = observe(trigger, key)
        val missing = required.filterNot { GameAllowlist.contains(current, it) }
        if (missing.isEmpty() && !needsReload) return PatchOutcome.ALREADY_OK to null

        // 第一次寫入之前記錄基準，清理時只移除本 App 加入的項目。
        baselines.recordIfAbsent(
            GameAllowlistBaseline(
                gmsWasPresent = GameAllowlist.contains(current, Packages.GMS),
                selfWasPresent = GameAllowlist.contains(current, selfPackage),
                recordedAtMillis = clock(),
            ),
        )

        val reloadOnly = missing.isEmpty()
        val names = list(missing.map(::displayName))
        val action = if (reloadOnly) msg(LogKey.TOGGLE_SEPARATOR) else msg(LogKey.APPEND, names)
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
        val summary = if (reloadOnly) msg(LogKey.GAME_ALLOWLIST_RELOADED) else msg(LogKey.GAME_ALLOWLIST_RESTORED, names)
        return outcome to summary
    }

    private suspend fun removeOwnedLocked(trigger: Trigger, packages: Set<String>, clearBaseline: Boolean): CleanupResult {
        val key = SettingKeys.GAME_ALLOWLIST
        val current = observe(trigger, key)
        val baseline = baselines.get()
        if (baseline == null) {
            record(trigger, key, null, null, msg(LogKey.CLEANUP_GAME_ALLOWLIST), msg(LogKey.NO_BASELINE))
            return CleanupResult.NOTHING_TO_DO
        }

        val owned = buildSet {
            if (!baseline.gmsWasPresent) add(Packages.GMS)
            if (!baseline.selfWasPresent) add(selfPackage)
        }.intersect(packages)
        val present = owned.filterTo(mutableSetOf()) { GameAllowlist.contains(current, it) }
        if (present.isEmpty()) {
            if (clearBaseline) baselines.clear()
            record(trigger, key, null, null, msg(LogKey.CLEANUP_GAME_ALLOWLIST), msg(LogKey.NOTHING_OWNED))
            return CleanupResult.NOTHING_TO_DO
        }

        val outcome = patch(
            trigger = trigger,
            key = key,
            action = msg(LogKey.REMOVE, list(present.map(::displayName))),
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
        action: LogText,
        compute: (String?) -> String?,
        verify: (readBack: String?, written: String) -> Boolean,
        rateLimited: Boolean = true,
    ): PatchOutcome {
        if (!settings.canWrite()) {
            record(trigger, key, null, null, action, msg(LogKey.NO_WRITE_PERMISSION), EventLevel.WARNING)
            return PatchOutcome.FAILED
        }
        for (attempt in 0..policy.maxRetries) {
            if (attempt > 0) delay(policy.backoffBeforeRetry(attempt))
            val latest = settings.read(key)
            val target = compute(latest) ?: return PatchOutcome.ALREADY_OK
            val label = if (attempt == 0) action else msg(LogKey.RETRY, action, raw(attempt))
            if (rateLimited && !rateLimiter.tryAcquire(clock())) {
                record(
                    trigger, key, latest, null, label,
                    msg(LogKey.RATE_LIMITED, raw(policy.maxWritesPerMinute), raw(policy.pauseMillis / 60_000)),
                    EventLevel.WARNING,
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
            when {
                error != null -> record(trigger, key, latest, readBack, label, msg(LogKey.EXCEPTION, raw(error)), EventLevel.WARNING)
                ok -> record(trigger, key, latest, readBack, label, msg(LogKey.SUCCESS), EventLevel.SUCCESS)
                else -> record(trigger, key, latest, readBack, label, msg(LogKey.VERIFY_FAILED), EventLevel.WARNING)
            }
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
                record(trigger, key, previous, current, msg(LogKey.EXTERNAL_CHANGE, diff(key, previous, current)), membership(key, current))
            }
        }
        lastSeen[key] = current
        return current
    }

    private suspend fun unfreezeLocked(trigger: Trigger) {
        recordSignals(trigger, msg(LogKey.SEND_UNFREEZE), signals.sendUnfreeze())
        delay(policy.reconnectDelayMillis)
        recordSignals(trigger, msg(LogKey.SEND_RECONNECT), signals.sendReconnect())
        states.update { it.copy(lastUnfreeze = ActionRecord(clock(), trigger, msg(LogKey.UNFREEZE_SUMMARY))) }
    }

    /** 寫入事件紀錄。紀錄失敗（例如儲存空間錯誤）不應該中斷守護，所以吞掉例外。 */
    private suspend fun record(
        trigger: Trigger,
        key: String?,
        before: String?,
        after: String?,
        action: LogText,
        result: LogText,
        level: EventLevel = EventLevel.INFO,
    ) {
        val entry = EventEntry(
            timeMillis = clock(),
            trigger = trigger.name,
            key = key,
            before = truncateForLog(before),
            after = truncateForLog(after),
            action = action,
            result = result,
            level = level,
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

    private fun diff(key: String, before: String?, after: String?): LogText {
        val old = entries(key, before).toSet()
        val new = entries(key, after).toSet()
        val added = new - old
        val removed = old - new
        if (added.isEmpty() && removed.isEmpty()) return msg(LogKey.MEMBERS_UNCHANGED)
        return clauses(
            buildList {
                if (removed.isNotEmpty()) add(msg(LogKey.REMOVED_MEMBERS, list(removed.map(::raw))))
                if (added.isNotEmpty()) add(msg(LogKey.ADDED_MEMBERS, list(added.map(::raw))))
            },
        )
    }

    private fun membership(key: String, value: String?): LogText {
        val gms = if (key == SettingKeys.MILLET_NO_RESTRICT_APP) {
            MilletList.contains(value, Packages.GMS)
        } else {
            GameAllowlist.contains(value, Packages.GMS)
        }
        return msg(if (gms) LogKey.GMS_IN_LIST else LogKey.GMS_NOT_IN_LIST)
    }

    private fun displayName(pkg: String): LogText = when (pkg) {
        Packages.GMS -> raw("GMS")
        selfPackage -> msg(LogKey.SELF_APP)
        else -> raw(pkg)
    }

    /** 一組廣播的結果記成一筆；有任何一則拋出例外時標為警告。 */
    private suspend fun recordSignals(trigger: Trigger, action: LogText, results: List<SendResult>) {
        val level = if (results.any { it.error != null }) EventLevel.WARNING else EventLevel.INFO
        val summary = clauses(
            results.map { result ->
                result.error?.let { msg(LogKey.SEND_EXCEPTION, raw(result.label), raw(it)) } ?: msg(LogKey.SENT, raw(result.label))
            },
        )
        record(trigger, null, null, null, action, summary, level)
    }
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
        Trigger.SELF_MONITOR,
        -> false
        else -> true
    }
