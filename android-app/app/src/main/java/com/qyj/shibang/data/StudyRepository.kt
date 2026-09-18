package com.qyj.shibang.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.random.Random

/**
 * 间隔层（全局永久状态）：间隔天数 + 上次学习日期（yyyy-MM-dd）+ 遗忘史次数。
 * 「认识」未满当日连击（3 次）**不写**本状态——词保持原到期状态，次日自然回池（遗留词机制）。
 * 满 3 移除时升一级（1→3→7→15→30，每日最多升一级），升级时 lapses 减半（遗忘史半衰）；
 * 「忘了」→ days=1 + lastSeen=今天 + lapses+1（v5：遗忘史让 due 排序获得优先权，修正忘词排池尾的问题）。
 * 未学过的词没有 TermState。迁移：第二轮的 "count" 键弃读，days 缺省 1（design.md §9.1）；
 * 旧 JSON 无 "lapses" 键缺省 0（v5 兼容口径）。
 */
data class TermState(val days: Int, val lastSeen: String, val lapses: Int = 0)

/**
 * 连击层（当日状态，跨频道共享，隔天随日期不符整体作废）：
 * counts = 每词当日「认识」计数（0–3；R10 后只有**每日任务的复习卡**作答会写入——浏览卡没有作答入口）；
 * seen   = 当日已教读词集合（markSeen 幂等去重——防重启/回滑后对同一 NEW 卡重复追加考核卡；
 *          v5 R7 另供「新词卡当日是否已教读」的待处理判定：wasSeenToday）；
 * knownAnswers / forgotAnswers = 当日「认识」/「忘了」作答**次数**（v5 R7 完成卡战果，design.md §5.3）：
 *   与 counts 同一当日生命周期（隔天随 date 不符整体作废），重启不清零——完成是跨会话可达的事件，
 *   会话计数重启归零会让最真实的完成卡显示「认识了 0 次」。
 */
data class DayState(
    val date: String,
    val counts: Map<String, Int>,
    val seen: Set<String>,
    val knownAnswers: Int = 0,
    val forgotAnswers: Int = 0,
)

/**
 * 当日队列（**队列型频道只有每日任务 `CHANNEL_DAILY` 一条**，R10 §5.7）：date 与今天不符则作废重建。
 * 场景分区已改判为**池型频道**（运行时抽取、无限追加、不落库），不再有队列。
 * modes / answered 与 queue 平行等长：
 * modes 记录每张卡生成时的形态（新学/复习）；
 * answered 记录**该卡实例**是否已作答——断点恢复按卡逐卡还原（design.md §9.1）：
 * v4 同一词会出现多张卡（学 1 次 + 连击考核若干次），词级判定会把剩余考核卡一并展开，
 * 重启后连击卡死，故必须按卡实例记录。
 */
data class DailyQueue(
    val date: String,
    val channel: String,
    val queue: List<String>,
    val modes: List<String>,
    val answered: List<Boolean>,
    val position: Int,
)

/**
 * 学习状态仓库：间隔层（TermState）+ 连击层（DayState）+ 每日队列（DailyQueue）。
 * SharedPreferences + JSON 持久化（36 词量级不上 Room）；全部收在 "state" 一个 JSON 里：
 * terms = {"days", "lastSeen", "lapses"}；day = {"date", "counts", "seen", "knownAnswers", "forgotAnswers"}；
 * queues 各频道含 "answered"。
 *
 * v4 连击 + 间隔双层模型（design.md §9，第三轮定稿）：
 * 连击层管当日移出队列（满 3 移除、忘了清零）；间隔层管跨天调度（1→3→7→15→30、每日最多升一级）。
 * v5（09-17-scheduling-v5）：阶梯封顶延至 30（稳态日到期量降到配额可覆盖量级），毕业判定固定 15
 * 与封顶解耦；TermState 增 lapses 遗忘史（due 排序优先 + 升级减半）；推荐频道高债务日
 * （due ≥ DEBT_THRESHOLD）进清债模式扩配额全复习、不补新词。
 * v5 R7 附带当日战果计数（knownAnswers / forgotAnswers）——只加计数，不动调度逻辑。
 * v5 R9：场景分区改判为**专题自主练习**——完成卡与前向拦截只在每日任务频道
 * （CHANNEL_DAILY = "rec"）生效；分区**不做到期筛选**（该区全部词入池，故不存在空白屏）。
 * v5 R10（本轮）：自主学习（温故流 + 场景分区）改判为**纯浏览的池型频道**，频道因此分两类——
 * **队列型（只有每日任务频道）**：`buildQueue` / `ensureQueue` / `appendQueue` / `markAnswered` /
 * `saveQueuePosition` 一律**不带 channel 参数**（内部固定 CHANNEL_DAILY），让「队列 = 每日任务」
 * 成为签名层面的事实；**池型（12 个场景分区 + 推荐频道的温故流）**：`poolIds` 运行时抽取、抽完重洗、
 * 无限追加、**不落库**，分区池 = 该区**全部词**（含未学词）加权随机。
 * 池型频道**零写入**：浏览卡不作答也不 markSeen → 未学词只「看」不算学会（新词入口唯一化到每日任务）。
 * 调度层其余逻辑（间隔阶梯、lapses、毕业判定、清债模式、连击/战果计数）一律不动。
 */
class StudyRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("study_state", Context.MODE_PRIVATE)

    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val termStates = linkedMapOf<String, TermState>()
    private val queues = linkedMapOf<String, DailyQueue>()
    private var day = DayState("", emptyMap(), emptySet())

    init {
        load()
    }

    /* ---------- 双层状态机（design.md §9.2，任何频道/自由刷口径一致） ---------- */

    fun today(): String = fmt.format(Date())

    /** 连击层：该词当日认识计数（隔天自动归零） */
    fun dayCount(id: String): Int = currentDay().counts[id] ?: 0

    /** 当日已移除（连击满 3）：不再追加队列；只出现在自由刷/自主复习中 */
    fun isRemovedById(id: String): Boolean = dayCount(id) >= DAILY_COMBO_TARGET

    /**
     * 当日该词是否已教读（v5 R7，供 UI 恢复「新词卡待处理」判定：教读态不持久化，
     * 按当日 seen 集合恢复——否则重启后同一张新词卡会被再算一次待处理）。
     */
    fun wasSeenToday(id: String): Boolean = id in currentDay().seen

    /** 当日「认识」作答次数（v5 R7 完成卡战果，持久口径：重启不清零，隔天随 DayState 作废） */
    fun todayKnown(): Int = currentDay().knownAnswers

    /** 当日「忘了」作答次数（同上） */
    fun todayForgot(): Int = currentDay().forgotAnswers

    /**
     * 当日首次教读：写入 seen 并返回 true（不写 TermState——教读不产生间隔层状态）；
     * 当日已教读过（重启/回滑重触）返回 false，调用方不得重复追加考核卡。
     */
    fun markSeen(id: String): Boolean {
        val d = currentDay()
        if (id in d.seen) return false
        day = d.copy(seen = d.seen + id)
        persist()
        return true
    }

    /**
     * 「认识」：连击层计数 +1（封顶 3）；满 3（移除当日队列）时间隔层升级：
     * - 无 TermState → 写 {1, 今天, lapses=0}（首次完成）；
     * - 有状态且 lastSeen ≠ 今天 → days=nextInterval(days)、lastSeen=今天、lapses 减半（遗忘史半衰）；
     * - lastSeen == 今天（当日已升级或已忘了）→ 不升级（每日最多升一级闸门）。
     * 返回 (count, upgraded, daysAfter) 供 UI 播报剩余次数与升级后的天数。
     * v5 R7：当日「认识」次数 +1（完成卡战果，与 counts 同一当日生命周期）。
     */
    fun markKnown(id: String): Triple<Int, Boolean, Int> {
        val d = currentDay()
        val count = ((d.counts[id] ?: 0) + 1).coerceAtMost(DAILY_COMBO_TARGET)
        var upgraded = false
        var daysAfter = termStates[id]?.days ?: 0
        if (count >= DAILY_COMBO_TARGET) {
            val cur = termStates[id]
            when {
                cur == null -> {
                    termStates[id] = TermState(1, today(), 0)
                    upgraded = true
                    daysAfter = 1
                }
                cur.lastSeen != today() -> {
                    val ni = nextInterval(cur.days)
                    termStates[id] = TermState(ni, today(), cur.lapses / 2)
                    upgraded = ni != cur.days   // 已在 30 封顶时不再算升级（间隔值未变）
                    daysAfter = ni
                }
                // cur.lastSeen == 今天：闸门挡住，不升级、不播天数
            }
        }
        day = d.copy(counts = d.counts + (id to count), knownAnswers = d.knownAnswers + 1)
        persist()
        return Triple(count, upgraded, daysAfter)
    }

    /**
     * 「忘了」（任何地方作答口径一致）：连击层清零 + 间隔层写 {1, 今天, lapses+1}——
     * 只重置间隔，不回退为未学词；lapses+1（v5：次日 due 排序优先，遗忘曲线上最该复现的词先见）；
     * 分区完成状态随之即时回退（design.md §9.5）。
     * v5 R7：当日「忘了」次数 +1（完成卡战果，与 counts 同一当日生命周期）。
     */
    fun markForgot(id: String) {
        val d = currentDay()
        termStates[id] = TermState(1, today(), (termStates[id]?.lapses ?: 0) + 1)
        day = d.copy(counts = d.counts + (id to 0), forgotAnswers = d.forgotAnswers + 1)
        persist()
    }

    /* ---------- 到期与间隔阶梯（design.md §9.1） ---------- */

    /** 间隔阶梯：1→3→7→15→30（封顶；v5 延长阶梯压稳态到期量，毕业判定另行固定 15） */
    fun nextInterval(days: Int): Int = INTERVALS.firstOrNull { it > days } ?: INTERVALS.last()

    /** 到期日时间戳（lastSeen + days）；lastSeen 解析失败按最早处理（排最前清债） */
    private fun dueTime(state: TermState): Long =
        runCatching { fmt.parse(state.lastSeen)?.time }.getOrNull()
            ?.plus(state.days * DAY_MS) ?: Long.MIN_VALUE

    /** 是否到期：lastSeen + days ≤ 今天；空 lastSeen 视为立即到期 */
    fun isDue(state: TermState): Boolean {
        if (state.lastSeen.isEmpty()) return true
        val todayTime = runCatching { fmt.parse(today())?.time }.getOrNull() ?: return true
        return dueTime(state) <= todayTime
    }

    /* ---------- 分区完成（design.md §9.5；v5：毕业判定 days >= 15 与间隔封顶 30 解耦） ---------- */

    /**
     * 分区完成判定：该分区**每个词 days >= GRADUATED_DAYS**（毕业判定 = 15，v5 与封顶 30 解耦——
     * 毕业词继续升到 30 后 🎓 保持不回退）。
     * 达成 = 多轮**不同日**的满 3 连击（1→3→7→15）；任何一处「忘了」days 打回 1 → 即时退出完成状态（可逆）。
     * 空分区（`rec` 自身、或有词无条目的分区）不算完成。
     * R10：间隔层的唯一写入来源是**每日任务的复习卡作答**——在分区/温故流里浏览不写任何状态，
     * 故分区 🎓 只能靠每日任务推进（这是 D4 的已接受代价，见 prd R10）。
     */
    fun isSceneGraduated(sceneId: String): Boolean {
        val ids = STUDY_TERMS.filter { it.scene == sceneId }.map { it.id }
        if (ids.isEmpty()) return false
        return ids.all { (termStates[it]?.days ?: 0) >= GRADUATED_DAYS }
    }

    /**
     * 已完成分区集合（**仅供频道栏徽章展示，不影响队列范围**）；
     * 每日任务频道（`rec`）是聚合频道，不参与完成判定。
     */
    fun graduatedScenes(): Set<String> =
        SCENES.map { it.id }.filter { it != CHANNEL_DAILY && isSceneGraduated(it) }.toSet()

    /* ---------- 每日队列调度器（design.md §9.3） ---------- */

    /**
     * 范围（每日任务队列 + 池型频道抽取共用）：
     * - 每日任务频道（`rec`）= 全部词条（毕业分区不排除，纯进度标记）；
     * - 场景频道（池型）= 该场景词条。
     */
    private fun scopeIds(channel: String): List<String> =
        if (channel == CHANNEL_DAILY) STUDY_TERMS.map { it.id }
        else STUDY_TERMS.filter { it.scene == channel }.map { it.id }

    /**
     * 每日任务频道当日队列生成（design.md §9.3；R10 后**只有队列型频道**需要队列）：
     * - learned = 全部已学词，双键排序：lapses 降序优先（忘词先见）→ 到期日升序（先清旧债）；
     * - due     = learned 中到期的词（isDue）；
     * - news    = 未学词洗牌；
     * - pool：due ≥ DEBT_THRESHOLD（20）→ 清债模式 due.take(DEBT_QUOTA)（15 张全复习、不补新词，v5）；
     *   否则 (due + news).take(DAILY_POOL_QUOTA)（到期复核优先 + 新词补足，共 10 个，当日冻结由 ensureQueue 持久化保证）；
     * - queue 按 pool 原序（到期在前、每词一次），modes 按有无 TermState 标注，answered 全 false；
     * - pool 为空（全部已学且都没到期）→ 队列只有完成卡，直接进温故流（prd：一个也没有当天直接自由刷）
     *   ——**这是合法状态，不要「修」它**。
     * R10：R9 的 `else -> learned + news`（分区全量入队）分支已删除——分区是**池型**频道（§5.7），
     * 不再生成队列，保留那个分支只会是一段永远走不到、却看起来仍在服务分区的死代码。
     */
    fun buildQueue(): DailyQueue {
        val scope = scopeIds(CHANNEL_DAILY)
        // ⚠️ 显式类型参数是**元素类型**（这里是词 id，String），不是选择器的返回类型：
        //    写 <Int> 会得到 Comparator<Int>——既与 List<String>.sortedWith(Comparator<in String>) 不匹配，
        //    lambda 里的 termStates[id] 也会因 id: Int 取不到值，两者都是编译错误。
        val learned = scope
            .filter { termStates[it] != null }
            .sortedWith(
                compareByDescending<String> { id -> termStates[id]?.lapses ?: 0 }
                    .thenBy { id -> termStates[id]?.let { s -> dueTime(s) } ?: Long.MAX_VALUE }
            )
        val due = learned.filter { isDue(termStates[it]!!) }
        val news = scope.filter { termStates[it] == null }.shuffled()
        val pool = if (due.size >= DEBT_THRESHOLD) due.take(DEBT_QUOTA)   // 清债模式：全复习、不补新词
                   else (due + news).take(DAILY_POOL_QUOTA)               // 到期复核 + 新词补足

        val queue = ArrayList<String>(pool.size)
        val modes = ArrayList<String>(pool.size)
        val answered = ArrayList<Boolean>(pool.size)
        for (id in pool) {
            queue.add(id)
            modes.add(if (termStates[id] == null) MODE_NEW else MODE_REVIEW)
            answered.add(false)
        }
        return DailyQueue(today(), CHANNEL_DAILY, queue, modes, answered, 0)
    }

    /**
     * 当日有效队列（**只有每日任务频道**，R10 §5.7）：date 相符直接复用（断点续刷 + 当日冻结），
     * 否则重建并持久化。
     * R10 简化：场景分区已不再有队列，故 R9 那条「空队列不复用」补丁一并删除——每日任务的
     * **空队列是合法状态**（pool 空 → 队列只有完成卡 → 直接温故流，prd 已定义），不要把它当异常修掉。
     */
    fun ensureQueue(): DailyQueue {
        val existing = queues[CHANNEL_DAILY]
        if (existing != null && existing.date == today()) return existing
        val fresh = buildQueue()
        queues[CHANNEL_DAILY] = fresh
        persist()
        return fresh
    }

    /**
     * 作答/教读后词**未移除**（当日连击 <3）→ 追加队尾（REVIEW 形态、answered=false，当天再见）；
     * 已移除不追加。返回追加后的队尾下标（作为该卡实例的队列索引，markAnswered 用）；未追加返回 -1。
     * 调用方须先 markKnown/markForgot 更新连击计数，再调本方法（移除判定依赖最新计数）。
     * R10：只有每日任务频道的复习卡会走到这里（池型频道的浏览卡没有作答入口）。
     */
    fun appendQueue(id: String): Int {
        val q = queues[CHANNEL_DAILY] ?: return -1
        if (q.date != today()) return -1
        if (isRemovedById(id)) return -1
        queues[CHANNEL_DAILY] = q.copy(
            queue = q.queue + id,
            modes = q.modes + MODE_REVIEW,
            answered = q.answered + false,
        )
        persist()
        return q.queue.size   // q 仍是旧队列：旧长度 N = 新元素（队尾）下标
    }

    /** 断点续刷用：标记该卡实例已作答（answered[i]=true），按卡逐卡持久化（design.md §9.1） */
    fun markAnswered(index: Int) {
        val q = queues[CHANNEL_DAILY] ?: return
        if (index < 0 || index >= q.queue.size) return
        val ans = q.answered.toMutableList()
        while (ans.size < q.queue.size) ans.add(false)   // 旧数据兜底
        if (ans[index]) return
        ans[index] = true
        queues[CHANNEL_DAILY] = q.copy(answered = ans)
        persist()
    }

    /** 断点位置落库（只有每日任务频道；池型频道顺序随机、页码无意义，不落库） */
    fun saveQueuePosition(position: Int) {
        val q = queues[CHANNEL_DAILY] ?: return
        val p = position.coerceIn(0, q.queue.size)
        if (q.position == p) return
        queues[CHANNEL_DAILY] = q.copy(position = p)
        persist()
    }

    /* ---------- 池型频道抽取（R10 §3.1 / §5.7） ---------- */

    /**
     * 池型频道的抽取池（温故流与分区共用同一条追加逻辑，差别只在池的构成）：
     * - **每日任务频道（`rec`）的温故流** = 该范围**已学词**洗牌（等概率，保持 v4 既有行为）——
     *   有 TermState 即已学（含毕业词）；「教读未完成连击」的词无 TermState，不入池；
     * - **场景分区** = 该区**全部词**（含未学词）加权随机（D3/D5）——未学词因此也能被浏览，
     *   但浏览不写状态、不算学会。
     * 抽完由调用方重洗（`AppRoot.appendPoolPages`），池必非空（每区 ≥ 30 词，§5.7-8）。
     */
    fun poolIds(channel: String): List<String> =
        if (channel == CHANNEL_DAILY) scopeIds(channel).filter { termStates[it] != null }.shuffled()
        else weightedShuffle(scopeIds(channel))

    /** 加权随机排列（Efraimidis–Spirakis 指数键）：key = -ln(u)/w，升序即无放回加权抽样。
     *  选它而不是「按权重轮盘逐个抽」：一趟 `sortedBy` 完成（O(n log n)，n ≤ 368）且边界更少。 */
    private fun weightedShuffle(ids: List<String>): List<String> {
        val rnd = Random.Default
        return ids.map { id ->
            val w = poolWeight(id)
            val u = 1.0 - rnd.nextDouble()   // u ∈ (0,1]：nextDouble() 返回 0 时 ln(0) → +Inf
            id to (-ln(u) / w)
        }.sortedBy { it.second }.map { it.first }
    }

    /**
     * 分区抽词权重（**只读**：忘得越多、越久没看 → 越容易被抽到；未学词取固定中等权重）：
     * `w = 1.0`（基础：刚复习过的词也不是 0 概率）`+ 2×lapses`（遗忘史）
     *      `+ min(距上次学习天数, 60) / 30.0`（久未复习，两个月封顶，避免「一年没看」把权重拉到压倒性）。
     * 不写任何状态：浏览卡不作答 → `poolIds` 的调用链上没有 `persist()`。
     */
    private fun poolWeight(id: String): Double {
        val st = termStates[id] ?: return SCENE_NEW_WEIGHT            // 未学词：固定中等权重 2.0
        val last = runCatching { fmt.parse(st.lastSeen)?.time }.getOrNull()
        val now = runCatching { fmt.parse(today())?.time }.getOrNull()
        val daysAgo = if (last == null || now == null) 0L else ((now - last) / DAY_MS).coerceAtLeast(0L)
        return 1.0 + 2.0 * st.lapses + minOf(daysAgo, 60L) / 30.0
    }

    /* ---------- 连击层当日状态（隔天作废） ---------- */

    /** 当日有效 DayState：date 与今天不符（隔天/首次）则整体作废重建 */
    private fun currentDay(): DayState {
        if (day.date != today()) day = DayState(today(), emptyMap(), emptySet())
        return day
    }

    /* ---------- 持久化（读写对称） ---------- */

    private fun persist() {
        runCatching {
            val obj = JSONObject()
            val ts = JSONObject()
            termStates.forEach { (k, st) ->
                ts.put(k, JSONObject().put("days", st.days).put("lastSeen", st.lastSeen).put("lapses", st.lapses))
            }
            obj.put("terms", ts)
            val d = currentDay()
            obj.put(
                "day",
                JSONObject()
                    .put("date", d.date)
                    .put("counts", JSONObject().apply { d.counts.forEach { (k, v) -> put(k, v) } })
                    .put("seen", JSONArray(d.seen.toList()))
                    // v5 R7 当日战果（读写对称；旧版本读到多余 key 无害）
                    .put("knownAnswers", d.knownAnswers)
                    .put("forgotAnswers", d.forgotAnswers),
            )
            val qs = JSONObject()
            queues.forEach { (ch, q) ->
                qs.put(
                    ch,
                    JSONObject()
                        .put("date", q.date)
                        .put("queue", JSONArray(q.queue))
                        .put("modes", JSONArray(q.modes))
                        .put("answered", JSONArray(q.answered))
                        .put("position", q.position),
                )
            }
            obj.put("queues", qs)
            prefs.edit().putString("state", obj.toString()).apply()
        }
    }

    private fun load() {
        val raw = prefs.getString("state", null) ?: return
        runCatching {
            val obj = JSONObject(raw)
            obj.optJSONObject("terms")?.let { ts ->
                ts.keys().forEach { k ->
                    val s = ts.optJSONObject(k) ?: return@forEach
                    // 第二轮的 "count" 键弃读；days 缺省 1（视为已学、近期到期，design.md §9.1 迁移口径）；
                    // v5：旧 JSON 无 "lapses" 键缺省 0（无遗忘史，读写对称）
                    termStates[k] = TermState(s.optInt("days", 1), s.optString("lastSeen"), s.optInt("lapses", 0))
                }
            }
            // 连击层：date 不符 = 隔天，整体作废（不读入）
            obj.optJSONObject("day")?.let { dj ->
                val date = dj.optString("date")
                if (date == today()) {
                    val counts = linkedMapOf<String, Int>()
                    dj.optJSONObject("counts")?.let { cj ->
                        cj.keys().forEach { k -> counts[k] = cj.optInt(k) }
                    }
                    val seen = linkedSetOf<String>()
                    dj.optJSONArray("seen")?.let { sa ->
                        for (i in 0 until sa.length()) seen.add(sa.optString(i))
                    }
                    // v5 R7 当日战果：旧 JSON 无这两键 → 缺省 0（无需迁移代码，读写对称）
                    day = DayState(
                        date,
                        counts,
                        seen,
                        dj.optInt("knownAnswers", 0),
                        dj.optInt("forgotAnswers", 0),
                    )
                }
            }
            obj.optJSONObject("queues")?.let { qs ->
                qs.keys().forEach { ch ->
                    // R10：队列型频道只有每日任务——旧版本写下的**分区队列条目直接忽略**，
                    // 下次 persist() 自然消失（一次性清理，不需要迁移代码；DailyQueue.channel 字段保留）
                    if (ch != CHANNEL_DAILY) return@forEach
                    val qj = qs.optJSONObject(ch) ?: return@forEach
                    val queue = mutableListOf<String>()
                    val modes = mutableListOf<String>()
                    val answered = mutableListOf<Boolean>()
                    qj.optJSONArray("queue")?.let { ja ->
                        for (i in 0 until ja.length()) queue.add(ja.optString(i))
                    }
                    qj.optJSONArray("modes")?.let { ma ->
                        for (i in 0 until ma.length()) modes.add(ma.optString(i))
                    }
                    qj.optJSONArray("answered")?.let { aa ->
                        for (i in 0 until aa.length()) answered.add(aa.optBoolean(i))
                    }
                    // 读写对称兜底：modes/answered 与 queue 等长（旧数据缺省 REVIEW/未作答）
                    while (modes.size < queue.size) modes.add(MODE_REVIEW)
                    while (answered.size < queue.size) answered.add(false)
                    queues[ch] = DailyQueue(
                        qj.optString("date"),
                        ch,
                        queue,
                        modes,
                        answered,
                        qj.optInt("position", 0),
                    )
                }
            }
        }
    }

    companion object {
        /** 队列卡形态标注（持久化用；与 UI 层 CardMode 对应；自由刷页不落库，由 UI 运行时追加） */
        const val MODE_NEW = "NEW"
        const val MODE_REVIEW = "REVIEW"

        /** 每日任务频道 id（v5 R9 / R10）：**队列型频道只有它**——有完成卡、有前向拦截、有当日队列与断点。
         *  其余频道（场景分区）+ 推荐频道完成卡之后的温故流 = **池型**（运行时抽取、无限追加、不落库，
         *  design.md §5.7）。集中在此常量，避免各处硬编码字符串。 */
        const val CHANNEL_DAILY = "rec"

        /** 连击目标：当日「认识」满 3 = 移出当日队列并升一级间隔；任何一次「忘了」清零（design.md §9.0） */
        const val DAILY_COMBO_TARGET = 3

        /** 每日任务频道池配额（到期复核优先 + 新词补足，design.md §9.3）；
         *  池型频道不设配额（R10：分区 = 该区全部词，只是抽取顺序加权随机） */
        const val DAILY_POOL_QUOTA = 10

        /** 间隔阶梯（design.md §9.1；v5 延长封顶压稳态到期量）：1→3→7→15→30 */
        val INTERVALS = listOf(1, 3, 7, 15, 30)

        /** 分区毕业判定阈值（v5：固定 15，与 INTERVALS 封顶 30 显式解耦，不再跟随阶梯） */
        const val GRADUATED_DAYS = 15

        /** 推荐频道清债模式触发阈值：到期词 ≥ 20 时当日全复习、不补新词（v5） */
        const val DEBT_THRESHOLD = 20

        /** 清债模式配额：due.take(15)（v5：先清债再学新，阈值与扩容量保守） */
        const val DEBT_QUOTA = 15

        /** 分区抽词权重：**未学词**的固定中等权重（R10 D5）——比刚复习过的词（1.0）更靠前、
         *  比忘过/久未复习的词靠后，让未学词也能被浏览到但不是压倒性优先（design.md §3.1） */
        const val SCENE_NEW_WEIGHT = 2.0

        /** 一天的毫秒数（到期日 = lastSeen + days * DAY_MS，原型口径） */
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
