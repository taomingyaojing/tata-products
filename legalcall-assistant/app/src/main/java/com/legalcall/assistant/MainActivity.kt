package com.legalcall.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.legalcall.assistant.R
import com.legalcall.assistant.block.BlockList
import com.legalcall.assistant.engine.AudioEngine
import com.legalcall.assistant.fakecall.FakeBombHelper
import com.legalcall.assistant.fakecall.FakeCallHelper
import com.legalcall.assistant.fakecall.FakeShutdownActivity
import com.legalcall.assistant.fakecall.FloatingBallService
import com.legalcall.assistant.fakecall.Personas
import com.legalcall.assistant.fakecall.ScheduleHelper
import com.legalcall.assistant.fakecall.SmsTrigger
import com.legalcall.assistant.monitor.CallMonitor
import com.legalcall.assistant.util.CryptoUtil
import com.legalcall.assistant.util.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray

class MainActivity : ComponentActivity() {

    data class Script(val id: String, val title: String, val text: String)

    private enum class Page { HOME, ANSWER, BLOCK, ESCAPE, TRICKS }

    private lateinit var callMonitor: CallMonitor
    private val scripts = mutableListOf<Script>()

    // 统计上报服务器地址（匿名，不碰隐私）
    private val SERVER_URL = "http://49.233.207.110:8981"

    private val machineCode by lazy { CryptoUtil.getMachineCode(this) }

    private val appVersion by lazy {
        try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            callMonitor.start()
            Toast.makeText(this, "已授权，接听电话后将可自动播报", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "未授权通话监听，自动播报功能不可用", Toast.LENGTH_SHORT).show()
        }
    }

    private var pendingBombPreset = 0

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            FakeBombHelper.bomb(this, pendingBombPreset)
            report("假消息轰炸")
        } else {
            Toast.makeText(this, "未授权通知，无法模拟消息轰炸", Toast.LENGTH_SHORT).show()
        }
    }

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            SmsTrigger.setEnabled(this, true)
            Toast.makeText(this, "短信暗号已启用", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "未授权短信，暗号触发不可用", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadScripts()

        callMonitor = CallMonitor(this)
        callMonitor.setScriptQueue(scripts.map { it.text })
        requestPhoneStatePermission()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MainScreen()
            }
        }

        // 启动上报（匿名）
        report("launch")
    }

    override fun onDestroy() {
        callMonitor.stop()
        AudioEngine.getInstance(this).shutdown()
        super.onDestroy()
    }

    private fun requestPhoneStatePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            callMonitor.start()
        } else {
            permissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        }
    }

    private fun loadScripts() {
        try {
            val raw = assets.open("scripts.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                scripts.add(Script(o.getString("id"), o.getString("title"), o.getString("text")))
            }
        } catch (e: Exception) {
            scripts.clear()
        }
    }

    /** 匿名上报一个使用事件（静默，失败不影响功能） */
    private fun report(event: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            Network.reportEvent(SERVER_URL, machineCode, event, appVersion)
        }
    }

    private fun triggerEscape(personaId: String, countdown: Int) {
        FakeCallHelper.launch(this, personaId, countdown)
        report("立即脱身")
        if (countdown > 0) {
            Toast.makeText(this, "已设置，$countdown 秒后弹出脱身来电", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setFloatingBall(enabled: Boolean) {
        if (enabled) {
            if (Settings.canDrawOverlays(this)) {
                try {
                    if (Build.VERSION.SDK_INT >= 26) {
                        startForegroundService(Intent(this, FloatingBallService::class.java))
                    } else {
                        startService(Intent(this, FloatingBallService::class.java))
                    }
                    report("悬浮球脱身")
                } catch (e: Exception) {
                    // ignore
                }
            } else {
                Toast.makeText(this, "请先授权悬浮窗权限", Toast.LENGTH_LONG).show()
                try {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    )
                } catch (e: Exception) {
                    // ignore
                }
            }
        } else {
            stopService(Intent(this, FloatingBallService::class.java))
        }
    }

    private fun scheduleEscape(personaId: String, minutes: Int) {
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.canUseFullScreenIntent()) {
                Toast.makeText(this, "请授权「全屏通知」权限", Toast.LENGTH_LONG).show()
                try {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
                    )
                } catch (e: Exception) {
                    // ignore
                }
                return
            }
        }
        ScheduleHelper.schedule(this, personaId, minutes)
        report("定时脱身")
        Toast.makeText(this, "已设置 $minutes 分钟后弹出脱身来电", Toast.LENGTH_SHORT).show()
    }

    private fun triggerBomb(presetIndex: Int) {
        pendingBombPreset = presetIndex
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            FakeBombHelper.bomb(this, presetIndex)
            report("假消息轰炸")
        }
    }

    private fun triggerShutdown(mode: String) {
        startActivity(
            Intent(this, FakeShutdownActivity::class.java).apply {
                putExtra(FakeShutdownActivity.EXTRA_MODE, mode)
            }
        )
        report("物理脱身")
    }

    private fun toggleSmsTrigger(enabled: Boolean) {
        if (enabled) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
            } else {
                SmsTrigger.setEnabled(this, true)
                Toast.makeText(this, "短信暗号已启用", Toast.LENGTH_SHORT).show()
            }
        } else {
            SmsTrigger.setEnabled(this, false)
            Toast.makeText(this, "短信暗号已关闭", Toast.LENGTH_SHORT).show()
        }
        report("短信暗号")
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "请手动到「设置→无障碍」开启", Toast.LENGTH_LONG).show()
        }
    }

    private fun isAccessibilityOn(): Boolean {
        return try {
            val enabled = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            enabled.contains(packageName)
        } catch (e: Exception) {
            false
        }
    }

    @Composable
    private fun MainScreen() {
        val page = remember { mutableStateOf(Page.HOME) }
        val autoEngage = remember { mutableStateOf(false) }
        val dialogueMode = remember { mutableStateOf(false) }
        val blockEnabled = remember { mutableStateOf(false) }
        val blacklist = remember { mutableStateOf(BlockList.numbers(this@MainActivity)) }

        LaunchedEffect(autoEngage.value) {
            callMonitor.setAutoEngage(autoEngage.value)
        }
        LaunchedEffect(dialogueMode.value) {
            callMonitor.setDialogueMode(dialogueMode.value)
        }
        LaunchedEffect(blockEnabled.value) {
            callMonitor.setBlockEnabled(blockEnabled.value)
        }

        val selectedPersona = remember { mutableStateOf("courier") }
        val escapeCountdown = remember { mutableStateOf(0) }
        val floatBallOn = remember { mutableStateOf(FloatingBallService.isRunning()) }
        val smsEnabled = remember { mutableStateOf(SmsTrigger.isEnabled(this@MainActivity)) }

        when (page.value) {
            Page.HOME -> HomeScreen(onOpen = { page.value = it })
            Page.ANSWER -> DetailPage("📞 接听自动处理", "接听骚扰电话后的自动应对", onBack = { page.value = Page.HOME }) {
                ToggleRow("接通后自动播报", "手动接听后自动外放话术", autoEngage.value) { autoEngage.value = it }
                Spacer(Modifier.height(14.dp))
                ToggleRow("对答模式", "分句对答，模拟真人语气", dialogueMode.value) { dialogueMode.value = it }
            }
            Page.BLOCK -> DetailPage("📵 空号拦截", "黑名单来电 → 自动播空号音 + 挂断，让电销系统洗号", onBack = { page.value = Page.HOME }) {
                BlockSection(
                    blockEnabled = blockEnabled.value,
                    onToggle = { blockEnabled.value = it },
                    onOpenAccessibility = { openAccessibilitySettings() },
                    numbers = blacklist.value,
                    onAdd = { n ->
                        BlockList.add(this@MainActivity, n)
                        blacklist.value = BlockList.numbers(this@MainActivity)
                    },
                    onRemove = { n ->
                        BlockList.remove(this@MainActivity, n)
                        blacklist.value = BlockList.numbers(this@MainActivity)
                    }
                )
            }
            Page.ESCAPE -> DetailPage("🆘 一键脱身", "弹出一通假来电，帮你体面脱身", onBack = { page.value = Page.HOME }) {
                EscapeSection(
                    selectedPersona = selectedPersona.value,
                    onSelectPersona = { selectedPersona.value = it; FakeCallHelper.savePersona(this@MainActivity, it) },
                    escapeCountdown = escapeCountdown.value,
                    onCountdownChange = { escapeCountdown.value = it },
                    onEscape = { triggerEscape(selectedPersona.value, escapeCountdown.value) },
                    floatBallOn = floatBallOn.value,
                    onFloatBallToggle = {
                        floatBallOn.value = it
                        setFloatingBall(it)
                    },
                    onSchedule = { minutes -> scheduleEscape(selectedPersona.value, minutes) }
                )
            }
            Page.TRICKS -> DetailPage("🎭 神操作", "脱身 · 反骚扰的进阶玩法", onBack = { page.value = Page.HOME }) {
                MoreTricksSection(
                    onBomb = { triggerBomb(it) },
                    onShutdown = { triggerShutdown(it) },
                    smsEnabled = smsEnabled.value,
                    onSmsToggle = { toggleSmsTrigger(it) },
                    onSmsSave = { trusted, keyword ->
                        SmsTrigger.save(this@MainActivity, trusted, keyword, selectedPersona.value)
                        Toast.makeText(this@MainActivity, "暗号设置已保存", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    @Composable
    private fun HomeScreen(onOpen: (Page) -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Text("獭獭救场", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "假来电 · 反骚扰 · 体面开溜",
                fontSize = 13.sp,
                color = Color(0xFF9A9AAB)
            )
            Spacer(Modifier.height(20.dp))

            HomeEntry("📞", "接听自动处理", "接听骚扰电话后的自动应对") { onOpen(Page.ANSWER) }
            Spacer(Modifier.height(10.dp))
            HomeEntry("📵", "空号拦截", "黑名单来电 → 自动播空号音 + 挂断") { onOpen(Page.BLOCK) }
            Spacer(Modifier.height(10.dp))
            HomeEntry("🆘", "一键脱身", "弹出一通假来电，帮你体面脱身") { onOpen(Page.ESCAPE) }
            Spacer(Modifier.height(10.dp))
            HomeEntry("🎭", "神操作", "假消息轰炸 · 物理脱身 · 短信暗号") { onOpen(Page.TRICKS) }
            Spacer(Modifier.height(20.dp))

            DonateCard(onDonate = { report("打赏") })
            Spacer(Modifier.height(20.dp))
        }
    }

    @Composable
    private fun HomeEntry(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A26)),
            border = BorderStroke(1.dp, Color(0xFF2A2A3C))
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(icon, fontSize = 28.sp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEDEDF5))
                    Spacer(Modifier.height(3.dp))
                    Text(subtitle, fontSize = 12.sp, color = Color(0xFF8A8A9A))
                }
                Text("›", fontSize = 24.sp, color = Color(0xFF555566))
            }
        }
    }

    @Composable
    private fun DetailPage(
        title: String,
        subtitle: String? = null,
        onBack: () -> Unit,
        content: @Composable ColumnScope.() -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Text(
                "‹ 返回",
                fontSize = 15.sp,
                color = Color(0xFF7CE8A0),
                modifier = Modifier.clickable { onBack() }
            )
            Spacer(Modifier.height(16.dp))
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(subtitle, fontSize = 13.sp, color = Color(0xFF9A9AAB))
            }
            Spacer(Modifier.height(20.dp))
            content()
            Spacer(Modifier.height(20.dp))
        }
    }

    @Composable
    private fun ToggleRow(
        title: String,
        subtitle: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE6E6EE))
                Text(subtitle, fontSize = 12.sp, color = Color(0xFF8A8A9A))
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }

    @Composable
    private fun DonateCard(onDonate: () -> Unit) {
        val showDialog = remember { mutableStateOf(false) }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2A4A))
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("喜欢这个 App？", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "完全免费，如果它帮到了你，欢迎请我喝杯咖啡 ☕",
                    fontSize = 13.sp,
                    color = Color(0xFFBBBBBB)
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        onDonate()
                        showDialog.value = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("☕ 自愿打赏", fontSize = 15.sp)
                }
            }
        }

        if (showDialog.value) {
            AlertDialog(
                onDismissRequest = { showDialog.value = false },
                title = { Text("感谢支持") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "扫码打赏，金额随意 🦦",
                            fontSize = 14.sp,
                            color = Color(0xFFCCCCCC)
                        )
                        Spacer(Modifier.height(12.dp))
                        Image(
                            painter = painterResource(R.drawable.pay_qr),
                            contentDescription = "收款码",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.73f)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDialog.value = false }) {
                        Text("关闭")
                    }
                }
            )
        }
    }

    @Composable
    private fun BlockSection(
        blockEnabled: Boolean,
        onToggle: (Boolean) -> Unit,
        onOpenAccessibility: () -> Unit,
        numbers: List<String>,
        onAdd: (String) -> Unit,
        onRemove: (String) -> Unit
    ) {
        val accessibilityOn = remember { mutableStateOf(isAccessibilityOn()) }
        LaunchedEffect(Unit) { accessibilityOn.value = isAccessibilityOn() }

        // 第 1 步：无障碍状态
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (accessibilityOn.value) Color(0xFF16321F) else Color(0xFF3A2A1B),
                    RoundedCornerShape(12.dp)
                )
                .padding(14.dp)
        ) {
            Text(if (accessibilityOn.value) "✅" else "⚠️", fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (accessibilityOn.value) "无障碍服务已开启" else "第 1 步 · 开启无障碍服务",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (accessibilityOn.value) Color(0xFF7CE8A0) else Color(0xFFFFB37C)
                )
                Text(
                    if (accessibilityOn.value) "黑名单来电会自动播空号音并挂断" else "没开启就没法自动挂断，点右边按钮去开",
                    fontSize = 12.sp,
                    color = Color(0xFFAAAAAA)
                )
            }
            if (!accessibilityOn.value) {
                Button(onClick = { onOpenAccessibility() }) {
                    Text("去开启", fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // 总开关
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.weight(1f)) {
                Text("空号拦截开关", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("打开后，黑名单来电自动处理", fontSize = 12.sp, color = Color(0xFFAAAAAA))
            }
            Switch(checked = blockEnabled, onCheckedChange = onToggle)
        }
        Spacer(Modifier.height(16.dp))

        // 第 2 步：黑名单
        Text("第 2 步 · 添加骚扰号码", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (numbers.isEmpty()) {
            Text("还没添加，下面输入号码点「添加」", fontSize = 13.sp, color = Color(0xFF666666))
            Spacer(Modifier.height(8.dp))
        } else {
            numbers.forEach { n ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1F1F1F), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(n, fontSize = 14.sp, color = Color(0xFFDDDDDD), modifier = Modifier.weight(1f))
                    Text("✕", fontSize = 16.sp, color = Color(0xFFE53935), modifier = Modifier.clickable { onRemove(n) })
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        val input = remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input.value,
                onValueChange = { input.value = it },
                placeholder = { Text("输入骚扰号码，如 4001234567", fontSize = 14.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (input.value.isNotBlank()) {
                    onAdd(input.value)
                    input.value = ""
                }
            }) {
                Text("添加", fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(14.dp))

        // 使用说明
        Text(
            "💡 三步搞定：①开启无障碍 → ②加黑名单 → ③打开开关。之后黑名单号码打进来，会自动播「您拨打的电话是空号」并挂断。",
            fontSize = 12.sp,
            color = Color(0xFF888899),
            lineHeight = 18.sp
        )
    }

    @Composable
    private fun MoreTricksSection(
        onBomb: (Int) -> Unit,
        onShutdown: (String) -> Unit,
        smsEnabled: Boolean,
        onSmsToggle: (Boolean) -> Unit,
        onSmsSave: (String, String) -> Unit
    ) {
        Text("💣 假消息轰炸", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text("连发几条「工作消息」通知，眉头一皱起身离开", fontSize = 12.sp, color = Color(0xFFAAAAAA))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("领导催" to 0, "客户催" to 1, "家人催" to 2).forEach { (label, idx) ->
                Surface(
                    onClick = { onBomb(idx) },
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF2A2A2A)
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Text("🔋 物理脱身", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text("高仿低电量关机/系统卡死，连按音量键 3 次退出", fontSize = 12.sp, color = Color(0xFFAAAAAA))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                onClick = { onShutdown(FakeShutdownActivity.MODE_LOW_BATTERY) },
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF2A2A2A)
            ) {
                Text("低电量关机", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 13.sp, color = Color.White)
            }
            Surface(
                onClick = { onShutdown(FakeShutdownActivity.MODE_CRASH) },
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF2A2A2A)
            ) {
                Text("系统卡死", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 13.sp, color = Color.White)
            }
        }
        Spacer(Modifier.height(16.dp))

        Text("📩 短信暗号触发", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text("信任号码发暗号 → 30 秒后自动来电", fontSize = 12.sp, color = Color(0xFFAAAAAA))
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.weight(1f)) {
                Text("启用短信暗号", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("需授权短信权限，仅本地匹配不上传", fontSize = 12.sp, color = Color(0xFFAAAAAA))
            }
            Switch(checked = smsEnabled, onCheckedChange = onSmsToggle)
        }
        if (smsEnabled) {
            Spacer(Modifier.height(8.dp))
            val trusted = remember { mutableStateOf(SmsTrigger.trustedNumber(this@MainActivity)) }
            val keyword = remember { mutableStateOf(SmsTrigger.keyword(this@MainActivity)) }
            OutlinedTextField(
                value = trusted.value,
                onValueChange = { trusted.value = it },
                placeholder = { Text("信任号码（如 13800138000）", fontSize = 14.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = keyword.value,
                onValueChange = { keyword.value = it },
                placeholder = { Text("暗号（如 111）", fontSize = 14.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onSmsSave(trusted.value, keyword.value) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存暗号设置", fontSize = 14.sp)
            }
        }
    }

    @Composable
    private fun EscapeSection(
        selectedPersona: String,
        onSelectPersona: (String) -> Unit,
        escapeCountdown: Int,
        onCountdownChange: (Int) -> Unit,
        onEscape: () -> Unit,
        floatBallOn: Boolean,
        onFloatBallToggle: (Boolean) -> Unit,
        onSchedule: (Int) -> Unit
    ) {
        Text("来电人设", fontSize = 13.sp, color = Color(0xFFAAAAAA))
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            Personas.ALL.forEach { p ->
                val selected = selectedPersona == p.id
                Surface(
                    onClick = { onSelectPersona(p.id) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) Color(0xFF7CE8A0) else Color(0xFF2A2A2A)
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(p.avatar, fontSize = 14.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            p.name,
                            fontSize = 13.sp,
                            color = if (selected) Color(0xFF111111) else Color.White
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Text("弹出时机", fontSize = 13.sp, color = Color(0xFFAAAAAA))
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "立即", 3 to "3秒", 5 to "5秒", 10 to "10秒").forEach { (v, label) ->
                val selected = escapeCountdown == v
                Surface(
                    onClick = { onCountdownChange(v) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) Color(0xFF4A6CF7) else Color(0xFF2A2A2A)
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Text("定时脱身", fontSize = 13.sp, color = Color(0xFFAAAAAA))
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "1分", 5 to "5分", 10 to "10分", 30 to "30分").forEach { (m, label) ->
                Surface(
                    onClick = { onSchedule(m) },
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF2A2A2A)
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.weight(1f)) {
                Text("悬浮球脱身", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("屏幕悬浮🆘，点一下立即脱身", fontSize = 12.sp, color = Color(0xFFAAAAAA))
            }
            Switch(checked = floatBallOn, onCheckedChange = onFloatBallToggle)
        }
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onEscape,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
        ) {
            Text("🚨 立即脱身", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
