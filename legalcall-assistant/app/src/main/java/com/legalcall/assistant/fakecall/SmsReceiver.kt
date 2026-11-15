package com.legalcall.assistant.fakecall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * 短信暗号触发：信任号码发来含暗号的短信 → 30 秒后触发假来电。
 * 只本地匹配「号码 + 暗号」，绝不读取/上传短信内容。
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!SmsTrigger.isEnabled(context)) return

        val trusted = SmsTrigger.trustedNumber(context)
        val keyword = SmsTrigger.keyword(context)
        if (trusted.isBlank() || keyword.isBlank()) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (msg in messages) {
            val from = msg.displayOriginatingAddress ?: ""
            val body = msg.messageBody ?: ""
            if (from.contains(trusted) && body.contains(keyword)) {
                val persona = SmsTrigger.persona(context)
                FakeCallHelper.launch(context, persona, 30)
                return
            }
        }
    }
}

/** 短信暗号触发的设置存取 */
object SmsTrigger {
    private const val PREFS = "sms_trigger"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TRUSTED = "trusted_number"
    private const val KEY_KEYWORD = "keyword"
    private const val KEY_PERSONA = "persona"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun trustedNumber(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TRUSTED, "") ?: ""

    fun keyword(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_KEYWORD, "111") ?: "111"

    fun persona(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PERSONA, "boss") ?: "boss"

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun save(context: Context, trusted: String, keyword: String, persona: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TRUSTED, trusted.trim())
            .putString(KEY_KEYWORD, keyword.trim())
            .putString(KEY_PERSONA, persona)
            .apply()
    }
}
