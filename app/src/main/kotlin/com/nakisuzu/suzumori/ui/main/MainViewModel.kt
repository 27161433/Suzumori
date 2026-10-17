package com.nakisuzu.suzumori.ui.main

import android.app.Application
import android.os.PowerManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nakisuzu.suzumori.AppContainer
import com.nakisuzu.suzumori.SuzumoriApp
import com.nakisuzu.suzumori.core.Packages
import com.nakisuzu.suzumori.core.guard.CleanupResult
import com.nakisuzu.suzumori.core.guard.EnsureResult
import com.nakisuzu.suzumori.core.guard.Trigger
import com.nakisuzu.suzumori.core.list.GameAllowlist
import com.nakisuzu.suzumori.core.list.MilletList
import com.nakisuzu.suzumori.core.log.EventLogText
import com.nakisuzu.suzumori.core.settings.SettingKeys
import com.nakisuzu.suzumori.platform.DeviceInfoReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val app: Application,
    private val container: AppContainer,
) : ViewModel() {
    private val selfPackage = app.packageName

    /** 不在 DataStore 或事件紀錄裡的畫面狀態。 */
    private val local = MutableStateFlow(MainUiState())

    val state: StateFlow<MainUiState> = combine(
        local,
        container.configs.config,
        container.guardState.state,
        container.eventLog.entries,
    ) { local, config, guard, events ->
        local.copy(
            config = config,
            lastPatch = guard.lastPatch,
            lastUnfreeze = guard.lastUnfreeze,
            lastSelfIssue = guard.lastSelfIssue,
            events = events.asReversed(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        merge(
            container.settings.changes(SettingKeys.MILLET_NO_RESTRICT_APP),
            container.settings.changes(SettingKeys.GAME_ALLOWLIST),
        ).onEach { refresh() }.launchIn(viewModelScope)

        viewModelScope.launch {
            val device = DeviceInfoReader.read(app)
            val version = device.hyperOsVersion?.let { name -> device.hyperOsIncremental?.let { "$name（$it）" } ?: name }
            local.update { it.copy(systemVersion = version) }
        }
        // 開啟 App 時依設定啟動服務與排程，並執行一次檢查。
        viewModelScope.launch {
            container.actions.applySchedule()
            container.controller.ensure(Trigger.APP_OPEN)
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val millet = container.settings.read(SettingKeys.MILLET_NO_RESTRICT_APP)
            val game = container.settings.read(SettingKeys.GAME_ALLOWLIST)
            local.update {
                it.copy(
                    loaded = true,
                    canWrite = container.settings.canWrite(),
                    milletHasGms = MilletList.contains(millet, Packages.GMS),
                    gameHasGms = GameAllowlist.contains(game, Packages.GMS),
                    selfNoRestrict = app.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(selfPackage),
                )
            }
        }
    }

    fun checkNow() = runAction {
        val result = container.controller.ensure(Trigger.MANUAL_CHECK)
        showDialog(MainDialog.Message("檢查完成", describe(result)))
    }

    fun unfreezeNow() = runAction {
        val result = container.controller.ensureAndUnfreeze(Trigger.MANUAL_UNFREEZE)
        val text = buildString {
            if (result.patched) append("已先補回名單。")
            append("已送出解凍與重連廣播。廣播無法確認對方是否收到，請開啟 FCM 診斷頁面確認連線狀態。")
        }
        showDialog(MainDialog.Message("已送出", text))
    }

    fun setAutoGuard(enabled: Boolean) = runAction { container.actions.setAutoGuard(enabled) }

    fun setNightProtection(enabled: Boolean) = runAction { container.actions.setNightProtection(enabled) }

    fun setSelfInGameAllowlist(enabled: Boolean) = runAction { container.actions.setSelfInGameAllowlist(enabled) }

    fun setPersistentNotification(enabled: Boolean) = runAction { container.actions.setPersistentNotification(enabled) }

    fun requestCleanup() = showDialog(MainDialog.ConfirmCleanup)

    fun confirmCleanup() = runAction {
        dismissDialog()
        val result = container.actions.cleanupForUninstall()
        val text = when (result) {
            CleanupResult.REMOVED -> "已移除本 App 加入遊戲白名單的項目，並停止自動守護。現在可以解除安裝。"
            CleanupResult.NOTHING_TO_DO -> "遊戲白名單中沒有本 App 加入的項目，已停止自動守護。現在可以解除安裝。"
            CleanupResult.FAILED -> "清理失敗，詳見事件紀錄。請確認「修改系統設定」權限仍然開啟後再試一次。"
        }
        showDialog(MainDialog.CleanupFinished(text, canUninstall = result != CleanupResult.FAILED))
    }

    fun requestClearLog() = showDialog(MainDialog.ConfirmClearLog)

    fun confirmClearLog() = runAction {
        dismissDialog()
        container.eventLog.clear()
    }

    fun showMessage(title: String, text: String) = showDialog(MainDialog.Message(title, text))

    fun dismissDialog() = local.update { it.copy(dialogVisible = false) }

    /** 收起動畫結束後才清除內容。 */
    fun clearDialog() = local.update { if (it.dialogVisible) it else it.copy(dialog = null) }

    /** 匯出用的純文字，舊到新。 */
    suspend fun exportLogText(): String {
        val entries = container.eventLog.entries.first()
        val header = "Suzumori 事件紀錄（匯出時間 ${EventLogText.formatTime(System.currentTimeMillis())}）"
        return EventLogText.format(entries, header)
    }

    private fun showDialog(dialog: MainDialog) = local.update { it.copy(dialog = dialog, dialogVisible = true) }

    private fun runAction(action: suspend () -> Unit) {
        if (local.value.busy) return
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                action()
            } finally {
                local.update { it.copy(busy = false) }
                refresh()
            }
        }
    }

    private fun describe(result: EnsureResult): String = when {
        result.paused -> "修補次數超過每分鐘上限，暫停中。可能有其他程式在反覆改寫名單，詳見事件紀錄。"
        result.failed -> "有項目修補失敗，詳見事件紀錄。"
        result.patched -> "已補回名單，並送出解凍與重連。"
        result.unfroze -> "名單都正常。這是開機後的第一次檢查，已送出解凍與重連。"
        else -> "名單都正常，不需要修補。"
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SuzumoriApp
                MainViewModel(app, app.container)
            }
        }
    }
}
