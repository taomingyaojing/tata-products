package com.gongka.guardian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 新粗野主义（Neo-Brutalism）设计系统
 *
 * 核心特征：
 *  - 米白背景（#F4F4F0），拒绝死白
 *  - 粗黑边框（2.dp 纯黑）+ 硬阴影（offset 4.dp 纯黑块，无模糊）
 *  - 12.dp 圆角，粗犷中带呆萌
 */

// ============ 全局配色 ============

/** 背景：米白 / 极浅灰 */
val NeoBackground = Color(0xFFF4F4F0)

/** 主色：活力黄 */
val NeoPrimary = Color(0xFFFFDE4D)

/** 强调色：珊瑚橙 */
val NeoAccent = Color(0xFFFF6B6B)

/** 文字与边框：纯黑 */
val NeoBlack = Color(0xFF1A1A1A)

/** 纯白（卡片底） */
val NeoWhite = Color(0xFFFFFFFF)

/** 柔和绿（状态/成功点缀） */
val NeoGreen = Color(0xFF7CE8A0)

// ============ 通用常量 ============

/** 统一圆角 */
private val NeoCorner = RoundedCornerShape(12.dp)

/** 硬阴影偏移量 */
private val ShadowOffset = 4.dp

/** 粗黑边框宽度 */
private val BorderWidth = 2.dp

/**
 * 新粗野主义卡片：粗黑边框 + 硬阴影（offset 纯黑块）+ 12.dp 圆角
 */
@Composable
fun NeoCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = NeoWhite,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier) {
        // 硬阴影层（偏移的纯黑块）
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = ShadowOffset, y = ShadowOffset)
                .background(NeoBlack, NeoCorner)
        )
        // 内容层
        Column(
            modifier = Modifier
                .matchParentSize()
                .border(BorderWidth, NeoBlack, NeoCorner)
                .background(backgroundColor, NeoCorner)
                .padding(padding),
            content = content
        )
    }
}

/**
 * 新粗野主义按钮：粗黑边框 + 硬阴影 + 圆角，点击有反馈
 */
@Composable
fun NeoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = NeoPrimary,
    textColor: Color = NeoBlack,
    fontSize: Int = 18
) {
    Box(
        modifier = modifier.clickable(onClick = onClick)
    ) {
        // 硬阴影层
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = ShadowOffset, y = ShadowOffset)
                .background(NeoBlack, NeoCorner)
        )
        // 按钮主体
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(BorderWidth, NeoBlack, NeoCorner)
                .background(backgroundColor, NeoCorner),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize.sp
            )
        }
    }
}
