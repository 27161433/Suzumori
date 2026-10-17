package com.nakisuzu.suzumori.platform

import android.content.Context
import android.content.Intent
import com.nakisuzu.suzumori.core.Packages
import com.nakisuzu.suzumori.core.signal.GmsSignals
import com.nakisuzu.suzumori.core.signal.SendResult
import com.nakisuzu.suzumori.core.util.describe

class BroadcastGmsSignals(private val context: Context) : GmsSignals {

    override fun sendUnfreeze(): List<SendResult> = listOf(send(unfreezeIntent()))

    override fun sendReconnect(): List<SendResult> = reconnectIntents().map(::send)

    private fun send(intent: Intent): SendResult = try {
        context.sendBroadcast(intent)
        SendResult(label(intent), error = null)
    } catch (e: Exception) {
        // 只有受保護廣播會在這裡拋出 SecurityException；接收端要求權限時則是默默丟棄。
        SendResult(label(intent), error = describe(e))
    }

    private fun label(intent: Intent) = "${intent.action?.substringAfterLast('.')} → ${intent.`package`}"

    companion object {
        const val ACTION_REFRESH_WHITE_LIST = "com.xiaomi.mipush.REFRESH_WHITE_LIST_ACTION"
        const val ACTION_GCM_RECONNECT = "com.google.android.intent.action.GCM_RECONNECT"
        const val ACTION_MCS_HEARTBEAT = "com.google.android.intent.action.MCS_HEARTBEAT"

        /**
         * 系統（package `android`）的接收端會先解凍 GMS 在主使用者下的 UID，之後才處理 option。
         * option = -1 不會改變任何 MiPush 名單。
         */
        fun unfreezeIntent(): Intent = Intent(ACTION_REFRESH_WHITE_LIST)
            .setPackage("android")
            .putExtra("pkgName", Packages.GMS)
            .putExtra("option", -1)

        fun reconnectIntents(): List<Intent> = listOf(
            Intent(ACTION_GCM_RECONNECT).setPackage(Packages.GMS),
            Intent(ACTION_MCS_HEARTBEAT).setPackage(Packages.GMS),
        )
    }
}
