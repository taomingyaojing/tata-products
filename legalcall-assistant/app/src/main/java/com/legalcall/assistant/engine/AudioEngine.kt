package com.legalcall.assistant.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 语音播报核心。
 *
 * 说明：话术是文本，因此用系统 TTS（离线可用）直接朗读，无需打包音频文件。
 * 这比 MediaPlayer 更省体积、更可靠，同时仍可控制听筒/外放通道。
 */
class AudioEngine private constructor(private val context: Context) :
    TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<String, Boolean>? = null

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            tts = null
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            tts?.language = Locale.CHINA
            pending?.let { (text, earpiece) -> speak(text, earpiece) }
            pending = null
        }
    }

    /** 是否连接了耳机（有线/蓝牙），用于"防尴尬外放"提示 */
    fun isHeadphoneConnected(): Boolean {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                }
            } else {
                @Suppress("DEPRECATION")
                am.isWiredHeadsetOn
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 播报一段话术。
     * @param useEarpiece true = 听筒（试听模式，防尴尬）；false = 外放（自动播报模式）
     */
    fun speak(text: String, useEarpiece: Boolean) {
        if (!ready) {
            pending = text to useEarpiece
            return
        }
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(
                    if (useEarpiece) AudioAttributes.USAGE_VOICE_COMMUNICATION
                    else AudioAttributes.USAGE_MEDIA
                )
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(attrs)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "script")
        } catch (e: Exception) {
            // 播报失败时静默处理
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            // ignore
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            // ignore
        }
        tts = null
    }

    companion object {
        @Volatile
        private var instance: AudioEngine? = null

        fun getInstance(context: Context): AudioEngine =
            instance ?: synchronized(this) {
                instance ?: AudioEngine(context.applicationContext).also { instance = it }
            }
    }
}
