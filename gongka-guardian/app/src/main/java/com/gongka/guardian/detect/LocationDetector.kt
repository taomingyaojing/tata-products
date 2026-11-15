package com.gongka.guardian.detect

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.gongka.guardian.core.Const
import com.gongka.guardian.core.Prefs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 【离家检测】系统定位 + 距离计算（替代 GMS 地理围栏）
 *
 * 用 Android 原生 [LocationManager]，零第三方 SDK。
 * 按需触发（WiFi 断连/加速度触发时定位一次确认），不做持续定位，省电。
 *
 * 判定逻辑：当前坐标与"家"坐标的 Haversine 距离 > 50 米 → 判定离家。
 */
class LocationDetector(
    private val context: Context,
    private val onAway: () -> Unit
) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /**
     * 检查当前是否离家（由 WiFi 断连等触发点调用，做一次定位确认）
     */
    fun checkIfAwayFromHome() {
        if (!Prefs.hasHome(context)) return
        val loc = lastKnownLocation() ?: return  // 无定位缓存，跳过（交给其他触发源）
        val distance = haversine(
            Prefs.homeLat(context), Prefs.homeLng(context),
            loc.latitude, loc.longitude
        )
        if (distance > Const.GEOFENCE_RADIUS_METERS) {
            onAway()  // 离家 > 50 米
        }
    }

    /** 取最近一次定位缓存（GPS 优先取较新的） */
    @Suppress("MissingPermission")
    private fun lastKnownLocation(): Location? {
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null

        val gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val net = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        return when {
            gps == null -> net
            net == null -> gps
            gps.time >= net.time -> gps
            else -> net
        }
    }

    /** Haversine 球面距离（米） */
    private fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
