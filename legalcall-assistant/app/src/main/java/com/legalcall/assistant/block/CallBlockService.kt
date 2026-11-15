package com.legalcall.assistant.block

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍服务：负责通话界面的「自动接听 / 自动挂断」模拟点击。
 * 这是功能 6（空号音拦截）和后续自动接听的地基。
 */
class CallBlockService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: CallBlockService? = null

        /** 待执行动作：answer / hangup */
        @Volatile
        private var pendingAction: String? = null

        /** 请求挂断（立即执行，若服务未连接则等下次事件） */
        fun requestHangup() {
            pendingAction = "hangup"
            instance?.performHangup()
        }

        /** 请求接听 */
        fun requestAnswer() {
            pendingAction = "answer"
            instance?.performAnswer()
        }
    }

    override fun onServiceConnected() {
        instance = this
        // 服务连上后，若之前有挂起动作则执行
        when (pendingAction) {
            "hangup" -> performHangup()
            "answer" -> performAnswer()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 事件驱动兜底：处理挂起的动作
        when (pendingAction) {
            "hangup" -> performHangup()
            "answer" -> performAnswer()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance == this) instance = null
        super.onDestroy()
    }

    private fun performHangup() {
        val root = rootInActiveWindow ?: return
        val node = findClickable(root, listOf(
            "挂断", "结束通话", "结束", "End", "End call", "Hang up", "挂机"
        ))
        if (node != null) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            pendingAction = null
        }
    }

    private fun performAnswer() {
        val root = rootInActiveWindow ?: return
        val node = findClickable(root, listOf(
            "接听", "接电话", "Answer", "接起"
        ))
        if (node != null) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            pendingAction = null
        }
    }

    private fun findClickable(root: AccessibilityNodeInfo, texts: List<String>): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        val visited = mutableSetOf<AccessibilityNodeInfo>()
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (!visited.add(n)) continue
            val text = n.text?.toString() ?: ""
            val desc = n.contentDescription?.toString() ?: ""
            val hit = texts.any { text.contains(it) || desc.contains(it) }
            if (hit && (n.isClickable || n.isEnabled)) {
                return n
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }
}
