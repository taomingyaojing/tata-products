package com.gongka.guardian.detect

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.gongka.guardian.core.Const
import com.gongka.guardian.core.Prefs
import com.gongka.guardian.receiver.GeofenceReceiver
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

/**
 * 【第二道防线】地理围栏兜底（离开"家" 50 米强制唤醒）
 *
 * 本实现基于 Google Play Services 的 [GeofencingClient]（标准写法）。
 *
 * ⚠️ 国内落地说明（重要）：
 *  国行手机无 GMS，GeofencingClient 不可用。商业落地时请替换为：
 *    - 高德地图 SDK 的 AMapLocationClient 地理围栏（GeoFenceClient）
 *    - 或百度地图 SDK 的地理围栏（GeoFenceClient）
 *  两者 API 思路一致：注册圆形围栏 → 监听 EXIT 回调 → 广播里触发扫描。
 *  触发链路保持不变，只需替换 [GeofencingClient] 这一段。
 *
 * 为什么用它兜底：
 *  加速度方案可能漏判（比如坐着被车带着走、慢速移动），
 *  地理围栏是"位置级"的硬兜底，只要 GPS/网络定位离开家 50 米就一定触发，
 *  且系统会在后台自动唤醒我们的 Receiver，不依赖 App 进程存活。
 */
class GeofenceManager(
    private val context: Context,
    private val onExitHome: () -> Unit
) {

    /**
     * GMS 客户端（可空）。国行手机普遍无 Google Play Services，
     * 直接调用 getGeofencingClient 会抛异常导致 App 闪退，这里做可用性检查 + 异常防护。
     * 无 GMS 时围栏功能降级禁用，WiFi 断连 + WiFi 指纹 + 加速度仍可正常触发。
     */
    private val client: GeofencingClient? = try {
        if (GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        ) {
            LocationServices.getGeofencingClient(context)
        } else null
    } catch (t: Throwable) {
        null
    }

    /** 是否已注册围栏 */
    private var registered = false

    fun start() {
        val c = client ?: return // 无 GMS，跳过围栏
        if (!Prefs.hasHome(context)) return // 还没设置家坐标，不注册
        val lat = Prefs.homeLat(context)
        val lng = Prefs.homeLng(context)
        addGeofence(c, lat, lng)
    }

    private fun addGeofence(c: GeofencingClient, lat: Double, lng: Double) {
        val geofence = Geofence.Builder()
            .setRequestId(Const.GEOFENCE_HOME_ID)
            .setCircularRegion(lat, lng, Const.GEOFENCE_RADIUS_METERS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            // 只关心"离开"，进入事件忽略，减少无用唤醒
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER) // 首次注册不误报"离开"
            .addGeofence(geofence)
            .build()

        val pendingIntent = geofencePendingIntent()

        try {
            c.addGeofences(request, pendingIntent)
                .addOnSuccessListener { registered = true }
                .addOnFailureListener { registered = false }
        } catch (t: Throwable) {
            registered = false
        }
    }

    fun stop() {
        val c = client ?: return
        if (registered) {
            try {
                c.removeGeofences(geofencePendingIntent())
            } catch (t: Throwable) {
                // ignore
            }
            registered = false
        }
    }

    private fun geofencePendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // 暴露给 Receiver 调用：真正"离开家"了
    fun handleExit() = onExitHome()
}
