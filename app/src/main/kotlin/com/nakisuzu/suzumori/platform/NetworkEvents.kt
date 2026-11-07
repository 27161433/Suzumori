package com.nakisuzu.suzumori.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.SystemClock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * 網路變化：任何能上網的網路出現、消失，或它的傳輸方式改變（例如 VPN 底下從 Wi-Fi 換成行動數據）。
 *
 * 實測 GMS 會在這些時候主動關閉 FCM 連線，再靠鬧鐘重連；SSRU 預算用完時鬧鐘不會響，
 * 所以 GuardService 在變化後補送一次解凍與重連。
 * 註冊後系統會先回報現有的網路，那些不算變化；每個網路第一次回報的傳輸方式也只是記下來。
 */
fun networkEvents(context: Context): Flow<Unit> = callbackFlow {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val registeredAt = SystemClock.elapsedRealtime()
    // 回呼都在 ConnectivityManager 的同一條執行緒上，不需要同步。
    val transports = mutableMapOf<Network, Set<Int>>()

    fun changed(what: String) {
        if (SystemClock.elapsedRealtime() - registeredAt < INITIAL_REPORT_MILLIS) return
        Diag.i("network $what")
        trySend(Unit)
    }

    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = changed("available $network")

        override fun onLost(network: Network) {
            transports.remove(network)
            changed("lost $network")
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val now = TRANSPORTS.filterTo(mutableSetOf()) { capabilities.hasTransport(it) }
            val before = transports.put(network, now)
            if (before != null && before != now) changed("transports $network $before -> $now")
        }
    }
    // 預設的請求排除 VPN；這裡要看到 VPN 網路，它底下的網路換了才看得出來。
    val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        .build()
    manager.registerNetworkCallback(request, callback)
    awaitClose { manager.unregisterNetworkCallback(callback) }
}

/** 註冊後這段時間內收到的回報是現有網路的狀態，不是變化。 */
private const val INITIAL_REPORT_MILLIS = 2_000L

private val TRANSPORTS = listOf(
    NetworkCapabilities.TRANSPORT_CELLULAR,
    NetworkCapabilities.TRANSPORT_WIFI,
    NetworkCapabilities.TRANSPORT_ETHERNET,
    NetworkCapabilities.TRANSPORT_VPN,
)
