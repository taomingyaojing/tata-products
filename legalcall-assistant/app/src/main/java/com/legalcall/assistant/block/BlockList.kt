package com.legalcall.assistant.block

import android.content.Context

/** 骚扰号码黑名单存储 */
object BlockList {
    private const val PREFS = "block_list"
    private const val KEY_NUMBERS = "numbers"

    fun numbers(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_NUMBERS, "") ?: ""
        return raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun add(context: Context, number: String) {
        val set = numbers(context).toMutableSet()
        set.add(number.trim())
        save(context, set.toList())
    }

    fun remove(context: Context, number: String) {
        save(context, numbers(context).filter { it != number.trim() })
    }

    /** 来电号码是否命中黑名单（尾号匹配，兼容 +86 前缀） */
    fun contains(context: Context, incoming: String): Boolean {
        if (incoming.isBlank()) return false
        return numbers(context).any { blocked ->
            incoming == blocked || incoming.endsWith(blocked) || blocked.endsWith(incoming)
        }
    }

    private fun save(context: Context, list: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_NUMBERS, list.joinToString("\n")).apply()
    }
}
