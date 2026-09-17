package com.qyj.shibang.data

import android.content.Context
import org.json.JSONObject

/**
 * 学习状态仓库：生词本、设置项。
 * 第一版用 SharedPreferences + JSON 持久化；复习调度入库（Room）留待下一迭代。
 */
class StudyRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("study_state", Context.MODE_PRIVATE)

    private val forgot = linkedMapOf<String, Int>()

    var fontScale: Float = 1f
        private set
    var speechRate: Float = 0.85f
        private set
    var remindTime: String = "下午 3 点"
        private set

    init {
        load()
    }

    val forgotIds: List<String>
        get() = forgot.keys.toList()

    fun forgotCount(id: String): Int = forgot[id] ?: 0

    fun addForgot(id: String) {
        forgot[id] = (forgot[id] ?: 0) + 1
        persist()
    }

    fun setFontScale(v: Float) {
        fontScale = v
        persist()
    }

    fun setSpeechRate(v: Float) {
        speechRate = v
        persist()
    }

    fun setRemindTime(t: String) {
        remindTime = t
        persist()
    }

    private fun persist() {
        runCatching {
            val obj = JSONObject()
            obj.put("fscale", fontScale.toDouble())
            obj.put("rate", speechRate.toDouble())
            obj.put("remind", remindTime)
            val f = JSONObject()
            forgot.forEach { (k, v) -> f.put(k, v) }
            obj.put("forgot", f)
            prefs.edit().putString("state", obj.toString()).apply()
        }
    }

    private fun load() {
        val raw = prefs.getString("state", null) ?: return
        runCatching {
            val obj = JSONObject(raw)
            fontScale = obj.optDouble("fscale", 1.0).toFloat()
            speechRate = obj.optDouble("rate", 0.85).toFloat()
            remindTime = obj.optString("remind", "下午 3 点")
            val f = obj.optJSONObject("forgot") ?: return
            f.keys().forEach { k -> forgot[k] = f.optInt(k, 0) }
        }
    }

    companion object {
        /** 简化 Leitner 间隔：认识 → 1→3→7→15 天；忘了 → 明天（1 天） */
        val INTERVALS = listOf(1, 3, 7, 15)

        fun nextInterval(current: Int): Int {
            val i = INTERVALS.indexOf(current)
            if (i < 0) return INTERVALS[1]
            return INTERVALS[(i + 1).coerceAtMost(INTERVALS.lastIndex)]
        }
    }
}
