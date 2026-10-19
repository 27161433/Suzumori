package com.nakisuzu.suzumori.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nakisuzu.suzumori.AppContainer
import com.nakisuzu.suzumori.SuzumoriApp
import com.nakisuzu.suzumori.core.log.EventEntry
import com.nakisuzu.suzumori.core.log.EventLogText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EventLogUiState(
    /** 紀錄讀進來之前為 false，避免一開頁先閃過「尚無紀錄」。 */
    val loaded: Boolean = false,
    /** 新的在前。 */
    val events: List<EventEntry> = emptyList(),
    val dialog: LogDialog? = null,
    val dialogVisible: Boolean = false,
)

sealed interface LogDialog {
    data class Message(val title: String, val text: String) : LogDialog

    data object ConfirmClear : LogDialog
}

/** 事件紀錄頁的狀態。和主畫面分開，開啟紀錄頁時不會觸發主畫面的檢查。 */
class EventLogViewModel(private val container: AppContainer) : ViewModel() {
    private val local = MutableStateFlow(EventLogUiState())

    val state: StateFlow<EventLogUiState> = combine(local, container.eventLog.entries) { local, events ->
        local.copy(loaded = true, events = events.asReversed())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventLogUiState())

    fun requestClear() = showDialog(LogDialog.ConfirmClear)

    fun confirmClear() {
        dismissDialog()
        viewModelScope.launch { container.eventLog.clear() }
    }

    fun showMessage(title: String, text: String) = showDialog(LogDialog.Message(title, text))

    fun dismissDialog() = local.update { it.copy(dialogVisible = false) }

    /** 收起動畫結束後才清除內容。 */
    fun clearDialog() = local.update { if (it.dialogVisible) it else it.copy(dialog = null) }

    /** 匯出用的純文字，舊到新。 */
    suspend fun exportText(): String {
        val entries = container.eventLog.entries.first()
        val header = "Suzumori 事件紀錄（匯出時間 ${EventLogText.formatTime(System.currentTimeMillis())}）"
        return EventLogText.format(entries, header)
    }

    private fun showDialog(dialog: LogDialog) = local.update { it.copy(dialog = dialog, dialogVisible = true) }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SuzumoriApp
                EventLogViewModel(app.container)
            }
        }
    }
}
