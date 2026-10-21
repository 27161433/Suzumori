package com.nakisuzu.suzumori.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 介面上的說明，使用者手動關閉後就不再顯示。
 *
 * 記錄在 App 資料裡，所以重新安裝或清除資料後會再出現。
 */
class HintStore(private val dataStore: DataStore<Preferences>) {
    /** FCM App 檢查頁上方的說明是否還要顯示。 */
    val showFcmAppsIntro: Flow<Boolean> = dataStore.data
        .map { it[FCM_APPS_INTRO_DISMISSED] != true }
        .distinctUntilChanged()

    suspend fun dismissFcmAppsIntro() {
        dataStore.edit { it[FCM_APPS_INTRO_DISMISSED] = true }
    }

    private companion object {
        val FCM_APPS_INTRO_DISMISSED = booleanPreferencesKey("fcm_apps_intro_dismissed")
    }
}
