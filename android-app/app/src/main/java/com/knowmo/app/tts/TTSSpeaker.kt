package com.knowmo.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** 中文语音可用性（2026-09-23 修复 #3）：部分国产 ROM 的默认 TTS 引擎不支持中文，
 *  setLanguage 返回 LANG_MISSING_DATA / LANG_NOT_SUPPORTED 时 ready 仍是 true——
 *  App 全程无声且无提示。状态暴露给 UI 做大字提示 + 跳转系统 TTS 设置。 */
enum class ChineseVoiceState { INIT, OK, MISSING }

/**
 * Android TTS 封装：中文、语速可调（老年人默认 0.85）。
 * 初始化是异步的：ready 之前的朗读请求按序存入 pending，就绪后按序补播
 * （不设就绪回调——回调里再 speak 是 QUEUE_FLUSH，会打断/重播已补播的内容）。
 *
 * v5 R8（design.md §5.5）：新增 [speaking] / [awaitQuiet]，供「作答后自动前进」等待本次播报念完。
 * [speak] / [stop] 的既有签名与 QUEUE_FLUSH 语义**不变**（调用点很多）。
 */
class TTSSpeaker(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null

    @Volatile
    var ready: Boolean = false
        private set

    /** 当前是否正在朗读（v5 R8）。
     *
     *  ⚠️ 置位由 [speak] **同步**完成，不能只靠 listener 的 onStart：否则「`speak()` 之后立刻
     *  `awaitQuiet()`」会读到还没被置位的 `false` 而抢跑——落点卡的播报是 QUEUE_FLUSH，会把
     *  「词 + 提示 + 反馈」整句掐掉。
     *  清除时机：onDone / onError（两个重载）/ onStop / [stop] / [shutdown]；
     *  ⚠️ onStart 也要**置位**（见 [createEngine] 里的 listener）——`speak()` 是 QUEUE_FLUSH，冲掉上一句会先给
     *  上一句发 onStop，那一瞬间 `speaking` 会被清成 false，而新这一句整段播放期间它都不再被置回 true。
     *  `!ready` 的 pending 分支不置位（那只入队，没有实际播报）。 */
    @Volatile
    var speaking: Boolean = false
        private set

    /** 中文语音是否可用（见 [ChineseVoiceState]；UI 轮询读取） */
    @Volatile
    var chineseVoice: ChineseVoiceState = ChineseVoiceState.INIT
        private set

    @Volatile
    var rate: Float = 0.85f

    /** 就绪前的朗读请求按顺序排队（欢迎语等不被后续卡片播报覆盖） */
    private val pendingQueue = ArrayDeque<String>()

    init {
        createEngine()
    }

    /** 创建引擎连接并挂回调（构造与 [recheckLanguage] 重建共用） */
    private fun createEngine() {
        tts = TextToSpeech(appContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                refreshLanguageState()
                tts?.setSpeechRate(rate)
                // 按序补播就绪前排队的请求（QUEUE_ADD 接续，不互相打断）
                pendingQueue.forEach { text ->
                    tts?.setSpeechRate(rate)
                    tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "knowmo-utt")
                }
                pendingQueue.clear()
            } else {
                // 引擎连接失败（手机没有任何 TTS 引擎等）：落 MISSING 让横幅出现。
                // 2026-09-24 修复 #2：停在 INIT 的话「缺中文引擎」横幅永远不显示，
                // 「全程无声」里最严重的一类（无预装引擎）反而全程无提示。
                chineseVoice = ChineseVoiceState.MISSING
            }
        }
        // v5 R8：朗读开始/结束 → speaking 置位/清除（awaitQuiet 靠它挂起轮询；onDone / onError / onStop 都要覆盖）
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            // ⚠️ onStart 必须**置 true**，不能留空：`speak()` 是 QUEUE_FLUSH，冲掉上一句时框架会先给**上一句**
            //   发 onStop（「flushed from the queue」也走 onStop）→ `speaking` 被清成 false，而紧接着开始的
            //   这一句若不在 onStart 置回 true，它整段播放期间 `speaking` 都是 false：`awaitQuiet()` 立刻返回，
            //   自动前进抢跑，落点卡的播报（QUEUE_FLUSH）会把「词 + 提示 + 反馈」整句掐掉——正是 R8 要防的。
            //   （flush 的 onStop 必在下一句的 onStart 之前到达：两者是同一条回调 binder 上的顺序消息。）
            override fun onStart(utteranceId: String?) {
                speaking = true
            }

            override fun onDone(utteranceId: String?) {
                speaking = false
            }

            // onError(String) 是 API 21 起被 deprecated 的**旧重载**（新重载带 errorCode）。它是平台的
            // **抽象**成员——**必须**覆盖（不覆盖编译不过），而覆盖它又会在 Kotlin 2.0 下产生
            // OVERRIDE_DEPRECATION **警告**（不是错误；app/build.gradle.kts 未开 allWarningsAsErrors），
            // 故用 @Suppress 压掉。新重载 onError(String?, Int) 与 onStop 都有默认实现，一并覆盖是为了让
            // 三条路径（onDone / onError / onStop）都能清 speaking——漏任何一条都会让 awaitQuiet() 挂到 20s 上限。
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) {
                speaking = false
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                speaking = false
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                speaking = false
            }
        })
    }

    /** setLanguage 结果落 [chineseVoice]；LANG_AVAILABLE_DATA 及以上视为可用（音可能不完美但能读） */
    private fun refreshLanguageState() {
        val result = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE) ?: return
        chineseVoice = when (result) {
            TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> ChineseVoiceState.MISSING
            else -> ChineseVoiceState.OK
        }
    }

    /** 供 ON_RESUME 重检（修复 #3）：用户按横幅提示装好中文引擎后不必重启 App。
     *  ⚠️ 系统默认引擎切换后，已有的 TextToSpeech 实例**仍连着旧引擎**，setLanguage 结果
     *  不会变——所以仍 MISSING 时销毁重建再连一次；重建期间朗读请求照旧走 pending 排队。
     *  2026-09-24 修复 #2：状态为 MISSING 时**不管 ready 是什么都重建**——引擎初始化失败
     *  （ready=false）也落 MISSING，那种手机装好引擎回来靠这里重连；旧实现 `if (!ready) return`
     *  会把它挡死在无声状态。仍处 INIT（初始化进行中）不动，避免对未完成的连接重复 init。 */
    fun recheckLanguage() {
        if (ready) {
            refreshLanguageState()
        }
        if (chineseVoice == ChineseVoiceState.MISSING) {
            tts?.shutdown()
            tts = null
            ready = false
            speaking = false
            createEngine()
        }
    }

    fun speak(text: String) {
        if (!ready) {
            if (pendingQueue.lastOrNull() != text) pendingQueue.add(text)
            if (pendingQueue.size > 4) pendingQueue.removeFirst()
            return
        }
        speaking = true                 // ⚠️ 同步置位，勿挪进 listener（原因见 speaking KDoc）
        tts?.setSpeechRate(rate)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "knowmo-utt")
    }

    /** 立即停止当前朗读（快速连滑打断播报用） */
    fun stop() {
        tts?.stop()
        speaking = false
    }

    /** 挂起到当前朗读结束（v5 R8，design.md §5.5）。
     *  20s 上限兜底：TTS 引擎异常不回调 onDone / onError 时不至于永久挂住自动前进。 */
    suspend fun awaitQuiet() {
        withTimeoutOrNull(20_000) {
            while (speaking) delay(100)
        }
    }

    fun shutdown() {
        tts?.stop()
        speaking = false
        tts?.shutdown()
        tts = null
        ready = false
    }
}
