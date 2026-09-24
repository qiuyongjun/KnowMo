package com.knowmo.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.knowmo.app.data.AppSettings
import com.knowmo.app.data.CustomBank
import com.knowmo.app.data.CustomBanks
import com.knowmo.app.data.Scene
import com.knowmo.app.data.SceneProgress
import com.knowmo.app.data.StudyStats
import com.knowmo.app.ui.theme.AppLine
import com.knowmo.app.ui.theme.AppSurface
import com.knowmo.app.ui.theme.AppText
import com.knowmo.app.ui.theme.AppText2
import com.knowmo.app.ui.theme.BluePrimary
import com.knowmo.app.ui.theme.CardShapeMedium
import com.knowmo.app.ui.theme.ControlShape
import com.knowmo.app.ui.theme.GreenBg
import com.knowmo.app.ui.theme.GreenKnown
import com.knowmo.app.ui.theme.OrangeBg
import com.knowmo.app.ui.theme.OrangeDark
import com.knowmo.app.ui.theme.PageGutter
import com.knowmo.app.ui.theme.cardShadow
import kotlin.math.roundToInt

/** 分区行高：**必须固定** —— 拖动让位是按「行高 × 跨过几行」算位移的，行高不固定算法即失效 */
private val SCENE_ROW_H = 76.dp

/** 底部固定「完成」条的内容高度（12 + 80 + 12）——滚动内容末尾按这个数留白，免得最后一段被盖住。
 *  导航栏那一截由 Column 的 `navigationBarsPadding()` 补上（见下），故此处不含它。 */
private val DONE_BAR_SPACE = 104.dp

/**
 * 全屏设置页（v6，design.md §11.2）：由「推荐」tab **连点 5 次**（间隔 ≤ 2s）唤出的隐藏入口，
 * 不占日常界面。覆盖 feed 的全屏 overlay。
 * 适老化硬约束：字号 ≥ 20sp（说明性小字 17sp）、可点目标 ≥ 64dp 高；配色沿用主题常量。
 * 视觉语言与 feed 同源：暖米色页底 + 白卡分组（每组一张卡）+ 选中态实心蓝，箭头用矢量图标。
 *
 * 四张卡：
 * ⓪**学习统计**（v14，只读）：总览（已学/学完/收藏）+ 今日战果 + 连续学习天数 + 分档复习记住率小字（v28，ReviewLog）。
 *   受众拍板 = 家属/年轻人（QYJ：设置本来就不给老人用），信息密度不受「少而大」约束；
 *   纯展示零写入（进出设置页不改学习状态，池型零写入契约不破坏）。
 * ①②每日学习词数量（3/5/10/15/20，缺省 10）+ 每天学几个新词（1/3/5/10，缺省 5）—— 两组同为配额语义、
 *   合一张卡；**当日队列冻结不变、次日生效**（只写 `AppSettings`，不触碰当日队列）。
 * ②.5 **我的词库**（v22）：SAF 导入 JSON 词库文件 → 一个普通自定义分区（默认隐藏）；
 *   已装库列表 + 两段确认删除；fail-closed 校验（见 `CustomBank.kt`），不合格整库拒绝并逐条报错。
 * ③④**分区显示与顺序**（v16 合并为一张卡）：每行 = 分区名 + 进度 + 显隐开关，
 *   分「已显示 / 未显示」两段。v17（QYJ 2026-09-21）：
 *   **↑↓ 按钮删除**（点按箭头是拖动之外的第三种重排方式，实测多余——只留拖动）；
 *   **未显示的分区不挂拖动手势**（隐藏态没有顺序语义，拖了也没意义）；
 *   **新开启显示的分区自动排到「已显示」段末尾**（= 未显示段之前，落位统一在
 *   `AppSettings.setSceneVisible` 里做，UI 不感知）。
 *
 * v16 本轮重构（QYJ 2026-09-21）—— 原版实测 ≈ 2530dp ≈ 3.5 屏，其中约七成是**同一批分区被列了两遍**
 * （统计里 14 行进度条 + 管理里 13 行操作行），出口按钮还在第 3.5 屏。逐条修法：
 * 1. **合并两处分区列表**：分区进度不再是统计卡里的一串行，而是分区卡每行的进度部分 ——
 *    省掉一整批重复行，也让「这区学到哪了」和「要不要显示它」在同一处决策。
 * 2. **出口常驻**：底部「完成」改为固定在页面底部（滚动内容末尾留 [DONE_BAR_SPACE] 占位），
 *    中途想退出不必滚到底。
 * 3. **可见分区自动排前面**：渲染顺序 = 可见组（按 order）+ 隐藏组（按 order），
 *    用户不必在「想看的」和「已关掉的」之间翻找。
 * 4. **已显示段内长按拖动重排**：拖动仅在「已显示」段内生效 —— 跨段意味着改变可见性，
 *    那是开关的职责，不做隐式迁移。未显示段不参与拖动（v17，QYJ 拍板）；
 *    开关显隐时的落位（开 → 已显示段末尾；关 → 未显示段开头）由持久层统一处理。
 *
 * ⚠️ 拖动实现的两个前提，改动前务必先读：
 * - **行高固定**（[SCENE_ROW_H]）：让位位移 = ±行高，行高变了算法要跟着改；
 * - **拖动的落点换算成「目标分区在 order 里的下标」再落库**：渲染是分段的、order 是全局的，
 *   但**同组项在 order 中的相对顺序与渲染顺序一致**，所以段内搬下标即可，无需感知分组语义。
 */
@Composable
fun SettingsScreen(
    orderedScenes: List<Scene>,
    hiddenIds: Set<String>,
    quota: Int,
    quotaNew: Int,
    stats: StudyStats,   // v14 只读快照（AppRoot 在「打开设置页」与「显隐变化」时各重取一次）
    customBanks: List<CustomBank>,   // v22「我的词库」镜像（AppRoot 在导入/删除回调里重读）
    onSetVisible: (String, Boolean) -> Unit,
    onMoveTo: (String, Int) -> Unit,      // v16 拖动落位：目标分区在 order 里的下标
    onSetQuota: (Int) -> Unit,
    onSetQuotaNew: (Int) -> Unit,
    onBanksChanged: (List<String>) -> Unit,  // 导入/删除后统一刷新；参数 = 首次入库的库 id 列表
    onDone: () -> Unit,
) {
    val visibleScenes = orderedScenes.filter { it.id !in hiddenIds }
    val hiddenScenes = orderedScenes.filter { it.id in hiddenIds }
    val progressById = stats.scenes.associateBy { it.scene.id }

    // v22「我的词库」会话态：导入结果文案（importOk 区分成功/失败配色）+ 删除的两段确认
    // v22.1：字节入口 + 取 DISPLAY_NAME；v24（QYJ 2026-09-23）起 DISPLAY_NAME 只用于
    // 生成命名对话框的预填建议——库名 = 库身份，由用户命名决定（见 CustomBanks KDoc）
    val context = LocalContext.current
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }
    // 更新官方词库的两段确认（2026-09-24 修复 #3）：更新会覆盖已装的同名库（含家属自己
    // 导入的同名 CSV），必须第二击确认，不做成一按就覆盖。
    var confirmUpdateOfficial by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var importOk by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<PendingImport?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            val fileName = runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
                }
            }.getOrNull().orEmpty()
            when {
                bytes == null -> {
                    importOk = false
                    importMessage = "读不到文件内容，请重试。"
                }
                // .csv 扩展名兜底（MIME 白名单里 octet-stream 仍可能选到别的文件）：
                // 库 id 不再依赖文件名，格式校验留在选文件这一步（原在 CustomBanks.import）
                !fileName.endsWith(".csv", ignoreCase = true) -> {
                    importOk = false
                    importMessage = "仅支持 CSV 词库文件：用 Excel 填三列（词 / 拼音 / 用途），另存为 CSV 即可"
                }
                // v24：选好文件先暂存，弹命名对话框——确认库名后才入库
                else -> pendingImport = PendingImport(bytes, CustomBanks.suggestBankName(fileName))
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(AppSurface),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                // ⚠️ inset padding 必须排在 verticalScroll **之前**：滚动视口由 verticalScroll 决定，
                // 写在它之后的 padding 只作用于内容内边距 —— 内容会滚到状态栏底下被遮挡。
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                // navigationBarsPadding 排在 verticalScroll **之后**：它在这里是**内容底部内边距**，
                // 与末尾的 DONE_BAR_SPACE 合起来正好等于常驻「完成」条的实际占位
                //（104dp + 导航栏高度）—— 滚到底时最后一行不会被按钮压住。
                .navigationBarsPadding()
                .padding(horizontal = PageGutter),
        ) {
            Spacer(Modifier.height(18.dp))
            Text("设置", fontSize = 32.sp, fontWeight = FontWeight.Black, color = AppText)
            Spacer(Modifier.height(16.dp))

            /* ---------- ⓪ 学习统计（只读：总览 + 今日战果 + 分档复习记住率） ---------- */
            SettingsCard {
                CardTitle("学习统计")
                Spacer(Modifier.height(14.dp))
                // v21 指标格重排：原是一大段行内数字墙（一行里蓝绿蓝三色数字连排，扫读时
                // 不知道哪个数字配哪个词），改为三格"数字在上、标签在下"的指标块，
                // 竖分隔线分隔——先扫大数字、再看它叫什么，信息层级清楚
                Row(Modifier.fillMaxWidth()) {
                    StatCell("已学", "${stats.learned}", "/ ${stats.totalWords}", BluePrimary, Modifier.weight(1f))
                    StatDivider()
                    StatCell("学完", "${stats.graduated}", "词", GreenKnown, Modifier.weight(1f))
                    StatDivider()
                    StatCell("收藏", "${stats.favorites}", "个", BluePrimary, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                // 分母口径说明（v16，v23 起无内置常用词）：不是全词库条数，而是「当前开启的分区」——
                // 不写清楚的话，用户隐藏一个分区、看见分母变小时会以为数据丢了。
                Text(
                    "分母 = 当前可学的词（开启的 ${visibleScenes.size} 个分区）；" +
                        "关掉的分区不计入，但它们已学的进度仍保留。",
                    fontSize = 17.sp,
                    color = AppText2,
                )
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = AppLine, thickness = 1.dp)
                Spacer(Modifier.height(14.dp))
                // 今日战果：同样三格指标，与总览同语言
                Row(Modifier.fillMaxWidth()) {
                    StatCell("今日认识", "${stats.todayKnownWords}", "个词", BluePrimary, Modifier.weight(1f))
                    StatDivider()
                    StatCell("忘了", "${stats.todayForgot}", "次", OrangeDark, Modifier.weight(1f))
                    StatDivider()
                    StatCell("连续学习", "${stats.streak}", "天", GreenKnown, Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                // v28 分档复习记住率（调参用，QYJ 看的小字，来自 ReviewLog 作答日志）：
                // 只统计隔天后的复习检验（elapsed ≥ 1），按作答时的实际间隔分档；
                // 没有任何隔天复习记录时给占位文案，不显示一排 0%
                Text(
                    run {
                        val line = stats.retention.filter { it.tests > 0 }
                            .joinToString("；") {
                                "${it.label}：${it.known * 100 / it.tests}%（${it.known}/${it.tests}）"
                            }
                        if (line.isEmpty()) "复习记住率：学几天后，这里会按间隔分档显示真实记住率。"
                        else "复习记住率（按间隔）：$line"
                    },
                    fontSize = 17.sp,
                    color = AppText2,
                )
            }
            Spacer(Modifier.height(14.dp))

            /* ---------- ①② 每日学习量（总量 + 新词，同为配额语义合一张卡） ---------- */
            SettingsCard {
                CardTitle("每日学习词数量")
                Text("改完明天生效，今天学的不变。", fontSize = 17.sp, color = AppText2)
                Spacer(Modifier.height(10.dp))
                QuotaSelector(selected = quota, options = AppSettings.QUOTA_OPTIONS, onSelect = onSetQuota)
                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = AppLine, thickness = 1.dp)
                Spacer(Modifier.height(18.dp))
                CardTitle("每天学几个新词")
                Text("新词固定几个，不被复习挤掉。改完明天生效。", fontSize = 17.sp, color = AppText2)
                Spacer(Modifier.height(10.dp))
                QuotaSelector(selected = quotaNew, options = AppSettings.NEW_QUOTA_OPTIONS, onSelect = onSetQuotaNew)
            }
            Spacer(Modifier.height(14.dp))

            /* ---------- ②.5 我的词库（v22：SAF 导入 JSON 词库文件 → 一个普通自定义分区） ---------- */
            SettingsCard {
                CardTitle("我的词库")
                Text(
                    "「一键导入官方词库」只装还没装的库，不会覆盖改过的库；要刷新官方词库用下面的「更新官方词库」。" +
                        "自己做的库用 Excel 填三列「词 / 拼音 / 用途」另存为 CSV，点「导入词库文件」起个名字就成。" +
                        "想更新自己装的库，导入时用同一个名字（会提示将替换哪个库）。" +
                        "拼音列可以不填，会自动标注（多音字建议核对）。",
                    fontSize = 17.sp,
                    color = AppText2,
                )
                Spacer(Modifier.height(10.dp))
                if (customBanks.isEmpty()) {
                    Text("还没有导入的词库。", fontSize = 17.sp, color = AppText2)
                    Spacer(Modifier.height(8.dp))
                } else {
                    customBanks.forEach { bank ->
                        val confirming = confirmDeleteId == bank.id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 库名与条数分两行：条数降为次级小字，库名不再被括号挤到截断
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${bank.icon} ${bank.name}",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text("${bank.terms.size} 条", fontSize = 16.sp, color = AppText2)
                            }
                            // 两段确认删除（第一击变「确认删除」，再击才删）——少一层弹窗交互
                            DeleteBankButton(confirming = confirming) {
                                if (confirming) {
                                    CustomBanks.delete(bank.id)
                                    confirmDeleteId = null
                                    onBanksChanged(emptyList())
                                } else {
                                    confirmDeleteId = bank.id
                                }
                            }
                        }
                        if (bank.id != customBanks.last().id) {
                            HorizontalDivider(color = AppLine, thickness = 1.dp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(10.dp))
                // 一键导入官方词库（2026-09-23 修复 #1）：官方 CSV 已随 APK assets 分发。
                // 2026-09-24 修复 #3：**只装还没装的库**（skipInstalled）——家属可能用与官方库
                // 相同的名字导过自己的 CSV（设置页文案就是这么教更新方式的），一键导入若照旧
                // 无条件入库，会把家属改过的库用官方版悄悄覆盖。更新走下面的「更新官方词库」。
                SolidButton(
                    label = "一键导入官方词库",
                    container = BluePrimary,
                    icon = Icons.Filled.Add,
                    shape = ControlShape,
                    onClick = {
                        confirmDeleteId = null
                        confirmUpdateOfficial = false
                        val result = CustomBanks.importOfficial(context, skipInstalled = true)
                        val termCount = result.imported.sumOf { it.bank.terms.size }
                        // 跨库重词跳过总数（2026-09-24 修复 #8）：与手动导入同口径，不再静默丢词
                        val skippedRepeatCount = result.imported.sumOf { it.skippedRepeats }
                        importOk = result.imported.isNotEmpty() || result.skippedExisting > 0
                        importMessage = when {
                            result.imported.isEmpty() && result.skippedExisting == 0 && result.failed.isEmpty() ->
                                "没有找到官方词库文件。"
                            result.imported.isEmpty() && result.skippedExisting == 0 ->
                                "官方词库导入失败：" + result.failed.joinToString("；")
                            result.imported.isEmpty() ->
                                "官方词库都已装过了，没有要新装的。"
                            else ->
                                "新装了 ${result.imported.size} 个官方词库，共 $termCount 条词。" +
                                    (if (skippedRepeatCount > 0) "另有 $skippedRepeatCount 条与已装词库重复，已跳过。" else "") +
                                    (if (result.skippedExisting > 0) "已装的 ${result.skippedExisting} 个保持原样。" else "") +
                                    (if (result.failed.isNotEmpty()) "另有 ${result.failed.size} 个失败：${result.failed.joinToString("；")}" else "")
                        }
                        onBanksChanged(result.imported.filter { it.isNew }.map { it.bank.id })
                    },
                )
                Spacer(Modifier.height(10.dp))
                // 更新官方词库（2026-09-24 修复 #3）：显式替换更新入口（skipInstalled = false，
                // 已装的同 id 库走替换更新、进度保留），两段确认防误触覆盖家属自己导入的同名库
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(ControlShape)
                        .background(if (confirmUpdateOfficial) OrangeBg else AppSurface)
                        .clickable {
                            confirmDeleteId = null
                            if (confirmUpdateOfficial) {
                                val result = CustomBanks.importOfficial(context)
                                val termCount = result.imported.sumOf { it.bank.terms.size }
                                val skippedRepeatCount = result.imported.sumOf { it.skippedRepeats }
                                confirmUpdateOfficial = false
                                importOk = result.imported.isNotEmpty()
                                importMessage = when {
                                    result.imported.isEmpty() && result.failed.isEmpty() ->
                                        "没有找到官方词库文件。"
                                    result.imported.isEmpty() ->
                                        "官方词库更新失败：" + result.failed.joinToString("；")
                                    else ->
                                        "官方词库已更新 ${result.imported.size} 个，共 $termCount 条词。" +
                                            (if (skippedRepeatCount > 0) "另有 $skippedRepeatCount 条与已装词库重复，已跳过。" else "") +
                                            (if (result.failed.isNotEmpty()) "另有 ${result.failed.size} 个失败：${result.failed.joinToString("；")}" else "")
                                }
                                onBanksChanged(result.imported.filter { it.isNew }.map { it.bank.id })
                            } else {
                                confirmUpdateOfficial = true
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (confirmUpdateOfficial) "再点一次，确认更新官方词库" else "更新官方词库",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = if (confirmUpdateOfficial) OrangeDark else AppText2,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(ControlShape)
                        .background(AppSurface)
                        .clickable {
                            confirmDeleteId = null
                            confirmUpdateOfficial = false
                            // 白名单要兜住各应用给 CSV 标的 MIME：微信常见 text/x-csv 与
                            // application/vnd.ms-excel，缺了会在文件选择器里显示灰色不可选
                            importLauncher.launch(
                                arrayOf(
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "text/x-csv",
                                    "text/plain",
                                    "application/octet-stream",
                                    "application/vnd.ms-excel",
                                ),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("导入词库文件", fontSize = 22.sp, fontWeight = FontWeight.Black, color = AppText2)
                }
                importMessage?.let { msg ->
                    Spacer(Modifier.height(10.dp))
                    ImportResultNote(msg, importOk)
                }
            }
            Spacer(Modifier.height(14.dp))

            /* ---------- ③④ 分区显示与顺序（v16：进度 + 显隐 + 排序合并成一张卡，分两段） ---------- */
            SettingsCard {
                CardTitle("分区显示与顺序")
                Text(
                    "长按上面已显示的分区可以拖动排序；未显示的不能拖。点右边的按钮决定它显不显示。",
                    fontSize = 17.sp,
                    color = AppText2,
                )
                Spacer(Modifier.height(16.dp))

                SceneSectionHeader("已显示", visibleScenes.size)
                if (visibleScenes.isEmpty()) {
                    Text(
                        "现在只显示推荐和收藏。点下面「未显示」里的按钮可以打开某个分区。",
                        fontSize = 17.sp,
                        color = AppText2,
                    )
                    Spacer(Modifier.height(8.dp))
                } else {
                    SceneRows(
                        scenes = visibleScenes,
                        isHiddenGroup = false,
                        allOrdered = orderedScenes,
                        progressById = progressById,
                        onSetVisible = onSetVisible,
                        onMoveTo = onMoveTo,
                    )
                }
                Spacer(Modifier.height(16.dp))

                SceneSectionHeader("未显示", hiddenScenes.size)
                if (hiddenScenes.isEmpty()) {
                    Text("所有分区都已显示。", fontSize = 17.sp, color = AppText2)
                    Spacer(Modifier.height(8.dp))
                } else {
                    SceneRows(
                        scenes = hiddenScenes,
                        isHiddenGroup = true,
                        allOrdered = orderedScenes,
                        progressById = progressById,
                        onSetVisible = onSetVisible,
                        onMoveTo = onMoveTo,
                    )
                }
            }

            Spacer(Modifier.height(DONE_BAR_SPACE))
        }

        /* ---------- 完成（唯一出口，v16 改为常驻底部） ---------- */
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // 顶端一小段由透明渐变到页底色：滚动内容从下方穿过时柔和淡出，而不是被一条硬边齐刀切断
                .background(
                    Brush.verticalGradient(
                        0f to AppSurface.copy(alpha = 0f),
                        0.2f to AppSurface,
                        1f to AppSurface,
                    ),
                )
                .navigationBarsPadding()
                .padding(horizontal = PageGutter, vertical = 12.dp),
        ) {
            SolidButton(
                label = "完成",
                container = BluePrimary,
                onClick = onDone,
                height = 80.dp,
                fontSize = 26.sp,
            )
        }

        /* ---------- v24 命名导入对话框：库名 = 库身份，入库前必须由家属命名 ---------- */
        pendingImport?.let { pending ->
            BankNameDialog(
                suggestedName = pending.suggestedName,
                // 2026-09-24 修复 #4：输入的名字撞上已装库时明确提示「将替换」——10 词的小库
                // 整库替换掉 70 条的官方库而界面只说「替换更新完成」，是静默覆盖
                replaceTarget = { typedName ->
                    customBanks.firstOrNull { it.name == typedName.trim() }
                        ?.let { "「${it.name}」（${it.terms.size} 条）" }
                },
                onConfirm = { title ->
                    runCatching { CustomBanks.import(context, pending.bytes, title) }
                        .onSuccess { outcome ->
                            importOk = true
                            importMessage = "已导入「${outcome.bank.name}」（${outcome.bank.terms.size} 条）。" +
                                // 跨库重词跳过提示：面馆私有库与官方常用字词库有交集属常态，家属需要知道没全收
                                (if (outcome.skippedRepeats > 0) "另有 ${outcome.skippedRepeats} 条与已装词库重复，已跳过。" else "") +
                                (if (outcome.isNew) "已自动显示，可回学习界面开始。" else "替换更新完成。")
                            // 首次入库的库 id 交给 AppRoot 做默认显示；重导替换不动显隐
                            onBanksChanged(if (outcome.isNew) listOf(outcome.bank.id) else emptyList())
                        }
                        .onFailure { e ->
                            importOk = false
                            importMessage = e.message ?: "导入失败，请重试。"
                        }
                    pendingImport = null
                },
                onDismiss = { pendingImport = null },
            )
        }
    }
}

/** 设置页分组卡：白卡 + 轻投影浮在暖米色页底上（与 feed 词条卡同语言，用次级圆角） */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .cardShadow(CardShapeMedium, elevated = true)
            .clip(CardShapeMedium)
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        content = content,
    )
}

/** 分组标题：左侧蓝色短竖条作锚点，长页滚动时一眼找到每张卡的起点 */
@Composable
private fun CardTitle(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 5.dp, height = 22.dp)
                .clip(RoundedCornerShape(50))
                .background(BluePrimary),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AppText)
    }
}

/**
 * 导入结果提示条：导入顺利 = 浅绿底，失败 = 浅橙底，配对应图标。正文用深色 AppText 而非绿/橙字——
 * 17sp 小字在浅色底上用彩色字达不到 7:1，颜色语义交给底色与图标承担。
 */
@Composable
private fun ImportResultNote(message: String, ok: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ControlShape)
            .background(if (ok) GreenBg else OrangeBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (ok) GreenKnown else OrangeDark,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(message, fontSize = 17.sp, lineHeight = 25.sp, color = AppText)
    }
}

/** 统计指标格（v21）：数字在上、标签在下，一行三格。数字 30sp 大字号先被扫到，
 *  标签用次级文字色退后；单位用小一号字贴在数字基线旁，不与标签抢层级。 */
@Composable
private fun StatCell(
    label: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 30.sp, fontWeight = FontWeight.Black, color = color)
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(unit, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppText2)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 16.sp, color = AppText2)
    }
}

/** 指标格之间的竖分隔线：高度只到数字行（约 40dp），顶天立地会切碎卡片 */
@Composable
private fun StatDivider(height: Dp = 40.dp) {
    Box(
        Modifier
            .padding(top = 4.dp)
            .width(1.dp)
            .height(height)
            .background(AppLine),
    )
}

/** 分段小标题：「已显示 3」——数字即该段项数，让「关掉了几个」一眼可见 */
@Composable
private fun SceneSectionHeader(title: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = AppText2)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(AppSurface)
                .padding(horizontal = 10.dp, vertical = 2.dp),
        ) {
            Text("$count", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AppText2)
        }
    }
    Spacer(Modifier.height(6.dp))
}

/**
 * 一段分区行（v17）：**已显示段内**长按拖动重排 + 显隐开关；**未显示段只渲染行，不挂拖动手势**。
 *
 * 拖动模型（前提：行高固定 = [SCENE_ROW_H]，仅 [isHiddenGroup] == false 时启用）：
 * - 拖动中的行按手指位移整体平移（`dragDy`），并抬升（缩放 + 阴影）表明「它被拿起来了」；
 * - 被跨过的行整体让位一格（向上拖则下方行上移、向下拖则上方行下移）—— 位移 = ±行高，可精确算；
 * - 松手时把落点换算成**目标分区在 order 里的下标**交给持久层。
 *
 * **跨段不生效**：拖到另一段的范围会被钳回本段 —— 跨段等于改变可见性，那是开关的职责，
 * 隐式迁移会让「拖了一下」变成「开关变了」这种难以预期的事。v17 起未显示段干脆不挂手势，
 * 顺序语义只属于已显示的分区。
 *
 * ⚠️ 手势 block 会**被记住不再重建**，所以闭包里不能直接用 `scenes` / `allOrdered` / `onMoveTo`
 * —— 段内重排后列表内容变了，而 `pointerInput` 的 key 仍是同一个 id，读到的会是旧列表。
 * 一律经 `rememberUpdatedState` 取最新值；起点下标也在 `onDragStart` 里**按 id 现算**，
 * 不用编译期捕获的 `i`。比把列表塞进 `pointerInput` 的 key 更稳（那样重排一发生就重启手势，
 * 正在进行的拖动会被打断）。
 */
@Composable
private fun SceneRows(
    scenes: List<Scene>,
    isHiddenGroup: Boolean,
    allOrdered: List<Scene>,
    progressById: Map<String, SceneProgress>,
    onSetVisible: (String, Boolean) -> Unit,
    onMoveTo: (String, Int) -> Unit,
) {
    // 拖动会话态（本段私有）：谁在拖、从本段哪个下标起、手指累计移动多少像素。
    // 未显示段（isHiddenGroup = true）不会开拖动会话，这些状态恒为初值。
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragFrom by remember { mutableStateOf(0) }
    var dragDy by remember { mutableStateOf(0f) }
    val latestScenes by rememberUpdatedState(scenes)
    val latestOrdered by rememberUpdatedState(allOrdered)
    val latestOnMoveTo by rememberUpdatedState(onMoveTo)
    val rowPx = with(LocalDensity.current) { SCENE_ROW_H.toPx() }

    /** 手指位移 → 本段落点下标（钳在本段内，跨段不生效） */
    fun landingIndex(from: Int): Int =
        (from + (dragDy / rowPx).roundToInt())
            .coerceIn(0, (latestScenes.size - 1).coerceAtLeast(0))

    val dragTo = if (draggingId == null) -1 else landingIndex(dragFrom)

    scenes.forEachIndexed { i, s ->
        val isDragging = s.id == draggingId
        // 让位位移：只有被拖动的区间内的行需要平移，其余保持原位
        val shift = when {
            draggingId == null || isDragging -> 0f
            dragTo > dragFrom && i > dragFrom && i <= dragTo -> -rowPx
            dragTo < dragFrom && i >= dragTo && i < dragFrom -> rowPx
            else -> 0f
        }
        SceneRow(
            scene = s,
            hidden = isHiddenGroup,
            progress = progressById[s.id],
            onSetVisible = onSetVisible,
            modifier = run {
                val base = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) dragDy else shift
                        if (isDragging) {
                            scaleX = 1.02f
                            scaleY = 1.02f
                            shadowElevation = 10f
                            shape = RoundedCornerShape(12.dp)
                            clip = true
                        }
                    }
                    .background(if (isDragging) Color.White else Color.Transparent)
                // v17：未显示段不挂拖动手势 —— 隐藏分区没有顺序语义，不允许拖动位置
                if (isHiddenGroup) base
                else base.pointerInput(s.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            draggingId = s.id
                            // 起点下标**现算**：闭包里的 `i` 是手势 block 创建时的值，重排后即失效
                            dragFrom = latestScenes.indexOfFirst { it.id == s.id }.coerceAtLeast(0)
                            dragDy = 0f
                        },
                        onDrag = { change, amount ->
                            change.consume()      // 别让父级 verticalScroll 同时跟着滚
                            dragDy += amount.y
                        },
                        onDragEnd = {
                            val target = latestScenes.getOrNull(landingIndex(dragFrom))
                            if (target != null && target.id != s.id) {
                                val targetOrderIndex = latestOrdered.indexOf(target)
                                if (targetOrderIndex >= 0) latestOnMoveTo(s.id, targetOrderIndex)
                            }
                            draggingId = null
                            dragDy = 0f
                        },
                        onDragCancel = {
                            draggingId = null
                            dragDy = 0f
                        },
                    )
                }
            },
        )
        if (i < scenes.lastIndex) HorizontalDivider(color = AppLine, thickness = 1.dp)
    }
}

/**
 * 分区行（v17）：左侧 = 分区名 +（进度条 + 已学/总数），右侧 = 显隐开关（v17 起 ↑↓ 已删，
 * 操作区只剩开关一项，名称列因此拿到更多宽度）。
 * 隐藏段的分区名用次级文字色（`AppText2`）—— 与「显示」按钮的橙系配色一起表达当前状态。
 */
@Composable
private fun SceneRow(
    scene: Scene,
    hidden: Boolean,
    progress: SceneProgress?,
    onSetVisible: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val learned = progress?.learned ?: 0
    val total = progress?.total ?: 0
    val done = total > 0 && learned >= total

    Row(
        modifier
            .fillMaxWidth()
            .height(SCENE_ROW_H),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(end = 6.dp),
        ) {
            Text(
                "${scene.icon} ${scene.name}",
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = if (hidden) AppText2 else AppText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AppLine),
                ) {
                    val fraction = if (total > 0) learned.toFloat() / total else 0f
                    if (fraction > 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (done) GreenKnown else BluePrimary),
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    "$learned/$total",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (done) GreenKnown else AppText2,
                )
            }
        }
        // ⚠️ v17 修 v16 回归：`onSetVisible` 的第二参数是**目标可见性** = 当前是否隐藏（`hidden`），
        // 不是「取反」。v16 曾在这里写成 `!hidden` —— 隐藏中点「显示」传 false（往 hidden 里加
        // 已存在的 id）、可见中点「隐藏」传 true（从 hidden 里删不存在的 id），**两个方向都是
        // 原样写回、界面毫无反应**。历史教训：v14 原本就是 `isHidden`（对的），v16 重构时误判
        // 成 bug 才改反 —— 改语义前先核对持久层函数（`setSceneVisible`）的参数定义。
        ShowHideButton(hidden) { onSetVisible(scene.id, hidden) }
    }
}

/**
 * v6 R14 抽出：配额单选组（①每日总量/②每日新词两组同构）——64dp 高档位块。
 * 选中 = 实心蓝底白字（与频道 chip 同语言），未选中 = 米色块浮在白卡上，不用描边。
 * 档位**等宽铺满整行**而非横向滚动：旧版 5 档在常见屏宽下末档被裁半截，老人看不出还能滑；
 * 档位数 ≤5、数字 ≤2 位，360dp 屏上每档仍有约 50dp 宽。
 */
@Composable
private fun QuotaSelector(selected: Int, options: List<Int>, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { n ->
            val isSelected = n == selected
            val bg by animateColorAsState(if (isSelected) BluePrimary else AppSurface, label = "quotaBg")
            val fg by animateColorAsState(if (isSelected) Color.White else AppText2, label = "quotaFg")
            Box(
                Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(ControlShape)
                    .background(bg)
                    .clickable { onSelect(n) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$n",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = fg,
                )
            }
        }
    }
}

/**
 * v22 删除自定义词库按钮：两段确认（第一击变「确认删除」，再击才删）——不用弹窗，少一层适老交互。
 * 删除只移除词库本身，学习进度与收藏保留（重导同 id 的 JSON 自动恢复，见 CustomBanks KDoc）。
 */
@Composable
private fun DeleteBankButton(confirming: Boolean, onClick: () -> Unit) {
    // 进入「确认删除」态时颜色渐变加深：比瞬间跳色更容易被察觉到「按钮变了，要再点一次」
    val bg by animateColorAsState(if (confirming) OrangeDark else OrangeBg, label = "deleteBg")
    val fg by animateColorAsState(if (confirming) Color.White else OrangeDark, label = "deleteFg")
    Box(
        Modifier
            .size(width = 96.dp, height = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (confirming) "确认删除" else "删除",
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = fg,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * v6 设置页显示/隐藏开关：**68×64dp** 大按钮。**本组件只渲染「当前态」**
 * （当前隐藏 → 显示「显示」），点击语义是"取反"—— 目标值由调用点算好再交给 `onSetVisible`，
 * 组件自身不持有可见性状态。
 * 隐藏中的分区按钮显示「显示」（橙系，引导恢复），可见分区显示「隐藏」（绿系）——
 * 颜色本身也承担状态说明（不依赖小字识别）。
 * v17：↑↓ 按钮删除后它是行内唯一的操作按钮（宽度维持 68dp 不变）。
 */
@Composable
private fun ShowHideButton(isHidden: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 68.dp, height = 64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isHidden) OrangeBg else GreenBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (isHidden) "显示" else "隐藏",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = if (isHidden) OrangeDark else GreenKnown,
            textAlign = TextAlign.Center,
        )
    }
}

/** v24 命名导入的暂存体：SAF 读到的字节 + 由文件名生成的预填建议名（确认后才入库） */
private data class PendingImport(val bytes: ByteArray, val suggestedName: String)

/**
 * v24 命名导入对话框（QYJ 2026-09-23 拍板：库名由用户命名，不再取文件名）。
 * - 预填建议来自文件名（[CustomBanks.suggestBankName]，已剥「(1)/- 副本」后缀）——重导
 *   同一文件时预填稳定，直接确认即替换更新；
 * - **重导更新要用同一个库名**（换名 = 新库 id，旧进度失联）——说明文字必须写清，
 *   这是新契约里家属最容易踩的坑；
 * - 输入限 8 字（库显示名上限，输入即所见）；空名置灰导入按钮；
 * - [replaceTarget] = 输入名命中已装库时返回其展示片段（名字 + 条数），非空则在确认按钮
 *   上方标出「将替换已装的 X」——同名 = 整库替换，不能只靠结果文案事后告知（2026-09-24 修复 #4）。
 */
@Composable
private fun BankNameDialog(
    suggestedName: String,
    replaceTarget: (String) -> String? = { null },
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(suggestedName) }
    val valid = name.isNotBlank()
    val replaceWarning = replaceTarget(name.trim())
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .imePadding()   // 软键盘弹出时对话框整体上移，输入框不被键盘盖住
            .clickable(onClick = onDismiss),   // 点遮罩 = 取消
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .cardShadow(CardShapeMedium, elevated = true)
                .clip(CardShapeMedium)
                .background(Color.White)
                .padding(horizontal = 22.dp, vertical = 24.dp)
                // 消费卡内点击，防穿透到遮罩的「点外关闭」
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
        ) {
            Text("给词库起个名字", fontSize = 26.sp, fontWeight = FontWeight.Black, color = AppText)
            Spacer(Modifier.height(10.dp))
            Text(
                "想更新已装的库，就用和当时一样的名字（换名字会当成新的库）。",
                fontSize = 17.sp,
                color = AppText2,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(8) },
                singleLine = true,
                textStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.fillMaxWidth(),
            )
            if (replaceWarning != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "将替换已装的$replaceWarning，学习进度保留。",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = OrangeDark,
                )
            }
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (valid) BluePrimary else AppLine)
                    .clickable(enabled = valid) { onConfirm(name.trim()) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "导入",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = if (valid) Color.White else AppText2,
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppSurface)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text("取消", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppText2)
            }
        }
    }
}
