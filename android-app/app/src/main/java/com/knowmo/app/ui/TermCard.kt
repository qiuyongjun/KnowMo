package com.knowmo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.knowmo.app.data.Term
import com.knowmo.app.data.sceneColor
import com.knowmo.app.ui.theme.AppLine
import com.knowmo.app.ui.theme.AppSurface
import com.knowmo.app.ui.theme.AppText
import com.knowmo.app.ui.theme.AppText2
import com.knowmo.app.ui.theme.BlueBg
import com.knowmo.app.ui.theme.BluePrimary
import com.knowmo.app.ui.theme.GreenBg
import com.knowmo.app.ui.theme.GreenKnown
import com.knowmo.app.ui.theme.OrangeBg
import com.knowmo.app.ui.theme.OrangeDark
import com.knowmo.app.ui.theme.RedForgot
import com.knowmo.app.ui.theme.StarGold

/**
 * 学习卡（v8 连击 + 全显拼音，design.md §13.2）：形态由调度器运行时计算——
 * 任务卡（NEW 首见 / REVIEW 到期/重复，**词 + 逐字拼音 + 用途恒全展开** → 用户自评 认识/忘了）
 * 与浏览卡（FREE，v5 R10：温故流 / 场景分区 / 收藏，同样全展开、**无动作区**）。
 * v8：**防泄题契约废除**（v3 以来）——自评模式下拼音本来就可见，无密可泄；
 * 考试隐藏态、「想看答案」peek 求助按钮一并删除。`revealed` 语义收窄为**已作答**
 * （控制 √/× 按钮隐藏与结果文案显示）；`isExam = mode != FREE` 保留（任务卡才动作区）。
 * v8 连击：连击未满 3 的词由 `repeatCard` 打散重插（同词不连续/紧邻），同一词当日多次出现
 * ——每次出现是独立卡实例（独立 seq），各自作答。
 * v7 单字直读：点词卡上任意单字 → `onSpeakWord()` **读整词**（不读提示、不打开弹层——
 * 字卡弹层已随 v7 删除）；与点卡片重听「词 + 用途」区分。
 * v7 多字词布局：词组与拼音行改 **FlowRow** 允许换行、字号降档（≤3:64 / 4:54 / 5:44 / ≥6:36 sp）、
 * 词区与拼音区 `padding(end = 56.dp)` 为右缘星按钮预留空间——6 字词 + 星按钮同屏无遮挡无溢出。
 * 不再展示"第 x/y 张"进度条（队列边界对用户不可见）。
 * v6（prd v6 第 3 条 / design.md §11.2）：右侧竖排**收藏星按钮**（抖音式动作栏）——
 * 藏 = ★ 金色（`StarGold`）、未藏 = ☆ 灰；触控 ≥ 64dp；**全卡型可见**（收藏与作答互不干扰）。
 * 星按钮是叠在卡片右缘的**兄弟节点**（后声明者在上层），点击自消费、不冒泡到卡片 `onSpeakTerm`
 * （收藏与重听互不触发）；收藏状态由调用方（AppRoot）持有，本组件只渲染。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TermCard(
    term: Term,
    mode: CardMode,
    revealed: Boolean,
    resultText: String?,
    isFavorite: Boolean,
    onSpeakTerm: () -> Unit,
    onSpeakWord: () -> Unit,
    onAnswer: (Boolean) -> Unit,
    onToggleFavorite: () -> Unit,
) {
    // 任务卡 = 非 FREE（NEW 首见词也是任务卡）；badge 仍区分 新学/复习
    val isExam = mode != CardMode.FREE
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
    // v6：卡片主体包进 Box——右缘竖排放收藏星按钮（兄弟节点在上层，点击自消费）
    Box(
        Modifier
            .weight(1f)
            .fillMaxWidth(),
    ) {
        // 卡片主体：点任意处重听「词 + 用途」（v8：防泄题分流已删，未作答/已作答无条件重听）；
        // 点单字读整词（v7）
        Column(
            Modifier
                .fillMaxSize()
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

            // v7 词组行：FlowRow 允许换行（多字词不挤压不溢出）；end 预留 56dp 给右缘星按钮；
            // 字号档 ≤3:64 / 4:54 / 5:44 / ≥6:36 sp。点单字 = 读整词（onSpeakWord，v7 单字直读）
            val charSize = when {
                term.chars.size <= 3 -> 64.sp
                term.chars.size == 4 -> 54.sp
                term.chars.size == 5 -> 44.sp
                else -> 36.sp
            }
            FlowRow(
                modifier = Modifier.padding(end = 56.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
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
                            .clickable { onSpeakWord() }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            // 逐字拼音（v8：恒显示——防泄题契约废除，自评模式下无密可泄）
            FlowRow(
                modifier = Modifier.padding(end = 56.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                term.chars.forEach { ch ->
                    Text(ch.p, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = BluePrimary)
                }
            }
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
            Spacer(Modifier.height(10.dp))
            Text(
                "👆 点一下再听 · 点单字读词",
                fontSize = 18.sp,
                color = AppText2,
            )
        }

        // v6 收藏星按钮（抖音式右侧动作栏）：叠在卡片右缘垂直居中——后声明的兄弟节点在顶层，
        // 点击自消费、不会冒泡到卡片主体的 `onSpeakTerm`。触控 64dp，星字号 40sp；
        // 全卡型可见（NEW / REVIEW / FREE，复习卡考试态也不隐藏）。
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp)
                .size(64.dp)
                .clickable { onToggleFavorite() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (isFavorite) "★" else "☆",
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                color = if (isFavorite) StarGold else AppText2,
            )
        }
    }

        Spacer(Modifier.height(12.dp))

        // 底部动作区：任务卡未作答 → 认识/忘了大按钮；已作答 → 结果文案；
        // 浏览卡（FREE）→ 无 resultText，只渲染上滑引导
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
            // SwipeHint 只在**浏览卡**（FREE）渲染：v7 考试阶段锁滑（userScrollEnabled=false），
            // 考试卡作答后翻页由自动前进接管（v5 R8）——此时提示「上滑看下一个 ↑」是误导
            // （与 implement.md v7 步骤 4 从 DoneCard 移除 SwipeHint 的理由同源；check 修复）。
            // FREE 卡没有 resultText，不受上面分支影响，恒显示上滑引导。
            if (!isExam) SwipeHint()
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
