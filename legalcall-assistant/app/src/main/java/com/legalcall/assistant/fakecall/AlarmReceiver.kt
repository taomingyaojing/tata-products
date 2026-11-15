package com.legalcall.assistant.fakecall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 定时脱身到点触发：拉起假来电界面 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val fakeIntent = Intent(context, FakeCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("persona_id", intent.getStringExtra("persona_id") ?: "")
            putExtra("persona_name", intent.getStringExtra("persona_name") ?: "老板")
            putExtra("persona_number", intent.getStringExtra("persona_number") ?: "")
            putExtra("persona_avatar", intent.getStringExtra("persona_avatar") ?: "💼")
            putExtra("voice_line", intent.getStringExtra("voice_line") ?: "")
        }
        try {
            context.startActivity(fakeIntent)
        } catch (e: Exception) {
            // ignore
        }
    }
}
