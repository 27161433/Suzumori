package com.nakisuzu.suzumori.core.log

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 以檔案保存的環狀事件紀錄，保留最近 [capacity] 筆。
 *
 * 新紀錄直接附加到檔案結尾；行數超過 [capacity] + [slack] 時，
 * 把最近 [capacity] 筆寫到暫存檔再改名取代，避免改寫到一半時遺失整份紀錄。
 */
class FileEventLog(
    private val file: File,
    private val capacity: Int = 2000,
    private val slack: Int = 200,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : EventLog {
    private val mutex = Mutex()
    private val cache = MutableStateFlow<List<EventEntry>?>(null)
    private var linesInFile = 0

    override val entries: Flow<List<EventEntry>> = flow {
        mutex.withLock { loadIfNeeded() }
        emitAll(cache.filterNotNull())
    }

    override suspend fun append(entry: EventEntry) {
        mutex.withLock {
            val current = loadIfNeeded()
            withContext(io) {
                file.parentFile?.mkdirs()
                file.appendText(EventLogCodec.encode(entry) + "\n")
            }
            linesInFile++
            val updated = (current + entry).takeLast(capacity)
            if (linesInFile > capacity + slack) rewrite(updated)
            cache.value = updated
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            rewrite(emptyList())
            cache.value = emptyList()
        }
    }

    private suspend fun loadIfNeeded(): List<EventEntry> {
        cache.value?.let { return it }
        val lines = withContext(io) { if (file.exists()) file.readLines() else emptyList() }
        linesInFile = lines.size
        val loaded = lines.mapNotNull(EventLogCodec::decode).takeLast(capacity)
        cache.value = loaded
        return loaded
    }

    private suspend fun rewrite(entries: List<EventEntry>) = withContext(io) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(entries.joinToString("") { EventLogCodec.encode(it) + "\n" })
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
        linesInFile = entries.size
    }
}
