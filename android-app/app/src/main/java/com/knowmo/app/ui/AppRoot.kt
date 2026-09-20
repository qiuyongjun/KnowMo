package com.knowmo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.knowmo.app.data.AppSettings
import com.knowmo.app.data.STUDY_TERMS
import com.knowmo.app.data.StudyRepository
import com.knowmo.app.data.Term
import com.knowmo.app.data.sceneName
import com.knowmo.app.tts.TTSSpeaker
import com.knowmo.app.ui.theme.AppSurface
import kotlinx.coroutines.delay

/** feed 页：词条卡（mode 由调度器运行时计算：新学/复习/浏览）或总结卡。
 *  seq = 本会话内的出现序号：v8 起每日队列**首排每词一张卡**，连击未满 3 的词由 `repeatCard`
 *  在作答后运行时插入重复卡（打散，prd v8 第 1 条）——展开/作答状态仍按「出现」记录而非按词记录
 *  （同一词的多次出现各自独立作答），池型浏览页与任务页互不干扰，旧数据兼容也更稳。
 *  qIndex = 该卡在持久化当日队列中的下标（作答时 markAnswered 用；repeatCard 插入的新卡带新下标）；
 *  池型页（温故流 / 分区浏览）= -1（不落库）。
 *  v6：`Guide` = 空收藏引导页（**仅收藏频道空池可达**——场景分区池必非空；大字引导 + 播报同源 FAV_GUIDE_SPEECH，
 *  防 VerticalPager pageCount == 0 白屏）。 */
sealed interface Page {
    data class TermPage(val t: Term, val mode: CardMode, val seq: Int, val qIndex: Int) : Page
    data object Done : Page
    data object Guide : Page
}

data class SessionStats(val known: Int = 0, val forgot: Int = 0)

/** 池型频道的预追加页数（v5 R10：温故流与分区共用同一条追加逻辑；临近队尾 / 确认后装载时预追加，池空重洗） */
private const val POOL_BATCH = 2

/** 停稳判定后的播报去抖（ms）：滑动进行中不播，停稳后再等这么久，快速连滑不闪播 */
private const val SETTLE_SPEECH_DELAY_MS = 200L

/** v5 R8 作答后自动前进的最短停留（ms）：答案与反馈至少在屏幕上停这么久再翻页，
 *  TTS 不可用时也不至于瞬间翻走（§5.5 两段式等待的第一段） */
private const val AUTO_ADVANCE_MIN_MS = 1500L

/**
 * 根界面：抖音式垂直 feed（v9：任务卡静默自评 + 完成卡上滑转浏览，prd v9 / design.md §14）。
 * 交互契约（v6–v8 未推翻条目沿用）：
 * 1. 滑到停稳（isScrollInProgress=false 后 ~200ms）才播报；快速连滑中间卡不闪播
 * 2. **任务卡静默**（v9 第 1 条）：每日任务卡（NEW/REVIEW 同一卡型，词+拼音+提示恒全显示，v8）
 *    停稳**不自动朗读**——用户直接按「认识 / 忘了」自评，或**点卡片听读**（词+提示）、
 *    点单字读词后再作答；作答后仍念「词+提示+反馈」（适老化）。
 *    **浏览卡**（温故流/分区/收藏）滑到停稳自动念「词 + 用途」（v9 第 2 条：自主学习划入即读）。
 * 3. **当日连击**（v8 保留）：同词当日 3 次「认识」才移出队列，任意「忘了」清零；
 *    未满 3 → `insertRepeatCard` 按 `repo.repeatCard` 的打散位置重插（同词不连续/紧邻，
 *    插入点之后的任务卡 qIndex 同步 +1）；间隔层作答即写（SM-2 + 每日最多升一级闸门 + 封顶分级）。
 * 4. **任务锁滑**（v7 保留，v9 第 1 条不变）：每日任务频道任务阶段 `userScrollEnabled = false`
 *    ——任务中不允许滑动切换词卡，唯一翻页动力 = 作答后自动前进（等播报念完再翻）。
 * 5. **完成卡上滑转浏览**（v9 第 1 条，取代 v7「点确认」）：全部任务词连击满 3 → 完成卡落地，
 *    **到达完成卡即 `enterBrowse`**：`repo.markConfirmed()` 持久化 + 移除全部任务卡
 *    （**完成卡保留在首位**）+ 装载温故流——上滑进入推荐浏览、回滑仍可看完成卡战果，
 *    但任务卡已被移除、**回不去**。当日重启：confirmed == true 直接进浏览模式（完成卡在首位）；
 *    false 走断点恢复（仍锁滑）。
 * 6. **推荐范围收窄**（v9 第 3/4 条）：推荐频道（每日任务 + 温故流）只含**可见分区**的词
 *    （`recScenesProvider` 注入，MainActivity 传 settings.visibleScenes()）+ **常用词分区**
 *    （`CHANNEL_COMMON`，不可显隐、恒入推荐——QYJ 拍板的内容源特例：默认只有推荐+收藏
 *    两个频道时推荐才有内容）。隐藏分区的词两个入口都抽不到。
 * 7. 不展示"第 x/y 张"进度条，进度由完成卡和语音表达
 * 8. 分区完成（design.md §9.5，days >= 15）→ 频道栏加 🎓，即时可逆
 *    （间隔层只由每日任务作答写入）
 * 9. 频道分两类（v5 R10，§5.7）：
 *    **队列型 = 只有每日任务频道**（`CHANNEL_DAILY` = "rec"）——`repo.ensureQueue()` / `DailyQueue` /
 *    完成卡 / 锁滑 / 断点 = frontier 只属于它；
 *    **池型 = 场景分区 + 推荐频道的温故流 + 收藏频道**——运行时从池里**加权随机**抽卡（v9 第 2 条：
 *    算法排列不用固定顺序）、无限追加、**不落库**（`appendPoolPages`）。两处的卡都是**浏览卡**
 *    （`CardMode.FREE`：词 + 逐字拼音 + 用途全展开、无 √/×——是否认识只在每日任务中出现、
 *    点卡重听、点单字读词、零写入）。
 *    分区因此：**无完成卡**（`syncDonePage` 对非每日任务频道直接 return）、**不做到期筛选**
 *    （池 = 该区全部词含未学词 → 池必非空 → 不白屏）、**不恢复位置**（D6：每次进区都是一轮新的随机；
 *    装载时 `restoreTo = 0` 必须**显式归零**——切频道时 `pagerState.currentPage` 可能还是上一个频道
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
 *     重组；浏览中取消收藏**不重排**已出卡（pages 不动），后续池重洗自然不再抽到该词。
 */
@Composable
fun AppRoot(repo: StudyRepository, tts: TTSSpeaker, settings: AppSettings) {
    var channel by remember { mutableStateOf(StudyRepository.CHANNEL_DAILY) }
    var session by remember { mutableStateOf(SessionStats()) }
    val revealed = remember { mutableStateMapOf<Int, Boolean>() }      // seq -> 任务卡是否已作答（v8：不再控制拼音显隐——卡片恒全展开，只控制按钮隐藏/结果文案；浏览卡不写这里）
    val results = remember { mutableStateMapOf<Int, String>() }        // seq -> 反馈文案
    var seqGen by remember { mutableStateOf(0) }                       // 页出现序号发生器（只在 effect/回调中递增）
    val spokenKeys = remember { mutableSetOf<String>() }               // 自动朗读去重（频道 + 页身份）
    var graduated by remember { mutableStateOf(emptySet<String>()) }   // 已完成分区（频道栏 🎓）

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
    val visibleScenes = orderedScenes.filter { it.id !in hiddenIds }   // 频道栏渲染序（rec/fav 固定渲染，不在此列）
    // v6 收藏：收藏集合快照（TermCard 星按钮显色用；toggleFavorite 后整体重读触发重组）
    var favorites by remember { mutableStateOf(repo.favorites().toSet()) }

    // feed 页面：队列型 = 当日队列 + 总结卡（条件存在）+ 温故流追加页；池型（分区）= 池型浏览页（无限流）
    var pages by remember { mutableStateOf<List<Page>>(emptyList()) }
    var freePool by remember { mutableStateOf<List<Term>>(emptyList()) } // 池型抽取池：顺序抽取，池空重洗（两个频道类型共用）
    var restoreTo by remember { mutableStateOf<Int?>(null) }             // 断点/频道切换的目标页
    var pendingAnnounce by remember { mutableStateOf<String?>(null) }    // 频道播报（与首卡合并成一句播）
    var firstChannelLoad by remember { mutableStateOf(true) }
    // v5 R8 作答后自动前进（§5.5）：本次刚作答的卡 seq；-1 = 没有待办的自动前进。
    // 用作答卡的 seq 作 effect key（而非布尔）——既能触发，又能在收尾时置回 -1 取消
    var advanceAfterSpeechSeq by remember { mutableStateOf(-1) }

    val pagerState = rememberPagerState(pageCount = { pages.size })

    // 欢迎语（TTS 未就绪时进 pending 队列，就绪后按序补播，始终先于卡片播报）
    LaunchedEffect(Unit) {
        tts.speak("欢迎回来。上滑开始学习。")
    }

    // v6：打开设置页播报「已打开设置」（prd v6 第 2 条；keyed showSettings，关闭不播）。
    // v14：打开时重取学习统计快照（覆盖上次打开之后的作答变化——今日战果/streak/分区进度）
    LaunchedEffect(showSettings) {
        if (showSettings) {
            stats = repo.studyStats()
            tts.speak("已打开设置")
        }
    }

    // v6：设置显隐改动**即时生效**——当前频道被隐藏（非 rec/fav 且不在可见分区）→ 回退推荐频道。
    // 回退由既有的 LaunchedEffect(channel) 装载分支自然接管（重建队列 / 重建池型页）。
    LaunchedEffect(visibleScenes) {
        if (channel != StudyRepository.CHANNEL_DAILY &&
            channel != StudyRepository.CHANNEL_FAV &&
            channel !in visibleScenes.map { it.id }
        ) {
            channel = StudyRepository.CHANNEL_DAILY
        }
    }

    fun termById(id: String): Term? = STUDY_TERMS.firstOrNull { it.id == id }

    fun nextSeq(): Int = ++seqGen

    fun termSpeech(t: Term) = "${t.text}。${t.tip}"

    /** 待处理（pending）口径（v8 语义同 v7）：每日任务卡（非 FREE）**未作答**——
     *  首见词也是任务卡，无「教读待处理」这一态；v8 连击插入的重复卡 unanswered 时同样计入
     *  （repeatCard 插入的新 seq 未写 revealed）。
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

    /** 任务区去重词数 = 总结卡战果里的 N（§5.3：N = 任务区去重词数）。池型页（浏览卡）不计入。 */
    fun taskWordCount(): Int = pages.asSequence()
        .filterIsInstance<Page.TermPage>()
        .filter { it.mode != CardMode.FREE }
        .map { it.t.id }
        .distinct()
        .count()

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
            "太棒了，今日任务完成！今天学完了${taskWordCount()}个词，" +
                "认识了${session.known}次，忘了${session.forgot}次。" +
                "上滑进入推荐模式，随便看看吧。"
        Page.Guide -> FAV_GUIDE_SPEECH    // v6 空收藏引导（文案与引导卡同源）
    }

    /** 池型追加（v5 R10 §5.7-2）：从池里顺序取 count 张追加到**尾部**；池空用 `repo.poolIds(channel)`
     *  重洗补满（**无限流**，随时可停）。qIndex = -1 → 不落库。三个使用场景共用同一实现：
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
            extra.add(Page.TermPage(next, CardMode.FREE, nextSeq(), -1))
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
        pages = pages.filter { it is Page.Done || (it is Page.TermPage && it.qIndex < 0) }
        pagerState.scrollToPage(target)
        advanceAfterSpeechSeq = -1
    }

    // 装载：**队列型频道**（只有每日任务）走当日队列 + frontier 断点；**池型频道**（场景分区/收藏）
    // 没有队列——直接填一批池型浏览页开始无限流（`pages` 不能为空：`VerticalPager` 的 pageCount == 0
    // 就是白屏）。repo 内 date 不符自动重建队列。
    // v9：rec 装载先看 `q.confirmed`——true 直接进浏览模式（**完成卡保留在首位**，回滑可看战果，
    // 上滑进入浏览流；当日重启不重放任务卡）；false 走任务模式（断点恢复按卡实例 answered[i]
    // 还原作答态，仍锁滑）。
    LaunchedEffect(channel) {
        val announce = !firstChannelLoad
        firstChannelLoad = false
        freePool = emptyList()
        spokenKeys.clear()
        advanceAfterSpeechSeq = -1           // 频道已切换，丢弃上一频道未完成的自动前进
        if (channel == StudyRepository.CHANNEL_DAILY) {
            val q = repo.ensureQueue()
            if (q.confirmed) {
                // v9：今日已进过浏览模式 → 完成卡保留在首位（回滑可看战果），上滑进入浏览流
                browseMode = true
                pages = listOf(Page.Done)
                appendPoolPages(POOL_BATCH)
                restoreTo = 0
            } else {
                browseMode = false
                pages = q.queue.mapIndexedNotNull { i, id ->
                    val t = termById(id) ?: return@mapIndexedNotNull null
                    val mode = if (q.modes[i] == StudyRepository.MODE_NEW) CardMode.NEW else CardMode.REVIEW
                    val seq = nextSeq()
                    // 断点恢复：按该卡实例的 answered[i] 逐卡恢复展开态（design.md §9.1）
                    if (q.answered[i]) revealed[seq] = true
                    Page.TermPage(t, mode, seq, i)
                }
                syncDonePage()                   // 总结卡条件出现：pending == 0 才有收尾页
                // 断点：frontier = 第一张待处理卡（都做完了则是总结卡）——「在哪儿」=「做到哪儿了」
                restoreTo = frontierIndex()
            }
        } else {
            browseMode = false                   // 仅 rec 频道有语义，切走时复位
            // v5 R10（§5.7-3）：池型频道 = 池 + 浏览卡，没有队列、也没有断点——**显式归零**，
            // 用 `0` 而**不是** `null`：切频道时 `pagerState.currentPage` 可能还是上一个频道的旧值
            // （比如 7），而新 `pages` 只有 2 张。D6：不恢复位置，每次进区都是一轮新的随机。
            pages = emptyList()
            appendPoolPages(POOL_BATCH)
            // v6：收藏频道空池（没有任何收藏）→ appendPoolPages 拿不到卡，pages 为空 = 白屏。
            // 插引导页（仅 fav 空池可达；场景分区池 = 全区词必非空，不受影响）。
            if (pages.isEmpty()) pages = listOf(Page.Guide)
            restoreTo = 0
        }
        session = SessionStats(repo.todayKnown(), repo.todayForgot())   // 战果当日持久口径（§5.3）
        if (announce) pendingAnnounce = "换到${sceneName(channel)}频道。"
    }

    // 分区完成徽章：进入 / 切频道 / 每次作答（session 变化）后刷新。
    // 注：完成判定只依赖间隔层 days（design.md §9.5），徽章即时更新；「忘了」days=1 → 即时退出完成状态。
    // 间隔层只由每日任务考试卡作答写入 → 分区 🎓 只能靠每日任务推进（浏览不写状态）。
    LaunchedEffect(channel, session) { graduated = repo.graduatedScenes() }

    // 断点/频道切换跳页（restoreTo 置空防重复；需先于停稳监听声明，保证跳页先行）
    // v5 R10：队列型取 frontier；池型恒为 0（D6 不恢复位置）；v9 转浏览/浏览模式装载也归 0
    LaunchedEffect(pages, restoreTo) {
        val target = restoreTo ?: return@LaunchedEffect
        restoreTo = null
        if (pages.isNotEmpty()) pagerState.scrollToPage(target.coerceIn(0, pages.lastIndex))
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

    /** v8 连击重复卡插入（design.md §13.2；v9 修正 qIndex 平移）：`repeat` 为真且是任务卡
     *  （qIndex ≥ 0）时调 `repo.repeatCard` 拿 insertAt，pages 在**同一下标**插入 REVIEW 页——
     *  任务阶段任务卡区与 queue 一一对应，pages 下标 == queue 下标恒成立；插入点恒在当前卡之后
     *  → 已有卡下标 / frontier 不受影响。⚠️ 插入点之后的任务卡在 queue 里的下标都 +1 了，
     *  pages 里对应卡的 qIndex 必须同步 +1（否则 markAnswered 会按旧下标标记错卡——v8 实现缺陷，
     *  v9 修正）。repo 返回 -1（无队列 / afterIndex 越界的防御兜底）则不插页。 */
    fun insertRepeatCard(t: Term, p: Page.TermPage, repeat: Boolean) {
        if (!repeat || p.qIndex < 0) return
        val insertAt = repo.repeatCard(t.id, p.qIndex)
        if (insertAt < 0) return
        val newSeq = nextSeq()
        val out = ArrayList<Page>(pages.size + 1)
        pages.forEachIndexed { i, pg ->
            if (i == insertAt) out.add(Page.TermPage(t, CardMode.REVIEW, newSeq, insertAt))
            out.add(
                if (pg is Page.TermPage && pg.qIndex >= insertAt) pg.copy(qIndex = pg.qIndex + 1) else pg
            )
        }
        if (insertAt >= pages.size) out.add(Page.TermPage(t, CardMode.REVIEW, newSeq, insertAt))
        pages = out
    }

    /** 作答（认识/忘了）：连击层 + 间隔层双层并存（prd v8 / design.md §13.2）。作答入口只剩
     *  **每日任务卡**（温故流/分区/收藏都是浏览卡，没有 √/×）——`knownAnswers` / `forgotAnswers`
     *  只在这里 +1，故总结卡战果天然只统计每日任务频道。
     *  - **间隔层**：作答即写（v7 口径保留：首见认识 → {1,今天,0}；首见忘了 → {1,今天,lapses+1}；
     *    SM-2 动态间隔 + 每日升一级闸门 + restoreTo 兑现 + 封顶分级，全在 repo.markKnown/markForgot 内）。
     *  - **连击层**（v8）：markKnown 返回连击计数 count——`count < DAILY_COMBO_TARGET` →
     *    `insertRepeatCard` 打散重插（同词不连续/紧邻，prd v8 第 1 条）；count == 3 → 移出当日队列。
     *    忘了 → 连击清零，当日同样打散重现（重新连击 3 次才能移出）。
     *  - **播报按剩余次数分级**（v4 口径恢复）：1 → 再认对 2 次；2 → 再认对 1 次；3 → 学会啦
     *    （upgraded 才播天数，闸门挡住不播——沿用 v5 R11 口径）。
     *  展开/文案按 seq（本次出现）记录；作答同时按卡实例落库 answered（断点恢复用，design.md §9.1）。 */
    fun answer(p: Page.TermPage, known: Boolean) {
        val t = p.t
        revealed[p.seq] = true
        if (p.qIndex >= 0) repo.markAnswered(p.qIndex)   // 池型页 qIndex=-1 不落库
        if (known) {
            val (count, upgraded, daysAfter) = repo.markKnown(t.id)
            session = session.copy(known = session.known + 1)
            // 播报（v8 连击分级 + v5 R11 升级口径：满 3 且升级才报天数）
            val target = StudyRepository.DAILY_COMBO_TARGET
            val (msg, spoken) = when {
                count < target ->
                    "👍 记得牢！再认对 ${target - count} 次就学会" to "记得牢！再认对${target - count}次就学会。"
                upgraded && daysAfter == 1 ->
                    "👍 这个词学会啦！明天再来复习" to "这个词学会啦！明天再来复习。"
                upgraded ->
                    "👍 这个词学会啦！${daysAfter} 天后再来复习" to "这个词学会啦！${daysAfter}天后再来复习。"
                else -> "👍 这个词学会啦" to "这个词学会啦。"
            }
            results[p.seq] = msg
            tts.speak("${termSpeech(t)}。$spoken")
            insertRepeatCard(t, p, repeat = count < target)
        } else {
            repo.markForgot(t.id)
            session = session.copy(forgot = session.forgot + 1)
            results[p.seq] = "没关系，再学一遍 💪"
            tts.speak("${termSpeech(t)}。没关系，再学一遍。")
            insertRepeatCard(t, p, repeat = true)          // 忘了 → 连击清零，当日必重现
        }
        // v5 R7 总结卡条件出现：全部任务词连击满 3 移出（无待处理卡）→ 总结卡此时才出现在队尾
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
                if (scrolling) {
                    tts.stop()
                    return@collect
                }
                delay(SETTLE_SPEECH_DELAY_MS)
                if (pagerState.isScrollInProgress) return@collect   // 200ms 内又开始滑了
                if (pagerState.currentPage != idx) return@collect   // 迟到的旧页事件（跳页/连滑已完成）：交给新事件播报，防错位闪播
                if (restoreTo != null) return@collect               // 断点跳页完成前不播，防错位闪播
                val p = pages.getOrNull(idx) ?: return@collect
                val announce = pendingAnnounce
                pendingAnnounce = null
                // v9：任务卡停稳返回空文案 → 整体沉默（显示词卡不朗读，prd v9 第 1 条）；
                // 浏览卡 / 完成卡 / 引导卡照常播报。换频道播报与本卡播报拼成**一句**：
                // 分开 speak 会互相 FLUSH 掉
                val text = cardSpeech(p)
                val full = if (announce != null) announce + text else text
                if (full.isNotBlank() && spokenKeys.add(spokenKey(p))) tts.speak(full)
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
                .background(androidx.compose.ui.graphics.Color.White)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // v6：scenes = 设置过滤排序后的可见分区（rec/fav 固定渲染在前列，不参与隐藏/排序）；
            // onOpenSettings 由推荐 tab 连点 5 次触发（检测在 ChannelBar 内部）
            ChannelBar(
                current = channel,
                graduated = graduated,
                scenes = visibleScenes,
                onOpenSettings = { showSettings = true },
                onSelect = { channel = it },
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
                        // 收藏分区零写入不破坏）。浏览中取消收藏**不重排**已出卡（pages 不动），
                        // 后续 appendPoolPages 重洗时自然不再抽到该词。
                        isFavorite = p.t.id in favorites,
                        onToggleFavorite = {
                            val added = repo.toggleFavorite(p.t.id)
                            favorites = repo.favorites().toSet()
                            // 关键操作语音反馈（适老化硬约束：按钮点击朗读含义）
                            tts.speak(if (added) "已收藏" else "已取消收藏")
                        },
                        // v8：防泄题分流删除（design.md §13.2）——点卡片一律重听「词 + 用途」
                        onSpeakTerm = { tts.speak(termSpeech(p.t)) },
                        // v7 单字直读（prd v7 第 5 条）：点单字 = 只读词（不读提示、不开弹层），
                        // 与点卡片重听「词 + 用途」区分；字卡弹层已随 v7 删除。
                        // v8：防泄题分流删除——未作答时也直读整词（拼音本来就可见，无密可泄）
                        onSpeakWord = { tts.speak(p.t.text) },
                        onAnswer = { known -> answer(p, known) },
                    )
                    Page.Done -> DoneCard(
                        known = session.known,
                        forgot = session.forgot,
                        words = taskWordCount(),
                        // v9：无「确认」按钮——到达完成卡即转浏览模式（enterBrowse，见 collector）；
                        // inBrowse 切换底部引导文案（浏览模式中显示「随便看看吧」）
                        inBrowse = browseMode,
                    )
                    Page.Guide -> GuideCard()      // v6 空收藏引导（播报在 cardSpeech 分支）
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
                // 显隐/顺序/配额都是「写 AppSettings → 重读镜像」两步：真源在持久层，镜像只管重组
                onSetVisible = { id, visible ->
                    settings.setSceneVisible(id, visible)
                    hiddenIds = settings.hiddenIds()
                    orderedScenes = settings.orderedScenes()
                },
                onMove = { id, delta ->
                    settings.moveScene(id, delta)
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
