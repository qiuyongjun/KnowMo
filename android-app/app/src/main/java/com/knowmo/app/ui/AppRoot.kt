package com.knowmo.app.ui

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.knowmo.app.data.AppSettings
import com.knowmo.app.data.AnswerResult
import com.knowmo.app.data.CustomBanks
import com.knowmo.app.data.STUDY_TERMS
import com.knowmo.app.data.StudyRepository
import com.knowmo.app.data.Term
import com.knowmo.app.data.sceneName
import com.knowmo.app.tts.ChineseVoiceState
import com.knowmo.app.tts.TTSSpeaker
import com.knowmo.app.ui.theme.AppSurface
import com.knowmo.app.ui.theme.BlueBg
import com.knowmo.app.ui.theme.BluePrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** feed 页：词条卡（mode 由调度器运行时计算：新学/复习/浏览）或总结卡。
 *  seq = 本会话内的出现序号：v8 起每日队列**首排每词一张卡**，连击未满 3 的词由 `answerCard`
 *  在作答后运行时插入重复卡（打散，prd v8 第 1 条）——展开/作答状态仍按「出现」记录而非按词记录
 *  （同一词的多次出现各自独立作答），池型浏览页与任务页互不干扰，旧数据兼容也更稳。
 *  cardId = 持久化队列中的稳定卡实例 id；重复卡插入后原有卡的身份不变。
 *  池型页（温故流 / 分区浏览）= null（不落库）。
 *  v6：`Guide` = 空态引导页（v23 起两种：零词库未导入 → EMPTY_BANK_*；空收藏池 → FAV_GUIDE_*；
 *  大字引导 + 播报同源，
 *  防 VerticalPager pageCount == 0 白屏）。 */
sealed interface Page {
    data class TermPage(val t: Term, val mode: CardMode, val seq: Int, val cardId: String?) : Page
    data object Done : Page
    data object Guide : Page
}

/** 池型频道的预追加页数（v5 R10：温故流与分区共用同一条追加逻辑；临近队尾 / 确认后装载时预追加，池空重洗） */
private const val POOL_BATCH = 2

/** 停稳判定后的播报去抖（ms）：滑动进行中不播，停稳后再等这么久，快速连滑不闪播 */
private const val SETTLE_SPEECH_DELAY_MS = 200L

/**
 * v21.1 作答夸奖池（QYJ 2026-09-22 反馈）：连击未满时「认识」的播报从池中**随机取一条**——
 * 不再报「再认对 N 次就学会」（那是向老人解释调度机制：每天要听几十遍、语气像系统提示不像夸奖，
 * 且次数信息没有行动价值）。随机变化消除「每句都一样」的单调感；TTS 友好——短句、无符号、口语。
 */
private val ANSWER_PRAISE = listOf("认对了，真棒", "好，记住了", "不错不错", "记得好", "真厉害")

/**
 * v20 频道会话快照（QYJ 2026-09-21 三条反馈的统一载体，见 AppRoot 内 channelSnapshots 注释）：
 * 切走频道时保存该频道的页面流 / 抽取池 / 落点 / browseMode，切回时原样恢复。
 * date 用于隔日作废：快照跨天一律丢弃，走完整装载（队列重建 / 重新洗牌）。
 * 纯会话态（remember map），不持久化——App 重启后按原有 D6 逻辑重新洗牌。
 */
private class ChannelSnapshot(
    val date: String,
    val pages: List<Page>,
    val pool: List<Term>,
    val page: Int,
    val browseMode: Boolean,
)

/** v5 R8 作答后自动前进的最短停留（ms）：答案与反馈至少在屏幕上停这么久再翻页，
 *  TTS 不可用时也不至于瞬间翻走（§5.5 两段式等待的第一段） */
private const val AUTO_ADVANCE_MIN_MS = 1500L

/**
 * 根界面：抖音式垂直 feed（v9：任务卡静默自评 + 完成卡上滑转浏览，prd v9 / design.md §14）。
 * 交互契约（v6–v8 未推翻条目沿用）：
 * 1. 滑到停稳（isScrollInProgress=false 后 ~200ms）才播报；快速连滑中间卡不闪播。
 *    **例外（v18）**：频道装载的「换到 X 频道」+ 首卡播报不走停稳事件——切频道前后页码净不变时
 *    snapshotFlow 不发事件（v18 前因此整段哑掉），改由跳页 effect 在 scrollToPage 后直接播
 * 2. **任务卡静默**（v9 第 1 条）：每日任务卡（NEW/REVIEW 同一卡型，词+拼音+提示恒全显示，v8）
 *    停稳**不自动朗读**——用户直接按「认识 / 忘了」自评，或**点卡片听读**（词+提示）、
 *    点单字读该字后再作答；作答后仍念「词+提示+反馈」（适老化）。
 *    **浏览卡**（温故流/分区/收藏）滑到停稳自动念「词 + 用途」（v9 第 2 条：自主学习划入即读）。
 * 3. **当日连击**（v8 保留）：同词当日 3 次「认识」才移出队列，任意「忘了」清零；
 *    未满 3 → `answerCard` 在仓库事务内插入重复卡（同词不连续/紧邻）；完成当日连击后才写入跨日间隔
 *    （SM-2 + 封顶分级）。
 * 4. **任务锁滑**（v7 保留，v9 第 1 条不变）：每日任务频道任务阶段 `userScrollEnabled = false`
 *    ——任务中不允许滑动切换词卡，唯一翻页动力 = 作答后自动前进（等播报念完再翻）。
 * 5. **完成卡上滑转浏览**（v9 第 1 条，取代 v7「点确认」）：全部任务词连击满 3 → 完成卡落地，
 *    **到达完成卡即 `enterBrowse`**：`repo.markConfirmed()` 持久化 + 移除全部任务卡
 *    （**完成卡保留在首位**）+ 装载温故流——上滑进入推荐浏览、回滑仍可看完成卡战果，
 *    但任务卡已被移除、**回不去**。当日重启：confirmed == true 直接进浏览模式（完成卡在首位）；
 *    false 走断点恢复（仍锁滑）。
 * 6. **推荐范围收窄**（v9 第 3/4 条）：推荐频道（每日任务 + 温故流）只含**可见分区**的词
 *    （`recScenesProvider` 注入，MainActivity 传 settings.visibleScenes()）+ **常用词分区**
 *    （`CHANNEL_COMMON`，恒入推荐——QYJ 拍板的内容源特例：默认场景分区全隐藏时，推荐仍靠
 *    常用词有内容）。隐藏分区的词两个入口都抽不到。
 *    ⚠️ v17：`CHANNEL_COMMON` 同时成了**第三个固定频道**（默认显示、不可移除，见 Common.kt 的
 *    ChannelBar）。它是**池型频道**（channel != CHANNEL_DAILY → 走 appendPoolPages 分支），
 *    浏览零写入；其词仍在推荐范围内，两条入口并存。
 * 7. 不展示"第 x/y 张"进度条，进度由完成卡和语音表达
 * 8. 分区完成（design.md §9.5，days >= 15）→ 频道栏加「学完」后缀（v16 起用文字，不再用 🎓），即时可逆
 *    （间隔层只由每日任务作答写入）
 * 9. 频道分两类（v5 R10，§5.7）：
 *    **队列型 = 只有每日任务频道**（`CHANNEL_DAILY` = "rec"）——`repo.ensureQueue()` / `DailyQueue` /
 *    完成卡 / 锁滑 / 断点 = frontier 只属于它；
 *    **池型 = 场景分区 + 推荐频道的温故流 + 收藏频道**——运行时从池里**加权随机**抽卡（v9 第 2 条：
 *    算法排列不用固定顺序）、无限追加、**不落库**（`appendPoolPages`）。两处的卡都是**浏览卡**
 *    （`CardMode.FREE`：词 + 逐字拼音 + 用途全展开、无 √/×——是否认识只在每日任务中出现、
 *    点卡重听、点单字读该字、零写入）。
 *    分区因此：**无完成卡**（`syncDonePage` 对非每日任务频道直接 return）、**不做到期筛选**
 *    （池 = 该区全部词含未学词 → 池必非空 → 不白屏）；位置恢复 v20 起按**快照**（见下），
 *    首次进区仍显式归零（`restoreTo = 0`——切频道时 `pagerState.currentPage` 可能还是上一个频道
 *    的旧值）。池型频道**零写入**：浏览卡没有作答入口 → 不写 `TermState` / `DayState`
 *    （分区里未学词只看不算学，学会它的唯一路径是每日任务——D4 新词入口唯一化）。
 * 10. v6 隐藏设置入口（prd v6 第 1 条 / design.md §11.2）：推荐 tab 连点 5 次（间隔 ≤2s，检测在
 *     ChannelBar 内部）→ `showSettings = true` 打开全屏设置页（**overlay 条件渲染**——不能写成
 *     if/else 互换渲染，那会把 pages / seqGen 等会话态全部丢掉）；打开时播报「已打开设置」。
 *     设置改动**即时生效**：orderedScenes / hiddenIds / quota 三个镜像 state 在回调里写 AppSettings
 *     后重读；当前频道被隐藏（非 rec/fav 且不在可见分区）→ 回退推荐频道。配额只影响次日 buildQueue。
 * 11. v6 收藏分区（prd v6 第 3 条）：`CHANNEL_FAV` 是池型频道，channel != CHANNEL_DAILY 自然落入
 *     池型分支（零改动复用 `appendPoolPages`）；空池插 `Page.Guide` 引导页。星按钮 → `repo.toggleFavorite`
 *     只写 favorites（不碰 TermState / DayState，收藏分区零写入不破坏），favorites 快照整体刷新触发
 *     重组。取消收藏的口径 v20 起分频道：**fav 频道内即时移除**该词的已出卡（收藏页的本体就是
 *     收藏集合，挂着已取消的卡是误导，见 `removeFavoriteCards`）；**其他频道不重排**已出卡
 *     （pages 不动），后续池重洗/重装载自然不再抽到该词。
 * 12. v20 频道会话快照（QYJ 2026-09-21 三条反馈）：切走频道时保存该频道的页面流/抽取池/落点/
 *     browseMode，切回时原样恢复——① rec 浏览模式切回落点 = 离开时的浏览页（旧行为被拽回
 *     完成卡；完成卡仍在首位，回滑可看战果）；② 池型频道切回不再重新洗牌（D6 收窄为
 *     「App 重启 / 跨日后首轮进区随机洗牌」）；③ 收藏频道取消收藏即时移除该词的卡（见第 11 条）。
 *     快照是纯会话态、跨日作废、不持久化。
 */
@Composable
fun AppRoot(repo: StudyRepository, tts: TTSSpeaker, settings: AppSettings) {
    var channel by remember { mutableStateOf(StudyRepository.CHANNEL_DAILY) }
    // v16：作答计数器 —— **每次作答 +1**，唯一用途是给「刷新分区完成徽章」的 effect 当 key。
    // 为什么不能拿战果数字当 key：`mutableStateOf` 走**结构相等**，值没变就不重启 effect ——
    // 答对但「忘了」次数不变时，徽章该重算却不会重算（v16 之前的 `DayStats(knownWords, forgot)`
    // 有同样的毛病）。计数器每次作答必变，是唯一可靠的触发源。
    // 战果数字本身（完成卡 / 播报）**不缓存**，直接读 repo 的持久口径 ——
    // 本地累加或本地镜像都得与 repo 的写入严格同步，任一处漏改就漂移。
    var answerTick by remember { mutableStateOf(0) }
    // v23：词库/显隐结构变化计数器——导入/删除词库、显隐开关后 +1，作装载 effect 的 key
    //（当前频道重载以反映新词库/新范围；空态引导页写着「分区都隐藏了」，开启分区后必须换掉）
    var bankTick by remember { mutableStateOf(0) }
    // v23：频道切换播报标记——装载 effect 的 key 加了 bankTick 后，「播不播换频道语」不能再由
    // 「非首次装载」推断（bankTick 重载不是切频道），改为 onSelect 里显式置位
    var switchAnnounce by remember { mutableStateOf(false) }
    val revealed = remember { mutableStateMapOf<Int, Boolean>() }      // seq -> 任务卡是否已作答（v8：不再控制拼音显隐——卡片恒全展开，只控制按钮隐藏/结果文案；浏览卡不写这里）
    val results = remember { mutableStateMapOf<Int, String>() }        // seq -> 反馈文案
    var seqGen by remember { mutableStateOf(0) }                       // 页出现序号发生器（只在 effect/回调中递增）
    val spokenKeys = remember { mutableSetOf<String>() }               // 自动朗读去重（频道 + 页身份）
    // v18：最近一次停稳落点页的去重键。**离开该页就清掉它的 spokenKeys 键** —— 否则回滑 /
    // 再次划到已听过的卡会被去重挡成整段沉默（「划入即读」要防的是同一次停稳的重播，
    // 不是「回头重听」）。键随频道装载在 spokenKeys.clear() 处一并复位。
    var lastSettledKey by remember { mutableStateOf<String?>(null) }
    var graduated by remember { mutableStateOf(emptySet<String>()) }   // 已完成分区（频道栏「学完」）

    // v9（prd 第 1 条）：**browseMode = 每日任务频道到达完成卡之后的自由浏览**——
    // false = 任务阶段（锁滑，唯一翻页动力是作答后自动前进）；true = 到达完成卡转浏览
    // （完成卡保留在首位 + 温故流浏览页，自由滑动）。仅 rec 频道有语义；装载时按 q.confirmed 恢复。
    var browseMode by remember { mutableStateOf(false) }

    // v6 设置（design.md §11.2）：AppSettings 是真源，这里只持**镜像**（回调写库后重读触发重组）
    var showSettings by remember { mutableStateOf(false) }             // 全屏设置页显隐（隐藏入口 = 推荐 tab 连点 5 次）
    var orderedScenes by remember { mutableStateOf(settings.orderedScenes()) }
    var hiddenIds by remember { mutableStateOf(settings.hiddenIds()) }
    var quota by remember { mutableStateOf(settings.quota()) }
    var quotaNew by remember { mutableStateOf(settings.quotaNew()) }   // v6 R14 每日新词配额镜像
    // v14 学习统计快照（design.md §16.2）：打开设置页时从 repo 一次性重取（设置页打开期间
    // 无作答发生，静态快照够用，不需要响应式订阅）
    var stats by remember { mutableStateOf(repo.studyStats()) }
    // v22 自定义词库镜像（设置页「我的词库」导入/删除后经 onBanksChanged 重读触发重组；
    // 分区/词条本身经 allScenes()/STUDY_TERMS getter 动态并入，无需在此展开）
    var customBanks by remember { mutableStateOf(CustomBanks.all) }
    val visibleScenes = orderedScenes.filter { it.id !in hiddenIds }   // 频道栏渲染序（rec/fav 固定渲染，不在此列）
    // v6 收藏：收藏集合快照（TermCard 星按钮显色用；toggleFavorite 后整体重读触发重组）
    var favorites by remember { mutableStateOf(repo.favorites().toSet()) }

    // v20 频道会话快照（QYJ 2026-09-21 三条反馈的统一修复）：
    // ① rec 浏览模式切走再切回，落点 = 离开时的浏览页（旧行为：无条件重建 pages 且落第 0 页
    //    完成卡——用户浏览到深处切回来被拽回完成卡）；
    // ② 分区/收藏/常用词等池型频道切回不再重新洗牌（D6「每次进区都是一轮新的随机」改为
    //    「会话内每频道只洗一次，切回续看」；App 重启 / 跨日仍按 D6 重新随机）；
    // ③ 收藏频道取消收藏的即时移除（removeFavoriteCards）操作的是当前 pages，
    //    与本 map 无关——本 map 只在切走瞬间写入（onSelect），切回时消费一次（remove）。
    val channelSnapshots = remember { mutableMapOf<String, ChannelSnapshot>() }
    val uiScope = rememberCoroutineScope()

    // ===== 2026-09-23 修复 #2：跨零点会话卡死 =====
    // App 开着跨过零点后，repo 的日期已变而 pages 仍是昨日队列——answerCard 对旧日期
    // 队列返回 null，任务阶段又锁滑（userScrollEnabled=false）：按「认识 / 忘了」毫无
    // 反应也滑不动，只能切频道或重启。检测两个时机：ON_RESUME（后台跨零点回来）+
    // 前台每分钟轮询（一直开着跨零点）。跨日动作 = 作废全部会话态并 dayTick++ 触发
    // 当前频道完整重装载（rec 的 ensureQueue 按新日期重建队列；池型频道按 D6 跨日口径重洗）。
    var sessionDate by remember { mutableStateOf(repo.today()) }
    var dayTick by remember { mutableStateOf(0) }
    fun checkDayRollover() {
        val today = repo.today()
        if (today == sessionDate) return
        sessionDate = today
        channelSnapshots.clear()   // 快照的 date 判定本会作废，clear 让语义显式
        revealed.clear()
        results.clear()
        dayTick++
        // 静默换卡会让老人困惑「怎么内容突然变了」；跨日重装载前先播一句
        tts.speak("新的一天。")
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkDayRollover()
                // 修复 #3：用户按横幅提示装好中文引擎回来 → 免重启恢复朗读
                tts.recheckLanguage()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            checkDayRollover()
        }
    }

    // ===== 2026-09-23 修复 #3：无声故障（缺中文引擎 / 媒体静音）的适老提示 =====
    // TTSSpeaker.chineseVoice 与音量都是普通 @Volatile / 系统值，重组不感知——用低频
    // 轮询（2s，成本可忽略）落到 Compose 状态驱动横幅；静音横幅恢复音量自动消失。
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var chineseVoice by remember { mutableStateOf(ChineseVoiceState.INIT) }
    var mediaMuted by remember { mutableStateOf(false) }
    var mutedDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            chineseVoice = tts.chineseVoice
            mediaMuted = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
            delay(2_000)
        }
    }

    // feed 页面：队列型 = 当日队列 + 总结卡（条件存在）+ 温故流追加页；池型（分区）= 池型浏览页（无限流）
    var pages by remember { mutableStateOf<List<Page>>(emptyList()) }
    var freePool by remember { mutableStateOf<List<Term>>(emptyList()) } // 池型抽取池：顺序抽取，池空重洗（两个频道类型共用）
    var restoreTo by remember { mutableStateOf<Int?>(null) }             // 断点/频道切换的目标页
    var pendingAnnounce by remember { mutableStateOf<String?>(null) }    // 频道播报（与首卡合并成一句播；主消费者 = 跳页 effect，停稳 collector 只兜底）
    var firstChannelLoad by remember { mutableStateOf(true) }
    // v5 R8 作答后自动前进（§5.5）：本次刚作答的卡 seq；-1 = 没有待办的自动前进。
    // 用作答卡的 seq 作 effect key（而非布尔）——既能触发，又能在收尾时置回 -1 取消
    var advanceAfterSpeechSeq by remember { mutableStateOf(-1) }

    val pagerState = rememberPagerState(pageCount = { pages.size })

    // v6：打开设置页播报「已打开设置」（prd v6 第 2 条；keyed showSettings，关闭不播）。
    // v14：打开时重取学习统计快照（覆盖上次打开之后的作答变化——今日战果/streak/分区进度）。
    // v16：快照的总览分母依赖**可见分区**，所以显隐变化时也要重取 —— 见 onSetVisible 回调。
    LaunchedEffect(showSettings) {
        if (showSettings) {
            stats = repo.studyStats()
            tts.speak("已打开设置")
        }
    }

    // v6：设置显隐改动**即时生效**——当前频道被隐藏（非 rec/fav 且不在可见分区）→ 回退推荐频道。
    // 回退由既有的 LaunchedEffect(channel, bankTick) 装载分支自然接管（重建队列 / 重建池型页）。
    LaunchedEffect(visibleScenes) {
        // ⚠️ v17：固定频道有三个（推荐 / 收藏 / 常用词），三者都**不来自 visibleScenes**，
        // 必须全部放行。漏掉 CHANNEL_COMMON 的后果：点「常用词」频道的那一瞬就被本 effect
        // 判为"当前频道已被隐藏"弹回推荐 —— 表现为该 tab 完全点不动。
        if (channel != StudyRepository.CHANNEL_DAILY &&
            channel != StudyRepository.CHANNEL_FAV &&
            channel != StudyRepository.CHANNEL_COMMON &&
            channel !in visibleScenes.map { it.id }
        ) {
            channel = StudyRepository.CHANNEL_DAILY
        }
    }

    // 词条 id 索引由 CustomBanks 在导入/删除/装载时重建（2026-09-24 修复 #1），这里直接读。
    // 不能在界面层 remember(bankTick) 缓存：下方翻页监听（LaunchedEffect(pagerState)）只在
    // 首次组合启动，闭包捕获的本地缓存跨 bankTick 不更新——空库当天一键导入后，补浏览页
    // 会一直用旧（空）词表，浏览模式一张卡都出不来。
    fun termById(id: String): Term? = CustomBanks.termById(id)

    fun nextSeq(): Int = ++seqGen

    fun termSpeech(t: Term) = "${t.text}。${t.tip}"

    /** 待处理（pending）口径（v8 语义同 v7）：每日任务卡（非 FREE）**未作答**——
     *  首见词也是任务卡，无「教读待处理」这一态；v8 连击插入的重复卡 unanswered 时同样计入
     *  （answerCard 插入的新 seq 未写 revealed）。
     *  浏览卡（`CardMode.FREE`：温故流 / 分区 / 收藏）恒不计入——它们不是当日任务，也没有作答入口。 */
    fun isPending(p: Page): Boolean =
        p is Page.TermPage && p.mode != CardMode.FREE && revealed[p.seq] != true

    /** 待处理任务卡数：总结卡「条件存在」的判据。
     *
     *  ⚠️ 必须写成局部函数而非 `val`：这里读的是 `pages` / `revealed` 的 State 委托 getter，
     *  每次调用都取当前值。若写成 `val`，key 不含 `pages` 的 effect 闭包会捕获旧值——
     *  页面集合未变而仅发生作答时该 effect 不重启，滑到总结卡会算出过期张数。 */
    fun pendingTaskCount(): Int = pages.count { isPending(it) }

    /** frontier = 第一张待处理卡的下标（无待处理卡时取总结卡下标；都没有则 0）。
     *  不变量：列表**只追加不重排**且（考试阶段）不允许越过未处理的卡——锁滑 + 作答自动前进
     *  结构性地保证了这一点（v5 R7 前向拦截已删），所以「在哪儿」就等于「做到哪儿了」
     *  ——断点（restoreTo / saveQueuePosition）直接取它即可。 */
    fun frontierIndex(): Int {
        val firstPending = pages.indexOfFirst { isPending(it) }
        if (firstPending >= 0) return firstPending
        val doneIdx = pages.indexOfFirst { it is Page.Done }
        return if (doneIdx >= 0) doneIdx else 0
    }

    /* 完成卡战果里的 N（「今天学完了 N 个词」）= **当日任务区去重词数**，取自
     * `repo.todayTaskWordCount()`（当日队列 distinct），**刻意不由 `pages` 派生** ——
     * v9 的 `enterBrowse()` 在用户到达完成卡的**那一帧**就移除全部任务卡，pages 派生的计数
     * 会当场归零，完成卡与语音都报「今天学完了 0 个词」（当日重启直接进浏览模式同理：
     * pages 里只剩完成卡）。v16 修复。池型浏览页本就不在队列里，天然不计入。 */

    /** v5 R7 总结卡「条件出现」（§5.1）：`pending == 0` 且还没有总结卡 → 追加到任务区末尾；
     *  反向保底（`pending > 0` 且总结卡还在队尾）→ 移除。
     *  单调性靠调用点保证：装载后、每次 `answer()` 后各调一次（v7 无追加考核卡，调用面收窄）。
     *  v5 R9（§5.6）：**只有每日任务频道有总结卡**——分区 feed 里没有任何收尾页。
     *  v7/v9：`browseMode` 必须**短路**——转浏览后的浏览页永不挂总结卡（否则转浏览后总结卡
     *  又回来了，与「任务卡回不去」直接矛盾）。 */
    fun syncDonePage() {
        if (channel != StudyRepository.CHANNEL_DAILY || browseMode) return
        val pending = pendingTaskCount()
        val doneIdx = pages.indexOfFirst { it is Page.Done }
        when {
            pending == 0 && doneIdx < 0 -> pages = pages + Page.Done
            pending > 0 && doneIdx >= 0 && doneIdx == pages.lastIndex ->
                pages = pages.filterNot { it is Page.Done }
        }
    }

    /** 自动朗读去重键：按**页身份**（seq）而非下标——追加（浏览页 / 完成卡）会改变下标，
     *  绑 seq 让「页身份」与「列表位置」解耦。完成卡全程至多一张（v9：转浏览后保留在首位），
     *  故用 `channel:done` 即可。 */
    fun spokenKey(p: Page) = "$channel:" + when (p) {
        is Page.TermPage -> "seq:${p.seq}"
        Page.Done -> "done"
        Page.Guide -> "guide"          // v6 空收藏引导页
    }

    /** 停稳后播报文案（v9，prd 第 1/2 条）：
     *  - **任务卡**（NEW/REVIEW）：返回空串 → 停稳**不自动朗读**（显示词卡不朗读——
     *    用户自主点按听读 / 直接自评，prd v9 第 1 条）；
     *  - **浏览卡**（FREE）：滑到停稳自动念「词 + 用途」（自主学习划入即读，prd v9 第 2 条）；
     *  - 完成卡：战果 + 上滑进入推荐模式的引导（v9 取代 v7「点确认」话术）；
     *  - 引导卡：空收藏文案（与引导卡同源）。 */
    fun cardSpeech(p: Page): String = when (p) {
        is Page.TermPage -> if (p.mode == CardMode.FREE) termSpeech(p.t) else ""
        Page.Done ->
            "太棒了，今日任务完成！今天学完了${repo.todayTaskWordCount()}个词，" +
                "忘了${repo.todayForgot()}次。" +
                "上滑进入推荐模式，随便看看吧。"
        Page.Guide ->
            // v6 空收藏引导；v23 区分零词库（全库无词 → 导入引导）。
            // 2026-09-23 复审修复（#2）：第三种空态——推荐频道上词库在、但可见范围为空
            // （分区全隐藏），旧判据会把它当「空收藏」念出与现场无关的话术。
            when {
                STUDY_TERMS.isEmpty() -> EMPTY_BANK_GUIDE_SPEECH
                channel == StudyRepository.CHANNEL_DAILY -> HIDDEN_ALL_GUIDE_SPEECH
                else -> FAV_GUIDE_SPEECH
            }
    }

    /** 池型追加（v5 R10 §5.7-2）：从池里顺序取 count 张追加到**尾部**；池空用 `repo.poolIds(channel)`
     *  重洗补满（**无限流**，随时可停）。cardId = null → 不落库。三个使用场景共用同一实现：
     *  - 池型（分区/收藏）= 该范围**全部词**（含未学词）；
     *  - 队列型（`rec`）温故流 = v5 完成卡之后的追加，以及 **v9 转浏览模式后的装载**；
     *    池 = 推荐范围**已学词**（作答即写间隔层 → 转浏览前已答的词必有 TermState，池必非空）。
     *  v6 统一（2026-09-20 QYJ 拍板）：**抽卡算法全部为加权随机**（`weightedShuffle`，忘词/久未见优先）。
     *  追加只发生在**列表尾部**（与「只追加不重排」的既有不变量一致），`spokenKeys` / pager `key`
     *  的页身份机制不受影响。
     *  ⚠️ 场景分区池必非空（每区 ≥ 17 词——v10 口水话清理后最小分区 emergency，§5.7-8）→ 追加总能拿到内容；**收藏频道（v6）池可能为空**
     *  ——这里拿不到卡只是不加页，装载分支负责在 `pages` 为空时插 `Page.Guide` 引导页。 */
    fun appendPoolPages(count: Int) {
        val extra = ArrayList<Page>(count)
        repeat(count) {
            if (freePool.isEmpty()) {
                freePool = repo.poolIds(channel).mapNotNull(::termById)
            }
            val next = freePool.firstOrNull() ?: return@repeat
            freePool = freePool.drop(1)
            extra.add(Page.TermPage(next, CardMode.FREE, nextSeq(), null))
        }
        if (extra.isNotEmpty()) pages = pages + extra
    }

    /** v9 转浏览模式（prd 第 1 条，取代 v7「点确认」）：**到达完成卡即触发**（见 currentPage collector）。
     *  ① `repo.markConfirmed()` 持久化（DailyQueue.confirmed，当日重启 / 切频道回来直接进浏览模式，
     *     完成卡保留在首位）；② `browseMode = true`（解锁滑动 + `syncDonePage` 从此短路）；
     *  ③ 移除全部任务卡（完成卡与池型浏览页保留）——上滑进入推荐浏览、回滑仍可看完成卡战果，
     *     但任务卡已被移除、**回不去**（prd v9 第 1 条）；④ `scrollToPage` 校正下标：任务卡都在
     *     完成卡之前，移除后当前页下标左移 doneIdx；⑤ 丢弃未完成的自动前进。
     *  必须在 collector（suspend 上下文）中调用。 */
    suspend fun enterBrowse(doneIdx: Int) {
        repo.markConfirmed()
        browseMode = true
        val target = (pagerState.currentPage - doneIdx).coerceIn(0, pages.size - 1)
        pages = pages.filter { it is Page.Done || (it is Page.TermPage && it.cardId == null) }
        pagerState.scrollToPage(target)
        advanceAfterSpeechSeq = -1
    }

    // 装载：**队列型频道**（只有每日任务）走当日队列 + frontier 断点；**池型频道**（场景分区/收藏）
    // 没有队列——直接填一批池型浏览页开始无限流（`pages` 不能为空：`VerticalPager` 的 pageCount == 0
    // 就是白屏）。repo 内 date 不符自动重建队列。
    // v9：rec 装载先看 `q.confirmed`——true 直接进浏览模式（**完成卡保留在首位**，回滑可看战果，
    // 上滑进入浏览流；当日重启不重放任务卡）；false 走任务模式（断点恢复按卡实例 answered[i]
    // 还原作答态，仍锁滑）。
    // 2026-09-23 修复 #2：key 增加 dayTick——跨零点由 checkDayRollover 置位，当前频道
    // 完整重装载（旧 pages 挂在昨日的队列上，answerCard 全部返回 null = 会话卡死）。
    LaunchedEffect(channel, bankTick, dayTick) {
        // v18：App 首次装载（冷启动 / 后台进程被杀后切换回来重建）→ 播与**恢复后状态**匹配的欢迎语。
        // 旧实现是写死的「上滑开始学习」（LaunchedEffect(Unit)）：任务阶段是**锁滑**的（唯一前进方式
        // 是作答，v9），切回来一听「上滑」就是错误指示 —— 欢迎语必须按队列状态分流。
        // 时序：TTSSpeaker 未就绪时 speak 进 pending 队列按序补播，恒先于卡片播报（停稳播报有
        // SETTLE_SPEECH_DELAY + restoreTo 守卫，见下方停稳 effect）。
        val isAppEntry = firstChannelLoad
        // v23：播报只在「用户真的切了频道」时播（onSelect 置位）；bankTick 触发的重载不播
        val announce = switchAnnounce
        switchAnnounce = false
        firstChannelLoad = false
        spokenKeys.clear()
        lastSettledKey = null               // 去重键复位须同步：旧键留在 spokenKeys 会挡回头重听（v18）
        advanceAfterSpeechSeq = -1           // 频道已切换，丢弃上一频道未完成的自动前进
        // v20：本频道会话内来过 → 原样恢复（页面流 / 抽取池 / 落点 / browseMode），不重洗不重排。
        // 只认**当天**的快照：date 不符 = 跨日，队列该重建、池该重洗，走下方完整装载。
        val snap = channelSnapshots.remove(channel)
        if (snap != null && snap.date == repo.today()) {
            browseMode = snap.browseMode
            pages = snap.pages
            freePool = snap.pool
            restoreTo = snap.page.coerceIn(0, maxOf(0, snap.pages.lastIndex))
        } else {
            freePool = emptyList()
            if (channel == StudyRepository.CHANNEL_DAILY) {
                val q = repo.ensureQueue()
                if (q.confirmed) {
                    // v9：今日已进过浏览模式 → 完成卡保留在首位（回滑可看战果），上滑进入浏览流
                    browseMode = true
                    pages = listOf(Page.Done)
                    appendPoolPages(POOL_BATCH)
                    restoreTo = 0
                    // 浏览模式可自由滑动，「上滑」指引成立
                    if (isAppEntry) tts.speak("欢迎回来。上滑继续浏览。")
                } else {
                    browseMode = false
                    pages = q.queue.mapIndexedNotNull { i, id ->
                        val t = termById(id) ?: return@mapIndexedNotNull null
                        val mode = if (q.modes[i] == StudyRepository.MODE_NEW) CardMode.NEW else CardMode.REVIEW
                        val seq = nextSeq()
                        // 断点恢复：按该卡实例的 answered[i] 逐卡恢复展开态（design.md §9.1）
                        if (q.answered[i]) revealed[seq] = true
                        Page.TermPage(t, mode, seq, q.cardIds[i])
                    }
                    // 空态判据（2026-09-23 复审修复 #2）：用**当前可见学习范围**而非全局
                    // STUDY_TERMS——全部分区隐藏时全局仍有词、rec 却抽不到任何卡，旧判据
                    // 会落到总结卡「今日任务完成 0 个词」。recScopeEmpty 与 buildQueue 同口径。
                    if (pages.isEmpty()) {
                        if (repo.recScopeEmpty()) pages = listOf(Page.Guide) else syncDonePage()
                    } else {
                        syncDonePage()
                    }
                    // 断点：frontier = 第一张待处理卡（都做完了则是总结卡）——「在哪儿」=「做到哪儿了」
                    restoreTo = frontierIndex()
                    // 任务阶段锁滑 → 不指引「上滑」，只说继续（作答是唯一前进方式）
                    if (isAppEntry) tts.speak("欢迎回来。继续学习。")
                }
            } else {
                browseMode = false                   // 仅 rec 频道有语义，切走时复位
                // v5 R10（§5.7-3）：池型频道 = 池 + 浏览卡，没有队列、也没有断点——**显式归零**，
                // 用 `0` 而**不是** `null`：切频道时 `pagerState.currentPage` 可能还是上一个频道的旧值
                // （比如 7），而新 `pages` 只有 2 张。
                // ⚠️ v20 起 D6 口径收窄：**会话内**每频道只洗一次（切回走上方快照恢复），
                // 本分支只在首次进入该频道 / 快照跨日作废时执行——「每次进区都是一轮新的随机」
                // 仅剩 App 重启 / 跨日两个场景成立。
                pages = emptyList()
                appendPoolPages(POOL_BATCH)
                // v6：收藏频道空池（没有任何收藏）→ appendPoolPages 拿不到卡，pages 为空 = 白屏。
                // 插引导页（仅 fav 空池可达；场景分区池 = 全区词必非空，不受影响）。
                if (pages.isEmpty()) pages = listOf(Page.Guide)
                restoreTo = 0
            }
        }
        if (announce) pendingAnnounce = "换到${sceneName(channel)}频道。"
    }

    // 分区完成徽章：进入 / 切频道 / 每次作答（answerTick 变化）后刷新。
    // 注：完成判定只依赖间隔层 days（design.md §9.5），徽章即时更新；「忘了」days=1 → 即时退出完成状态。
    // 间隔层只由每日任务考试卡作答写入 → 分区「学完」标记只能靠每日任务推进（浏览不写状态）。
    LaunchedEffect(channel, answerTick) { graduated = repo.graduatedScenes() }

    // 断点/频道切换跳页（restoreTo 置空防重复；需先于停稳监听声明，保证跳页先行）
    // v5 R10：队列型取 frontier；池型首次进区恒为 0（v20 起切回走快照恢复，见装载 effect）；
    // v9 转浏览/浏览模式装载也归 0
    // v18 修复「切频道有时不播报、首卡不播报」：频道装载播报**不再依赖停稳事件**——
    // 目标页与切换前 currentPage 净不变（最常见：都停在第 0 页）时 `currentPage to
    // isScrollInProgress` 无变化，snapshotFlow 不发事件，停稳 collector 整个不跑，
    // pendingAnnounce 滞留到下一次滑动粘在错误的卡上（复现条件：从第 0 页切频道必哑；
    // 从 ≥ 第 2 页切才有钳位事件可播）。改为跳页完成后**在此直接播**：
    // - announce == null（App 首次装载）不播——欢迎语已在装载分支按队列状态播过；
    // - 先记 spokenKeys 去重键再 speak，挡住停稳 collector 稍后对同一页的迟到事件（不重播）；
    // - 用户已抢滑离目标页 → 不播不消费，交还给停稳 collector（它的 announce 消费逻辑兜底）。
    LaunchedEffect(pages, restoreTo) {
        val target = restoreTo ?: return@LaunchedEffect
        restoreTo = null
        if (pages.isEmpty()) return@LaunchedEffect
        val idx = target.coerceIn(0, pages.lastIndex)
        pagerState.scrollToPage(idx)
        val announce = pendingAnnounce ?: return@LaunchedEffect
        if (pagerState.currentPage != idx) return@LaunchedEffect
        pendingAnnounce = null
        val p = pages.getOrNull(idx) ?: return@LaunchedEffect
        val key = spokenKey(p)
        // 落点键同步：跳页播过的卡若不登记 lastSettledKey，下一次停稳事件清的是旧键，
        // 本页键留在 spokenKeys 里 → 回滑到首卡会被去重挡成沉默（v18）
        lastSettledKey = key
        if (spokenKeys.add(key)) tts.speak(announce + cardSpeech(p))
    }

    // 翻页落库（仅队列型任务阶段）+ 临近队尾预追加池型页（v5 R10 §5.7-2）。
    // - **队列型任务阶段**（每日任务 && !browseMode）：断点写 frontier；浏览页追加的触发是
    //   `doneIdx >= 0 && idx >= doneIdx - 1`——任务阶段锁滑，此分支实际只在自动前进到完成卡时可达；
    //   **v9：到达完成卡（idx >= doneIdx）→ `enterBrowse` 转浏览模式**（上滑引导已播，
    //   任务卡移除、完成卡保留在首位）；
    // - **browseMode / 池型（分区/收藏）**：**不落库**（页码无意义）+ 临近队尾即追加 = **无限流**（D2）。
    // 追加只发生在**列表尾部**，与「只追加不重排」的既有不变量一致。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { idx ->
            if (channel == StudyRepository.CHANNEL_DAILY && !browseMode) {
                repo.saveQueuePosition(frontierIndex())
                val doneIdx = pages.indexOfFirst { it is Page.Done }
                if (doneIdx >= 0 && idx >= doneIdx - 1) appendPoolPages(POOL_BATCH)
                if (doneIdx >= 0 && idx >= doneIdx) enterBrowse(doneIdx)
            } else {
                if (idx >= pages.lastIndex - 1) appendPoolPages(POOL_BATCH)
            }
        }
    }

    /** 将仓库事务已插入的重复卡同步到页面；页面只按稳定 cardId 找定位，不平移旧卡身份。 */
    fun insertRepeatCard(t: Term, p: Page.TermPage, result: AnswerResult) {
        val repeatCardId = result.repeatCardId ?: return
        val currentIndex = pages.indexOfFirst { (it as? Page.TermPage)?.cardId == p.cardId }
        if (currentIndex < 0) return
        val beforeIndex = result.insertBeforeCardId?.let { beforeId ->
            pages.indexOfFirst { (it as? Page.TermPage)?.cardId == beforeId }
        } ?: -1
        val insertAt = beforeIndex.takeIf { it > currentIndex }
            ?: minOf(currentIndex + 1 + StudyRepository.MIN_GAP, pages.size)
        val newSeq = nextSeq()
        val out = ArrayList<Page>(pages.size + 1)
        pages.forEachIndexed { i, pg ->
            if (i == insertAt) out.add(Page.TermPage(t, CardMode.REVIEW, newSeq, repeatCardId))
            out.add(pg)
        }
        if (insertAt >= pages.size) out.add(Page.TermPage(t, CardMode.REVIEW, newSeq, repeatCardId))
        pages = out
    }

    /** v20 收藏频道（QYJ 2026-09-21 反馈）：取消收藏后该词的浏览卡**即时移除**——收藏页的内容
     *  本体就是收藏集合，取消后卡片还挂着是误导。v6 的「浏览中不重排已出卡」口径**保留给其他
     *  池型频道**（分区/温故流/常用词：取消收藏不动已出卡，下次装载/重洗自然不再抽到）。
     *  实现要点：
     *  - 移除该词在 pages 里的**全部**卡（无限流池重洗后同词可能已出现多次）；
     *  - 同步从抽取池余量剔除（否则临近队尾追加时又出来）；
     *  - 落点校正：当前页之前的移除使下标左移（removedBefore）；当前页本身被移除时钳到
     *    最近的合法下标——自然落在下一张保留卡上；落点没变就不跳页；
     *  - 全部移光 → 插引导页（与装载分支同一兜底）；
     *  - `scrollToPage` 是 suspend → 经 uiScope 派发；`pages` 写入先行，协程执行时读到新列表。
     *  零写入契约不破坏：本函数只动 UI 会话态，不碰 favorites / TermState / DayState。 */
    fun removeFavoriteCards(termId: String) {
        val cur = pagerState.currentPage
        var removedBefore = 0
        var currentRemoved = false
        pages.forEachIndexed { i, pg ->
            if (pg is Page.TermPage && pg.t.id == termId) {
                if (i < cur) removedBefore++
                if (i == cur) currentRemoved = true
            }
        }
        val kept = pages.filterNot { it is Page.TermPage && it.t.id == termId }
        if (kept.size == pages.size) return
        freePool = freePool.filterNot { it.id == termId }
        pages = if (kept.isEmpty()) listOf(Page.Guide) else kept
        val target = (cur - removedBefore).coerceIn(0, pages.lastIndex)
        if (!currentRemoved && target == cur) return   // 落点未变，不必跳页
        uiScope.launch { pagerState.scrollToPage(target) }
    }

    /** 词库结构变化后的统一刷新（2026-09-23 抽取：设置页导入/删除与空态一键导入共用，
     *  原是 SettingsScreen.onBanksChanged 的内联实现）。
     *  newShownIds = 本次**首次入库**的库 id——「显式导入 = 明确想学」→ 默认显示；
     *  重导替换不在列表里，不动用户手动改过的显隐。 */
    fun applyBanksChanged(newShownIds: List<String>) {
        settings.rescanScenes()
        newShownIds.forEach { settings.setSceneVisible(it, true) }
        // 词库已变，会话快照里的 Term 对象可能是旧库内容（删除/替换后切回该频道会恢复出
        // 已删除的词或旧用途说明）——全部作废，切回时走完整装载重新取词。
        channelSnapshots.clear()
        repo.invalidateStaleQueue()
        customBanks = CustomBanks.all
        orderedScenes = settings.orderedScenes()
        hiddenIds = settings.hiddenIds()
        stats = repo.studyStats()
        bankTick++
    }

    /** 一键导入官方词库（2026-09-23 修复 #1）：assets 里的官方 CSV 逐库走与手动导入
     *  相同的 parseCsv 校验入库，成功后同一套刷新。结果用语音反馈——空态场景下老人
     *  看着引导页，导入成功后 bankTick 会把 Guide 页换成真正的任务卡。
     *  skipInstalled = 只补缺不覆盖（2026-09-24 修复 #3）：家属同名导入过的库不被官方版覆盖。 */
    fun importOfficialBanks() {
        val result = CustomBanks.importOfficial(context, skipInstalled = true)
        applyBanksChanged(result.imported.filter { it.isNew }.map { it.bank.id })
        when {
            result.imported.isNotEmpty() -> tts.speak("词库装好了，可以开始学习了。")
            result.skippedExisting > 0 -> tts.speak("词库已经装好了。")
            else -> tts.speak("词库没有装上，请家人帮忙看看。")
        }
    }

    /** 作答（认识/忘了）：连击层 + 间隔层双层并存（prd v8 / design.md §13.2）。作答入口只剩
     *  **每日任务卡**（温故流/分区/收藏都是浏览卡，没有 √/×）——战果只在每日任务频道产生，
     *  故完成卡天然只统计每日任务（v16：战果数字不再缓存，完成卡与播报都直接读 repo 的持久口径）。
     *  - **间隔层**：新词首次认识先建立 {1,今天,0}；复习词（隔天首次作答）答「认识」即推进间隔（v28）；
     *    新词/当天答错过的词完成当日连击后等下次复习检验再推进；首见忘了只建状态不记遗忘（v28）。
     *    SM-2 动态间隔 + restoreTo 兑现 + 封顶分级，全在 repo.answerCard 内。
     *  - **连击层**（v8）：answerCard 返回连击计数 count——`count < result.target` →
     *    仓库事务内插入重复卡（同词不连续/紧邻，prd v8 第 1 条）；count == target → 移出当日队列
     *    （v28：复习检验 target=1，新词/当天答错过的词 target=3）。
     *    忘了 → 连击清零，当日同样打散重现（重新连击到目标才能移出）。
     *  - **播报分级**（v21.1 起）：连击未满 → 随机夸奖（ANSWER_PRAISE，不报剩余次数）；
     *    满目标 → 学会啦（upgraded 才播天数，闸门挡住不播——沿用 v5 R11 口径）；
     *    忘了 → 「没关系，再学一遍」（不变）。
     *  展开/文案按 seq（本次出现）记录；作答同时按卡实例落库 answered（断点恢复用，design.md §9.1）。 */
        fun answer(p: Page.TermPage, known: Boolean) {
            val cardId = p.cardId ?: return
            if (revealed[p.seq] == true) return
            val t = p.t
            val result = repo.answerCard(cardId, known)
            if (result == null) {
                // 队列日期不符（App 开着跨零点）返回 null：立即检查跨日并重装载，
                // 不等最多 60s 的前台轮询——否则这段时间按「认识 / 忘了」毫无反应
                //（2026-09-24 修复 #5）。
                checkDayRollover()
                return
            }
            revealed[p.seq] = true
            if (known) {
                val count = result.count
                val upgraded = result.upgraded
                val daysAfter = result.daysAfter
                // 播报（v8 连击分级 + v5 R11 升级口径：满目标且升级才报天数）
                // v21.1（QYJ 2026-09-22 三项拍板）：连击未满 → 随机夸奖池（不报次数）；
                // 满目标升级 → 保留「N 天后再来复习」（复习计划信息，只在升级那刻说一次）；忘了 → 不动
                // v28：目标连击数从 AnswerResult 读——复习检验 1 次「认识」即过关，
                // 不能再用固定的 DAILY_COMBO_TARGET(3) 判断，否则复习卡会说成「真棒」而漏报复习计划
                val target = result.target
                val (msg, spoken) = when {
                    count < target -> {
                        val praise = ANSWER_PRAISE.random()
                        "👍 $praise" to "$praise。"
                    }
                    upgraded && daysAfter == 1 ->
                        "👍 这个词学会啦！明天再来复习" to "这个词学会啦！明天再来复习。"
                    upgraded ->
                        "👍 这个词学会啦！${daysAfter} 天后再来复习" to "这个词学会啦！${daysAfter}天后再来复习。"
                    else -> "👍 这个词学会啦" to "这个词学会啦。"
                }
                results[p.seq] = msg
                tts.speak("${termSpeech(t)}。$spoken")
            } else {
                results[p.seq] = "没关系，再学一遍 💪"
                tts.speak("${termSpeech(t)}。没关系，再学一遍。")
            }
            insertRepeatCard(t, p, result)
            // v16：本次作答一定会改间隔层，徽章可能要重算 —— 计数器无脑 +1（战果数字直接读 repo，不在这里缓存）
            answerTick++
            // v5 R7 总结卡条件出现：全部任务词连击满各自目标移出（无待处理卡）→ 总结卡此时才出现在队尾
            syncDonePage()
            // v5 R8 作答后自动前进（§5.5）：置触发键，由下面 keyed 该 seq 的 effect 等播报念完再翻页
            advanceAfterSpeechSeq = p.seq
        }

    // 滑动即播（v9 契约）。
    // ⚠️ key 只有 pagerState（**去掉 pages**）：转浏览装载浏览页、完成卡条件出现都在本 effect
    //    体外/体内改 `pages`，若 `pages` 还是 key，effect 会执行到一半被自己重启掉（改了但没播报）。
    //    `pages` 在 collect 体内读 State 委托 getter 即最新值。
    // 任务阶段锁滑（userScrollEnabled=false）后本 effect 的翻页事件只来自程序 animateScrollToPage
    // （作答自动前进 / 断点跳页 / 转浏览装载）；v5 R7 前向拦截已删除（锁滑结构性取代，死代码不留）。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
            .collect { (idx, scrolling) ->
                // 设置页打开期间不响应停稳事件（2026-09-23 复审修复）：导入/删除词库或显隐变化
                // 会在设置页背后重载当前频道并跳页，若照常消费停稳事件，会朗读设置页背后的卡片
                //（设置页是全屏 overlay，期间停稳事件只来自程序跳页，屏蔽无副作用）。
                if (showSettings) return@collect
                if (scrolling) {
                    tts.stop()
                    return@collect
                }
                delay(SETTLE_SPEECH_DELAY_MS)
                if (pagerState.isScrollInProgress) return@collect   // 200ms 内又开始滑了
                if (pagerState.currentPage != idx) return@collect   // 迟到的旧页事件（跳页/连滑已完成）：交给新事件播报，防错位闪播
                if (restoreTo != null) return@collect               // 断点跳页完成前不播，防错位闪播
                val p = pages.getOrNull(idx) ?: return@collect
                val key = spokenKey(p)
                // v18：落点换了页 = 离开了上一张落点卡 → 清它的去重键，回头再划到可以重新朗读；
                // 同一页内的重复停稳事件（拖动后弹回等）键相同、不清，去重继续防同一次重播
                if (lastSettledKey != null && lastSettledKey != key) spokenKeys.remove(lastSettledKey)
                lastSettledKey = key
                val announce = pendingAnnounce
                pendingAnnounce = null
                // v9：任务卡停稳返回空文案 → 整体沉默（显示词卡不朗读，prd v9 第 1 条）；
                // 浏览卡 / 完成卡 / 引导卡照常播报。换频道播报与本卡播报拼成**一句**：
                // 分开 speak 会互相 FLUSH 掉。
                // v18：频道装载播报的主消费者已移到跳页 effect（currentPage 净不变时本 collector
                // 收不到事件，见该处注释）；此处 announce 实际恒为 null，保留只为兜底
                // （跳页 effect 因用户抢滑提前 return 而未消费的场景）。
                val text = cardSpeech(p)
                val full = if (announce != null) announce + text else text
                if (full.isNotBlank() && spokenKeys.add(key)) tts.speak(full)
            }
    }

    // v5 R8 作答后自动前进（§5.5）：必须等这句播报念完再翻，否则落点卡的播报会把答案与反馈掐掉。
    // key 只用作答卡的 seq：`answer()` 置位触发，effect 收尾置回 -1。
    // v7：作答入口只剩每日任务的考试卡 → 浏览卡天然不自动前进（用户还没看完就翻页是打扰）；
    // 考试阶段 userScrollEnabled=false，用户无法抢方向，「播报期间放弃自动前进」的防御分支
    // 只在 browseMode（自由浏览，无作答入口）理论可达，保守保留。
    LaunchedEffect(advanceAfterSpeechSeq) {
        val seq = advanceAfterSpeechSeq
        if (seq < 0) return@LaunchedEffect
        delay(AUTO_ADVANCE_MIN_MS)          // 兜底最短停留（TTS 不可用时也不瞬间翻走）
        tts.awaitQuiet()                    // 等朗读结束（含作答反馈；内部 20s 上限）
        try {
            val cur = pagerState.currentPage
            val curPage = pages.getOrNull(cur)
            // 用户已自己动过（手动上滑 / 回看）→ 放弃本次自动前进，不抢方向
            if (curPage !is Page.TermPage || curPage.seq != seq || pagerState.isScrollInProgress) return@LaunchedEffect
            // 落点 = 下一张：v8 连击重复卡由 answer() 在**当前卡之后**（隔 MIN_GAP 张，钳到队尾）
            // 插入，插入点恒 ≥ cur+1——cur+1 仍是既有下一张（队列中段）或刚插入的重复卡（队尾钳制时），
            // 下标关系稳定；若本次作答使全部任务词连击满 3 移出，`syncDonePage()` 已在 `answer()` 里
            // 同步插入完成卡，落点恰好是完成卡（当日会话自然收尾；到达后由 collector 触发
            // enterBrowse 转浏览模式，v9）
            if (cur < pages.lastIndex) pagerState.animateScrollToPage(cur + 1)
        } finally {
            // ⚠️ 置回 -1 必须排在 `animateScrollToPage` **之后**（用 finally 保证用户抢方向打断动画时也归位）：
            //    `advanceAfterSpeechSeq` 是本 effect 自己会改写的 key——写在翻页之前会让 effect 被自己重启并取消，
            //    翻页动画刚开始就胎死腹中（spec/frontend/state-management.md Gotcha (2)：effect 体内写过的 state
            //    不要作自己的 key）。也不能把 delay/awaitQuiet 一起包进来：用户答下一张（key 换成新 seq）时
            //    会被这里一并清成 -1，下一次自动前进就丢了。
            //    比较后再清：动画途中用户若已答了下一张（key 已换成新 seq），这里的收尾不能把新 key 抹掉。
            if (advanceAfterSpeechSeq == seq) advanceAfterSpeechSeq = -1
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(AppSurface)   // 暖米色整页底：状态栏/频道栏/卡片区同色，白卡浮其上
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // 无声故障横幅（2026-09-23 修复 #3）：缺中文引擎优先于静音提示——前者整个 App
            // 不出声且必须装引擎才能修，后者通常调一下音量就好。「去设置」跳系统 TTS 设置，
            // 装好引擎回来 ON_RESUME 自动重检（免重启）。
            when {
                chineseVoice == ChineseVoiceState.MISSING -> NoticeBanner(
                    text = "手机缺少中文朗读，请家人装一个中文语音引擎",
                    actionLabel = "去设置",
                ) {
                    runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
                }
                mediaMuted && !mutedDismissed -> NoticeBanner(
                    text = "手机静音中，听不到朗读",
                    actionLabel = "知道了",
                ) { mutedDismissed = true }   // 本次会话不再提醒；恢复音量横幅也会自动消失
            }
            // v6：scenes = 设置过滤排序后的可见分区（rec/fav 固定渲染在前列，不参与隐藏/排序）；
            // onOpenSettings 由推荐 tab 连点 5 次触发（检测在 ChannelBar 内部）
            ChannelBar(
                current = channel,
                graduated = graduated,
                scenes = visibleScenes,
                onOpenSettings = { showSettings = true },
                onSelect = { new ->
                    // v20：切走前保存当前频道的会话快照（页面流 + 抽取池 + 落点 + browseMode），
                    // 切回时由装载 effect 原样恢复——rec 浏览不再被拽回完成卡，池型频道不再重新洗牌
                    if (new != channel) {
                        channelSnapshots[channel] = ChannelSnapshot(
                            date = repo.today(),
                            pages = pages,
                            pool = freePool,
                            page = pagerState.currentPage,
                            browseMode = browseMode,
                        )
                        channel = new
                        switchAnnounce = true   // v23：装载 effect 的 key 含 bankTick 后，播报改由这里显式置位
                    }
                },
            )

            VerticalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(AppSurface),
                // 任务锁滑（v7 prd 第 2 条 / v9 第 1 条）：每日任务频道任务阶段（browseMode=false 且
                // 完成卡未到达）**禁止上下滑**——任务中不允许滑动切换词卡，唯一翻页动力 = 作答后
                // 自动前进（程序 animateScrollToPage 不受影响）。全部任务词连击满 3 → 完成卡落地，
                // 当前页到达完成卡即转浏览模式（enterBrowse → browseMode=true 解锁）；此后完成卡
                // 之后全是浏览页，可自由上滑 / 回滑到完成卡，任务卡已被移除回不去。
                // browseMode / 池型频道恒可滑；末行兜底覆盖 collector 触发前的极短窗口。
                userScrollEnabled = run {
                    val doneIdx = pages.indexOfFirst { it is Page.Done }
                    channel != StudyRepository.CHANNEL_DAILY || browseMode ||
                        (doneIdx >= 0 && pagerState.currentPage >= doneIdx)
                },
                // 列表只追加不重排，index 其实已足够；保留 key 是为了让「页身份」与
                // 「列表位置」解耦——TermPage -> 页身份 seq、Done -> "done"，将来若再引入重排不必重踩坑。
                // 取不到页（列表刚被重建）时给一个不会撞的兜底 key
                key = { idx ->
                    when (val pg = pages.getOrNull(idx)) {
                        is Page.TermPage -> "seq-${pg.seq}"
                        Page.Done -> "done"
                        Page.Guide -> "guide"     // v6 空收藏引导页
                        null -> "ghost-$idx"
                    }
                },
            ) { idx ->
                when (val p = pages.getOrNull(idx) ?: return@VerticalPager) {
                    is Page.TermPage -> TermCard(
                        term = p.t,
                        mode = p.mode,
                        // v8：revealed = 已作答（控制按钮隐藏/结果文案）；拼音/提示**恒显示**
                        //（防泄题契约废除，design.md §13.2——自评模式下无密可泄）
                        revealed = p.mode == CardMode.FREE || revealed[p.seq] == true,
                        resultText = results[p.seq],
                        // v6 收藏星按钮：全卡型可见；只写 favorites（独立于 TermState/DayState，
                        // 收藏分区零写入不破坏）。v20：取消收藏在 **fav 频道内即时移除**该词的卡；
                        // 其他频道不重排已出卡，后续装载/重洗自然不再抽到该词。
                        isFavorite = p.t.id in favorites,
                        onToggleFavorite = {
                            val added = repo.toggleFavorite(p.t.id)
                            favorites = repo.favorites().toSet()
                            // 关键操作语音反馈（适老化硬约束：按钮点击朗读含义）
                            tts.speak(if (added) "已收藏" else "已取消收藏")
                            // v20：收藏频道内取消收藏 → 该词的卡即时移出收藏页（removeFavoriteCards）；
                            // 其他频道浏览卡不动（v6「不重排已出卡」口径保留），下次装载/重洗自然生效
                            if (!added && channel == StudyRepository.CHANNEL_FAV) {
                                removeFavoriteCards(p.t.id)
                            }
                        },
                        // v8：防泄题分流删除（design.md §13.2）——点卡片一律重听「词 + 用途」
                        onSpeakTerm = { tts.speak(termSpeech(p.t)) },
                        // v18 单字直读升级：点单字 = 只读被点的那个字（不再读整词），
                        // 与点卡片重听「词 + 用途」区分；字卡弹层已随 v7 删除。
                        // v8：防泄题分流删除——未作答时也直读（拼音本来就可见，无密可泄）
                        onSpeakWord = { c -> tts.speak(c) },
                        onAnswer = { known -> answer(p, known) },
                    )
                    Page.Done -> DoneCard(
                        // v16：两个数都取**持久口径** —— v9 的 enterBrowse 会在到达完成卡的那一帧
                        // 移除任务卡，由 pages 派生的计数会当场归零（见上方注释）。
                        // 「认识了 x 个词」已从完成卡移除：连击机制下它恒等于 words（见 DoneCard KDoc）。
                        words = repo.todayTaskWordCount(),
                        forgot = repo.todayForgot(),
                        // v9：无「确认」按钮——到达完成卡即转浏览模式（enterBrowse，见 collector）；
                        // inBrowse 切换底部引导文案（浏览模式中显示「随便看看吧」）
                        inBrowse = browseMode,
                    )
                    Page.Guide ->
                        // 空态文案三分（2026-09-23 复审修复 #2，与 cardSpeech 同一判据）：
                        // 全库无词 → 导入引导（v23.1 起带「一键导入」按钮，修复 #1：官方词库
                        // 随 APK 分发，家属不用再去 GitHub 下载 CSV）；推荐频道 + 有词 →
                        // 分区全隐藏引导；否则空收藏引导
                        if (STUDY_TERMS.isEmpty()) {
                            GuideCard(
                                EMPTY_BANK_GUIDE_TITLE, EMPTY_BANK_GUIDE_BODY, "一键导入官方词库",
                                icon = Icons.Filled.Add, iconTint = BluePrimary, haloColor = BlueBg,
                            ) {
                                importOfficialBanks()
                            }
                        } else if (channel == StudyRepository.CHANNEL_DAILY) {
                            GuideCard(
                                HIDDEN_ALL_GUIDE_TITLE, HIDDEN_ALL_GUIDE_BODY,
                                icon = Icons.Filled.Settings, iconTint = BluePrimary, haloColor = BlueBg,
                            )
                        } else {
                            GuideCard(FAV_GUIDE_TITLE, FAV_GUIDE_BODY)
                        }
                }
            }
        }

        // v6 全屏设置页 overlay：条件渲染在 feed **之上**。⚠️ 不能写成 `if (showSettings) SettingsScreen
        // else Column` 的互换渲染——Column 离开组合会把 pages / seqGen / revealed 等会话态全部丢掉
        //（remember 状态与其在组合中的位置绑定）。
        if (showSettings) {
            SettingsScreen(
                orderedScenes = orderedScenes,
                hiddenIds = hiddenIds,
                quota = quota,
                quotaNew = quotaNew,   // v6 R14 每日新词配额（「写库 → 重读镜像」同下）
                stats = stats,         // v14 学习统计只读快照（打开设置页时重取，见上方 effect）
                customBanks = customBanks,   // v22「我的词库」镜像
                // v22/v23 导入/删除后的统一刷新（2026-09-23 抽取为 applyBanksChanged，
                // 与空态引导页的「一键导入官方词库」共用）；参数 = 首次入库的库 id 列表
                //（设置页单文件导入至多一个元素）
                onBanksChanged = { firstImportIds -> applyBanksChanged(firstImportIds) },
                // 显隐/顺序/配额都是「写 AppSettings → 重读镜像」两步：真源在持久层，镜像只管重组
                onSetVisible = { id, visible ->
                    settings.setSceneVisible(id, visible)
                    hiddenIds = settings.hiddenIds()
                    orderedScenes = settings.orderedScenes()
                    // v16：总览分母 = 当前可学范围（常用词 + 可见分区），显隐一变口径就变 ——
                    // 必须当场重取快照，否则用户开关分区后上面的「已学 X / N」纹丝不动。
                    stats = repo.studyStats()
                    // 开启分区当天生效（2026-09-23 复审修复）：全部分区隐藏时 rec 当天生成的是
                    // 0 词空队列，不作废它「当日冻结」会让新词拖到明天。invalidateStaleQueue 只动
                    // 空队列 / 未确认的鬼 id 队列，非空已确认队列的冻结语义不破。
                    repo.invalidateStaleQueue()
                    // 只有**当前频道的池依赖可见范围**时才需要重载（2026-09-24 修复 #6）：
                    // rec（聚合范围 = 可见分区 + 常用词）与常用词频道（同为聚合范围）。
                    // 分区 / 收藏频道的池只含本分区（或收藏集）的词，开关**别的**分区不影响
                    // 它们——bankTick++ 会把浏览页重新洗牌、丢掉浏览位置，不做。
                    // 当前分区被关掉的情形不需要这里管：visibleScenes effect 会把频道弹回 rec，
                    // channel 变化本身就是装载 effect 的 key。
                    if (channel == StudyRepository.CHANNEL_DAILY ||
                        channel == StudyRepository.CHANNEL_COMMON
                    ) {
                        bankTick++
                    }
                },
                // v16 拖动落位（v17 起仅已显示分区可拖；↑↓ 按钮已删，无 onMove 回调）：
                // 与 onSetVisible 同一套「写库 → 重读镜像」，落点是目标分区在 order 里的下标
                onMoveTo = { id, targetIndex ->
                    settings.moveSceneTo(id, targetIndex)
                    orderedScenes = settings.orderedScenes()
                },
                onSetQuota = { n ->
                    settings.setQuota(n)
                    quota = settings.quota()
                    // 刻意不触碰当日队列：配额只在下次 buildQueue 重建队列时被读（当日冻结、次日生效）
                },
                onSetQuotaNew = { n ->
                    settings.setQuotaNew(n)
                    quotaNew = settings.quotaNew()
                    // 同上：当日队列冻结、次日生效
                },
                onDone = { showSettings = false },
            )
        }
    }
}
