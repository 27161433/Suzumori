package com.nakisuzu.suzumori.platform

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import com.nakisuzu.suzumori.core.fcm.FcmApps
import com.nakisuzu.suzumori.core.fcm.PermissionState

/**
 * 小米的「自啟動」權限，只讀取、不修改。
 *
 * 系統以自訂的 AppOps 代碼 10008 記錄（`appops get` 顯示為 `MIUIOP(10008)`），沒有公開 API，
 * 只能透過隱藏的 `checkOpNoThrow(int, int, String)` 讀取。targetSdk 22 不受隱藏 API 限制。
 * 部分機型還有其他相關代碼（例如 10053），用途未經確認，這裡只看 10008。
 */
object AutoStart {
    private const val OP_AUTO_START = 10008

    /** 任一 App 的自啟動狀態；讀不到時為 [PermissionState.UNKNOWN]。 */
    fun state(context: Context, uid: Int, packageName: String): PermissionState = FcmApps.autoStart(mode(context, uid, packageName))

    /** 本 App 的自啟動：已開啟為 true、未開啟為 false；不是小米系統或無法判斷時為 null。 */
    fun isAllowed(context: Context): Boolean? = when (state(context, Process.myUid(), context.packageName)) {
        PermissionState.ALLOWED -> true
        PermissionState.DENIED -> false
        PermissionState.UNKNOWN -> null
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun mode(context: Context, uid: Int, packageName: String): Int? = try {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val checkOp = AppOpsManager::class.java.getMethod(
            "checkOpNoThrow",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java,
        )
        checkOp.invoke(appOps, OP_AUTO_START, uid, packageName) as? Int
    } catch (e: ReflectiveOperationException) {
        Diag.i("autostart unreadable for $packageName: $e")
        null
    } catch (e: RuntimeException) {
        // 例如系統不允許查詢其他 App 時的 SecurityException。
        Diag.i("autostart unreadable for $packageName: $e")
        null
    }
}
