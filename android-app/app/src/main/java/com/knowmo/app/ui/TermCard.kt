package com.knowmo.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knowmo.app.data.Term
import com.knowmo.app.data.TermChar
import com.knowmo.app.data.sceneColor
import com.knowmo.app.ui.theme.AppSurface
import com.knowmo.app.ui.theme.AppText
import com.knowmo.app.ui.theme.AppText2
import com.knowmo.app.ui.theme.BluePrimary
import com.knowmo.app.ui.theme.ButtonShape
import com.knowmo.app.ui.theme.CardShapeLarge
import com.knowmo.app.ui.theme.GreenBg
import com.knowmo.app.ui.theme.GreenKnown
import com.knowmo.app.ui.theme.OrangeBg
import com.knowmo.app.ui.theme.OrangeDark
import com.knowmo.app.ui.theme.PageGutter
import com.knowmo.app.ui.theme.RedForgot
import com.knowmo.app.ui.theme.cardShadow

/** 作答区固定高度：按钮与作答结果条同高，作答前后词块不因底部区高度变化而上下跳动 */
private val ANSWER_ZONE_H = 96.dp

/**
 * 学习卡（v8 连击 + 全显拼音，design.md §13.2）：形态由调度器运行时计算——
 * 任务卡（NEW 首见 / REVIEW 到期/重复，**词 + 逐字拼音 + 用途恒全展开** → 用户自评 认识/忘了）
 * 与浏览卡（FREE，v5 R10：温故流 / 场景分区 / 收藏，同样全展开、**无动作区**）。
 * v8：**防泄题契约废除**（v3 以来）——自评模式下拼音本来就可见，无密可泄。
 * `revealed` 语义 = **已作答**（控制 √/× 按钮隐藏与结果文案显示）；`isExam = mode != FREE`。
 *
 * 布局（适老化重设计）：
 * - 卡头一行：模式徽标居左 + 收藏按钮居右（**v19 起两者同款小胶囊**，外形数值共用
 *   Common.kt 的 `HeaderPill` token）——词块区不再为该按钮预留空间（旧版
 *   `padding(end = 56.dp)` 把整个词块推离屏幕中线，是「词不居中」的根因）；
 * - 词 = 唯一视觉主角，垂直 + 水平**真居中**；单字块底色用场景色（替代被删除的大图标块，
 *   保留每张卡的场景色彩身份）；
 * - 字号档 ≤3:64 / 4:52 / 5:42 / ≥6:36 sp；多字词**显式分排**（v21.2：4 字 2+2 /
 *   5 字 2+3 / ≥6 字每排 3 字，逐排居中），≤3 字单排；FlowRow 仅作单排超宽兜底。
 * - 干扰项裁剪：删除 104dp 场景图标块、徽标/引导去 emoji、操作提示降为卡片底部小字。
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
    onSpeakWord: (String) -> Unit,
    onAnswer: (Boolean) -> Unit,
    onToggleFavorite: () -> Unit,
) {
    // 任务卡 = 非 FREE（NEW 首见词也是任务卡）；badge 仍区分 新学/复习
    val isExam = mode != CardMode.FREE
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = PageGutter, vertical = 14.dp),
    ) {
        // 卡片主体：白卡 + 柔和投影浮在暖米色页底上（替代描边，层次更柔和）；
        // 点任意处重听「词 + 用途」，点单字读整词（v7）。
        // 涟漪改淡蓝：整卡是一个大点击区，默认灰涟漪会把整张白卡刷成脏灰（实机截图可见），
        // 淡蓝与拼音同色系，读作「正在朗读」而不是「卡片变灰了」
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .cardShadow(CardShapeLarge)
                .clip(CardShapeLarge)
                .background(Color.White)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = BluePrimary),
                    onClick = onSpeakTerm,
                )
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // 卡头：徽标居左、收藏按钮居右（按钮挪出词块区 → 词块可整幅居中）
            Row(verticalAlignment = Alignment.CenterVertically) {
                TermBadge(mode)
                Spacer(Modifier.weight(1f))
                // v19：收藏按钮改为与左侧徽标**同款小胶囊**（形状/字号/内边距共用 Common.kt
                // 的 HeaderPill token），触摸目标外扩与配色口径见 FavoriteButton 的 KDoc
                FavoriteButton(isFavorite = isFavorite, onClick = onToggleFavorite)
            }

            // 词块（视觉主角）：占据卡头与底部提示之间的全部余量，双向居中
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val charSize = when {
                    term.chars.size <= 3 -> 64.sp
                    term.chars.size == 4 -> 52.sp
                    term.chars.size == 5 -> 42.sp
                    else -> 36.sp
                }
                val blockColor = sceneColor(term.scene)
                // 字 + 拼音上下成对渲染（v21：原是两个独立 FlowRow，拼音与字块各自均布、
                // 多字词对不齐——字的读音看起来"漂"在词下方。成对列后拼音恒对正自己的字）。
                // v21.2（QYJ 2026-09-22 拍板）：多字词**显式分排**，不再让 FlowRow 按屏宽自动
                // 换行——自动断点随设备宽度漂移（5 字词 42sp 在 360dp 屏必然溢出，排出 4+1），
                // 溢出行在 FlowRow 内是 start 对齐，不满足「每排居中」。定档：
                // 4 字 2+2（v21.1）/ 5 字 2+3 / 6 字以上每排 3 字（末排不足自动居中）/ ≤3 字单排。
                // 每排仍是独立 FlowRow（超宽兜底），由外层 Column 的 CenterHorizontally 逐排居中。
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val rows = when {
                        term.chars.size == 4 -> term.chars.chunked(2)
                        term.chars.size == 5 -> listOf(term.chars.take(2), term.chars.drop(2))
                        term.chars.size >= 6 -> term.chars.chunked(3)
                        else -> listOf(term.chars)
                    }
                    rows.forEach { rowChars ->
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            rowChars.forEach { ch ->
                                CharPinyinCell(ch, charSize, blockColor, onSpeakWord)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))

                // 用途提示：暖色块承载，居中短句
                Text(
                    term.tip,
                    fontSize = 22.sp,
                    lineHeight = 34.sp,
                    color = AppText2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppSurface)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }

            // 底部操作提示：小字弱化（教学提示，熟悉后即成背景噪声，不再用 👆 emoji）。
            // ⚠️ v16：**不要再用 alpha 弱化**——`AppText2.copy(alpha = 0.75f)` 叠在白卡上只有
            // 约 4.9:1，低于本项目的 7:1 指标（16sp 属小字，AAA 门槛 7:1）；
            // 16sp + AppText2 原色（9.4:1）已经足够轻，弱化交给字号而不是透明度。
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // v18：图标由三角播放（PlayArrow）改为提示（Info）——这个提示行讲的不是「播放」
                // 而是操作引导；文案「再听」→「朗读」（QYJ 拍板）。Info 在 material-icons-core 核心集内
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = AppText2,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "点一下朗读 · 点单字读字",
                    fontSize = 16.sp,
                    color = AppText2,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // 底部动作区：任务卡未作答 → 认识/忘了大按钮；已作答 → 结果条（同高位置淡入替换）；
        // 浏览卡（FREE）→ 无 resultText，只渲染上滑引导
        if (isExam) {
            Crossfade(
                targetState = revealed,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ANSWER_ZONE_H),
                label = "answerZone",
            ) { answered ->
                if (!answered) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        SolidButton(
                            label = "忘了",
                            container = RedForgot,
                            onClick = { onAnswer(false) },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.Close,
                            height = ANSWER_ZONE_H,
                            fontSize = 26.sp,
                            iconSize = 38.dp,
                        )
                        SolidButton(
                            label = "认识",
                            container = GreenKnown,
                            onClick = { onAnswer(true) },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.Check,
                            height = ANSWER_ZONE_H,
                            fontSize = 26.sp,
                            iconSize = 38.dp,
                        )
                    }
                } else {
                    resultText?.let { AnswerResultBar(it) }
                }
            }
        } else {
            // SwipeHint 只在**浏览卡**（FREE）渲染：v7 考试阶段锁滑（userScrollEnabled=false），
            // 考试卡作答后翻页由自动前进接管（v5 R8）——此时提示「上滑看下一个」是误导
            // （与 implement.md v7 步骤 4 从 DoneCard 移除 SwipeHint 的理由同源；check 修复）。
            SwipeHint()
        }
    }
}

/**
 * 作答结果条：展示层去掉 👍/💪 emoji、改矢量图标承载正/误语义（适老化硬约束：
 * 关键语义不用 emoji 表达）；startsWith 判定保留——AppRoot 话术仍含 emoji 前缀。
 * 与作答按钮同高同圆角，在原位置替换按钮；超长升级文案自动换行（Text 用非填充 weight 收窄）。
 */
@Composable
private fun AnswerResultBar(msg: String) {
    val known = msg.startsWith("👍")
    val display = if (known) msg.removePrefix("👍").trim() else msg.removeSuffix("💪").trim()
    val fg = if (known) GreenKnown else OrangeDark
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ANSWER_ZONE_H)
            .clip(ButtonShape)
            .background(if (known) GreenBg else OrangeBg)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 图标垫白色圆底：浅绿/浅橙底上的细线图标偏弱，圆底让对错一眼可辨
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (known) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            display,
            fontSize = 22.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * 单个「字块 + 拼音」成对单元格（v21.1 从 TermCard 抽出，供四字词分排渲染复用）：
 * 字块底色用场景色、点击读单字（v7），拼音恒对正自己的字（v21 成对渲染契约）。
 * 字块垂直 padding 上小下大：汉字字形重心偏上，对称留白在白卡上看起来反而"下沉"，
 * 下侧多留 2dp 修正。
 */
@Composable
private fun CharPinyinCell(
    ch: TermChar,
    charSize: TextUnit,
    blockColor: Color,
    onSpeakWord: (String) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            ch.c,
            fontSize = charSize,
            fontWeight = FontWeight.Black,
            color = AppText,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(blockColor)
                .clickable { onSpeakWord(ch.c) }
                .padding(start = 8.dp, end = 8.dp, top = 3.dp, bottom = 7.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            ch.p,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = BluePrimary,
        )
    }
}
