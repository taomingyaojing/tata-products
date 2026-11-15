package com.legalcall.assistant.monitor

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import com.legalcall.assistant.block.BlockList
import com.legalcall.assistant.block.CallBlockService
import com.legalcall.assistant.engine.AudioEngine

/**
 * 通话监听：检测到接通（OFFHOOK）后，若 VIP 且开启自动播报，则自动外放话术。
 *
 * 重要：本组件只负责"接听后"的自动播报，不参与自动接听。
 * 自动接听是系统特权（仅默认拨号器可用），第三方应用无法干净实现。
 */
class CallMonitor(private val context: Context) {

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private val handler = Handler(Looper.getMainLooper())

    private var active = false
    private var autoEngage = false
    private var dialogueMode = false
    private var blockEnabled = false
    private var currentIncoming = ""
    private var scriptQueue: List<String> = emptyList()
    private var activeQueue: List<String> = emptyList()
    private var queueIndex = 0

    private val playNextRunnable = object : Runnable {
        override fun run() {
            if (queueIndex < activeQueue.size) {
                AudioEngine.getInstance(context).speak(activeQueue[queueIndex], useEarpiece = false)
                queueIndex++
                handler.postDelayed(this, if (dialogueMode) DIALOGUE_INTERVAL_MS else SCRIPT_INTERVAL_MS)
            }
        }
    }

    @Suppress("DEPRECATION")
    private val listener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> currentIncoming = phoneNumber ?: ""
                TelephonyManager.CALL_STATE_OFFHOOK -> onAnswered()
                TelephonyManager.CALL_STATE_IDLE -> onIdle()
                else -> Unit
            }
        }
    }

    fun setAutoEngage(enabled: Boolean) {
        autoEngage = enabled
    }

    fun setDialogueMode(enabled: Boolean) {
        dialogueMode = enabled
    }

    fun setBlockEnabled(enabled: Boolean) {
        blockEnabled = enabled
    }

    fun setScriptQueue(scripts: List<String>) {
        scriptQueue = scripts
        queueIndex = 0
    }

    private fun onAnswered() {
        // 空号拦截：黑名单来电 → 播放空号音 + 延迟自动挂断
        if (blockEnabled && BlockList.contains(context, currentIncoming)) {
            AudioEngine.getInstance(context).speak(EMPTY_NUMBER_AUDIO, useEarpiece = false)
            handler.postDelayed({ CallBlockService.requestHangup() }, EMPTY_NUMBER_HANGUP_DELAY_MS)
            currentIncoming = ""
            return
        }
        if (autoEngage) {
            activeQueue = if (dialogueMode) DIALOGUE_SCRIPT else scriptQueue
            if (activeQueue.isNotEmpty()) {
                queueIndex = 0
                handler.removeCallbacks(playNextRunnable)
                handler.post(playNextRunnable)
            }
        }
    }

    private fun onIdle() {
        handler.removeCallbacks(playNextRunnable)
        queueIndex = 0
        currentIncoming = ""
        AudioEngine.getInstance(context).stop()
    }

    @Suppress("DEPRECATION")
    fun start() {
        if (active) return
        try {
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            active = true
        } catch (e: Exception) {
            active = false
        }
    }

    @Suppress("DEPRECATION")
    fun stop() {
        if (!active) return
        try {
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE)
        } catch (e: Exception) {
            // ignore
        }
        active = false
    }

    companion object {
        private const val SCRIPT_INTERVAL_MS = 8000L
        private const val DIALOGUE_INTERVAL_MS = 6000L
        private const val EMPTY_NUMBER_AUDIO = "您好，您拨打的电话是空号，请查证后再拨。Sorry, the number you dialed does not exist."
        private const val EMPTY_NUMBER_HANGUP_DELAY_MS = 4000L

        // 对答式话术：接听后“你一句我一句”的降级版（固定间隔轮播）
        private val DIALOGUE_SCRIPT = listOf(
            "喂，您好，请问哪位？",
            "哦……这样啊。我现在不太方便讲话，您把具体事项发短信或者邮箱给我吧。",
            "嗯，好的，那我先挂了，回头联系，谢谢。"
        )
    }
}
