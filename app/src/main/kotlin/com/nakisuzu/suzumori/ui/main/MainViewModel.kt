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
import com.nakisuzu.suzumori.R
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
            local.update { it.copy(systemVersion = device.hyperOsVersion, systemBuild = device.hyperOsIncremental) }
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
        MainDialog.Message(app.getString(R.string.check_done), app.getString(describe(result)))
    }

    fun unfreezeNow() = runAction(BusyAction.UNFREEZE) {
        val result = container.controller.ensureAndUnfreeze(Trigger.MANUAL_UNFREEZE)
        val text = app.getString(if (result.patched) R.string.unfreeze_patched_sent_text else R.string.unfreeze_sent_text)
        MainDialog.Message(app.getString(R.string.unfreeze_sent), text)
    }

    /** 首頁的自動守護開關。開關本身就是回饋，不顯示轉圈或對話框。 */
    fun setAutoGuard(enabled: Boolean) {
        viewModelScope.launch { container.actions.setAutoGuard(enabled) }
    }

    fun requestCleanup() = showDialog(MainDialog.ConfirmCleanup)

    fun confirmCleanup() = runAction(BusyAction.CLEANUP) {
        dismissDialog()
        val result = container.actions.cleanupForUninstall()
        val text = app.getString(
            when (result) {
                CleanupResult.REMOVED -> R.string.cleanup_removed
                CleanupResult.NOTHING_TO_DO -> R.string.cleanup_nothing
                CleanupResult.FAILED -> R.string.cleanup_failed
            },
        )
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

    private fun describe(result: EnsureResult): Int = when {
        result.paused -> R.string.check_paused
        result.failed -> R.string.check_failed
        result.patched -> R.string.check_patched
        result.unfroze -> R.string.check_first_boot
        else -> R.string.check_ok
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
