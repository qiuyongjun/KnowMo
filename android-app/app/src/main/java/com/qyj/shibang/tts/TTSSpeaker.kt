package com.qyj.shibang.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Android TTS 封装：中文、语速可调（老年人默认 0.85）。
 * 初始化是异步的：ready 之前的朗读请求按序存入 pending，就绪后按序补播
 * （不设就绪回调——回调里再 speak 是 QUEUE_FLUSH，会打断/重播已补播的内容）。
 *
 * v5 R8（design.md §5.5）：新增 [speaking] / [awaitQuiet]，供「作答后自动前进」等待本次播报念完。
 * [speak] / [stop] 的既有签名与 QUEUE_FLUSH 语义**不变**（调用点很多）。
 */
class TTSSpeaker(context: Context) {

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
     *  ⚠️ onStart 也要**置位**（见 init 里的 listener）——`speak()` 是 QUEUE_FLUSH，冲掉上一句会先给
     *  上一句发 onStop，那一瞬间 `speaking` 会被清成 false，而新这一句整段播放期间它都不再被置回 true。
     *  `!ready` 的 pending 分支不置位（那只入队，没有实际播报）。 */
    @Volatile
    var speaking: Boolean = false
        private set

    @Volatile
    var rate: Float = 0.85f

    /** 就绪前的朗读请求按顺序排队（欢迎语等不被后续卡片播报覆盖） */
    private val pendingQueue = ArrayDeque<String>()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.SIMPLIFIED_CHINESE
                tts?.setSpeechRate(rate)
                // 按序补播就绪前排队的请求（QUEUE_ADD 接续，不互相打断）
                pendingQueue.forEach { text ->
                    tts?.setSpeechRate(rate)
                    tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "shibang-utt")
                }
                pendingQueue.clear()
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

            // onError(String) 是 API 21 起 deprecated 的**旧重载**（新重载带 errorCode），框架两条路径都会回调，
            // 故一并覆盖；它非抽象成员（覆盖是可选的），只在 Kotlin 2.0 下产生 OVERRIDE_DEPRECATION **警告**
            // （不是错误；app/build.gradle.kts 未开 allWarningsAsErrors），@Suppress 把它压掉。
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

    fun speak(text: String) {
        if (!ready) {
            if (pendingQueue.lastOrNull() != text) pendingQueue.add(text)
            if (pendingQueue.size > 4) pendingQueue.removeFirst()
            return
        }
        speaking = true                 // ⚠️ 同步置位，勿挪进 listener（原因见 speaking KDoc）
        tts?.setSpeechRate(rate)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "shibang-utt")
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
