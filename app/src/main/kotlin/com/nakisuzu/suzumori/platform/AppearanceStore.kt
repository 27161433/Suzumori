package com.nakisuzu.suzumori.platform

import android.app.LocaleManager
import android.app.UiModeManager
import android.content.Context
import android.os.LocaleList
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/** App 的介面語言。[tag] 為 null 表示跟隨系統。 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    TRADITIONAL_CHINESE("zh-TW"),
    SIMPLIFIED_CHINESE("zh-CN"),
    ENGLISH("en"),
}

/** 常駐通知的樣式。 */
enum class NotificationStyle {
    /** 系統標準樣式，位置由系統決定。 */
    SYSTEM,

    /** 系統標準樣式，固定排在最上方或最下方（見 [NotificationPosition]）。 */
    POSITIONED,

    /** 自訂版面。HyperOS 只允許有自訂版面的通知長按收納。 */
    FOLDABLE,
}

/** [NotificationStyle.POSITIONED] 時，常駐通知在通知欄中的位置。 */
enum class NotificationPosition {
    TOP,
    BOTTOM,
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

    private val localeManager = context.getSystemService(LocaleManager::class.java)
    private val languageState = MutableStateFlow(readLanguage())

    /**
     * 介面語言交給系統的單一 App 語言設定（[LocaleManager.setApplicationLocales]），由系統保存，
     * 系統設定的「App 語言」頁面也能修改，所以每次都從系統讀回，不另外保存。
     */
    val language: StateFlow<AppLanguage> = languageState.asStateFlow()

    /** 切換語言。系統會重建畫面，介面隨即換成新的語言。 */
    fun setLanguage(language: AppLanguage) {
        languageState.value = language
        localeManager.applicationLocales = language.tag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
    }

    /** 從系統讀回目前的語言，例如從系統設定改過之後。 */
    fun refreshLanguage() {
        languageState.value = readLanguage()
    }

    private fun readLanguage(): AppLanguage {
        val locale = localeManager.applicationLocales.takeUnless { it.isEmpty }?.get(0) ?: return AppLanguage.SYSTEM
        return when {
            locale.language == "en" -> AppLanguage.ENGLISH
            locale.language != "zh" -> AppLanguage.SYSTEM
            locale.script == "Hans" || locale.country in setOf("CN", "SG") -> AppLanguage.SIMPLIFIED_CHINESE
            else -> AppLanguage.TRADITIONAL_CHINESE
        }
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
