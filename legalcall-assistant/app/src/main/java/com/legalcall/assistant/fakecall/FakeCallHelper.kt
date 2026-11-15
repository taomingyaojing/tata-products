package com.legalcall.assistant.fakecall

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/** 假来电触发工具 */
object FakeCallHelper {

    private const val PREFS = "fakecall_prefs"
    private const val KEY_PERSONA = "persona_id"

    fun savePersona(context: Context, id: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PERSONA, id).apply()
    }

    fun lastPersona(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PERSONA, "courier") ?: "courier"
    }

    /**
     * 启动假来电。
     * @param delaySeconds 倒计时（秒），0 = 立即
     */
    fun launch(context: Context, personaId: String, delaySeconds: Int) {
        val persona = Personas.byId(personaId)
        val intent = Intent(context, FakeCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("persona_id", persona.id)
            putExtra("persona_name", persona.name)
            putExtra("persona_number", persona.number)
            putExtra("persona_avatar", persona.avatar)
            putExtra("voice_line", persona.voiceLine)
        }
        if (delaySeconds > 0) {
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // ignore
                }
            }, delaySeconds * 1000L)
        } else {
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
