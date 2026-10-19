package com.nakisuzu.suzumori.platform

import android.app.UiModeManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) {
    SYSTEM("跟隨系統"),
    LIGHT("淺色"),
    DARK("深色"),
}

/** 常駐通知的樣式。 */
enum class NotificationStyle(val label: String) {
    /** 系統標準樣式，位置由系統決定。 */
    SYSTEM("系統預設"),

    /** 系統標準樣式，固定排在最上方或最下方（見 [NotificationPosition]）。 */
    POSITIONED("固定位置"),

    /** 自訂版面。HyperOS 只允許有自訂版面的通知長按收納。 */
    FOLDABLE("可收納"),
}

/** [NotificationStyle.POSITIONED] 時，常駐通知在通知欄中的位置。 */
enum class NotificationPosition(val label: String) {
    TOP("置頂"),
    BOTTOM("置底"),
}

/**
 * App 的外觀設定。
 *
 * 深淺色交給系統的單一 App 深淺色設定（[UiModeManager.setApplicationNightMode]），由系統保存並在啟動前套用，
 * 視窗底色與狀態列圖示都會一致，啟動時不會先閃一下錯的顏色。系統沒有提供讀回這個值的方法，
 * 所以另外記下使用者的選擇，給設定頁顯示。
 */
class AppearanceStore(private val context: Context, private val dataStore: DataStore<Preferences>) {
    val themeMode: Flow<ThemeMode> = dataStore.data
        .map { prefs -> ThemeMode.entries.firstOrNull { it.name == prefs[THEME_MODE] } ?: ThemeMode.SYSTEM }
        .distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
        context.getSystemService(UiModeManager::class.java).setApplicationNightMode(
            when (mode) {
                ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
            },
        )
    }

    val notificationStyle: Flow<NotificationStyle> = dataStore.data
        .map { prefs -> NotificationStyle.entries.firstOrNull { it.name == prefs[NOTIFICATION_STYLE] } ?: NotificationStyle.SYSTEM }
        .distinctUntilChanged()

    suspend fun setNotificationStyle(style: NotificationStyle) {
        dataStore.edit { it[NOTIFICATION_STYLE] = style.name }
    }

    val notificationPosition: Flow<NotificationPosition> = dataStore.data
        .map { prefs ->
            NotificationPosition.entries.firstOrNull { it.name == prefs[NOTIFICATION_POSITION] } ?: NotificationPosition.BOTTOM
        }
        .distinctUntilChanged()

    suspend fun setNotificationPosition(position: NotificationPosition) {
        dataStore.edit { it[NOTIFICATION_POSITION] = position.name }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NOTIFICATION_STYLE = stringPreferencesKey("notification_style")
        val NOTIFICATION_POSITION = stringPreferencesKey("notification_position")
    }
}
