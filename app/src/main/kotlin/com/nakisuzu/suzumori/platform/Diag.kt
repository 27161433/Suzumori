package com.nakisuzu.suzumori.platform

import android.os.Process
import android.util.Log

/**
 * 寫到 logcat 的診斷訊息，tag 為 `Suzumori`，用 `adb logcat -s Suzumori` 查看。
 *
 * 只記錄程序與服務的生命週期、觸發原因與耗時，不記錄名單內容。
 * 事件紀錄是給使用者看的；這裡是用來追查「程序在不在、有沒有在執行」這類事件紀錄看不到的問題。
 */
internal object Diag {
    private const val TAG = "Suzumori"

    fun i(message: String) {
        Log.i(TAG, "[pid ${Process.myPid()}] $message")
    }
}
