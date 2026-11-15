package com.legalcall.assistant.util

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 联网（匿名使用量统计 + 自愿打赏引导，不碰隐私） */
object Network {

    /**
     * 上报一个使用事件（粗粒度、匿名）。
     * 只传机器码（本身是 MD5 匿名值）+ 事件名 + 版本号。
     * 静默失败，不影响任何功能。
     */
    fun reportEvent(serverUrl: String, machineCode: String, event: String, version: String) {
        try {
            val url = URL(
                "$serverUrl/api/event?machine=${URLEncoder.encode(machineCode, "UTF-8")}" +
                    "&event=${URLEncoder.encode(event, "UTF-8")}" +
                    "&version=${URLEncoder.encode(version, "UTF-8")}"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"
            conn.responseCode  // 触发请求，结果不关心
        } catch (e: Exception) {
            // 静默失败：统计不可达不影响使用
        }
    }

    /**
     * 拉取云端段子库，返回 JSON 字符串；失败返回 null。
     */
    fun fetchJokes(serverUrl: String): String? {
        return try {
            val url = URL("$serverUrl/api/jokes")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 8000
            conn.requestMethod = "GET"
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
