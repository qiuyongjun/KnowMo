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
   - TermCard 增 `onPeekHint: () -> Unit`（考试态未作答点卡回调）与 `peeked: Boolean` 参数
   - 主区域 clickable 按形态分流；底部提示文案条件化；考试态未作答区加「👀 想看答案」小按钮
   - AppRoot：`peeked` map（按 seq）；peek 回调只置 map 不写 repo；TermCard 调用点接线
3. **prototype/index.html 契约同步**
   - 考试卡点按不播读音（播「再想一想」）；加「想看答案」按钮（展开不写状态——原型本就无持久层，只改交互表现）
   - **完成卡条件出现**（保留）：`renderFeed` 只在待处理数为 0 时才渲染 `doneCardHTML`，否则队尾没有任何收尾页
   - **前向拦截取代「滑过即回收」**：**删掉** `appendChild` 回收与配套的 scroll-anchoring 位移补偿；滚动停稳（200ms 去抖）后，若当前可见卡之前仍存在待处理卡，则 `scrollTo` 回最靠前的那一张（`behavior:'smooth'`）并播一句解释。**DOM 顺序完全不动**
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
   - **前向拦截**（§5.2）：在停稳播报 effect 内、`spokenKeys.add` **之前**插入——`val block = blockingIndex(idx)`；`block >= 0` → 记下提示语（考试卡 / 新词卡两个文案常量，挂 `blockHint`）+ `pagerState.animateScrollToPage(block)` + `return@collect`。提示语**不当场 `speak`**（`speak` 是 QUEUE_FLUSH，会被退回目标卡停稳那句播报掐断），改在目标卡停稳时与 cardSpeech **拼成一句**播出。**不重排 `pages`、不写任何状态、不调 repo、被拦页的 `spokenKey` 不入 `spokenKeys`**
   - 播报 effect 的 key 保持只有 `pagerState`（body 仍会改 `pages`：教读追加考核卡、条件完成卡）
   - `cardSpeech` 的 `Page.Done` 收敛为单一形态（§5.1 文案）；`spokenKey` 用 `channel:seq:<seq>` / `channel:done`；`saveQueuePosition` 写 frontier
   - **不得**给 `VerticalPager` 设 `userScrollEnabled = false`（会连下滑回看一起锁死）；`key` 可保留，但其 KDoc 里「回收会错位」的理由要改成「页身份与列表位置解耦」
   - DoneCard：删掉 `pending` / `answered` 参数与分流分支，改传 `known` / `forgot` / `words`（N）
   - `session` 在装载时从 repo 初始化（跨重启不归零）
6. **验证**
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
| 8 | 一路快甩（含全程新词卡、一张没答） | 每次向前越界都被退回并听到解释语；一次连滑**只退回一次**（不逐页拦）；`pages` / DOM 顺序不变；**不出现完成卡、无完成播报** |
| 9 | 站在未作答的复习卡上向前划 | 退回本卡 + 播「先回答这张卡，再往上滑…」（该卡此前已播报过 → 只补解释、不重复念词）；连击/间隔/answered/持久化队列**均未变化**；当前显示页不错位 |
| 10 | 快甩过一张未教读的新词卡 | 退回该卡 + 播「先听一遍这张新词卡…」；解释语与该词的教读播报**合成一句**完整播出（不被 FLUSH 掐断）；停稳 200ms 后触发教读（markSeen）与追加考核卡，之后可继续上滑 |
| 11 | 队列真的处理完（新词教读过、复习词满 3 连击） | 完成卡出现在队尾：「🎉 / 今日任务完成！/ 今天学完了 N 个词 · 认识了 x 次，忘了 y 次 / 继续上滑，随便看看」+ 同口径播报 |
| 12 | 答若干卡后杀进程重进 | 落在 frontier（第一张待处理卡）；完成卡战果数字不归零 |
| 13 | 完成卡后继续上滑（自由刷） | 自由刷温故卡不计入待处理数、**不受拦截**；作答走双层状态机；完成卡不因自由刷作答而消失 |
| 14 | 站在未作答的卡上**向下**滑 | **不受限制**，可自由回看已处理的卡（只拦前向；未引入 `userScrollEnabled = false`） |

## 回滚点

- 每步一个独立可编译状态；任意步出问题 revert 该步即可，无跨文件半成品依赖（StudyRepository 改动向后兼容 UI 旧调用；TermCard/AppRoot 改动向后兼容 repo 新字段——lapses 有默认值）。
