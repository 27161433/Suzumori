package com.nakisuzu.suzumori.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import com.nakisuzu.suzumori.core.Packages
import com.nakisuzu.suzumori.core.util.describe

/** 跳轉到系統或其他 App 的頁面。成功時回傳 null，失敗時回傳例外描述。 */
object SystemPages {
    /** 「修改系統設定」授權頁；不存在時退回 AOSP 的 App 詳細資訊頁。 */
    fun openWriteSettings(context: Context): String? =
        startFirstAvailable(context, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, packageUri(context)))

    /**
     * 小米安全中心的 App 詳情頁，裡面有「省電策略」與「自啟動」。
     * 實測 HyperOS 4 上由 `com.miui.securitycenter/com.miui.appmanager.ApplicationsDetailsActivity` 處理。
     */
    fun openAppPowerSettings(context: Context): String? = startFirstAvailable(
        context,
        Intent(ACTION_MIUI_APP_DETAIL)
            .putExtra("package_name", context.packageName)
            .putExtra("miui.intent.extra.USER_ID", Process.myUid() / PER_USER_RANGE),
    )

    fun openFcmDiagnostics(context: Context): String? = start(
        context,
        Intent().setClassName(Packages.GMS, "${Packages.GMS}.gcm.GcmDiagnostics"),
    )

    /** 系統的解除安裝確認畫面。 */
    fun requestUninstall(context: Context): String? = start(context, Intent(Intent.ACTION_DELETE, packageUri(context)))

    fun shareText(context: Context, subject: String, text: String): String? = start(
        context,
        Intent.createChooser(
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_TEXT, text),
            subject,
        ),
    )

    /** 先確認 [preferred] 存在再啟動；不存在時退回 AOSP 的 App 詳細資訊頁。 */
    private fun startFirstAvailable(context: Context, preferred: Intent): String? {
        val intent = if (preferred.resolveActivity(context.packageManager) != null) {
            preferred
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))
        }
        return start(context, intent)
    }

    private fun start(context: Context, intent: Intent): String? = try {
        context.startActivity(intent)
        null
    } catch (e: ActivityNotFoundException) {
        describe(e)
    } catch (e: SecurityException) {
        describe(e)
    }

    private fun packageUri(context: Context): Uri = Uri.fromParts("package", context.packageName, null)

    private const val ACTION_MIUI_APP_DETAIL = "miui.intent.action.APP_MANAGER_APPLICATION_DETAIL"

    /** AOSP `UserHandle.PER_USER_RANGE`。 */
    private const val PER_USER_RANGE = 100_000
}
