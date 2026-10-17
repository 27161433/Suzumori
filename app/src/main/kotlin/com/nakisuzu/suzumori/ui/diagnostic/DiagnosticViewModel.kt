package com.nakisuzu.suzumori.ui.diagnostic

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nakisuzu.suzumori.SuzumoriApp
import com.nakisuzu.suzumori.core.Packages
import com.nakisuzu.suzumori.core.baseline.BaselineStore
import com.nakisuzu.suzumori.core.diagnostic.FeasibilityTests
import com.nakisuzu.suzumori.core.diagnostic.WriteReport
import com.nakisuzu.suzumori.core.list.GameAllowlist
import com.nakisuzu.suzumori.core.list.MilletList
import com.nakisuzu.suzumori.core.settings.SettingKeys
import com.nakisuzu.suzumori.core.settings.SettingsStore
import com.nakisuzu.suzumori.core.signal.GmsSignals
import com.nakisuzu.suzumori.core.signal.SendResult
import com.nakisuzu.suzumori.platform.BroadcastGmsSignals
import com.nakisuzu.suzumori.platform.DeviceInfoReader
import com.nakisuzu.suzumori.platform.ReceiverInspector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class DiagnosticViewModel(
    private val app: Application,
    private val settings: SettingsStore,
    private val baselines: BaselineStore,
    private val signals: GmsSignals,
    private val tests: FeasibilityTests,
) : ViewModel() {
    private val selfPackage = app.packageName
    private val nextLogId = AtomicLong()
    private val _state = MutableStateFlow(DiagnosticUiState())
    val state: StateFlow<DiagnosticUiState> = _state.asStateFlow()

    init {
        for (key in listOf(SettingKeys.MILLET_NO_RESTRICT_APP, SettingKeys.GAME_ALLOWLIST)) {
            settings.changes(key).onEach { onObservedChange(key) }.launchIn(viewModelScope)
        }
        log("App 啟動", "observer 已註冊：${SettingKeys.MILLET_NO_RESTRICT_APP}、${SettingKeys.GAME_ALLOWLIST}")
    }

    fun refresh() {
        viewModelScope.launch {
            val device = DeviceInfoReader.read(app)
            val receivers = ReceiverInspector.inspect(
                app,
                listOf(BroadcastGmsSignals.unfreezeIntent()) + BroadcastGmsSignals.reconnectIntents(),
            )
            _state.update {
                it.copy(
                    canWrite = settings.canWrite(),
                    millet = readKey(it.millet),
                    game = readKey(it.game),
                    baseline = baselines.get(),
                    device = device,
                    receivers = receivers,
                )
            }
        }
    }

    fun testMilletWrite() = runAction("測試寫入 MILLET") { describe(tests.testMilletWrite()) }

    fun testGameAllowlistWrite() = runAction("測試寫入遊戲白名單") { describe(tests.testGameAllowlistWrite()) }

    fun cleanupTestWrites() = runAction("清理測試寫入") { describe(tests.cleanupTestWrites()) }

    fun sendUnfreeze() = runAction("送出解凍廣播") { describe(signals.sendUnfreeze()) }

    fun sendReconnect() = runAction("送出重連廣播") { describe(signals.sendReconnect()) }

    fun log(title: String, detail: String = "") {
        val entry = LogEntry(nextLogId.getAndIncrement(), System.currentTimeMillis(), title, detail)
        _state.update { it.copy(log = (listOf(entry) + it.log).take(MAX_LOG)) }
    }

    fun clearLog() = _state.update { it.copy(log = emptyList()) }

    private fun runAction(title: String, action: suspend () -> String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                log(title, action())
            } finally {
                _state.update { it.copy(busy = false) }
                refresh()
            }
        }
    }

    private suspend fun onObservedChange(key: String) {
        val now = System.currentTimeMillis()
        _state.update { state ->
            fun bump(k: KeyState) = if (k.key == key) {
                k.copy(observedChanges = k.observedChanges + 1, lastObservedAtMillis = now)
            } else {
                k
            }
            state.copy(millet = bump(state.millet), game = bump(state.game))
        }
        val value = settings.read(key)
        log("觀察到 $key 變更", "目前的值：${DiagnosticReport.quote(value, LOG_VALUE_LIMIT)}")
        _state.update { it.copy(millet = readKeyFrom(it.millet, key, value), game = readKeyFrom(it.game, key, value)) }
    }

    private suspend fun readKey(current: KeyState): KeyState = readKeyFrom(current, current.key, settings.read(current.key))

    private fun readKeyFrom(current: KeyState, key: String, raw: String?): KeyState {
        if (current.key != key) return current
        val isMillet = key == SettingKeys.MILLET_NO_RESTRICT_APP
        fun has(pkg: String) = if (isMillet) MilletList.contains(raw, pkg) else GameAllowlist.contains(raw, pkg)
        return current.copy(
            loaded = true,
            raw = raw,
            entries = if (isMillet) MilletList.entries(raw) else GameAllowlist.entries(raw).filter { it.isNotEmpty() },
            gmsPresent = has(Packages.GMS),
            selfPresent = has(selfPackage),
        )
    }

    private fun describe(report: WriteReport): String = buildString {
        appendLine("key：${report.key}")
        appendLine("動作：${report.action.label}")
        report.note?.let { appendLine("說明：$it") }
        appendLine("寫入前：${DiagnosticReport.quote(report.before, LOG_VALUE_LIMIT)}")
        if (report.written != null) {
            appendLine("寫入值：${DiagnosticReport.quote(report.written, LOG_VALUE_LIMIT)}")
            appendLine("putString 回傳：${report.putResult ?: "（拋出例外，沒有回傳值）"}")
            appendLine("讀回：${DiagnosticReport.quote(report.readBack, LOG_VALUE_LIMIT)}")
            appendLine("讀回與寫入值相同：${if (report.readBackMatches) "是" else "否"}")
        }
        val gmsAfter = if (report.key == SettingKeys.MILLET_NO_RESTRICT_APP) {
            MilletList.contains(report.readBack, Packages.GMS)
        } else {
            GameAllowlist.contains(report.readBack, Packages.GMS)
        }
        appendLine("讀回後 GMS 在名單中：${if (gmsAfter) "是" else "否"}")
        append("例外：${report.error ?: "無"}")
    }

    private fun describe(results: List<SendResult>): String = results.joinToString("\n") { result ->
        "${result.label}：${result.error?.let { "拋出例外 $it" } ?: "已送出（無法確認對方是否收到）"}"
    }

    companion object {
        private const val MAX_LOG = 500
        private const val LOG_VALUE_LIMIT = 400

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SuzumoriApp
                val container = app.container
                DiagnosticViewModel(
                    app = app,
                    settings = container.settings,
                    baselines = container.baselines,
                    signals = container.signals,
                    tests = container.feasibilityTests,
                )
            }
        }
    }
}
