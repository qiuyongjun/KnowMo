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


## v6 步骤清单（2026-09-20：隐藏设置入口 + 收藏分区，见 prd.md v6 / design.md §11）

1. [x] `data/AppSettings.kt`（新建）：SharedPreferences `app_settings` + 单 JSON（order/hidden/quota）；`visibleScenes()` / `setSceneVisible()` / `moveScene()` / `quota()` / `setQuota()`；默认 = 全可见、原顺序、10
2. [x] `StudyRepository.kt`：`CHANNEL_FAV`；favorites 有序持久化（state JSON 顶层 `favorites`，旧数据缺省空）；`favorites()/isFavorite()/toggleFavorite()`；`scopeIds("fav")`/`poolIds("fav")`（weightedShuffle）；构造函数注入 `quotaProvider: () -> Int`，`buildQueue` 改读之（DAILY_POOL_QUOTA 保留为缺省值）
3. [x] `StudyData.kt`：`sceneName("fav")` → "收藏"（fav 不进 SCENES）
4. [x] `Common.kt` `ChannelBar`：签名改 `scenes` + `onOpenSettings`；渲染 推荐→收藏→可见分区；推荐 tab 连点 5 次（间隔 ≤2s 重置）回调 `onOpenSettings`
5. [x] `TermCard.kt`：右侧竖排五角星（isFavorite/onToggleFavorite，自消费点击，≥64dp，全卡型可见）
6. [x] `ui/SettingsScreen.kt`（新建）：全屏设置页（配额单选 3/5/10/15/20 + 分区显隐开关 + ↑↓ 排序 + 完成按钮；适老化约束）
7. [x] `AppRoot.kt`：fav 频道走池型分支（零改动验证）；空收藏插入引导页（Page.Guide + 播报）；星按钮接线（toggleFavorite + 重组）；showSettings/ChannelBar/SettingsScreen 接线（打开播报「已打开设置」；当前频道被隐藏回退 rec）；MainActivity 组装 quotaProvider
8. [x] 静态自检：Grep 无 DAILY_POOL_QUOTA 直接引用残留（buildQueue 除外）；favorites/settings 读写对称；graduatedScenes 不含 fav；星按钮不冒泡到卡片重听；括号/导入核对（详见 v6 汇报：DAILY_POOL_QUOTA 仅剩 companion 定义 + quotaProvider 缺省 lambda 两处，均为有意保留）
9. [x] 抽卡算法统一（2026-09-20 QYJ 追加拍板，prd v6 第 4 条 / design §11.3）：`poolIds` rec 分支从 `shuffled()` 改 `weightedShuffle`（保留已学词过滤，D4 新词入口唯一化），三分支抽卡算法统一；AppRoot/StudyRepository 注释同步
10. [ ] QYJ 实机验证 v6 验收标准（prd.md）——**评审门**

## v7 步骤清单（2026-09-20：直接考试 + 总结确认 + 布局修正 + 单字直读，见 prd.md v7 / design.md §12）

1. [x] `StudyRepository.kt`：删 markSeen/wasSeenToday/appendQueue/DayState.seen（旧 JSON seen 键忽略）；DailyQueue + `confirmed`（缺省 false，读写对称）+ `markConfirmed()`。**偏离记录**：markKnown 由 v6 R12 连击（满 3 才写间隔层）改回首答定调度（作答即写，prd v7 第 1 条明文口径）——每词每日恰好一张考试卡后连击满 3 永远不可达，不改则间隔层永不写入、调度死亡；SM-2/ease/restoreTo/闸门/f30 全部保留
2. [x] `AppRoot.kt`：browseMode 会话态 + rec 装载 confirmed 分支；删 taught/appendReviewCard/markSeen 调用/charFor/CharSheet/blockingIndex 及拦截分支；isPending 收窄；userScrollEnabled 锁滑；onConfirmDone（markConfirmed + 清 pages + 温故流 + 播报）；browseMode 短路 syncDonePage；NEW 考试卡播报文案（NEW_EXAM_SPEECH）；onSpeakWord 接线（读词不带提示）；顶部 KDoc 契约同步；翻页落库 effect 按 browseMode 分流（browseMode 不写队列位置、走池型无限追加）
3. [x] `TermCard.kt`：isExam = mode != FREE；onCharClick → onSpeakWord()；FlowRow 换行 + 字号档（64/54/44/36）+ 词区/拼音区 end 56dp 预留星位
4. [x] `DoneCard.kt`：onConfirm 参数 + 大号确认按钮（92dp ≥ 88dp，居中大字）；去「继续上滑」文案与 SwipeHint（锁滑后误导）
5. [x] 删除 `ui/Sheets.kt`（仅含 CharSheet）
6. [x] 静态自检：Grep markSeen/wasSeenToday/appendQueue/CharSheet/charFor/blockingIndex/taught 零残留（含注释，README.md 除外——禁改清单内）；when 穷尽（Page 三分支）；导入核对（删 TermChar、加 FlowRow/ExperimentalLayoutApi）；括号配平（脚本核验全部 .kt = 0）；JSON 读写对称核验（confirmed put/opt 各 1、seen 只在注释出现）；不碰包名 com.knowmo.app 与 prototype/
7. [ ] QYJ 实机验证 v7 验收标准（prd.md）——**评审门**（与 v6 步骤 10 一并验证）

## v8 步骤清单（2026-09-20：连击回归 + 最小间隔插入 + 全显拼音 + 封顶分级，见 prd.md v8 / design.md §13）

1. [x] `StudyRepository.kt`：DayState.counts 恢复连击计数（markKnown `min(3, +1)` / markForgot `0`）+ `comboCount(id)`；markKnown 返回 Triple(count, upgraded, daysAfter)、markForgot 返回 count；新增 `repeatCard(id, afterIndex)`（MIN_GAP=2，insertAt=min(afterIndex+1+MIN_GAP, size)，queue/modes/answered 三数组同步插入 + persist，返回 insertAt）；f30 去重（`id in currentDay().counts` 跳过 recordF30）；`nextInterval(prevDays, ease, lapses)` 签名改造 + `INTERVAL_CAP_NORMAL=30` / `INTERVAL_CAP_MATURE=60`（ease≥2.5 且 lapses==0 → 60）+ `DAILY_COMBO_TARGET=3`；KDoc 全部按 v8 口径校准。**执行记录**：首轮子代理网络断线（copilot.tencent.com 502×3），主体由子代理完成、companion 常量与注释收尾由主会话补齐
2. [x] `AppRoot.kt`：answer() 连击分流（count<3 → insertRepeatCard：repeatCard + pages 同下标插 TermPage(REVIEW)；播报按剩余次数分级：1→再认对 2 次 / 2→再认对 1 次 / 3→学会啦+升级天数；忘了→再学一遍且当日必重现）；cardSpeech 统一 termSpeech（删 EXAM_TAP_HINT_SPEECH / NEW_EXAM_SPEECH / examUnanswered 分流）；删 peeked/onPeek；revealed 收窄为已作答；顶部 KDoc 契约同步 v8
3. [x] `TermCard.kt`：删考试隐藏态与 peek 按钮（所有卡恒全展开，拼音/提示无条件渲染）；√/× 仅 mode != FREE 渲染；badge 新学/复习保留；DoneCard.kt 过期注释同步
4. [x] 静态自检：Grep EXAM_TAP_HINT_SPEECH / NEW_EXAM_SPEECH / peeked / onPeek / showAnswer 零残留（仅 KDoc 历史记录有意保留）；repeatCard/nextInterval/markKnown/markForgot 调用点签名全部匹配；括号/圆括号配平脚本核验 12 个 .kt 全平衡（research/brace_check_v8.py）；counts JSON 读写对称（persist/load 未动，结构不变）；插入点不变量走查（pages 任务卡区与 queue 一一对应，Done 卡共存 pending>0 不可达）
5. [ ] QYJ 实机验证 v8 验收标准（prd.md）——**评审门**（与 v6/v7 评审门一并验证）

## 面馆词库并入「吃饭」分区（2026-09-20，QYJ 新需求；**不占 v 编号**）

> ⚠️ 本节**不叫 v9**：v9 已被 QYJ 的迭代占用（prd.md:230「任务卡静默自评 + 完成卡上滑转浏览 +
> 推荐范围收窄 + 常用词默认分区」，其实现见 `WordBank.DAILY_COMMON` / `StudyRepository.CHANNEL_COMMON`）。
> 本节记录的是与那条迭代并行的**纯词库内容探索**，改动只有词条与计数，无调度逻辑。

> 需求原话：妈妈在成都面馆工作（主营炸酱面/米线/抄手），要一个面馆场景词库作为生活分区。
> **同日改判**：先按独立分区 `noodle` 接入（34 条），随后 QYJ 指示「把面馆的内容移动到吃饭」——
> 独立分区撤销，词条并入 `food`。属**追加词条**、不新增分区，prd.md 未列。
> 词条内容、收录口径、两次落位沿革与判定依据见 `research/vocab/scene_noodle.md`。

1. [x] 词条创作与机器校验（`research/vocab/`）：34 条面馆词组 —— 招牌面名 9 / 抄手加料 3 / 分量 3 / 辣度 4 / 成都行话 4（免青·加青·干拌·宽汤）/ 客人用语 1 / 店内流程 4 / 后厨食安 4 / 打烊与告示 2。校验项：字数↔音节配对、id 唯一性、scene 字段一致、组内重词、pypinyin 对照、3500 常用字覆盖（超纲仅 `烊`，与既有 `驿` 同档）。脚本 `validate_scene.py` 可复跑（对原稿）。
2. [x] 两处人工判定并留档：**「担担面」`dān`→`dàn`**（因旧时挑担 dàn 叫卖得名，《现代汉语词典》/汉典；pypinyin 判对、初稿判错）；**「牛肉面」→「鸡杂面」**（前者与既有 `food-1` 完全重词，改用更具成都本地性的浇头名，同时净增一个词面孔）。有意保留 2 处不改 pypinyin 的语流变调：`干拌 gān bàn`（与库内「干净」同源）、`不要辣 bù yào là`（与库内「不要乱动」沿用同一注音惯例）。
3. [x] ~~第一版接入：新增独立分区 `noodle`（`SCENES` + `NOODLE` 34 条 + `STUDY_TERMS`），全库 402 条 / 13 分区~~ —— **已按步骤 6 撤销**，留档备查；此版从未 commit、从未构建。
4. [x] trellis-check 独立复核第一版（Agent 形式）：**P0 无、P1 无**。确认 402 条 `scene` 全在 `SCENES` 内（`ICON.getValue` 无启动崩溃）、逐字配对 402/402、既有 368 条零改动零删除、id 全库唯一、`AppSettings` 补尾兼容确无数据迁移、ChannelBar 零 UI 改动、🥢=U+1F962 与在用 🛒 同属 Unicode 9.0（API 26 可渲染）。
5. [x] 连带修正第一版被改动**变旧**的 3 处注释：`AppRoot.kt:88`、`StudyRepository.kt:88`「12 个场景分区」→13；`StudyRepository.kt:529`「n ≤ 368」→402。
6. [x] **第二版（现行）：并入 `food` 分区** —— 删 `Scene("noodle", …)`、删 `NOODLE` 列表、`STUDY_TERMS` 还原；33 条以 `food-33` … `food-65` 追加在 `FOOD` 末尾（原 `food-1`…`food-32` 未动），插入处留一行来源注释；KDoc 改「12 场景 401 词条」；README 计数 402→401、场景数 13→12、频道清单去「面馆」；步骤 5 的 3 处注释回退（场景数回 12、`n ≤ 401`）。
   - **「微辣」剔除**：`food-2` 已是「微辣」，同分区内两条一样的词 = 学习单元重复（硬缺陷），故并入 33 条而非 34。辣度留 `中辣`/`特辣`/`不要辣` 三条。
   - id 重编号为 `food-33`…`food-65` 安全：`noodle-*` 从未 commit、从未构建，不存在已落盘的学习状态（`TermState` 按 id 挂靠）。
7. [x] 不变量复核（`check_wordbank_invariants.py`，通用脚本，取代第一版的一次性脚本 `check_noodle_integration.py`）：总词条 **401**、`SCENES` 13 项（rec + 12）、`food` 65 条、逐字配对 401/401、id 全库唯一、**孤儿 scene 0**、分区内重词 0、id 前缀 == scene 0 处不符、跨分区重词 1 组（`身份证` bank-16 / gov-2，既有）、pypinyin 差异 11 处（全库既有口径，非本次引入）。**结构性不变量全过**。
8. [ ] **QYJ 待裁定（2 项）**：
   - ① `README.md` 在 v8 步骤 4 的禁改清单内，两次接入均为**文档同步**而动 —— 依据项目文档观「行为变了必须同步文档，否则视为待修缺口」，但确属越出 v8 派单范围，需 QYJ 认可或回退。
   - ② README 其余 v7/v8 漂移**不在本次范围**，未动：仍列 v7 已删的 `ui/Sheets.kt`、缺 `AppSettings.kt`/`SettingsScreen.kt`、「无设置页、无生词本」与 v6–v8 实装矛盾。是否另起一轮同步。
9. [ ] 实机验证：本机无 JDK/SDK，**未编译**。需随 v6/v7/v8 评审门一并走查 —— 「吃饭」频道词条数变 93 后抽卡/队列表现正常、面馆类词条的朗读与逐字拼音正确。

### 面馆补充词（2026-09-20 追加，**已定稿并接入 v11**）

QYJ 两轮输入：
- 第一轮「面馆相关的词还不够，比如牛肉面、大碗、小碗、少盐、少辣椒、不要香菜、不要葱、不要鸡精、味精等」，
  并明确要求**先审查再添加** → 出 v1（43 条平铺）。
- 第二轮给出**筛选口径**：「这些那些通常是口诉的，那些通常接触到是文字？感觉忌口和口味和调味品名词比较重复，学会调味品就差不多了」
  → 出 v2，按渠道重新分桶。

**v2 确立的口径（本任务的核心判据，后续补词沿用）**：
> **这个词会不会以文字形式出现在她眼前、需要她认出来。**
> **文字桶（T）**：印在包装/罐子上、挂在墙上、打在单子上 → 收录价值高，这正是识字要解决的问题。
> **口语桶（S）**：只在人嘴里来回传，她不看这些字也能干活 → 收录价值低，不收。

**v2 名单：51 条候选 = 文字桶 38（建议收）+ 口语桶 13（判定不收）**
- T1 后厨调料干货 12：盐 / 糖 / 醋 / 味精 / 鸡精 / 花椒 / 胡椒 / 酱油 / 香油 / 料酒 / 淀粉 / 豆瓣酱（**「学会调味品就差不多了」的落点**）
- T2 食材与浇头 7：香菜 / 葱花 / 泡菜 / 酸菜 / 煎蛋 / 冰粉 / 凉糕
- T3 规格与价目 6：小碗 / 半份 / 加面 / 清汤 / 红汤 / 原汤
- T4 告示与证照 6：招牌 / 价目表 / 营业中 / 自助调料 / 明厨亮灶 / 卫生许可证
- T5 后厨与桌前物件 7：围裙 / 抹布 / 洗洁精 / 保鲜膜 / 一次性筷子 / 牙签 / 纸巾
- S1 忌口与口味 11 + S2 分量说法 2 → **整组不收**（v1 的 A 组 11 条整句由此降级；B 组的 `小碗/半份/加面` 因在价目表上是字而上移 T3，`少面/加汤` 降级）

- 三档规模曾供选：最小 20 / 推荐 38（T 全收）/ 全套 43（+外卖备注 5）。
- 材料：`research/vocab/scene_noodle_extra.md`（审查稿 + 裁定记录）、`scene_noodle_extra.fragment.kt`（接入来源记录，逐字节比对通过）、
  `_scene_noodle_extra_check.md`（机器校验）、`build_scene_noodle_extra.py`（单一数据源，改一处三份产出同步）。
- 校验：51/51 逐字配对；与当时词库精确重词 **0**；3500 常见字**零超纲**；无声调拼音 1 处（`筷子` 的子＝轻声，正确）；pypinyin 差异 7 处全部判定为**有意保留**（6 处「不」写本调 `bù` ＋ 1 处「一」写 `yī`，均与库内既有词条同惯例）。
- **QYJ 举例中 2 个库里已有，不重复收录**：`牛肉面` = `food-1`、`大碗` = `food-26`（同分区重词＝硬缺陷）。

**QYJ 裁定（2026-09-20，三条）**：
- ① **按推荐方案收** —— 文字桶 38 条全收。
- ② 店里有外卖，但**不单收备注整句** —— 备注都是围绕食物/调料的说法，**名词已在库中，重复无益**（收了「香菜」就不收「不要香菜」）。
- ③ **不拆独立分区** —— 「不管吃面、还是在馆子上班，学习这些词都是没问题的」，仍并入 `food`。

**执行结果（v11）**：38 条作为 `food-66` … `food-103` 并入 `FOOD` 末尾 → `food` 55 → **93 条**，全库 379 → **417 条**。
同步 4 处计数：WordBank KDoc（升 v11、417）、`README.md` L35/L36、`StudyRepository.kt` L543（`n ≤ 417`）。
`food` 空号 `[3,7,9,17,25,31,32,50,55,59]` **未回填**（id 永不复用，回填会让新词继承旧学习状态）。
trellis-check 复核：**P0 = 0**；38 条与来源片段逐字节 `identical True`；417/417 配对；孤儿 scene 0；分区内重词 0。

**P1 已修（QYJ 点头，2026-09-20）**：
- `food-26`「大碗」说明原为器皿义（「装面装汤的家伙」），与新增 `food-85`「小碗」的份量义（「分量小的那种碗」）**同一根轴两侧释义打架**。已改为「**分量大的那种碗，饭量大的点这个**」——与 `小碗` 对仗、与 `一两/二两/三两/半份` 成组。**只改 `tip` 字段**，text / pinyin / id 一字未动（不影响 `TermState`）。
- （`盐`/`糖`/`醋` 单字词条随「推荐」方案一并认可，已接入。）

### 遗留观察项（不阻断，供下轮决策）

- **`food` 分区 93 条**（12 个场景分区里最大，其余 17–32 条），同一频道内既有下馆子吃饭的词、也有面馆行话与后厨食安。**QYJ 已明确裁定不拆**（2026-09-20 第 ② 条），故不再作为待办，仅记录规模事实。
- **原稿的语义分组线索**：`FOOD` 尾部保留了两行来源注释（`v11` 块按调料罐/菜单/价目表/告示/物件分 5 组）。池型频道加权随机抽卡、展示顺序本就随机，不影响使用。
- ~~`food-16 不辣` 与 `food-50 不要辣` 的教学语义重叠~~ → **已随 v10 口水话清理消失**：`food-50 不要辣` 已被 QYJ 删除（口说类）。现库内仅存 `food-16 不辣`。

## v12 步骤清单（2026-09-20：家电分区词库 + 新增分区默认隐藏口径）

> 需求：QYJ「添加一个家电分区、主要包含各种电器上可能出现的文字，帮助老人学习各种电器的使用」；
> 追加拍板「家电分区默认隐藏是对的，不管新装还是升级，都需要设置开启分区才行」。
> 设计见 design.md §15；词库契约见 `.trellis/spec/frontend/wordbank-guidelines.md`。

1. ✅ 词库：`WordBank.kt` 新增 `APPLIANCE` 63 条（`appliance-1..63`，12 组）+ `STUDY_TERMS` 并入；`StudyData.kt` SCENES 插 `Scene("appliance","家电","🔌",0xFFF0F4C3)`（property 与 emergency 之间）。全库 417 → 480。
2. ✅ 计数同步 4 处：`WordBank.kt` KDoc（升 v12）/ `README.md` 两行 / `StudyRepository.kt` L89、L543 / `.trellis/spec/frontend/component-guidelines.md` 池型契约。
3. ✅ 词库校验：`research/vocab/check_wordbank_invariants.py` → `terms=480` / `fails=0` / `orphan=[]` / `intra_dup=[]`。
4. ✅ 留档：`research/vocab/build_scene_appliance_doc.py` → `scene_appliance.md`（**由 `WordBank.kt` 派生**，不是手工原稿，故不构成回退风险）。
5. ✅ **设置口径（本轮唯一行为改动）**：`AppSettings.load()` L104/L124 —— 存储 `order` 里不存在的分区默认隐藏（design.md §15.1）。无 schema 变化、无迁移。
6. ✅ 静态校验：`research/vocab/check_appsettings_load.py` → `_appsettings_load_check.md`。两段：① **静态断言 6 条**（代码里真有那条判定 + 判定必须排在 `hidden.clear()` 之后；防「文档写了代码没写」和「上移即静默失效」）② **语义模拟 8 个场景**（新装机 / 升级自 v6–v8 / 升级自 v9–v11 / 用户已开启 appliance / 全开 / order 缺失 / 未知 id / order 类型错），每个场景同时算新旧口径并对比差异。结果 `static_fails=0 case_fails=0`；S2/S3/S7 有差异证明**改动确实生效**，S4/S5 无差异证明**用户选择不被覆盖**。
7. ✅ **校验脚本自身的加固 + 变异测试**（质检 agent 实测出来的洞）：脚本首版把差异列算成 `new - old`，而断言写的是 `old != new` —— 该条件**照样为真**，于是每行差异都显示「无」、报告却全绿。已把断言改绑到**差异列本身非空**，并加 `_mutate_test_diffdir.py` 做变异测试（把方向改回反向 → 期望失败）：实测 `case_fails=3`（恰好 S2/S3/S7），未变异版本仍绿。**教训：断言必须绑定「呈现出来的那个量」，否则拦不住它自称要拦的回归。**
8. ✅ 质检整改（trellis-check，P0=0）：修本清单自身漏掉的 2 处旧口径 —— `design.md` §14.2「新分区自动出现在设置页**与频道栏**」加作废标注；`prd.md` v12 验收 #2 补限定（此前已开启过分区的机器升级后**这些分区保持可见**，与 §15.2「用户显式选择优先」对齐）。另在 `AppSettings.persist()` 加前提注释（`order` 必须是完整全集，§15.1 的推理依赖它）。
9. ⬜ **实机走查（本机无 JDK/SDK，未编译）**：
   - 频道栏默认只有推荐 + 收藏（新装 / 升级两条路径都验）；
   - 设置页能看到「家电」分区且可开启，开启后重启仍可见；
   - `appliance-32 请勿用水冲洗` 是**全库第一条 6 字词条**，首次触发 TermCard 的「≥6 字」布局分支（36sp + `FlowRow` 换行 + 右缘 56dp 星位预留）。
10. ⬜ **待 QYJ 裁定（不阻断）**：`android-app/README.md` 既有漂移仍未处理（L43 仍列 v7 已删的 `Sheets.kt`、缺 `AppSettings.kt`/`SettingsScreen.kt`；L51「点单字开字卡弹层」v7 已删；频道清单未含「家电/常用词/收藏」；L56「无设置页、无生词本」与 v6–v12 实装矛盾）。另注意：`git ls-files` 显示 `app/src/main/java/com/knowmo/**` **整个源码树未被 git 跟踪**（`com/qyj/shibang/*` 显示为 `D`）—— 提交前需先 `git add` 新路径。
