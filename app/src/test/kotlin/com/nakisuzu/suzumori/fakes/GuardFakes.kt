package com.nakisuzu.suzumori.fakes

import com.nakisuzu.suzumori.core.config.ConfigStore
import com.nakisuzu.suzumori.core.config.GuardConfig
import com.nakisuzu.suzumori.core.guard.BootCounter
import com.nakisuzu.suzumori.core.guard.GuardState
import com.nakisuzu.suzumori.core.guard.GuardStateStore
import com.nakisuzu.suzumori.core.log.EventEntry
import com.nakisuzu.suzumori.core.log.EventLog
import com.nakisuzu.suzumori.core.signal.GmsSignals
import com.nakisuzu.suzumori.core.signal.SendResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeConfigStore(initial: GuardConfig = GuardConfig()) : ConfigStore {
    private val flow = MutableStateFlow(initial)
    override val config: Flow<GuardConfig> = flow
    override suspend fun current() = flow.value
    override suspend fun update(transform: (GuardConfig) -> GuardConfig): GuardConfig = transform(flow.value).also { flow.value = it }
}

class FakeGuardStateStore(initial: GuardState = GuardState()) : GuardStateStore {
    private val flow = MutableStateFlow(initial)
    override val state: Flow<GuardState> = flow
    val value: GuardState get() = flow.value
    override suspend fun current() = flow.value
    override suspend fun update(transform: (GuardState) -> GuardState) {
        flow.value = transform(flow.value)
    }
}

class FakeBootCounter(var count: Int? = 1) : BootCounter {
    override suspend fun current() = count
}

/** 依序把 `unfreeze`、`reconnect` 記到 [timeline]。 */
class FakeGmsSignals(private val timeline: MutableList<String> = mutableListOf()) : GmsSignals {
    var unfreezeCount = 0
    var reconnectCount = 0

    override fun sendUnfreeze(): List<SendResult> {
        unfreezeCount++
        timeline += "unfreeze"
        return listOf(SendResult("REFRESH_WHITE_LIST_ACTION → android", null))
    }

    override fun sendReconnect(): List<SendResult> {
        reconnectCount++
        timeline += "reconnect"
        return listOf(SendResult("GCM_RECONNECT → com.google.android.gms", null))
    }
}

class FakeEventLog : EventLog {
    private val flow = MutableStateFlow<List<EventEntry>>(emptyList())
    override val entries: Flow<List<EventEntry>> = flow
    val all: List<EventEntry> get() = flow.value
    override suspend fun append(entry: EventEntry) {
        flow.value = flow.value + entry
    }

    override suspend fun clear() {
        flow.value = emptyList()
    }
}
