package com.nakisuzu.suzumori.platform

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.PowerManager
import androidx.core.graphics.drawable.toBitmap
import com.nakisuzu.suzumori.core.fcm.FcmAppCandidate
import com.nakisuzu.suzumori.core.fcm.FcmAppEntry
import com.nakisuzu.suzumori.core.fcm.FcmApps
import com.nakisuzu.suzumori.core.fcm.PermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 一個使用 FCM 的 App，以及會影響它收推播的兩項設定。 */
data class FcmAppStatus(
    override val packageName: String,
    val uid: Int,
    override val label: String,
    val icon: Bitmap?,
    override val autoStart: PermissionState,
    override val noRestrictions: PermissionState,
) : FcmAppEntry {
    /** 有任一項明確沒開。 */
    val needsAttention: Boolean
        get() = autoStart == PermissionState.DENIED || noRestrictions == PermissionState.DENIED
}

/**
 * 找出手機上使用 FCM 的 App，並讀取它們的自啟動與省電策略。全部唯讀，不修改任何設定。
 *
 * 只在使用者打開「FCM App 檢查」或按重新整理時執行，不在背景執行，結果也不寫進事件紀錄。
 * targetSdk 22 不受 Android 11 的套件可見性限制，查詢其他 App 不需要 `<queries>`。
 */
object FcmAppScanner {
    /** FCM 的訊息服務（firebase-messaging 的 FirebaseMessagingService）。 */
    private const val ACTION_MESSAGING_EVENT = "com.google.firebase.MESSAGING_EVENT"

    /** GMS 把推播交給 App 的廣播（FirebaseInstanceIdReceiver 與舊版 GCM）。 */
    private const val ACTION_C2DM_RECEIVE = "com.google.android.c2dm.intent.RECEIVE"

    suspend fun scan(context: Context): List<FcmAppStatus> = withContext(Dispatchers.Default) {
        val pm = context.packageManager
        val resolved = pm.queryIntentServices(Intent(ACTION_MESSAGING_EVENT), PackageManager.ResolveInfoFlags.of(0)) +
            pm.queryBroadcastReceivers(Intent(ACTION_C2DM_RECEIVE), PackageManager.ResolveInfoFlags.of(0))
        // 服務的結果在 serviceInfo，廣播接收器的在 activityInfo。
        val apps = resolved.mapNotNull { (it.serviceInfo ?: it.activityInfo)?.applicationInfo }.associateBy { it.packageName }
        val candidates = apps.values.map {
            FcmAppCandidate(it.packageName, enabled = it.enabled, hasLauncher = pm.getLaunchIntentForPackage(it.packageName) != null)
        }
        val power = context.getSystemService(PowerManager::class.java)
        // 排序與搜尋由畫面依使用者的選擇處理（FcmApps.arrange）。
        FcmApps.filter(candidates, context.packageName)
            .mapNotNull { apps[it] }
            .map { app -> status(context, pm, power, app) }
    }

    private fun status(context: Context, pm: PackageManager, power: PowerManager, app: ApplicationInfo): FcmAppStatus {
        val ignoring = runCatching { power.isIgnoringBatteryOptimizations(app.packageName) }.getOrNull()
        return FcmAppStatus(
            packageName = app.packageName,
            uid = app.uid,
            label = app.loadLabel(pm).toString(),
            icon = runCatching { app.loadIcon(pm).toBitmap(ICON_PX, ICON_PX) }.getOrNull(),
            autoStart = AutoStart.state(context, app.uid, app.packageName),
            noRestrictions = FcmApps.noRestrictions(ignoring),
        )
    }

    private const val ICON_PX = 120
}
