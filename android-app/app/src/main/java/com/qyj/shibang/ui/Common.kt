package com.qyj.shibang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qyj.shibang.ui.theme.AppSurface
import com.qyj.shibang.ui.theme.AppText2
import com.qyj.shibang.ui.theme.BlueBg
import com.qyj.shibang.ui.theme.BlueDark
import com.qyj.shibang.ui.theme.GreenBg
import com.qyj.shibang.ui.theme.GreenKnown
import com.qyj.shibang.ui.theme.OrangeBg
import com.qyj.shibang.ui.theme.OrangeDark

/* ---------- 频道 Tab（参考抖音顶部 tab） ---------- */

/**
 * 频道 Tab（参考抖音顶部 tab）。
 * 已完成分区（v4：该区每个词认识计数满 3 = 已移除，design.md §9.6）加 🎓 标记、文字转绿，
 * 但仍可点进去自主复习；区内任何一词「忘了」清零即即时回退。
 */
@Composable
fun ChannelBar(current: String, graduated: Set<String>, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        com.qyj.shibang.data.SCENES.forEach { s ->
            val active = s.id == current
            val isGraduated = s.id in graduated
            val shape = RoundedCornerShape(14.dp)
            Box(
                Modifier
                    .clip(shape)
                    .background(if (active) BlueBg else AppSurface)
                    .clickable { onSelect(s.id) }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Text(
                    if (isGraduated) "${s.icon} ${s.name} 🎓" else "${s.icon} ${s.name}",
                    fontSize = 21.sp,
                    fontWeight = if (active) FontWeight.Black else FontWeight.Bold,
                    color = when {
                        active -> BlueDark
                        isGraduated -> GreenKnown
                        else -> AppText2
                    },
                )
            }
        }
    }
}

/* ---------- 共享小组件 ---------- */

/**
 * 卡片形态（v4 运行时计算，不再是静态数据字段）：
 * 新学（NEW）= 无学习记录（无 TermState），全展开、无动作区；
 * 复习（REVIEW）= 每日任务队列里到期/考核中的词，考试交互（先考回忆，作答后展开）；
 * 浏览（FREE，v5 R10）= 池型频道（完成卡之后的温故流 + 场景分区）抽出的词，与新学卡同一呈现：
 * 词 + 逐字拼音 + 用途全展开、**无 √/×**、点卡重听、点单字看讲解。
 * 它**不作答**，因此不写任何学习状态（`TermState` / `DayState` 零写入）——分区里的未学词
 * 只「看」不算学会，新词入口唯一化到每日任务。
 * ⚠️ R10 刻意**不新增也不重命名**成员：`FREE` 本义「自由刷」语义仍成立，而重命名要动 4 个文件的
 * 十余处引用，在本机无法编译的条件下不值这个风险。
 */
enum class CardMode { NEW, REVIEW, FREE }

/**
 * 词条角标：三态。
 * 新学=蓝 / 复习=橙 / 浏览=绿（绿色=正面回炉，与「认识」配色同系，不与另两态冲突）。
 * v5 R10：浏览态文案「📖 温故」→「📖 看看」——同一徽标要同时服务推荐频道的温故流与场景分区，
 * 而分区池含**未学词**，叫「温故」不成立。
 */
@Composable
fun TermBadge(mode: CardMode) {
    val (bg, fg, label) = when (mode) {
        CardMode.NEW -> Triple(BlueBg, BlueDark, "✨ 新学")
        CardMode.REVIEW -> Triple(OrangeBg, OrangeDark, "🔁 复习")
        CardMode.FREE -> Triple(GreenBg, GreenKnown, "📖 看看")
    }
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Text(
            label,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun SwipeHint() {
    Text(
        "上滑看下一个 ↑",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = AppText2,
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        textAlign = TextAlign.Center,
    )
}
