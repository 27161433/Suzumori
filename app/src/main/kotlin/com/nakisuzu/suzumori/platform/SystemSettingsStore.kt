package com.nakisuzu.suzumori.platform

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.provider.Settings
import com.nakisuzu.suzumori.core.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

/**
 * 以 `Settings.System` 實作 [SettingsStore]。
 *
 * 寫入非公開 key 能成功，完全仰賴 targetSdk 22 的相容行為：
 * targetSdk 23 以上會拋出 IllegalArgumentException。
 */
class SystemSettingsStore(private val context: Context) : SettingsStore {
    private val resolver: ContentResolver = context.contentResolver

    override fun canWrite(): Boolean = Settings.System.canWrite(context)

    override suspend fun read(key: String): String? = withContext(Dispatchers.IO) {
        Settings.System.getString(resolver, key)
    }

    override suspend fun write(key: String, value: String): Boolean = withContext(Dispatchers.IO) {
        Settings.System.putString(resolver, key, value)
    }

    override fun changes(key: String): Flow<Unit> = callbackFlow {
        // Handler 傳 null：onChange 會在 binder 執行緒呼叫，trySend 是執行緒安全的。
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(Settings.System.getUriFor(key), false, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate()
}
