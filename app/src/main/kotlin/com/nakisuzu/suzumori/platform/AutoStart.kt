package com.nakisuzu.suzumori.platform

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.os.Process

/**
 * 小米的「自啟動」權限。
 *
 * 系統以自訂的 AppOps 代碼 10008 記錄（`appops get` 顯示為 `MIUIOP(10008)`），沒有公開 API，
 * 只能透過隱藏的 `checkOpNoThrow(int, int, String)` 讀取。targetSdk 22 不受隱藏 API 限制。
 */
object AutoStart {
    private const val OP_AUTO_START = 10008

    /** 已開啟為 true、未開啟為 false；不是小米系統或讀取失敗時為 null。 */
    @SuppressLint("DiscouragedPrivateApi")
    fun isAllowed(context: Context): Boolean? = try {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val checkOp = AppOpsManager::class.java.getMethod(
            "checkOpNoThrow",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java,
        )
        checkOp.invoke(appOps, OP_AUTO_START, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    } catch (e: ReflectiveOperationException) {
        Diag.i("autostart unreadable: $e")
        null
    }
}
