package com.nakisuzu.suzumori.platform

import android.content.Context
import android.os.UserManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File

/**
 * 所有資料都放在裝置保護儲存區（device-protected storage），
 * 開機後、第一次解鎖前（LOCKED_BOOT_COMPLETED）也能讀寫，才能在那時就補回名單。
 * 存的只有開關、清理基準與事件紀錄，沒有需要加密保護的內容。
 */
object DeviceStorage {
    fun context(context: Context): Context = context.createDeviceProtectedStorageContext()

    /**
     * 注意不能用 `preferencesDataStoreFile()`：它會改用 applicationContext，
     * 回到一般（解鎖後才能用的）儲存區。
     */
    fun dataStore(storage: Context, name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { dataStoreFile(storage, name) })

    fun eventLogFile(storage: Context): File = File(storage.filesDir, "events.log")

    /**
     * 第 0 階段把清理基準存在一般儲存區。搬到裝置保護儲存區，否則升級後會遺失基準，
     * 清理時就分不出 GMS 是不是本 App 加入的。必須在建立 DataStore 之前呼叫。
     */
    fun migrateLegacyDataStore(context: Context, storage: Context, name: String) {
        if (!context.getSystemService(UserManager::class.java).isUserUnlocked) return
        val legacy = File(context.filesDir, "datastore/$name.preferences_pb")
        val target = dataStoreFile(storage, name)
        if (!legacy.exists() || target.exists()) return
        target.parentFile?.mkdirs()
        legacy.copyTo(target)
        legacy.delete()
    }

    private fun dataStoreFile(storage: Context, name: String) = File(storage.filesDir, "datastore/$name.preferences_pb")
}
