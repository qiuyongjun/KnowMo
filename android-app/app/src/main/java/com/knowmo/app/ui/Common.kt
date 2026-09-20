package com.knowmo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knowmo.app.data.Scene
import com.knowmo.app.data.StudyRepository
import com.knowmo.app.ui.theme.AppSurface
import com.knowmo.app.ui.theme.AppText
import com.knowmo.app.ui.theme.AppText2
import com.knowmo.app.ui.theme.BlueBg
import com.knowmo.app.ui.theme.BlueDark
import com.knowmo.app.ui.theme.GreenBg
import com.knowmo.app.ui.theme.GreenKnown
import com.knowmo.app.ui.theme.OrangeBg
import com.knowmo.app.ui.theme.OrangeDark

/* ---------- 频道 Tab（参考抖音顶部 tab） ---------- */

/** v6 隐藏设置入口：推荐 tab 连点目标次数（prd v6 第 1 条） */
private const val SETTING_TAP_TARGET = 5

/** v6 隐藏设置入口：连点间隔上限（ms），超时重置计数 */
private const val SETTING_TAP_GAP_MS = 2000L

/* ---------- v6 空收藏引导（引导卡与播报同源；AppRoot.cardSpeech 引用 FAV_GUIDE_SPEECH） ---------- */

private const val FAV_GUIDE_TITLE = "还没有收藏的词"
private const val FAV_GUIDE_BODY = "学习时点卡片右边的星星，就能把词收进来。"
val FAV_GUIDE_SPEECH = "$FAV_GUIDE_TITLE。$FAV_GUIDE_BODY。"

/**
 * 频道 Tab（参考抖音顶部 tab）。
 * v6 渲染顺序：**推荐（固定第一）→ 收藏（固定第二）→ 可见场景分区**（`scenes` = 设置过滤排序后传入，
 * rec/fav 不参与隐藏与排序，故不在此列表里）。已完成分区（v5：该区每个词间隔天数都 >= 15，
 * design.md §9.5）加 🎓 标记、文字转绿，但仍可点进去自主复习；区内任何一词「忘了」清零即即时回退。
 * 收藏 tab 不参与毕业（fav 不在 SCENES，graduatedScenes 天然不含它）。
 * **隐藏设置入口（v6，prd v6 第 1 条）**：在推荐 tab 上**连点 5 次**（每次间隔 ≤ 2s，超时重置）
 * 回调 `onOpenSettings`；每次点击照常 `onSelect(CHANNEL_DAILY)`——第 1 次切到推荐，
 * 后 4 次重复选中推荐 = 无操作（design.md §11.3 已接受的副作用）。
 */
@Composable
fun ChannelBar(
    current: String,
    graduated: Set<String>,
    scenes: List<Scene>,
    onOpenSettings: () -> Unit,
    onSelect: (String) -> Unit,
) {
    // 连点检测：计数 + 上次点击时间戳放在 remember 里（组件内私有会话态，不进任何持久层）
    var recTaps by remember { mutableStateOf(0) }
    var lastTapAt by remember { mutableStateOf(0L) }

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 推荐（固定第一；连点 5 次打开设置页）
        ChannelChip(
            label = "⭐ 推荐",
            active = current == StudyRepository.CHANNEL_DAILY,
            isGraduated = false,
            onClick = {
                onSelect(StudyRepository.CHANNEL_DAILY)   // 正常单点仍切频道（重复选中推荐 = 无操作）
                val now = System.currentTimeMillis()
                val base = if (now - lastTapAt > SETTING_TAP_GAP_MS) 0 else recTaps
                lastTapAt = now
                val next = base + 1
                if (next >= SETTING_TAP_TARGET) {
                    recTaps = 0
                    onOpenSettings()
                } else {
                    recTaps = next
                }
            },
        )
        // 收藏（固定第二；不参与毕业 / 隐藏 / 排序）
        ChannelChip(
            label = "⭐ 收藏",
            active = current == StudyRepository.CHANNEL_FAV,
            isGraduated = false,
            onClick = { onSelect(StudyRepository.CHANNEL_FAV) },
        )
        // 可见场景分区（设置过滤 + 排序后传入；AppRoot 在当前频道被隐藏时回退推荐）
        scenes.forEach { s ->
            ChannelChip(
                label = if (s.id in graduated) "${s.icon} ${s.name} 🎓" else "${s.icon} ${s.name}",
                active = s.id == current,
                isGraduated = s.id in graduated,
                onClick = { onSelect(s.id) },
            )
        }
    }
}

/** 频道 chip（v6 抽出：推荐 / 收藏 / 场景分区三种共用同一渲染） */
@Composable
private fun ChannelChip(label: String, active: Boolean, isGraduated: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) BlueBg else AppSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(
            label,
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

/* ---------- 共享小组件 ---------- */

/**
 * 卡片形态（v4 运行时计算，不再是静态数据字段）：
 * 新学（NEW）= 无学习记录（无 TermState），全展开、无动作区；
 * 复习（REVIEW）= 每日任务队列里到期/考核中的词，考试交互（先考回忆，作答后展开）；
 * 浏览（FREE，v5 R10）= 池型频道（完成卡之后的温故流 + 场景分区 + v6 收藏频道）抽出的词，
 * 与新学卡同一呈现：词 + 逐字拼音 + 用途全展开、**无 √/×**、点卡重听、点单字看讲解。
 * 它**不作答**，因此不写任何学习状态（`TermState` / `DayState` 零写入）——分区/收藏里的未学词
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

/**
 * v6 空收藏引导卡（**仅收藏频道空池可达**——场景分区池 = 全区词必非空，design.md §11.2）：
 * 大字引导文案，与播报同源（`FAV_GUIDE_SPEECH`，prd v6 第 3 条）。
 */
@Composable
fun GuideCard() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("⭐", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            FAV_GUIDE_TITLE,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            color = AppText,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            FAV_GUIDE_BODY,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = AppText2,
            textAlign = TextAlign.Center,
        )
        // ⚠️ 不渲染 SwipeHint（「上滑看下一个 ↑」）：空收藏池时引导卡是 feed 里唯一的页，
        // 引导上滑会误导（spec/frontend/component-guidelines.md 的方向语义约束）。
    }
}
