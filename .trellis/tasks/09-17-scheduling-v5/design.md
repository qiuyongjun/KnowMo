# design.md — 学习复习调度优化 v5

> 延续 09-17-elderly-literacy-app/design.md §9（v4 连击 + 间隔双层模型）的迭代记录文体。
> v5 只动调度参数与排序、毕业判定、复习卡交互契约；双层状态机结构（连击层/间隔层/每日队列/自由刷）不变。

## 0. 问题定位（为什么改）

| # | 问题 | 根因 | 影响 |
|---|---|---|---|
| P0-1 | 到期债务必然堆积 | 370 词 × 15 天封顶 → 稳态每日到期 ≈25 > 配额 10 | 长期用户逾期队列只增不减，「忘了」率上升 → days 打回 1 → 债更多，恶性循环 |
| P0-2 | 「忘了」词排序反向 | markForgot 写 {days=1, lastSeen=今天} → 次日 dueTime 全池最新 → dueTime 升序排池尾 | 超额日 take(quota) 首先挤掉昨天刚忘的词——最需要复现的词反而见不到 |
| P1-1 | 复习卡点按泄题 | TermCard 主区域 clickable → onSpeakTerm 对所有形态生效 | 考试态未作答时一个 tap 听到「词+提示」，考回忆契约失效，连击虚高 |
| P1-2 | 完成卡与队列完成度不一致 | ① 完成卡与队尾绑定（`pages` = 队列卡 + Done），滑到底 ≡ 完成；② 上一轮补救口径只数「未作答复习卡」，漏掉「整天都是新词卡被快速甩过」——新词卡未停稳就不触发教读，该口径恒为 0 | 一张卡没答也能听到「今日任务完成」；完成卡变成固定在队尾的装饰，而不是成绩 |

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
| 滑动策略 | 考试态未作答**不锁滑动**，全程开放 | ① 跳过不写 `TermState`，次日 `isDue` 仍为真且 `dueTime` 更旧 → 在 v5 双键排序中反而更靠前，**自愈无债务**；② 强制作答会把「跳过」转成「乱答」（「忘了」在 v5 代价加重，用户倾向点「认识」），污染连击层与 lapses——即往 Out of Scope 的「认识虚高」方向加码；③ Anki bury / SuperMemo postpone 说明跳过是一等控制权 |
| 完成卡时机 | **条件出现**：`pending == 0` 时才把 `Done` 插入队列尾部，否则 feed 里根本没有它 | 「完成」应该是**事件**，不是**位置**。上一版保留固定位置、只把文案改写（「还有 N 张没作答」），既没说清、又漏判新词卡场景；而只要位置还在，用户就会一路滑到它 |
| 前向消化 | 向前滑过的待处理卡**回收队尾**，不写任何状态 | 「不锁滑动」与「必须回看才能做完」之间只剩这一条路：跳过仍是一等控制权（同 Anki bury），但不再能把用户困在队尾。「跳过 = 稍后」，不是「放弃」 |
| 待处理口径 | 新学卡按**是否已教读**、复习卡按**是否已作答**；FREE 卡不计入 | 新词卡是当日任务真实的一员，只数复习卡会整片漏判；停稳教读（markSeen）才等于这张新词卡真的被处理过 |
| 战果数字 | 当日持久计数（`DayState` 增设认识/忘了次数），不用会话计数 | 完成是跨会话可达的事件（10 张配额约 30 张卡），会话计数重启归零会让最该真实的完成卡显示「认识了 0 个」 |

## 2. 数据模型变更

```kotlin
// TermState：间隔层 + 遗忘史
data class TermState(val days: Int, val lastSeen: String, val lapses: Int = 0)
```

事件表（增量，其余同 v4 §9.2）：

| 事件 | 间隔层 |
|---|---|
| 「忘了」（任何地方） | days=1、lastSeen=今天、**lapses+1** |
| 满 3 连击升级（lastSeen≠今天） | days=nextInterval、lastSeen=今天、**lapses/2** |
| 满 3 连击但闸门挡住 / 无状态首次完成 | lapses 不变（首次完成写 {1, 今天, 0}） |

## 3. 排序与配额（buildQueue）

```kotlin
val due = scope.filter { isDue(...) }
    .sortedWith(
        compareByDescending<Int> { termStates[it]?.lapses ?: 0 }
            .thenBy { termStates[it]?.let { s -> dueTime(s) } ?: Long.MAX_VALUE }
    )
val pool = when {
    channel == "rec" && due.size >= DEBT_THRESHOLD -> due.take(DEBT_QUOTA)   // 清债模式
    channel == "rec" -> (due + news).take(DAILY_POOL_QUOTA)                   // 现状
    else -> due + news                                                        // 场景频道不变
}
```

常量：`INTERVALS = [1, 3, 7, 15, 30]`；`GRADUATED_DAYS = 15`（const，不再跟随 INTERVALS.last()）；`DEBT_THRESHOLD = 20`；`DEBT_QUOTA = 15`。

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
- TTS 泄题修复的实现点：TermCard 主区域 clickable 的回调按形态分流——考试态未作答回调 onPeek-hint（播提示语）而非 onSpeakTerm。
- **滑动策略（R7）**：全程开放，不因未作答而锁定（决策理由见 §1）。跳过不写任何状态；被滑过的待处理卡由 §5.2 的「前向消化」回收队尾，所以**不再需要「往回滑」这个动作**——用户一路往上滑就能做完。

### 5.1 完成卡「真做完才出现」（R7）

- **待处理口径（pending）**：`pages` 中满足任一条的卡 —— `mode == NEW && taught[seq] != true`（新词卡当日未被教读）、`mode == REVIEW && revealed[seq] != true`（复习卡未作答）。FREE 卡恒不计入。
- **条件存在**：`Page.Done` 仅在 `pending == 0` 时存在于 `pages`（插在任务区末尾）。未做完时 feed 尾部就是最后一张待处理卡，不存在任何含完成语义的页。判定 `pending` 的必须是**局部函数**而非 `val`——函数体内每次读 State 委托 getter 才是当前值（key 不含 `revealed` 的 effect 闭包会捕获旧 `val`）。
- ⚠️ **播报 effect 的 key 改为只有 `pagerState`**（原来含 `pages`）：前向消化与追加都会在 effect 体内改 `pages`，若 `pages` 仍是 key，effect 会执行到一半被自己重启掉，出现「回收了但没播报」。`pages` 在 collect 体内读取即为最新值。
- 完成卡文案与播报回到**单一形态**（不再分流）：
  - 视图：🎉 / 今日任务完成！/ 今天学完了 N 个词 · 认识了 x 次，忘了 y 次 / 继续上滑，随便看看 / `SwipeHint`
  - 播报：「太棒了，今日任务完成！今天学完了 N 个词，认识了 x 次，忘了 y 次。继续上滑，随便看看，温故知新。」
- 上一版「还有 N 张没作答 / 往下滑，回去把它们答完」分支与对应播报**删除**（条件出现后不可达，留作死代码只会误导）。
- 播报去重键改为按**页身份**：`channel:seq:<seq>`（词卡）/ `channel:done`（完成卡），**不含下标**。含下标的键（如上一版的 `channel:idx:done`）与「前向消化」直接冲突——回收会改变同一张卡的下标，卡片被回收队尾再滑到时旧下标已失效，会被当成新页**重复播报**；实现里完成卡一旦出现就不会再消失（`pending > 0` 时不可能凭空产生新的待处理卡），故 `channel:done` 常量键足够。
### 5.2 前向消化（滑过即回收）

- **不变量**：设 frontier = 第一张待处理卡的下标。**frontier 之前全是已处理卡，frontier 起全是待处理卡**。用户永远站在 frontier 上，「往上滑」永远有下一张待处理卡可做；`pending == 0` 时下一张就是完成卡。于是「不需要往回滑」不再是提示语，而是结构保证。
- **回收动作**：pager 向前停稳到 `i` 后，把区间 `[lastSettled, i)` 里**仍待处理**的卡按原相对顺序移到队尾（完成卡之前）。典型情况只回收刚滑过的那一张，快速连滑则一次多张；往回滑不回收（身后只可能是已处理卡）。
- **不写任何学习状态**：回收只改 `pages` 顺序，不碰 `revealed` / `peeked` / `results`（状态按 `seq` 跟着卡走），不调 repo（连击、间隔、answered、持久化队列顺序全不动）。所以「滑过」与「跳过」在数据上完全等价——跳过是「稍后」，不是「放弃」，也不会被永久丢弃。
- ⚠️ **必须给 `VerticalPager` 传 `key`**（`TermPage -> seq`，`Done -> "done"`）：回收会移动/追加列表项，没有稳定 key 时 pager 按 index 定位，会把用户正在看的卡换成别的卡（错位）。有 key 时 Compose 锚定当前项，列表增删移动不改变用户看到的内容。
- **装载时做一次稳定分区**：任务区按「已处理在前、待处理在后」稳定分区（各自保持原相对顺序），把上次会话里「被滑过但未回收」的卡归位到 frontier 之后——否则重启后身后留着待处理卡，用户走到队尾会真的卡死。
- **断点 = frontier**：`restoreTo` 取 `frontierIndex()`（无待处理卡时取完成卡下标，都没有则 0）；`saveQueuePosition` 同样写 frontier。本模型里「在哪儿」就等于「做到哪儿了」，回溯到轨迹中间回听不算断点。

### 5.3 战果数字（当日持久口径）

- `DayState` 增 `knownAnswers` / `forgotAnswers`：`markKnown` / `markForgot` 各 +1；隔天随 `DayState` 整体作废（与连击层同一生命周期）。
- 持久化读写对称：`day` 对象增两个键，load 侧 `optInt(..., 0)`（旧数据缺省 0，无需迁移代码）。
- UI 侧 `session`（Compose 状态，负责触发重组）在装载队列时从 repo 初始化，之后随作答递增——既保住重组，又做到跨重启不归零。
- N（今天学完了 N 个词）= 任务区去重词数（完成时即「每个词都过了当日连击」，口径与队列一致）。

## 6. 回滚

- 全部改动集中在 StudyRepository.kt / TermCard.kt / AppRoot.kt / DoneCard.kt 四个文件 + 原型 HTML；按改动分层可分别 revert。
- R7 本轮落在 AppRoot.kt / DoneCard.kt / 原型 HTML，并在 StudyRepository.kt 增设当日战果计数（`DayState.knownAnswers/forgotAnswers`，缺省读 0）——调度层（排序/配额/间隔/毕业）与防泄题逻辑不受影响，可单独 revert。
- 持久化新增字段为缺省读，回滚版本读到多余 key 无害。

## 7. 验证

- 编译：`gradlew assembleDebug`。
- 手动场景走查（见 implement.md 清单）：升级播报 30 天、lapse 优先排序、清债配额、防泄题、peek 不写状态、旧 JSON 兼容、完成卡条件出现、滑过即回收且不写状态、frontier 不变量、战果跨重启不归零。
