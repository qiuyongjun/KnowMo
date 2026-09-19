# design.md — 学习复习调度优化 v5

> 延续 09-17-elderly-literacy-app/design.md §9（v4 连击 + 间隔双层模型）的迭代记录文体。
> v5 只动调度参数与排序、毕业判定、复习卡交互契约；双层状态机结构（连击层/间隔层/每日队列/自由刷）不变。
> ⚠️ **R11 修订（2026-09-19）**：升级判据从「当日满 3 连击」改为「首答定调度」，连击层（`DayState.counts`）降级为**当日已作答标记**——三层结构仍在，语义见 §5.8。

## 0. 问题定位（为什么改）

| # | 问题 | 根因 | 影响 |
|---|---|---|---|
| P0-1 | 到期债务必然堆积 | 370 词 × 15 天封顶 → 稳态每日到期 ≈25 > 配额 10 | 长期用户逾期队列只增不减，「忘了」率上升 → days 打回 1 → 债更多，恶性循环 |
| P0-2 | 「忘了」词排序反向 | markForgot 写 {days=1, lastSeen=今天} → 次日 dueTime 全池最新 → dueTime 升序排池尾 | 超额日 take(quota) 首先挤掉昨天刚忘的词——最需要复现的词反而见不到 |
| P1-1 | 复习卡点按泄题 | TermCard 主区域 clickable → onSpeakTerm 对所有形态生效 | 考试态未作答时一个 tap 听到「词+提示」，考回忆契约失效，连击虚高 |
| P1-2 | 完成卡与队列完成度不一致 | ① 完成卡与队尾绑定（`pages` = 队列卡 + Done），滑到底 ≡ 完成；② 上一轮补救口径只数「未作答复习卡」，漏掉「整天都是新词卡被快速甩过」——新词卡未停稳就不触发教读，该口径恒为 0；③ 第二轮补救「滑过即回收队尾」保证了前向可达，但机制**不可见**且**不终止**（回收无次数上限、未答完时 FREE 页不追加）| 一张卡没答也能听到「今日任务完成」；完成卡变成固定在队尾的装饰，而不是成绩；第二轮之后的问题形态变成**无限循环**——同一批卡反复出现，没有完成卡、进不去温故、也没有任何解释 |
| P1-3 | 分区被当成「第二个每日任务」 | 每个频道各有一套当日队列 + **一张完成卡**，文案一律「今日任务完成！」（一天最多能「完成」13 次）；场景频道沿用 `due + news` 全量入队。R7 改判后「不允许越过未处理的卡」在分区里**没有任何收尾页来终止它** | 进分区即约 120 张强制卡（15–20 分钟）；若分区按到期筛选，则刚毕业的分区（全部词 days ≥ 15 且未到期）pool 为空 → `pageCount == 0` **白屏**、可持续十几天 |
| P1-4 | 自主学习（温故流 + 分区）与每日任务共用「考试态」与「队列」模型 | 温故与分区沿用复习卡的考试交互（√/× 自评 + 防泄题），且作答**写间隔层**；分区还用 `DailyQueue`（`modes`/`answered`/`position`）表达一条**固定顺序**的队列 | ① 「自主学习」成了第二个考试流：老人想随便看看却被要求自评，随手一点就改写间隔层（√ 可提前升级 / × 打回 days=1，见 `learning-mechanism-review.md` C 项）；② 固定顺序 → 用户设想的「不固定次序」无法表达；③ 分区 pool 含 `news` = 绕开每日配额教新词，且次日全部到期抢配额（埋债）；④ R9 的「滑完停住 + 轻提示」与「自主学习没有限制」自相矛盾 |
| P0-5（R11） | 「满 3 连击才升级」+ `DayState` 隔天作废 = **死锁** | 做不完当日队列 → 每词 counts 永远 <3 → `TermState` 永不写 → 队列每天一模一样 | 做不完的用户（配额 10、老人每天几分钟——常态）**永远学不会任何词**（§5.8；`learning-optimization-proposals.md` P0-1） |
| P0-6（R11） | 新词垫底 | `(due + news).take(10)` 到期词全在前 + R7 前向拦截 | `due ≥ 5` 而用户只做前 5 张时新词永远轮不到 → 做不完的用户新词通道关闭（§5.8 R11-2） |

## 1. 决策

| 决策点 | 选择 | 理由 |
|---|---|---|
| 封顶间隔 | 1→3→7→15→**30** | 370/30 ≈ 12 词/天，配额 10 附近收支接近平衡；45 天对高龄学习者记忆维持偏激进 |
| 毕业门槛 | 固定 **15**（与 INTERVALS 解耦） | 🎓 毕业体验不变；15 之后升 30 只影响到期复核节奏 |
| 毕业判定 | `days >= GRADUATED_DAYS`（原为 `==`） | ⚠️ 若维持 `==`，毕业词升到 30 后 🎓 反而回退——本变更是 R4 的硬前提 |
| lapse 权重 | TermState 增 `lapses`，排序 `lapses 降序 → dueTime 升序` | 简单插队首方案会打乱「先清旧债」的次要目标，双键排序两者兼顾 |
| lapses 衰减 | 升级时 `lapses / 2` | 遗忘史随成功升级半衰，避免一次忘了永久高优先 |
| 清债模式 | due ≥ 20 → pool = due.take(15)、不补新词 | 先清债再学新；阈值与扩容量保守，避免老人单日负担过重 |
| peek 求助 | 展开内容但**不写任何状态**，按钮保留 | 老人需要帮助通道；peek 后作答仍走正常状态机，自评权留给用户 |
| 滑动策略 | **不允许越过未处理的卡**：向前越界 → **静默**退回该卡（不播提示语）；**回看自由** | ① 「自由划 + 滑过即回收」的机制**不可见**（屏幕上没有任何东西告诉用户可以划走，回收也无任何反馈）且**不终止**，用户会无限循环并以为 app 坏了；② 隐形规则对高龄用户最糟：试不出来、屏幕上也**什么都不会发生**（静默退回至少看得见「卡片滑走又滑回来」）；③ 两种方案**都会**逼用户答完（`Done` 的条件是 `pending == 0`），差别只是「当场、看得见」还是「延迟、隐形」——后者更差；④ 先例：主流 SRS 的复习循环本身就是「评分才能前进」（Anki 桌面端不评分不能翻卡），bury 是**显式**动作而非滑动副作用——上一版引 Anki 支持「自由划」属引用不精确 |
| 完成卡时机 | **条件出现**：`pending == 0` 时才把 `Done` 插入队列尾部，否则 feed 里根本没有它 | 「完成」应该是**事件**，不是**位置**。上一版保留固定位置、只把文案改写（「还有 N 张没作答」），既没说清、又漏判新词卡场景；而只要位置还在，用户就会一路滑到它 |
| 越界的处理方式 | **退回**（`animateScrollToPage` 到最靠前的待处理卡）+ **静默**（不播提示语），**不重排列表** | 只拦前向 = 保留「下滑回看」；**不得**用 `userScrollEnabled = false`（全向开关会把回看一起锁死，且卡片静止不动会被误读为卡死）。列表只追加不重排 → 上一版的回收锚点、装载稳定分区、pager `key` 错位防线全部不再必需 |
| 待处理口径 | 新学卡按**是否已教读**、复习卡按**是否已作答**；FREE 卡不计入 | 新词卡是当日任务真实的一员，只数复习卡会整片漏判；停稳教读（markSeen）才等于这张新词卡真的被处理过 |
| 战果数字 | 当日持久计数（`DayState` 增设认识/忘了次数），不用会话计数 | 完成是跨会话可达的事件（10 张配额约 30 张卡），会话计数重启归零会让最该真实的完成卡显示「认识了 0 个」 |
| 作答后前进 | **自动前进**（等本次播报念完 + 最短停留的两段式等待；新词卡不适用） | 高龄用户答完停在原地、不知道下一步做什么。R7 管「没作答不能走」、R8 管「作答完自动走」——合起来 feed 只剩一种前进方式：处理完当前卡（§5.5） |
| 分区定位 | **专题自主练习**：不设完成卡、不做前向拦截、不做到期筛选（~~滑完停住~~ → **[R10 改为无限流]**，见 §5.7） | 「每日任务」应当只有一份。分区不设完成卡 → R7 前向拦截的**唯一存在理由**（保证完成卡 = 真做完，§5.2）随之消失；不做到期筛选 → 排除刚毕业分区的空队列白屏。详见 §5.6（形态部分已被 §5.7 取代） |
| 分区队列范围 | 该区**全部词**（已学按「lapses 降序 → dueTime 升序」在前，未学洗牌接后） | 「自主练习」= 进来就有东西练。若保留到期筛选，一个刚毕业的分区（全部词 days ≥ 15 且未到期）会在十几天里 pool 为空 |
| 分区断点 | 上次滑到的那一页（**不是** frontier） | 自由划之后「在哪儿」≠「做到哪儿了」；用 frontier 会把用户拽回第一张未处理的卡 |
| 分区收尾 | **轻提示**（最后一张卡底部 + 并入该卡播报），不是完成卡 | 分区没有「完成」语义；但上滑无反应会被高龄用户当成卡死，需要一句看得见也听得见的「就这些了」 |

## 2. 数据模型变更

```kotlin
// TermState：间隔层 + 遗忘史
data class TermState(val days: Int, val lastSeen: String, val lapses: Int = 0)
```

事件表（**R11 修订：首答定调度**——当日首次作答即写间隔层，同日不再有第二次作答机会；连击层降级为「当日已作答标记」）：

| 事件 | 间隔层 |
|---|---|
| 首答「忘了」 | days=1、lastSeen=今天、**lapses+1**（当日移除，次日 lapses 排序优先回池） |
| 首答「认识」且无状态 | 首写 {1, 今天, 0} |
| 首答「认识」且有状态（lastSeen≠今天） | days=nextInterval、lastSeen=今天、**lapses/2**（每日最多升一级闸门保留） |
| 同日第二张考核卡作答「认识」（仅旧数据迁移日可达） | lastSeen==今天 → 闸门挡住，不升级（防双升级的兜底） |

## 3. 排序与配额（buildQueue）+ 池型抽取（poolIds）

**队列型只有每日任务频道**（R10 §5.7）。`buildQueue` / `ensureQueue` / `appendQueue` / `markAnswered` / `saveQueuePosition` 的 `channel` 参数**一律删掉**，内部固定用 `CHANNEL_DAILY`——让「队列 = 每日任务」成为签名层面的事实，而不是靠调用方自觉（P1-4 ③ 的根源正是「同一个函数服务两种频道类型」）。

```kotlin
// 已学词统一排序（v5 双键）：lapses 降序（忘词先见）→ dueTime 升序（先清旧债）
// ⚠️ 类型参数是**元素类型**（词 id = String），必须是 <String>：
//    写 <Int> 会得到 Comparator<Int>——与 List<String>.sortedWith(Comparator<in String>) 不匹配，
//    且 lambda 内 id: Int 会让 termStates[id] 直接编译不过（本轮修的就是这个）
val learned = scope.filter { termStates[it] != null }.sortedWith(
    compareByDescending<String> { id -> termStates[id]?.lapses ?: 0 }
        .thenBy { id -> termStates[id]?.let { s -> dueTime(s) } ?: Long.MAX_VALUE }
)
val due  = learned.filter { isDue(termStates[it]!!) }
val news = scope.filter { termStates[it] == null }.shuffled()
val pool = if (due.size >= DEBT_THRESHOLD) due.take(DEBT_QUOTA)   // 清债模式：全复习、不补新词（R11-3：每词 1 张 → 15 张，不再有 45 张债日）
           else interleave(due, news, DAILY_POOL_QUOTA)           // R11-2 交错：新词不垫底
```

```kotlin
/** R11-2 交错编排：格号 % 3 == 1 的位置优先放新词，其余位置优先到期词；任一池空则另一池顶上，直到配额。
 *  保证 news 非空时队列前 3 张内必有新词（做不完的用户也见得到新词）；两池皆空返回空（合法状态，见 buildQueue KDoc）。 */
private fun interleave(due: List<String>, news: List<String>, quota: Int): List<String> {
    val out = ArrayList<String>(quota)
    var d = 0; var n = 0
    while (out.size < quota && (d < due.size || n < news.size)) {
        val takeNews = (out.size % 3 == 1 && n < news.size) || d >= due.size
        if (takeNews) out.add(news[n++]) else out.add(due[d++])
    }
    return out
}
```

> 交错的行为核对：due 与 news 皆非空 → 新词落在下标 1、4、7…；news=0 → 全 due（与旧 `(due+news).take` 行为一致）；due=0 → 连续 news；清债模式不走本函数（due.take(15)）。

> ⚠️ **R10 删掉了 `else -> learned + news` 分支**（R9 的分区全量入队）。分区已不再生成队列（池型，§5.7），保留这个分支就是留一段永远不会被走到、却看起来仍在服务分区的死代码。

### 3.1 池型抽取（R10）

温故流与分区共用同一条追加逻辑，差别只在池的构成：

```kotlin
/**
 * 池型频道的抽取池（R10 §5.7）：
 * - 每日任务频道（温故流）= 该范围**已学词**洗牌（等概率，保持 v4 既有行为）
 * - 分区 = 该区**全部词**（含未学），加权随机（抽完由调用方重洗）
 */
fun poolIds(channel: String): List<String> =
    if (channel == CHANNEL_DAILY) scopeIds(channel).filter { termStates[it] != null }.shuffled()
    else weightedShuffle(scopeIds(channel))

/** 加权随机排列（Efraimidis–Spirakis 指数键）：key = -ln(u)/w，升序即无放回加权抽样 */
private fun weightedShuffle(ids: List<String>): List<String> {
    val rnd = Random.Default
    return ids.map { id ->
        val w = poolWeight(id)
        val u = 1.0 - rnd.nextDouble()      // u ∈ (0,1]：避免 nextDouble() 返回 0 时 ln(0) → +Inf
        id to (-ln(u) / w)
    }.sortedBy { it.second }.map { it.first }
}

/** 分区抽词权重（**只读**：忘得越多、越久没看 → 越容易被抽到；未学词固定中等权重） */
private fun poolWeight(id: String): Double {
    val st = termStates[id] ?: return SCENE_NEW_WEIGHT            // 2.0
    val last = runCatching { fmt.parse(st.lastSeen)?.time }.getOrNull()
    val now = runCatching { fmt.parse(today())?.time }.getOrNull()
    val daysAgo = if (last == null || now == null) 0L else ((now - last) / DAY_MS).coerceAtLeast(0L)
    return 1.0 + 2.0 * st.lapses + minOf(daysAgo, 60L) / 30.0
}
```

- 新常量：`SCENE_NEW_WEIGHT = 2.0`（未学词的固定中等权重）。
- 为什么用指数键而不是「按权重轮盘逐个抽」：前者一趟 `sortedBy` 完成（O(n log n)，n ≤ 368），且天然是无放回加权抽样；后者 O(n²) 且更容易写错边界。
- 权重公式的三段含义：`1.0` 基础（刚复习过的词也不是 0 概率）；`2×lapses` 遗忘史（忘一次翻倍权重）；`min(天数, 60)/30` 久未复习（两个月封顶，避免「一年没看」把权重拉到压倒性）。
- **只读**：`poolWeight` 不写任何状态；浏览卡不作答 → `poolIds` 的调用链上没有 `persist()`（§5.7 只读保证）。

常量：`INTERVALS = [1, 3, 7, 15, 30]`；`GRADUATED_DAYS = 15`（const，不再跟随 INTERVALS.last()）；`DEBT_THRESHOLD = 20`；`DEBT_QUOTA = 15`；`CHANNEL_DAILY = "rec"`；`SCENE_NEW_WEIGHT = 2.0`。

> ⚠️ 有意打破 09-17-elderly-literacy-app/design.md §8「毕业阈值自动跟随阶梯」不变量：阶梯与毕业解耦后，改阶梯不再自动改毕业门槛。GRADUATED_DAYS 必须显式评审。

## 4. 持久化与迁移

- terms JSON 增写 `"lapses"`；load 侧 `optInt("lapses", 0)`——旧数据自动迁移，无需迁移代码。
- 其余结构（day / queues / answered / position）不动；读写对称兜底逻辑不动。

## 5. 交互契约修订（TermCard/AppRoot）

| 卡形态 | 点卡片主体 | 「想看答案」按钮 |
|---|---|---|
| 新学（NEW） | 重听「词+提示」（不变） | 无 |
| 考试态未作答（REVIEW/FREE 且未 reveal） | TTS「再想一想，想起来了吗？」——**不念词、不念提示** | 有：展开拼音+提示（peek 态），按钮保留 |
| peek 后未作答 | 重听「词+提示」（已展开，泄题无意义） | 已展开，消失 |
| 已作答 | 重听（不变） | 无 |

- peek 态 = AppRoot 内 `peeked: mutableStateMapOf<seq, Boolean>`（会话态、不持久化、不写 repo）；重启后回未展开——与 revealed（作答）区别在于不 markAnswered。
- 底部提示文案：考试态未作答时「👆 想不起来？点下面的按钮」；其余维持「👆 点一下再听 · 点单字看讲解」。
- 自动播报 cardSpeech 不变（peek 不触发重播）。
- TTS 泄题修复的实现点：分流在 AppRoot 的 **TermCard 调用点**——考试态未作答且未 peek 时投给 `onSpeakTerm` 的是「再想一想，想起来了吗？」这一句，其余（新学 / 已作答 / peek 后）才投重听；求助通道另走 `onPeek`。TermCard 只收 `revealed` / `peeked` 等展示参数，不感知形态。
- **滑动策略（R7）**：**不允许越过未处理的卡**（决策理由见 §1）。向前越界会被**静默**退回该卡（不播提示语）；**下滑回看与完成卡之后的自由刷不受限制**。本版**不存在「跳过」这个动作**——出口只有 √ / ×（可先 👀 想看答案），作答完由 §5.5 自动前进。

### 5.1 完成卡「真做完才出现」（R7）

- **待处理口径（pending）**：`pages` 中满足任一条的卡 —— `mode == NEW && taught[seq] != true`（新词卡当日未被教读）、`mode == REVIEW && revealed[seq] != true`（复习卡未作答）。FREE 卡恒不计入。
- **条件存在**：`Page.Done` 仅在 `pending == 0` 时存在于 `pages`（插在任务区末尾）。未做完时 feed 尾部就是最后一张待处理卡，不存在任何含完成语义的页。判定 `pending` 的必须是**局部函数**而非 `val`——函数体内每次读 State 委托 getter 才是当前值（key 不含 `revealed` 的 effect 闭包会捕获旧 `val`）。
- ⚠️ **播报 effect 的 key 改为只有 `pagerState`**（原来含 `pages`）：教读追加考核卡、条件完成卡都会在 effect 体内改 `pages`，若 `pages` 仍是 key，effect 会执行到一半被自己重启掉，出现「追加了但没播报」。`pages` 在 collect 体内读取即为最新值。
- 完成卡文案与播报回到**单一形态**（不再分流）：
  - 视图：🎉 / 今日任务完成！/ 今天学完了 N 个词 · 认识了 x 次，忘了 y 次 / 继续上滑，随便看看 / `SwipeHint`
  - 播报：「太棒了，今日任务完成！今天学完了 N 个词，认识了 x 次，忘了 y 次。继续上滑，随便看看，温故知新。」
- 上一版「还有 N 张没作答 / 往下滑，回去把它们答完」分支与对应播报**删除**（条件出现后不可达，留作死代码只会误导）。
- 播报去重键按**页身份**：`channel:seq:<seq>`（词卡）/ `channel:done`（完成卡），**不含下标**。列表已不重排，下标键本可工作；仍绑 `seq` 是为了让「页身份」与「列表位置」解耦——追加（考核卡 / 完成卡 / 自由刷页）与将来可能重新引入的重排都不会改到已播报的键。完成卡用常量键是安全的：`pending == 0` 之后不可能再产生待处理卡（追加只发生在未毕业词上，而此时任务区所有词都已毕业；自由刷作答不追加），所以完成卡一旦出现就不会再消失。
### 5.2 不允许越过未处理的卡（前向拦截）

> **适用范围（R9）**：**仅每日任务频道（`CHANNEL_DAILY`）**。这条规则的全部意义是「让完成卡 = 真做完」（§5.1 的 `pending == 0`）；分区没有完成卡 → 没有「真做完」这个概念 → 分区**自由划**。分区若也拦，就是「全量入队 + 不可跳过 + 无收尾页」，是本版最糟的组合。

- **规则**：pager 向前停稳到 `i` 时，若 `pages` 中在 `i` 之前仍存在待处理卡，则**退回最靠前的那一张**（`pagerState.animateScrollToPage(block)`），然后 `return`（本次不播被拦页的 cardSpeech）。**不播任何提示语**。**列表顺序完全不动**：不回收、不重排、不写任何学习状态（不碰 `revealed` / `peeked` / `results`，不调 repo）。
- **判定式**：`block = pages.indexOfFirst { isPending(it) }`，当且仅当 `block >= 0 && block < i` 时拦截。**同一个 `isPending` 谓词同时定义「完成卡何时出现」与「何时拦人」**——一个口径，不会漂移。`pending == 0`（自由刷区）恒不拦。
- **静默是刻意的**：这条规则是**结构性**的——滑不动就是滑不动，不需要语音反复告知；而且用户随时可以翻回以前的卡，之后想往前走仍然要回来处理它，规则自己会说话。逐次拦截都播一句解释语，对高龄用户是噪声；何况拦住只在「手动作答前上滑」时才会触发，属边缘路径。退弹动画本身即反馈：卡片滑走又滑回来。若实测发现用户误以为卡死，再补一句极短提示——而不是默认就播。
- **为什么不用 `userScrollEnabled = false`**：它是全向开关，会把「下滑回看上一张」一并锁死；且卡片完全静止不动最容易被高龄用户误读为卡死。退回式拦截保留了拖拽反馈与退弹动画、且回看方向自由。
- **为什么不再需要 `key` / 回收锚点 / 装载分区**：列表**只追加不重排**（考核卡、条件完成卡、自由刷页都追加在尾部），下标不会因移动而错位。上一版的 `lastSettled` 回收锚点、装载时的稳定分区、`key` 错位防线都不再是必需（`key` 保留作卫生，KDoc 说明随之修正）。
- **新词卡的拦截窗口很窄**：停稳 200ms 即 `markSeen` + `taught = true`，只有「快甩而过、未停稳」才会被拦——恰恰是希望用户听一遍的那种情况。
- **fast fling 的处理**：一次跨越多个页面的连滑只做**一次**退回（退回最靠前的待处理卡），不做逐页拦截。
- **断点 = frontier**：`restoreTo` 取 `frontierIndex()`（无待处理卡时取完成卡下标，都没有则 0）；`saveQueuePosition` 同样写 frontier。本模型里「在哪儿」就等于「做到哪儿了」。

### 5.3 战果数字（当日持久口径）

- `DayState` 增 `knownAnswers` / `forgotAnswers`：`markKnown` / `markForgot` 各 +1；隔天随 `DayState` 整体作废（与连击层同一生命周期）。
- 持久化读写对称：`day` 对象增两个键，load 侧 `optInt(..., 0)`（旧数据缺省 0，无需迁移代码）。
- UI 侧 `session`（Compose 状态，负责触发重组）在装载队列时从 repo 初始化，之后随作答递增——既保住重组，又做到跨重启不归零。
- N（今天学完了 N 个词）= 任务区去重词数（完成时即「每个词都过了当日连击」，口径与队列一致）。

### 5.4 本次改判的代价（接受）

- **强制逐张处理**：单日队列约 30 张卡（10 词配额 × 每词最多 3 次连击），必须全部处理完才看得到完成卡，约两三分钟【R11 修订：每词当日 1 张考核卡（新词另 1 张教读卡）→ 单日 10–15 张，约一两分钟】。这是设计上的单日会话量、不是惩罚；但代价是「只想随便看看」的用户在完成前也进不了温故自由刷（FREE 页只在 `Done` 存在后追加）。
- **「认识」虚高的压力上升**：越界被拦后，用户为脱身可能直接点 √。这是本决策的主要代价，也是 Out of Scope 里「三选一拼音客观校验」留着的原因。缓解：peek 仍必须作答（👀 不是免答通道）；不进一步软化「忘了」的代价（那会让连击层失去意义）。
- **「跳过」能力归零**：本版没有任何跳过 / 延后动作。若实测发现用户确实需要，须以**显式动作 + 明确代价**（延到次日）补回，不能退回「滑动即跳过」。
- **观察信号**：若走查中发现用户反复向前划同一张卡（反复被静默退回、看起来像在跟界面较劲），说明他们没意识到「必须先作答」——此时应补一句极短提示或视觉引导，而不是改回自由划。
- **自动前进的代价（R8）**：节奏改由 app 掌握，用户不能自主停留——想多看一眼只能下滑回看。这是「高龄用户答完停在原地不知道下一步做什么」的交换条件；两段式等待（最短停留 + 等播报念完）是把它压到可接受的关键。

### 5.5 作答后自动前进（R8）

- **触发**：复习 / 温故卡完成作答（√ 或 ×）后自动前进一张（`animateScrollToPage(cur + 1)`）。**新词卡（NEW）不自动前进**——它没有「作答完成」这个事件，保留 `SwipeHint` 让用户自己掌握节奏。
- ⚠️ **时机是硬约束：必须等本次作答播报念完再翻。** `TTSSpeaker.speak` 是 `QUEUE_FLUSH`，抢跑会被紧随其后的落点卡播报把「词 + 提示 + 反馈」整句掐掉——与 R7 上一版踩的坑同一根因。实现两段式：
  1. **最短停留兜底** `delay(AUTO_ADVANCE_MIN_MS)`（约 1.5s）：保证答案与反馈在屏幕上至少停留这么久，也兜住「TTS 永久不可用」时不至于瞬间翻走；
  2. **等待朗读结束** `tts.awaitQuiet()`：`TTSSpeaker` 新增 `speaking` 标志（由 `UtteranceProgressListener` 维护）+ 挂起轮询到静音，带 **20s 上限**，避免 TTS 异常时永久挂起。
- ⚠️ **`speaking` 必须在 `speak()` 里同步置 `true`**，不能只靠 listener 的 `onStart`：否则「`speak()` 之后立刻 `awaitQuiet()`」会读到还没置位的 `false` 而抢跑。**但 `onStart` 也必须置回 `true`**：`speak()` 是 QUEUE_FLUSH，冲掉上一句时框架会先给**上一句**发 `onStop`（「flushed from the queue」也走 onStop）→ `speaking` 被清成 false，而新这一句整段播放期间都不再置位——`awaitQuiet()` 就会立刻返回、自动前进抢跑，正是本节要防的掐断（flush 的 `onStop` 必先于下一句的 `onStart` 到达：同一条回调 binder 上的顺序消息）。清除时机：`onDone` / `onError`（两个重载）/ `onStop` / `stop()` / `shutdown()`。
- **不抢用户方向**：翻页前重读当前页——若已不是刚才作答的那张（用户自己滑走了）或 `isScrollInProgress`，**放弃本次自动前进**。
- **落点 = `cur + 1`**：作答后未移除的考核卡追加在**队尾**，不改变下标关系；若当前卡是最后一张待处理卡，`syncDonePage()` 已在作答时同步插入完成卡，落点恰好是完成卡——当日会话自然收尾。
- **取消机制**：用 `advanceAfterSpeechSeq`（记作答卡的 `seq`）作 `LaunchedEffect` 的 key，effect 收尾置回 -1。⚠️ **置回 -1 必须排在 `animateScrollToPage` 之后（finally），并且「比较后再清」**（`if (advanceAfterSpeechSeq == seq) advanceAfterSpeechSeq = -1`）：它是本 effect 自己会改写的 key，写在翻页之前会让 effect 被自己重启并取消、动画刚开始就被掐掉（spec/frontend/state-management.md Gotcha (2)）；而动画途中用户若已答下一张（key 已是新 `seq`），收尾也不能把新 key 抹掉。同理不能把 `delay` / `awaitQuiet` 包进同一个 finally。

### 5.6 分区 = 专题自主练习（R9）

> ⚠️ **本节的「分区形态」部分已被 §5.7 取代**（R10）：池型纯浏览、无限流、不恢复位置、不作答。**仍然有效**的部分：完成卡只在每日任务频道（本节第 1 条）、前向拦截只在每日任务频道（第 2 条）、分区不做到期筛选且因此不白屏（第 3 条）、「分区队列含该区全部词」的范围口径。读本节时请以 §5.7 为准判断哪些还在生效。

- **完成卡只在每日任务频道存在**：`syncDonePage()` 在 `channel != CHANNEL_DAILY` 时直接 `return`。分区 feed 里没有任何收尾页，`pages` = 队列卡（+ 作答未移除时追加的考核卡）。
- **前向拦截只在每日任务频道生效**：`blockingIndex(idx)` 在 `channel != CHANNEL_DAILY` 时返回 -1。分区自由划——快甩过任意多张未处理的卡都不被退回，`pages` 不动、连击/间隔/answered 不因此变化。
- **队列 = 该区全部词**（§3）：不做到期筛选，因此**不存在空队列**。这是硬要求而非优化：若保留到期筛选，一个刚毕业的分区（全部词 `days ≥ 15` 且未到期，可持续十几天）会让 `pool` 为空 → `pageCount == 0` → `VerticalPager` **白屏**（现版本是靠「pool 空 → 队列只有完成卡 + 直接自由刷」兜住的，分区拿掉完成卡后就再没有东西兜了）。
- **滑完停住 + 轻提示**：分区不再触发 `appendFreePages`——它本就以 `doneIdx >= 0` 为前提，而分区 `doneIdx` 恒为 -1，**无需改代码**即自然停住。最后一张卡追加一句轻提示：
  - 视图：`TermCard(..., footerHint = ...)`，调用点判定 `channel != CHANNEL_DAILY && idx == pages.lastIndex` 时传入；**不新增页**——新增页会和完成卡长得像，正是 R9 要消除的混淆。
  - 播报：在该页 `cardSpeech` 之后拼一句（⚠️ 必须与 cardSpeech 拼成**同一句** `speak`——`QUEUE_FLUSH` 下分两次 speak 会互相掐断，与 R7/R8 同一根因）。
  - ⚠️ 「最后一张」**是会变的**：分区作答未移除时 `appendReviewCard` 仍会追加考核卡（`insertAt` 在无完成卡时天然落到 `pages.size`），提示随之移到新队尾；而分区**不做拦截**，用户快甩也能立刻抵达队尾。所以这句轻提示的准确含义是**「后面没有了」**（对当前位置而言），**不是「你练完了」**——这正是它必须是轻提示而不是完成卡的原因，读的时候不要把它当成完成语义。
  - ⚠️ 有 `footerHint` 时**不渲染 `SwipeHint`**（`TermCard`）：「上滑看下一个 ↑」与「后面没有了」自相矛盾，高龄用户对矛盾的容忍度极低（同一处理在原型 `refreshTailHint` 里）。
- **原型的已知简化**（不构成契约分歧，但别照着原型核对面）：原型没有 SRS 持久层，`buildQueue` 的场景分支是文档序（无法表达「已学在前」），作答也不追加考核卡（只换按钮区）——因此原型里的「最后一张」在 `renderFeed` 时定一次即可、不会移动。R9 的 5 条契约（无完成卡 / 不拦截 / 不做到期筛选 / 滑完停住 + 轻提示 / 断点语义）在原型侧均可验证，但**「提示随追加移走」这一条只能看安卓**。
- **断点 = 上次滑到的那一页**：
  - 装载：`restoreTo = if (channel == CHANNEL_DAILY) frontierIndex() else q.position.coerceIn(0, maxOf(0, pages.size - 1))`（`position` 落库时 clamp 到 `queue.size`、比 `pages.lastIndex` 大 1，需再夹一次）。
  - 落库：翻页 effect 里 `saveQueuePosition(channel, if (channel == CHANNEL_DAILY) frontierIndex() else pagerState.currentPage)`。分区 `pages` 与队列一一对应（无完成卡、无自由刷页），页码即队列下标。
- **双层状态机不变**：分区作答仍走 `markKnown` / `markForgot`（口径一致）；「忘了」⇒ days=1、lapses+1、连击清零；分区作答**计入当日战果**（`knownAnswers` / `forgotAnswers`，全局当日口径）。
- **待处理谓词在分区不再被消费**：`isPending` / `pendingTaskCount()` / `frontierIndex()` 保持原样（只在每日任务频道被调用），不为分区额外分支——口径只有一套，避免漂移。

### 5.7 自主学习 = 纯浏览的池型频道（R10）

**0. 一句话**：频道分成**队列型（只有 `rec`）**与**池型（12 个场景分区）**两类；完成卡之后的**温故流**与**分区**都是「池型 + 纯浏览卡」。

**1. 卡形态：浏览卡沿用 `CardMode.FREE`，只改呈现**

- **不新增也不重命名 `CardMode` 成员**：`FREE` 本义就是「自由刷」，语义仍然成立；而重命名要动 4 个文件的十余处引用，在**本机无法编译**的条件下不值这个风险。
- 呈现：`TermCard` 的 `isExam` 从 `mode != CardMode.NEW` 改为 **`mode == CardMode.REVIEW`** → FREE 卡自动走「全展开 + 无动作区」那条路（与新学卡同一形态）。`peeked` / `onPeek`（「👀 想看答案」）**保留但只剩复习卡可达**——不要删，删了复习卡的求助通道就没了。
- 调用点：`revealed = p.mode != CardMode.REVIEW || revealed[p.seq] == true`（FREE 恒展开）。
- 徽标：`CardMode.FREE` 文案从「📖 温故」改为「**📖 看看**」——同一个徽标要同时服务「推荐频道的温故流」与「分区浏览」，而分区里的词可能是**未学词**，叫「温故」不成立。
- ⚠️ **`cardSpeech` 必须显式加 `p.mode == CardMode.FREE -> termSpeech(p.t)` 分支**：现在它靠 `revealed[p.seq] == true` 判定展开，而浏览卡永远不写 `revealed`，会掉进 `else` 播防泄题话术（「这个词，还记得它念什么吗？」）——在浏览卡上是错的。
- ⚠️ 点按分流（AppRoot 的 `onSpeakTerm` 调用点）必须从 `p.mode != CardMode.NEW && revealed[p.seq] != true` 收窄为 **`p.mode == CardMode.REVIEW && revealed[p.seq] != true`**：否则浏览卡会播提示语而不是念词。

**2. 池与追加：一条追加逻辑服务两种频道**

- 池：`repo.poolIds(channel)`（§3.1）。
- `appendFreePages(count)` 改名 **`appendPoolPages(count)`**：从 `freePool` 顺序取，池空时用 `repo.poolIds(channel)` 重洗补满。两个频道类型共用同一实现。
- 触发（翻页 effect 的 `snapshotFlow { pagerState.currentPage }` 内）：
  - **队列型**（`rec`）：`doneIdx >= 0 && idx >= doneIdx - 1` → 追加（**保持现状**：完成卡之前不追加 = 未做完不进温故流）；
  - **池型**（分区）：`idx >= pages.lastIndex - 1` → 追加（**无限流**，任何时候都追加）。
- 装载时池型频道先填一批（`POOL_BATCH`）：`pages` 不能为空——`VerticalPager` 的 `pageCount == 0` 就是白屏。
- 常量 `FREE_BATCH` 改名 `POOL_BATCH = 2`（两个频道类型共用）。
- 追加只会**发生在列表尾部**：与「只追加不重排」的既有不变量一致，`spokenKeys` / `key` 的页身份机制不受影响。

**3. 断点：队列型 = frontier，池型 = 不恢复**

- 装载：`restoreTo = if (channel == CHANNEL_DAILY) frontierIndex() else 0`——池型频道**滚回第一张**（D6：每次进分区都是一轮新的随机）。用 `0` 而**不是** `null`：切频道时 `pagerState.currentPage` 可能还是上一个频道的旧值（比如 7），而新 `pages` 只有 2 张，必须显式归零。
- 落库：翻页 effect 里**只对队列型**调 `repo.saveQueuePosition(frontierIndex())`；池型不落库（页码无意义）。

**4. 三样东西整个删掉**

- `TermCard` 的 `footerHint` 参数与调用点；
- `AppRoot` 的 `SCENE_TAIL_HINT` / `SCENE_TAIL_SPEECH` / `isSceneTail()`；
- 池型频道的队列持久化（见下条）。

**5. 持久化：只留每日任务队列**

- `ensureQueue` / `appendQueue` / `markAnswered` / `saveQueuePosition` 删掉 `channel` 参数，内部固定 `CHANNEL_DAILY`；`buildQueue` 去掉 `channel` 参数与 `else` 分支（§3）。`ensureQueue` 的「空队列不复用」补丁随之简化——分区不再有队列，而每日任务的**空队列是合法状态**（pool 空 → 队列只有完成卡 → 直接温故流，prd 已定义）。
- `load()` 只读入 `CHANNEL_DAILY` 的队列：旧版本写下的分区队列在下次 `persist()` 时自然消失（一次性清理，**不需要迁移代码**）。`DailyQueue.channel` 字段保留（读写对称）。

**6. 只读保证（D4：新词入口唯一化）**

浏览卡没有作答入口 → `answer()` 在池型频道不可达 → 不写 `TermState`、不追加考核卡；且浏览卡**不调 `markSeen`**（`markSeen` 只在队列型频道的 `NEW` 卡停稳分支里被调用）。故：

- 分区里浏览过的**未学词永远拿不到 `TermState`** → 下次仍以「未学」出现，学会它的唯一路径是每日任务；
- `DayState`（`counts` / `seen` / 战果计数）在池型频道**零写入**。

**7. 自动收窄（这些地方不需要额外代码，别误改）**

- `answer()` 只剩复习卡一条入口 → **R8 自动前进**（`advanceAfterSpeechSeq` 在 `answer()` 末尾置位）天然只对复习卡生效；浏览卡不自动前进（用户还没看完就翻页是打扰）。
- **战果计数**（`knownAnswers` / `forgotAnswers`）只在 `answer()` 里 +1 → 天然只统计每日任务频道。
- `isPending` 里 `CardMode.FREE -> false` 保持；池型频道全页 FREE → `pendingTaskCount() == 0`，但 `syncDonePage()` / `blockingIndex()` 已被频道判定拦在前面（R9 保留部分）——**不要**为了「统一」去掉那两处 `if (channel != CHANNEL_DAILY)`。

**8. 不变式**

- 每个场景频道在 `WordBank` 里有 ≥ 30 词（实测 hospital / food / phone / weather = 32，其余 = 30）→ 池型频道的池**必非空** → 不存在 `pageCount == 0` 白屏。这是 D3（不做到期筛选）之外的**第二重**保证。
- 每日任务频道的全套行为（R1–R8）不变。

**9. 本条已接受的代价**（用户 2026-09-18 明确接受）

- **温故流与分区不再产生任何学习记录**：间隔层唯一写入来源 = 复习卡作答；分区 🎓 毕业徽章只能靠每日任务推进。
- **学习效果全押在每日任务 n 个词上**：机制杠杆（连击目标 / 新词保底 / 封顶 / 配额）从「可选优化」变成唯一调节手段；n 的隐式上限 ≈ 30n（n = 10 → 约 300 词 < 词库 368）随之成为必须处理的问题（prd Out of Scope 已记）。

**10. 原型的已知简化**

`prototype/index.html` 没有 SRS 持久层 → **加权不可复现**，分区只能做**等概率随机**（本次唯一允许的原型偏差，必须在原型注释里写明）。其余契约（浏览卡形态 / 无限流追加 / 无队尾轻提示 / 不恢复位置 / 无完成卡 / 不拦截）都要在原型里成立。原型此前**整个缺温故流**——本轮把池型追加机制做出来后，推荐频道完成卡之后的温故流用同一套机制补上（同一个函数 + 一个触发条件）。

### 5.8 调度核心修订：首答定调度（R11，2026-09-19 定案）

**0. 一句话**：连击目标 3 → 1——当日**首次**作答即写间隔层并把该词移出当日；作答后**不再追加**任何考核卡；新词**交错**编排；清债日随「每词一卡」自然守恒；附 f30 观测点。依据：`research/learning-optimization-proposals.md` P0-1/P0-3/P0-4 + `learning-mechanism-review.md` §5 方案 B（日均 39.6 → 13.1 张、能学词 119 → 177）。**用户已明确拒绝 P0-2（作答撤销窗口）——不做。**

**1. 要修的死锁（P0-5）**：满 3 连击才升级 + `DayState` 隔天作废 ⇒ 做不完 → counts 永远 <3 → `TermState` 永不写 → 队列每天一模一样。首答定调度后：做多少、成多少，进度跨天不丢（这正是墨墨「当天第一次反馈决定长期调度」的产品规则）。

**2. StudyRepository 语义重写**

```kotlin
/** R11：当日首答「认识」即写间隔层（首写 {1,今天,0}；lastSeen≠今天 → nextInterval + lapses/2；
 *  lastSeen==今天 → 闸门兜底不升级——正常流程同日无第二张考核卡，只有旧数据迁移日可达）。
 *  counts[id] = 1（当日已作答标记）；返回 (1, scheduled, daysAfter)：
 *  scheduled = 本次作答决定了间隔层（首写 / 升级 / 封顶同值刷新都算 true；闸门挡住为 false）。 */
fun markKnown(id: String): Triple<Int, Boolean, Int>

/** R11：当日首答「忘了」→ {1, 今天, lapses+1}；counts[id] = 1——忘了同样当日移除，
 *  次日 lapses 降序排 due 前部优先回池。 */
fun markForgot(id: String)
```

- `isRemovedById(id)` = `counts[id] != null`（旧数据 1/2/3 兼容为已作答）；**删除 `DAILY_COMBO_TARGET` 常量**——删完必须 grep 全部引用（`MainActivity.kt` 等处一并清理，本机不能编译，漏一处就是编译错误）。
- `appendQueue` **保留**且只剩一个用途：新词卡停稳教读（`markSeen` 返回 true）后追加**恰好一张**考核卡——「学会」仍必须经过一次提取测试（新词入口唯一化不动，D4 不回归）。
- 「忘了」当日不复现（不追加）；错误纠正由作答播报整句（词 + 提示 + 反馈）承担——R5 的防泄题只限制**作答前**，作答后本来就会念全句。
- 「忘了」打回 days=1 **维持现状**（P1-1 不改：归零对认知衰退风险人群更安全，等 f30 数据再议）。

**3. 交错编排（R11-2）**：`interleave` 见 §3。清债模式不动（due ≥ 20 → due.take(15)，无新词）。

**4. AppRoot 侧**

- `answer()`：**删除** `if (p.mode != CardMode.FREE) appendReviewCard(t)`——作答后不再追加。`appendReviewCard` 从此只由新词教读（停稳 effect 的 `markSeen` 分支）触发，KDoc 同步。
- 作答播报（`answer()` 内按 `count` 的三分支删除，改按 `scheduled`）：
  - `scheduled == true` → 视觉「👍 这个词学会啦！{明天 / X 天后}再来复习」；播报「这个词学会啦！{明天 / X天}后再来复习。」（`daysAfter == 1` 念「明天」，避免「1 天后」的生硬）
  - `scheduled == false`（闸门兜底，仅迁移日可达）→ 视觉「👍 这个词今天学过啦」；播报「这个词今天学过啦。」
  - 忘了 → 视觉「没关系，明天再学 💪」；播报「没关系，明天再来学它。」
- R5–R10 全部交互契约**零改动**：待处理口径（taught / revealed）、完成卡条件出现（pending == 0）、前向拦截、自动前进（作答后落点仍是 cur+1；最后一卡答完自动落在完成卡——队尾不再有追加卡，`syncDonePage` 插完成卡的位置关系不变）。

**5. 迁移与兼容**

- 旧 `counts` ∈ {1,2,3} → 一律「当日已作答」（`isRemovedById` 恒真）——迁移日语义甚至更连贯：counts=2 未毕业的词当天不再被反复考核。
- 旧队列里同词的**多张未作答考核卡**（旧规则追加的）：逐张可作答；第二张起 `lastSeen == 今天` 闸门挡住 → 不双升级。这正是闸门保留的理由，**不要顺手删闸门**。
- f30 计数用**独立 prefs key**（`f30_total` / `f30_fail`），不进主 state JSON——观测数据与学习状态解耦，清观测不清学习进度。

**6. f30 观测（R11-4，只读不改行为）**：`markKnown` / `markForgot` 内、写库**之前**取 `termStates[id]?.days`；`== 30` → `f30_total + 1`，且若是「忘了」→ `f30_fail + 1`。首答口径（R11 后每日每词至多一次）；迁移日同词第二张考核卡可能重复计数——可接受的观测噪声，不必去重。

**7. 不变式（不得顺手改掉）**：每日最多升一级闸门；`markSeen` 教读不写 `TermState`；新词入口唯一化（池型频道零写入）；清债阈值 / 配额常量；毕业判定 15；`lapses` 排序与升级半衰；间隔阶梯 1→3→7→15→30。

**8. 代价（用户 2026-09-19 已知悉）**：误点 √ 无撤销（P0-2 被拒——首答 1 天代价小，长间隔代价大但受闸门限制）；「忘了」当天不复现（跨天间隔提取，Maddox & Balota 2015：对老人有效）；失去三重确认仪式感（由「学会啦」播报补）。

**9. 原型同步**：`prototype/index.html`——作答后的考核卡追加逻辑（若有）删除；新词交错进 `buildQueue`（原型无持久层，按当日会话内模拟 due/news）；作答反馈文案同步；完成卡条件出现 / 前向拦截 / 自动前进契约不动。

## 6. 回滚

- **R11（首答定调度）**落在 StudyRepository.kt（`markKnown` / `markForgot` / `isRemovedById` / 删 `DAILY_COMBO_TARGET` / `buildQueue` 的 `interleave` / f30 计数）、AppRoot.kt（`answer()` 删追加 + 播报文案）+ 原型 HTML（作答追加删除 + 交错 + 文案）。revert 该提交即回到「满 3 连击」口径；持久化无 schema 变更（counts 值语义放宽，旧版本读到 1 无害）。
- R9（分区 = 自主练习）落在 StudyRepository.kt（`buildQueue` 的 `else` 分支 + `CHANNEL_DAILY` 常量）、AppRoot.kt（`syncDonePage` / `blockingIndex` 的频道收窄、断点两处、`footerHint` 传参）、TermCard.kt（新增可空 `footerHint` 参数）+ 原型 HTML（`refreshDoneCard` / `blockSkippedCards` 的频道判定 + 队尾轻提示）。`footerHint` 有缺省值，UI 侧改动向后兼容；调度侧 `learned + news` 只是把分区 pool 从 `due + news` 扩大，可单独 revert。
- 全部改动集中在 StudyRepository.kt / TermCard.kt / AppRoot.kt / DoneCard.kt 四个文件 + 原型 HTML；按改动分层可分别 revert。
- R7 本轮落在 AppRoot.kt / DoneCard.kt / 原型 HTML，并在 StudyRepository.kt 增设当日战果计数（`DayState.knownAnswers/forgotAnswers`，缺省读 0）——调度层（排序/配额/间隔/毕业）与防泄题逻辑不受影响，可单独 revert。
- 本轮把上一版 R7 的「前向消化」整体删除（`recycleSkipped()`、`lastSettled` 锚点、装载稳定分区），改为前向拦截；拦截逻辑全在 AppRoot.kt（+ 原型对应函数），删掉即回到「自由划 + 完成卡条件出现」这一版。
- R8 自动前进落在 AppRoot.kt（一个 `LaunchedEffect` + `answer()` 里置 key）与 `TTSSpeaker.kt`（新增 `speaking` / `awaitQuiet()`，不改既有 `speak` / `stop` 语义）——删掉该 effect 即回到「作答后用户自己上滑」，TTS 侧新增成员留着无害。
- 持久化新增字段为缺省读，回滚版本读到多余 key 无害。

## 7. 验证

- 编译：`gradlew assembleDebug`。
- 手动场景走查（见 implement.md 清单）：升级播报 30 天、lapse 优先排序、清债配额、防泄题、peek 不写状态、旧 JSON 兼容、完成卡条件出现、向前越界被静默退回且列表不重排、回看与自由刷不受限、作答后等播报念完自动前进（含落在完成卡）、用户滑动时不抢方向、战果跨重启不归零。
- 分区（R9）：非 `rec` 频道无完成卡与完成播报；自由划不被退回；全学完且未到期的分区不白屏；滑完停住并出现轻提示 + 同句播报；分区断点回到上次那一页；分区「忘了」仍写 days=1 / lapses+1。
- 自主学习（R10）：温故流与分区都是浏览卡（全展开、无 √/×、点按念词）；分区池含未学词且浏览后仍无 `TermState`；分区顺序两次不同、池尾继续追加（无限流）、重进从第一张开始；温故流里看完一个词其 days / lapses 不变；完成卡战果只来自复习卡作答；每日任务频道 R1–R8 全套不回归。
- 调度核心（R11）：首答「认识」立即写间隔层（三种分支）；首答「忘了」写 {1, 今天, lapses+1} 且当日移除、次日排 due 前部；作答后 feed 不再出现该词任何卡；新词教读恰好追加一张考核卡；交错编排（前 3 张内有新词）；清债日 15 张；旧 counts/旧多卡队列兼容（闸门防双升级）；f30 计数落独立 key 且不改调度行为；R5–R10 交互契约零回归。
