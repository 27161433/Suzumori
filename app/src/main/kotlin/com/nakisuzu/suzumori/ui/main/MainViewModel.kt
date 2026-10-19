package com.nakisuzu.suzumori.ui.main

import android.app.Application
import android.os.PowerManager
import android.os.SystemClock
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
import com.nakisuzu.suzumori.core.settings.SettingKeys
import com.nakisuzu.suzumori.platform.AutoStart
import com.nakisuzu.suzumori.platform.DeviceInfoReader
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
                    selfAutoStart = AutoStart.isAllowed(app),
                )
            }
        }
    }

    fun checkNow() = runAction(BusyAction.CHECK) {
        val result = container.controller.ensure(Trigger.MANUAL_CHECK)
        MainDialog.Message("檢查完成", describe(result))
    }

    fun unfreezeNow() = runAction(BusyAction.UNFREEZE) {
        val result = container.controller.ensureAndUnfreeze(Trigger.MANUAL_UNFREEZE)
        val text = buildString {
            if (result.patched) append("已先補回名單。")
            append("已送出解凍與重連廣播。廣播無法確認對方是否收到，請開啟 FCM 診斷頁面確認連線狀態。")
        }
        MainDialog.Message("已送出", text)
    }

    /** 首頁的自動守護開關。開關本身就是回饋，不顯示轉圈或對話框。 */
    fun setAutoGuard(enabled: Boolean) {
        viewModelScope.launch { container.actions.setAutoGuard(enabled) }
    }

    fun requestCleanup() = showDialog(MainDialog.ConfirmCleanup)

    fun confirmCleanup() = runAction(BusyAction.CLEANUP) {
        dismissDialog()
        val result = container.actions.cleanupForUninstall()
        val text = when (result) {
            CleanupResult.REMOVED -> "已移除本 App 加入遊戲白名單的項目，並停止自動守護。現在可以解除安裝。"
            CleanupResult.NOTHING_TO_DO -> "遊戲白名單中沒有本 App 加入的項目，已停止自動守護。現在可以解除安裝。"
            CleanupResult.FAILED -> "清理失敗，詳見事件紀錄。請確認「修改系統設定」權限仍然開啟後再試一次。"
        }
        MainDialog.CleanupFinished(text, canUninstall = result != CleanupResult.FAILED)
    }

    fun showMessage(title: String, text: String) = showDialog(MainDialog.Message(title, text))

    fun dismissDialog() = local.update { it.copy(dialogVisible = false) }

    /** 收起動畫結束後才清除內容。 */
    fun clearDialog() = local.update { if (it.dialogVisible) it else it.copy(dialog = null) }

    private fun showDialog(dialog: MainDialog) = local.update { it.copy(dialog = dialog, dialogVisible = true) }

    /** 執行一個操作，期間 [MainUiState.busy] 為 true。[action] 回傳的對話框在結束後才顯示。 */
    private fun runAction(kind: BusyAction, action: suspend () -> MainDialog?) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, busyAction = kind) }
        viewModelScope.launch {
            val started = SystemClock.uptimeMillis()
            var dialog: MainDialog? = null
            try {
                dialog = action()
            } finally {
                // 檢查通常不到 0.1 秒就完成，轉圈會一閃而過，看起來像按了沒反應。至少轉一小段時間。
                delay((MIN_BUSY_MILLIS - (SystemClock.uptimeMillis() - started)).coerceAtLeast(0))
                local.update { it.copy(busy = false, busyAction = null) }
                dialog?.let(::showDialog)
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
        private const val MIN_BUSY_MILLIS = 700L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SuzumoriApp
                MainViewModel(app, app.container)
            }
        }
    }
}
