package com.qyj.shibang.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Android TTS 封装：中文、语速可调（老年人默认 0.85）。
 * 初始化是异步的：ready 之前的朗读请求存入 pending，就绪后补播最后一条。
 */
class TTSSpeaker(context: Context) {

    private var tts: TextToSpeech? = null

    @Volatile
    var ready: Boolean = false
        private set

    @Volatile
    var rate: Float = 0.85f

    /** TTS 引擎就绪回调（用于播欢迎语） */
    var onReady: (() -> Unit)? = null

    private var pending: String? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.SIMPLIFIED_CHINESE
                tts?.setSpeechRate(rate)
                pending?.let { speak(it) }
                pending = null
                onReady?.invoke()
            }
        }
    }

    fun speak(text: String) {
        if (!ready) {
            pending = text
            return
        }
        tts?.setSpeechRate(rate)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "shibang-utt")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
