package com.legalcall.assistant.fakecall

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** 定时脱身：用 AlarmManager 在指定分钟后触发假来电 */
object ScheduleHelper {

    fun schedule(context: Context, personaId: String, delayMinutes: Int) {
        val persona = Personas.byId(personaId)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("persona_id", persona.id)
            putExtra("persona_name", persona.name)
            putExtra("persona_number", persona.number)
            putExtra("persona_avatar", persona.avatar)
            putExtra("voice_line", persona.voiceLine)
        }
        val pi = PendingIntent.getBroadcast(
            context, 1001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + delayMinutes * 60_000L
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } catch (e: Exception) {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }
}
