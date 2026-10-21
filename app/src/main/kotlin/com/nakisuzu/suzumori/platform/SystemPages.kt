package com.nakisuzu.suzumori.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import androidx.core.net.toUri
import com.nakisuzu.suzumori.R
import com.nakisuzu.suzumori.core.Packages
import com.nakisuzu.suzumori.core.util.describe

/** 跳轉到系統或其他 App 的頁面。成功時回傳 null，失敗時回傳例外描述。 */
object SystemPages {
    /** 「修改系統設定」授權頁；不存在時退回 AOSP 的 App 詳細資訊頁。 */
    fun openWriteSettings(context: Context): String? =
        startFirstAvailable(context, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, packageUri(context)))

    /**
     * 小米「應用管理」的 App 詳情頁，裡面有「省電策略」與「自啟動」。預設是本 App，也可以帶入其他 App。
     * 實測 HyperOS 4 上由 `com.miui.securitycenter/com.miui.appmanager.ApplicationsDetailsActivity` 處理。
     * 找不到這個頁面時退回 AOSP 的 App 詳細資訊頁。
     */
    fun openAppDetails(context: Context, packageName: String = context.packageName, uid: Int = Process.myUid()): String? =
        startFirstAvailable(
            context,
            Intent(ACTION_MIUI_APP_DETAIL)
                .putExtra("package_name", packageName)
                .putExtra("miui.intent.extra.USER_ID", uid / PER_USER_RANGE),
            packageName,
        )

    /**
     * 直接開啟某個 App 的「省電策略」選擇頁（無限制、智能限制…），不必先進 App 詳情頁再點一次。預設是本 App。
     *
     * HyperOS 4 的 App 詳情頁點「省電策略」時，開的是安全中心的 PowerDetailActivity（exported），
     * 並帶 `iconPackage`、`uid`、`UserId`；這裡帶一樣的參數。找不到這個頁面時退回 App 詳情頁。
     */
    fun openPowerPolicy(context: Context, packageName: String = context.packageName, uid: Int = Process.myUid()): String? {
        val intent = Intent()
            .setClassName(SECURITY_CENTER, POWER_DETAIL_ACTIVITY)
            .putExtra("iconPackage", packageName)
            .putExtra("package_name", packageName)
            .putExtra("uid", uid)
            .putExtra("UserId", uid / PER_USER_RANGE)
        return if (intent.resolveActivity(context.packageManager) != null) start(context, intent) else openAppDetails(context, packageName, uid)
    }

    /**
     * GMS 的 FCM 診斷頁。新版是 `gcm.GcmDiagnostics`，舊版是 `gtalkservice.diagnostics.GTalkServiceDiagnostics`，
     * 依序找第一個存在的；兩個都沒有時回傳說明。
     */
    fun openFcmDiagnostics(context: Context): String? {
        val intent = FCM_DIAGNOSTICS
            .map { Intent().setClassName(Packages.GMS, it) }
            .firstOrNull { it.resolveActivity(context.packageManager) != null }
            ?: return context.getString(R.string.fcm_diagnostics_not_found)
        return start(context, intent)
    }

    /** 在瀏覽器開啟網址，例如更新的下載連結。 */
    fun openUrl(context: Context, url: String): String? = start(context, Intent(Intent.ACTION_VIEW, url.toUri()))

    /** 在瀏覽器開啟原始碼頁面。 */
    fun openSourceCode(context: Context): String? = start(context, Intent(Intent.ACTION_VIEW, SOURCE_URL.toUri()))

    /** 在瀏覽器開啟授權條款全文。 */
    fun openLicense(context: Context): String? = start(context, Intent(Intent.ACTION_VIEW, "$SOURCE_URL/blob/main/LICENSE".toUri()))

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

    /** 先確認 [preferred] 存在再啟動；不存在時退回 [packageName] 的 AOSP App 詳細資訊頁。 */
    private fun startFirstAvailable(context: Context, preferred: Intent, packageName: String = context.packageName): String? {
        val intent = if (preferred.resolveActivity(context.packageManager) != null) {
            preferred
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
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

    const val SOURCE_URL = "https://github.com/27161433/Suzumori"
    const val LICENSE_NAME = "GPL-3.0"

    private val FCM_DIAGNOSTICS = listOf(
        "${Packages.GMS}.gcm.GcmDiagnostics",
        "${Packages.GMS}.gtalkservice.diagnostics.GTalkServiceDiagnostics",
    )

    private const val ACTION_MIUI_APP_DETAIL = "miui.intent.action.APP_MANAGER_APPLICATION_DETAIL"
    private const val SECURITY_CENTER = "com.miui.securitycenter"
    private const val POWER_DETAIL_ACTIVITY = "com.miui.powercenter.legacypowerrank.PowerDetailActivity"

    /** AOSP `UserHandle.PER_USER_RANGE`。 */
    private const val PER_USER_RANGE = 100_000
}
