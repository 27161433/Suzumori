package com.nakisuzu.suzumori.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.nakisuzu.suzumori.core.Packages
import com.nakisuzu.suzumori.core.diagnostic.describe

/** 跳轉到系統或其他 App 的頁面。 */
object SystemPages {
    /** 「修改系統設定」授權頁；不存在時退回 AOSP 的 App 詳細資訊頁。 */
    fun openWriteSettings(context: Context): String? {
        val uri = Uri.fromParts("package", context.packageName, null)
        val primary = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, uri)
        val intent = if (primary.resolveActivity(context.packageManager) != null) {
            primary
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
        }
        return start(context, intent)
    }

    fun openFcmDiagnostics(context: Context): String? = start(
        context,
        Intent().setClassName(Packages.GMS, "${Packages.GMS}.gcm.GcmDiagnostics"),
    )

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

    /** 成功時回傳 null，失敗時回傳例外描述。 */
    private fun start(context: Context, intent: Intent): String? = try {
        context.startActivity(intent)
        null
    } catch (e: ActivityNotFoundException) {
        describe(e)
    } catch (e: SecurityException) {
        describe(e)
    }
}
