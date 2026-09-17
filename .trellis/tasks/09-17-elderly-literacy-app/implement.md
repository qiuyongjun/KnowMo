# 执行计划 — 老年人识字辅助应用（原型阶段 v2 · 抖音式 feed）

> v1 导航式原型作废（评审指示），v2 重做为抖音式 feed。
> v2 原型 + 工程首版（android-app）已交付。当前迭代 = v3（见 design.md §8，prd.md「v3 交互修订」）。

## 交付物

`prototype/index.html` — 单文件高保真交互原型（**已冻结于 v2**，2026-09-17 决策 C：不再迭代、不再作为工程基准）。
`android-app/` — Kotlin + Compose 工程版（**当前唯一交付线**，v2 已交付，v3 迭代中）。

## v3 步骤清单（当前迭代）

1. [x] `StudyData.kt`：删除 `Term.kind`/`Term.days` 静态字段与 `REC_QUEUE`（调用方同步清理）
2. [x] `StudyRepository.kt`：新增 TermState（days/lastSeen）+ DailyQueue（date/channel/queue/modes/position）持久化；到期计算（lastSeen+days <= today）；调度器 `buildQueue(channel)`（到期复习优先+未学新词洗牌交错，见 design.md §8.2）；自由刷池
3. [x] `AppRoot.kt`：接入调度器；朗读契约改造（design.md §8.3：复习卡不念词、停稳 200ms debounce、snapshotFlow）；欢迎语简化；页面模型加自由刷卡/动态 kind
4. [x] `TermCard.kt`：去 ProgressRow 调用与 index/total 参数；badge 支持动态 kind（新学/复习/温故）
5. [x] `DoneCard.kt`/`Common.kt`：去 ProgressRow；完成卡文案（"下面随意看看，温故知新"）；若 ProgressRow 不再被引用则删除
6. [x] 静态检查：无残留 REC_QUEUE/kind 引用；JSON 持久化字段读写对称
7. [x] 审查修复（2026-09-17 审查 GLM 改动后）：`DoneCard.kt` 补回 `Box` 导入（原缺失导致编译失败）
8. [x] 词库校验：`WordBank.kt` 修正 2 处拼音 —— 扫码出库 `má`→`mǎ`、早晨 `chen`→`chén`（其余 9 处与 pypinyin 的差异经逐条判定为 GLM 正确的轻声/变调标注，不改）
9. [x] 分区毕业机制（design.md §8.6 / prd.md v3 第 7 条）：`StudyRepository.isSceneGraduated/graduatedScenes` + `GRADUATED_DAYS = INTERVALS.last()`；~~推荐频道 `scopeIds` 排除已毕业分区、自由刷池 `freePoolScopeIds` 不排除~~ → **该排除逻辑已被步骤 13 推翻**（毕业改为纯标记，`freePoolScopeIds` 随之删除）；`ChannelBar` 加 🎓 徽章（AppRoot 传 `graduated`）保留
10. [x] 每日最多升一级（design.md §8.1）：`markKnown` 加 `lastSeen == 今天` 闸门并返回 `TermState`；`AppRoot.answer` 播报实际天数。修 trellis-check 发现的 P1 —— `replay()` 清空 `revealed` 后，同一天可把 days 刷到封顶，使分区毕业绕过 1/3/7/15 间隔
11. [ ] QYJ 在 Android Studio 构建 + 实机验证（本机无 JDK/SDK，无法本地编译）
12. [x] 决策①（2026-09-17 拍板）：**推荐频道不设每日新词配额**固化为正式决策 —— `buildQueue` 保持 `news` 无上限，不加任何 quota 逻辑；关闭「368 词首日 368 张 = 缺陷」待决项
13. [x] 决策②：分区毕业改为**纯进度标记**（design.md §8.6）—— `scopeIds` 推荐分支不再排除 `graduatedScenes()`；`scopeIds` 与 `freePoolScopeIds` 合并为单个 `private fun scopeIds(channel)`，`buildQueue` 与 `freePoolIds` 共用；`isSceneGraduated`/`graduatedScenes`/`GRADUATED_DAYS` 保留（仅供频道栏 🎓 徽章，已补 KDoc 说明）；`AppRoot` 毕业注释同步
14. [x] 决策③：删除设置页与生词本相关死代码（design.md §8.7）—— `StudyRepository` 删 `forgot`/`forgotIds`/`forgotCount`/`addForgot`、`fontScale`/`setFontScale`、`speechRate`/`setSpeechRate`、`remindTime`/`setRemindTime` 及 `persist()`/`load()` 的 `fscale`/`rate`/`remind`/`forgot` 四字段（`markForgot` 保留，仅删写生词本那一行）；`AppRoot` 删 `fontScale`/`speechRate` 状态、`LaunchedEffect(speechRate)`、`CompositionLocalProvider(Density(...))` 包裹与 4 个 import，「忘了」文案去「已加入生词本」
15. [x] 本轮静态自检：（a）Grep `forgot`/`fontScale`/`setFontScale`/`speechRate`/`setSpeechRate`/`remindTime`/`freePoolScopeIds`/`addForgot` 在 `android-app/` 内无残留（仅剩保留项：`SessionStats.forgot` 完成卡战果计数、`DoneCard` 的 `forgot` 参数、`TTSSpeaker.setSpeechRate` 内部应用）；（b）逐文件核对增删 import 无漏删/无未用；（c）确认 `AppRoot` 去 provider 后 `Column` 与 `charFor?.let { CharSheet(...) }` 平级、括号层级配对

16. [x] **原型冻结（2026-09-17 决策 C）**：`prototype/index.html` 保留为 v2 交互演示的历史版本，**不再同步 v3、不再作为工程基准**。文档同步：`prd.md`「原型范围与边界」加冻结声明 + Goal 补「交付现状」+ AC「原型冻结为工程基准」条款作废 + AC「无设置页/生词本」限定为 Android 版；`design.md` §1/§3/§4 标注为 v2 描述；`implement.md` 交付物改为「唯一交付线 = android-app」并关闭词库同步待决项。

## 验证方式

- 本机静态审查（Grep 残留引用、逐符号核对导入与签名、逻辑走查到期/断点/隔天三条路径）。
- 拼音：`research/review/pinyin_check.py` 全库 368 条逐字与 pypinyin 对照 + 无声调清单全量人工过目。
- 调度负荷：`research/review/quota_sim.py` 复刻调度器算法做 180 天模拟（配额/正确率敏感性）。
- 实机构建验证由 QYJ 执行：编译通过 + 走查 v3 验收标准（prd.md）。

## 评审门（Review Gate）

- 步骤 11 为强制评审点：QYJ 实机确认后才能 commit 收尾 / 进入下一迭代。

## 已知未决（不在本轮范围）

- ~~**推荐频道首日队列 = 全部未学新词（368 张）**~~ → **已关闭（2026-09-17 决策①）**：推荐频道不设配额为正式决策，队列长度由用户自己决定何时停。
- ~~**网页原型词库未同步**（仍 36 词 vs 安卓 368 词）~~ → **已关闭（2026-09-17 决策 C）**：原型冻结于 v2，不再同步；`research/vocab/wordbank_plan.md` 的「单一数据源 + 脚本生成两端」路线仅在原型需要复活时启用。
- **完成卡战果只统计本次会话**（重启归零）。
- ~~**`addForgot`/`forgotIds`/`forgotCount` 已成死代码**~~ → **已关闭（2026-09-17 决策③）**：随生词本、设置项一并删除。


## v4 步骤清单 —— 第二轮（纯计数，已实现后被推翻，留档）

> 2026-09-17 第二轮拍板并实现（TermState 改 knowCount、删间隔阶梯、buildQueue 未学取 10 + count<3 已学、
> FREE 卡可作答、DoneCard 文案）。同日第三轮修订定稿**连击 + 间隔双层**（prd.md「v4 交互修订」），
> 上述实现需按下表返工。第二轮改动中**仍然有效**的部分：FREE 卡可作答、DoneCard"今日任务完成"、
> appendQueue"未移除即追加"框架、seq 按出现记录展开态。

## v4 步骤清单 —— 第三轮返工（当前迭代）

> 需求与口径见 prd.md「v4 交互修订」（第三轮定稿）、design.md §9。

1. [x] `StudyRepository.kt` 数据层：`TermState` 恢复 `{days, lastSeen}`（JSON `days`/`lastSeen`；第二轮 `count` 弃读，days 缺省 1）；新增 `DayState {date, counts, seen}`（当日连击计数 + 教读去重，隔天作废）；`DailyQueue` 增 `answered: List<Boolean>` 平行数组；恢复 `INTERVALS`/`GRADUATED_DAYS`/`nextInterval`/`isDue`/`DAY_MS`
2. [x] `StudyRepository.kt` 状态机：`markSeen` 改幂等 Boolean（写 seen，不写 TermState）；`markKnown` 双层——count+1 封顶 3（常量定名 `DAILY_COMBO_TARGET`），满 3 时无状态写 `{1,今天}`、有状态且 lastSeen≠今天升一级、lastSeen==今天不升级（闸门），返回 `(count, upgraded, daysAfter)`；`markForgot` 双层——count=0 + 写 `{1,今天}`；新增 `dayCount(id)`/`isRemovedById(id)`/`markAnswered(channel, index)`（命名偏离原清单 `knowCount`——为满足"grep knowCount 无残留"自检项，语义相同）
3. [x] `StudyRepository.kt` 调度器：`buildQueue` 重写——due（isDue，按到期日升序）优先 + 未学洗牌，推荐 `take(10)`、场景全量；queue 原序、modes 标注、answered 全 false；`appendQueue` 同步追加 answered，**返回类型 Boolean→Int**（-1=未追加 / 队尾下标，供 AppRoot 作 qIndex；trellis-check 修复了初版 off-by-one P0：copy 后新元素下标 = 旧 size，误返回旧 size−1）；`freePoolIds` 不变；`isSceneGraduated` 改 `全词 days == GRADUATED_DAYS`
4. [x] `AppRoot.kt`：`TermPage` 携带 `qIndex`（自由刷页 -1）；`answer()` 走双层状态机 + `markAnswered` + 文案（剩余次数；满 3 且升级时追加"N 天后再来复习"，days==15 封顶再满 3 视为未升级不播天数、但刷新 lastSeen 进入 15 天复核周期）；NEW 卡停稳改 `if (repo.markSeen(id)) appendReviewCard(t)`；断点恢复 `revealed` 改按 `q.answered[i]` 逐卡恢复（修复第二轮按词级 lastSeen 判定导致同词多卡连击卡死的缺陷）；注释同步 §9
5. [x] `TermCard.kt`/`DoneCard.kt`/`Common.kt`：注释语义核对，无行为变化
6. [x] 静态自检 + trellis-check 复检：prd v4 六条验收逐条对照通过；§9.2 状态机逐格一致；持久化读写对称（含旧数据兜底）；符号/签名/括号配平全过；**P0 已修**（见步骤 3）。P2 遗留：`StudyData.kt`/`Common.kt` 两处第二轮口径注释过期（禁改清单内，下轮文档同步一并修正）、`dayCount` 可收 private、app 不重启跨零点沿用旧队列至切频道（少见场景，不阻断验收）、断点恢复不还原上次反馈文案（可接受降级）
7. [ ] QYJ 实机验证 v4 验收标准（prd.md）——**评审门**（v3 步骤 11 一并验证）。2026-09-17 QYJ 改走 **GitHub Actions**：v3+v4 已合并提交 `c0b0fd8`（76 files，含 .trellis 文档与研究产物），待 QYJ 经 VS Code push 后 CI 构建出 APK 走查验收。

## v4 验证方式

- 本机静态审查（Grep 残留、导入核对、上面第 6 条四条路径逻辑走查）。
- 实机构建验证由 QYJ 执行（本机无 JDK/SDK）。

## v4 口径备忘（QYJ 拍板，2026-09-17 第三轮定稿）

- 连击层：当天同一个词累计 3 次「认识」→ 移出当日队列；任意一次「忘了」计数清零。计数跨频道共享。
- 间隔层：满 3 移除时升一级（1→3→7→15），每日最多升一级；「忘了」days=1。未满 3 的作答不写 TermState。
- 推荐频道每日 10 词 = 到期复核优先（按到期日升序）+ 新词补足；场景频道不另设配额。
- 自由刷/场景自主复习作答同样生效（连击/降级照算，受闸门约束）。
- "重新学习" = 只重置计数与间隔（days=1），不回退为未学词。

## 回滚点

- git 提交粒度 = 迭代；出问题回退上一提交。

