package com.nakisuzu.suzumori.platform

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class DeviceInfo(
    val model: String,
    val androidVersion: String,
    /** `ro.mi.os.version.name`，讀不到時為 null。 */
    val hyperOsVersion: String?,
    /** `ro.mi.os.version.incremental`，讀不到時為 null。 */
    val hyperOsIncremental: String?,
    val targetSdk: Int,
    /** 0 是主使用者。Greezer 只看主使用者的設定值，在分身空間寫入不會生效。 */
    val userId: Int,
    /** `Settings.Global.BOOT_COUNT`，讀不到時為 null。之後用來判斷是否為新的一次開機。 */
    val bootCount: Int?,
    /** 小米把 App 設為「無限制」時也會加入 DeviceIdle 使用者白名單，可作為輔助指標。 */
    val ignoringBatteryOptimizations: Boolean,
)

object DeviceInfoReader {
    suspend fun read(context: Context): DeviceInfo = withContext(Dispatchers.IO) {
        DeviceInfo(
            model = "${Build.MANUFACTURER} ${Build.MODEL}（${Build.DEVICE}）",
            androidVersion = "${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）",
            hyperOsVersion = getprop("ro.mi.os.version.name"),
            hyperOsIncremental = getprop("ro.mi.os.version.incremental"),
            targetSdk = context.applicationInfo.targetSdkVersion,
            userId = Process.myUid() / PER_USER_RANGE,
            bootCount = runCatching {
                Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
            }.getOrNull()?.takeIf { it >= 0 },
            ignoringBatteryOptimizations = context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName),
        )
    }

    /** 盡力讀取系統屬性，失敗或空值時回傳 null。 */
    private fun getprop(name: String): String? = runCatching {
        val process = ProcessBuilder("getprop", name).redirectErrorStream(true).start()
        try {
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            if (process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0) output.ifEmpty { null } else null
        } finally {
            process.destroy()
        }
    }.getOrNull()

    /** AOSP `UserHandle.PER_USER_RANGE`。 */
    private const val PER_USER_RANGE = 100_000
}
