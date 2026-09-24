package com.knowmo.app.data

import java.io.File

/**
 * 一次作答记录。interval = 作答前的间隔天数（新词 0）；elapsed = 距上次学习的天数（新词 -1）。
 * elapsed ≥ 1 的记录就是隔天后的复习检验——当天的后续作答（学习连击）elapsed 恒为 0。
 */
data class ReviewEntry(
    val date: String,
    val termId: String,
    val interval: Int,
    val elapsed: Int,
    val known: Boolean,
)

/** 一个间隔档的复习记住率：tests = 检验次数，known = 其中答「认识」的次数 */
data class RetentionBucket(val label: String, val tests: Int, val known: Int)

/**
 * 作答日志：每次作答追加一行 CSV（date,termId,interval,elapsed,known），用于统计真实记住率，
 * 也为将来按真实数据拟合调度参数（如 FSRS）留底。
 * 单独存文件而不进 SharedPreferences：日志只增不改，放进主 state JSON 会让每次作答重写整份历史。
 */
class ReviewLog(private val file: File) {

    /** 追加一条记录；写失败只丢这一条日志，不影响学习状态 */
    fun append(e: ReviewEntry) {
        runCatching {
            file.appendText("${e.date},${e.termId},${e.interval},${e.elapsed},${if (e.known) 1 else 0}\n")
        }
    }

    /** 读出全部记录；格式不对的行跳过 */
    fun entries(): List<ReviewEntry> {
        if (!file.isFile) return emptyList()
        return runCatching { file.readLines() }.getOrDefault(emptyList()).mapNotNull(::parseLine)
    }

    /** 按实际间隔分档的复习记住率（只统计 elapsed ≥ 1 的复习检验） */
    fun retentionByGap(): List<RetentionBucket> {
        val tests = entries().filter { it.elapsed >= 1 }
        return GAP_BUCKETS.map { (label, range) ->
            val inBucket = tests.filter { it.elapsed in range }
            RetentionBucket(label, inBucket.size, inBucket.count { it.known })
        }
    }

    /** 解析一行日志；字段数或数字不合法返回 null */
    private fun parseLine(line: String): ReviewEntry? {
        val f = line.split(',')
        if (f.size != 5) return null
        return ReviewEntry(
            date = f[0],
            termId = f[1],
            interval = f[2].toIntOrNull() ?: return null,
            elapsed = f[3].toIntOrNull() ?: return null,
            known = f[4] == "1",
        )
    }

    companion object {
        private val GAP_BUCKETS = listOf(
            "1~3天" to 1..3,
            "4~10天" to 4..10,
            "11~30天" to 11..30,
            "30天以上" to 31..Int.MAX_VALUE,
        )
    }
}
