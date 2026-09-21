package com.knowmo.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import com.knowmo.app.ui.theme.BluePrimary
import com.knowmo.app.ui.theme.GreenBg
import com.knowmo.app.ui.theme.GreenKnown
import com.knowmo.app.ui.theme.OrangeBg
import com.knowmo.app.ui.theme.OrangeDark
import com.knowmo.app.ui.theme.StarGold

/* ---------- 频道 Tab（参考抖音顶部 tab） ---------- */

/** v6 隐藏设置入口：推荐 tab 连点目标次数（prd v6 第 1 条） */
private const val SETTING_TAP_TARGET = 5

/** v6 隐藏设置入口：连点间隔上限（ms），超时重置计数 */
private const val SETTING_TAP_GAP_MS = 2000L

/* ---------- v6 空收藏引导（引导卡与播报同源；AppRoot.cardSpeech 引用 FAV_GUIDE_SPEECH） ---------- */

private const val FAV_GUIDE_TITLE = "还没有收藏的词"
private const val FAV_GUIDE_BODY = "学习时点卡片右上角的星星，就能把词收进来。"
val FAV_GUIDE_SPEECH = "$FAV_GUIDE_TITLE。$FAV_GUIDE_BODY。"

/**
 * 频道 Tab（参考抖音顶部 tab）。
 * 渲染顺序：**推荐（固定第一）→ 收藏（固定第二）→ 常用词（固定第三，v17）→ 可见场景分区**
 * （`scenes` = 设置过滤排序后传入；显隐与排序只管场景分区，固定频道不参与）。
 *
 * **固定频道的三种「不可移除」各有来源**：推荐与收藏不在 `SCENES`（无 Scene 项可开关）；
 * 常用词在 `SCENES` 里但被 `AppSettings.manageableIds()` 排除，故 order/hidden 都不含它、
 * 设置页也没有它的行 —— 用户**无从关闭**，这是 v17「默认频道显示、不可移除」的实现方式
 * （不是靠一个禁用态的开关）。见 StudyData.kt 的 `SCENES` 注释。
 *
 * 已完成的分区（该区每个词间隔天数都 >= 15，v16 起面向用户的文案统一称「学完」）加「学完」
 * 后缀、文字转绿，但仍可点进去自主复习；区内任何一词「忘了」清零即即时回退。
 * 收藏 tab 不参与该判定（fav 不在 SCENES，graduatedScenes 天然不含它）；常用词在 SCENES
 * 里故参与判定，但 72 条词全部 ≥15 天实际不可达 —— 保留该分支只为行为一致。
 * ⚠️ 代码标识符沿用 `graduated` / `GRADUATED_DAYS`（多处引用、且与产品措辞解耦），
 * 只把**用户能看到的文案**统一成「学完」—— 别为了对齐措辞去重命名标识符。
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 推荐（固定第一；连点 5 次打开设置）——不用图标，实心蓝底即可表达「当前所在」
        ChannelChip(
            label = "推荐",
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
        // 收藏（固定第二；不参与毕业 / 隐藏 / 排序）——金星图标与「推荐」区分（旧版两个 ⭐ 易混淆）
        ChannelChip(
            label = "收藏",
            active = current == StudyRepository.CHANNEL_FAV,
            isGraduated = false,
            star = true,
            onClick = { onSelect(StudyRepository.CHANNEL_FAV) },
        )
        // 常用词（固定第三，v17）：v9 起长期只作推荐的内容源、频道栏永不出现；v17 升为
        // **默认显示的固定频道**——恒在、不可移除（`AppSettings.manageableIds()` 排除它，
        // 故设置页无此行、order/hidden 不含它，用户无从关闭）。
        // 它同时仍是推荐范围的一部分（`StudyRepository.scopeIds` 的 CHANNEL_COMMON 特例），
        // 所以这 72 条词既会被推荐队列推给用户，也能在这里主动翻阅（池型频道，浏览零写入）。
        ChannelChip(
            label = if (StudyRepository.CHANNEL_COMMON in graduated) "常用词 学完" else "常用词",
            active = current == StudyRepository.CHANNEL_COMMON,
            isGraduated = StudyRepository.CHANNEL_COMMON in graduated,
            onClick = { onSelect(StudyRepository.CHANNEL_COMMON) },
        )
        // 可见场景分区（设置过滤 + 排序后传入；AppRoot 在当前频道被隐藏时回退推荐）
        scenes.forEach { s ->
            ChannelChip(
                // v16：完成的分区加**文字**后缀「学完」，不再用 🎓 emoji ——
                // 本文件早就写明「场景 chip 只放文字：彩色 emoji 混在实心蓝底/白底胶囊里显杂乱」，
                // 🎓 一直是那条规则的例外；且 emoji 在 20sp 小字下发虚，老年用户难辨认。
                // 代价：chip 变宽（4 字分区名 + 「学完」= 6 字），顶栏一屏能放的频道变少，靠横向滚动兜。
                // ⚠️ 与设置页的「学完 N 词」同一措辞：设置页是**词**级，这里是**分区**级（该区每个词都学完）。
                label = if (s.id in graduated) "${s.name} 学完" else s.name,
                active = s.id == current,
                isGraduated = s.id in graduated,
                onClick = { onSelect(s.id) },
            )
        }
    }
}

/**
 * 频道 chip（v6 抽出：推荐 / 收藏 / 场景分区三种共用同一渲染）。
 * 选中态用**实心蓝底白字**（对比浅底深字的旧样式，老年用户更容易识别「当前在哪个频道」）；
 * 未选中 = 白色胶囊浮在暖米色页面底上。
 */
@Composable
private fun ChannelChip(
    label: String,
    active: Boolean,
    isGraduated: Boolean,
    onClick: () -> Unit,
    star: Boolean = false,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) BluePrimary else Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (star) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = StarGold,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            fontSize = 20.sp,
            fontWeight = if (active) FontWeight.Black else FontWeight.Bold,
            color = when {
                active -> Color.White
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
 * 与新学卡同一呈现：词 + 逐字拼音 + 用途全展开、**无 √/×**、点卡重听、点单字读该字。
 * 它**不作答**，因此不写任何学习状态（`TermState` / `DayState` 零写入）——分区/收藏里的未学词
 * 只「看」不算学会，新词入口唯一化到每日任务。
 * ⚠️ R10 刻意**不新增也不重命名**成员：`FREE` 本义「自由刷」语义仍成立，而重命名要动 4 个文件的
 * 十余处引用，在本机无法编译的条件下不值这个风险。
 */
enum class CardMode { NEW, REVIEW, FREE }

/**
 * 词条角标：三态。
 * 新学=蓝 / 复习=橙 / 浏览=绿（绿色=正面回炉，与「认识」配色同系，不与另两态冲突）。
 * v5 R10：浏览态文案「温故」→「看看」——同一徽标要同时服务推荐频道的温故流与场景分区，
 * 而分区池含**未学词**，叫「温故」不成立。
 * 徽标只放文字不放 emoji：emoji 在小字号下发虚，老年用户更难辨认。
 */
@Composable
fun TermBadge(mode: CardMode) {
    val (bg, fg, label) = when (mode) {
        CardMode.NEW -> Triple(BlueBg, BlueDark, "新学")
        CardMode.REVIEW -> Triple(OrangeBg, OrangeDark, "复习")
        CardMode.FREE -> Triple(GreenBg, GreenKnown, "看看")
    }
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Text(
            label,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
        )
    }
}

/**
 * 上滑引导：箭头图标做 0→-8dp 的往返浮动动画——用**运动**提示可滑方向（老年用户对静态
 * 箭头的理解弱于动态暗示）；文案固定「上滑看下一个」（方向语义契约见 spec 组件规范）。
 */
@Composable
fun SwipeHint() {
    val transition = rememberInfiniteTransition(label = "swipeHint")
    val dy by transition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "dy",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.KeyboardArrowUp,
            contentDescription = null,
            tint = AppText2,
            modifier = Modifier
                .size(28.dp)
                .offset(y = dy.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text("上滑看下一个", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppText2)
    }
}

/**
 * v6 空收藏引导卡（**仅收藏频道空池可达**——场景分区池 = 全区词必非空，design.md §11.2）：
 * 大字引导文案，与播报同源（`FAV_GUIDE_SPEECH`，prd v6 第 3 条）。
 * 视觉与词条卡/完成卡同语言（白卡 + 投影）；星标用与卡内收藏按钮**同款**的
 * 米色圆底 + 金星——空状态图标即按钮的真实样子，降低「去哪点星星」的理解成本。
 */
@Composable
fun GuideCard() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(50))
                    .background(AppSurface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = StarGold,
                    modifier = Modifier.size(44.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
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
        }
        // ⚠️ 不渲染 SwipeHint（「上滑看下一个」）：空收藏池时引导卡是 feed 里唯一的页，
        // 引导上滑会误导（spec/frontend/component-guidelines.md 的方向语义约束）。
    }
}
