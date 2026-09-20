package com.knowmo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import com.knowmo.app.data.AppSettings
import com.knowmo.app.data.Scene
import com.knowmo.app.data.SceneProgress
import com.knowmo.app.data.StudyStats
import com.knowmo.app.ui.theme.AppLine
import com.knowmo.app.ui.theme.AppSurface
import com.knowmo.app.ui.theme.AppText
import com.knowmo.app.ui.theme.AppText2
import com.knowmo.app.ui.theme.BlueBg
import com.knowmo.app.ui.theme.BlueDark
import com.knowmo.app.ui.theme.GreenBg
import com.knowmo.app.ui.theme.GreenKnown
import com.knowmo.app.ui.theme.OrangeBg
import com.knowmo.app.ui.theme.OrangeDark

/**
 * 全屏设置页（v6，design.md §11.2）：由「推荐」tab **连点 5 次**（间隔 ≤ 2s）唤出的隐藏入口，
 * 不占日常界面。白底全屏 overlay，覆盖 feed；唯一出口 = 底部「完成」大按钮。
 * 适老化硬约束：字号 ≥ 22sp（说明性小字 18sp）、可点目标 ≥ 64dp 高、顺序调整用 ↑/↓ **大按钮**
 * （无拖拽手势）；配色沿用主题常量（高对比 ≥ 7:1）。
 * 四组设置：
 * ⓪学习统计（v14，design.md §16，**只读一节**）：总览（已学/毕业/收藏）+ 今日战果 + 连续学习天数
 *   + 各分区进度条 + f30 观测小字。受众拍板 = 家属/年轻人（QYJ：设置本来就不给老人用），
 *   信息密度不受「少而大」约束，但沿用本页既有版式 token；纯展示零写入、无作答入口
 *   （进出设置页不改学习状态，池型零写入契约不破坏）。
 * ①每日学习词数量（单选 3/5/10/15/20，缺省 10）——**当日队列冻结不变、次日生效**（本页只写
 *   `AppSettings.setQuota`，不触碰当日队列）；
 * ②每天学几个新词（单选 1/3/5/10，缺省 5，v6 R14 新词配额独立：新词速率恒定、不被复习挤占；
 *   次日生效，同①）；
 * ③分区显示开关（除推荐/收藏外的场景分区，可全部隐藏——只剩推荐 + 收藏）；
 * ④分区显示顺序（↑ 上移 / ↓ 下移；推荐固定第一、收藏固定第二，不在此列）。
 * 显隐/顺序改动即时生效：回调写 `AppSettings` 后由 AppRoot 重读可见分区触发频道栏重排。
 */
@Composable
fun SettingsScreen(
    orderedScenes: List<Scene>,
    hiddenIds: Set<String>,
    quota: Int,
    quotaNew: Int,
    stats: StudyStats,   // v14 学习统计只读快照（AppRoot 打开设置页时从 repo 一次性取）
    onSetVisible: (String, Boolean) -> Unit,
    onMove: (String, Int) -> Unit,
    onSetQuota: (Int) -> Unit,
    onSetQuotaNew: (Int) -> Unit,
    onDone: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Text("⚙️ 设置", fontSize = 32.sp, fontWeight = FontWeight.Black, color = AppText)
        Spacer(Modifier.height(20.dp))

        /* ---------- ⓪ 学习统计（v14，只读一节：总览 + 今日战果 + 分区进度 + f30） ---------- */
        Text("📊 学习统计", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AppText)
        Spacer(Modifier.height(8.dp))
        Text(
            "已学 ${stats.learned} / ${stats.totalWords} 词（毕业 ${stats.graduated}）· 收藏 ${stats.favorites} 个",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = AppText,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "今日认识 ${stats.todayKnown} 次 · 忘了 ${stats.todayForgot} 次 · 连续学习 ${stats.streak} 天",
            fontSize = 22.sp,
            color = AppText,
        )
        Spacer(Modifier.height(12.dp))
        stats.scenes.forEachIndexed { i, p ->
            SceneProgressRow(p)
            if (i < stats.scenes.lastIndex) Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            // f30 观测（调参用，QYJ 看的小字）：忘了率取整百分比，总作答为 0 时省略
            "30天词观测：作答 ${stats.f30Total} · 忘了 ${stats.f30Fail}" +
                if (stats.f30Total > 0) "（忘了率 ${stats.f30Fail * 100 / stats.f30Total}%）" else "",
            fontSize = 18.sp,
            color = AppText2,
        )
        Spacer(Modifier.height(24.dp))

        /* ---------- ① 每日学习词数量（总量） ---------- */
        Text("每日学习词数量", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AppText)
        Text("改完明天生效，今天学的不变。", fontSize = 18.sp, color = AppText2)
        Spacer(Modifier.height(10.dp))
        QuotaSelector(selected = quota, options = AppSettings.QUOTA_OPTIONS, onSelect = onSetQuota)
        Spacer(Modifier.height(24.dp))

        /* ---------- ② 每天学几个新词（v6 R14 独立配额） ---------- */
        Text("每天学几个新词", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AppText)
        Text("新词固定几个，不被复习挤掉。改完明天生效。", fontSize = 18.sp, color = AppText2)
        Spacer(Modifier.height(10.dp))
        QuotaSelector(selected = quotaNew, options = AppSettings.NEW_QUOTA_OPTIONS, onSelect = onSetQuotaNew)
        Spacer(Modifier.height(24.dp))

        /* ---------- ③④ 分区显隐与顺序 ---------- */
        Text("分区显示与顺序", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AppText)
        Text("推荐和收藏固定在最前面，不在这里调整。", fontSize = 18.sp, color = AppText2)
        Spacer(Modifier.height(6.dp))
        orderedScenes.forEachIndexed { i, s ->
            val isHidden = s.id in hiddenIds
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${s.icon} ${s.name}",
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isHidden) AppText2 else AppText,
                    modifier = Modifier.weight(1f),
                )
                ArrowButton("↑", enabled = i > 0) { onMove(s.id, -1) }
                Spacer(Modifier.width(8.dp))
                ArrowButton("↓", enabled = i < orderedScenes.lastIndex) { onMove(s.id, +1) }
                Spacer(Modifier.width(8.dp))
                ShowHideButton(isHidden) { onSetVisible(s.id, isHidden) }
            }
            if (i < orderedScenes.lastIndex) {
                HorizontalDivider(color = AppLine, thickness = 1.dp)
            }
        }
        Spacer(Modifier.height(24.dp))

        /* ---------- 完成（唯一出口） ---------- */
        Box(
            Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(BlueDark)
                .clickable(onClick = onDone),
            contentAlignment = Alignment.Center,
        ) {
            Text("完成", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
        Spacer(Modifier.height(18.dp))
    }
}

/** v14 学习统计的分区进度行：icon+名称 与 已学/总数 同行，下方 10dp 细进度条
 *  （BlueDark 填充 / AppLine 轨道；total 为 0 时按 0 进度渲染，防除零） */
@Composable
private fun SceneProgressRow(p: SceneProgress) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${p.scene.icon} ${p.scene.name}",
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = AppText,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${p.learned}/${p.total}",
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = if (p.learned >= p.total) GreenKnown else AppText2,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(AppLine),
        ) {
            val fraction = if (p.total > 0) p.learned.toFloat() / p.total else 0f
            if (fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(BlueDark),
                )
            }
        }
    }
}

/** v6 R14 抽出：配额单选组（①每日总量/②每日新词两组同构）——64dp 高档位块、横向滚动、选中蓝系高亮 */
@Composable
private fun QuotaSelector(selected: Int, options: List<Int>, onSelect: (Int) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEach { n ->
            val isSelected = n == selected
            Box(
                Modifier
                    .height(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) BlueBg else AppSurface)
                    .border(2.dp, if (isSelected) BlueDark else AppLine, RoundedCornerShape(14.dp))
                    .clickable { onSelect(n) }
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$n",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isSelected) BlueDark else AppText2,
                )
            }
        }
    }
}

/** v6 设置页 ↑/↓ 顺序按钮：56×64dp 触控；边界（已到顶/底）置灰且不可点 */
@Composable
private fun ArrowButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 56.dp, height = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) BlueBg else AppSurface)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            color = if (enabled) BlueDark else AppLine,
        )
    }
}

/**
 * v6 设置页显示/隐藏开关：88×64dp 大按钮。隐藏中的分区按钮显示「显示」（橙系，引导恢复），
 * 可见分区显示「隐藏」（绿系）——颜色本身也承担状态说明（不依赖小字识别）。
 */
@Composable
private fun ShowHideButton(isHidden: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 88.dp, height = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isHidden) OrangeBg else GreenBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (isHidden) "显示" else "隐藏",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = if (isHidden) OrangeDark else GreenKnown,
            textAlign = TextAlign.Center,
        )
    }
}
