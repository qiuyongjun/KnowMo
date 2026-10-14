package com.knowmo.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * StudyRepository 调度核心 JVM 单测（2026-10-14 第 3 批，CI `testDebugUnitTest` 跑）：
 * 覆盖 buildQueue（复习优先 / 保底新词 / 清债配额 / interleave 前 3 含新词 / 逾期程度裁剪）、
 * answerCard（新词连击 3 次 / 复习检验过关 / 忘记 + restoreTo 兑现）、nextInterval 封顶分级、
 * 跨日队列重建、streak、persist/load 往返。
 *
 * 可测性由 [StudyRepository] 的 clock / store / reviewLog 注入保证（类 KDoc「可测性」）；
 * org.json 用 build.gradle.kts 的 testImplementation 真实现（android.jar 桩会抛 Stub!）。
 * 拼音 / 词库内容对本套断言无意义（调度只读 term id / scene），音节统一占位。
 */
class StudyRepositoryScheduleTest {

    /** 可拨钟（CST 时区，起日 2026-10-14）——所有日期判断经它 */
    private class MutableClock(start: Instant, private val zone: ZoneId = ZoneId.of("Asia/Shanghai")) : Clock() {
        @Volatile var instant: Instant = start
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
        override fun instant(): Instant = instant
        fun advanceDays(days: Long) { instant = instant.plus(Duration.ofDays(days)) }
    }

    /** 内存版持久化（load = 读 map；save = 覆盖 + 可选 streak；其余键各存各的） */
    private class MemStore : StudyStateStore {
        val map = mutableMapOf<String, String?>()
        override fun loadState(): String? = map["state"]
        override fun saveState(stateJson: String, streakCount: Int?, streakLastDate: String?) {
            map["state"] = stateJson
            if (streakCount != null && streakLastDate != null) {
                map["streak_count"] = streakCount.toString()
                map["last_study_date"] = streakLastDate
            }
        }
        override fun streakCount(): Int = map["streak_count"]?.toIntOrNull() ?: 0
        override fun streakLastDate(): String? = map["last_study_date"]
        override fun backupCorrupt(raw: String, date: String) {
            map["state_corrupt_backup"] = raw
            map["state_corrupt_backup_date"] = date
        }
    }

    private val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private lateinit var clock: MutableClock
    private lateinit var store: MemStore
    private lateinit var logFile: File
    private lateinit var repo: StudyRepository

    private val today: String get() = LocalDate.now(clock).format(fmt)
    private fun daysAgo(n: Long): String = LocalDate.now(clock).minusDays(n).format(fmt)

    @Before fun setUp() {
        // 2026-10-13T16:00Z = 2026-10-14 00:00 CST（CST = UTC+8）
        clock = MutableClock(Instant.parse("2026-10-13T16:00:00Z"))
        store = MemStore()
        logFile = File.createTempFile("reviewlog", ".csv").apply { deleteOnExit() }
    }

    @After fun tearDown() {
        // 复位全局注册表（parseCsv 测试依赖空 banks；本套会 install，不清会污染同 JVM 其它单测）
        CustomBanks.installForTest(emptyList())
        logFile.delete()
    }

    /* ---------- 测试夹具 ---------- */

    /** 词条（scene 固定 c1，占位音节与字数匹配即可——调度只读 id / scene） */
    private fun term(id: String, text: String = id): Term = Term(
        id = id, scene = "c1", icon = "📚",
        text = text,
        chars = text.mapIndexed { i, ch -> TermChar(ch.toString(), "p$i") },
        tip = "用途",
    )

    /** 安装单库 c1 含给定词条 */
    private fun install(vararg terms: Term) {
        CustomBanks.installForTest(listOf(CustomBank(id = "c1", name = "库", icon = "📚", terms = terms.toList())))
    }

    private fun newRepo(quota: Int = 10, newQuota: Int = 5): StudyRepository = StudyRepository(
        context = null,
        quotaProvider = { quota },
        newQuotaProvider = { newQuota },
        recScenesProvider = { setOf("c1") },
        clock = clock,
        store = store,
        reviewLog = ReviewLog(logFile),
    )

    /** 预置 termStates 进 store（每个 Pair = id to TermState）；day 字段恒今天 */
    private fun seedStates(vararg states: Pair<String, TermState>, day: String? = null) {
        val ts = JSONObject()
        states.forEach { (id, st) ->
            ts.put(id, JSONObject()
                .put("days", st.days)
                .put("lastSeen", st.lastSeen)
                .put("lapses", st.lapses)
                .put("ease", st.ease)
                .put("restoreTo", st.restoreTo))
        }
        val obj = JSONObject()
        obj.put("terms", ts)
        obj.put("day", JSONObject().put("date", day ?: today).put("counts", JSONObject()).put("forgotAnswers", 0))
        obj.put("queues", JSONObject())
        obj.put("favorites", JSONArray())
        store.map["state"] = obj.toString()
    }

    /* ---------- nextInterval 封顶分级 ---------- */

    @Test fun nextInterval_capsAndRounding() {
        repo = newRepo()
        assertEquals(3, repo.nextInterval(1, 2.5, 0))    // 1*2.5=2.5 → round half up = 3
        assertEquals(13, repo.nextInterval(5, 2.5, 0))   // 5*2.5=12.5 → 13
        assertEquals(25, repo.nextInterval(10, 2.5, 0))  // 25
        assertEquals(1, repo.nextInterval(0, 2.5, 0))    // 0 → coerce 1（下限）
        assertEquals(60, repo.nextInterval(40, 2.5, 0))   // 100 → 成熟词封顶 60
        assertEquals(30, repo.nextInterval(40, 2.5, 1))  // 有遗忘史 → 普通封顶 30
        assertEquals(30, repo.nextInterval(40, 2.4, 0))   // ease < 2.5 → 普通封顶 30
    }

    /* ---------- buildQueue ---------- */

    @Test fun buildQueue_freshDayAllNews() {
        install(*(1..12).map { term("w$it") }.toTypedArray())
        repo = newRepo(quota = 10, newQuota = 5)
        val q = repo.buildQueue()
        // 无到期词：reservedNews=1, reviews=0, newsPool=min(5,10)=5 → 5 张全 NEW
        assertEquals(5, q.queue.size)
        assertEquals(5, q.modes.count { it == StudyRepository.MODE_NEW })
        assertEquals(0, q.modes.count { it == StudyRepository.MODE_REVIEW })
    }

    @Test fun buildQueue_reviewsTakePriority_newsCappedToFloor1() {
        // 12 到期挤占 10 配额 + 2 新词：reservedNews=1, reviews=9, newsPool=min(5,10-9)=1
        val due = (1..12).map { "w$it" to TermState(2, daysAgo(3), 0, 2.5, 0) }
        install(*((1..12).map { term("w$it") } + (13..14).map { term("w$it") }).toTypedArray())
        seedStates(*due.toTypedArray())
        repo = newRepo(quota = 10, newQuota = 5)
        val q = repo.buildQueue()
        assertEquals(10, q.queue.size)
        assertEquals(9, q.modes.count { it == StudyRepository.MODE_REVIEW })
        assertEquals(1, q.modes.count { it == StudyRepository.MODE_NEW })
    }

    @Test fun buildQueue_cutsLeastOverdueWhenQuotaBelowDue() {
        // 4 个到期词、配额 3：按逾期程度（已过天数÷间隔）降序取前 3，最不逾期的被切掉
        install(*(1..4).map { term("w$it") }.toTypedArray())
        seedStates(
            "w1" to TermState(2, daysAgo(5), 0, 2.5, 0),   // 5/2 = 2.5
            "w2" to TermState(2, daysAgo(4), 0, 2.5, 0),   // 4/2 = 2.0
            "w3" to TermState(2, daysAgo(3), 0, 2.5, 0),   // 3/2 = 1.5
            "w4" to TermState(10, daysAgo(11), 0, 2.5, 0),  // 11/10 = 1.1 ← 被切
        )
        repo = newRepo(quota = 3, newQuota = 5)
        val q = repo.buildQueue()
        assertEquals(3, q.queue.size)
        assertTrue("最该复习的 w1 必入队", "w1" in q.queue)
        assertFalse("逾期程度最低的 w4 被配额切掉", "w4" in q.queue)
        assertEquals(3, q.modes.count { it == StudyRepository.MODE_REVIEW })
    }

    @Test fun buildQueue_debtModeBumpsQuota() {
        // 到期 ≥ 20（DEBT_THRESHOLD）触发清债：默认配额 10 → 清债总量 15，仍保底 1 新词
        val due = (1..22).map { "w$it" to TermState(2, daysAgo(3), 0, 2.5, 0) }
        install(*((1..22).map { term("w$it") } + (23..25).map { term("w$it") }).toTypedArray())
        seedStates(*due.toTypedArray())
        repo = newRepo(quota = 10, newQuota = 5)
        val q = repo.buildQueue()
        assertEquals(15, q.queue.size)
        assertEquals(14, q.modes.count { it == StudyRepository.MODE_REVIEW })
        assertEquals(1, q.modes.count { it == StudyRepository.MODE_NEW })
    }

    @Test fun buildQueue_interleavePutsNewsInFirstThree() {
        // 5 到期 + 5 新词、配额 10：interleave 在位置 1 放新词，保证前 3 张内见得到新词
        val due = (1..5).map { "w$it" to TermState(2, daysAgo(3), 0, 2.5, 0) }
        install(*((1..5).map { term("w$it") } + (6..10).map { term("w$it") }).toTypedArray())
        seedStates(*due.toTypedArray())
        repo = newRepo(quota = 10, newQuota = 5)
        val q = repo.buildQueue()
        assertEquals(10, q.queue.size)
        assertEquals(5, q.modes.count { it == StudyRepository.MODE_NEW })
        assertEquals(5, q.modes.count { it == StudyRepository.MODE_REVIEW })
        assertEquals(StudyRepository.MODE_NEW, q.modes[1])
    }

    /* ---------- answerCard ---------- */

    @Test fun answerCard_newWordNeedsThreeHits() {
        install(term("w1"), term("w2"))
        repo = newRepo()
        val q = repo.ensureQueue()
        assertEquals(2, q.queue.size)
        val first = q.cardIds[0]
        val r1 = repo.answerCard(first, true)!!
        assertEquals(1, r1.count)
        assertFalse(r1.completed)
        assertEquals(StudyRepository.DAILY_COMBO_TARGET, r1.target)
        assertNotNull(r1.repeatCardId)
        val r2 = repo.answerCard(r1.repeatCardId!!, true)!!
        assertEquals(2, r2.count)
        assertFalse(r2.completed)
        assertNotNull(r2.repeatCardId)
        val r3 = repo.answerCard(r2.repeatCardId!!, true)!!
        assertEquals(3, r3.count)
        assertTrue(r3.completed)
        assertNull(r3.repeatCardId)
        // 已答卡重复提交 → null（防快速双击重复累计连击）
        assertNull(repo.answerCard(r2.repeatCardId!!, true))
    }

    @Test fun answerCard_reviewKnownUpgradesInterval() {
        // days=5、6 天前学过 → 到期；答认识即过关，ni = nextInterval(5,2.5,0) = 13（12.5 → 13）
        install(term("w1"))
        seedStates("w1" to TermState(5, daysAgo(6), 0, 2.5, 0))
        repo = newRepo()
        val q = repo.ensureQueue()
        val r = repo.answerCard(q.cardIds[0], true)!!
        assertTrue(r.upgraded)
        assertEquals(13, r.daysAfter)
        assertTrue(r.completed)
        assertEquals(StudyRepository.REVIEW_COMBO_TARGET, r.target)
    }

    @Test fun answerCard_reviewForgotSetsRestoreTo_redeemedNextDay() {
        // days=10、11 天前学过 → 到期；答忘了 → restoreTo = days/2 = 5，未过关
        install(term("w1"))
        seedStates("w1" to TermState(10, daysAgo(11), 0, 2.5, 0))
        repo = newRepo()
        val q = repo.ensureQueue()
        val r = repo.answerCard(q.cardIds[0], false)!!
        assertFalse(r.completed)
        assertEquals(0, r.count)
        assertEquals(StudyRepository.REVIEW_COMBO_TARGET, r.target)
        assertNotNull(r.repeatCardId)
        assertEquals(1, repo.todayForgot())
        // 次日重考通过：nextInterval(1, 2.3, 1) = round(2.3) = 2，被 restoreTo(5) 抬到 5（兑现）
        clock.advanceDays(1)
        val q2 = repo.ensureQueue()
        val r2 = repo.answerCard(q2.cardIds[0], true)!!
        assertTrue(r2.upgraded)
        assertEquals(5, r2.daysAfter)
        assertTrue(r2.completed)
    }

    /* ---------- 跨日重建 ---------- */

    @Test fun crossDay_rebuildsQueue() {
        install(term("w1"))
        repo = newRepo()
        val q1 = repo.ensureQueue()
        assertEquals(today, q1.date)
        clock.advanceDays(1)
        val q2 = repo.ensureQueue()
        assertNotEquals(q1.date, q2.date)
        assertEquals(today, q2.date)
    }

    /* ---------- streak ---------- */

    @Test fun streak_advancesPerDay_andBreaksAfterGap() {
        install(term("w1"))
        repo = newRepo()
        assertEquals(0, repo.studyStreak())
        val q = repo.ensureQueue()
        repo.answerCard(q.cardIds[0], true)
        assertEquals(1, repo.studyStreak())
        // 次日未答仍「活着」（昨天学过、今天还没学）
        clock.advanceDays(1)
        assertEquals(1, repo.studyStreak())
        val q2 = repo.ensureQueue()
        repo.answerCard(q2.cardIds[0], true)
        assertEquals(2, repo.studyStreak())
        // 断 ≥ 2 天 → 0
        clock.advanceDays(3)
        assertEquals(0, repo.studyStreak())
    }

    /* ---------- persist/load 往返 ---------- */

    @Test fun persistLoad_restoresFrozenQueueAndCombos() {
        install(term("w1"), term("w2"))
        repo = newRepo()
        val q = repo.ensureQueue()
        // 答一次 w1（count=1，未满 3）：事务插入重复卡 → 队列 2→3
        repo.answerCard(q.cardIds[0], true)
        // 同 store / clock 再开一个仓库：当日队列应冻结恢复、连击 / 战果同口径
        val repo2 = newRepo()
        val q2 = repo2.ensureQueue()
        assertEquals(3, q2.queue.size)            // 原始 2 张 + 1 张重复卡
        assertEquals(2, q2.queue.distinct().size)
        assertTrue("已答卡实例的 answered 标记恢复", q2.answered[0])
        assertEquals(1, repo2.studyStreak())
        assertEquals(1, repo2.todayKnownWords())  // w1 连击计数 ≥ 1
    }
}
