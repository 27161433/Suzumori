package com.nakisuzu.suzumori.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nakisuzu.suzumori.AppContainer
import com.nakisuzu.suzumori.SuzumoriApp
import com.nakisuzu.suzumori.core.config.GuardConfig
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
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notificationStyle: NotificationStyle = NotificationStyle.SYSTEM,
    val notificationPosition: NotificationPosition = NotificationPosition.BOTTOM,
    /** 有設定正在寫入，期間擋下其他開關。 */
    val busy: Boolean = false,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val busy = MutableStateFlow(false)

    val state: StateFlow<SettingsUiState> = combine(
        container.configs.config,
        container.appearance.themeMode,
        container.appearance.notificationStyle,
        container.appearance.notificationPosition,
        busy,
    ) { config, themeMode, style, position, busy ->
        SettingsUiState(config, themeMode, style, position, busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

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
