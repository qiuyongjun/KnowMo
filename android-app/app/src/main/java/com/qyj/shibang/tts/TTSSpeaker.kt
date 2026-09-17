package com.qyj.shibang.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Android TTS 封装：中文、语速可调（老年人默认 0.85）。
 * 初始化是异步的：ready 之前的朗读请求按序存入 pending，就绪后按序补播
 * （不设就绪回调——回调里再 speak 是 QUEUE_FLUSH，会打断/重播已补播的内容）。
 */
class TTSSpeaker(context: Context) {

    private var tts: TextToSpeech? = null

    @Volatile
    var ready: Boolean = false
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
    }

    fun speak(text: String) {
        if (!ready) {
            if (pendingQueue.lastOrNull() != text) pendingQueue.add(text)
            if (pendingQueue.size > 4) pendingQueue.removeFirst()
            return
        }
        tts?.setSpeechRate(rate)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "shibang-utt")
    }

    /** 立即停止当前朗读（快速连滑打断播报用） */
    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
