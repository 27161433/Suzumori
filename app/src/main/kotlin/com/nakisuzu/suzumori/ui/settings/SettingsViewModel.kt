package com.nakisuzu.suzumori.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nakisuzu.suzumori.AppContainer
import com.nakisuzu.suzumori.SuzumoriApp
import com.nakisuzu.suzumori.core.config.GuardConfig
import com.nakisuzu.suzumori.core.config.SleepKeepAlive
import com.nakisuzu.suzumori.platform.AppLanguage
import com.nakisuzu.suzumori.platform.NotificationPosition
import com.nakisuzu.suzumori.platform.NotificationStyle
import com.nakisuzu.suzumori.platform.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val config: GuardConfig = GuardConfig(),
    val language: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notificationStyle: NotificationStyle = NotificationStyle.SYSTEM,
    val notificationPosition: NotificationPosition = NotificationPosition.BOTTOM,
    val autoCheckUpdates: Boolean = true,
    /** 有設定正在寫入，期間擋下其他開關。 */
    val busy: Boolean = false,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val busy = MutableStateFlow(false)

    val state: StateFlow<SettingsUiState> = combine(
        combine(container.configs.config, container.appearance.language, container.appearance.themeMode, ::Triple),
        combine(container.appearance.notificationStyle, container.appearance.notificationPosition, container.updates.autoCheck, ::Triple),
        busy,
    ) { (config, language, themeMode), (style, position, autoCheck), busy ->
        SettingsUiState(config, language, themeMode, style, position, autoCheck, busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** 關閉後不再自動連線到 GitHub；更新頁仍可手動檢查。 */
    fun setAutoCheckUpdates(enabled: Boolean) {
        container.appScope.launch { container.updates.setAutoCheck(enabled) }
    }

    fun setLanguage(language: AppLanguage) = container.appearance.setLanguage(language)

    /** 回到畫面時重新讀取語言，使用者可能在系統設定的「App 語言」改過。 */
    fun refreshLanguage() = container.appearance.refreshLanguage()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.appearance.setThemeMode(mode) }
    }

    fun setNotificationStyle(style: NotificationStyle) {
        viewModelScope.launch { container.appearance.setNotificationStyle(style) }
    }

    fun setNotificationPosition(position: NotificationPosition) {
        viewModelScope.launch { container.appearance.setNotificationPosition(position) }
    }

    fun setNightProtection(enabled: Boolean) = run { container.actions.setNightProtection(enabled) }

    fun setSelfInGameAllowlist(enabled: Boolean) = run { container.actions.setSelfInGameAllowlist(enabled) }

    fun setPersistentNotification(enabled: Boolean) = run { container.actions.setPersistentNotification(enabled) }

    fun setSleepKeepAlive(interval: SleepKeepAlive) = run { container.actions.setSleepKeepAlive(interval) }

    private fun run(action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                action()
            } finally {
                busy.value = false
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SuzumoriApp
                SettingsViewModel(app.container)
            }
        }
    }
}
