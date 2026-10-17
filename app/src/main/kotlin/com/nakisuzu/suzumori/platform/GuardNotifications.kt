package com.nakisuzu.suzumori.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.nakisuzu.suzumori.MainActivity
import com.nakisuzu.suzumori.R
import com.nakisuzu.suzumori.core.guard.ActionRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 前景服務的常駐通知：低重要性、無聲、不顯示角標。 */
object GuardNotifications {
    const val ID = 1
    private const val CHANNEL_ID = "guard"
    private val time = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "守護狀態", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Suzumori 在背景監聽名單變化時顯示的常駐通知"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun build(context: Context, lastPatch: ActionRecord?): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = lastPatch?.let { "最近修補：${time.format(Instant.ofEpochMilli(it.atMillis))}・${it.trigger.label}" }
            ?: "正在監聽名單變化"
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("正在守護 FCM 推播")
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun update(context: Context, lastPatch: ActionRecord?) {
        context.getSystemService(NotificationManager::class.java).notify(ID, build(context, lastPatch))
    }
}
