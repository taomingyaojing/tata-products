package com.legalcall.assistant.fakecall

/**
 * 戏精剧本：多句台词 + 停顿，接听后听筒逐句播放，用户跟着停顿回答。
 * gapSeconds = 该句播完后到下句之间的停顿秒数（留白给用户对答）。
 */
data class ScriptLine(val text: String, val gapSeconds: Int)

object Scripts {

    val LINES: Map<String, List<ScriptLine>> = mapOf(
        "boss" to listOf(
            ScriptLine("喂，是我。你现在方便说话吗？", 2),
            ScriptLine("客户那边方案要改，挺急的，你现在能回趟公司吗？", 4),
            ScriptLine("行，我等你，到了直接来我办公室。", 2)
        ),
        "courier" to listOf(
            ScriptLine("喂您好，我是快递员，您的包裹到楼下了。", 3),
            ScriptLine("这个件有点大，我不太方便上楼，您下来取一下？", 4),
            ScriptLine("好嘞，我在楼下等您。", 2)
        ),
        "client" to listOf(
            ScriptLine("喂，王经理。之前约的那个合作，方便现在聊两句吗？", 3),
            ScriptLine("我们这边想再确认几个细节，你看下午有空碰一下吗？", 4),
            ScriptLine("好，那就下午三点，我发你地址。", 2)
        ),
        "family" to listOf(
            ScriptLine("喂，你在哪儿呢？", 2),
            ScriptLine("家里有点急事，你赶紧回来一趟吧。", 3),
            ScriptLine("路上慢点，注意安全。", 2)
        )
    )

    fun byId(id: String): List<ScriptLine> = LINES[id] ?: emptyList()
}
