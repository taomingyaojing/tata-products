package com.gongka.guardian.alert

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.gongka.guardian.R
import com.gongka.guardian.core.Const

/**
 * 【第三道防线】强力阻断式报警
 *
 * 忘带工卡 = 高危突发事件，报警必须"足够强势"：
 *  1. 强振动：持续震动，直到用户处理
 *  2. 强音量：无视静音/振动模式，直接把媒体音量拉到最大并播放报警音
 *     （静音开关通常只管 RING/NOTIFICATION 流，STREAM_MUSIC 可被程序强制拉起）
 *  3. 全屏界面：锁屏/后台也能顶起 AlarmActivity
 *  4. 屏幕点亮 + 唤醒锁：确保用户能看见
 */
class AlertManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val vibrator: Vibrator = getVibrator(context)
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private var mediaPlayer: MediaPlayer? = null

    /** 是否正在报警 */
    @Volatile private var alerting = false

    /** 唤醒锁：报警期间保持屏幕点亮 */
    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * 启动强力报警
     */
    fun launchStrongAlert() {
        if (alerting) return
        alerting = true

        startLoudSound()
        startVibration()
        startFullScreenActivity()
        acquireWakeLock()
    }

    /** 停止报警（用户点了"我知道了"/"今天请假"，或已带卡） */
    fun stop() {
        if (!alerting) return
        alerting = false

        stopSound()
        stopVibration()
        releaseWakeLock()
    }

    // ================= 声音 =================

    private fun startLoudSound() {
        // 关键：强制拉起 STREAM_MUSIC 音量到最大，绕过静音/振动模式
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)

        try {
            mediaPlayer = MediaPlayer.create(context, R.raw.alarm).apply {
                isLooping = true                        // 循环播放，直到停止
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                start()
            }
        } catch (e: Exception) {
            // 兜底：资源缺失时用系统默认报警音 + STREAM_ALARM 全音量
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        }
    }

    private fun stopSound() {
        mediaPlayer?.runCatching {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }

    // ================= 振动 =================

    private fun startVibration() {
        // 连续强振动：pattern 无限循环（0 立即开始，之后 500ms 振 / 300ms 停）
        val pattern = longArrayOf(0, 500, 300)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(pattern, 0) // repeat=0 表示无限循环
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0)
        }
    }

    private fun stopVibration() {
        vibrator.cancel()
    }

    // ================= 全屏界面 =================

    private fun startFullScreenActivity() {
        val intent = Intent(context, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            // 即使 App 在后台/锁屏，也要顶起
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
        context.startActivity(intent)
    }

    // ================= 唤醒锁 =================

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "gongka:alarm"
            )
        }
        wakeLock?.acquire(10 * 60 * 1000L) // 最长 10 分钟，防止忘关
    }

    private fun releaseWakeLock() {
        wakeLock?.runCatching { if (isHeld) release() }
        wakeLock = null
    }

    // ================= 工具 =================

    @Suppress("DEPRECATION")
    private fun getVibrator(ctx: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
}
