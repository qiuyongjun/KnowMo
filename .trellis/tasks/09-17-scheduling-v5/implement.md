# implement.md — 执行计划

## 执行顺序（含验证命令）

1. **StudyRepository.kt 调度层**
   - INTERVALS 增 30 档；GRADUATED_DAYS 改 `const val = 15`
   - TermState 增 `lapses: Int = 0`；markForgot `lapses+1`；markKnown 升级分支 `lapses/2`
   - isSceneGraduated：`days == GRADUATED_DAYS` → `days >= GRADUATED_DAYS`
   - buildQueue：due 排序 compareByDescending(lapses).thenBy(dueTime)；rec 频道清债模式（due.size >= 20 → due.take(15)）
   - persist 写 `"lapses"`；load `optInt("lapses", 0)`
   - 同步类/KDoc 注释（1→3→7→15→30 封顶、毕业判定 15）
2. **TermCard.kt + AppRoot.kt 防泄题 + peek**
   - TermCard 增 `peeked: Boolean` 参数（组件只管渲染；不新增回调参数，「该播提示语还是重听」的分流在 AppRoot 调用点）
   - 主区域 clickable 的回调由 AppRoot 调用点按形态投递：考试态未作答且未 peek → 播「再想一想，想起来了吗？」；其余（新学/已作答/peek 后）→ 重听「词 + 提示」
   - 底部提示文案条件化；考试态未作答区加「👀 想看答案」小按钮
   - AppRoot：`peeked` map（按 seq）；peek 回调只置 map 不写 repo；TermCard 调用点接线
3. **prototype/index.html 契约同步**
   - 考试卡点按不播读音（播「再想一想」）；加「想看答案」按钮（展开不写状态——原型本就无持久层，只改交互表现）
   - **完成卡条件出现**（保留）：`renderFeed` 只在待处理数为 0 时才渲染 `doneCardHTML`，否则队尾没有任何收尾页
   - **前向拦截取代「滑过即回收」**：**删掉** `appendChild` 回收与配套的 scroll-anchoring 位移补偿；滚动停稳（200ms 去抖）后，若当前可见卡之前仍存在待处理卡，则**静默** `scrollTo` 回最靠前的那一张（`behavior:'smooth'`，**不播任何提示语**；删掉 `BLOCK_HINT_*` 与 `pendingBlockHint`）。**DOM 顺序完全不动**
   - 待处理口径与 App 对齐：未作答的复习卡 + 尚未看过的温故卡（原型的「看过」= `onCardVisible` 触发过）
4. **StudyRepository.kt 当日战果计数（R7 附带，design.md §5.3）**
   - `DayState` 增 `knownAnswers` / `forgotAnswers`；`markKnown` / `markForgot` 各 +1（与 `counts` 同一当日生命周期，隔天作废）
   - `persist` 写两键、`load` 侧 `optInt(..., 0)`，读写对称
   - 新增只读 getter：`wasSeenToday(id)`（`id in currentDay().seen`，供新词卡断点恢复判断是否已教读）、`todayKnown()` / `todayForgot()`
5. **AppRoot.kt + DoneCard.kt 完成卡条件出现 + 前向拦截（R7，design.md §5.1–§5.2）**
   - `taught: mutableStateMapOf<Int, Boolean>`：装载时新词卡按 `repo.wasSeenToday(id)` 初始化；停稳教读（markSeen）后置 true
   - `pendingTaskCount()` / `frontierIndex()` / `blockingIndex()` 写成局部**函数**（不能是 `val`，见 §5.1）
   - **删除**上一版的 `recycleSkipped()`、`lastSettled` 回收锚点、装载时的稳定分区（列表只追加不重排，三者都不再需要）
   - 装载队列时：`pages` 只在 `pending == 0` 时含 `Page.Done`（`syncDonePage()`）；`restoreTo = frontierIndex()`
   - **前向拦截**（§5.2）：在停稳播报 effect 内、`spokenKeys.add` **之前**插入——`val block = blockingIndex(idx)`；`block >= 0` → `pagerState.animateScrollToPage(block.coerceIn(0, pages.lastIndex))` + `return@collect`。**静默**：不播任何提示语——把上一版的 `blockHint` 状态、`BLOCK_HINT_*` 两个常量、以及「与 cardSpeech 拼成一句」的逻辑一并删除，播报恢复为 `announce + text` 的原式。**不重排 `pages`、不写任何状态、不调 repo、被拦页的 `spokenKey` 不入 `spokenKeys`**
   - 播报 effect 的 key 保持只有 `pagerState`（body 仍会改 `pages`：教读追加考核卡、条件完成卡）
   - `cardSpeech` 的 `Page.Done` 收敛为单一形态（§5.1 文案）；`spokenKey` 用 `channel:seq:<seq>` / `channel:done`；`saveQueuePosition` 写 frontier
   - **不得**给 `VerticalPager` 设 `userScrollEnabled = false`（会连下滑回看一起锁死）；`key` 可保留，但其 KDoc 里「回收会错位」的理由要改成「页身份与列表位置解耦」
   - DoneCard：删掉 `pending` / `answered` 参数与分流分支，改传 `known` / `forgot` / `words`（N）
   - `session` 在装载时从 repo 初始化（跨重启不归零）
6. **TTSSpeaker.kt + AppRoot.kt 作答后自动前进（R8，design.md §5.5）**
   - `TTSSpeaker` 新增 `@Volatile var speaking: Boolean`（private set）与 `suspend fun awaitQuiet()`；init 里 `setOnUtteranceProgressListener`：`onStart` → `speaking = true`，`onDone` / `onError`（两个重载）/ `onStop` → `speaking = false`
   - ⚠️ **`speak()` 在 ready 分支里同步置 `speaking = true`**——不能只靠 listener 的 `onStart`，否则「`speak()` 之后立刻 `awaitQuiet()`」会读到尚未置位的 `false` 而抢跑；`stop()` / `shutdown()` 同样置 false。**但 `onStart` 也必须置回 `true`**：`speak()` 是 QUEUE_FLUSH，冲掉上一句时框架会先给上一句发 `onStop`（flushed from the queue 也走 onStop）→ 否则新这一句整段播放期间 `speaking` 都是 false，`awaitQuiet()` 立刻返回、自动前进抢跑（R8 要防的掐断）
   - `awaitQuiet()` = `withTimeoutOrNull(20_000) { while (speaking) delay(100) }`（上限防止 TTS 异常时永久挂起）。**不改** `speak` / `stop` 的既有签名与 `QUEUE_FLUSH` 语义
   - AppRoot：新增 `var advanceAfterSpeechSeq by remember { mutableStateOf(-1) }`；`answer()` 末尾（`tts.speak(...)` 之后）置为 `p.seq`
   - 新增 `LaunchedEffect(advanceAfterSpeechSeq)`：`seq < 0` 直接返回 → `delay(AUTO_ADVANCE_MIN_MS)`（1500L 兜底，TTS 不可用时也不瞬间翻走）→ `tts.awaitQuiet()` → 重读当前页：若 `pages[currentPage]` 不是该 `seq`（用户自己滑走了）或 `isScrollInProgress`，**放弃本次自动前进** → 否则 `animateScrollToPage(currentPage + 1)`（`currentPage < pages.lastIndex` 才动）→ **动画结束后（finally）置回 -1**
   - ⚠️ 置回 -1 **不能**写在 `animateScrollToPage` 之前、也不能把 `delay` / `awaitQuiet` 包进同一个 finally：前者会因「effect 体内写自己的 key」被自己重启取消（spec/frontend/state-management.md Gotcha (2)），翻页动画刚开始就胎死腹中；后者会在用户答下一张（key 换成新 seq）时把新 key 一并清成 -1。收尾要**比较后再清**（`if (advanceAfterSpeechSeq == seq)`），动画途中用户已答下一张时不得抹掉新 key
   - 只有 `answer()` 会置 key（新词卡不自动前进）；FREE 卡作答同样触发
7. **场景分区 = 专题自主练习（R9，design.md §5.6）**

   > ⚠️ 本步的**「分区形态」部分已被第 8 步（R10）整体替换**（滑完停住 + 轻提示、分区断点、分区作答三项作废）。本步仍然生效的只有三件：分区无完成卡、分区无前向拦截、分区不做到期筛选（pool 含该区全部词）。先按本步做完，再按第 8 步改。

   - StudyRepository：新增 `const val CHANNEL_DAILY = "rec"`；`scopeIds` / `buildQueue` 里硬编码的 `"rec"` 改用常量
   - StudyRepository.buildQueue：把已学词排序提为 `learned`（lapses 降序 → dueTime 升序），`due = learned.filter { isDue(...) }`；`else` 分支（分区）改为 **`learned + news`**——不做到期筛选，因此分区**不存在空队列**（这是「全学完未到期 → `pageCount == 0` 白屏」的根治，不是优化）
   - AppRoot `syncDonePage()`：开头 `if (channel != CHANNEL_DAILY) return`（分区不设完成卡）
   - AppRoot `blockingIndex(idx)`：开头 `if (channel != CHANNEL_DAILY) return -1`（分区自由划）
   - AppRoot 装载 effect：`restoreTo = if (channel == CHANNEL_DAILY) frontierIndex() else q.position.coerceIn(0, maxOf(0, pages.size - 1))`（`position` 落库时 clamp 到 `queue.size`，比 `pages.lastIndex` 大 1，需再夹一次）
   - AppRoot 翻页 effect：`saveQueuePosition(channel, if (channel == CHANNEL_DAILY) frontierIndex() else pagerState.currentPage)`
   - AppRoot：新增 `SCENE_TAIL_HINT`（视觉）与 `SCENE_TAIL_SPEECH`（播报）两个常量；TermCard 调用点传 `footerHint = if (channel != CHANNEL_DAILY && idx == pages.lastIndex) SCENE_TAIL_HINT else null`；播报处按**同一条件**把 `SCENE_TAIL_SPEECH` 拼在 `cardSpeech(p)` 之后——⚠️ 必须拼成**同一句** `speak`（分两次 speak 会被 `QUEUE_FLUSH` 互相掐断）
   - TermCard：新增可空参数 `footerHint: String? = null`，渲染在底部提示附近
   - DoneCard：不动（分区不再渲染它）
   - `appendFreePages` 的触发条件（`doneIdx >= 0`）无需改动——分区 `doneIdx` 恒为 -1，**自然停住**
   - prototype/index.html：`refreshDoneCard()` 在 `state.channel !== "rec"` 时直接返回（并移除可能已存在的完成页）；`blockSkippedCards()` 在 `state.channel !== "rec"` 时直接返回；`buildQueue` 场景分支保持「该区全部词」（原型无到期概念，天然对齐 R9）；队尾轻提示——可见卡为 feed 最后一张且非 `rec` 时，在卡片底部显示轻提示并入播报
8. **R10：自主学习 = 纯浏览的池型频道（design.md §5.7）**
   - 频道分两类：**队列型 = 仅每日任务频道**（`buildQueue`/`ensureQueue`/`appendQueue`/`markAnswered`/`saveQueuePosition`，全部去 `channel` 参数）；**池型 = 12 个场景分区 + 推荐频道的温故流**（运行时抽取、无限追加、不落库）。
   - **A. StudyRepository.kt**
     - 新增 `const val SCENE_NEW_WEIGHT = 2.0`；新增 import **`kotlin.math.ln`** 与 **`kotlin.random.Random`**（现有 import 里都没有）
     - `buildQueue()`：删 `channel` 参数 + 删 `else -> learned + news` 分支（改 `if (due.size >= DEBT_THRESHOLD) due.take(DEBT_QUOTA) else (due + news).take(DAILY_POOL_QUOTA)`）；`DailyQueue(today(), CHANNEL_DAILY, ...)`
     - `ensureQueue()` / `appendQueue()` / `markAnswered()` / `saveQueuePosition()`：删 `channel` 参数，内部固定 `CHANNEL_DAILY`；`ensureQueue` 复用条件简化为 `existing != null && existing.date == today()`（分区不再有队列；**每日任务的空队列是合法状态**——pool 空 → 队列只有完成卡 → 直接温故流，prd 已定义，不要「修」它）
     - `freePoolIds(channel)` **改名** `poolIds(channel)`：`CHANNEL_DAILY` → `scopeIds(channel).filter { termStates[it] != null }.shuffled()`（温故流，等概率，保持现状）；其余 → `weightedShuffle(scopeIds(channel))`（分区，含未学词）
     - 新增 `weightedShuffle(ids)` 与 `poolWeight(id)`（实现照 design §3.1 抄；`u = 1.0 - rnd.nextDouble()` 防 `ln(0) → +Inf`；`minOf(daysAgo, 60L)`；`termStates[id] == null` → `SCENE_NEW_WEIGHT`；`lastSeen` 解析失败按 `daysAgo = 0`）
     - `load()`：`queues` **只读入 `CHANNEL_DAILY`** 的条目（旧版本的分区队列在下次 `persist()` 自然消失，一次性清理、无需迁移代码）；`DailyQueue.channel` 字段保留
     - KDoc 同步：类头（v5 R10 段）、`DailyQueue`（队列型只此一个频道）、`buildQueue`、`scopeIds`、`poolIds`、`isSceneGraduated`（毕业只能靠每日任务推进）
     - ⚠️ **改完必须 grep 全部调用点**：`freePoolIds`、`ensureQueue(`、`appendQueue(`、`markAnswered(`、`saveQueuePosition(`（含 `MainActivity.kt` 等其它文件）——本机不能编译，漏改一处就是编译错误
   - **B. AppRoot.kt**
     - 删除：`SCENE_TAIL_HINT` / `SCENE_TAIL_SPEECH` 两个常量、`isSceneTail()` 函数、`footerHint` 传参
     - `FREE_BATCH` → `POOL_BATCH`；`appendFreePages` → `appendPoolPages`（内部改调 `repo.poolIds(channel)`）
     - 装载 effect（`LaunchedEffect(channel)`）分型：
       - 队列型：`val q = repo.ensureQueue()` → 现有 `taskPages` 逻辑不动 → `pages = taskPages` → `syncDonePage()` → `restoreTo = frontierIndex()`
       - 池型：`pages = emptyList()` → `appendPoolPages(POOL_BATCH)`（进区即有内容；池型池必非空）→ `restoreTo = 0`
       - 两分支共用：`freePool` 重置、`spokenKeys.clear()`、`advanceAfterSpeechSeq = -1`、`session = SessionStats(repo.todayKnown(), repo.todayForgot())`、`pendingAnnounce`
     - 翻页 effect 分型：队列型 `repo.saveQueuePosition(frontierIndex())` + 现有 `doneIdx >= 0 && idx >= doneIdx - 1 → appendPoolPages(POOL_BATCH)`；池型**不落库** + `idx >= pages.lastIndex - 1 → appendPoolPages(POOL_BATCH)`（无限流）
     - `cardSpeech`：在 `NEW` 分支之后、`revealed[p.seq]` 判定**之前**插入 `p.mode == CardMode.FREE -> termSpeech(p.t)`（否则浏览卡会播防泄题话术）
     - 点按分流：`examUnanswered` 从 `p.mode != CardMode.NEW && revealed[p.seq] != true` 收窄为 **`p.mode == CardMode.REVIEW && revealed[p.seq] != true`**
     - TermCard 调用点：`revealed = p.mode != CardMode.REVIEW || revealed[p.seq] == true`（FREE 恒展开）；删掉 `footerHint = ...` 这一行
     - KDoc：文件顶部第 5 / 8 / 9 / 10 条与 `Page` 注释同步（温故流与分区都是「池 + 浏览卡」）
   - **C. TermCard.kt**
     - `isExam = mode == CardMode.REVIEW`（原 `mode != CardMode.NEW`）——FREE 因此自动变成「全展开 + 无动作区」
     - 删除 `footerHint: String? = null` 参数与底部渲染块（连同「有 footerHint 时不渲染 SwipeHint」那个例外）
     - KDoc 同步（`peeked` / `onPeek` 保留但只剩复习卡可达）
   - **D. Common.kt**
     - `CardMode.FREE` 徽标文案「📖 温故」→「**📖 看看**」（同一徽标要服务温故流与分区浏览，分区含未学词，叫「温故」不成立）
     - `CardMode` 的 KDoc 同步（FREE = 自主浏览卡）
   - **E. prototype/index.html**
     - 删队尾轻提示（`refreshTailHint` / `SCENE_TAIL_HINT` / 并入播报的那句）
     - 分区（`state.channel !== "rec"`）：卡渲染为**浏览形态**（词 + 拼音 + 提示全展开、**无** √/×、**无**「想看答案」按钮、点卡重听）；**无限流**——可见到末尾时继续追加（等概率随机；原型无持久层 → 加权不可复现，注释写明这是唯一允许的偏差）；**不恢复位置**（每次进区从头）
     - 推荐频道：完成卡之后的**温故流**用**同一套池机制**补上（浏览形态 + 无限追加）
     - 保留：分区无完成卡、分区不拦截（R9）；`node --check` 过语法
9. **验证**
   - `cd android-app && ./gradlew assembleDebug`（Windows: `gradlew.bat assembleDebug`）
   - 手动场景走查（下）

## 手动场景走查清单

| # | 场景 | 预期 |
|---|---|---|
| 1 | 旧 JSON（无 lapses）加载 | 不崩，due 正常，lapses 视为 0 |
| 2 | 毕业词（days=15）满 3 连击 | 播报「30 天后再来复习」，分区 🎓 不消失 |
| 3 | 「忘了」次日重建队列 | 该词排 due 池前部（lapse=1 优先） |
| 4 | due ≥ 20 重建推荐队列 | 15 张全复习、无新词 |
| 5 | 复习卡未作答点卡片 | TTS 只说「再想一想，想起来了吗？」 |
| 6 | peek 后作答 | 走正常连击（peek 未写任何状态） |
| 7 | 新学卡/已作答卡点按 | 重听行为不变 |
| 8 | 一路快甩（含全程新词卡、一张没答） | 每次向前越界都被**静默**退回（无任何语音）；一次连滑**只退回一次**（不逐页拦）；`pages` / DOM 顺序不变；**不出现完成卡、无完成播报** |
| 9 | 站在未作答的复习卡上向前划 | 静默退回本卡；连击/间隔/answered/持久化队列**均未变化**；当前显示页不错位 |
| 10 | 快甩过一张未教读的新词卡 | 静默退回该卡；停稳 200ms 后触发教读（markSeen）与追加考核卡，之后可继续上滑 |
| 11 | 复习卡作答（√ / ×） | 播「词 + 提示 + 反馈」，**整句念完之后**才自动翻到下一张；答案与反馈不被掐断 |
| 12 | 作答最后一张待处理卡 | 自动翻到完成卡：「🎉 / 今日任务完成！/ 今天学完了 N 个词 · 认识了 x 次，忘了 y 次 / 继续上滑，随便看看」+ 同口径播报 |
| 13 | 播报期间用户自己上滑 / 下滑 | 本次自动前进被放弃，不抢方向；用户停在哪就是哪 |
| 14 | 新词卡 | **不**自动前进，`SwipeHint` 保留，由用户自己上滑 |
| 15 | 答若干卡后杀进程重进 | 落在 frontier（第一张待处理卡）；完成卡战果数字不归零 |
| 16 | 完成卡后继续上滑（自由刷） | 自由刷温故卡不计入待处理数、**不受拦截**；作答后同样自动前进；完成卡不因自由刷作答而消失 |
| 17 | 站在未作答的卡上**向下**滑 | **不受限制**，可自由翻回以前任意一张已处理的卡（只拦前向；未引入 `userScrollEnabled = false`） |
| 18 | 进入任一场景分区（如医院） | feed 里**没有**完成卡、也没有含完成语义的播报；一路向前快甩**不被退回**（R9 保留）；卡是**浏览形态** → 分区里**没有任何作答入口**（「忘了」这条路不可达，见 27） |
| 19 | 一个全部词都学过且都未到期的分区（刚毕业的分区） | 进入后有内容、**不白屏**；池 = 该区全部词（含未学词），顺序为加权随机（见 26） |
| 20 | 分区里一路滑到池末尾 | **继续追加**（无限流、抽完重洗）——**不出现「停住」**，也没有任何队尾轻提示（R10 取代 R9） |
| 21 | 分区滑到第 k 张后切频道再切回（或杀进程重进） | 从**第一张**开始（R10 D6：不恢复位置），且新一轮顺序与上次不同 |
| 22 | 在分区浏览若干张（含未学词）→ 回推荐频道做完每日任务 | 完成卡「认识了 x 次 / 忘了 y 次」**只来自复习卡作答**（R10 修正口径，分区浏览不计入）；N 仍只数每日任务区词数 |
| 23 | ~~同一张卡：分区里点「忘了」→ 回推荐频道~~ | **[R10 取代]** 分区不再有作答入口（见 24 / 27） |
| 24 | 进入任一场景分区（如医院）**看卡形态** | 词 + 逐字拼音 + 💡用途**全展开**；**没有** √/× 按钮，**没有**「👀 想看答案」按钮；点卡片直接念「词 + 用途」（**不播**「再想一想，想起来了吗？」） |
| 25 | 在分区里「看完」一个**未学过**的词，切回推荐频道做完每日任务 | 该词在推荐频道仍以**新词卡（✨ 新学）**出现——分区只看不算学；期间 `TermState` 对该词**没有新增条目** |
| 26 | 同一分区连进两次（切频道出去再进来） | 两次顺序**不同**；`lapses` 大的词与久未复习的词在统计上更靠前；**从第一张开始**（不是上次那一张） |
| 27 | 在分区里找作答按钮 | **找不到**——没有任何入口能写 `TermState` 或当日战果；「忘了」这条路在分区里不可达 |
| 28 | 分区一路向前滑到池末尾之后 | **继续追加**（无限流），不出现「停住」；**没有**任何队尾轻提示或完成语义 |
| 29 | 推荐频道做完每日任务 → 上滑进入温故流 | 卡是**浏览形态**（全展开、无按钮）；继续上滑无限追加；对战果数字无影响（完成卡的 x/y 不再增长） |
| 30 | 温故流里看完一个刚升到 30 天的词 | 该词 `days` 仍为 30、`lapses` 不变（不会被随手 √ 提前升级、也不会被随手 × 打回 1 天） |
| 31 | 每日任务频道全流程回归 | R1–R8 的既有走查项 1–17 **全部仍成立**；`isPending`/`frontierIndex`/`syncDonePage`/`blockingIndex` 的队列型行为一字未变 |
| 32 | 旧持久化（含分区 `queues` 条目）加载 | 不崩；分区条目被忽略并在下次落库时消失；每日任务队列照常复用（当日冻结） |

## 回滚点

- 第 8 步（R10）与第 7 步（R9）在文件上有重叠，但**改动方向相反**（第 7 步给分区加队列与轻提示，第 8 步把它们拆掉）。第 8 步做完后，`git revert` 第 8 步的提交即回到 R9 状态；若要保持「每日任务频道不受影响」的回滚，第 8 步里属于队列型的部分（`poolIds` 的 `CHANNEL_DAILY` 分支、`appendPoolPages` 的追加条件）都应保持与第 7 步等价的行为。
- 每步一个独立可编译状态；任意步出问题 revert 该步即可，无跨文件半成品依赖（StudyRepository 改动向后兼容 UI 旧调用；TermCard/AppRoot 改动向后兼容 repo 新字段——lapses 有默认值）。
