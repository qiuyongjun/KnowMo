package com.knowmo.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.ln
import kotlin.random.Random
import kotlin.math.roundToInt
import kotlin.math.min
import kotlin.math.max

/**
 * 间隔层（全局永久状态）：间隔天数 + 上次学习日期（yyyy-MM-dd）+ 遗忘史次数 + 难度系数 ease。
 * v6 R12（SM-2 简化版）：废弃固定阶梯 INTERVALS，间隔由 `nextInterval(prevDays, ease, lapses)` 动态计算——
 * `days = (prevDays * ease).roundToInt().coerceIn(1, cap)`；v8 封顶分级：成熟词（ease=2.5 且 lapses=0）
 * cap=60、普通词 cap=30（见 nextInterval KDoc）。ease 默认 2.5（SM-2 初始值），
 * 认识升级时 `ease = min(2.5, ease + 0.1)`（答对加难度），忘了时 `ease = max(1.3, ease - 0.2)`（答错降难度）。
 * 当前调度规则：新词第一次认识只建立 1 天的学习中状态；已学词在同日第 1、2 次认识时只推进日内连击，
 * 第 3 次认识才提交跨日间隔。这样未完成连击就退出时，原到期状态仍保留，次日会重新回池。
 * 「忘了」→ days=1（次日必回池重考）+ restoreTo=减半目标；restoreTo 只在后续日期完成一次完整连击时兑现，
 * 避免同日重答直接恢复长间隔；同时保留 lapses+1 与 ease 降 0.2。
 * 旧 JSON 无 "ease" 键 → 缺省 2.5；无 "restoreTo" 键 → 缺省 0（兼容，无需迁移代码）；"lapses" 键缺省 0。
 */
data class TermState(val days: Int, val lastSeen: String, val lapses: Int = 0, val ease: Double = 2.5, val restoreTo: Int = 0)

/**
 * 连击层（当日状态，跨频道共享，隔天随日期不符整体作废）：
 * counts = 每词当日**连击计数**（0–3，v8 恢复 v4 语义，prd v8 第 1 条：认识 +1 封顶 3、忘了清零；
 *          满 3 → 该词移出当日队列；v7 期间写入的 1/0「已作答标记」按计数自然沿用——隔天随 date
 *          整体作废，无需迁移）；
 * knownAnswers / forgotAnswers = 当日「认识」/「忘了」作答**次数**（v5 R7 起持久化，design.md §5.3）：
 *   与 counts 同一当日生命周期（隔天随 date 不符整体作废），重启不清零——完成是跨会话可达的事件，
 *   会话计数重启归零会让最真实的完成卡显示「认识了 0 次」。
 *   ⚠️ v16：**UI 战果已不再读 knownAnswers**——完成卡与设置页改用 counts 派生的**去重词数**
 *   （`todayKnownWords()`）。字段本身保留：次数是 counts 之外的独立信息，且 persist/load 需读写对称，
 *   删字段会让旧 JSON 多出一个被忽略的键（无害但无谓）。
 * v7（design.md §12.1）：**seen 字段删除**（教读机制废除）——persist 不再写 "seen" 键；
 *   load 遇旧 JSON 的 "seen" 键直接忽略（读写不对称仅此一处，向前兼容，无需迁移）。
 */
data class DayState(
    val date: String,
    val counts: Map<String, Int>,
    val knownAnswers: Int = 0,
    val forgotAnswers: Int = 0,
)

/**
 * 当日队列（**队列型频道只有每日任务 `CHANNEL_DAILY` 一条**，R10 §5.7）：date 与今天不符则作废重建。
 * 场景分区已改判为**池型频道**（运行时抽取、无限追加、不落库），不再有队列。
 * modes / answered 与 queue 平行等长：
 * modes 记录每张卡生成时的形态（新学/复习）；
 * answered 记录**该卡实例**是否已作答——断点恢复按卡逐卡还原（design.md §9.1）。
 * v7（design.md §12.1）：**confirmed** = 总结卡（原完成卡）是否已被用户点「确认」——
 * 确认后当日重启 / 切频道回来直接进温故流浏览模式（browseMode），不重放考试卡与总结卡；
 * 旧 JSON 无 "confirmed" 键 → 缺省 false（兼容，无需迁移）；新队列构建恒 false。
 * v8（design.md §13.1）：连击未满 3 的词由作答事务在**运行时**插入重复卡；每张卡有稳定
 * `cardId`，断点恢复原样还原（含已插入的重复卡），不依赖会变化的队列下标定位。
 */
data class DailyQueue(
    val date: String,
    val channel: String,
    val queue: List<String>,
    val modes: List<String>,
    val answered: List<Boolean>,
    val position: Int,
    val confirmed: Boolean = false,
    /** 卡实例 id；旧 JSON 没有该字段时由仓库按日期/下标补齐。 */
    val cardIds: List<String> = emptyList(),
)

/** 一次作答后的结果；重复卡已在仓库事务中写入，UI 只负责同步页面。 */
data class AnswerResult(
    val count: Int,
    val upgraded: Boolean,
    val daysAfter: Int,
    val repeatCardId: String? = null,
    val insertBeforeCardId: String? = null,
)

/**
 * 分区学习进度（v14 起；v16 起由设置页的**分区卡**消费 —— 合并显隐/排序之后每一行的进度部分）：
 * scene 携带分区定义（icon/name 直接渲染）；learned/total = 该区已学数/总词条数。
 * **口径是分区自身**（不受该区显隐影响）：隐藏的分区照样报它自己学到哪儿了。
 * total 由 `STUDY_TERMS.filter` 动态算出——**不硬编码条数**（词库仍在扩，计数断言先跑
 * `research/vocab/check_wordbank_invariants.py`，工作备忘条款）。
 */
data class SceneProgress(val scene: Scene, val learned: Int, val total: Int)

/**
 * v14 学习统计快照（设置页「学习统计」节的全部数据，只读）：
 * 由 `StudyRepository.studyStats()` 一次性构建；设置页打开期间是**静态快照**——
 * 作答只发生在 feed、设置页打开期间无作答，不需要响应式订阅（design.md §16.2）。
 * learned / graduated / 各分区进度的口径都**限制在当前词库（STUDY_TERMS）内**：
 * termStates 可能残留已从词库删除的词 id（学习状态按 id 挂靠、id 永不复用），
 * 不剔除会让「已学 X / N」出现 X > N 的鬼账。
 *
 * ⚠️ v16：`totalWords` **不再是全词库条数**（v16 口径前的全词库 549 条），而是**当前可学范围**词数
 * （常用词 + 可见分区）—— 见 `studyStats()` 的 KDoc。QYJ 2026-09-21 拍板。
 * ⚠️ v16：**面向用户的文案一律用「学完」**（设置页「学完 N 词」是**词**级计数；顶栏分区 chip 的
 * 「学完」后缀是**分区**级 —— 该区每个词都学完）。字段名 `graduated` 沿用代码内的「毕业」术语
 * （与 `GRADUATED_DAYS` / `graduatedScenes` 一脉相承），**别为了对齐措辞去重命名标识符**。
 *
 * v16：`todayKnownWords` 由 v5 R7 的「作答**次数**」改为「认识过的**去重词数**」——
 * 连击机制下同一个词要点满 3 次「认识」才移出队列，次数口径会把 10 个词的成果报成 30 次，
 * 与完成卡「今天学完了 N 个词」并列时量纲不一致（两处现已统一为**词数**）。
 * 「忘了」刻意保留**次数**口径：忘了是可重复发生的事件，不是词的属性。
 */
data class StudyStats(
    val totalWords: Int,
    val learned: Int,
    val graduated: Int,
    val favorites: Int,
    val todayKnownWords: Int,
    val todayForgot: Int,
    val streak: Int,
    val f30Total: Int,
    val f30Fail: Int,
    val scenes: List<SceneProgress>,
)

/**
 * 学习状态仓库：间隔层（TermState）+ 连击层（DayState）+ 每日队列（DailyQueue）。
 * SharedPreferences + JSON 持久化（当前词库规模不上 Room）；主状态收在 "state" 一个 JSON 里：
 * terms = {"days", "lastSeen", "lapses", "ease", "restoreTo"}；
 * day = {"date", "counts", "knownAnswers", "forgotAnswers"}
 * （v7 起 "seen" 键废除不写，旧 JSON 读时忽略）；queues 各频道含 "answered" / "confirmed"。
 *
 * 历史决策记录（当前规则以 answerCard 为准）：
 * v4 曾用连击 + 间隔双层模型（design.md §9）：连击层管当日移出队列；间隔层管跨天调度（每日最多升一级）。
 * v5（09-17-scheduling-v5）：阶梯封顶延至 30（稳态日到期量降到配额可覆盖量级），毕业判定固定 15
 * 与封顶解耦；TermState 增 lapses 遗忘史（due 排序优先 + 升级减半）；推荐频道高债务日
 * （due ≥ DEBT_THRESHOLD）进清债模式扩配额优先复习（**v16 起新词配额照给**，见 DEBT_QUOTA）。
 * v5 R7 附带当日战果计数（knownAnswers / forgotAnswers）——只加计数，不动调度逻辑。
 * v5 R9：场景分区改判为**专题自主练习**——完成卡只在每日任务频道
 * （CHANNEL_DAILY = "rec"）生效；分区**不做到期筛选**（该区全部词入池，故不存在空白屏）。
 * v5 R10（本轮）：自主学习（温故流 + 场景分区）改判为**纯浏览的池型频道**，频道因此分两类——
 * **队列型（只有每日任务频道）**：`buildQueue` / `ensureQueue` / `answerCard` /
 * `saveQueuePosition` 一律**不带 channel 参数**（内部固定 CHANNEL_DAILY），让「队列 = 每日任务」
 * 成为签名层面的事实；**池型（13 个场景分区 + 推荐频道的温故流）**：`poolIds` 运行时抽取、抽完重洗、
 * 无限追加、**不落库**，分区池 = 该区**全部词**（含未学词）加权随机。
 * 池型频道**零写入**：浏览卡没有作答入口 → 未学词只「看」不算学会（新词入口唯一化到每日任务）。
 * v5 R11（本轮，design.md §5.8）：调度核心改**首答定调度**——当日首次作答即写间隔层并把该词当日移除
 * （counts 降级为已作答标记）；作答后不再追加考核卡；buildQueue 新词**交错**编排（interleave，新词不垫底）；
 * 清债日随「每词一卡」自然守恒（due ≥ 20 → 15 张，阈值/配额常量不动）；附 f30 观测计数
 * （答前 days == 30 落独立 prefs key，只读、不改任何调度行为）。
 * 调度层其余逻辑（间隔阶梯、lapses 排序与半衰、毕业判定、清债模式）不动。
 * v6 R12（09-20，SM-2 简化版）：废弃固定阶梯 INTERVALS，改为 SM-2 动态间隔——
 * `nextInterval(prevDays, ease) = (prevDays * ease).roundToInt().coerceIn(1, 30)`；TermState 增 `ease`
 * （默认 2.5，认识 +0.1 封顶 2.5、忘了 -0.2 下限 1.3）。（本轮曾恢复 v4 连击模型，v7 已废除，见下）
 * 毕业判定（days >= 15）、清债模式、池型频道零写入、f30 观测均不动。
 * v6（09-20，design.md §11.1）：新增**收藏分区**——`CHANNEL_FAV = "fav"` 是**池型频道**（池 = 收藏词
 * weightedShuffle、浏览卡、零写入、无完成卡）；收藏 = 词 id 有序集合（LinkedHashSet），存主 state JSON
 * 顶层 `"favorites"`（旧数据缺省空集，无需迁移），**与学习状态（TermState / DayState）完全独立**。
 * 每日配额改由构造函数注入 `quotaProvider`（MainActivity 组装 `{ appSettings.quota() }`）——
 * **只在 buildQueue 重建队列时读**，当日队列冻结语义不变（设置页改配额次日生效）。
 * v6 R14（09-20）：①忘了的减半改为**延迟兑现**——answerCard 写 {days=1, restoreTo=原days/2}，
 * 次日必回池重考，答满连击才把 days 提到 max(nextInterval, restoreTo)（Anki new-interval=50% 思路；
 * 修复 R13「忘了词当天未答满连击会凭空消失减半天数」的回归）。②新词配额独立注入
 * `newQuotaProvider`（设置页「每天学几个新词」）：新词速率恒定、不被到期复习挤占，
 * 复习用 quota 剩余额度，总量仍受 quota 约束。
 * v7（09-20，design.md §12.1）：**直接考试**——废除教读→追加考核两段机制，每日队列每词一张任务卡，
 * **每次作答即写间隔层**（首答定调度，prd v7 第 1 条；v6 R12 的连击满 3 才写
 * 在每词一卡前提下不可达，随追加考核卡机制一并废弃）。删除教读标记与追加考核卡链路
 * （DayState.seen 一并）；DailyQueue 增 **confirmed**（总结卡确认标记，持久化，缺省 false）+ `markConfirmed()`。
 * SM-2 动态间隔、ease、restoreTo、lapses 排序、每日升一级闸门、f30 观测、buildQueue 配额与交错全不动。
 * v8（09-20，design.md §13）：**连击回归 + 打散插入 + 全显拼音 + 封顶分级**——
 * ①恢复 v4 连击语义（当日 3 次「认识」才移出当日队列，任意「忘了」清零），但插入方式从 v4 的队尾
 * 追加改为**最小间隔插入**：`answerCard` 把重复卡插到源卡下标 + MIN_GAP + 1（钳到队尾），
 * queue/modes/answered/cardIds 四组数组在同一事务内同步插入 + persist；
 * ②counts 恢复连击计数（0–3），v7 写的 1/0 按计数自然沿用（隔天作废，无需迁移）；新增 `comboCount(id)`；
 * ③f30 观测去重：每词每日只计**首次**作答（`id in counts` 守卫，防连击相关观测膨胀）；
 * ④间隔封顶分级：成熟词（ease=2.5 且 lapses=0）60 天（INTERVAL_CAP_MATURE）、普通词 30 天
 * （INTERVAL_CAP_NORMAL）——nextInterval 增 lapses 参数；
 * ⑤防泄题契约废除（v3 以来）——所有任务卡全显示拼音+提示（UI 层改动，见 AppRoot / TermCard）；
 * 当前规则覆盖上述历史口径：连击未满 3 次不推进跨日间隔，第 3 次认识才提交状态；同日重学不兑现 restoreTo。
 * v9（09-20，prd v9 / design.md §14）：**推荐范围收窄 + 常用词特例**——构造函数注入 `recScenesProvider`
 * （MainActivity 传 settings.visibleScenes()），推荐频道（每日任务 + 温故流）只抽**可见分区**的词，
 * 隐藏分区的词两个入口都抽不到（prd v9 第 3 条）；常用词分区（CHANNEL_COMMON = "daily"，v15 清理后 19 条，
 * v16 日用洗护补充后 37 条，v17 通用词回流后 72 条，v18 收纳「天气日历」整区与 7 条例外词后 103 条，**v21 按原则增删（删 3 加 22）后 122 条**：
 * 公共标识字 + 跨场景通用词 + 居家消耗品包装词 + 时间/天气词 + 身份证 + 快递核心词）
 * **不进 AppSettings 显隐管理（不可开关）但恒入推荐范围**（内容源特例，
 * prd v9 第 4 条 QYJ 拍板——默认场景分区全隐藏时，推荐仍靠常用词有内容）。
 * ⚠️ v17：daily **同时是频道栏的第三个固定频道**（默认显示、不可移除）；作为频道它是**池型**
 * （channel != CHANNEL_DAILY 自然落入 §5.7 的池型分支，浏览零写入），其词仍在推荐范围内。
 * UI 侧 v9：任务卡停稳静默（点按听读、自评）、
 * 完成卡上滑转浏览（取代 v7 点确认，AppRoot.enterBrowse）——见 AppRoot / DoneCard。
 * v16（09-21，QYJ 拍板）：**战果量纲 + 统计范围 + 清债日新词**三处口径调整 ——
 * ①完成卡与设置页统一为「认识 → 去重词数、忘了 → 次数」（见 [todayKnownWords] / [todayTaskWordCount]）：
 *   连击满 3 才移出队列，旧的作答次数口径会把 10 个词报成「认识了 30 次」；
 * ②[studyStats] 的分子分母都收窄到**当前可学范围**（常用词 + 可见分区）：隐藏分区里已学过的词不计入，
 *   换来分子分母同口径、永不出现 X > N；
 * ③**清债日不再把新词配额归零**（[DEBT_QUOTA] 现在含新词）—— 设置页承诺「新词固定几个，
 *   不被复习挤掉」，v5 的实现让这句承诺在清债日整天不成立（该日约每周出现一次）。
 * v17（09-21，QYJ 拍板）：**分区合并 + 通用词回流 + daily 升为固定频道** ——
 * ①`SCENES` 由 13 个可管理分区降到 10（删 `bank` → 并入 `gov`、`medicine` → 并入 `hospital`、
 *   解散 `emergency` 分流到 phone/hospital/property/gov）；**本类的逻辑零改动** ——
 *   `scopeIds` / `isSceneGraduated` / `studyStats` / `poolIds` 全部按 `term.scene` 过滤，
 *   不含任何分区白名单（这正是「按 scene 而非按 id 前缀」的价值）；
 * ②「常用词」回流 35 条通用词（37 → 72 条），daily 由「推荐的内容源」升为**同时可浏览的固定频道**；
 * ③词库总量 565 → 550（去重删 15 条）。⚠️ **按 id 前缀做过滤/统计一律是 bug**（v17 起 id 前缀
 *   只表示首次归属）—— 见 WordBank.kt 文件头 id 契约。
 * v18（09-21，QYJ 拍板）：**分区大幅收敛，可管理分区 10 → 5** ——
 * ①整区移除 `weather`（24 条**全部**归入 `daily`）、`gov`（56 条删除，**唯留 `gov-2` 身份证**
 *   归入 daily）、`express`（30 条删除，留 快递/包裹/取件码/驿站/收件人/退货 **6 条**归入 daily）、
 *   `market`（25 条）与 `property`（32 条）**全部删除** —— 其中 property 的 4 条安全类词
 *   （灭火器/安全出口/电梯困人/电梯警铃，v17 刚从解散的 emergency 迁入）经 QYJ 确认**一并删除**；
 * ②词库 550 → **414**（删 136、迁 daily 31）；`daily` 由 72 涨到 **103 条**，成为全库最大分区；
 * ③**本类逻辑仍零改动** —— 分区集合来自 `SCENES`，本文件没有任何硬编码的分区清单。
 * v22（09-22，QYJ 拍板「纯 B：仅 SAF 导入」）：**自定义词库分区** —— 设置页导入 JSON 词库
 * （`CustomBank.kt`）即成一个普通场景分区。本类改动仅三处 `SCENES` → `allScenes()`
 * （recScenesProvider 缺省 / graduatedScenes / studyStats），词条来源 `STUDY_TERMS` 改为
 * 动态 getter（内置 + 自定义）后**签名与用法不变**——显隐、排序、毕业判定、推荐聚合、
 * 池型抽取对自定义分区全部按既有机制工作。删除词库只删文件：TermState/收藏按 id 保留
 * （鬼 id 容忍机制，重导同 id JSON 进度自动恢复）。
 */
class StudyRepository(
    context: Context,
    private val quotaProvider: () -> Int = { DAILY_POOL_QUOTA },
    private val newQuotaProvider: () -> Int = { DAILY_NEW_QUOTA },
    // v9 推荐范围注入（prd 第 3 条）：**可见分区** id 集合（MainActivity 传 settings.visibleScenes()）。
    // 推荐频道（每日任务 + 温故流）只抽这些分区的词；常用词分区（CHANNEL_COMMON）由 scopeIds
    // 特例恒入、不受本集合影响。与配额同语义：只在重建队列 / 重洗池时读，当日队列冻结不变。
    private val recScenesProvider: () -> Set<String> = { allScenes().map { it.id }.toSet() },
) {

    private val prefs = context.applicationContext
        .getSharedPreferences("study_state", Context.MODE_PRIVATE)

    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val termStates = linkedMapOf<String, TermState>()
    private val queues = linkedMapOf<String, DailyQueue>()
    private var day = DayState("", emptyMap())
    // v6 收藏：词 id **有序**集合（LinkedHashSet 保持收藏先后）；与学习状态独立，只随 toggleFavorite 变
    private val favoriteIds = linkedSetOf<String>()

    init {
        load()
    }

    /* ---------- 间隔层状态机（连击完成后写；与连击层并存） ---------- */

    fun today(): String = fmt.format(Date())

    /**
     * v16 当日「认识」的**去重词数**（完成卡与设置页的战果口径；持久口径：重启不清零，隔天随 DayState 作废）。
     * `counts` 的键集 = 当日作答过的词，值 ≥ 1 = 该词连击未被清零（答对过且此后没忘）——
     * 故本值 = 「今天答对过、目前还没忘掉」的词数，恒 ≤ 当日任务词数。
     * 与 `DayState.knownAnswers`（作答**次数**，连击期间每点一次「认识」都 +1）刻意区分：
     * v5 R7 起战果用的是次数口径，但连击满 3 才移出队列 ⇒ 10 个词的成果会显示成 30 次，
     * 与「今天学完了 N 个词」量纲不符，v16 改为词数（设置页与完成卡统一）。
     */
    fun todayKnownWords(): Int = currentDay().counts.count { it.value >= 1 }

    /** 当日「忘了」作答次数（不变；「忘了」是可重复发生的事件，保留次数口径） */
    fun todayForgot(): Int = currentDay().forgotAnswers

    /**
     * v16 当日任务区**去重词数**（完成卡「今天学完了 N 个词」的持久口径）。
     * 取自当日队列的 distinct——**不随 UI 层 `pages` 的增删变化**：v9 的 `enterBrowse()` 在用户
     * **到达完成卡的那一帧**就把全部任务卡移出 `pages`，若该数字由 pages 派生，完成卡会显示
     * 「今天学完了 0 个词」（当日重启直接进浏览模式同理：pages 里只剩完成卡与浏览页）。
     * `answerCard` 插入的是同 id 的重复卡 → distinct 后不影响本值。
     * 队列不存在（理论不可达：完成卡只在队列存在时出现）→ 0。
     */
    fun todayTaskWordCount(): Int = queues[CHANNEL_DAILY]?.queue?.distinct()?.size ?: 0

    /** v5 R11-4 f30 观测（只读）：答前 days == 30 的累计作答次数。
     *  落 SharedPreferences **独立 key**（不进主 state JSON——观测数据与学习状态解耦，清观测不清学习进度）。 */
    fun f30Total(): Int = prefs.getInt("f30_total", 0)

    /** v5 R11-4 f30 观测（只读）：答前 days == 30 且「忘了」的累计次数 */
    fun f30Fail(): Int = prefs.getInt("f30_fail", 0)

    /**
     * 在仓库内原子提交一次作答：更新词状态、当日连击、队列卡状态及重复卡后只持久化一次。
     * `cardId` 是卡实例身份，避免重复卡插入后使用旧下标误答其他卡；已提交卡会直接返回 null，
     * 同时防止快速双击造成连击被重复累计。
     */
    fun answerCard(cardId: String, known: Boolean): AnswerResult? {
        val rawQueue = queues[CHANNEL_DAILY] ?: return null
        if (rawQueue.date != today()) return null
        val q = normalizeQueue(rawQueue)
        val index = q.cardIds.indexOf(cardId)
        if (index !in q.queue.indices || q.answered[index]) return null

        val id = q.queue[index]
        val d = currentDay()
        val cur = termStates[id]
        val beforeDays = cur?.days
        val firstAnswer = id !in d.counts
        val streakUpdate = nextStreakUpdate()
        var count = 0
        var upgraded = false
        var daysAfter = cur?.days ?: 0

        if (known) {
            count = min(DAILY_COMBO_TARGET, (d.counts[id] ?: 0) + 1)
            when {
                // 新词先落一个 1 天学习状态；跨日升级仍要等下一次完整连击。
                cur == null -> {
                    termStates[id] = TermState(1, today(), 0, 2.5)
                    daysAfter = 1
                }
                // 只有第三次「认识」才提交跨日间隔。若当天中途退出，cur 保持原到期状态。
                count >= DAILY_COMBO_TARGET && cur.lastSeen != today() -> {
                    // 忘了的重考词新间隔不低于 restoreTo，兑现后清零。
                    val ni = max(nextInterval(cur.days, cur.ease, cur.lapses), cur.restoreTo)
                    val newEase = min(2.5, cur.ease + 0.1)
                    termStates[id] = TermState(ni, today(), cur.lapses, newEase, 0)
                    upgraded = true
                    daysAfter = ni
                }
                // 同日第 2/3 次认识，以及忘了后的同日重学，只推进连击。
            }
            day = d.copy(counts = d.counts + (id to count), knownAnswers = d.knownAnswers + 1)
        } else {
            val newEase = max(1.3, (cur?.ease ?: 2.5) - 0.2)
            // days=1（次日必回池）+ restoreTo 记减半目标；cur==null 视为 days=1。
            val restore = max(1, (cur?.days ?: 1) / 2)
            termStates[id] = TermState(1, today(), (cur?.lapses ?: 0) + 1, newEase, restore)
            day = d.copy(counts = d.counts + (id to 0), forgotAnswers = d.forgotAnswers + 1)
        }

        val answered = q.answered.toMutableList()
        answered[index] = true
        var updatedQueue = q.copy(answered = answered)
        var repeatCardId: String? = null
        var insertBeforeCardId: String? = null

        // 忘了，或认识连击未满：在同一事务里插入下一张重复卡。
        // v21.1（QYJ 2026-09-22 反馈）：插入位置**随机化**——原固定「源卡下标 + 3」让队列开头
        // 几个词的重复卡总是紧跟其后，形成「先在三个词循环」的体感；现在在下界（源卡后至少隔
        // [MIN_GAP] 张）与队列末尾之间随机取点，重复卡被打散进当日剩余队列。UI 侧
        // （AppRoot.insertRepeatCard）按 insertBeforeCardId 的 cardId 定位，不依赖固定偏移。
        if (!known || count < DAILY_COMBO_TARGET) {
            val earliest = index + 1 + MIN_GAP
            // CI 修复（d1299c3 失败教训）：IntRange.random() 扩展在 kotlin.random 包——该包**不在**
            // Kotlin 默认导入清单，须显式 import；此处改用本文件已 import 的 Random.nextInt(from, until)
            //（左闭右开，取 [earliest, size-1]；earliest < size 由上方分支保证，满足 from < until 前置条件）。
            val insertAt = if (earliest < q.queue.size) Random.nextInt(earliest, q.queue.size) else q.queue.size
            val newRepeatCardId = newCardId()
            repeatCardId = newRepeatCardId
            insertBeforeCardId = q.cardIds.getOrNull(insertAt)
            updatedQueue = updatedQueue.copy(
                queue = updatedQueue.queue.toMutableList().apply { add(insertAt, id) },
                modes = updatedQueue.modes.toMutableList().apply { add(insertAt, MODE_REVIEW) },
                answered = updatedQueue.answered.toMutableList().apply { add(insertAt, false) },
                cardIds = updatedQueue.cardIds.toMutableList().apply { add(insertAt, newRepeatCardId) },
            )
        }

        queues[CHANNEL_DAILY] = updatedQueue
        val f30TotalAfter = if (firstAnswer && beforeDays == 30) f30Total() + 1 else null
        val f30FailAfter = if (firstAnswer && beforeDays == 30 && !known) f30Fail() + 1 else null
        persist(
            f30TotalAfter = f30TotalAfter,
            f30FailAfter = f30FailAfter,
            streakUpdate = streakUpdate,
        )
        return AnswerResult(count, upgraded, daysAfter, repeatCardId, insertBeforeCardId)
    }

    /** 当日连击计数（0–3，供统计或调试读取）。 */
    fun comboCount(id: String): Int = currentDay().counts[id] ?: 0

    /* ---------- 到期与间隔计算（v6 R12：SM-2 简化版；v8：封顶分级 30/60） ---------- */

    /**
     * SM-2 简化版动态间隔：`days = (prevDays * ease).roundToInt().coerceIn(1, cap)`。
     * - 认识升级：ease 在 [1.3, 2.5]，间隔随 ease 增长；
     * - 忘了：days 归 1 + restoreTo 记减半目标（answerCard，重考「认识」时兑现）、ease 降 0.2；
     * - **v8 封顶分级**（design.md §13.1）：成熟词（ease 已到上限 2.5 且 lapses == 0）cap =
     *   [INTERVAL_CAP_MATURE]（60），普通词 cap = [INTERVAL_CAP_NORMAL]（30）——
     *   预期稳态日到期量 ~12/天 → ~7/天，清债日振荡消失；f30 实测遗忘率不达标时可下调
     *   INTERVAL_CAP_MATURE（改常量即可，45–60 区间为 QYJ 授权范围）。
     * 旧固定阶梯 INTERVALS = [1,3,7,15,30] 已废弃为注释参考——SM-2 让 ease 低的词间隔短、ease 高的词间隔长，
     * 比固定阶梯更贴合个体记忆差异。
     */
    fun nextInterval(prevDays: Int, ease: Double, lapses: Int): Int {
        val cap = if (ease >= 2.5 && lapses == 0) INTERVAL_CAP_MATURE else INTERVAL_CAP_NORMAL
        return (prevDays * ease).roundToInt().coerceIn(1, cap)
    }

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
     * 毕业词继续升到 30 后「学完」标记保持不回退）。
     * 达成 = 多轮**不同日**完成连击后的「认识」升级（1→~3→~7→~15，SM-2 动态间隔；
     * 任何一处「忘了」days 打回 1 → 即时退出完成状态（可逆）。
     * 空分区（`rec` 自身、或有词无条目的分区）不算完成。
     * R10：间隔层的唯一写入来源是**每日任务的复习卡作答**——在分区/温故流里浏览不写任何状态，
     * 故分区「学完」标记只能靠每日任务推进（这是 D4 的已接受代价，见 prd R10）。
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
        allScenes().map { it.id }.filter { it != CHANNEL_DAILY && isSceneGraduated(it) }.toSet()

    /* ---------- 收藏（v6，design.md §11.1：与学习状态完全独立，只写 favorites） ---------- */

    /** 收藏词 id 有序列表（LinkedHashSet 保序：收藏先后即收藏频道首次出池顺序的原始输入） */
    fun favorites(): List<String> = favoriteIds.toList()

    fun isFavorite(id: String): Boolean = id in favoriteIds

    /**
     * 切换收藏状态，返回**切换后**的新状态（true = 已收藏）。写后 persist。
     * 刻意**不碰** TermState / DayState / 队列——收藏分区的零写入契约（prd v6 第 3 条）不被破坏。
     */
    fun toggleFavorite(id: String): Boolean {
        val added = if (id in favoriteIds) {
            favoriteIds.remove(id)
            false
        } else {
            favoriteIds.add(id)
            true
        }
        persist()
        return added
    }

    /* ---------- 学习统计（v14，design.md §16：只读快照 + streak 旁路记账） ---------- */

    /**
     * 连续学习天数的**显示口径**：最后学习日是今天或昨天 → 返回存量计数
     * （昨天学过、今天还没学时 streak 仍然「活着」，今天的第一次作答会 +1）；
     * 断 ≥ 2 天 → 0（prd v14 验收：隔天不作答断掉重计）。
     * 旧安装无 key → 0，不回填历史（prd v14：显示 0 不崩溃）。
     */
    fun studyStreak(): Int {
        val last = prefs.getString(KEY_STREAK_LAST_DATE, null) ?: return 0
        val count = prefs.getInt(KEY_STREAK, 0)
        if (count <= 0) return 0
        val t = today()
        return if (last == t || last == dayBefore(t)) count else 0
    }

    /** today 的前一天（yyyy-MM-dd）；解析失败返回 null（streak 视为断掉，fail-closed 到 0/重计） */
    private fun dayBefore(today: String): String? = runCatching {
        val d = fmt.parse(today) ?: return null
        fmt.format(Date(d.time - DAY_MS))
    }.getOrNull()

    /** 作答事务需要一并提交的连续学习天数变更；当天已记账时返回 null。 */
    private data class StreakUpdate(val count: Int, val lastDate: String)

    /** 计算连续学习天数，不在这里写库，避免一次作答产生多个持久化步骤。 */
    private fun nextStreakUpdate(): StreakUpdate? {
        val t = today()
        if (prefs.getString(KEY_STREAK_LAST_DATE, null) == t) return null
        val last = prefs.getString(KEY_STREAK_LAST_DATE, null)
        val next = if (last != null && last == dayBefore(t)) prefs.getInt(KEY_STREAK, 0) + 1 else 1
        return StreakUpdate(next, t)
    }

    /**
     * 学习统计只读快照（v14）：设置页「学习统计」节的全部数据，一次性构建（StudyStats KDoc）。
     * 全函数**零写入**；分区进度以 STUDY_TERMS 为口径（剔除词库已删的鬼 id）。
     * v16：`todayKnownWords` 走去重词数口径（见 [todayKnownWords]），不再用 knownAnswers 次数。
     *
     * v16 **总览范围口径 = 当前可学的词**（QYJ 2026-09-21 拍板）：分子分母都只算
     * [scopeIds]`(CHANNEL_DAILY)`（常用词 + **可见分区**）——与调度器的推荐范围同一函数，
     * 隐藏分区后分母不再包含那些词。原口径的分母是全词库（v15 口径，549 条），而缺省全隐藏时用户实际
     * 只学得到常用词那几十条，"已学 3 / 549" 对用户没有意义。
     * **隐藏分区里已学过的词也一并不计**（数字会随隐藏而下降）——这是 QYJ 选定的代价，
     * 换来分子分母同口径、永不出现 X > N。
     * 调用方须在**显隐变化后重取**本快照（分母依赖可见分区）。
     *
     * 分区进度列表 = **可管理分区**（排除 rec / fav / daily，与 `AppSettings.manageableIds` 同一判据）：
     * 设置页把这些行与显隐开关合并成一张卡，常用词不可显隐、不在该卡出现，
     * 但它的词**计入总览分母**（恒入推荐范围）。
     */
    fun studyStats(): StudyStats {
        // 当前可学范围（与 buildQueue / poolIds 的推荐范围同源，口径不会漂）
        val currentIds = scopeIds(CHANNEL_DAILY)
        val learnedIds = currentIds.filter { termStates.containsKey(it) }
        val scenes = allScenes()
            .filter { it.id != CHANNEL_DAILY && it.id != CHANNEL_FAV && it.id != CHANNEL_COMMON }
            .map { scene ->
                val ids = STUDY_TERMS.filter { it.scene == scene.id }
                SceneProgress(scene, ids.count { termStates.containsKey(it.id) }, ids.size)
            }
        return StudyStats(
            totalWords = currentIds.size,
            learned = learnedIds.size,
            graduated = learnedIds.count { (termStates[it]?.days ?: 0) >= GRADUATED_DAYS },
            favorites = favoriteIds.size,
            todayKnownWords = todayKnownWords(),
            todayForgot = todayForgot(),
            streak = studyStreak(),
            f30Total = f30Total(),
            f30Fail = f30Fail(),
            scenes = scenes,
        )
    }

    /* ---------- 每日队列调度器（design.md §9.3） ---------- */

    /**
     * 范围（每日任务队列 + 池型频道抽取共用）：
     * - 每日任务频道（`rec`）= **可见分区**的词（v9 `recScenesProvider`，隐藏分区排除——prd 第 3 条）
     *   + 常用词分区（v9 `CHANNEL_COMMON` **恒入**——不进显隐管理、只作推荐内容源，prd 第 4 条特例）；
     * - 收藏频道（`fav`，v6）= 收藏词 id（**有序**，不在这里洗牌——洗牌交给 poolIds 的 weightedShuffle）；
     * - 场景频道（池型）= 该场景词条。
     */
    private fun scopeIds(channel: String): List<String> =
        when (channel) {
            CHANNEL_DAILY -> STUDY_TERMS
                .filter { it.scene == CHANNEL_COMMON || it.scene in recScenesProvider() }
                .map { it.id }
            CHANNEL_FAV -> favoriteIds.toList()
            else -> STUDY_TERMS.filter { it.scene == channel }.map { it.id }
        }

    /**
     * 推荐范围当前是否为空（2026-09-23 复审修复 #2）——**可见范围**口径，供空态判别：
     * 全部分区隐藏时 `STUDY_TERMS` 仍非空，但 rec 一张卡都抽不到，空态判据若用
     * `STUDY_TERMS.isEmpty()` 会落到总结卡「今日任务完成 0 个词」。内部走 `scopeIds`
     * 的同一套特例（CHANNEL_COMMON 恒入），口径与 buildQueue 完全一致。
     */
    fun recScopeEmpty(): Boolean = scopeIds(CHANNEL_DAILY).isEmpty()

    /**
     * 每日任务频道当日队列生成（design.md §9.3；R10 后**只有队列型频道**需要队列）：
     * - learned = 全部已学词，双键排序：lapses 降序优先（忘词先见）→ 到期日升序（先清旧债）；
     * - due     = learned 中到期的词（isDue）；
     * - news    = 未学词洗牌；
     * - pool：due ≥ DEBT_THRESHOLD（20）→ 清债模式；总量按用户配额增加最多 5 张（默认 10 → 15），
     *   同时**新词配额照给**（不再整天学不到新词；低配额用户不会突然跳到 15 张），
     *   见该分支处注释）；v5 原口径是 `due.take(15)` 全复习、不补新词；
     *   否则 interleave(due.take(quota - newsPool.size), newsPool, quota)（R11-2 **交错**：格号 % 3 == 1
     *   优先放新词，news 非空时前 3 张内必有新词——做不完的用户也见得到新词；当日冻结由 ensureQueue
     *   持久化保证）；
     *   v6 R14 新词配额独立：newsPool = news.take(min(quotaNew, quota))——新词速率恒定、不被到期
     *   复习挤占；复习用剩余额度（quota - newsPool.size），总量仍以 quota 为上限（适老化防过载）；
     *   v6：配额改读 `quotaProvider()` / `newQuotaProvider()`（设置页两项，MainActivity 注入）——
     *   只在**重建队列**这一刻读，当日队列冻结不变、次日生效；
     * - queue 按 pool 原序（每词一次），modes 按有无 TermState 标注，answered 全 false；
     * - pool 为空（全部已学且都没到期）→ 队列只有完成卡，直接进温故流（prd：一个也没有当天直接自由刷）
     *   ——**这是合法状态，不要「修」它**。
     * R10：R9 的 `else -> learned + news`（分区全量入队）分支已删除——分区是**池型**频道（§5.7），
     * 不再生成队列，保留那个分支只会是一段永远走不到、却看起来仍在服务分区的死代码。
     * v8（design.md §13.1）：首排仍**每词恰好一张卡**（无教读卡）；连击未满 3 的重复卡不在首排——
     * 由 `answerCard` 在作答后**运行时**插入（打散，prd v8 第 1 条）。只有连击完成后才写入跨日间隔，
     * 当日没答满连击的到期词保持到期 → 次日自动回池（遗留词机制，由 isDue 保证）。
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
        val quota = quotaProvider().coerceAtLeast(0)
        // v6 R14 新词配额独立：新词速率恒定 = quotaNew（不被到期复习挤占，主流「新学/复习分开配置」），
        // 复习用剩余额度；新词上限同时受总量约束（quota < quotaNew 时取小，防总量爆表）
        val newsQuota = newQuotaProvider().coerceAtLeast(0)
        val newsPool = news.take(minOf(newsQuota, quota))
        // v16：清债日**不再把新词配额归零**。设置页承诺「新词固定几个，不被复习挤掉」，
        // 而 v5 的实现是 `due.take(DEBT_QUOTA)`（15 张全复习、一个新词都不排）—— 那句承诺在清债日
        // 整天不成立。清债日并不罕见：复习槽位 = quota - newQuota（默认 5/天），已学约 300 词时
        // 每天自然到期 ~7 个 → 净积压 ~2 个 → 约 10 天攒到 DEBT_THRESHOLD(20) → 清债日约每周一次。
        // 清债总量按用户配额计算：默认 10 → 15；设置 3/5 时分别为 8/10，设置 15/20 时不降级。
        // 这样保留清债加速，又不让「每日学习词数量」在清债日失去意义。
        val debtQuota = if (quota == 0) 0
        else max(quota, min(quota + (DEBT_QUOTA - DAILY_POOL_QUOTA), DEBT_QUOTA))
        // v21.1（QYJ 2026-09-22 反馈）：取出的复习词在入队前**洗牌**——挑选仍按 lapses 降序 /
        // 到期日升序 take（忘得最多、欠得最久的词优先占复习槽位），但取出的这一批当天以随机顺序
        // 呈现。原固定排序让每天开头都是同一批难词，与重复卡插入叠加形成「先在三个词循环」的体感。
        // 新词交错编排（interleave 的「前 3 张内必有新词」保证）不受影响。
        val pool = if (due.size >= DEBT_THRESHOLD)
            interleave(due.take(max(0, debtQuota - newsPool.size)).shuffled(), newsPool, debtQuota)
        else
            interleave(due.take(max(0, quota - newsPool.size)).shuffled(), newsPool, quota)

        val queue = ArrayList<String>(pool.size)
        val modes = ArrayList<String>(pool.size)
        val answered = ArrayList<Boolean>(pool.size)
        val cardIds = ArrayList<String>(pool.size)
        for (id in pool) {
            queue.add(id)
            modes.add(if (termStates[id] == null) MODE_NEW else MODE_REVIEW)
            answered.add(false)
            cardIds.add(newCardId())
        }
        return DailyQueue(
            today(), CHANNEL_DAILY, queue, modes, answered, 0,
            confirmed = false,
            cardIds = cardIds,
        )
    }

    /**
     * R11-2 交错编排（design.md §3）：格号 % 3 == 1 的位置优先放新词，其余位置优先到期词；
     * 任一池空则另一池顶上，直到配额或两池用尽。保证 news 非空时队列前 3 张内必有新词
     * （做不完的用户也见得到新词，新词通道不再因 due 垫底被关闭）；两池皆空返回空列表
     * （合法状态，见 buildQueue KDoc）。**v16：清债日也走本函数** —— 由 buildQueue 传入按用户配额
     * 计算的 debtQuota；v5 原口径是清债日 `due.take(15)` 全复习、**绕过本函数**，代价是新词配额被整天归零
     * （与设置页「新词固定几个，不被复习挤掉」的承诺矛盾）。
     */
    private fun interleave(due: List<String>, news: List<String>, quota: Int): List<String> {
        val out = ArrayList<String>(quota)
        var d = 0
        var n = 0
        while (out.size < quota && (d < due.size || n < news.size)) {
            val takeNews = (out.size % 3 == 1 && n < news.size) || d >= due.size
            if (takeNews) out.add(news[n++]) else out.add(due[d++])
        }
        return out
    }

    /**
     * 当日有效队列（**只有每日任务频道**，R10 §5.7）：date 相符直接复用（断点续刷 + 当日冻结），
     * 否则重建并持久化。
     * R10 简化：场景分区已不再有队列，故 R9 那条「空队列不复用」补丁一并删除——每日任务的
     * **空队列是合法状态**（pool 空 → 队列只有完成卡 → 直接温故流，prd 已定义），不要把它当异常修掉。
     */
    fun ensureQueue(): DailyQueue {
        val existing = queues[CHANNEL_DAILY]
        // 2026-09-23 复审修复（#1）：复用前校验词条仍然存在——删除词库 / 升级清空内置词库后，
        // 鬼 id 留在非空队列里会被装载时的 termById 静默丢弃（全丢则误显完成卡），须整体重建
        if (existing != null && existing.date == today() && !queueHasGhostIds(existing)) {
            val normalized = normalizeQueue(existing)
            if (normalized != existing) {
                queues[CHANNEL_DAILY] = normalized
                persist()
            }
            return normalized
        }
        val fresh = buildQueue()
        queues[CHANNEL_DAILY] = fresh
        persist()
        return fresh
    }

    /**
     * 队列里的鬼 id（2026-09-23 复审修复 #1）：任一词条 id 不在当前 `STUDY_TERMS` 里。
     * 刻意只查**存在性**、不查显隐——分区被隐藏时当日队列照常冻结（与配额同语义，
     * 只在重建时读 recScenesProvider），但词都没了的 id 不可复用。
     */
    private fun queueHasGhostIds(q: DailyQueue): Boolean {
        val known = STUDY_TERMS.map { it.id }.toHashSet()
        return q.queue.any { it !in known }
    }

    /**
     * v7（design.md §12.1）：总结卡「确认」落库——queues[CHANNEL_DAILY] 置 confirmed=true + persist。
     * 幂等（已 true 直接返回）；确认后当日重启 / 切频道回来由 AppRoot 装载分支直接进浏览模式
     * （browseMode），不重放考试卡与总结卡。无队列时静默返回（理论不可达：总结卡只在队列存在时出现）。
     */
    fun markConfirmed() {
        val q = queues[CHANNEL_DAILY] ?: return
        if (q.confirmed) return
        queues[CHANNEL_DAILY] = q.copy(confirmed = true)
        persist()
    }

    /**
     * v23：导入/删除自定义词库后由 AppRoot 调用——作废**不再合法**的当日队列，下次 ensureQueue
     * 按新词库范围重建。两类失效（2026-09-23 复审修复 #1 收窄了原「只作废空队列」的口径）：
     * - 空队列：空库首导若不作废，「当日队列冻结」口径会让新词拖到明天才出现（用户导入完
     *   看到的还是空引导态）；
     * - 含鬼 id 的队列：词条已不在当前 `STUDY_TERMS`（删除词库 / 升级清空内置词库后的残留），
     *   装载时会被 termById 丢弃——全丢则误显完成卡，部分丢则当天任务悄悄缩水，故发现即整队
     *   作废（词库已变，旧队列本就不可续）。
     * 非空且全部有效 → 不动（当日冻结语义不破）。
     */
    fun invalidateStaleQueue() {
        val q = queues[CHANNEL_DAILY] ?: return
        if (q.queue.isEmpty() || queueHasGhostIds(q)) {
            queues.remove(CHANNEL_DAILY)
            persist()
        }
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
     * 池型频道的抽取池（温故流与分区共用同一条追加逻辑，差别只在池的构成）。
     * v6 统一（2026-09-20 QYJ 拍板）：**三类池型频道全部走同一套加权随机抽卡**（`weightedShuffle`），
     * 温故流不再用等概率洗牌——「忘得越多、越久没看越容易被抽到」的算法对自由浏览同样成立，
     * 避免「每次都从头开始学」的固定顺序感（prd v6 抽卡统一决策）：
     * - **每日任务频道（`rec`）的温故流** = 该范围**已学词**加权随机——
     *   有 TermState 即已学（含毕业词）；当日考试未作答的词无 TermState，不入池；
     * - **场景分区** = 该区**全部词**（含未学词）加权随机（D3/D5）——未学词因此也能被浏览，
     *   但浏览不写状态、不算学会（未学词走 `poolWeight` 的固定中等权重分支）；
     * - **收藏频道（`fav`，v6）** = 收藏词加权随机（同一套加权逻辑，复用 else 分支）。
     *   ⚠️ 收藏池**可能为空**（没有任何收藏）→ 调用方（AppRoot）拿不到卡时插引导页；
     *   场景分区池必非空（导入校验 ≥1 条/库）；**零词库时全部池型频道都空** → 引导页（v23）。
     */
    fun poolIds(channel: String): List<String> {
        // 池的构成差异保留（统一的是抽卡**算法**，不是池的范围）：
        // rec 温故流 = 仅**已学词**（未学词的入口唯一化在每日任务新词卡，D4）；分区/收藏 = 全部词。
        val ids = if (channel == CHANNEL_DAILY) scopeIds(channel).filter { termStates[it] != null }
                  else scopeIds(channel)
        return weightedShuffle(ids)
    }

    /** 加权随机排列（Efraimidis–Spirakis 指数键）：key = -ln(u)/w，升序即无放回加权抽样。
     *  选它而不是「按权重轮盘逐个抽」：一趟 `sortedBy` 完成（O(n log n)，n = 已导入词数）且边界更少。 */
    private fun weightedShuffle(ids: List<String>): List<String> {
        val rnd = Random.Default
        return ids.map { id ->
            val w = poolWeight(id)
            val u = 1.0 - rnd.nextDouble()   // u ∈ (0,1]：nextDouble() 返回 0 时 ln(0) → +Inf
            id to (-ln(u) / w)
        }.sortedBy { it.second }.map { it.first }
    }

    /**
     * 池型频道抽词权重（**只读**：忘得越多、越久没看 → 越容易被抽到；未学词取固定中等权重）。
     * v6 起温故流/分区/收藏三类池型频道共用此权重（抽卡算法统一，见 `poolIds` KDoc）：
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

    /* ---------- 连击层当日状态（v8：连击计数 0–3，隔天作废；design.md §13.1） ---------- */

    /** 当日有效 DayState：date 与今天不符（隔天/首次）则整体作废重建 */
    private fun currentDay(): DayState {
        if (day.date != today()) day = DayState(today(), emptyMap())
        return day
    }

    /** 为新卡生成不会因队列插入而变化的实例 id。 */
    private fun newCardId(): String = UUID.randomUUID().toString()

    /** 为旧版本队列补齐卡实例 id，并修正三组并行数组长度。 */
    private fun normalizeQueue(q: DailyQueue): DailyQueue {
        val ids = ArrayList<String>(q.queue.size)
        val used = mutableSetOf<String>()
        q.queue.forEachIndexed { index, termId ->
            val stored = q.cardIds.getOrNull(index)
            var cardId: String? = if (!stored.isNullOrBlank() && used.add(stored)) stored else null
            if (cardId == null) {
                var fallback = "legacy-${q.date}-$index-$termId"
                while (!used.add(fallback)) fallback = "$fallback-${UUID.randomUUID()}"
                cardId = fallback
            }
            ids.add(requireNotNull(cardId))
        }

        val modes = q.modes.take(q.queue.size).toMutableList()
        val answered = q.answered.take(q.queue.size).toMutableList()
        while (modes.size < q.queue.size) modes.add(MODE_REVIEW)
        while (answered.size < q.queue.size) answered.add(false)
        return q.copy(modes = modes, answered = answered, cardIds = ids)
    }

    /* ---------- 持久化（读写对称） ---------- */

    /** 持久化主状态，并在作答事务中一并提交统计旁路数据。 */
    private fun persist(
        f30TotalAfter: Int? = null,
        f30FailAfter: Int? = null,
        streakUpdate: StreakUpdate? = null,
    ) {
        runCatching {
            val obj = JSONObject()
            val ts = JSONObject()
            termStates.forEach { (k, st) ->
                ts.put(k, JSONObject()
                    .put("days", st.days)
                    .put("lastSeen", st.lastSeen)
                    .put("lapses", st.lapses)
                    .put("ease", st.ease)       // v6 R12 SM-2：难度系数，旧数据缺省 2.5
                    .put("restoreTo", st.restoreTo))   // v6 R14：忘了的减半目标，旧数据缺省 0
            }
            obj.put("terms", ts)
            val d = currentDay()
            obj.put(
                "day",
                JSONObject()
                    .put("date", d.date)
                    .put("counts", JSONObject().apply { d.counts.forEach { (k, v) -> put(k, v) } })
                    // v7：seen 键已废除（教读机制删除），persist 不再写；旧 JSON 的 seen 键 load 时忽略
                    // v5 R7 当日战果（读写对称；旧版本读到多余 key 无害）
                    .put("knownAnswers", d.knownAnswers)
                    .put("forgotAnswers", d.forgotAnswers),
            )
            val qs = JSONObject()
            queues.toMap().forEach { (ch, rawQueue) ->
                val q = normalizeQueue(rawQueue)
                if (q != rawQueue) queues[ch] = q
                qs.put(
                    ch,
                    JSONObject()
                        .put("date", q.date)
                        .put("queue", JSONArray(q.queue))
                        .put("modes", JSONArray(q.modes))
                        .put("answered", JSONArray(q.answered))
                        .put("cardIds", JSONArray(q.cardIds))
                        .put("position", q.position)
                        // v7 总结卡确认标记（旧版本读到多余 key 无害）
                        .put("confirmed", q.confirmed),
                )
            }
            obj.put("queues", qs)
            // v6 收藏：顶层 "favorites" 有序数组（LinkedHashSet 保序）；与学习状态独立
            obj.put("favorites", JSONArray(favoriteIds.toList()))
            val editor = prefs.edit().putString("state", obj.toString())
            f30TotalAfter?.let { editor.putInt("f30_total", it) }
            f30FailAfter?.let { editor.putInt("f30_fail", it) }
            streakUpdate?.let {
                editor.putInt(KEY_STREAK, it.count)
                    .putString(KEY_STREAK_LAST_DATE, it.lastDate)
            }
            editor.apply()
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
                    // v5：旧 JSON 无 "lapses" 键缺省 0（无遗忘史，读写对称）；
                    // v6 R12：旧 JSON 无 "ease" 键缺省 2.5（SM-2 初始值，读写对称）；
                    // v6 R14：旧 JSON 无 "restoreTo" 键缺省 0（无待兑现减半目标，读写对称）
                    termStates[k] = TermState(
                        s.optInt("days", 1),
                        s.optString("lastSeen"),
                        s.optInt("lapses", 0),
                        s.optDouble("ease", 2.5),
                        s.optInt("restoreTo", 0),
                    )
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
                    // v7：旧 JSON 的 "seen" 键**读时忽略**（教读机制已删除，读写不对称仅此一处，向前兼容）
                    // v5 R7 当日战果：旧 JSON 无这两键 → 缺省 0（无需迁移代码，读写对称）
                    day = DayState(
                        date,
                        counts,
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
                    val cardIds = mutableListOf<String>()
                    qj.optJSONArray("queue")?.let { ja ->
                        for (i in 0 until ja.length()) queue.add(ja.optString(i))
                    }
                    qj.optJSONArray("modes")?.let { ma ->
                        for (i in 0 until ma.length()) modes.add(ma.optString(i))
                    }
                    qj.optJSONArray("answered")?.let { aa ->
                        for (i in 0 until aa.length()) answered.add(aa.optBoolean(i))
                    }
                    qj.optJSONArray("cardIds")?.let { ca ->
                        for (i in 0 until ca.length()) cardIds.add(ca.optString(i))
                    }
                    // 读写对称兜底：modes/answered 与 queue 等长（旧数据缺省 REVIEW/未作答）
                    while (modes.size < queue.size) modes.add(MODE_REVIEW)
                    while (answered.size < queue.size) answered.add(false)
                    queues[ch] = normalizeQueue(DailyQueue(
                        qj.optString("date"),
                        ch,
                        queue,
                        modes,
                        answered,
                        qj.optInt("position", 0),
                        // v7 总结卡确认标记：旧 JSON 无 "confirmed" 键 → 缺省 false（读写对称）
                        qj.optBoolean("confirmed", false),
                        cardIds,
                    ))
                }
            }
            // v6 收藏：旧 JSON 无 "favorites" 键 → 缺省空集（无需迁移；读写对称）
            favoriteIds.clear()
            obj.optJSONArray("favorites")?.let { fa ->
                for (i in 0 until fa.length()) favoriteIds.add(fa.optString(i))
            }
        }
    }

    companion object {
        /** 队列卡形态标注（持久化用；与 UI 层 CardMode 对应；自由刷页不落库，由 UI 运行时追加） */
        const val MODE_NEW = "NEW"
        const val MODE_REVIEW = "REVIEW"

        /** 每日任务频道 id（v5 R9 / R10）：**队列型频道只有它**——有完成卡、有总结确认、有当日队列与断点；
         *  v7 起考试阶段（未确认总结卡）**锁滑**（userScrollEnabled=false）。
         *  其余频道（场景分区）+ 推荐频道温故流 = **池型**（运行时抽取、无限追加、不落库，
         *  design.md §5.7）。集中在此常量，避免各处硬编码字符串。 */
        const val CHANNEL_DAILY = "rec"

        /** v6 收藏频道 id：**池型频道**（池 = 收藏词 weightedShuffle、浏览卡、无限追加、零写入、
         *  无完成卡）。刻意**不进 SCENES**——否则会被当成可调度场景参与毕业判定
         *  （design.md §11.1）。 */
        const val CHANNEL_FAV = "fav"

        /**
         * v9 常用词分区 id（prd 第 4 条）：「日常生活常用词」默认分区——高频字词、非固定场景。
         * 在 SCENES 里（参与分区毕业判定），但**不进 AppSettings 显隐管理**（order/hidden 均不含
         * daily；不可开关、频道栏不出现，2026-09-20 口径细化）；它的词**恒入推荐范围**
         * （scopeIds 特例）——软件默认只有推荐 + 收藏两个频道时，推荐才有内容（QYJ 拍板：
         * 只作推荐内容源，用户不可手动开启/关闭）。
         */
        const val CHANNEL_COMMON = "daily"

        /** v8 连击目标（恢复 v4 语义，prd v8 第 1 条）：当日同一个词累计 3 次「认识」→ 移出当日队列；
         *  任意一次「忘了」→ 清零。v7 曾随追加机制一并删除，v8 恢复但重复卡改由 `answerCard`
         *  最小间隔插入（取代 v4 的队尾追加，见 [MIN_GAP]）。 */
        const val DAILY_COMBO_TARGET = 3

        /** v8 最小间隔（prd v8 第 1 条打散约束）：同词两张卡之间至少隔 2 张其他卡——
         *  「避免连续/紧邻出现」的最小满足（QYJ 授权口径，可调）。
         *  v21.1：插入点不再固定为源卡下标 + [MIN_GAP] + 1，而是在该下界与队列末尾之间
         *  **随机取点**（见 answerCard）——[MIN_GAP] 降级为打散约束的下界。 */
        const val MIN_GAP = 2

        /** 每日任务频道池配额（R11-2 后由 interleave 消费：格号 % 3 == 1 优先新词，直到配额）；
         *  池型频道不设配额（R10：分区 = 该区全部词，只是抽取顺序加权随机）。
         *  v6：buildQueue 改读构造注入的 quotaProvider（设置页「每日学习词数量」），本常量保留为
         *  quotaProvider 的**缺省值**（未注入时的兜底）。
         *  ⚠️ 容量观测记录（2026-09-20 起，QYJ 拍板「先不动、留记录」）：稳态持有量 ≈ 复习槽位(NEW=5) ×
         *  封顶间隔(30~60 天) = **150~300 条**，而词库 v21 为 452 条（超载 **1.5~3.0 倍**）。
         *  历史：v15 549 条 / 1.8~3.7；v16 565 条 / 1.9~3.8（daily 日用洗护净 +16）；v17 550 条 /
         *  1.8~3.7（去重删 15，但 daily 37→72）；v18 414 条 / 1.4~2.8（删 136、daily 72→103）——
         *  总量第一次明显回落，超载已接近「无债运转」的边界（仍需 quota 提到 14~22 才完全不超载）；
         *  **v21 452 条 / 1.5~3.0（删 8 加 46、daily 103→122）**——超载回升但仍优于 v15~v17；**v22 364 条 / 1.2~2.4（面馆分区退役 88 条）**——首度落在无债运转边界以内；
         *  **v23 内置 0 条（词库全面外置为 wordbanks/*.csv）**——容量口径改由**用户导入量**决定，
         *  观测记录对零内置形态失去参照意义，仅存历史。
         *  扩库不提配额 → 复习债累积 → 触发 DEBT_THRESHOLD=20 清债模式 → 新词长期学不进去。
         *  暂不动，待实机 f30 观测出现复习债征兆（清债频繁触发、新词多日不进队列）再议。
         *  依据：research/vocab/char_coverage_report.md 容量节（该文件已随 Trellis 卸载删除，
         *  仅存 git 历史 `9604d1b`）。 */
        const val DAILY_POOL_QUOTA = 10

        /** v6 R14 每日新词配额缺省值（newQuotaProvider 兜底；设置页「每天学几个新词」注入）——
         *  新词速率恒定，不被到期复习挤占（主流「新学/复习分开配置」的口径） */
        const val DAILY_NEW_QUOTA = 5

        /** v8 间隔封顶分级（prd v8 第 4 条）：普通词 30 天（v5 以来的口径不变）；
         *  成熟词（ease >= 2.5 且 lapses == 0）放宽至 60 天（45–60 区间 QYJ 授权内取上限——
         *  稳态负载 ~12/天 → ~7/天；f30 实测遗忘率不达标可下调）。判定与消费都在 nextInterval。 */
        const val INTERVAL_CAP_NORMAL = 30
        const val INTERVAL_CAP_MATURE = 60

        /** 分区毕业判定阈值（v5：固定 15，与 INTERVALS 封顶 30 显式解耦，不再跟随阶梯） */
        const val GRADUATED_DAYS = 15

        /** 推荐频道清债模式触发阈值：到期词 ≥ 20 时当天进入清债模式（v5）。
         *  v16：清债日**不再等同于「全复习、不补新词」**，新词配额照给 —— 见 [DEBT_QUOTA]。 */
        const val DEBT_THRESHOLD = 20

        /** 清债日的默认上限（默认配额 10 → 清债总量 15）；实际总量按用户配额计算：
         *  `max(baseQuota, min(baseQuota + 5, DEBT_QUOTA))`，并保留新词配额。
         *  因此低配额用户不会突然跳到 15 张，高配额用户也不会被清债上限反向降级。 */
        const val DEBT_QUOTA = 15

        /** 分区抽词权重：**未学词**的固定中等权重（R10 D5）——比刚复习过的词（1.0）更靠前、
         *  比忘过/久未复习的词靠后，让未学词也能被浏览到但不是压倒性优先（design.md §3.1） */
        const val SCENE_NEW_WEIGHT = 2.0

        /** 一天的毫秒数（到期日 = lastSeen + days * DAY_MS，原型口径） */
        const val DAY_MS = 24L * 60 * 60 * 1000

        /** v14 streak 持久化 key（独立 prefs，不进主 state JSON——与 f30 观测同模式，design.md §16.1） */
        private const val KEY_STREAK = "streak_count"
        private const val KEY_STREAK_LAST_DATE = "last_study_date"
    }
}
