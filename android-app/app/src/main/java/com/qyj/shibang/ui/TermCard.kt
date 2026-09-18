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
 * 学习卡（v4 双层模型）：形态由调度器运行时计算——
 * 新学（NEW）与浏览（FREE，v5 R10：温故流 / 场景分区）/ 复习（REVIEW，考试交互）。
 * 浏览卡 = 词 + 逐字拼音 + 用途**全展开**、**无动作区**（没有 √/×、也没有「👀 想看答案」）：
 * 点卡片重听「词 + 提示」、点单字看讲解；它不作答，因此不改写任何学习状态（design.md §5.7-1）。
 * v5 防泄题 + 求助通道：**仅复习卡**（考试态未作答时点卡片的分流逻辑在调用方 `AppRoot`，
 * 由它传对应的 onSpeakTerm）；`peeked` = 「想看答案」求助展开（会话态，不写任何学习状态）——
 * 拼音/提示显示但认识/忘了按钮保留，可继续作答。R10 后 peek 只剩复习卡可达（浏览卡不需要求助）。
 * 不再展示"第 x/y 张"进度条（队列边界对用户不可见）。
 * v5 R10：删除 R9 的 `footerHint`（分区队尾轻提示）——分区改无限流后没有「最后一张」，轻提示失去时机。
 */
@Composable
fun TermCard(
    term: Term,
    mode: CardMode,
    revealed: Boolean,
    peeked: Boolean,
    resultText: String?,
    onSpeakTerm: () -> Unit,
    onPeek: () -> Unit,
    onCharClick: (TermChar) -> Unit,
    onAnswer: (Boolean) -> Unit,
) {
    // v5 R10（§5.7-1）：**只有复习卡**是考试态（先只出词 → 认识/忘了）；新学卡与浏览卡同走
    // 「全展开 + 无动作区」那条路。原判定是 `mode != CardMode.NEW`，会把浏览卡也当成考试卡。
    val isExam = mode == CardMode.REVIEW
    // 答案区展示：作答展开 || peek 求助展开（peek 不等于作答——按钮判定仍只看 revealed）
    // 浏览卡由调用点传 revealed = true（恒展开）
    val showAnswer = revealed || peeked
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        // 卡片主体：点任意处重听（**复习卡**未作答的分流见 onSpeakTerm 调用方）；点单字看讲解
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
            TermBadge(mode)
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

            // 逐字拼音：新学卡与浏览卡（FREE）常显；复习卡作答前隐藏（考回忆），peek 求助后显示（v5）
            if (showAnswer) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    term.chars.forEach { ch ->
                        Text(ch.p, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = BluePrimary)
                    }
                }
            }
            if (showAnswer) {
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
                // v5：考试态未作答引导用求助按钮；其余（新学/已作答/peek 后）维持重听引导
                if (isExam && !revealed) "👆 想不起来？点下面的按钮" else "👆 点一下再听 · 点单字看讲解",
                fontSize = 18.sp,
                color = AppText2,
            )
        }

        Spacer(Modifier.height(12.dp))

        // 底部动作区
        if (isExam && !revealed) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                FeedbackButton(
                    icon = "×", label = "忘了",
                    container = RedForgot,
                    onClick = { onAnswer(false) },
                    modifier = Modifier.weight(1f),
                )
                FeedbackButton(
                    icon = "√", label = "认识",
                    container = GreenKnown,
                    onClick = { onAnswer(true) },
                    modifier = Modifier.weight(1f),
                )
            }
            // v5 求助通道：弱权重小按钮，只展开答案不写任何学习状态（作答按钮保留在上方）；
            // peek 后已展开，按钮不再显示
            if (!peeked) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppSurface)
                        .clickable { onPeek() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("👀 想看答案", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppText2)
                }
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

/** 复习反馈按钮：图标为 × / √ 字形（字体渲染，随字号缩放、无彩色表情歧义） */
@Composable
private fun FeedbackButton(
    icon: String,
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
            Text(
                icon,
                fontSize = 44.sp,
                lineHeight = 46.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
            )
            Text(label, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
    }
}
