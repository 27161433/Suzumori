package com.nakisuzu.suzumori.core.signal

/**
 * 送給系統與 GMS 的廣播。
 *
 * `sendBroadcast()` 沒有拋出例外，只代表廣播已交給系統，不代表對方有收到：
 * 接收端要求權限時，廣播會被默默丟棄。所以結果只分「已送出」和「拋出例外」。
 */
interface GmsSignals {
    /** 請系統解凍主使用者下的 GMS（MiPush 白名單刷新廣播，option = -1）。 */
    fun sendUnfreeze(): List<SendResult>

    /**
     * 請 GMS 重新連線：`GCM_RECONNECT` 與 `MCS_HEARTBEAT`。
     *
     * 實測 HyperOS 4 上 `GTALK_HEARTBEAT` 與送給 GSF 的心跳都沒有接收端，所以不送。
     */
    fun sendReconnect(): List<SendResult>
}

data class SendResult(
    /** 例如 `GCM_RECONNECT → com.google.android.gms`。 */
    val label: String,
    /** null 表示已送出；否則是拋出的例外描述。 */
    val error: String?,
)
