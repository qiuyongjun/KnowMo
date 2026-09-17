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

/** v5 防泄题：考试态（复习/温故）未作答且未 peek 时，点卡片只播这句提示语——不念词、不念提示 */
private const val EXAM_TAP_HINT_SPEECH = "再想一想，想起来了吗？"

/** v5 R7 前向拦截提示语：不能越过未处理的卡 */
private const val BLOCK_HINT_EXAM = "先回答这张卡，再往上滑。认识点 √，想不起来点 ×。"
private const val BLOCK_HINT_NEW = "先听一遍这张新词卡，听完就能往上滑。"

/**
 * 根界面：抖音式垂直 feed（v4 第三轮定稿：连击 + 间隔双层模型，design.md §9）。
 * 交互契约（见任务 design.md §9 与 v5 §5.1–§5.3）：
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
 * 6. 不展示"第 x/y 张"进度条，进度由完成卡和语音表达
 * 7. 分区完成（§9.5，v5：毕业判定 days >= 15 与间隔封顶 30 解耦）→ 频道栏加 🎓，毕业词升 30 后不回退；
 *    「忘了」days=1 即时回退
 * 8. 完成卡「真做完才出现」（v5 R7，§5.1）：`Page.Done` 仅在**待处理数 == 0** 时存在于 feed 尾；
 *    未做完时队尾就是最后一张待处理卡，feed 里没有任何含完成语义的页。文案与播报回到**单一形态**，
 *    战果用当日持久计数（§5.3，跨重启不归零）。
 *    **前向拦截**（§5.2）：向前停稳到某页时，若其前方仍存在待处理卡，则退回**最靠前的那一张**并播
 *    一句语音解释，然后 return（本次不播 cardSpeech）。`pages` 顺序**完全不动**——不回收、不重排、
 *    不写任何学习状态（不碰 revealed / peeked / results，不调 repo）；被拦的不是用户当前页，其
 *    `spokenKey` 也不入 `spokenKeys`。退回后动画会再触发一次停稳，此时该页前方已无待处理卡 → 正常播报，
 *    解释语与该卡播报**拼成一句**播出（`tts.speak` 是 QUEUE_FLUSH，两句分开播会互相掐断）。
 *    **只拦前向**：下滑回看与完成卡之后的自由刷不受限制——**不得**设 `userScrollEnabled = false`
 *    （全向开关会把回看一起锁死，且卡片静止不动会被高龄用户误读为卡死）。
 *    **不存在「跳过 / 延后」这个动作**：待处理卡的唯一出口是 √ / ×（可先「👀 想看答案」再作答）。
 */
@Composable
fun AppRoot(repo: StudyRepository, tts: TTSSpeaker) {
    var channel by remember { mutableStateOf("rec") }
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
    var blockHint by remember { mutableStateOf<String?>(null) }          // v5 R7 越界解释语（与退回目标卡的播报合并成一句，§5.2）
    var firstChannelLoad by remember { mutableStateOf(true) }

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
        val first = pages.indexOfFirst { isPending(it) }
        return if (first >= 0 && first < idx) first else -1
    }

    /** 任务区去重词数 = 完成卡战果里的 N（§5.3：N = 任务区去重词数）。自由刷页不计入。 */
    fun taskWordCount(): Int = pages.asSequence()
        .filterIsInstance<Page.TermPage>()
        .filter { it.mode != CardMode.FREE }
        .map { it.t.id }
        .distinct()
        .count()

    /** v5 R7 完成卡「条件出现」（§5.1）：`pending == 0` 且还没有完成卡 → 追加到任务区末尾；
     *  反向保底（`pending > 0` 且完成卡还在队尾、其后无自由刷页）→ 移除。
     *  单调性靠调用点保证：装载后、每次 `answer()` 后、追加考核卡后各调一次。 */
    fun syncDonePage() {
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

    // 装载当日队列（repo 内 date 不符自动重建）：进入 / 切频道都回到 frontier（= 断点）
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
        blockHint = null                     // 队列已重建，丢弃上一队列未播出的越界解释语
        syncDonePage()                       // 完成卡条件出现：pending == 0 才有收尾页
        restoreTo = frontierIndex()          // 断点 = frontier（第一张待处理卡；都做完了则是完成卡）
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
     *  repo.appendQueue 追加成功返回队尾下标（作为该卡的 qIndex，markAnswered 用）；已移除返回 -1 不插入。 */
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

    // 翻页落库：保存当日断点（= frontier）+ 临近完成卡（差一页）预追加自由刷页。
    // 完成卡条件存在（§5.1）：doneIdx < 0 时不追加——未做完时队尾是最后一张待处理卡，不是收尾页
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { idx ->
            repo.saveQueuePosition(channel, frontierIndex())
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
                // v5 R7 前向拦截（§5.2）：不能越过未处理的卡——退回最靠前的那张 + 播一句解释，
                // 列表顺序完全不动（不回收、不重排、不写任何状态、不调 repo）。
                // 不用 userScrollEnabled=false：那是全向开关，会把「下滑回看」一起锁死，且静止不动会被误读为卡死。
                // ⚠️ 提示语**不能在这里当场** `tts.speak`：`TTSSpeaker.speak` 是 QUEUE_FLUSH，而退回动画结束
                //    后会在目标页再触发一次停稳并播 cardSpeech，当场播会被那句 FLUSH 掐断（只播出一两个字；
                //    把 speak 挪到动画之后只会把掐断点从 ~500ms 提到 ~200ms，不能解决问题）。
                //    故挂到 `blockHint`，与目标卡那句播报拼成**一句** utterance——视觉先解释、语音后解释。
                //    动画自身的 `scrolling == true` 不会掐断它：collect 此刻正挂在 `animateScrollToPage` 里，
                //    而 snapshotFlow 是 conflated 的——采集中途的状态变化不逐个 emit，只在 collect 空出来时
                //    重读一次 `block()`，那时动画已结束、`isScrollInProgress` 已回到 false。
                val block = blockingIndex(idx)
                if (block >= 0) {
                    blockHint = if ((pages.getOrNull(block) as? Page.TermPage)?.mode == CardMode.NEW) BLOCK_HINT_NEW
                    else BLOCK_HINT_EXAM
                    pagerState.animateScrollToPage(block.coerceIn(0, pages.lastIndex))
                    return@collect
                }
                val p = pages.getOrNull(idx) ?: return@collect
                val hint = blockHint                // 越界解释语（若有）只消费一次
                blockHint = null
                if (!spokenKeys.add(spokenKey(p))) {
                    // 目标卡此前已播报过（如站在未作答的复习卡上往前划被退回）：只补解释，不重复念词
                    if (hint != null) tts.speak(hint)
                    return@collect
                }
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
                val text = cardSpeech(p)
                // 换频道播报 + 越界解释语都与本卡播报拼成**一句**：分开 speak 会互相 FLUSH 掉
                val prefix = (announce ?: "") + (hint ?: "")
                tts.speak(if (prefix.isEmpty()) text else prefix + text)
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
