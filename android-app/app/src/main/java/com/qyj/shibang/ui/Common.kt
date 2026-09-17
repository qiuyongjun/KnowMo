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
 * 新学 = 无学习记录（无 TermState）；复习 = 任务池内到期/考核中的词；
 * 温故 = 自由刷池抽出的已学词（有 TermState 即已学，含毕业词），与复习卡同交互：先考回忆，作答后展开。
 */
enum class CardMode { NEW, REVIEW, FREE }

/**
 * 词条角标：三态。
 * 新学=蓝 / 复习=橙 / 温故=绿（绿色=正面回炉，与「认识」配色同系，不与另两态冲突）。
 */
@Composable
fun TermBadge(mode: CardMode) {
    val (bg, fg, label) = when (mode) {
        CardMode.NEW -> Triple(BlueBg, BlueDark, "✨ 新学")
        CardMode.REVIEW -> Triple(OrangeBg, OrangeDark, "🔁 复习")
        CardMode.FREE -> Triple(GreenBg, GreenKnown, "📖 温故")
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
