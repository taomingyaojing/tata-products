package com.gongka.guardian.core

import java.util.UUID

/**
 * 全局常量 / 可调参数集中地
 *
 * 说明：所有"魔法数字"都集中在这里，方便老大根据硬件参数、业务节奏做调优，
 * 而不是散落在各业务类里。
 */
object Const {

    // ============ 工卡 iBeacon 参数（对接硬件时替换） ============

    /**
     * 工卡 iBeacon 的 Proximity UUID（16 字节，标准 iBeacon 广播在 manufacturer data 里）
     * 真实工卡 UUID（老大 2026-11-10 提供）
     */
    val WORK_CARD_UUID: UUID = UUID.fromString("E2C56DB5-DFFB-48D2-B060-D0F5A71096E0")

    /**
     * iBeacon 的 Apple 公司 ID（广播 manufacturer data 的前两个字节）
     * 0x004C = Apple, Inc.，用于 ScanFilter 粗筛，只放行 iBeacon 类广播，大幅降低回调噪音
     */
    const val APPLE_COMPANY_ID = 0x004C

    // ============ 第一道防线：早高峰窗口 ============

    /** 早高峰起始小时（24 小时制） */
    const val PEAK_START_HOUR = 7
    /** 早高峰结束小时 */
    const val PEAK_END_HOUR = 10

    /**
     * 静止判定阈值：加速度计合加速度的方差（m²/s⁴）低于该值视为"静止"。
     * 方差小 = 设备几乎不动；超过阈值视为"开始移动"。
     */
    const val ACCEL_STILL_VARIANCE = 0.6f

    /** 连续采样窗口长度（用于计算方差，约 2 秒 @50Hz） */
    const val ACCEL_SAMPLE_WINDOW = 100

    // ============ 第二道防线：地理围栏 ============

    /** "家"围栏半径（米） */
    const val GEOFENCE_RADIUS_METERS = 50f

    /** 围栏 requestId */
    const val GEOFENCE_HOME_ID = "home_geofence"

    // ============ 裁决机制：蓝牙扫描 ============

    /** 触发后高频扫描时长（毫秒），15 秒内搜不到即判定忘带 */
    const val SCAN_TIMEOUT_MS = 15_000L

    /** 触发裁决后，若搜到卡，静默冷却期（毫秒）——避免刚到家门口又立刻误触发 */
    const val SCAN_COOLDOWN_MS = 60_000L

    // ============ 通知相关 ============

    const val NOTIFICATION_CHANNEL_ID = "gka_foreground"
    const val NOTIFICATION_ID = 1001

    // 通知栏快捷操作（Broadcast 的 action）
    const val ACTION_MARK_PRESENT = "com.gongka.guardian.ACTION_MARK_PRESENT"
    const val ACTION_MARK_LEAVE = "com.gongka.guardian.ACTION_MARK_LEAVE"

    // 通知栏刷新广播（服务内部状态变化时发给自己，刷新通知按钮文案）
    const val ACTION_REFRESH_NOTIFICATION = "com.gongka.guardian.ACTION_REFRESH_NOTIFICATION"

    // ============ 首启 / 权限 ============

    /** 首次运行标记 */
    const val PREF_FIRST_LAUNCH = "first_launch"

    /** 今天是否已请假（日期 yyyy-MM-dd） */
    const val PREF_LEAVE_DATE = "leave_date"
    /** 今天是否已确认带卡（日期 yyyy-MM-dd） */
    const val PREF_PRESENT_DATE = "present_date"
}
