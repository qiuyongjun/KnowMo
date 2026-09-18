package com.qyj.shibang.ui

import androidx.compose.foundation.background
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
import com.qyj.shibang.data.STUDY_TERMS
import com.qyj.shibang.data.StudyRepository
import com.qyj.shibang.data.Term
import com.qyj.shibang.data.TermChar
import com.qyj.shibang.data.sceneName
import com.qyj.shibang.tts.TTSSpeaker
import com.qyj.shibang.ui.theme.AppSurface
import kotlinx.coroutines.delay

/** feed 页：词条卡（mode 由调度器运行时计算：新学/复习/温故）或完成卡。
 *  seq = 本会话内的出现序号：v4 中同一词会出现多张卡（学 1 次 + 连击考核若干次），
 *  展开/作答状态必须按「出现」记录而非按词记录——否则第一次作答会把后续追加的
 *  考核卡一并展开。
 *  qIndex = 该卡在持久化当日队列中的下标（作答时 markAnswered 用）；自由刷页 = -1（不落库）。 */
sealed interface Page {
    data class TermPage(val t: Term, val mode: CardMode, val seq: Int, val qIndex: Int) : Page
    data object Done : Page
}

data class SessionStats(val known: Int = 0, val forgot: Int = 0)

/** 每次预追加的自由刷页数（临近完成卡时预追加，池空重洗） */
private const val FREE_BATCH = 2

/** 停稳判定后的播报去抖（ms）：滑动进行中不播，停稳后再等这么久，快速连滑不闪播 */
private const val SETTLE_SPEECH_DELAY_MS = 200L

/** v5 R8 作答后自动前进的最短停留（ms）：答案与反馈至少在屏幕上停这么久再翻页，
 *  TTS 不可用时也不至于瞬间翻走（§5.5 两段式等待的第一段） */
private const val AUTO_ADVANCE_MIN_MS = 1500L

/** v5 防泄题：考试态（复习/温故）未作答且未 peek 时，点卡片只播这句提示语——不念词、不念提示 */
private const val EXAM_TAP_HINT_SPEECH = "再想一想，想起来了吗？"

/** v5 R9 分区队尾轻提示（视觉）：分区（非每日任务频道）滑到最后一张时挂在卡底部。
 *  它是**轻提示**不是完成卡——分区没有「完成」语义，禁止 🎉 /「今日任务完成」类文案与播报。 */
private const val SCENE_TAIL_HINT = "这个区就这些了 · 点上面的频道可以换区"

/** v5 R9 分区队尾轻提示（播报）：与 SCENE_TAIL_HINT 在**同一条件**下拼在 `cardSpeech` **之后**，
 *  且必须拼成**同一句** `speak`——`TTSSpeaker.speak` 是 QUEUE_FLUSH，分两次 speak 会互相掐断
 *  （与 R7 解释语、R8 作答播报同一根因）。 */
private const val SCENE_TAIL_SPEECH = "这个区就这些了。点上边的频道可以换个区。"

/**
 * 根界面：抖音式垂直 feed（v4 第三轮定稿：连击 + 间隔双层模型，design.md §9）。
 * 交互契约（见任务 design.md §9 与 v5 §5.1–§5.5）：
 * 1. 滑到停稳（isScrollInProgress=false 后 ~200ms）才播报；快速连滑中间卡不闪播
 * 2. 复习/温故卡未作答只念"还记得它念什么吗"，绝不念词的读音；认识/忘了后才念词+提示
 *    （v5 防泄题：考试态未作答**点卡片**也只播「再想一想，想起来了吗？」，分流在 TermCard 调用点
 *    ——按形态传对应的 onSpeakTerm；「想看答案」peek 只展开拼音/提示 + 念一遍，不写任何学习状态，
 *    作答按钮保留，看完仍走正常状态机）
 * 3. 新词卡停稳播报 = markSeen（当日幂等，首次返回 true）+ 立即追加该词复习卡到队尾
 *    （开始当日 3 次认识连击考核）；教读不写 TermState（间隔层）
 * 4. 作答走同一双层状态机（任何频道/自由刷口径一致）：连击层「认识」+1、「忘了」清零
 *    （跨频道共享，当日有效）；满 3 = 移出当日队列 + 间隔层升一级（1→3→7→15→30，每日最多
 *    升一级闸门）；未满 3 的作答不写 TermState。任务卡答后未移除追加队尾（REVIEW 形态），
 *    自由刷卡不追加。展开/作答状态按页出现（seq）记录；断点恢复按队列卡实例 answered[i]
 *    逐卡恢复（不按词级判定，防同词多卡一并展开卡死连击）
 * 5. 完成卡之后继续上滑 = 自由刷无限流（温故，可作答、连击/降级照算），pageCount 随滑动增长
 *    ——**仅每日任务频道**（分区没有完成卡、也不追加自由刷页，第 10 条）
 * 6. 不展示"第 x/y 张"进度条，进度由完成卡和语音表达
 * 7. 分区完成（§9.5，v5：毕业判定 days >= 15 与间隔封顶 30 解耦）→ 频道栏加 🎓，毕业词升 30 后不回退；
 *    「忘了」days=1 即时回退
 * 8. 完成卡「真做完才出现」（v5 R7，§5.1，**仅每日任务频道**——R9 收窄）：`Page.Done` 仅在**待处理数 == 0** 时存在于 feed 尾；
 *    未做完时队尾就是最后一张待处理卡，feed 里没有任何含完成语义的页。文案与播报回到**单一形态**，
 *    战果用当日持久计数（§5.3，跨重启不归零）。
 *    **前向拦截**（§5.2，**仅每日任务频道**——R9 收窄）：向前停稳到某页时，若其前方仍存在待处理卡，则**静默**退回**最靠前的那一张**
 *    （不播任何提示语——滑不动本身就是结构性的反馈，逐次拦截都解释一句对高龄用户是噪声），
 *    然后 return（本次不播被拦页的 cardSpeech）。`pages` 顺序**完全不动**——不回收、不重排、
 *    不写任何学习状态（不碰 revealed / peeked / results，不调 repo）；被拦页的 `spokenKey` 不入
 *    `spokenKeys`。退回后动画会再触发一次停稳，此时该页前方已无待处理卡 → 正常播报。
 *    **只拦前向**：下滑回看（含翻回以前任意一张已处理的卡）与完成卡之后的自由刷不受限制——**不得**
 *    设 `userScrollEnabled = false`（全向开关会把回看一起锁死，且卡片静止不动会被高龄用户误读为卡死）。
 *    **不存在「跳过 / 延后」这个动作**：待处理卡的唯一出口是 √ / ×（可先「👀 想看答案」再作答）。
 * 9. 作答后自动前进（v5 R8，§5.5）：复习/温故卡完成作答（√ / ×）后，等本次播报「词 + 提示 + 反馈」
 *    整句念完（最短停留 AUTO_ADVANCE_MIN_MS + `tts.awaitQuiet()`）再 `animateScrollToPage(cur + 1)`
 *    ——抢跑会被落点卡的播报 FLUSH 掉答案与反馈（`tts.speak` 是 QUEUE_FLUSH）。新词卡没有「作答完成」
 *    这个事件，**不**自动前进（`SwipeHint` 保留，节奏由用户自己掌握）；播报期间用户自己滑动
 *    （前进或回看）→ 放弃本次自动前进，不抢方向。
 * 10. 分区 = 专题自主练习（v5 R9，§5.6）：**只有每日任务频道（`CHANNEL_DAILY` = "rec"）**有完成卡与前向
 *    拦截（`syncDonePage` / `blockingIndex` 对分区直接放行）——分区的语义是「随时进、随时走、想练多久练多久」，
 *    「完成」与「真做完」在分区里都不存在。分区因此：**自由划**（快甩过任意多张未处理的卡都不退回）、
 *    **不做到期筛选**（队列 = 该区全部词，见 `StudyRepository.buildQueue`，故不会白屏）、**滑完停住**
 *    （`appendFreePages` 以 `doneIdx >= 0` 为前提，分区 `doneIdx` 恒 -1，无需改代码即自然停住）+ 队尾
 *    **轻提示**（`SCENE_TAIL_HINT` 显示在最后一张卡底部，并把 `SCENE_TAIL_SPEECH` 并入该卡**同一次**播报）。
 *    分区作答仍走同一套双层状态机、同样计入当日战果；断点 = **上次滑到的那一页**（不是 frontier——
 *    自由划之后「在哪儿」≠「做到哪儿了」）。
 */
@Composable
fun AppRoot(repo: StudyRepository, tts: TTSSpeaker) {
    var channel by remember { mutableStateOf(StudyRepository.CHANNEL_DAILY) }
    var session by remember { mutableStateOf(SessionStats()) }
    val revealed = remember { mutableStateMapOf<Int, Boolean>() }      // seq -> 复习/温故卡是否已作答展开（按出现记，不按词记）
    val peeked = remember { mutableStateMapOf<Int, Boolean>() }        // seq -> 考试卡「想看答案」求助展开（v5：会话态不持久化，不写任何学习状态）
    val taught = remember { mutableStateMapOf<Int, Boolean>() }        // seq -> 新词卡当日是否已教读（v5 R7 待处理口径；装载时按 repo.wasSeenToday 恢复）
    val results = remember { mutableStateMapOf<Int, String>() }        // seq -> 反馈文案
    var seqGen by remember { mutableStateOf(0) }                       // 页出现序号发生器（只在 effect/回调中递增）
    val spokenKeys = remember { mutableSetOf<String>() }               // 自动朗读去重（频道 + 页身份）
    var charFor by remember { mutableStateOf<TermChar?>(null) }
    var graduated by remember { mutableStateOf(emptySet<String>()) }   // 已完成分区（频道栏 🎓）

    // feed 页面：当日队列 + 完成卡（条件存在）+ 自由刷追加页（无限流）
    var pages by remember { mutableStateOf<List<Page>>(emptyList()) }
    var freePool by remember { mutableStateOf<List<Term>>(emptyList()) } // 自由刷池：顺序抽取，池空重洗
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

    fun termById(id: String): Term? = STUDY_TERMS.firstOrNull { it.id == id }

    fun nextSeq(): Int = ++seqGen

    fun termSpeech(t: Term) = "${t.text}。${t.tip}"

    /** v5 R7 待处理（pending）口径（design.md §5.1）：新词卡**当日未被教读** 或 复习卡**未作答**；
     *  温故/自由刷（FREE）卡恒不计入（自由刷是完成之后的消遣，不属于当日任务）。 */
    fun isPending(p: Page): Boolean = p is Page.TermPage && when (p.mode) {
        CardMode.NEW -> taught[p.seq] != true
        CardMode.REVIEW -> revealed[p.seq] != true
        CardMode.FREE -> false
    }

    /** 待处理任务卡数：完成卡「条件存在」的判据。
     *
     *  ⚠️ 必须写成局部函数而非 `val`：这里读的是 `pages` / `taught` / `revealed` 的 State 委托 getter，
     *  每次调用都取当前值。若写成 `val`，key 不含 `pages` 的 effect 闭包会捕获旧值——
     *  页面集合未变而仅发生作答时该 effect 不重启，滑到完成卡会算出过期张数。 */
    fun pendingTaskCount(): Int = pages.count { isPending(it) }

    /** frontier = 第一张待处理卡的下标（无待处理卡时取完成卡下标；都没有则 0）。
     *  不变量（§5.2）：列表**只追加不重排**且不允许越过未处理的卡，所以「在哪儿」就等于「做到哪儿了」
     *  ——断点（restoreTo / saveQueuePosition）直接取它即可，不再需要回收锚点。 */
    fun frontierIndex(): Int {
        val firstPending = pages.indexOfFirst { isPending(it) }
        if (firstPending >= 0) return firstPending
        val doneIdx = pages.indexOfFirst { it is Page.Done }
        return if (doneIdx >= 0) doneIdx else 0
    }

    /** v5 R7 前向拦截（§5.2）：返回「该被拦回的页下标」——若当前位置 idx 之前仍存在待处理卡，
     *  取最靠前的那一张；否则返回 -1（不拦）。写成局部函数的原因同 pendingTaskCount()：每次读 pages 的委托 getter。
     *  与 `isPending` 共用同一口径，故「完成卡何时出现」与「何时拦人」永不漂移；
     *  `isPending(FREE)` 恒 false，所以自由刷区永不拦。 */
    fun blockingIndex(idx: Int): Int {
        // v5 R9（§5.6）：前向拦截**只在每日任务频道生效**。这条规则的全部意义是「让完成卡 = 真做完」
        // （§5.1 的 pending == 0）；分区没有完成卡 → 没有「真做完」这个概念 → 分区**自由划**。
        // 分区若也拦，就是「全量入队 + 不可跳过 + 无收尾页」，是本版最糟的组合。
        if (channel != StudyRepository.CHANNEL_DAILY) return -1
        val first = pages.indexOfFirst { isPending(it) }
        return if (first >= 0 && first < idx) first else -1
    }

    /** v5 R9（§5.6）分区队尾轻提示的判定式：**仅分区**（非每日任务频道）**且当前卡是该区最后一张**。
     *  视觉（`footerHint`）与播报（`SCENE_TAIL_SPEECH`）共用这一个函数，条件不会漂移。
     *  ⚠️「最后一张」是会变的：分区作答未移除时 `appendReviewCard` 仍会追加考核卡（无完成卡时
     *  `insertAt` 天然落到 `pages.size`），所以轻提示只在**该区所有词都练到当日连击 3** 之后才出现
     *  ——这正是它想表达的「这个区就这些了」。 */
    fun isSceneTail(idx: Int): Boolean =
        channel != StudyRepository.CHANNEL_DAILY && idx == pages.lastIndex

    /** 任务区去重词数 = 完成卡战果里的 N（§5.3：N = 任务区去重词数）。自由刷页不计入。 */
    fun taskWordCount(): Int = pages.asSequence()
        .filterIsInstance<Page.TermPage>()
        .filter { it.mode != CardMode.FREE }
        .map { it.t.id }
        .distinct()
        .count()

    /** v5 R7 完成卡「条件出现」（§5.1）：`pending == 0` 且还没有完成卡 → 追加到任务区末尾；
     *  反向保底（`pending > 0` 且完成卡还在队尾、其后无自由刷页）→ 移除。
     *  单调性靠调用点保证：装载后、每次 `answer()` 后、追加考核卡后各调一次。
     *  v5 R9（§5.6）：**只有每日任务频道有完成卡**——分区 feed 里没有任何收尾页，也不播任何含完成
     *  语义的语音（分区「练不完」不会产生任何误导）。 */
    fun syncDonePage() {
        if (channel != StudyRepository.CHANNEL_DAILY) return
        val pending = pendingTaskCount()
        val doneIdx = pages.indexOfFirst { it is Page.Done }
        when {
            pending == 0 && doneIdx < 0 -> pages = pages + Page.Done
            pending > 0 && doneIdx >= 0 && doneIdx == pages.lastIndex ->
                pages = pages.filterNot { it is Page.Done }
        }
    }

    /** 自动朗读去重键：按**页身份**（seq）而非下标——追加（考核卡 / 完成卡 / 自由刷页）会改变下标，
     *  绑 seq 让「页身份」与「列表位置」解耦。完成卡回到单一形态（§5.1），故用 `channel:done` 即可
     *  （完成卡一旦出现就不会再消失）。 */
    fun spokenKey(p: Page) = "$channel:" + when (p) {
        is Page.TermPage -> "seq:${p.seq}"
        Page.Done -> "done"
    }

    /** 停稳后播报文案（design.md §9 契约）：复习/温故卡未作答绝不念词的读音。
     *  v5 R7：完成卡回到**单一形态**（条件出现后不存在「还没答完」的完成卡，故不再分流）。 */
    fun cardSpeech(p: Page): String = when (p) {
        is Page.TermPage -> when {
            p.mode == CardMode.NEW -> termSpeech(p.t)          // 新学：词 + 提示语，全展开
            revealed[p.seq] == true -> termSpeech(p.t)         // 已作答：词 + 提示语
            else -> "这个词，还记得它念什么吗？想一想，再按下面的按钮"
        }
        Page.Done ->
            "太棒了，今日任务完成！今天学完了${taskWordCount()}个词，" +
                "认识了${session.known}次，忘了${session.forgot}次。" +
                "继续上滑，随便看看，温故知新。"
    }

    // 装载当日队列（repo 内 date 不符自动重建）：进入 / 切频道都回到断点（每日任务频道 = frontier，
    // 分区 = 上次滑到的那一页，见下面 restoreTo 处的口径说明）
    LaunchedEffect(channel) {
        val announce = !firstChannelLoad
        firstChannelLoad = false
        val q = repo.ensureQueue(channel)
        val taskPages = q.queue.mapIndexedNotNull { i, id ->
            val t = termById(id) ?: return@mapIndexedNotNull null
            val mode = if (q.modes[i] == StudyRepository.MODE_NEW) CardMode.NEW else CardMode.REVIEW
            val seq = nextSeq()
            // 断点恢复：按该卡实例的 answered[i] 逐卡恢复展开态（design.md §9.1）。
            // 不按词级 lastSeen 判定——v4 同词多卡，词级判定会把剩余连击考核卡一并展开，重启后卡死
            if (q.answered[i]) revealed[seq] = true
            // v5 R7：新词卡当日已教读过（上一会话 markSeen）→ 不算待处理。教读态不持久化，
            // 按当日 seen 集合恢复（wasSeenToday），否则重启后同一张新词卡会被再算一次待处理
            if (mode == CardMode.NEW && repo.wasSeenToday(id)) taught[seq] = true
            Page.TermPage(t, mode, seq, i)
        }
        // v5 R7：列表只追加不重排，`taskPages` 的顺序就是队列顺序——不再需要上一版的「稳定分区」
        // （前向拦截保证了待处理卡不会被留在身后，且用户总会被放到第一张待处理卡上）
        pages = taskPages
        freePool = emptyList()
        spokenKeys.clear()
        advanceAfterSpeechSeq = -1           // 队列已重建，丢弃上一队列未完成的自动前进
        syncDonePage()                       // 完成卡条件出现：pending == 0 才有收尾页（分区直接 return）
        // 断点（§5.2 / §5.6）：
        // - 每日任务频道 = frontier（第一张待处理卡；都做完了则是完成卡）——本模型里「在哪儿」=「做到哪儿了」；
        // - 分区 = **上次滑到的那一页**（R9：自由划之后「在哪儿」≠「做到哪儿了」，用 frontier 会把用户
        //   拽回第一张未处理的卡）。⚠️ `position` 落库时被 `saveQueuePosition` clamp 到 `queue.size`，
        //   比 `pages.lastIndex` 大 1，这里必须**再夹一次**；分区 `pages` 非空（buildQueue 全量入队，
        //   不存在白屏），`maxOf(0, ...)` 只是空表兜底
        restoreTo = if (channel == StudyRepository.CHANNEL_DAILY) {
            frontierIndex()
        } else {
            q.position.coerceIn(0, maxOf(0, pages.size - 1))
        }
        session = SessionStats(repo.todayKnown(), repo.todayForgot())   // 战果当日持久口径（§5.3）
        if (announce) pendingAnnounce = "换到${sceneName(channel)}频道。"
    }

    // 分区完成徽章：进入 / 切频道 / 每次作答（session 变化）后刷新。
    // 注：完成判定只依赖间隔层 days（design.md §9.5），徽章即时更新；「忘了」days=1 → 即时退出完成状态。
    LaunchedEffect(channel, session) { graduated = repo.graduatedScenes() }

    // 断点/频道切换跳页（restoreTo 置空防重复；需先于停稳监听声明，保证跳页先行）
    LaunchedEffect(pages, restoreTo) {
        val target = restoreTo ?: return@LaunchedEffect
        restoreTo = null
        if (pages.isNotEmpty()) pagerState.scrollToPage(target.coerceIn(0, pages.lastIndex))
    }

    /** 自由刷：从池里顺序取 count 张追加到尾部；池空重洗（无限流，随时可停）。qIndex = -1：不落库 */
    fun appendFreePages(count: Int) {
        val extra = ArrayList<Page>(count)
        repeat(count) {
            if (freePool.isEmpty()) {
                freePool = repo.freePoolIds(channel).mapNotNull(::termById)
            }
            val next = freePool.firstOrNull() ?: return@repeat
            freePool = freePool.drop(1)
            extra.add(Page.TermPage(next, CardMode.FREE, nextSeq(), -1))
        }
        if (extra.isNotEmpty()) pages = pages + extra
    }

    /** 该词未被移除（当日连击 <3）时追加到持久化队列尾，并在 feed「任务区末尾」插入一张复习卡（当天再见）。
     *  ⚠️ 插入点 = 完成卡下标；完成卡在 v5 是**条件存在**（未做完时根本不在 `pages` 里），
     *  此时 `indexOfFirst` 返回 -1，必须落到 `pages.size`（任务区末尾）——若沿用旧写法
     *  `coerceAtLeast(0)`，-1 会变成 0 把新卡插到**队首**（用户身后），前向可达性直接破掉。
     *  repo.appendQueue 追加成功返回队尾下标（作为该卡的 qIndex，markAnswered 用）；已移除返回 -1 不插入。
     *  v5 R9：分区（无完成卡）永远走 `pages.size` 这条路——新追加的考核卡总是成为新的队尾，
     *  队尾轻提示（`isSceneTail`）随之移到新队尾，于是它只在「该区所有词都练到当日连击 3」后出现。 */
    fun appendReviewCard(t: Term) {
        val qIndex = repo.appendQueue(channel, t.id)
        if (qIndex >= 0) {
            val doneIdx = pages.indexOfFirst { it is Page.Done }
            val insertAt = if (doneIdx >= 0) doneIdx else pages.size
            pages = pages.toMutableList().apply {
                add(insertAt, Page.TermPage(t, CardMode.REVIEW, nextSeq(), qIndex))
            }
        }
    }

    // 翻页落库：保存当日断点 + 临近完成卡（差一页）预追加自由刷页。
    // 断点口径（§5.2 / §5.6）：每日任务频道写 frontier；分区写**当前页**（自由划，「在哪儿」就是断点
    // ——分区 `pages` 与队列一一对应：无完成卡、无自由刷页，页码即队列下标）。
    // 完成卡条件存在（§5.1）：doneIdx < 0 时不追加——未做完时队尾是最后一张待处理卡，不是收尾页；
    // 分区 `doneIdx` 恒为 -1（`syncDonePage` 对分区直接 return），故分区**滑完自然停住、不再追加页**。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { idx ->
            repo.saveQueuePosition(
                channel,
                if (channel == StudyRepository.CHANNEL_DAILY) frontierIndex() else idx,
            )
            val doneIdx = pages.indexOfFirst { it is Page.Done }
            if (doneIdx >= 0 && idx >= doneIdx - 1) appendFreePages(FREE_BATCH)
        }
    }

    /** 作答（认识/忘了）：所有卡型共用同一双层状态机（design.md §9.2，任何频道/自由刷口径一致）。
     *  展开/文案按 seq（本次出现）记录：同一词的多张考核卡互不影响，各自可再作答；
     *  作答同时按卡实例落库 answered（断点恢复用，design.md §9.1）。 */
    fun answer(p: Page.TermPage, known: Boolean) {
        val t = p.t
        revealed[p.seq] = true
        if (p.qIndex >= 0) repo.markAnswered(channel, p.qIndex)   // 自由刷页 qIndex=-1 不落库
        if (known) {
            // 双层状态机（design.md §9.2）：连击层 +1（满 3 = 移除当日队列，不追加）；
            // 间隔层仅满 3 时升级（1→3→7→15→30，受每日最多升一级闸门约束）
            val (count, upgraded, daysAfter) = repo.markKnown(t.id)
            session = session.copy(known = session.known + 1)
            val (msg, spoken) = when (count) {
                1 -> "👍 记得牢！再认对 2 次就学会" to "记得牢！再认对2次就学会。"
                2 -> "👍 再认对 1 次" to "再认对1次。"
                else -> "👍 这个词学会啦！" to "这个词学会啦！"
            }
            // 升级发生时追加播报天数；闸门挡住（同日已升级/已忘了）不播（design.md §9.2）
            val full = if (upgraded) "$spoken${daysAfter}天后再来复习。" else spoken
            results[p.seq] = msg
            tts.speak("${termSpeech(t)}。$full")
        } else {
            repo.markForgot(t.id)
            session = session.copy(forgot = session.forgot + 1)
            results[p.seq] = "没关系，再学一遍 💪"
            tts.speak("${termSpeech(t)}。没关系，再学一遍。")
        }
        // 作答后未移除（当日连击 <3）→ 追加队尾（REVIEW 形态，当天再见；repo 内按最新计数判定）。
        // 自由刷（Done 之后）不追加：插入点在完成卡之前 = 已滑过的位置（还会引发 pager 错位），
        // 当天再见由自由刷池循环自然覆盖，次日回池由 days=1 + buildQueue 保证（design.md §9.4）
        if (p.mode != CardMode.FREE) appendReviewCard(t)
        // v5 R7 完成卡条件出现：答完最后一张待处理卡 → 完成卡此时才出现在队尾
        syncDonePage()
        // v5 R8 作答后自动前进（§5.5）：置触发键，由下面 keyed 该 seq 的 effect 等播报念完再翻页
        //（必须放在 `tts.speak(...)` 之后）。只有 √ / × 会走到这里 —— 新词卡没有「作答完成」这个事件，
        // 天然不自动前进（它也不显示作答按钮）。
        advanceAfterSpeechSeq = p.seq
    }

    // 滑动即播（design.md §9 契约）+ v5 R7 前向拦截（§5.2）。
    // ⚠️ key 只有 pagerState（**去掉 pages**）：教读追加考核卡、完成卡条件出现都在本 effect 体内改
    //    `pages`，若 `pages` 还是 key，effect 会执行到一半被自己重启掉（改了但没播报）。
    //    `pages` 在 collect 体内读 State 委托 getter 即最新值。
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
                // v5 R7 前向拦截（§5.2）：不能越过未处理的卡——退回最靠前的那张，**静默**（不播任何提示语）。
                // 规则是**结构性**的：滑不动就是滑不动，逐次拦截都播一句解释对高龄用户是噪声；而且回看本来就自由，
                // 用户翻回以前的卡、之后想往前走仍然要回来处理它。列表顺序完全不动（不回收、不重排、不写任何状态、不调 repo）。
                // 不用 userScrollEnabled=false：那是全向开关，会把「下滑回看」一起锁死，且静止不动会被误读为卡死。
                // ⚠️ 拦截分支必须在 `spokenKeys.add` **之前** return：被拦页这一次并没有播报，它的 spokenKey
                //    不该入集合——否则退回动画结束、本页重新停稳时会因「已播报过」被去重，用户永远听不到它。
                val block = blockingIndex(idx)
                if (block >= 0) {
                    pagerState.animateScrollToPage(block.coerceIn(0, pages.lastIndex))
                    return@collect
                }
                val p = pages.getOrNull(idx) ?: return@collect
                if (!spokenKeys.add(spokenKey(p))) return@collect
                // 新词卡首次停稳播报 = 当日首次教读：markSeen 幂等，返回 true 才追加该词的
                // 复习卡到队尾（开始当日连击考核，design.md §9.2）；教读不写 TermState。
                // 无论 markSeen 返回什么都标记本页已教读——v5 R7 待处理口径看的是「是否教读过」
                if (p is Page.TermPage && p.mode == CardMode.NEW) {
                    val firstTeachToday = repo.markSeen(p.t.id)
                    taught[p.seq] = true
                    if (firstTeachToday) appendReviewCard(p.t)
                    syncDonePage()                                  // 追加考核卡后重算完成卡条件存在
                }
                val announce = pendingAnnounce
                pendingAnnounce = null
                // v5 R9 分区队尾轻提示（§5.6）：与 footerHint **同一条件**（isSceneTail），拼在 cardSpeech
                // **之后**。⚠️ 必须拼成**同一句** speak——`tts.speak` 是 QUEUE_FLUSH，分两次 speak 会互相
                // 掐断（与 R7 解释语、R8 作答播报同一根因）。
                val text = cardSpeech(p) + if (isSceneTail(idx)) SCENE_TAIL_SPEECH else ""
                // 换频道播报与本卡播报拼成**一句**：分开 speak 会互相 FLUSH 掉
                tts.speak(if (announce != null) announce + text else text)
            }
    }

    // v5 R8 作答后自动前进（§5.5）：必须等这句播报念完再翻，否则落点卡的播报会把答案与反馈掐掉。
    // key 只用作答卡的 seq：`answer()` 置位触发，effect 收尾置回 -1；新词卡不经过 `answer()`，故不触发。
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
            // 落点 = 下一张：未移除的考核卡追加在队尾，不影响下标关系；若当前卡是最后一张待处理卡，
            // `syncDonePage()` 已在 `answer()` 里同步插入完成卡，落点恰好是完成卡（当日会话自然收尾）
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

    Column(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.White)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ChannelBar(current = channel, graduated = graduated, onSelect = { channel = it })

        VerticalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(AppSurface),
            // v5 R7（§5.2）：列表只追加不重排，index 其实已足够；保留 key 是为了让「页身份」与
            // 「列表位置」解耦——TermPage -> 页身份 seq、Done -> "done"，将来若再引入重排不必重踩坑。
            // 取不到页（列表刚被重建）时给一个不会撞的兜底 key
            key = { idx ->
                when (val pg = pages.getOrNull(idx)) {
                    is Page.TermPage -> "seq-${pg.seq}"
                    Page.Done -> "done"
                    null -> "ghost-$idx"
                }
            },
        ) { idx ->
            when (val p = pages.getOrNull(idx) ?: return@VerticalPager) {
                is Page.TermPage -> TermCard(
                    term = p.t,
                    mode = p.mode,
                    revealed = p.mode == CardMode.NEW || revealed[p.seq] == true,
                    peeked = peeked[p.seq] == true,
                    resultText = results[p.seq],
                    // v5 防泄题分流（在本调用点按形态选回调，TermCard 不感知）：
                    // 考试态未作答且未 peek → 点卡片只播提示语；其余（新学/已作答/peek 后）重听词+提示
                    onSpeakTerm = {
                        val examUnanswered = p.mode != CardMode.NEW && revealed[p.seq] != true
                        if (examUnanswered && peeked[p.seq] != true) tts.speak(EXAM_TAP_HINT_SPEECH)
                        else tts.speak(termSpeech(p.t))
                    },
                    // v5 求助通道：只置会话态 + 念出词和提示（用户主动求助，给足信息）；
                    // 不写 repo 任何状态、不 markAnswered、不追加队列——作答仍走正常状态机
                    onPeek = {
                        peeked[p.seq] = true
                        tts.speak(termSpeech(p.t))
                    },
                    onCharClick = { charFor = it },
                    onAnswer = { known -> answer(p, known) },
                    // v5 R9 分区队尾轻提示（§5.6）：仅分区（非每日任务频道）的最后一张卡，且**不新增页**
                    // ——新增页会和完成卡长得像，正是 R9 要消除的混淆。播报侧用同一个 isSceneTail，
                    // 见上面停稳 effect（同一句 speak）
                    footerHint = if (isSceneTail(idx)) SCENE_TAIL_HINT else null,
                )
                Page.Done -> DoneCard(
                    known = session.known,
                    forgot = session.forgot,
                    words = taskWordCount(),
                )
            }
        }
    }

    charFor?.let { ch ->
        CharSheet(ch = ch, terms = STUDY_TERMS, onSpeak = { tts.speak(it) }, onDismiss = { charFor = null })
    }
}
