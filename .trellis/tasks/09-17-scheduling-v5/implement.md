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
7. **验证**
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

## 回滚点

- 每步一个独立可编译状态；任意步出问题 revert 该步即可，无跨文件半成品依赖（StudyRepository 改动向后兼容 UI 旧调用；TermCard/AppRoot 改动向后兼容 repo 新字段——lapses 有默认值）。
