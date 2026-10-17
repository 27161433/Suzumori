package com.nakisuzu.suzumori.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.nakisuzu.suzumori.core.baseline.BaselineStore
import com.nakisuzu.suzumori.core.baseline.GameAllowlistBaseline
import kotlinx.coroutines.flow.first

class DataStoreBaselineStore(private val dataStore: DataStore<Preferences>) : BaselineStore {
    override suspend fun get(): GameAllowlistBaseline? = dataStore.data.first().toBaseline()

    override suspend fun recordIfAbsent(baseline: GameAllowlistBaseline): GameAllowlistBaseline {
        val saved = dataStore.edit { prefs ->
            if (prefs.toBaseline() == null) {
                prefs[GMS_WAS_PRESENT] = baseline.gmsWasPresent
                prefs[SELF_WAS_PRESENT] = baseline.selfWasPresent
                prefs[RECORDED_AT] = baseline.recordedAtMillis
            }
        }
        return checkNotNull(saved.toBaseline())
    }

    override suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(GMS_WAS_PRESENT)
            prefs.remove(SELF_WAS_PRESENT)
            prefs.remove(RECORDED_AT)
        }
    }

    private fun Preferences.toBaseline(): GameAllowlistBaseline? {
        val gms = this[GMS_WAS_PRESENT] ?: return null
        val self = this[SELF_WAS_PRESENT] ?: return null
        val at = this[RECORDED_AT] ?: return null
        return GameAllowlistBaseline(gmsWasPresent = gms, selfWasPresent = self, recordedAtMillis = at)
    }

    private companion object {
        val GMS_WAS_PRESENT = booleanPreferencesKey("game_allowlist_gms_was_present")
        val SELF_WAS_PRESENT = booleanPreferencesKey("game_allowlist_self_was_present")
        val RECORDED_AT = longPreferencesKey("game_allowlist_baseline_recorded_at")
    }
}
