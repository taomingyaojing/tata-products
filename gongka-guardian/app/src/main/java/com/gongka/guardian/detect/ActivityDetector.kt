package com.gongka.guardian.detect

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.gongka.guardian.core.Const
import kotlin.math.sqrt

/**
 * 【第一道防线】出门动作检测（静止 → 步行/车载）
 *
 * 设计取舍（省电 vs 精确）：
 *  - 不用 Google ActivityRecognition API：国内手机无 GMS，不可用。
 *  - 不用死循环/短定时器轮询：费电，违背需求。
 *  - 方案：监听系统【加速度传感器】，用"滑动窗口内合加速度方差"做状态机：
 *      方差 < 阈值  → 判定"静止"
 *      方差 ≥ 阈值  → 判定"开始移动"（步行/车载/其他大幅晃动都会越阈）
 *    传感器由系统按采样率回调，App 只被动收数、零轮询，几乎不耗电。
 *
 * 状态机：
 *   STILL ──(方差越阈)──▶ MOVING ──(回调一次 onMoving)──▶ STILL（进入冷却，避免连触发）
 *
 * 注：若后续要区分"步行/车载"精确到活动类型，可叠加 TYPE_STEP_DETECTOR 硬件计步器
 *     （计步器为硬件级、超省电），在 MOVING 基础上用"步数>0"佐证步行。
 */
class ActivityDetector(
    private val context: Context,
    private val onMoving: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accel: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /** 滑动窗口：保存最近 N 个合加速度值 */
    private val window = ArrayDeque<Float>()

    private var isMoving = false

    fun start() {
        accel?.let {
            // SENSOR_DELAY_UI ≈ 15Hz，足够判断动静，又不费电
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        window.clear()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        // 合加速度模长（不含重力方向修正，仅看波动幅度即可）
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)

        window.addLast(magnitude)
        if (window.size > Const.ACCEL_SAMPLE_WINDOW) window.removeFirst()
        if (window.size < Const.ACCEL_SAMPLE_WINDOW) return // 窗口未满，先攒样本

        val variance = calcVariance(window)
        val nowMoving = variance >= Const.ACCEL_STILL_VARIANCE

        // 状态机：静止 → 移动 的上升沿，触发一次
        if (nowMoving && !isMoving) {
            isMoving = true
            onMoving()
        } else if (!nowMoving) {
            isMoving = false
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /** 计算滑动窗口方差（m²/s⁴） */
    private fun calcVariance(data: ArrayDeque<Float>): Float {
        if (data.isEmpty()) return 0f
        val mean = data.sum().toDouble() / data.size
        var sumSq = 0.0
        for (v in data) {
            val d = v - mean
            sumSq += d * d
        }
        return (sumSq / data.size).toFloat()
    }
}
