package com.nakisuzu.suzumori.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * 查詢廣播的靜態接收端與它要求的權限。
 *
 * 只是輔助資訊：系統動態註冊的接收端（例如 system_server 的解凍接收端）查不到，
 * 查到了也不代表廣播一定會生效。
 */
object ReceiverInspector {
    data class Receiver(
        val component: String,
        val permission: String?,
        val permissionHeld: Boolean,
        val exported: Boolean,
    )

    data class Check(val label: String, val receivers: List<Receiver>)

    fun inspect(context: Context, intents: List<Intent>): List<Check> = intents.map { intent ->
        val receivers = context.packageManager
            .queryBroadcastReceivers(intent, PackageManager.ResolveInfoFlags.of(0))
            .map { info ->
                val activityInfo = info.activityInfo
                val permission = activityInfo.permission
                Receiver(
                    component = "${activityInfo.packageName}/${activityInfo.name}",
                    permission = permission,
                    permissionHeld = permission == null ||
                        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED,
                    exported = activityInfo.exported,
                )
            }
        Check(label = "${intent.action?.substringAfterLast('.')} → ${intent.`package`}", receivers = receivers)
    }
}
