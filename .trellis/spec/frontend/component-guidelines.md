# 组件与交互契约

> 认识么（KnowMo）的 Compose UI 约定与 feed 交互契约。
> 语言：中文，与 `prd.md` / `design.md` / 代码注释保持一致。

---

## 组件结构

- 所有 Composable 放 `ui/`；业务状态由 `AppRoot` 持有并下传，子组件**不感知调度器与 repo**。
- **形态分流集中在调用点**，组件只渲染。例：`TermCard` 只收 `revealed` / `peeked` / `onSpeakTerm` / `onPeek` / `onAnswer`，「考试态未作答该播提示语还是重听」的判定在 `AppRoot` 的调用点完成。
- 参数扁平传递；不要在两个文件里各维护一份分流规则。
- 文案与 TTS 话术集中在 `AppRoot`（`cardSpeech`），组件只负责渲染。

## feed 交互契约（VerticalPager）

### 方向语义

`VerticalPager` 以 index 递增为「下一个」：**手指上滑 = index+1**；手指下滑 = index−1（回看）。

> **Warning**：凡是引导用户「回去看前面某张卡」的文案，方向词必须是**往下滑**。
> `SwipeHint()` 的文案固定为「上滑看下一个 ↑」，在需要往回滑的分支里**不得渲染**，否则方向相反会误导。
>
> v5 R7（前向拦截）下**没有任何分支该说「往下滑」**：用户不可能落在未处理的卡之后（见下），所以「回去补答」这个动作不存在。回看（下滑）是用户自发的自由动作，不靠文案引导。

### 滑动策略：不允许越过未处理的卡（只拦前向，静默退回）

**契约（v5 R7）**：向前停稳到某页时，若其前方仍存在**待处理**卡（`isPending`，见下），则**静默退回最靠前的那一张**——只做 `animateScrollToPage`，**不播任何提示语**。**不得**用 `userScrollEnabled = false`，也不得做任何全向锁定。

> **适用范围（v5 R9；R10 后措辞更新）**：**仅每日任务频道**（`StudyRepository.CHANNEL_DAILY` = `"rec"`）。拦截的**唯一**意义是「让完成卡 = 真做完」；池型频道（分区 / 温故流）没有完成卡，该理由不成立 → **自由划**。若池型也拦，就是「全部词入池 + 不可跳过 + 无收尾页」，是最糟的组合。

判定与完成卡条件**共用同一个谓词**：

```kotlin
fun blockingIndex(idx: Int): Int {
    if (channel != StudyRepository.CHANNEL_DAILY) return -1   // R9/R10：池型频道（分区 / 温故流）自由划
    val first = pages.indexOfFirst { isPending(it) }
    return if (first >= 0 && first < idx) first else -1   // -1 = 不拦
}
```

- 站在待处理卡**自身**上不拦（`first == idx`）；`pending == 0`（完成卡之后）恒不拦；FREE 页恒不计入（v5 R10：池型频道全页都是浏览卡，`isPending` 恒 false）。
- **只拦前向**：下滑回看（含翻回以前任意一张已处理的卡）是用户的自由动作，必须保持可用。用 `userScrollEnabled` 是错的——它是全向开关，会连回看一起锁死，且卡片静止不动会被高龄用户误读为卡死。
- 一次 fast fling 跨多页只做**一次**退回（退回最靠前的那张），不逐页拦截。
- 退回**不写任何学习状态**：不碰 `revealed` / `peeked` / `results`，不调 repo，列表顺序完全不动（不回收、不重排）。
- **静默是刻意的**：这条规则是**结构性**的——滑不动就是滑不动；用户随时可以翻回以前的卡，之后想往前走仍然要回来处理它，规则自己会说话。逐次拦截都播一句解释语对高龄用户是噪声；何况拦截只在「手动作答前上滑」时才触发。退弹动画本身即反馈。
- ⚠️ 拦截分支必须在 `spokenKeys.add(...)` **之前** `return`——否则被拦页下次真停稳时会被去重，用户**永远听不到**它。

**为什么不用「回收队尾」保证前向可达**（上一版做法，已废弃）：「滑过即回收」把「必须答完」变成了一个**不可见**的机制——屏幕上没有任何东西提示可以划走，回收也不给任何反馈；而且它**不终止**：回收无次数上限，未答完时 FREE 页不追加（`doneIdx >= 0` 才追加），用户会陷入同一批卡的无限循环（没有完成卡、进不了温故、也没有解释）。隐形规则对高龄用户最糟：试不出来，屏幕上也什么都不会发生（静默退回至少看得见「卡片滑走又滑回来」）。

### 作答后自动前进（v5 R8）

**复习卡（`CardMode.REVIEW`）**上 √ / × 之后**自动前进一张**（`animateScrollToPage(cur + 1)`）。

> **适用范围（v5 R10）**：作答入口只剩每日任务的复习卡——温故流与分区都是**浏览卡**（没有 √/×，也就没有「作答完成」这个事件），因此自动前进天然只对复习卡生效。R10 之前 FREE 卡也走这条路，现在已收窄。

- **新词卡（NEW）与浏览卡（FREE）都不自动前进**：它们没有「作答完成」这个事件（浏览卡用户还没看完就翻页是打扰），`SwipeHint` 保留，由用户自己上滑（手动上滑也永远是「不想等」时的加速通道）。
- **不抢用户方向**：翻页前重读当前页，若已不是刚才作答的那张（用户自己滑走了）或 `isScrollInProgress`，放弃本次自动前进。
- **落点 = `cur + 1`**：v5 R11 后作答**不再追加**任何考核卡（首答定调度，见下节），`cur + 1` 恒指向下一张队列卡；若当前卡是最后一张待处理卡，`syncDonePage()` 已在作答时同步插入完成卡，落点恰好是完成卡。

> **Warning**：翻页**必须等本次作答播报念完**。`TTSSpeaker.speak` 是 `QUEUE_FLUSH`，抢跑会被落点卡的播报把「词 + 提示 + 反馈」整句掐断。
>
> 两段式实现：`delay(AUTO_ADVANCE_MIN_MS)`（约 1.5s 最短停留，兼兜「TTS 永久不可用」）→ `tts.awaitQuiet()`（挂起到静音，内部 20s 上限）。
>
> **`awaitQuiet()` 的正确性依赖 `speaking` 的置位时机**：必须由 `speak()` **同步**置位、且 `onStart` 也置位——`QUEUE_FLUSH` 冲掉上一句时会先给**上一句**发 `onStop`，那一瞬间标志会被清成 `false`，而新那句整段播放期间若不再被置回，`awaitQuiet()` 会立刻返回。详见 `state-management.md` 的「TTS 播报完成信号」。

### 完成度语义：完成卡是**事件**，不是**位置**（仅每日任务频道）

**规则**：`Page.Done` 只在 `pendingTaskCount() == 0` 时**插入** feed（任务区末尾）。未做完时 feed 里根本没有收尾页，队尾就是最后一张待处理卡。

**适用范围（v5 R9）**：`syncDonePage()` 开头 `if (channel != StudyRepository.CHANNEL_DAILY) return` —— **只有每日任务频道有完成卡**，分区 feed 里没有任何收尾页，也不播含完成语义的语音。

```kotlin
fun isPending(p: Page) = p is Page.TermPage && when (p.mode) {
    CardMode.NEW -> taught[p.seq] != true      // 新词卡按「是否已教读」
    CardMode.REVIEW -> revealed[p.seq] != true // 复习卡按「是否已作答」
    CardMode.FREE -> false                     // 浏览卡（温故流 / 分区）不计入：不是当日任务，也没有作答入口
}
```

完成卡文案是**单一形态**（不再按 pending 分流）：🎉 / 今日任务完成！/ 今天学完了 N 个词 / 忘了 y 次 / `SwipeHint`；底部引导 = `inBrowse ? "随便看看吧" : "上滑进入推荐模式"`。

⚠️ **完成卡上不放「认识了 x 个词」**（v16 定稿）：连击机制下它**恒等于 N** —— 每词连对 3 次才移出队列、任意一次「忘了」清零重来，而完成卡只在**全部词都过关**时出现，所以到达完成卡时每个词的连击数都是 3，两行数字永远一样、纯重复。**设置页那行保留**（学到一半打开时 x < N 是有信息的）。

⚠️ **v16 战果量纲**：认识取**去重词数**、忘了取**次数**（连击满 3 才移出队列，次数口径会把 10 个词报成 30 次）；N 取**持久口径**（`repo.todayTaskWordCount()` = 当日队列 distinct），**不得由 `pages` 派生** —— 到达完成卡的**那一帧** `enterBrowse()` 就移除了全部任务卡，pages 派生的计数会归零。详见 `state-management.md` 的「战果计数（当日持久）」。

### 列表只追加不重排（前向拦截的副产品）

**契约**：`pages` 只在**尾部追加**——新词教读后追加**恰好一张**考核卡（v5 R11：这是考核卡唯一的产生时机）、`pending == 0` 时追加完成卡、临近队尾时追加**池型页**。**永不移动、永不重排**（前向拦截靠「退人」而不是「移卡」）。

由此：

- `VerticalPager` 的 `key` 不再是错位防线（列表不重排，index 已足够）；保留它是为了页身份与列表位置解耦，将来若重新引入重排不必再踩坑。
- 装载时**不需要稳定分区**：拦截保证待处理卡不会被留在用户身后，且装载总是定位到 frontier。
- **断点（队列型 = 每日任务频道）= frontier**：`restoreTo` 与 `saveQueuePosition` 都取 `frontierIndex()`（第一张待处理卡；无待处理卡时取完成卡下标，都没有则 0）。本模型里「在哪儿」就等于「做到哪儿了」。
- **断点（池型 = 分区）= 不恢复位置**（v5 R10）：顺序是加权随机，第 k 页没有稳定所指。装载 `restoreTo = 0`（**必须是 `0`，不能是 `null`**——切频道时 `pagerState.currentPage` 可能还是上一个频道的旧值，而新 `pages` 只有 2 张），且池型**不调 `saveQueuePosition`**。

### 频道分两类：队列型 vs 池型（v5 R10；R9 的「分区队列 + 轻提示」形态已废止）

**契约**：**队列型 = 只有每日任务频道**（`StudyRepository.CHANNEL_DAILY` = `"rec"`）；**池型 = 13 个场景分区 + 每日任务完成卡之后的温故流**（运行时从池里抽卡、无限追加、**不落库**）。

| 维度 | 队列型（`rec` 每日任务） | 池型（分区 / 温故流） |
|---|---|---|
| 数据结构 | `DailyQueue`（`queue`/`modes`/`answered`/`position`） | 无——运行时池（`repo.poolIds(channel)` + `freePool`），不落库 |
| 完成卡 | `pending == 0` 时出现 | **没有**（`syncDonePage` 直接 return） |
| 前向拦截 | 拦（静默退回） | **不拦**（`blockingIndex` 返回 -1） |
| 池范围 | `interleave(due, news)` 配额 10 / 清债日 15（due ≥ 20，**v16 起含新词**） | 温故流 = 推荐范围**已学词**（加权随机）；分区 = 该区**全部词含未学词**（加权随机） |
| 卡形态 | 新学（NEW）/ 复习（REVIEW，**唯一考试态**） | **浏览卡**（`CardMode.FREE`：词 + 逐字拼音 + 用途全展开、无 √/×、无「想看答案」） |
| 追加 | **仅完成卡之后**（`doneIdx >= 0 && idx >= doneIdx - 1`） | **无限流**（`idx >= pages.lastIndex - 1`，池空重洗） |
| 断点 | frontier | **不恢复位置**（每次从第一张开始） |

- **考试态只有 `REVIEW`**：`TermCard` 的 `isExam = mode == CardMode.REVIEW`（原 `mode != CardMode.NEW`——那个写法会把浏览卡也当成考试卡）。浏览卡由调用点传 `revealed = true` 恒展开。
- ⚠️ **`cardSpeech` 必须显式先判 `FREE`**：浏览卡永远不写 `revealed`，若落到 `else` 就会播防泄题话术（「这个词，还记得它念什么吗？」）——在浏览卡上是错的。
- ⚠️ **点按分流必须收窄为 `p.mode == CardMode.REVIEW && revealed[p.seq] != true`**：否则浏览卡点一下播提示语而不是念词。
- **池型频道零写入**：浏览卡不调 `markSeen`、也没有作答入口 → `TermState` / `DayState` 零写入。分区里的未学词**只看不算学**（学会它的唯一路径是每日任务，新词入口唯一化）；分区「学完」标记（v16 起顶栏用文字「学完」，不再用 🎓）也只能靠每日任务推进。`answer()` / `markKnown` / `markForgot` / `appendQueue` / `markAnswered` / `saveQueuePosition` 在池型频道**一律不可达**。
- **池必非空是硬要求**：分区池 = 该区全部词（含未学词，每区 ≥ 17 词）→ 不存在 `pageCount == 0` 白屏。R9 的「`ensureQueue` 空队列不复用」补丁已随分区队列一起删除——每日任务的空队列是**合法**状态（pool 空 → 队列只有完成卡 → 直接温故流），不要把它当异常「修」掉。
- **加权随机（分区）**：`w = 1 + 2×lapses + min(距上次学习天数, 60)/30`，未学词固定 `SCENE_NEW_WEIGHT = 2.0`；用 Efraimidis–Spirakis 指数键（`key = -ln(u)/w`，升序）实现无放回加权抽样，`u = 1.0 - random` 防 `ln(0) → +Inf`（O(n log n)，n ≤ 549，比轮盘逐个抽更少边界）。**只读**：权重计算不写任何状态。
- **温故流与分区共用同一条追加逻辑**（`appendPoolPages`），差别只在池的构成。R9 的队尾轻提示（`footerHint` / `SCENE_TAIL_HINT` / `SCENE_TAIL_SPEECH` / `isSceneTail`）**已整体删除**——无限流之后不存在「最后一张」，轻提示失去出现时机。
- **持久化**：`buildQueue` / `ensureQueue` / `appendQueue` / `markAnswered` / `saveQueuePosition` **不带 `channel` 参数**（内部固定 `CHANNEL_DAILY`）——让「队列 = 每日任务」成为签名层面的事实。`load()` 只读入 `CHANNEL_DAILY` 的队列条目（旧版本写下的分区队列在下次 `persist()` 自然消失，无需迁移代码）。

> **Warning**：`appendReviewCard` 的插入点是**任务区末尾**——`indexOfFirst { it is Page.Done }` 取不到时用 `pages.size`。写成 `coerceAtLeast(0)` 会在 Done 缺席（v5 常态）时把新卡插到**队首**，新卡就落到了用户身后。

### 调度核心：连击 + SM-2 双层（v6 R12；恢复 v4 连击 + 废弃固定阶梯 INTERVALS）

**契约**：复习/考核卡当日累计 **3 次「认识」** 才写间隔层并把该词**当日移除**；未满 3 不写 TermState、追加该词一张考核卡到队尾（间隔复现型，append-only 天然形成间隔）；「忘了」清零 + 写 `{1, today, lapses+1, ease-0.2}` + 追加纠错卡到队尾。依据：`tasks/09-17-elderly-literacy-app` v6 R12 计划——当日多次复现补回 R11 砍掉的复习密度，SM-2 ease 让忘得多的词间隔增长变慢。

> **历史**：v5 R11 曾改「首答定调度」（当日首次作答即移除、不再追加），v6 R12 推翻回 v4 连击；废弃固定阶梯 `INTERVALS = [1,3,7,15,30]`，改由 `nextInterval(prevDays, ease) = round(prevDays × ease).coerceIn(1, 30)` 动态计算。`DAILY_COMBO_TARGET = 3` 恢复。

- **间隔层写入语义**（`StudyRepository.markKnown` / `markForgot`）：
  - 「认识」：`counts[id]+1` 封顶 3。`count < 3` → **不写** TermState（词保持原到期，次日自然回池——遗留词机制）；`count == 3` → 无 `TermState` 首写 `{1, today, 0, ease=2.5}`；`lastSeen != today` → `days = nextInterval(cur.days, cur.ease)`、`ease = min(2.5, cur.ease + 0.1)`、`lastSeen = today`；`lastSeen == today` → **闸门兜底不升级**（counts 照常 +1 到 3）。返回 `Triple(count, upgraded, daysAfter)`，UI 据此播报剩余次数 / 升级天数。
  - 「忘了」：`counts[id] = 0`（不当日移除，`appendQueue` 会追加纠错卡）+ 写 `{1, today, lapses+1, ease = max(1.3, cur.ease - 0.2)}`。次日因 lapses 降序排 due 前部优先回池。
- **`DayState.counts` = 当日认识计数 0..3**。`isRemovedById = dayCount >= DAILY_COMBO_TARGET`（= 3）。旧 v5 R11 数据 `counts=1` 会被当作「未移除」——迁移日当日可对该词再追加考核卡，属可接受的迁移噪声（次日随 DayState 作废清掉）。
- **`appendQueue` 两个用途**：①新词卡停稳教读（`markSeen` 返回 true）后追加**恰好一张**考核卡（学会必须经过一次提取测试）；②作答后未满连击移除（counts<3，含忘了清零后）→ 追加该词一张考核卡到队尾。append-only 队尾追加天然形成间隔——同词多张卡被其他词隔开。
- **新词交错（R11-2 保留）**：`buildQueue` 的 pool 合并用 `interleave(due, news, quotaProvider())`——格号 `% 3 == 1` 优先放新词。v6：配额改读 `quotaProvider()`（MainActivity 注入 AppSettings.quota()；当日队列冻结不变，次日生效）。
- **清债日（v16 改口径）**：`due ≥ DEBT_THRESHOLD`(20) → `interleave(due.take(DEBT_QUOTA - newsPool.size), newsPool, DEBT_QUOTA)` —— 总额仍 15 张，**新词配额照给**（默认 5），复习占剩下的 10。v5 原口径是 `due.take(15)` 全复习、不补新词，与设置页「新词固定几个，不被复习挤掉」的承诺直接矛盾（复习槽位 5/天、已学约 300 词时净积压 ~2/天 → 约 10 天攒到 20 → 清债日约每周一次），QYJ 2026-09-21 裁改为保底新词；代价是清债日复习量 15 → 10，还债稍慢。
- **f30 观测（R11-4）**：`markKnown` / `markForgot` 内、写库**之前**取 `termStates[id]?.days`，`== 30` → 独立 prefs key `f30_total`（忘了再 `f30_fail`）。v6 R12 连击恢复后一日内同一词最多计 3 次（可接受噪声，不去重）。
- **闸门（每日最多升一级）保留的理由**：同一天内「忘了→再连击满 3」时不让 days 升级（lastSeen==today 挡住），与 prd「忘了次日重新连击三次才能移除」一致。**不要顺手删闸门**。
- **ease 边界**：`[1.3, 2.5]`，认识 +0.1 封顶 2.5、忘了 -0.2 下限 1.3。首次学会固定 ease=2.5。旧 JSON 无 `"ease"` 键缺省 2.5，无需迁移代码。
- **无撤销（用户 2026-09-19 明确拒绝 P0-2）**：不作答撤销 / 纠错窗口——不引入任何撤销 UI 与快照回滚。

#### Wrong vs Correct

#### Wrong
```kotlin
// R11 首答即定调度（v6 R12 已推翻——恢复 v4 连击）
val (_, scheduled, daysAfter) = repo.markKnown(t.id)   // 忽略 count、不追加
// 固定阶梯（已废弃）
val ni = nextInterval(cur.days)   // 缺 ease 参数
```

#### Correct
```kotlin
// answer()：连击满 3 才移出；未满追加考核卡；忘了追加纠错卡
val (count, upgraded, daysAfter) = repo.markKnown(t.id)
if (count < 3) appendReviewCard(t)                    // 间隔复现型
// else 分支忘了：appendReviewCard(t)                  // 纠错卡
// buildQueue：交错编排（配额来自 quotaProvider）
val pool = if (due.size >= DEBT_THRESHOLD) due.take(DEBT_QUOTA)
           else interleave(due, news, quotaProvider())
// 动态间隔
val ni = nextInterval(cur.days, cur.ease)              // SM-2 简化版
```

### 播报去重键绑「页身份」

去重键 = `"$channel:seq:${p.seq}"`，完成卡 = `"$channel:done"`。

**不要**把下标或会变动的派生值放进键：追加（考核卡 / 完成卡 / 池型页）会让同一张卡在不同时刻对应不同下标，绑 `seq` 才能让「页身份」与「列表位置」解耦。完成卡用常量键是安全的——`pending == 0` 之后不可能再产生待处理卡，它一旦出现就不会消失。

⚠️ **池型页要按「页身份」而不是「词 id」入键**：池抽完会重洗，同一个词在同一次会话里可能出现两次；按词 id 入键会让第二次出现被当成「已播报过」而**静默不播**。原型的做法是给每张池卡一个自增 `data-page`，键为 `channel:page:N`。

## 适老化硬约束

- 反馈按钮高度 92dp；图标 40–44sp；词字随词长 42–64sp；点击目标不小于 48dp。
- TTS 语速 0.85x。播报在**滑动停稳后**延迟约 200ms 触发，滑动进行中一律 `tts.stop()`（快速连滑不闪播）。
- 关键语义不用 emoji 表达：作答按钮用 √ / × 字形（随字号缩放、无彩色表情歧义）。
- ~~不做设置页、生词本、进度条~~（**v6/v14 已推翻**）：v6 恢复隐藏设置入口与收藏；v14 设置页新增只读「学习统计」节（含分区进度条）。
  - **v14 统计契约**：统计快照（`StudyStats`）由 `repo.studyStats()` 一次性构建、全**零写入**；分区进度以 `STUDY_TERMS` 为口径（剔除词库已删的鬼 id，否则分区会出现 learned > total 的鬼账）；分区总数动态算、**不硬编码**。`streak` 是唯一新增写路径：独立 prefs key（`streak_count`/`last_study_date`），`markKnown`/`markForgot` 开头旁路记账，**不进主 state JSON、不触碰调度状态**（与 f30 观测同模式）。显示口径：最后学习日是今天或昨天 → 存量计数，断 ≥ 2 天 → 0。
  - **v16 总览口径 = 当前可学的词**（QYJ 2026-09-21 拍板）：`totalWords` / `learned` / `graduated` 一律取 `scopeIds(CHANNEL_DAILY)`（常用词 + **可见分区**，与调度器推荐范围同源）。原分母是全词库（549），而缺省全隐藏时用户只学得到常用词那十几条 —— 对用户没有意义。**隐藏分区里已学过的词也一并不计**（数字会随隐藏而下降），换来分子分母同口径、永不出现 X > N。`stats.scenes` 只含**可管理分区**（排除 rec / fav / daily，与 `AppSettings.manageableIds` 同判据）。
  - ⚠️ **快照要在「显隐变化」时重取**：分母依赖可见分区，只按「打开设置页」重取会让用户在设置页里开关分区时分母纹丝不动。AppRoot 在 `onSetVisible` 回调里同步重取。
  - **术语统一为「学完」（v16）**：**面向用户的文案一律用「学完」，不再出现"毕业"** —— 设置页「学完 N 词」（**词**级 `days >= GRADUATED_DAYS`），顶栏分区 chip 加「学完」后缀（**整个分区**的词全部学完；v16 起用文字取代 🎓 emoji —— emoji 在 20sp 小字下发虚，且本文件早就写了「场景 chip 只放文字」，🎓 一直是那条规则的例外）。代价：chip 变宽（分区名 + 「学完」），顶栏一屏能放的频道变少，靠横向滚动兜。代码标识符沿用 `graduated` / `GRADUATED_DAYS` / `isSceneGraduated`（多处引用、与产品措辞解耦）—— **别为了对齐措辞去重命名标识符**。

## 设置页版式与拖动（v16）

原版实测 ≈ 2530dp ≈ 3.5 屏，其中约七成是**同一批分区被列了两遍**（统计里 14 行进度条 + 管理里 13 行操作行），出口按钮还在第 3.5 屏。现行版式：三张卡（学习统计 / 每日学习量 / 分区显示与顺序），分区卡**把进度与显隐排序合并**，分「已显示 / 未显示」两段。

- **可见分区自动排前面**：渲染顺序 = 可见组（按 `order`）+ 隐藏组（按 `order`）。`order` 本身不含可见性信息，分组只发生在渲染层。
- **「完成」常驻底部**：滚动内容末尾留白 = `DONE_BAR_SPACE`(104dp) + Column 的 `navigationBarsPadding()`（该 padding 排在 `verticalScroll` **之后**，因此是内容内边距而非视口收缩）。两者之和必须与常驻条的实际占位一致，否则滚到底时最后一行被压住。
- **段内长按拖动重排**（`detectDragGesturesAfterLongPress`），落点换算成**目标分区在 order 里的下标**再落库（`AppSettings.moveSceneTo`）。**同组项在 order 中的相对顺序与渲染顺序一致**，所以段内搬下标即可，持久层不必感知分组语义。↑↓ 按钮保留（适老化备选），与拖动写同一份持久层。
- **跨段拖动不生效**（钳回本段）：跨段等于改变可见性，那是开关的职责，隐式迁移会让"拖了一下"变成"开关变了"。
- ⚠️ **行高必须固定**（`SCENE_ROW_H`）：让位位移 = ±行高，行高一旦可变，让位算法立即失效。
- ⚠️ **拖动的手势 block 会被记住**，闭包里的列表与回调都会变旧（段内重排后列表内容变了，而 `pointerInput` 的 key 仍是同一个 id）。一律经 `rememberUpdatedState` 取最新值，起点下标在 `onDragStart` 里**按 id 现算**，不用编译期捕获的 `i`。
- ⚠️ **操作区宽度是挤出来的**：每行要同时放「名称 + 进度 + ↑ + ↓ + 开关」。↑↓ 收窄到 40dp（**高度仍 64dp**，适老化下限看高度）、开关收窄到 68dp，名称列才有 ~120dp（≥360dp 屏可完整显示 4 字以内的分区名，更窄的屏走单行省略）。
- ⚠️ **`AppSurface` 是页面底色，不能当卡内容器底**：它在白色卡片上只有 1.13:1，等于不存在。禁用态控件要用 `AppLine` 容器 + 中灰前景。

## Common Mistakes

| 症状 | 根因 | 修法 |
|---|---|---|
| 引导文案方向相反 | 把「上滑 = 下一个」误用于「回看」 | 回看用「往下滑」；`SwipeHint()` 只在**已处理**的卡上渲染 |
| 有卡没答却看到「任务完成」 | 完成卡被当成队尾的固定页 | `pendingTaskCount() == 0` 时才把 `Page.Done` 插入 feed |
| 未答完就一直划、永远看不到完成卡 | 只做了「自由划」，靠看不见的回收机制兜底（不终止） | 改为前向拦截：**静默**退回（退弹动画即反馈），规则结构性且终止 |
| 想回看上一张却被挡住 | 用 `userScrollEnabled = false` 做全向锁定 | 只拦前向：退回式拦截（`blockingIndex`） |
| 被拦页下次真停稳时**不播报** | 拦截分支写在 `spokenKeys.add(...)` 之后 | 拦截必须在 `add` 之前 `return` |
| 作答后自动前进把答案播报掐断 | 翻页抢在 `tts.awaitQuiet()` 之前 | 最短停留 + 等静音再翻，见「作答后自动前进」 |
| 追加 / 插入后该次播报消失 | 播报 effect 的 key 含它自己会改写的 `pages` | key 只留 `pagerState`；`pages` 在 collect 体内读 |
| 新追加的考核卡跑到队首 | `appendReviewCard` 用了 `coerceAtLeast(0)` | 取不到 Done 时用 `pages.size` |
| 同一词的多张考核卡一起展开 | 用词 id 而非 `seq` 记录展开态 | 按页出现（`seq`）记录；队列用 `answered[i]` |
| 复习卡点一下就把答案念出来 | 点卡回调没按形态分流 | 调用点按 `mode` + `revealed` + `peeked` 路由到提示语（**R10 收窄为 `mode == REVIEW`**，否则浏览卡也会播提示语） |
| 分区里冒出「今日任务完成」 | 完成卡对所有频道无条件插入 | `syncDonePage()` 开头 `if (channel != CHANNEL_DAILY) return` |
| 分区上滑被退回、像被锁住 | 前向拦截被无条件应用 | `blockingIndex()` 仅 `CHANNEL_DAILY`；池型自由划 |
| 学完的分区点进去白屏 | 池按到期筛选 → 空池 | 池 = 该区**全部词**（含未学词，每区 ≥ 17 词 → 必非空）；不要给池加到期筛选 |
| 浏览卡播成「这个词，还记得它念什么吗？」 | `cardSpeech` 漏了 `FREE` 分支（浏览卡不写 `revealed`，落进 `else`） | `cardSpeech` 在 `NEW` 之后显式判 `FREE -> termSpeech(t)` |
| 在温故流里随手一点就改写了间隔层 | 浏览卡仍是考试态（`isExam = mode != NEW`） | `isExam` 只认 `REVIEW`；`answer()` 只能由复习卡触达 |
| 在分区里「学会」了新词（其实没学会） | 池型频道写了 `markSeen` / `TermState` | 池型**零写入**：浏览卡不调 `markSeen`，没有作答入口；未学词只「看」不算学 |
| 进分区被拽回上次那一页 | 池型也用了页码断点 | 池型**不恢复位置**：装载 `restoreTo = 0`（必须是 `0` 而非 `null`——切频道时 `pagerState.currentPage` 可能是旧值） |
| 分区 / 温故流上滑到底就没了 | 池型追加条件抄成了队列型的 `doneIdx >= 0` | 队列型 `doneIdx >= 0 && idx >= doneIdx - 1`；池型 `idx >= pages.lastIndex - 1`（无限流） |
| 设置页里 ↑/↓ 到顶 / 到底时按钮「消失」 | 禁用态 = `AppSurface` 容器 + `AppLine` 箭头，两色在**白色卡片**上几乎无差（1.07:1 / 1.31:1） | 禁用态用 `AppLine` 容器 + 中灰箭头（≥3:1，WCAG 1.4.11 图形元素下限）；`AppSurface` 是**页面底色**，不能拿来当卡内容器底 |
| 设置页内容滚到状态栏 / 导航栏底下被遮挡 | `verticalScroll` 写在了 inset padding **之前**，之后的 padding 只算内容内边距 | inset padding（`statusBarsPadding` / `navigationBarsPadding`）必须排在 `verticalScroll` **之前** |
| 卡内小字对比度不达标 | 用 `Color.copy(alpha = 0.75f)` 弱化文字 | 弱化交给**字号**而不是透明度：`AppText2.copy(alpha = 0.75f)` 在白卡上只有 ~4.9:1，低于本项目 7:1 指标 |
| 设置页滚到底、最后一行被「完成」压住 | 末尾留白只算了按钮高度、漏了导航栏 | `DONE_BAR_SPACE`(104dp) + Column 的 `navigationBarsPadding()`，且该 padding 必须排在 `verticalScroll` **之后** |
| 拖动排序拖过一次之后就错位 | 手势 block 记着旧的列表内容与旧下标 | 列表/回调经 `rememberUpdatedState` 取最新值；起点下标在 `onDragStart` 里按 id 现算，别用捕获的 `i` |
| 改了分区显隐，「已学 X / N」纹丝不动 | 统计快照只在打开设置页时取一次，而分母依赖可见分区 | `onSetVisible` 回调里同步重取 `repo.studyStats()` |
| 拖动跨段把分区"拖没了"（或可见性被意外改变） | 允许跨段落位 | 段内拖动，落点钳回本段；改可见性是开关的职责 |
| 旧版本的分区队列一直躺在持久化里 | `load()` 仍读入全部频道 | `load()` 只读 `CHANNEL_DAILY`（旧条目在下次 `persist()` 自然消失） |
| 池型频道的卡排到一半顺序变了 | 追加时重排了 `pages` | 池型页**只在尾部追加**；顺序随机靠「重新进入频道时换一批池」表达，不是靠重排 |
| 同一词旧数据迁移日被双升级 | 闸门（`lastSeen == today` 不升级）被当死代码删掉 | 闸门是迁移日同词多张考核卡的防双升级兜底，**保留** |
| 忘了的词当天又冒出考核卡 | `answer()` 里仍调 `appendReviewCard` / `appendQueue` | R11 首答定调度：作答即当日移除，`appendQueue` 只服务新词教读 |
| 做不完的用户永远学不到新词 | pool 合并写回 `(due + news).take(...)` | 用 `interleave`（格号 % 3 == 1 优先新词）；前 3 张内必有新词 |
| f30 计数随学习状态一起被清 | 观测写进了主 state JSON | f30 用**独立 prefs key**（`f30_total` / `f30_fail`），且在写库**之前**取答前 days |
