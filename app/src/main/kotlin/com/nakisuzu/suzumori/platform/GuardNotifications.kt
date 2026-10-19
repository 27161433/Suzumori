package com.nakisuzu.suzumori.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
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

    /** 建立或更新通知頻道。頻道名稱跟著 App 語言，切換語言後再呼叫一次就會更新。 */
    fun ensureChannel(context: Context) {
        val name = context.getString(R.string.notification_channel_name)
        val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.notification_channel_description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        lastPatch: ActionRecord?,
        style: NotificationStyle = NotificationStyle.SYSTEM,
        position: NotificationPosition = NotificationPosition.BOTTOM,
    ): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = context.getString(R.string.notification_title)
        val text = lastPatch?.let {
            context.getString(
                R.string.notification_last_patch,
                time.format(Instant.ofEpochMilli(it.atMillis)),
                context.getString(Labels.trigger(it.trigger)),
            )
        } ?: context.getString(R.string.notification_watching)
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            // targetSdk 22 時，build() 會在本 App 的程序裡預先產生通知版面，文字顏色在當下就固定，
            // 系統不會依深淺色重畫，在 HyperOS 的通知背景上幾乎看不到。
            // 只有這個樣式會讓 build() 跳過預先產生，改由系統用自己的主題繪製。
            // 沒有設定自訂版面時，系統畫出來的就是一般的標題與內文。
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setContentIntent(openApp)
            .setOngoing(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        when (style) {
            NotificationStyle.SYSTEM -> Unit
            // HyperOS 通知欄同一區塊依 when 由新到舊排列。Builder 預設 when 是建立當下，
            // 每次更新內容都會讓這則常駐通知跳回最上面，所以改用固定的時間：
            // 置底用安裝時間，排在較新的通知下方；置頂用遙遠的未來，永遠比其他通知新。
            NotificationStyle.POSITIONED -> builder.setWhen(
                when (position) {
                    NotificationPosition.TOP -> FAR_FUTURE_MILLIS
                    NotificationPosition.BOTTOM -> installedAt(context)
                },
            )
            // HyperOS 只在通知帶有自訂版面（contentView）時才提供長按「收納」。
            // 系統仍會在上方加上 App 圖示與名稱的標頭，版面只負責標題與內文。
            NotificationStyle.FOLDABLE -> builder.setCustomContentView(
                RemoteViews(context.packageName, R.layout.notification_guard).apply {
                    setTextViewText(R.id.notification_title, title)
                    setTextViewText(R.id.notification_text, text)
                },
            )
        }
        return builder.build()
    }

    private fun installedAt(context: Context): Long =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime }
            .getOrDefault(0L)

    /** 重建並更新常駐通知，回傳新的通知。 */
    fun update(context: Context, lastPatch: ActionRecord?, style: NotificationStyle, position: NotificationPosition): Notification {
        val notification = build(context, lastPatch, style, position)
        context.getSystemService(NotificationManager::class.java).notify(ID, notification)
        return notification
    }

    /** 2100-01-01 00:00 UTC。通知設定為不顯示時間，這個值只用來排序。 */
    private const val FAR_FUTURE_MILLIS = 4_102_444_800_000L
}
