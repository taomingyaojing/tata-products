package com.legalcall.assistant.fakecall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.legalcall.assistant.MainActivity

/**
 * 悬浮球脱身：屏幕悬浮一个"🆘"气泡，点一下立即弹出假来电。
 * 需要 SYSTEM_ALERT_WINDOW 权限（App 内引导授权）。
 */
class FloatingBallService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubble: TextView? = null
    private lateinit var params: WindowManager.LayoutParams

    companion object {
        private const val CHANNEL_ID = "floating_ball"
        private const val NOTIFICATION_ID = 4001
        @Volatile
        private var running = false
        fun isRunning(): Boolean = running
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundCompat()
        createBubble()
    }

    private fun startForegroundCompat() {
        createChannel()
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle("脱身助手运行中")
            .setContentText("点悬浮球即可脱身")
            .setOngoing(true)
            .setContentIntent(openIntent)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "脱身助手", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun createBubble() {
        val v = TextView(this).apply {
            text = "🆘"
            textSize = 22f
            gravity = Gravity.CENTER
        }
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xE6E53935.toInt())
        }
        v.background = drawable

        params = WindowManager.LayoutParams(
            dp(56),
            dp(56),
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = dp(200)
        }

        var startX = 0
        var startY = 0
        var downRawX = 0f
        var downRawY = 0f
        var moved = false

        v.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(view: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        downRawX = e.rawX
                        downRawY = e.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - downRawX).toInt()
                        val dy = (e.rawY - downRawY).toInt()
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) moved = true
                        params.x = startX + dx
                        params.y = startY + dy
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (ex: Exception) {
                            // ignore
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            trigger()
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(v, params)
        bubble = v
    }

    private fun trigger() {
        val personaId = FakeCallHelper.lastPersona(this)
        FakeCallHelper.launch(this, personaId, 0)
    }

    private fun dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        bubble?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // ignore
            }
        }
        bubble = null
        running = false
        super.onDestroy()
    }
}
