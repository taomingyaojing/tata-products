package com.gongka.guardian

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.gongka.guardian.ble.GkaBleScanner
import com.gongka.guardian.core.Const
import com.gongka.guardian.core.Prefs
import com.gongka.guardian.detect.GeocodeHelper
import com.gongka.guardian.detect.WifiFingerprintHelper
import com.gongka.guardian.ui.NeoBackground
import com.gongka.guardian.ui.NeoBlack
import com.gongka.guardian.ui.NeoButton
import com.gongka.guardian.ui.NeoCard
import com.gongka.guardian.ui.NeoPrimary
import com.gongka.guardian.ui.NeoWhite

/**
 * MOMO Ta 主页（新粗野主义 Neo-Brutalism）
 *
 * 三个核心模块：
 *  1. 顶部状态区：水獭状态卡片
 *  2. 中部核心按钮：今日已带卡（巨大亮黄按钮）
 *  3. 底部看板：家的指纹 + 已安全通勤
 */
class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result.values.all { it }
            if (granted) {
                requestBackgroundLocation()
            } else {
                Toast.makeText(this, "关键权限未授予，守护功能无法完整运行", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 首次启动：申请权限
        if (Prefs.isFirstLaunch(this)) {
            requestAllPermissions()
            Prefs.markLaunched(this)
        }

        setContent {
            Dashboard()
        }

        // 检查版本更新（强制更新）
        checkUpdate()
    }

    // ================= 主页 UI =================

    @Composable
    private fun Dashboard() {
        var refresh by remember { mutableIntStateOf(0) }
        var testing by remember { mutableStateOf(false) }
        var testResult by remember { mutableStateOf("") }
        val status = statusText()
        val fpCount = WifiFingerprintHelper.deserialize(Prefs.homeWifiFingerprint(this)).size
        val days = Prefs.commuteDays(this)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NeoBackground)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // ---- 顶部状态区 ----
            NeoCard(backgroundColor = NeoWhite, padding = 20.dp) {
                Text(
                    text = "🦦 MOMO Ta",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeoBlack
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = status,
                    fontSize = 15.sp,
                    color = NeoBlack
                )
                if (testResult.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = testResult,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NeoBlack
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- 测试工卡连接按钮 ----
            NeoButton(
                text = if (testing) "正在扫描工卡…" else "🧪 测试工卡连接",
                onClick = {
                    if (testing) return@NeoButton
                    val scanner = GkaBleScanner(this@MainActivity)

                    // 自检 1：蓝牙开关
                    if (!scanner.isBluetoothEnabled()) {
                        Toast.makeText(this@MainActivity, "蓝牙未开启，请先打开蓝牙", Toast.LENGTH_LONG).show()
                        return@NeoButton
                    }
                    // 自检 2：权限
                    val hasBt = ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED
                    val hasLoc = ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!hasBt || !hasLoc) {
                        testResult = "缺少权限（蓝牙:${if (hasBt) "✓" else "✗"} 位置:${if (hasLoc) "✓" else "✗"}），正在重新申请…"
                        refresh++
                        requestAllPermissions()
                        return@NeoButton
                    }

                    testing = true
                    testResult = "正在扫描工卡（15 秒）…"
                    scanner.startDiagnosticScan(Const.SCAN_TIMEOUT_MS) { devices ->
                        runOnUiThread {
                            testing = false
                            val target = Const.WORK_CARD_UUID.toString()
                            val msg = when {
                                devices.isEmpty() ->
                                    "没扫到任何蓝牙设备（蓝牙/位置权限异常）"
                                devices.any { it.beaconUuid == target } -> {
                                    Prefs.markPresentToday(this@MainActivity)
                                    val r = devices.first { it.beaconUuid == target }.rssi
                                    "✅ 连接成功！工卡信号 $r dBm"
                                }
                                else -> {
                                    val beacons = devices.filter { it.beaconUuid != null }
                                    if (beacons.isEmpty()) {
                                        val list = devices.joinToString("\n") {
                                            "${it.name ?: "(无名)"} ${it.mac} ${it.rssi}dBm"
                                        }
                                        "扫到 ${devices.size} 个设备，但都不是 iBeacon：\n$list"
                                    } else {
                                        val list = beacons.joinToString("\n") {
                                            "${it.beaconUuid} ${it.rssi}dBm"
                                        }
                                        "扫到 ${beacons.size} 个 iBeacon，但 UUID 不匹配：\n$list"
                                    }
                                }
                            }
                            testResult = msg
                            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                            refresh++
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                backgroundColor = NeoPrimary,
                fontSize = 17
            )

            Spacer(Modifier.height(16.dp))

            // ---- 中部核心阻断按钮（今日已带卡） ----
            NeoButton(
                text = "今日已带卡",
                onClick = {
                    Prefs.markPresentToday(this@MainActivity)
                    Prefs.addCommuteDay(this@MainActivity)
                    refresh++
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp),
                backgroundColor = NeoPrimary,
                fontSize = 22
            )

            Spacer(Modifier.height(16.dp))

            // ---- 底部状态看板 ----
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                NeoCard(
                    modifier = Modifier.weight(1f),
                    backgroundColor = NeoWhite
                ) {
                    Text("家的指纹", fontSize = 14.sp, color = NeoBlack)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "已记录 $fpCount 个",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeoBlack
                    )
                }
                NeoCard(
                    modifier = Modifier.weight(1f),
                    backgroundColor = NeoWhite
                ) {
                    Text("已安全通勤", fontSize = 14.sp, color = NeoBlack)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "$days 天",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeoBlack
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- 底部操作 ----
            NeoButton(
                text = "设置家地址",
                onClick = { setHomeByAddress() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                backgroundColor = NeoWhite,
                fontSize = 16
            )
            Spacer(Modifier.height(12.dp))
            NeoButton(
                text = "绑定当前 WiFi 为家 WiFi",
                onClick = { bindCurrentWifiAsHome() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                backgroundColor = NeoWhite,
                fontSize = 16
            )
            Spacer(Modifier.height(12.dp))
            NeoButton(
                text = "申请忽略电池优化（保活）",
                onClick = { requestIgnoreBatteryOptimization() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                backgroundColor = NeoWhite,
                fontSize = 16
            )
        }
    }

    private fun statusText(): String {
        return when {
            Prefs.isLeaveToday(this) -> "今日已请假 ✅ 獭獭安心休息"
            Prefs.isPresentToday(this) -> "今日已带卡 ✓ 獭獭正在站岗"
            else -> "獭獭正在家门口为你站岗… 连接正常"
        }
    }

    // ================= 权限申请 =================

    private fun requestAllPermissions() {
        val perms = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_SCAN
            perms += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            perms += Manifest.permission.ACCESS_FINE_LOCATION
            perms += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        perms += Manifest.permission.ACCESS_FINE_LOCATION
        perms += Manifest.permission.ACCESS_COARSE_LOCATION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            perms += Manifest.permission.ACTIVITY_RECOGNITION
        }

        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(this, "请在设置里开启「始终允许」定位，用于离家检测", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ================= 强制更新 =================

    private fun checkUpdate() {
        Thread {
            try {
                val conn = java.net.URL("http://49.233.207.110:8981/api/version/gongka")
                    .openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                val resp = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val obj = org.json.JSONObject(resp)
                val serverCode = obj.optInt("versionCode", 0)
                val force = obj.optBoolean("force", false)
                val url = obj.optString("downloadUrl", "")
                val note = obj.optString("note", "")
                val localCode = packageManager.getPackageInfo(packageName, 0).versionCode
                if (serverCode > localCode && force) {
                    runOnUiThread { showForceUpdate(url, note) }
                }
            } catch (t: Throwable) {
                // 网络异常/无新版本，静默
            }
        }.start()
    }

    private fun showForceUpdate(url: String, note: String) {
        AlertDialog.Builder(this)
            .setTitle("发现新版本")
            .setMessage(note.ifBlank { "有新的版本可用，请更新后使用" })
            .setCancelable(false)
            .setPositiveButton("立即更新") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (t: Throwable) {
                    // 无浏览器时降级
                }
                finish()
            }
            .show()
    }

    // ================= 家地址 / WiFi / 白名单 =================

    /** 设置家地址：输入文字地址 → 高德地理编码 → 经纬度存本地 */
    private fun setHomeByAddress() {
        val input = EditText(this).apply {
            hint = "如：北京市海淀区中关村XX小区"
        }
        AlertDialog.Builder(this)
            .setTitle("设置家地址")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val address = input.text.toString().trim()
                if (address.isBlank()) {
                    Toast.makeText(this, "请输入地址", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                Toast.makeText(this, "正在解析地址…", Toast.LENGTH_SHORT).show()
                GeocodeHelper.geocode(address) { result ->
                    runOnUiThread {
                        if (result != null) {
                            Prefs.saveHome(this, result.first, result.second)
                            Toast.makeText(
                                this,
                                "家坐标已设置：${result.first}, ${result.second}",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(this, "地址解析失败，换个写法试试", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun requestIgnoreBatteryOptimization() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    /** 绑定当前连接的 WiFi 为"家 WiFi"（WiFi 断开监听的前置条件） */
    @Suppress("DEPRECATION")
    private fun bindCurrentWifiAsHome() {
        val wifiManager = getSystemService(WIFI_SERVICE) as WifiManager
        val ssid = wifiManager.connectionInfo?.ssid?.trim('"')
        if (ssid.isNullOrBlank() || ssid == "<unknown ssid>") {
            Toast.makeText(this, "请先连接到家里的 WiFi 再绑定", Toast.LENGTH_LONG).show()
            return
        }
        Prefs.saveHomeWifi(this, ssid)
        WifiFingerprintHelper.collect(this) { fingerprint ->
            Prefs.saveHomeWifiFingerprint(this, WifiFingerprintHelper.serialize(fingerprint))
            Toast.makeText(
                this,
                "已绑定家 WiFi：$ssid（指纹 ${fingerprint.size} 个热点）",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
