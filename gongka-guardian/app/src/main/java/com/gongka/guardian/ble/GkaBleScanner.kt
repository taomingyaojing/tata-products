package com.gongka.guardian.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.gongka.guardian.core.Const
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** 诊断扫描单条设备记录 */
data class DiagDevice(
    val name: String?,       // 设备名（可能为 null）
    val mac: String,         // MAC 地址（无位置权限时为 02:00:00:00:00:00）
    val rssi: Int,           // 信号强度 dBm
    val beaconUuid: String?  // iBeacon UUID（非 iBeacon 为 null）
)


/**
 * 【裁决机制】BLE 高频扫描 + iBeacon 比对
 *
 * 核心逻辑：
 *  1. [startScan] 被触发后，用 ScanFilter 粗筛（只放行 Apple iBeacon 广播），
 *     再在回调里手动解析 manufacturer data，精确比对工卡 UUID。
 *     搜到一次即回传 found=true。
 *  2. 15 秒超时未搜到 → 回传 found=false（判定忘带）。
 *  3. 扫描由 Handler 定时结束，零死循环。
 *
 * iBeacon 广播的 manufacturer data 结构（从 company ID 开始）：
 *   [0..1]  0x004C  Apple 公司 ID
 *   [2]     0x02    iBeacon 类型
 *   [3]     0x15    后续长度 21 字节
 *   [4..19] UUID（16 字节）
 *   [20..21] major
 *   [22..23] minor
 *   [24]     txPower
 */
class GkaBleScanner(private val context: Context) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scanner: BluetoothLeScanner? = adapter?.bluetoothLeScanner

    private val handler = Handler(Looper.getMainLooper())

    /** 扫描中的互斥标记 */
    private val scanning = AtomicBoolean(false)

    /** 超时 Runnable（15 秒后强制结束并判定"未搜到"） */
    private val timeoutRunnable = Runnable {
        finishScan(found = false)
    }

    /** 当前这次扫描的完成回调 */
    private var callback: ((Boolean) -> Unit)? = null

    /**
     * 启动一次 15 秒裁决扫描
     *
     * @param uuid      工卡 iBeacon 的 UUID
     * @param timeoutMs 超时时间
     * @param onResult  结果回调：true=搜到卡 / false=超时未搜到
     */
    fun startScan(uuid: UUID, timeoutMs: Long, onResult: (Boolean) -> Unit) {
        if (scanning.get()) return          // 已在扫描，忽略
        val s = scanner ?: return onResult(false)

        callback = onResult
        scanning.set(true)

        // 高频扫描：SCAN_MODE_LOW_LATENCY，保证 15 秒内尽可能多次命中广播
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // ScanFilter：粗筛 Apple iBeacon（锁 company ID 0x004C）
        // 精确 UUID 比对放在回调里手动解析（mask 精确到 UUID 在部分机型上不可靠）
        val filter = ScanFilter.Builder()
            .setManufacturerData(
                Const.APPLE_COMPANY_ID,
                byteArrayOf(0x02, 0x15),       // iBeacon type + length 前缀
                byteArrayOf(0xFF.toByte(), 0xFF.toByte()) // 只匹配前两字节，其余不约束
            )
            .build()

        s.startScan(listOf(filter), settings, scanCallback)

        // 15 秒超时 → 判定忘带
        handler.postDelayed(timeoutRunnable, timeoutMs)
    }

    fun stop() {
        if (scanning.getAndSet(false)) {
            try { scanner?.stopScan(scanCallback) } catch (_: Exception) {}
        }
        handler.removeCallbacks(timeoutRunnable)
        callback = null
        stopDiagnosticScan()
    }

    // ================= 诊断扫描（排查扫不到的问题） =================

    /** 诊断扫描互斥标记（独立于裁决扫描） */
    private val diagScanning = AtomicBoolean(false)

    /** 按 MAC 去重的诊断设备列表（保持插入顺序） */
    private val diagDevices = mutableMapOf<String, DiagDevice>()

    private var diagCallback: ((List<DiagDevice>) -> Unit)? = null

    private val diagTimeoutRunnable = Runnable { finishDiagScan() }

    /** 蓝牙是否开启 */
    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    /**
     * 诊断扫描：收集扫描期间所有 BLE 设备（名称/MAC/信号/是否 iBeacon），用于排查。
     * 无 ScanFilter，扫描全部广播。
     * @param onResult 返回去重后的设备列表，空列表表示一个设备都没扫到
     */
    fun startDiagnosticScan(timeoutMs: Long, onResult: (List<DiagDevice>) -> Unit) {
        if (diagScanning.get()) return
        val s = scanner ?: run { onResult(emptyList()); return }

        diagCallback = onResult
        diagDevices.clear()
        diagScanning.set(true)

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // 无过滤器扫描全部广播
        s.startScan(null, settings, diagScanCallback)
        handler.postDelayed(diagTimeoutRunnable, timeoutMs)
    }

    fun stopDiagnosticScan() {
        if (diagScanning.getAndSet(false)) {
            try { scanner?.stopScan(diagScanCallback) } catch (_: Exception) {}
        }
        handler.removeCallbacks(diagTimeoutRunnable)
        diagCallback = null
    }

    private val diagScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val mac = result.device?.address ?: "unknown"
            val uuid = parseBeaconUuid(result)?.toString()
            // 同一设备保留最强信号
            val existing = diagDevices[mac]
            if (existing == null || result.rssi > existing.rssi) {
                diagDevices[mac] = DiagDevice(
                    name = result.device?.name,
                    mac = mac,
                    rssi = result.rssi,
                    beaconUuid = uuid
                )
            }
        }

        override fun onScanFailed(errorCode: Int) {
            finishDiagScan()
        }
    }

    private fun finishDiagScan() {
        if (!diagScanning.getAndSet(false)) return
        try { scanner?.stopScan(diagScanCallback) } catch (_: Exception) {}
        handler.removeCallbacks(diagTimeoutRunnable)
        val cb = diagCallback
        diagCallback = null
        cb?.invoke(diagDevices.values.toList())
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // 精确解析 iBeacon UUID 比对
            if (parseBeaconUuid(result) == Const.WORK_CARD_UUID) {
                finishScan(found = true)   // 搜到工卡！
            }
        }

        override fun onScanFailed(errorCode: Int) {
            finishScan(found = false)
        }
    }

    /** 结束本次扫描并回调结果 */
    private fun finishScan(found: Boolean) {
        if (!scanning.getAndSet(false)) return  // 已经结束过，防止重复回调
        try { scanner?.stopScan(scanCallback) } catch (_: Exception) {}
        handler.removeCallbacks(timeoutRunnable)
        val cb = callback
        callback = null
        cb?.invoke(found)
    }

    /**
     * 从 ScanResult 中解析 iBeacon 的 UUID
     * @return 解析出的 UUID，非 iBeacon 或解析失败返回 null
     */
    private fun parseBeaconUuid(result: ScanResult): UUID? {
        val record = result.scanRecord ?: return null
        val bytes = record.bytes

        var i = 0
        while (i < bytes.size) {
            val length = bytes[i].toInt() and 0xFF
            if (length == 0) break
            val type = bytes[i + 1].toInt() and 0xFF

            // 0xFF = Manufacturer Specific Data
            if (type == 0xFF) {
                val companyId = ((bytes[i + 3].toInt() and 0xFF) shl 8) or (bytes[i + 2].toInt() and 0xFF)
                if (companyId == Const.APPLE_COMPANY_ID) {
                    // 确认是 iBeacon：type=0x02, length=0x15
                    if (bytes.size >= i + 9 &&
                        bytes[i + 4].toInt() == 0x02 &&
                        bytes[i + 5].toInt() == 0x15
                    ) {
                        val uuidBytes = ByteArray(16)
                        System.arraycopy(bytes, i + 6, uuidBytes, 0, 16)
                        val bb = ByteBuffer.wrap(uuidBytes).order(ByteOrder.BIG_ENDIAN)
                        val msb = bb.long
                        val lsb = bb.long
                        return UUID(msb, lsb)
                    }
                }
            }
            i += length + 1
        }
        return null
    }
}
