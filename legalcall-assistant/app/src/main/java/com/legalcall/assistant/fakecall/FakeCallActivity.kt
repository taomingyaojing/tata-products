package com.legalcall.assistant.fakecall

import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legalcall.assistant.engine.AudioEngine
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 假来电界面：来电界面 →（接听）→ 通话中界面。
 * 锁屏上也会弹出（setShowWhenLocked + setTurnScreenOn）。
 */
class FakeCallActivity : ComponentActivity() {

    private var ringtonePlayer: MediaPlayer? = null
    private var persona: Persona? = null
    private var scriptLines: List<ScriptLine> = emptyList()

    private val inCall = mutableStateOf(false)
    private val callSeconds = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        parseIntent()
        startRingtone()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                FakeCallScreen()
            }
        }
    }

    private fun showOverLockScreen() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun parseIntent() {
        val id = intent.getStringExtra("persona_id") ?: ""
        val name = intent.getStringExtra("persona_name") ?: "老板"
        val number = intent.getStringExtra("persona_number") ?: ""
        val avatar = intent.getStringExtra("persona_avatar") ?: "💼"
        val voiceLine = intent.getStringExtra("voice_line") ?: ""
        persona = Persona("", name, number, avatar, voiceLine)
        scriptLines = Scripts.byId(id)
    }

    private fun startRingtone() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtonePlayer = MediaPlayer.create(this, uri)?.apply {
                isLooping = true
                start()
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun stopRingtone() {
        try {
            ringtonePlayer?.stop()
            ringtonePlayer?.release()
        } catch (e: Exception) {
            // ignore
        }
        ringtonePlayer = null
    }

    private fun onAnswer() {
        stopRingtone()
        inCall.value = true
        if (scriptLines.isNotEmpty()) {
            lifecycleScope.launch {
                for (line in scriptLines) {
                    AudioEngine.getInstance(this@FakeCallActivity).speak(line.text, useEarpiece = true)
                    delay(line.gapSeconds * 1000L)
                }
            }
        } else {
            persona?.let { p ->
                if (p.voiceLine.isNotBlank()) {
                    AudioEngine.getInstance(this).speak(p.voiceLine, useEarpiece = true)
                }
            }
        }
    }

    private fun onHangup() {
        stopRingtone()
        AudioEngine.getInstance(this).stop()
        finish()
    }

    override fun onDestroy() {
        stopRingtone()
        super.onDestroy()
    }

    @Composable
    private fun FakeCallScreen() {
        val p = persona ?: return

        LaunchedEffect(Unit) {
            while (true) {
                delay(1000)
                if (inCall.value) callSeconds.value += 1
            }
        }

        val bg = Brush.verticalGradient(
            listOf(Color(0xFF1A1A2E), Color(0xFF0F0F1A))
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(Color(0xFF333350), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(p.avatar, fontSize = 48.sp)
                }
                Spacer(Modifier.height(20.dp))
                Text(p.name, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(p.number, fontSize = 16.sp, color = Color(0xFFAAAAAA))

                Spacer(Modifier.height(24.dp))

                if (!inCall.value) {
                    Text("来电", fontSize = 14.sp, color = Color(0xFF7CE8A0))
                    Spacer(Modifier.height(60.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                        ActionButton("挂断", Color(0xFFE53935), ::onHangup)
                        ActionButton("接听", Color(0xFF43A047), ::onAnswer)
                    }
                } else {
                    Text(
                        formatSeconds(callSeconds.value),
                        fontSize = 22.sp,
                        color = Color(0xFFDDDDDD)
                    )
                    Spacer(Modifier.height(60.dp))
                    ActionButton("挂断", Color(0xFFE53935), ::onHangup)
                }
            }
        }
    }

    @Composable
    private fun ActionButton(label: String, color: Color, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(color, CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }

    private fun formatSeconds(s: Int): String {
        val m = s / 60
        val sec = s % 60
        return "通话中 %02d:%02d".format(m, sec)
    }
}
