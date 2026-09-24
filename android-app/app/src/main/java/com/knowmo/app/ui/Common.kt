package com.knowmo.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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
import com.knowmo.app.ui.theme.ButtonShape
import com.knowmo.app.ui.theme.CardShapeLarge
import com.knowmo.app.ui.theme.ControlShape
import com.knowmo.app.ui.theme.GreenBg
import com.knowmo.app.ui.theme.GreenKnown
import com.knowmo.app.ui.theme.OrangeBg
import com.knowmo.app.ui.theme.OrangeDark
import com.knowmo.app.ui.theme.PageGutter
import com.knowmo.app.ui.theme.StarGold
import com.knowmo.app.ui.theme.cardShadow

/* ---------- 频道 Tab（参考抖音顶部 tab） ---------- */

/** v6 隐藏设置入口：推荐 tab 连点目标次数（prd v6 第 1 条） */
private const val SETTING_TAP_TARGET = 5

/** v6 隐藏设置入口：连点间隔上限（ms），超时重置计数 */
private const val SETTING_TAP_GAP_MS = 2000L

/* ---------- v6 空收藏引导（引导卡与播报同源；AppRoot.cardSpeech 引用 FAV_GUIDE_SPEECH） ---------- */
// v23：TITLE/BODY 改公开——AppRoot 渲染 GuideCard 时按空态类型选文案（零词库 vs 空收藏）

const val FAV_GUIDE_TITLE = "还没有收藏的词"
const val FAV_GUIDE_BODY = "学习时点卡片右上角的星星，就能把词收进来。"
val FAV_GUIDE_SPEECH = "$FAV_GUIDE_TITLE。$FAV_GUIDE_BODY。"

/** v23 零词库空态（裸装未导入任何词库时的推荐频道/空频道引导；文案与播报同源）
 *  2026-09-23 修复 #1：官方词库已随 APK 分发，空态卡上直接给「一键导入」按钮，
 *  文案从「去设置导文件」改为引导点按钮——对够不着 GitHub 的家属是决定性的一步。 */
const val EMPTY_BANK_GUIDE_TITLE = "这里还没有内容"
const val EMPTY_BANK_GUIDE_BODY = "点下面的按钮，先把官方词库装进来。家人也可以在设置里导入自己做的词库。"
val EMPTY_BANK_GUIDE_SPEECH = "$EMPTY_BANK_GUIDE_TITLE。$EMPTY_BANK_GUIDE_BODY。"

/** 2026-09-23 复审修复 #2：词库在但推荐可见范围为空（全部分区被隐藏）的推荐频道空态。
 *  勿与零词库混淆（词库存在，引导去**开启**分区而非导入）；文案与播报同源。 */
const val HIDDEN_ALL_GUIDE_TITLE = "还没有可学的词"
const val HIDDEN_ALL_GUIDE_BODY = "词库都在设置里隐藏了，请家人打开设置，开启要学的分区。"
val HIDDEN_ALL_GUIDE_SPEECH = "$HIDDEN_ALL_GUIDE_TITLE。$HIDDEN_ALL_GUIDE_BODY。"

/**
 * 频道 Tab（参考抖音顶部 tab）。
 * 渲染顺序：**推荐（固定第一）→ 收藏（固定第二）→ 可见场景分区**
 * （`scenes` = 设置过滤排序后传入；显隐与排序只管场景分区，固定频道不参与）。
 * v23：固定频道只剩两个——「常用词」固定频道随 daily 分区退役删除（词库全面外置后
 * CSV 派生 id 不可能等于 daily，该频道永远是空的；那份词进 `常用字词.csv` 走普通导入）。
 *
 * **固定频道的「不可移除」来源**：推荐与收藏不在 `SCENES`（无 Scene 项可开关）。
 *
 * 已完成的分区（该区每个词间隔天数都 >= 15，v16 起面向用户的文案统一称「学完」）加「学完」
 * 后缀、文字转绿，但仍可点进去自主复习；区内任何一词「忘了」清零即即时回退。
 * 收藏 tab 不参与该判定（fav 不在 SCENES，graduatedScenes 天然不含它）。
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
    val scroll = rememberScrollState()

    Row(
        Modifier
            .fillMaxWidth()
            // edgeFade 必须排在 horizontalScroll 之前：此处的绘制尺寸 = 可视窗口，渐隐贴在屏幕两端而非内容两端
            .edgeFade(scroll)
            .horizontalScroll(scroll)
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
        // 可见场景分区（设置过滤 + 排序后传入；AppRoot 在当前频道被隐藏时回退推荐）
        // v23：全部为导入的自定义分区（零词库时此列表为空，频道栏只剩推荐/收藏两个固定 chip）
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

/** 频道栏两端渐隐宽度 */
private val EDGE_FADE = 28.dp

/**
 * 横滑频道栏两端渐隐：分区多到一屏放不下时，被裁切的 chip 渐隐进页底，提示「这边还能滑」——
 * 生硬的裁切边对老年用户不构成可滑暗示。只在该方向**确实还能滑**时才画，滑到头即消失。
 */
private fun Modifier.edgeFade(state: ScrollState): Modifier = drawWithContent {
    drawContent()
    val w = EDGE_FADE.toPx()
    if (state.canScrollBackward) {
        drawRect(
            Brush.horizontalGradient(listOf(AppSurface, Color.Transparent), startX = 0f, endX = w),
            size = Size(w, size.height),
        )
    }
    if (state.canScrollForward) {
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.Transparent, AppSurface),
                startX = size.width - w,
                endX = size.width,
            ),
            topLeft = Offset(size.width - w, 0f),
            size = Size(w, size.height),
        )
    }
}

/**
 * 频道 chip（v6 抽出：推荐 / 收藏 / 场景分区三种共用同一渲染）。
 * 选中态用**实心蓝底白字**（对比浅底深字的旧样式，老年用户更容易识别「当前在哪个频道」）；
 * 未选中 = 白色胶囊浮在暖米色页面底上。
 * v21.1：QYJ 反馈恢复 v21 之前的样式——撤销 v21 的未选中 1dp 描边与选中态底部白色指示条
 * （指示条连带把胶囊从 Row 撑成了 Column、内边距 12→10、间距 8→10，一并回退）。
 * 静态外观保持 v21.1 定稿，只给选中切换加底色/字色过渡，避免蓝块"瞬移"。
 */
@Composable
private fun ChannelChip(
    label: String,
    active: Boolean,
    isGraduated: Boolean,
    onClick: () -> Unit,
    star: Boolean = false,
) {
    val bg by animateColorAsState(if (active) BluePrimary else Color.White, label = "chipBg")
    val fg by animateColorAsState(
        when {
            active -> Color.White
            isGraduated -> GreenKnown
            else -> AppText2
        },
        label = "chipFg",
    )
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
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
            color = fg,
        )
    }
}

/* ---------- 共享小组件 ---------- */

/** 按下时的缩放比例：幅度小到不影响点准，但足以让手指感到「按下去了」 */
private const val PRESSED_SCALE = 0.96f

/**
 * 按压回弹：按下微缩、松手弹回。老年用户点按常犹豫「到底按到没有」，
 * 涟漪在深色实心按钮上几乎看不见，缩放是更直接的触感反馈。
 * 须与 `clickable(interactionSource = source, ...)` 共用同一个 source，并排在 shadow/clip 之前（投影随按钮一起缩）。
 */
@Composable
fun Modifier.pressScale(source: InteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, label = "pressScale")
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 实心大按钮（作答「认识/忘了」、设置页「导入 / 完成」共用）：白字 + 可选图标 + 按压回弹。
 * 投影取按钮自身的颜色而非黑色：暖米底上的黑影发灰发脏，同色光晕更通透。
 * 图标不设 contentDescription：同排文字已表达同一语义，重复朗读只是噪声。
 */
@Composable
fun SolidButton(
    label: String,
    container: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 64.dp,
    fontSize: TextUnit = 22.sp,
    iconSize: Dp = 28.dp,
    shape: Shape = ButtonShape,
) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .pressScale(source)
            .shadow(6.dp, shape, spotColor = container, ambientColor = container)
            .clip(shape)
            .background(container)
            .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(iconSize))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, fontSize = fontSize, fontWeight = FontWeight.Black, color = Color.White)
    }
}

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
 * 卡头标识胶囊的统一外形（v19，QYJ 2026-09-21）：**「新学/复习/看看」徽标与收藏按钮共用这一份数值**。
 *
 * 抽成 token 而不是各写一遍的理由：QYJ 要求收藏按钮「外形和新学标识一样」，而外形一致性
 * 只靠注释承诺必然漂移（[GuideCard] 那句「与卡内收藏按钮同款」在 v18 就已经漂了）。
 * 改这里 = 徽标与收藏按钮同时改。
 */
internal object HeaderPill {
    /** 全圆角胶囊（50% 圆角被高度截断 ⇒ 端部呈半圆；高度 > 宽度时退化为正圆）。 */
    val shape = RoundedCornerShape(50)
    val fontSize = 17.sp
    val padH = 14.dp
    val padV = 5.dp
}

/**
 * 词条角标：三态。
 * 新学=蓝 / 复习=橙 / 浏览=绿（绿色=正面回炉，与「认识」配色同系，不与另两态冲突）。
 * v5 R10：浏览态文案「温故」→「看看」——同一徽标要同时服务推荐频道的温故流与场景分区，
 * 而分区池含**未学词**，叫「温故」不成立。
 * 徽标只放文字不放 emoji：emoji 在小字号下发虚，老年用户更难辨认。
 * v19：外形数值改取 [HeaderPill]，与收藏按钮锁死同款。
 */
@Composable
fun TermBadge(mode: CardMode) {
    val (bg, fg, label) = when (mode) {
        CardMode.NEW -> Triple(BlueBg, BlueDark, "新学")
        CardMode.REVIEW -> Triple(OrangeBg, OrangeDark, "复习")
        CardMode.FREE -> Triple(GreenBg, GreenKnown, "看看")
    }
    Surface(color = bg, shape = HeaderPill.shape) {
        Text(
            label,
            fontSize = HeaderPill.fontSize,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = HeaderPill.padH, vertical = HeaderPill.padV),
        )
    }
}

/**
 * 词卡收藏按钮（v19 改版，QYJ 2026-09-21：外形与同排 [TermBadge] **同款小胶囊**）。
 *
 * 旧版是 56dp 圆角矩形方块（v18）——与徽标并列时是两套视觉语言（方块 vs 文字胶囊）；
 * 现在卡头读作「左徽标 + 右收藏」一对同款胶囊。
 *
 * ⚠️ 两条硬约束，改这个函数前先读：
 * 1. **外形一律取 [HeaderPill]**，不要在此另写形状/字号/内边距；
 * 2. **触摸目标保持 ≥48dp** —— 视觉胶囊只有约 30dp 高，对老年用户手指偏小，故在
 *    `clickable` **之后**再叠一层透明 padding 把热区向外撑到约 48dp：按修饰符顺序，
 *    `clickable` 写在 padding 之前 ⇒ 它覆盖的是「含 padding 的整块」。涟漪因此略大于
 *    可见胶囊，这是刻意的——多出来的一圈本身就在提示「这里能点」。
 *
 * 配色沿用 v6 口径：米底、收藏 = 金星、未收藏 = 灰星。**文字恒为 AppText2**——
 * StarGold 在米底 AppSurface 上只有约 1.75:1，做不了正文色（AppText2 同底 8.9:1）；
 * 收藏态靠「星色 + 文案」双通道区分，不靠文字变色。
 */
@Composable
fun FavoriteButton(isFavorite: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(HeaderPill.shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .clip(HeaderPill.shape)
                .background(AppSurface)
                .padding(horizontal = HeaderPill.padH, vertical = HeaderPill.padV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Star,
                // 图标不占朗读位：可见文字「收藏 / 已收藏」已表达同一语义，重复朗读只是噪声
                contentDescription = null,
                tint = if (isFavorite) StarGold else AppText2,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (isFavorite) "已收藏" else "收藏",
                fontSize = HeaderPill.fontSize,
                fontWeight = FontWeight.Bold,
                color = AppText2,
            )
        }
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
 * v6 空态引导卡；v23 起文案参数化（title/body 由调用方按空态类型选择）：
 * - 空收藏池（fav）→ FAV_GUIDE_*（原口径，仅收藏频道可达）；
 * - **零词库**（裸装未导入任何词库，推荐/其他频道空池可达）→ EMPTY_BANK_GUIDE_*（引导去导入）。
 * 视觉与词条卡/完成卡同语言（白卡 + 投影）。图标按空态类型传入：空收藏 = 米底金星（缺省）；
 * 需要去设置/导入处理的空态用蓝系图标，与主按钮同色，暗示「从这里操作」。
 * 2026-09-23 修复 #1：新增可选动作按钮（零词库空态的「一键导入官方词库」）——
 * 蓝底白字大按钮与设置页同语言，≥64dp 适老触摸目标。
 * ⚠️ 图标参数必须排在 onAction **之前**：调用点用尾随 lambda 传 onAction。
 */
@Composable
fun GuideCard(
    title: String,
    body: String,
    actionLabel: String? = null,
    icon: ImageVector = Icons.Filled.Star,
    iconTint: Color = StarGold,
    haloColor: Color = AppSurface,
    onAction: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = PageGutter, vertical = 14.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .cardShadow(CardShapeLarge)
                .clip(CardShapeLarge)
                .background(Color.White)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(50))
                    .background(haloColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(44.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                title,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                color = AppText,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                body,
                fontSize = 24.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                color = AppText2,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(28.dp))
                SolidButton(
                    label = actionLabel,
                    container = BluePrimary,
                    onClick = onAction,
                    icon = Icons.Filled.Add,
                    height = 72.dp,
                    fontSize = 24.sp,
                    shape = ControlShape,
                )
            }
        }
        // ⚠️ 不渲染 SwipeHint（「上滑看下一个」）：空收藏池时引导卡是 feed 里唯一的页，
        // 引导上滑会误导（spec/frontend/component-guidelines.md 的方向语义约束）。
    }
}

/**
 * 顶部提醒横幅（2026-09-23 修复 #3）：TTS 引擎缺中文（App 全程无声）与媒体音量为 0
 * 这类「无声故障」的适老大字提示。橙底暖色（与「显示」按钮同系的警示色）+ 右侧动作按钮。
 * 独占一行、位于频道栏上方；一次只显示一条（缺中文优先——那个问题更致命）。
 */
@Composable
fun NoticeBanner(text: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(OrangeBg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = OrangeDark,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .height(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(BluePrimary)
                .clickable(onClick = onAction)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(actionLabel, fontSize = 19.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
    }
}
