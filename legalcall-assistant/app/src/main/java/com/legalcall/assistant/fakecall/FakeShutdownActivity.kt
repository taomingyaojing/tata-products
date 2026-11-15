package com.legalcall.assistant.fakecall

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 物理脱身：高仿「低电量自动关机」或「系统卡死」画面。
 * 拦截返回键、触摸无响应；连按音量键 3 次安全退出。
 */
class FakeShutdownActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_LOW_BATTERY = "low_battery"
        const val MODE_CRASH = "crash"
    }

    private var volumePresses = 0
    private var mode = MODE_LOW_BATTERY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_LOW_BATTERY
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                if (mode == MODE_LOW_BATTERY) LowBatteryScreen() else CrashScreen()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 吞掉返回键，不退出（演戏）
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            volumePresses++
            if (volumePresses >= 3) {
                finish()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Composable
    private fun LowBatteryScreen() {
        var progress by remember { mutableIntStateOf(100) }
        LaunchedEffect(Unit) {
            while (progress > 0) {
                delay(300)
                progress -= 1
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔋", fontSize = 80.sp)
                Spacer(Modifier.height(20.dp))
                Text("电量不足", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDDDDDD))
                Spacer(Modifier.height(8.dp))
                Text("$progress%", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
                Spacer(Modifier.height(24.dp))
                Text("正在关机…", fontSize = 16.sp, color = Color(0xFF888888))
                Spacer(Modifier.height(40.dp))
                Text("（连按音量键 3 次退出）", fontSize = 12.sp, color = Color(0xFF444444))
            }
        }
    }

    @Composable
    private fun CrashScreen() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0A)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⚠️", fontSize = 60.sp)
                Spacer(Modifier.height(16.dp))
                Text("系统界面无响应", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDDDDDD))
                Spacer(Modifier.height(12.dp))
                Text("应用已停止运行", fontSize = 15.sp, color = Color(0xFF888888))
                Spacer(Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1A1A1A), RoundedCornerShape(8.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text("等待或关闭", fontSize = 14.sp, color = Color(0xFFAAAAAA))
                }
                Spacer(Modifier.height(40.dp))
                Text("（连按音量键 3 次退出）", fontSize = 12.sp, color = Color(0xFF444444))
            }
        }
    }
}
