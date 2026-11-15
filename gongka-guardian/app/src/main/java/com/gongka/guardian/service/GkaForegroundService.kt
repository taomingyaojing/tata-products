package com.gongka.guardian.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.gongka.guardian.MainActivity
import com.gongka.guardian.R
import com.gongka.guardian.alert.AlertManager
import com.gongka.guardian.ble.GkaBleScanner
import com.gongka.guardian.core.Const
import com.gongka.guardian.core.Prefs
import com.gongka.guardian.detect.ActivityDetector
import com.gongka.guardian.detect.LocationDetector
import com.gongka.guardian.detect.WifiDisconnectDetector
import com.gongka.guardian.detect.WifiFingerprintHelper
import java.util.Calendar

/**
 * 【核心】常驻前台服务 —— 三道防线的"大脑"
 *
 * 状态机设计（防呆 + 省电）：
 *
 *   早高峰窗口(工作日 07:00-10:00) 内：
 *     第一道防线 ActivityDetector：静止→步行/车载  → onMovingDetected()
 *   任意时间（兜底）：
 *     第二道防线 GeofenceManager：离开家 50 米      → onExitHome()
 *
 *   两个触发点汇入同一个裁决入口 [triggerScan()]：
 *     - 15 秒高频 BLE 扫描（ScanFilter 锁 iBeacon UUID）
 *     - 搜到卡   → 静默，标记"今日已带卡"，当天不再打扰
 *     - 没搜到   → 强力报警（AlertManager）
 *
 * 关键省电/防呆点：
 *  1. 无死循环、无短间隔定时器；只在"触发点"才开一次 15 秒扫描
 *  2. [triggerScan()] 有扫描中互斥 + 冷却期，防止重复扫描
 *  3. "今日已带卡 / 今日请假"后当天直接短路，不再做任何扫描
 */
class GkaForegroundService : Service() {

    private lateinit var activityDetector: ActivityDetector
    private lateinit var locationDetector: LocationDetector
    private lateinit var wifiDisconnectDetector: WifiDisconnectDetector
    private lateinit var bleScanner: GkaBleScanner
    private lateinit var alertManager: AlertManager

    /** 是否正在裁决扫描中（互斥） */
    @Volatile private var isScanning = false

    /** 上次裁决结束时间戳（冷却期用） */
    private var lastScanAt = 0L

    /** 今日状态缓存：带卡/请假标记，快速短路 */
    @Volatile private var dismissedToday = false

    override fun onCreate() {
        super.onCreate()
        activityDetector = ActivityDetector(this) { onMovingDetected() }
        locationDetector = LocationDetector(this) { onExitHome() }
        wifiDisconnectDetector = WifiDisconnectDetector(this) { onHomeWifiLost() }
        bleScanner = GkaBleScanner(this)
        alertManager = AlertManager(this)

        // 常驻通知（含 [今日已带卡] [今日请假] 快捷按钮）
        startForeground(Const.NOTIFICATION_ID, buildNotification())

        // 注册通知栏按钮点击的广播接收器
        val actionFilter = IntentFilter().apply {
            addAction(Const.ACTION_MARK_PRESENT)
            addAction(Const.ACTION_MARK_LEAVE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 14+ 必须显式指定 exported 标志，否则 SecurityException
            registerReceiver(actionReceiver, actionFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(actionReceiver, actionFilter)
        }

        // 启动触发源（加速度 / WiFi 断开；定位按需触发，不持续监听）
        activityDetector.start()
        wifiDisconnectDetector.start()
    }

    // ================= 裁决入口（三道防线的汇合点） =================

    /** 第一道防线回调：早高峰内检测到"出门动作" */
    private fun onMovingDetected() {
        if (!isPeakWindow()) return          // 非早高峰，不触发
        if (dismissedToday) return           // 今天已请假/已带卡，不打扰
        triggerScan()
    }

    /** 定位确认回调：距离家 > 50 米（不限早高峰，兜底） */
    private fun onExitHome() {
        if (dismissedToday) return
        triggerScan() // 定位已确认离家，直接裁决
    }

    /** WiFi 断开回调：家 WiFi 断开 = 出门（不限早高峰，最精准的触发源） */
    private fun onHomeWifiLost() {
        if (dismissedToday) return
        confirmAwayThenTrigger()
    }

    /**
     * WiFi 指纹 + 定位双重确认（WiFi 断连触发源）
     *
     * 先扫热点环境与"家指纹"比对，指纹说离家则触发；
     * 指纹说没离家（信号波动）时，再用系统定位算距离兜底，双保险降误报。
     */
    private fun confirmAwayThenTrigger() {
        val homeFp = WifiFingerprintHelper.deserialize(Prefs.homeWifiFingerprint(this))
        if (homeFp.isEmpty()) {
            // 没采过指纹 → 定位确认兜底
            locationDetector.checkIfAwayFromHome()
            return
        }
        WifiFingerprintHelper.collect(this) { nowFp ->
            if (WifiFingerprintHelper.isAwayFromHome(homeFp, nowFp)) {
                triggerScan()   // 指纹确认：真离家了
            } else {
                locationDetector.checkIfAwayFromHome()  // 指纹说没离家 → 定位再确认
            }
        }
    }

    /**
     * 裁决逻辑：开 15 秒高频扫描 → 搜到静默 / 没搜到报警
     */
    private fun triggerScan() {
        if (isScanning) return                          // 防呆：扫描中，忽略重复触发
        val now = System.currentTimeMillis()
        if (now - lastScanAt < Const.SCAN_COOLDOWN_MS) return  // 冷却期内，防抖

        // 请假了今天直接短路
        if (Prefs.isLeaveToday(this)) {
            dismissedToday = true
            refreshNotification()
            return
        }

        isScanning = true
        lastScanAt = now

        bleScanner.startScan(
            uuid = Const.WORK_CARD_UUID,
            timeoutMs = Const.SCAN_TIMEOUT_MS
        ) { found ->
            isScanning = false
            if (found) {
                // ✅ 搜到卡 → 绝对静默，标记今日已带卡
                Prefs.markPresentToday(this)
                dismissedToday = true
                refreshNotification()
            } else {
                // ❌ 15 秒没搜到 → 强力阻断式报警
                alertManager.launchStrongAlert()
            }
        }
    }

    // ================= 早高峰窗口判断 =================

    private fun isPeakWindow(): Boolean {
        val cal = Calendar.getInstance()
        val day = cal.get(Calendar.DAY_OF_WEEK)  // 1=周日 ... 7=周六
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        if (day == Calendar.SATURDAY || day == Calendar.SUNDAY) return false // 周末不报
        return hour in Const.PEAK_START_HOUR until Const.PEAK_END_HOUR       // 07:00-09:59
    }

    // ================= 通知 =================

    private fun buildNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // [今日已带卡] 快捷按钮
        val markPresent = PendingIntent.getBroadcast(
            this, 1, Intent(Const.ACTION_MARK_PRESENT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // [今日请假] 快捷按钮
        val markLeave = PendingIntent.getBroadcast(
            this, 2, Intent(Const.ACTION_MARK_LEAVE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val status = when {
            Prefs.isLeaveToday(this) -> "今日请假，已豁免"
            Prefs.isPresentToday(this) -> "今日已带卡 ✓"
            else -> "守护中，出门自动检测"
        }

        return NotificationCompat.Builder(this, Const.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("MOMO Ta")
            .setContentText(status)
            .setOngoing(true)                       // 常驻，不可划掉
            .setContentIntent(tapIntent)
            .addAction(0, "今日已带卡", markPresent)
            .addAction(0, "今日请假", markLeave)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun refreshNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(Const.NOTIFICATION_ID, buildNotification())
    }

    /** 通知栏按钮点击处理 */
    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Const.ACTION_MARK_PRESENT -> {
                    Prefs.markPresentToday(context)
                    dismissedToday = true
                    alertManager.stop()       // 顺便停掉可能正在响的报警
                }
                Const.ACTION_MARK_LEAVE -> {
                    Prefs.markLeaveToday(context)
                    dismissedToday = true
                    alertManager.stop()
                }
            }
            refreshNotification()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 处理跨进程/跨组件唤醒时携带的动作指令
        when (intent?.action) {
            // 全屏报警界面点击"我知道了/今天请假"后，通知服务停止报警
            "STOP_ALERT" -> alertManager.stop()
            // 地理围栏广播：离开家 → 走裁决逻辑
            "GEOFENCE_EXIT" -> onExitHome()
        }
        // START_STICKY：被系统杀掉后尝试重建，提升存活率
        return START_STICKY
    }

    override fun onDestroy() {
        try { unregisterReceiver(actionReceiver) } catch (_: Exception) {}
        activityDetector.stop()
        wifiDisconnectDetector.stop()
        bleScanner.stop()
        alertManager.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
