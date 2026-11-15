package com.legalcall.assistant.fakecall

/**
 * 假来电人设。
 * 注意：只做日常身份（老板/客户/家人/快递），严禁冒充公检法等公权力身份。
 */
data class Persona(
    val id: String,
    val name: String,
    val number: String,
    val avatar: String,     // emoji 头像
    val voiceLine: String   // 接听后的假语音台词（TTS）
)

object Personas {

    val ALL = listOf(
        Persona(
            "courier", "快递员", "137****0000", "📦",
            "您好，您的快递到了，就在楼下，麻烦下来取一下。"
        ),
        Persona(
            "boss", "老板", "138****8888", "💼",
            "喂，是我。你现在方便说话吗？公司这边有个急事，你尽快回我个电话。"
        ),
        Persona(
            "client", "王经理", "139****6666", "🤝",
            "你好，我是合作方的王经理，之前约好的事情想跟你确认一下，方便回个电话吗？"
        ),
        Persona(
            "family", "妈妈", "136****1234", "🏠",
            "喂，你在哪呢？家里有点事，你回个电话过来。"
        )
    )

    fun byId(id: String): Persona = ALL.firstOrNull { it.id == id } ?: ALL[0]
}
