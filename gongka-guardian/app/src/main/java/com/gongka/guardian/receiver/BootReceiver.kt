package com.gongka.guardian.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.gongka.guardian.service.GkaForegroundService

/**
 * 开机自启接收器
 *
 * 手机重启后系统广播 BOOT_COMPLETED，这里重新拉起前台服务，
 * 让守护逻辑"复活"。
 *
 * ⚠️ 国产机保活注意：
 *  仅靠 BOOT_COMPLETED 在华为/小米/OV 上经常被系统拦截，
 *  必须引导用户手动把 App 加入「自启动管理」白名单（见 MainActivity 引导）。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val serviceIntent = Intent(context, GkaForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
