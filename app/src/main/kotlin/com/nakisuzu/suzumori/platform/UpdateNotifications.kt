package com.nakisuzu.suzumori.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.nakisuzu.suzumori.MainActivity
import com.nakisuzu.suzumori.R
import com.nakisuzu.suzumori.core.update.ReleaseInfo

/** 有新版本時的通知，點一下開啟 App 並顯示更新頁面。 */
object UpdateNotifications {
    private const val ID = 2
    private const val CHANNEL_ID = "update"

    fun show(context: Context, release: ReleaseInfo) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.update_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.update_channel_description)
                setShowBadge(false)
            },
        )
        // SINGLE_TOP：App 已經開著時交給現有的畫面（onNewIntent），不重建。
        val open = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_UPDATE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.update_notification_title, release.version.toString()))
            .setContentText(context.getString(R.string.update_tap_to_view))
            // targetSdk 22 時 build() 會在本 App 的程序裡預先產生版面、固定文字顏色；
            // 這個樣式讓系統自己繪製，原因見 GuardNotifications。
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(ID, notification)
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }

    /** 和常駐通知的 PendingIntent（request code 0）分開，避免互相覆蓋 extras。 */
    private const val REQUEST_CODE = 1
}
