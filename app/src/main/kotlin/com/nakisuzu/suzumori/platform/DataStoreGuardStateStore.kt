package com.nakisuzu.suzumori.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nakisuzu.suzumori.core.guard.ActionRecord
import com.nakisuzu.suzumori.core.guard.GuardState
import com.nakisuzu.suzumori.core.guard.GuardStateStore
import com.nakisuzu.suzumori.core.guard.Trigger
import com.nakisuzu.suzumori.core.log.LegacyLogText
import com.nakisuzu.suzumori.core.log.LogTextCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class DataStoreGuardStateStore(private val dataStore: DataStore<Preferences>) : GuardStateStore {
    override val state: Flow<GuardState> = dataStore.data.map { it.toState() }.distinctUntilChanged()

    override suspend fun current(): GuardState = state.first()

    override suspend fun update(transform: (GuardState) -> GuardState) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.toState())
            prefs.putOrRemove(LAST_ENSURED_BOOT, updated.lastEnsuredBootCount)
            prefs.putOrRemove(LAST_RELOADED_BOOT, updated.lastReloadedBootCount)
            prefs.putRecord(PATCH_PREFIX, updated.lastPatch)
            prefs.putRecord(UNFREEZE_PREFIX, updated.lastUnfreeze)
            prefs.putRecord(SELF_ISSUE_PREFIX, updated.lastSelfIssue)
            val exitKey = LAST_EXIT_REPORTED
            val exitAt = updated.lastExitReportedAtMillis
            if (exitAt == null) prefs.remove(exitKey) else prefs[exitKey] = exitAt
        }
    }

    private fun Preferences.toState() = GuardState(
        lastEnsuredBootCount = this[LAST_ENSURED_BOOT],
        lastReloadedBootCount = this[LAST_RELOADED_BOOT],
        lastPatch = record(PATCH_PREFIX),
        lastUnfreeze = record(UNFREEZE_PREFIX),
        lastSelfIssue = record(SELF_ISSUE_PREFIX),
        lastExitReportedAtMillis = this[LAST_EXIT_REPORTED],
    )

    private fun Preferences.record(prefix: String): ActionRecord? {
        val at = this[longPreferencesKey("${prefix}_at")] ?: return null
        val trigger = Trigger.fromName(this[stringPreferencesKey("${prefix}_trigger")]) ?: return null
        // 摘要以 LogTextCodec 編碼保存；1.0.0 以前存的是繁體中文句子。
        val summary = this[stringPreferencesKey("${prefix}_summary")] ?: return null
        return ActionRecord(at, trigger, LogTextCodec.decode(summary) ?: LegacyLogText.parse(summary))
    }

    private fun MutablePreferences.putRecord(prefix: String, record: ActionRecord?) {
        val at = longPreferencesKey("${prefix}_at")
        val trigger = stringPreferencesKey("${prefix}_trigger")
        val summary = stringPreferencesKey("${prefix}_summary")
        if (record == null) {
            remove(at)
            remove(trigger)
            remove(summary)
        } else {
            this[at] = record.atMillis
            this[trigger] = record.trigger.name
            this[summary] = LogTextCodec.encode(record.summary)
        }
    }

    private fun MutablePreferences.putOrRemove(key: Preferences.Key<Int>, value: Int?) {
        if (value == null) remove(key) else this[key] = value
    }

    private companion object {
        val LAST_ENSURED_BOOT = intPreferencesKey("last_ensured_boot_count")
        val LAST_RELOADED_BOOT = intPreferencesKey("last_reloaded_boot_count")
        val LAST_EXIT_REPORTED = longPreferencesKey("last_exit_reported_at")
        const val PATCH_PREFIX = "last_patch"
        const val UNFREEZE_PREFIX = "last_unfreeze"
        const val SELF_ISSUE_PREFIX = "last_self_issue"
    }
}
