package com.nakisuzu.suzumori.fakes

import com.nakisuzu.suzumori.core.settings.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/** 記憶體中的 `Settings.System`。和真的 SettingsProvider 一樣沒有刪除 key 的途徑。 */
class FakeSettingsStore(initial: Map<String, String?> = emptyMap()) : SettingsStore {
    val values = initial.filterValues { it != null }.mapValues { it.value!! }.toMutableMap()
    val writes = mutableListOf<Pair<String, String>>()
    var writable = true

    /** 寫入時要拋出的例外；null 表示正常寫入。 */
    var failure: Exception? = null

    /** 寫入完成後執行，用來模擬其他程式（PowerKeeper、Joyose）立刻改寫。 */
    var afterWrite: (key: String, value: String) -> Unit = { _, _ -> }

    /** 讀取前執行，用來模擬其他程式在兩次讀取之間改寫。 */
    var beforeRead: (key: String) -> Unit = {}

    private val events = MutableSharedFlow<String>(extraBufferCapacity = 64)

    override fun canWrite() = writable

    override suspend fun read(key: String): String? {
        beforeRead(key)
        return values[key]
    }

    override suspend fun write(key: String, value: String): Boolean {
        failure?.let { throw it }
        writes += key to value
        val changed = values[key] != value
        values[key] = value
        if (changed) events.tryEmit(key)
        afterWrite(key, value)
        return true
    }

    /** 模擬其他程式寫入，不計入 [writes]。 */
    fun externalWrite(key: String, value: String) {
        values[key] = value
        events.tryEmit(key)
    }

    override fun changes(key: String): Flow<Unit> = events.filter { it == key }.map { }
}
