package com.nakisuzu.suzumori.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nakisuzu.suzumori.core.config.ConfigStore
import com.nakisuzu.suzumori.core.config.GuardConfig
import com.nakisuzu.suzumori.core.config.SleepKeepAlive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class DataStoreConfigStore(private val dataStore: DataStore<Preferences>) : ConfigStore {
    override val config: Flow<GuardConfig> = dataStore.data.map { it.toConfig() }.distinctUntilChanged()

    override suspend fun current(): GuardConfig = config.first()

    override suspend fun update(transform: (GuardConfig) -> GuardConfig): GuardConfig {
        var result = GuardConfig()
        dataStore.edit { prefs ->
            result = transform(prefs.toConfig())
            prefs[AUTO_GUARD] = result.autoGuard
            prefs[NIGHT_PROTECTION] = result.nightProtection
            prefs[SELF_IN_GAME_ALLOWLIST] = result.selfInGameAllowlist
            prefs[PERSISTENT_NOTIFICATION] = result.persistentNotification
            prefs[SLEEP_KEEP_ALIVE] = result.sleepKeepAlive.name
        }
        return result
    }

    private fun Preferences.toConfig(): GuardConfig {
        val defaults = GuardConfig()
        return GuardConfig(
            autoGuard = this[AUTO_GUARD] ?: defaults.autoGuard,
            nightProtection = this[NIGHT_PROTECTION] ?: defaults.nightProtection,
            selfInGameAllowlist = this[SELF_IN_GAME_ALLOWLIST] ?: defaults.selfInGameAllowlist,
            persistentNotification = this[PERSISTENT_NOTIFICATION] ?: defaults.persistentNotification,
            sleepKeepAlive = SleepKeepAlive.fromName(this[SLEEP_KEEP_ALIVE]) ?: defaults.sleepKeepAlive,
        )
    }

    private companion object {
        val AUTO_GUARD = booleanPreferencesKey("auto_guard")
        val NIGHT_PROTECTION = booleanPreferencesKey("night_protection")
        val SELF_IN_GAME_ALLOWLIST = booleanPreferencesKey("self_in_game_allowlist")
        val PERSISTENT_NOTIFICATION = booleanPreferencesKey("persistent_notification")
        val SLEEP_KEEP_ALIVE = stringPreferencesKey("sleep_keep_alive")
    }
}
