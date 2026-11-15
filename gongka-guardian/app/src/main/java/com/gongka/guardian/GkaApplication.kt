package com.gongka.guardian

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import com.gongka.guardian.core.Const
import com.gongka.guardian.service.GkaForegroundService

/**
 * Application 入口
 *
 * 职责：
 *  1. 创建前台服务的通知渠道（Android 8+ 必须先建 channel，否则通知不显示）
 *  2. 首次启动后拉起前台服务，让守护逻辑"常驻"
 *  3. 引导用户申请关键权限（真正商业落地时建议放一个专门的引导页/弹窗）
 */
class GkaApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        setupCrashHandler() // 最早设置，捕获 Application 创建阶段的崩溃
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // 应用进程一旦被拉起（开机/自启/用户点开），就确保前台服务在跑
        startForegroundServiceIfNeeded()
    }

    /**
     * 全局崩溃捕获：闪退时把堆栈写到文件，下次启动在 MainActivity 里展示，方便定位。
     */
    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // 写本地文件（备用）
                val file = java.io.File(filesDir, "crash.log")
                val sw = java.io.StringWriter()
                val pw = java.io.PrintWriter(sw)
                throwable.printStackTrace(pw)
                pw.flush()
                file.writeText("=== ${System.currentTimeMillis()} ===\n${sw}\n")
            } catch (t: Throwable) {
                // ignore
            }
            // 上报到服务器：必须在独立线程执行（崩溃线程通常是主线程，
            // 直接网络请求会抛 NetworkOnMainThreadException 导致上报失败）
            val latch = java.util.concurrent.CountDownLatch(1)
            Thread {
                try {
                    reportCrashToServer(throwable)
                } catch (t: Throwable) {
                    // ignore
                } finally {
                    latch.countDown()
                }
            }.start()
            try {
                latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
            } catch (t: Throwable) {
                // ignore
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /** 崩溃上报到服务器（App 打不开也能收集到错误堆栈） */
    private fun reportCrashToServer(throwable: Throwable) {
        try {
            val sw = java.io.StringWriter()
            throwable.printStackTrace(java.io.PrintWriter(sw))
            val body = "crash=" + java.net.URLEncoder.encode(sw.toString(), "UTF-8") +
                    "&device=" + java.net.URLEncoder.encode(Build.MODEL, "UTF-8") +
                    "&sdk=" + Build.VERSION.SDK_INT
            val conn = java.net.URL("http://49.233.207.110:8981/api/crash")
                .openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write(body.toByteArray()) }
            conn.inputStream.close()
        } catch (t: Throwable) {
            android.util.Log.e("GkaCrash", "上报失败", t)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Const.NOTIFICATION_CHANNEL_ID,
                "MOMO Ta（常驻）",
                NotificationManager.IMPORTANCE_LOW // LOW：不响铃不打扰，仅常驻占位
            ).apply {
                description = "用于在后台持续监测忘带工卡"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceIfNeeded() {
        val intent = Intent(this, GkaForegroundService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (t: Throwable) {
            // 启动失败（权限未授予等）不崩溃，等用户授权后由 MainActivity 引导再启动
        }
    }
}
