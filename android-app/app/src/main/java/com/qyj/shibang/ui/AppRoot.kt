package com.qyj.shibang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.qyj.shibang.data.REC_QUEUE
import com.qyj.shibang.data.STUDY_TERMS
import com.qyj.shibang.data.StudyRepository
import com.qyj.shibang.data.Term
import com.qyj.shibang.data.TermChar
import com.qyj.shibang.data.sceneName
import com.qyj.shibang.tts.TTSSpeaker
import com.qyj.shibang.ui.theme.AppSurface
import kotlinx.coroutines.launch

/** feed 页：词条卡或完成卡 */
sealed interface Page {
    data class TermPage(val t: Term) : Page
    data object Done : Page
}

data class SessionStats(val known: Int = 0, val forgot: Int = 0)

/**
 * 根界面：抖音式垂直 feed。
 * 交互契约（见任务 design.md §6）：
 * 1. 卡片进入视口自动朗读（每卡一次）
 * 2. 点卡片重听，点单字开字卡弹层
 * 3. 复习卡先考回忆，认识/忘了后展开并即时反馈间隔
 * 4. 频道切换重建 feed 并播报
 */
@Composable
fun AppRoot(repo: StudyRepository, tts: TTSSpeaker) {
    // 字体/语速仍读取用户偏好，只是不再提供设置入口
    var fontScale by remember { mutableFloatStateOf(repo.fontScale) }
    var speechRate by remember { mutableFloatStateOf(repo.speechRate) }
    var channel by remember { mutableStateOf("rec") }
    var terms by remember { mutableStateOf(STUDY_TERMS) }
    var session by remember { mutableStateOf(SessionStats()) }
    val revealed = remember { mutableStateMapOf<String, Boolean>() }   // 复习卡是否已展开
    val results = remember { mutableStateMapOf<String, String>() }     // termId -> 反馈文案
    val spokenKeys = remember { mutableSetOf<String>() }               // 自动朗读去重
    var charFor by remember { mutableStateOf<TermChar?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(speechRate) { tts.rate = speechRate }

    // 欢迎语（TTS 就绪后播报）
    LaunchedEffect(Unit) {
        val welcome = "欢迎回来。上滑开始学习，今天有新词，也有要复习的词。"
        if (tts.ready) tts.speak(welcome) else tts.onReady = { tts.speak(welcome) }
    }

    // feed 队列：推荐频道 = 复习+新学交错；场景频道 = 该场景词条；末尾固定完成卡
    val pages: List<Page> = remember(channel, terms) {
        val queue = if (channel == "rec") {
            REC_QUEUE.mapNotNull { id -> terms.firstOrNull { it.id == id } }
        } else {
            terms.filter { it.scene == channel }
        }
        queue.map { Page.TermPage(it) } + Page.Done
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })

    // 频道切换：回第一页 + 播报（首次组合进入不播）
    var firstChannelRun by remember { mutableStateOf(true) }
    LaunchedEffect(channel) {
        if (firstChannelRun) {
            firstChannelRun = false
            return@LaunchedEffect
        }
        spokenKeys.clear()
        pagerState.scrollToPage(0)
        tts.speak("换到${sceneName(channel)}频道")
    }

    // 当前卡片进入视口 → 自动朗读（同卡只播一次）
    LaunchedEffect(pagerState.currentPage, pages) {
        val p = pages.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        val key = when (p) {
            is Page.TermPage -> "$channel:${p.t.id}"
            Page.Done -> "$channel:done"
        }
        if (spokenKeys.add(key)) {
            when (p) {
                is Page.TermPage -> {
                    if (p.t.kind == Term.Kind.REVIEW) {
                        tts.speak("${p.t.text}。还记得它念什么吗？想一想，再按下面的按钮")
                    } else {
                        tts.speak("${p.t.text}。${p.t.tip}")
                    }
                }
                Page.Done -> tts.speak(
                    "太棒了，今天的内容学完了！认识了${session.known}个，忘了${session.forgot}个。明天记得再来。",
                )
            }
        }
    }

    fun termSpeech(t: Term) = "${t.text}。${t.tip}"

    fun answer(t: Term, known: Boolean) {
        val newDays: Int
        if (known) {
            session = session.copy(known = session.known + 1)
            newDays = StudyRepository.nextInterval(t.days)
        } else {
            session = session.copy(forgot = session.forgot + 1)
            repo.addForgot(t.id)
            newDays = 1
        }
        terms = terms.map { if (it.id == t.id) it.copy(days = newDays) else it }
        revealed[t.id] = true
        results[t.id] =
            if (known) "👍 记得牢！$newDays 天后再见"
            else "没关系，明天再来一遍 💪 已加入生词本"
        tts.speak(termSpeech(t))
    }

    fun replay() {
        session = SessionStats()
        revealed.clear()
        results.clear()
        spokenKeys.clear()
        scope.launch { pagerState.scrollToPage(0) }
        tts.speak("好，我们从头再看一遍")
    }

    // 「更大字体」档：乘在系统 fontScale 上，全局即时生效
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(base.density, base.fontScale * fontScale),
    ) {
        Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White)) {
            ChannelBar(current = channel, onSelect = { channel = it })

            VerticalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(AppSurface),
            ) { idx ->
                when (val p = pages[idx]) {
                    is Page.TermPage -> TermCard(
                        term = p.t,
                        index = idx + 1,
                        total = pages.size,
                        revealed = p.t.kind == Term.Kind.NEW || revealed[p.t.id] == true,
                        resultText = results[p.t.id],
                        onSpeakTerm = { tts.speak(termSpeech(p.t)) },
                        onCharClick = { charFor = it },
                        onAnswer = { known -> answer(p.t, known) },
                    )
                    Page.Done -> DoneCard(
                        index = idx + 1,
                        total = pages.size,
                        known = session.known,
                        forgot = session.forgot,
                        onReplay = { replay() },
                    )
                }
            }
        }

        charFor?.let { ch ->
            CharSheet(ch = ch, terms = terms, onSpeak = { tts.speak(it) }, onDismiss = { charFor = null })
        }
    }
}
