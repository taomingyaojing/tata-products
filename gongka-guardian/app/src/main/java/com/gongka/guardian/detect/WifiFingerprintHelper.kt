package com.gongka.guardian.detect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build

/**
 * 【WiFi 指纹大法】—— 用周围热点环境判断"是否真的离家"
 *
 * 思路：
 *  单听一个家 WiFi 断连，可能因信号波动/路由器重启而误报。
 *  WiFi 指纹 = 当前位置能"看到"的所有热点（BSSID）及其信号强度（RSSI）的组合，
 *  这个组合在"家"是稳定且独特的。离开家后，能看到的热点集合会大幅变化。
 *
 * 用法：
 *  1. 首次在家时 [collect] 一次，把指纹存起来（家指纹）
 *  2. 家 WiFi 断连时，再 [collect] 一次当前指纹，用 [isAwayFromHome] 比对
 *  3. 共同热点比例跌破阈值 → 判定"真离家" → 才触发裁决
 *
 * 省电设计：只在"WiFi 断连"这个事件点才主动扫一次，不做周期扫描。
 *
 * 注意：
 *  - Android 9+ 对后台 WiFi 扫描有节流，但前台服务 + 定位权限可豁免大部分场景
 *  - getScanResults() 在 Android 13+ 返回结果受限（BSSID 可能为 null），已做过滤
 */
object WifiFingerprintHelper {

    /** 判定离家的共同热点比例阈值：家的热点还在眼前的不足 30% 即认为已离家 */
    const val AWAY_RATIO_THRESHOLD = 0.3f

    /**
     * 采集一次当前 WiFi 指纹（触发系统扫描，回调结果）
     *
     * @param context  上下文
     * @param onResult 回调：bssid -> rssi(dBm)
     */
    fun collect(context: Context, onResult: (Map<String, Int>) -> Unit) {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                try { c.unregisterReceiver(this) } catch (_: Exception) {}
                onResult(readScanResults(wifiManager))
            }
        }

        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        val started = wifiManager.startScan()
        if (!started) {
            // 无法发起新扫描（节流/被拒），直接读缓存结果兜底
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            onResult(readScanResults(wifiManager))
        }
    }

    /**
     * 判断当前指纹是否"已离家"
     *
     * @param home 家指纹（bssid -> rssi）
     * @param now  当前指纹
     * @return true = 判定已离家；false = 还在家附近
     */
    fun isAwayFromHome(home: Map<String, Int>, now: Map<String, Int>): Boolean {
        if (home.isEmpty()) return false // 没有家指纹，无法判断，交给其他触发源兜底

        var common = 0
        for (bssid in home.keys) {
            if (now.containsKey(bssid)) common++
        }
        val ratio = common.toFloat() / home.size
        return ratio < AWAY_RATIO_THRESHOLD
    }

    /**
     * 读取当前扫描结果（过滤掉无 BSSID 的结果）
     */
    @Suppress("DEPRECATION")
    private fun readScanResults(wm: WifiManager): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        val results = wm.scanResults ?: return map
        for (r in results) {
            val bssid = r.BSSID ?: continue
            map[bssid] = r.level
        }
        return map
    }

    // ================= 序列化（存入 Prefs，无第三方 JSON 依赖） =================
    // 格式： "aa:bb:cc:dd:ee:ff=-45;11:22:33:44:55:66=-60;..."

    fun serialize(map: Map<String, Int>): String =
        map.entries.joinToString(";") { "${it.key}=${it.value}" }

    fun deserialize(s: String): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        if (s.isBlank()) return map
        for (entry in s.split(";")) {
            val kv = entry.split("=")
            if (kv.size == 2) {
                val rssi = kv[1].toIntOrNull() ?: continue
                map[kv[0]] = rssi
            }
        }
        return map
    }
}
