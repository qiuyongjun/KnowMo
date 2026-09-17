package com.qyj.shibang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qyj.shibang.data.Term
import com.qyj.shibang.data.TermChar
import com.qyj.shibang.data.sceneColor
import com.qyj.shibang.ui.theme.AppLine
import com.qyj.shibang.ui.theme.AppSurface
import com.qyj.shibang.ui.theme.AppText
import com.qyj.shibang.ui.theme.AppText2
import com.qyj.shibang.ui.theme.BlueBg
import com.qyj.shibang.ui.theme.BluePrimary
import com.qyj.shibang.ui.theme.GreenBg
import com.qyj.shibang.ui.theme.GreenKnown
import com.qyj.shibang.ui.theme.OrangeBg
import com.qyj.shibang.ui.theme.OrangeDark
import com.qyj.shibang.ui.theme.RedForgot

/**
 * 学习卡：新学（词组+逐字拼音+用途） / 复习（先考回忆→认识/忘了→展开）。
 * 对应原型交互契约 §6。
 */
@Composable
fun TermCard(
    term: Term,
    index: Int,
    total: Int,
    revealed: Boolean,
    resultText: String?,
    onSpeakTerm: () -> Unit,
    onCharClick: (TermChar) -> Unit,
    onAnswer: (Boolean) -> Unit,
) {
    val isReview = term.kind == Term.Kind.REVIEW
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        ProgressRow(index, total)

        // 卡片主体：点任意处重听；点单字看讲解
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .border(2.dp, AppLine, RoundedCornerShape(20.dp))
                .clickable { onSpeakTerm() }
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // 场景图标
            Box(
                Modifier
                    .height(104.dp)
                    .width(104.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(sceneColor(term.scene)),
                contentAlignment = Alignment.Center,
            ) {
                Text(term.icon, fontSize = 60.sp)
            }
            Spacer(Modifier.height(12.dp))
            TermBadge(term)
            Spacer(Modifier.height(14.dp))

            // 词组：每个字可点开字卡弹层
            val charSize = when {
                term.chars.size <= 3 -> 64.sp
                term.chars.size == 4 -> 54.sp
                else -> 42.sp
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                term.chars.forEach { ch ->
                    val shape = RoundedCornerShape(12.dp)
                    Text(
                        ch.c,
                        fontSize = charSize,
                        fontWeight = FontWeight.Black,
                        color = AppText,
                        modifier = Modifier
                            .clip(shape)
                            .background(BlueBg)
                            .clickable { onCharClick(ch) }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            // 逐字拼音：新学卡常显；复习卡回忆阶段隐藏
            if (revealed) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    term.chars.forEach { ch ->
                        Text(ch.p, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = BluePrimary)
                    }
                }
            }
            if (revealed) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "💡 ${term.tip}",
                    fontSize = 22.sp,
                    lineHeight = 34.sp,
                    color = AppText2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppSurface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "👆 点一下再听 · 点单字看讲解",
                fontSize = 18.sp,
                color = AppText2,
            )
        }

        Spacer(Modifier.height(12.dp))

        // 底部动作区
        if (isReview && !revealed) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                FeedbackButton(
                    emoji = "😕", label = "忘了",
                    container = RedForgot,
                    onClick = { onAnswer(false) },
                    modifier = Modifier.weight(1f),
                )
                FeedbackButton(
                    emoji = "😀", label = "认识",
                    container = GreenKnown,
                    onClick = { onAnswer(true) },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            resultText?.let { msg ->
                val known = msg.startsWith("👍")
                Text(
                    msg,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (known) GreenKnown else OrangeDark,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (known) GreenBg else OrangeBg)
                        .padding(vertical = 16.dp, horizontal = 12.dp),
                )
                Spacer(Modifier.height(6.dp))
            }
            SwipeHint()
        }
    }
}

/** 复习反馈按钮 */
@Composable
private fun FeedbackButton(
    emoji: String,
    label: String,
    container: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .height(92.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 40.sp)
            Text(label, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
    }
}
