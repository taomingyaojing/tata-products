package com.gongka.guardian.core

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 轻量持久化封装
 *
 * 核心业务只需要存两个"当日状态"：
 *  - 今天是否已请假（请假了当天完全不打扰）
 *  - 今天是否已确认带卡（确认了当天不再扫描）
 * 都按日期字符串存储，跨天自动失效，避免手动清理。
 */
object Prefs {

    private const val FILE = "gka_prefs"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    // ---------- 首次启动 ----------
    fun isFirstLaunch(ctx: Context): Boolean = sp(ctx).getBoolean(Const.PREF_FIRST_LAUNCH, true)
    fun markLaunched(ctx: Context) = sp(ctx).edit().putBoolean(Const.PREF_FIRST_LAUNCH, false).apply()

    // ---------- 今日请假 ----------
    fun isLeaveToday(ctx: Context): Boolean = sp(ctx).getString(Const.PREF_LEAVE_DATE, "") == today()
    fun markLeaveToday(ctx: Context) = sp(ctx).edit().putString(Const.PREF_LEAVE_DATE, today()).apply()

    // ---------- 今日已带卡 ----------
    fun isPresentToday(ctx: Context): Boolean = sp(ctx).getString(Const.PREF_PRESENT_DATE, "") == today()
    fun markPresentToday(ctx: Context) = sp(ctx).edit().putString(Const.PREF_PRESENT_DATE, today()).apply()

    // ---------- 通勤天数（累计“今日已带卡”的天数） ----------
    fun commuteDays(ctx: Context): Int = sp(ctx).getInt("commute_days", 0)
    fun addCommuteDay(ctx: Context): Int {
        val last = sp(ctx).getString("last_commute_date", "")
        return if (last != today()) {
            val days = commuteDays(ctx) + 1
            sp(ctx).edit()
                .putInt("commute_days", days)
                .putString("last_commute_date", today())
                .apply()
            days
        } else {
            commuteDays(ctx) // 当天已计过，不重复
        }
    }

    // ---------- 家坐标（MainActivity 里设置） ----------
    fun saveHome(ctx: Context, lat: Double, lng: Double) {
        sp(ctx).edit().putString("home_lat", lat.toString()).putString("home_lng", lng.toString()).apply()
    }

    fun homeLat(ctx: Context): Double = sp(ctx).getString("home_lat", null)?.toDoubleOrNull() ?: 0.0
    fun homeLng(ctx: Context): Double = sp(ctx).getString("home_lng", null)?.toDoubleOrNull() ?: 0.0

    fun hasHome(ctx: Context): Boolean = sp(ctx).contains("home_lat") && sp(ctx).contains("home_lng")

    // ---------- 家 WiFi（用于 WiFi 断开监听，比 GPS 围栏更省电） ----------
    fun saveHomeWifi(ctx: Context, ssid: String) {
        sp(ctx).edit().putString("home_wifi_ssid", ssid).apply()
    }

    fun homeWifiSsid(ctx: Context): String? =
        sp(ctx).getString("home_wifi_ssid", null)?.takeIf { it.isNotBlank() }

    fun hasHomeWifi(ctx: Context): Boolean = homeWifiSsid(ctx) != null

    // ---------- 家 WiFi 指纹（BSSID+RSSI 组合，用于"是否真离家"二次确认） ----------
    fun saveHomeWifiFingerprint(ctx: Context, serialized: String) {
        sp(ctx).edit().putString("home_wifi_fingerprint", serialized).apply()
    }

    fun homeWifiFingerprint(ctx: Context): String =
        sp(ctx).getString("home_wifi_fingerprint", "") ?: ""
}
