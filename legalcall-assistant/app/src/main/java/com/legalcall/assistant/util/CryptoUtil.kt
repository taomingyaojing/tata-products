package com.legalcall.assistant.util

import android.content.Context
import android.os.Build
import java.security.MessageDigest
import java.util.UUID

/**
 * 匿名设备标识（用于使用量统计去重）。
 *
 * 机器码 = MD5(持久化 UUID + 系统硬件指纹) 前 8 位，是单向哈希的匿名值，
 * 不含手机号/IMEI/位置等任何可识别个人身份的信息，只用于服务器统计"活跃设备数"。
 */
object CryptoUtil {

    private const val PREFS = "device_identity"
    private const val KEY_UUID = "uuid"

    fun md5(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            input.hashCode().toString()
        }
    }

    /**
     * 生成并持久化 8 位「匿名设备标识」。
     * 由持久化 UUID + 系统硬件指纹（Build.FINGERPRINT）混合取 MD5 前 8 位。
     */
    fun getMachineCode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var uuid = prefs.getString(KEY_UUID, null)
        if (uuid.isNullOrBlank()) {
            uuid = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_UUID, uuid).apply()
        }
        val fingerprint = try {
            Build.FINGERPRINT ?: Build.MODEL
        } catch (e: Exception) {
            Build.MODEL
        }
        val mixed = "$uuid|$fingerprint"
        return md5(mixed).take(8).uppercase()
    }
}
