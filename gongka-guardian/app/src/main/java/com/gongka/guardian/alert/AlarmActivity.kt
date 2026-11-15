package com.gongka.guardian.alert

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import com.gongka.guardian.core.Prefs
import com.gongka.guardian.service.GkaForegroundService

/**
 * 全屏强力报警界面
 *
 * 特点：
 *  - 锁屏/后台也能顶起（manifest 里 showWhenLocked + turnScreenOn）
 *  - 巨大警告文案 + 两个明确按钮：{我知道了 / 今天请假}
 *  - 只有点按钮才能停止报警，返回键/划掉默认无效（拦截 onBackPressed）
 *
 * 注意：这里刻意不写花哨 UI（需求要求核心逻辑即可），
 *       仅用代码动态构建最简单的巨字界面，方便老大替换成正式布局。
 */
class AlarmActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 锁屏 + 常亮 + 键盘锁之上显示
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        // 动态构建界面（正式版替换为 XML 布局）
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(0xFFB71C1C.toInt())
            setPadding(48, 48, 48, 48)
        }

        val emoji = android.widget.TextView(this).apply {
            text = "🚨"
            textSize = 72f
            gravity = android.view.Gravity.CENTER
        }
        val title = android.widget.TextView(this).apply {
            text = "工卡未检测到！"
            textSize = 34f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = android.view.Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val subtitle = android.widget.TextView(this).apply {
            text = "你似乎忘带工卡了\n请确认卡片是否在身上"
            textSize = 20f
            setTextColor(0xFFFFCCCC.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(0, 32, 0, 48)
        }

        val btnOk = android.widget.Button(this).apply {
            text = "我知道了（停止报警）"
            textSize = 18f
            setOnClickListener {
                Prefs.markPresentToday(this@AlarmActivity) // 已确认，今天不再打扰
                stopServiceAndFinish()
            }
        }
        val btnLeave = android.widget.Button(this).apply {
            text = "今天请假"
            textSize = 18f
            setOnClickListener {
                Prefs.markLeaveToday(this@AlarmActivity)   // 请假，今天完全不打扰
                stopServiceAndFinish()
            }
        }

        root.addView(emoji)
        root.addView(title)
        root.addView(subtitle)
        root.addView(btnOk)
        root.addView(btnLeave)
        setContentView(root)
    }

    private fun stopServiceAndFinish() {
        // 通知前台服务停掉报警（声音/振动/唤醒锁）
        val intent = android.content.Intent(this, GkaForegroundService::class.java).apply {
            action = "STOP_ALERT"
        }
        startService(intent)
        finish()
    }

    // 拦截返回键：不允许直接划掉/返回逃避报警
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 空实现 = 返回键无效，必须点按钮
    }
}
