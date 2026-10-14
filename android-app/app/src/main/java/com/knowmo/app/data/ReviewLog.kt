package com.knowmo.app.data

import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

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
 *
 * I/O 模型（2026-10-14 重构，代码审查问题：原 append/entries 都在主线程做磁盘 I/O，且文件
 * 只增不减——重度使用一年上万行后，打开设置页做全量读取会卡主线程）：
 * - **追加走单线程后台执行器**（守护线程）：作答发生在主线程（answerCard 事务内），
 *   append 只入队，主线程不再碰磁盘；写失败只丢这一条日志，不影响学习状态（口径不变）。
 * - **读取进程内缓存**：首次读盘后缓存，之后 stats 重取不再碰磁盘；缓存与磁盘同步由
 *   同一条执行器串行维护（追加任务先写盘、再补进缓存，顺序天然一致）。
 * - **行数上限**：超 [MAX_LINES] 条时裁掉最旧的（IO 线程执行，主线程不等磁盘）——
 *   日志是统计原料不是账本，留最近一段足够分档记住率用。
 */
class ReviewLog(private val file: File) {

    /** 磁盘写线程（守护）：进程退出不等它，最多丢尾部几条日志（可接受，见类 KDoc） */
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "knowmo-review-log").apply { isDaemon = true }
    }

    private val cacheLock = Any()

    /** 已解析记录的进程内缓存（null = 尚未读过磁盘）；只在主线程读、执行器线程写，访问都过 [cacheLock] */
    private var cache: List<ReviewEntry>? = null

    /** 距上次超限检查的追加条数（IO 线程维护）：避免每次追加都读文件数行 */
    private val appendsSinceCheck = AtomicInteger(0)

    /** 追加一条记录；磁盘写在后台执行器完成，不阻塞调用方 */
    fun append(e: ReviewEntry) {
        io.execute {
            runCatching {
                file.appendText("${e.date},${e.termId},${e.interval},${e.elapsed},${if (e.known) 1 else 0}\n")
                // 缓存补录放在 IO 线程（不是 append 调用点）：entries() 先于本任务读盘时缓存已存在、
                // 由这里补上本条；读盘晚于本任务时文件已含本条、无需补——两种时序都不重不漏
                synchronized(cacheLock) { cache = cache?.plus(e) }
                if (appendsSinceCheck.incrementAndGet() >= TRIM_CHECK_EVERY) {
                    appendsSinceCheck.set(0)
                    trimIfOversized()
                }
            }
        }
    }

    /**
     * 读出全部记录；首次读盘后进程内缓存（格式不对的行跳过）。
     * 首次读盘仍发生在调用线程（设置页打开，一次/进程）——行数被 [MAX_LINES] 封顶，
     * 最坏几百 KB 的一次性读取，可接受。
     */
    fun entries(): List<ReviewEntry> {
        synchronized(cacheLock) { cache?.let { return it } }
        val loaded = loadFromDisk()
        synchronized(cacheLock) {
            // 竞态兜底：判空与装载之间别的线程恰好装过，以先装载的为准
            cache?.let { return it }
            cache = loaded
        }
        // 裁剪投递给执行器（entries 的调用线程通常是主线程，不能在这里等磁盘）；
        // 与追加串行，不会把并发新写入的行落在重写之外
        if (loaded.size > MAX_LINES) io.execute { trimIfOversized() }
        return loaded
    }

    /** 按实际间隔分档的复习记住率（只统计 elapsed ≥ 1 的复习检验） */
    fun retentionByGap(): List<RetentionBucket> {
        val tests = entries().filter { it.elapsed >= 1 }
        return GAP_BUCKETS.map { (label, range) ->
            val inBucket = tests.filter { it.elapsed in range }
            RetentionBucket(label, inBucket.size, inBucket.count { it.known })
        }
    }

    /* ---------- 内部（磁盘只在 IO 执行器上碰；entries 的首次读除外） ---------- */

    /** 全量读盘 + 逐行解析；文件不存在或读取失败返回空表 */
    private fun loadFromDisk(): List<ReviewEntry> {
        if (!file.isFile) return emptyList()
        return runCatching { file.readLines() }.getOrDefault(emptyList()).mapNotNull(::parseLine)
    }

    /**
     * 超限裁剪（只在 IO 执行器上执行，与追加天然串行——裁剪期间不会有并发写盘把新追加的行
     * 落在重写之外）：重读磁盘、保留最近 [MAX_LINES] 条重写；内存缓存同步取尾。
     */
    private fun trimIfOversized() {
        runCatching {
            val lines = file.readLines()
            if (lines.size > MAX_LINES) {
                file.writeText(lines.takeLast(MAX_LINES).joinToString("\n") + "\n")
            }
        }
        synchronized(cacheLock) { cache?.let { if (it.size > MAX_LINES) cache = it.takeLast(MAX_LINES) } }
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

        /** 日志行数上限：分档记住率只需近期样本；封顶也把 entries() 的首次读盘量钉死在几百 KB 内 */
        const val MAX_LINES = 10_000

        /** 每追加多少条做一次超限检查（读盘在 IO 线程，主线程无感） */
        private const val TRIM_CHECK_EVERY = 2_000
    }
}
