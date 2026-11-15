package com.gongka.guardian.detect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import com.gongka.guardian.core.Prefs

/**
 * 【新增触发点】WiFi 断开监听（在家连 WiFi → 出门 WiFi 断 = 最干净的"离开家"信号）
 *
 * 为什么它比地理围栏更优：
 *  - 零耗电：监听系统广播，不主动定位、不轮询，GPS 围栏要持续/周期定位才准
 *  - 零误报：家里 WiFi 断连几乎只发生在"真的离开家"或"路由器重启"两个场景
 *  - 精准：断连瞬间即触发，比"移动状态/位置围栏"的判定更干脆
 *
 * 状态机（维护"是否连着家 WiFi"的上升/下降沿）：
 *   CONNECTED ──(断开)──▶ DISCONNECTED ──▶ 触发一次 onHomeWifiLost()
 *   每次广播到来，都拿"当前连接的 SSID"与"家 WiFi"比对，只在
 *   「上一次连家 WiFi，这一次不连」的下降沿回调一次，避免重复触发。
 *
 * 注意：
 *  - 读取当前 SSID 在 Android 8.1+ 需要定位权限（manifest 已声明）
 *  - 本接收器在前台服务内动态注册（服务常驻，动态注册最可靠）
 */
class WifiDisconnectDetector(
    private val context: Context,
    private val onHomeWifiLost: () -> Unit
) {

    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /** 状态缓存：上一次是否连着家 WiFi */
    private var wasConnectedToHome = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val connected = isConnectedToHomeWifi()
            // 下降沿：上次连家 WiFi，这次断开了 → 回调（指纹确认交给服务层统一做）
            if (wasConnectedToHome && !connected) {
                onHomeWifiLost()
            }
            wasConnectedToHome = connected
        }
    }

    fun start() {
        // 没有绑定家 WiFi 就不监听
        if (!Prefs.hasHomeWifi(context)) return

        // 初始化状态（服务重启时，避免把"从未连接"误判成"断开"）
        wasConnectedToHome = isConnectedToHomeWifi()

        val filter = IntentFilter().apply {
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(ConnectivityManager.CONNECTIVITY_ACTION) // 兜底：网络整体变化
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 系统广播 → RECEIVER_EXPORTED
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    fun stop() {
        try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
    }

    /** 当前是否连接着"家 WiFi" */
    private fun isConnectedToHomeWifi(): Boolean {
        val homeSsid = Prefs.homeWifiSsid(context) ?: return false
        val currentSsid = currentConnectedSsid() ?: return false
        return currentSsid == homeSsid
    }

    /** 获取当前连接的 WiFi SSID（去掉系统包裹的双引号） */
    @Suppress("DEPRECATION")
    private fun currentConnectedSsid(): String? {
        val info = wifiManager.connectionInfo ?: return null
        val ssid = info.ssid ?: return null
        if (ssid == "<unknown ssid>" || ssid == "0x") return null
        return ssid.trim('"').takeIf { it.isNotBlank() }
    }
}
