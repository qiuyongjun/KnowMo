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

/**
 * 根界面：抖音式垂直 feed（v4 第三轮定稿：连击 + 间隔双层模型，design.md §9）。
 * 交互契约（见任务 design.md §9）：
 * 1. 滑到停稳（isScrollInProgress=false 后 ~200ms）才播报；快速连滑中间卡不闪播
 * 2. 复习/温故卡未作答只念"还记得它念什么吗"，绝不念词的读音；认识/忘了后才念词+提示
 * 3. 新词卡停稳播报 = markSeen（当日幂等，首次返回 true）+ 立即追加该词复习卡到队尾
 *    （开始当日 3 次认识连击考核）；教读不写 TermState（间隔层）
 * 4. 作答走同一双层状态机（任何频道/自由刷口径一致）：连击层「认识」+1、「忘了」清零
 *    （跨频道共享，当日有效）；满 3 = 移出当日队列 + 间隔层升一级（1→3→7→15，每日最多
 *    升一级闸门）；未满 3 的作答不写 TermState。任务卡答后未移除追加队尾（REVIEW 形态），
 *    自由刷卡不追加。展开/作答状态按页出现（seq）记录；断点恢复按队列卡实例 answered[i]
 *    逐卡恢复（不按词级判定，防同词多卡一并展开卡死连击）
 * 5. 完成卡之后继续上滑 = 自由刷无限流（温故，可作答、连击/降级照算），pageCount 随滑动增长
 * 6. 不展示"第 x/y 张"进度条，进度由完成卡和语音表达
 * 7. 分区完成（§9.5）：该区全词 days==15（间隔封顶）→ 频道栏加 🎓；「忘了」days=1 即时回退
 */
@Composable
fun AppRoot(repo: StudyRepository, tts: TTSSpeaker) {
    var channel by remember { mutableStateOf("rec") }
    var session by remember { mutableStateOf(SessionStats()) }
    val revealed = remember { mutableStateMapOf<Int, Boolean>() }      // seq -> 复习/温故卡是否已作答展开（按出现记，不按词记）
    val results = remember { mutableStateMapOf<Int, String>() }        // seq -> 反馈文案
    var seqGen by remember { mutableStateOf(0) }                       // 页出现序号发生器（只在 effect/回调中递增）
    val spokenKeys = remember { mutableSetOf<String>() }               // 自动朗读去重（频道+页序+词）
    var charFor by remember { mutableStateOf<TermChar?>(null) }
    var graduated by remember { mutableStateOf(emptySet<String>()) }   // 已完成分区（频道栏 🎓）

    // feed 页面：当日队列 + 完成卡 + 自由刷追加页（无限流）
    var pages by remember { mutableStateOf<List<Page>>(emptyList()) }
    var freePool by remember { mutableStateOf<List<Term>>(emptyList()) } // 自由刷池：顺序抽取，池空重洗
    var restoreTo by remember { mutableStateOf<Int?>(null) }             // 断点/频道切换的目标页
    var pendingAnnounce by remember { mutableStateOf<String?>(null) }    // 频道播报（与首卡合并成一句播）
    var firstChannelLoad by remember { mutableStateOf(true) }

    val pagerState = rememberPagerState(pageCount = { pages.size })

    // 欢迎语（TTS 未就绪时进 pending 队列，就绪后按序补播，始终先于卡片播报）
    LaunchedEffect(Unit) {
        tts.speak("欢迎回来。上滑开始学习。")
    }

    fun termById(id: String): Term? = STUDY_TERMS.firstOrNull { it.id == id }

    fun nextSeq(): Int = ++seqGen

    fun termSpeech(t: Term) = "${t.text}。${t.tip}"

    fun spokenKey(idx: Int, p: Page) = "$channel:$idx:" + when (p) {
        is Page.TermPage -> p.t.id
        Page.Done -> "done"
    }

    /** 停稳后播报文案（design.md §9 契约）：复习/温故卡未作答绝不念词的读音 */
    fun cardSpeech(p: Page): String = when (p) {
        is Page.TermPage -> when {
            p.mode == CardMode.NEW -> termSpeech(p.t)          // 新学：词 + 提示语，全展开
            revealed[p.seq] == true -> termSpeech(p.t)         // 已作答：词 + 提示语
            else -> "这个词，还记得它念什么吗？想一想，再按下面的按钮"
        }
        Page.Done ->
            "太棒了，今日任务完成！认识了${session.known}个，忘了${session.forgot}个。" +
                "继续上滑，随便看看，温故知新。"
    }

    // 装载当日队列（repo 内 date 不符自动重建）：首次进入 = 断点恢复；切频道 = 回第一页
    LaunchedEffect(channel) {
        val announce = !firstChannelLoad
        firstChannelLoad = false
        val q = repo.ensureQueue(channel)
        pages = q.queue.mapIndexed { i, id ->
            val t = termById(id) ?: return@mapIndexed null
            val mode = if (q.modes[i] == StudyRepository.MODE_NEW) CardMode.NEW else CardMode.REVIEW
            val seq = nextSeq()
            // 断点恢复：按该卡实例的 answered[i] 逐卡恢复展开态（design.md §9.1）。
            // 不按词级 lastSeen 判定——v4 同词多卡，词级判定会把剩余连击考核卡一并展开，重启后卡死
            if (q.answered[i]) revealed[seq] = true
            Page.TermPage(t, mode, seq, i)
        }.filterNotNull() + Page.Done
        freePool = emptyList()
        spokenKeys.clear()
        restoreTo = if (announce) 0 else q.position
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

    /** 该词未被移除（当日连击 <3）时追加到持久化队列尾，并在 feed 完成卡前插入一张复习卡（当天再见）。
     *  repo.appendQueue 追加成功返回队尾下标（作为该卡的 qIndex，markAnswered 用）；已移除返回 -1 不插入。 */
    fun appendReviewCard(t: Term) {
        val qIndex = repo.appendQueue(channel, t.id)
        if (qIndex >= 0) {
            pages = pages.toMutableList().apply {
                add(indexOfFirst { it is Page.Done }.coerceAtLeast(0), Page.TermPage(t, CardMode.REVIEW, nextSeq(), qIndex))
            }
        }
    }

    // 翻页落库：保存当日断点 + 临近完成卡（差一页）预追加自由刷页
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { idx ->
            repo.saveQueuePosition(channel, idx)
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
            // 间隔层仅满 3 时升级（1→3→7→15，受每日最多升一级闸门约束）
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
    }

    // 滑动即播（design.md §9 契约）：isScrollInProgress=true 期间不播；
    // 停稳后 delay ~200ms 再播，期间又开始滑则取消——快速连滑中间卡不闪播
    LaunchedEffect(pagerState, pages) {
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
                if (!spokenKeys.add(spokenKey(idx, p))) return@collect
                // 新词卡首次停稳播报 = 当日首次教读：markSeen 幂等，返回 true 才追加该词的
                // 复习卡到队尾（开始当日连击考核，design.md §9.2）；教读不写 TermState
                if (p is Page.TermPage && p.mode == CardMode.NEW && repo.markSeen(p.t.id)) {
                    appendReviewCard(p.t)
                }
                val announce = pendingAnnounce
                pendingAnnounce = null
                val text = cardSpeech(p)
                tts.speak(if (announce != null) announce + text else text)
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
        ) { idx ->
            when (val p = pages.getOrNull(idx) ?: return@VerticalPager) {
                is Page.TermPage -> TermCard(
                    term = p.t,
                    mode = p.mode,
                    revealed = p.mode == CardMode.NEW || revealed[p.seq] == true,
                    resultText = results[p.seq],
                    onSpeakTerm = { tts.speak(termSpeech(p.t)) },
                    onCharClick = { charFor = it },
                    onAnswer = { known -> answer(p, known) },
                )
                Page.Done -> DoneCard(
                    known = session.known,
                    forgot = session.forgot,
                )
            }
        }
    }

    charFor?.let { ch ->
        CharSheet(ch = ch, terms = STUDY_TERMS, onSpeak = { tts.speak(it) }, onDismiss = { charFor = null })
    }
}
