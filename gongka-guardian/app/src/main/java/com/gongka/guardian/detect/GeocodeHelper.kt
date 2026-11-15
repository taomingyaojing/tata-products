package com.gongka.guardian.detect

import com.gongka.guardian.core.Prefs
import org.json.JSONObject

/**
 * 高德地理编码 Web API（辅助：地址 → 经纬度）
 *
 * 用途：用户首次设置"家"时，输入"XX小区/XX大厦"等文字地址，
 * 调用高德地理编码接口转成经纬度，存本地供距离计算用。
 * 零 SDK、零 jar/so，纯 HTTP 请求。
 */
object GeocodeHelper {

    /** 高德 Web 服务 key（部署时替换为真实 key，不要提交到仓库） */
    private const val AMAP_KEY = "YOUR_AMAP_KEY"

    private const val GEO_URL = "https://restapi.amap.com/v3/geocode/geo"

    /**
     * 地址 → 经纬度
     *
     * @param address  用户输入的文字地址，如"北京市海淀区中关村"
     * @param onResult 回调（后台线程执行）：成功 (lat, lng)，失败 null
     */
    fun geocode(address: String, onResult: (Pair<Double, Double>?) -> Unit) {
        if (address.isBlank()) {
            onResult(null)
            return
        }
        Thread {
            try {
                val url = "$GEO_URL?address=" +
                        java.net.URLEncoder.encode(address, "UTF-8") +
                        "&key=$AMAP_KEY"
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                val resp = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val obj = JSONObject(resp)
                if (obj.optString("status") != "1") {
                    onResult(null)
                    return@Thread
                }
                val first = obj.optJSONArray("geocodes")?.optJSONObject(0)
                val location = first?.optString("location") ?: ""  // "经度,纬度"
                val parts = location.split(",")
                if (parts.size == 2) {
                    val lng = parts[0].trim().toDoubleOrNull()
                    val lat = parts[1].trim().toDoubleOrNull()
                    if (lat != null && lng != null) {
                        onResult(Pair(lat, lng))
                        return@Thread
                    }
                }
                onResult(null)
            } catch (t: Throwable) {
                onResult(null)
            }
        }.start()
    }
}
