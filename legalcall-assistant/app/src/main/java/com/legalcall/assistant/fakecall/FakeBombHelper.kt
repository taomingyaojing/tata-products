package com.legalcall.assistant.fakecall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.legalcall.assistant.MainActivity

/**
 * 假消息轰炸：连发多条自定义通知，模拟「工作消息连环催」。
 * 弱化版：不冒充钉钉/微信（来源仍是本 App），用工作化标题+文案制造紧迫感。
 */
object FakeBombHelper {

    data class BombMsg(val title: String, val content: String)

    val PRESETS = listOf(
        listOf(
            BombMsg("张总", "小王，线上出问题了，看到立刻回我"),
            BombMsg("项目群", "紧急：服务异常，相关同学速速上线"),
            BombMsg("张总", "电话打不通，看到马上处理，客户在等")
        ),
        listOf(
            BombMsg("李姐（客户）", "在吗？合同有个地方要改，急"),
            BombMsg("李姐（客户）", "领导催了，方便接个电话吗"),
            BombMsg("李姐（客户）", "收到回一下，真的挺急的")
        ),
        listOf(
            BombMsg("老妈", "儿子，看到回个电话"),
            BombMsg("老妈", "家里有事，赶紧回一下"),
            BombMsg("老妈", "你怎么不接电话？")
        )
    )

    private const val CHANNEL_ID = "fake_bomb"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "消息提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "模拟消息轰炸"
                }
            )
        }
    }

    /** 连发一组通知，每条约间隔 3 秒 */
    fun bomb(context: Context, presetIndex: Int) {
        ensureChannel(context)
        val msgs = PRESETS[presetIndex % PRESETS.size]
        val handler = Handler(Looper.getMainLooper())
        msgs.forEachIndexed { i, msg ->
            handler.postDelayed({ send(context, msg, i) }, i * 3000L)
        }
    }

    private fun send(context: Context, msg: BombMsg, id: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pi = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(msg.title)
                .setContentText(msg.content)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(msg.title)
                .setContentText(msg.content)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
        }
        try {
            nm.notify(1000 + id, notif)
        } catch (e: Exception) {
            // ignore
        }
    }
}
