package com.gongka.guardian.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.gongka.guardian.detect.GeofenceManager

/**
 * 地理围栏触发接收器（第二道防线兜底）
 *
 * 系统在后台通过 PendingIntent 广播唤醒本接收器，
 * 即使 App 进程被杀死也会被唤醒（GMS 保证，国内换高德/百度 SDK 同样机制）。
 *
 * 触发链路：离开家围栏(EXIT) → 唤醒前台服务 → 服务执行 15 秒 BLE 裁决
 */
class GeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return

        val transition = event.geofenceTransition

        // 只关心"离开家"（EXIT）事件
        if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            // 离开家：拉起前台服务，让它走裁决逻辑
            val serviceIntent = Intent(context, com.gongka.guardian.service.GkaForegroundService::class.java).apply {
                action = "GEOFENCE_EXIT"
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
