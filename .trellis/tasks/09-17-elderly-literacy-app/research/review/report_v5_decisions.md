# v5 决策轮独立复核报告（2026-09-17）

> 复核对象：QYJ 三项产品决策落地后的工作区状态
> —— 决策①（推荐频道不设每日新词配额，仅需求固化）、决策②（分区毕业改为纯进度标记）、决策③（删除设置页与生词本死代码）。
> 复核范围：**仅本轮改动**（不重审整个 v3）。检查者独立于实现者，逐符号人工核对 + 脚本化静态验证。
> **硬性约束遵守**：未修改任何源码。新增一个只读核对脚本 `research/review/check_v5_decisions.py`（审查产物，不改源码）。
> 方法：本机无 JDK / Android SDK，**无法编译**，所有结论均为静态核对并标注证据（文件:行号）。

---

## 结论

**通过，无 P0 / 无 P1。** 三项决策的代码实现正确、自洽，结构与符号层面可编译；`AppRoot` 去 `CompositionLocalProvider` 包裹后括号层级完全配平、行为等价；被删符号全仓 0 残留；`markKnown` 上一轮修复未被破坏。

- 本轮最易改错的一处（`AppRoot` 去掉 provider 包裹后的 `Column` / `charFor?.let` 层级）**已逐行核对通过**：两者都在 `AppRoot` 函数体内、depth=1、平级；`{}` 配平。
- 决策②的 `scopeIds` 合并语义正确：`buildQueue` 取全部词（368），`freePoolIds` 只取已学词，与合并前的 `freePoolScopeIds` 逐分支等价。
- 决策③的删除清单**逐项落实**，且「必须保留」项（`SessionStats.forgot` / `DoneCard(forgot=)` / `TTSSpeaker.setSpeechRate` 平台 API / `markForgot` / `appendQueue`）全部在位。
- 语速仍恒为 **0.85x**，不会退到平台默认 1.0x（全仓无任何 `.rate =` 赋值）。
- 发现 **2 项 P2**（均为文档漂移，不影响编译与运行）与 **2 项 P3**（残留旧口径）。
- 仍需 QYJ 在 Android Studio 实机构建验证（`implement.md` 步骤 11 强制评审门）——这是本报告最大的**未验证项**。

---

## 逐项验证表

| # | 验证项 | 结论 | 证据 |
|---|---|---|---|
| 1.1 | `AppRoot` 去 provider 后 `Column` 与 `charFor?.let` 平级、括号配平 | ✅ 通过 | 脚本配色：`AppRoot.kt` 251 行**全部配平**。层级扫描：`L212 Column(` depth=1、`L221 VerticalPager(` 为其子、`L245 }` 闭 Column、`L247 charFor?.let { ch ->` depth=1（与 Column 同级）、`L249 }` 闭 let、`L250 }` 闭 `AppRoot`。函数体内 depth=1 语句清单中 `L212` 与 `L247` 并列出现 |
| 1.2 | 删除的 4 个 import 确无残余引用 | ✅ 通过 | `AppRoot.kt:3-29` import 块已不含 `CompositionLocalProvider`/`mutableFloatStateOf`/`LocalDensity`/`Density`（v4→current diff 显示恰好删这 4 行）；全 `android-app/` 残留扫描这 4 个符号 **0 命中** |
| 1.3 | 保留的 import 每一个仍有使用点（无未使用 import 告警） | ✅ 通过 | `AppRoot.kt` 27 个 import 全部有使用点（`getValue`/`setValue` 为 `by` 委托隐式使用，`L57-71` 共 9 处 `by remember`）；`StudyRepository.kt` 6 个 import 全部有使用点；`StudyRepository` 本轮**未动 import 块** |
| 1.4 | 被删符号全仓无引用：`forgotIds`/`forgotCount`/`addForgot`/`setFontScale`/`setSpeechRate`/`setRemindTime`/`freePoolScopeIds`/`fontScale`/`speechRate`/`remindTime` | ✅ 通过 | 全 `android-app/` 扫描仅剩 3 处 `setSpeechRate`：`TTSSpeaker.kt:31/34/48` 的 `tts?.setSpeechRate(rate)` —— 接收者类型是 `android.speech.tts.TextToSpeech`，**不是**被删的 `StudyRepository.setSpeechRate`（已确认 `StudyRepository` 内 0 处） |
| 1.4b | 必须保留项未被误删 | ✅ 通过 | `SessionStats.forgot`：`AppRoot.kt:37,98,165,240`；`DoneCard(forgot=)`：`DoneCard.kt:27,40`；`markForgot`：`StudyRepository.kt:88-91`（仅删了 `forgot[id]=...` 一行）；`appendQueue`：`StudyRepository.kt:181-187` |
| 1.5 | `markKnown` 返回值类型变更后调用方全部适配 | ✅ 通过 | 全仓唯一调用点 `AppRoot.kt:159` `val newDays = repo.markKnown(t.id).days`（旧写法 `StudyRepository.nextInterval(...)` 已消失）。定义：`StudyRepository.kt:66` `fun markKnown(id: String): TermState`，`L73 return st`。`nextInterval` 仍被 `markKnown` 内部使用（`L69`），无死代码 |
| 1.6 | 删掉 `StudyRepository.speechRate` 后语速仍有确定取值（不退到 1.0x） | ✅ 通过 | `TTSSpeaker.kt:21` `var rate: Float = 0.85f`（唯一赋值来源=声明初始化）；`L31`（init 就绪）、`L34`（补播每条）、`L48`（每次 `speak()`）均 `tts?.setSpeechRate(rate)` 后再 `speak`。全仓 `\.rate\s*=` 赋值 **0 命中**，即 `rate` 恒 0.85f，且每次朗读前都显式下发 |
| 2 | 去 `CompositionLocalProvider` 是否行为等价 | ✅ 通过（含前置条件） | 前置条件成立：HEAD 版本 `setFontScale`/`setSpeechRate` **零调用点**（`git grep HEAD` 只命中定义 `:38-44`、`persist` `:56-57`、`load` `:70-71`），故持久化写回的就是默认 1f/0.85f → provider 恒等价于 `LocalDensity.current`，`tts.rate = 0.85f` 恒等价于 `TTSSpeaker` 默认值。逐行对比 v4→current：`ChannelBar`/`VerticalPager`/`TermCard`/`DoneCard` 调用体**逐字未变**，仅整体减一层缩进。`CharSheet` 从 `Column` 子项移到函数体：`ModalBottomSheet` 是窗口级弹层、不参与父级布局，且不使用 `ColumnScope` → 布局与视觉等价 |
| 3 | `scopeIds` 合并后 `buildQueue` / `freePoolIds` 语义仍各自正确 | ✅ 通过 | `StudyRepository.kt:121-123`：`rec` → `STUDY_TERMS.map { it.id }`；场景 → `filter { it.scene == channel }`。`buildQueue:132` 取全量 → 全部词（词库实测 **368 条 / 12 场景**，无 `scene="rec"` 词条、id 无重复）；`freePoolIds:202` = `scopeIds(channel).filter { termStates[it] != null }` → 只取已学词。与合并前逐分支等价：旧 `freePoolScopeIds("rec")` = `STUDY_TERMS.map{it.id}`、旧场景分支 = `filter{it.scene==channel}`，**逐字相同** |
| 3b | 决策①无代码改动（`news` 无上限） | ✅ 通过 | v4→current diff **未触及** `buildQueue`/`ensureQueue`/`appendQueue`/`saveQueuePosition`（无 hunk）；`L141 news = scope.filter { termStates[id] == null }.shuffled()` 后无 `take()`；全文件 `take(|quota|Quota|DAILY|limit` **0 命中** |
| 4 | 文档一致性 | ⚠️ 大体一致，2 处 P2 漂移 | 见下节「发现的问题」与「残留旧口径清单」。`prd.md:73-78`（第 7 条）/`prd.md:87`（验收）/`design.md:169-213`（§8.6）/`design.md:147-167`（§8.7）/`implement.md:24-27`（步骤 12-15）/`android-app/README.md:54` 均与代码实际行为一致 |
| 4b | `Common.kt:35` `ChannelBar` 注释及 `StudyRepository` 各处 KDoc 无旧口径 | ✅ 通过 | `Common.kt:33-36`：「已毕业分区…加 🎓 标记、文字转绿，但仍可点进去温故」——无"不排入推荐队列"。`StudyRepository.kt:106-111`「**仅供频道栏徽章展示，不影响队列范围**」、`:115-120`「推荐频道 = 全部词条，**含已毕业分区**」、`:95-99` 判定口径正确。`AppRoot.kt:53`「**毕业只是进度标记，不影响队列范围**」。全仓 grep「不再进推荐/排除毕业/不排入」在 `android-app/` 内 **0 命中** |
| 5 | 回归：调度 / 断点 / 朗读契约 / 自由刷 | ✅ 通过 | 调度：`buildQueue`/`ensureQueue` 逐行未变；断点：`saveQueuePosition:190-196` + `AppRoot.kt:125-129` jump 逻辑未变；朗读契约：`cardSpeech`(`AppRoot.kt:90-100`)、`spokenKey`(`:84-87`)、`snapshotFlow`+200ms 停稳(`:188-210`) 未变；自由刷：`appendFreePages`(`:132-143`) 未变、`freePoolIds` 语义等价（见 #3） |
| 5b | `markKnown` 每日升级闸门（上一轮修复）未被破坏 | ✅ 通过 | `StudyRepository.kt:66-74` 闸门完整：`val days = if (cur != null && cur.lastSeen == today()) cur.days else nextInterval(cur?.days ?: 1)`，并 `return st`。UI 取返回值（`AppRoot.kt:159`）。行为与 `sim_graduation.py` 验证的版本一致 |
| 5c | 已知但本轮未拍板的问题仍存在（仅确认，不重复报） | 确认存在 | 42sp 分支 `TermCard.kt:96`（`else -> 42.sp`，词库 ≥5 字共 **7 条**走此分支，含「三块五一斤」）；chip 触控热区偏小 `Common.kt:55`（`vertical = 14.dp`）；重现卡不可再答 `AppRoot.kt:156`（`revealed` 以 termId 为键）；TTS `stop()` 不清 `pendingQueue` `TTSSpeaker.kt:52-55`；`isDue(state, today: String = today())` 参数同名 `StudyRepository.kt:54` |

---

## 发现的问题（分级）

### P0（阻断）
无。

### P1（严重）
无。

### P2（文档漂移，不影响编译/运行）

**P2-1 「design.md §7 工程映射」仍以"已实现"口吻描述已被删除的代码**

| 位置 | 内容 | 与实际代码的冲突 |
|---|---|---|
| `design.md:77` | 「`StudyRepository.kt` **生词本 + 设置持久化**（SharedPreferences/JSON）+ `nextInterval`」；「`StudyData.kt` **36 词条（7 频道）**」 | 生词本/设置字段已删（`StudyRepository.kt:206-263` 的 `persist/load` 只剩 `terms`+`queues`）；词库已是 368 条 / 12 场景 |
| `design.md:78` | 「Sheets（字卡/**设置/生词本** ModalBottomSheet）」 | `Sheets.kt` 现只有 `CharSheet` |
| `design.md:79` | 「字体放大档：`CompositionLocalProvider(LocalDensity provides Density(density, fontScale * 1.15))` 全局乘系统缩放」 | 该包裹已删（`AppRoot.kt:212-250` 现为裸 `Column` + 平级 `charFor?.let`）；且 `* 1.15` 与 Android 代码**从来不一致**（原代码是 `base.fontScale * fontScale`，`fontScale` 恒 1f；1.15 是网页原型的值） |

§7 标题为「工程映射（v2 已实施 → `android-app/`）」，属 v2 历史记录，但正文用的是"**已实现**"现在时，且与同文件 §8.7（`:147-167`）**直接冲突**，正是 §8.7 立论要消灭的那种"文档写着有、代码里没有"的漂移。
建议：§7 加一行「**本节为 v2 记录，其中的生词本/设置持久化与字体放大档已被 §8.7 删除**」，或将三行改写。**（审查者未修改，等 QYJ 定。）**

**P2-2 网页原型仍带设置页与生词本，与 `prd.md` 的功能边界冲突**

- `prototype/index.html`：`--fscale` 变量（`:10,17-19`）、设置分段控件「正常/更大 🔎」「提醒时间」（`:204-205,214`）、`state.forgot` 生词本（`:307,556-576`）、`setFontScale`（`:582-584`）、`--forgot` 配色（`:14,108`）。
- `prd.md:104` 把「**无设置页、无生词本**（2026-09-17 决策）」列入 **Acceptance Criteria**；而 `prd.md:92` 又把 `prototype/index.html` 定为交付物。
- 本轮决策③的改动范围明确写的是 Kotlin（`implement.md:26` 只列 `StudyRepository`/`AppRoot`），故这更可能是**范围歧义**而非实现缺陷：决策是"Android 第一版不做"，还是"两端都不做"？
- 另注：`design.md:41`（§3 信息架构图「⚙️ 设置(右上)」）与 `design.md:50`（§4「忘了的词条自动进入**生词本**；顶部'我的生词本'入口」）描述的是**仍在运行的原型**，所以它们对原型是准确的——但这正是与 `prd.md:104` 冲突的那部分。
- 建议：QYJ 二选一 —— ①明确"仅 Android 不提供"，并在 prd 注明原型保留为历史版本；②把原型也同步删掉，消除 Acceptance Criteria 的悬空。

### P3（残留旧口径 / 提示）

**P3-1 `implement.md:21`（步骤 9）正文写的是被作废的旧方案**
> 「…推荐频道 `scopeIds` **排除已毕业分区**、自由刷池 `freePoolScopeIds` 不排除；`ChannelBar` 加 🎓 徽章」

而 4 行之后 `implement.md:25`（步骤 13，同为 `[x]`）写的是：「`scopeIds` 推荐分支**不再排除** `graduatedScenes()`；`scopeIds` 与 `freePoolScopeIds` **合并**为单个 `private fun scopeIds`」。步骤 9 的完成标记可以保留（当时确实这么做的），但正文应加「▲ 已被步骤 13 取代」，否则读者按步骤 9 检索会得到与代码相反的结论。

**P3-2 生效时机：跨日/当日队列的重建边界（提示，非缺陷）**
决策②改变的是 `scopeIds` 的**生成规则**，而 `ensureQueue`（`StudyRepository.kt:167-174`）只按 `date` 判定复用。因此：若某台设备**当天**已用旧规则生成了 `rec` 队列，升级到本版本后**当天内**仍会用旧队列（不含已毕业分区的词），要到次日（`date` 不符）才按新规则重建。此为该设计的自然结果（断点续刷优先），无需修，但如实机验证时看到"毕业分区当天仍不进推荐队列"，应知道是队列复用而非新规则失效。

---

## 残留旧口径清单

**代码侧：0 处。** `android-app/` 内已无任何"毕业分区不排入推荐队列"的表述，也无 `freePoolScopeIds`/`forgot*`/`fontScale`/`speechRate`/`remindTime` 符号（脚本残留扫描 0 命中）。

**文档/原型侧（按严重度排序）：**

| 位置 | 残留内容 | 级别 |
|---|---|---|
| `design.md:77-79` | §7「工程映射」：生词本+设置持久化 / Sheets 含设置与生词本 / `CompositionLocalProvider(... fontScale * 1.15)` 字体放大档 | P2（同 P2-1） |
| `prototype/index.html`（多处，见 P2-2） | `--fscale`、`setFontScale`、`#seg-remind`、`state.forgot`、生词本渲染 | P2（同 P2-2） |
| `design.md:41` / `design.md:50` | §3 图内「⚙️ 设置(右上)」、§4「自动进入生词本 / 我的生词本入口」 | P2 附带（对原型仍成立，与 prd 边界冲突） |
| `implement.md:21` | 步骤 9：「推荐频道 `scopeIds` 排除已毕业分区、自由刷池 `freePoolScopeIds` 不排除」 | P3（同 P3-1） |
| `design.md:115` / `design.md:209` | 提及 `freePoolScopeIds`（"与 `scopeIds` 合并为一个函数"） | 可保留 —— 属**有意**的命名对照，用于解释合并，不构成误读 |
| `design.md:171` / `design.md:200` / `prd.md:74` | 「不进退出池 / 退出池会让已学词彻底消失 / 不退出池」 | 可保留 —— 讨论的是**词级毕业**（明确不做），与分区毕业无关 |

---

## 未验证项

| 项 | 原因 | 需要的验证 |
|---|---|---|
| **编译通过**（本轮最关键未验证项） | 本机无 JDK / Android SDK，`./gradlew assembleDebug` 无法执行 | QYJ 在 Android Studio 构建（`implement.md` 步骤 11 强制评审门） |
| **Lint / typecheck** | 工程无 ktlint / detekt / `lintOptions` 配置（`android-app/*.gradle.kts` 内 grep 0 命中）；AGP 自带 `lint` 任务同样需 JDK | 随构建一并执行；结论不适用（N/A） |
| **运行期行为** | 无法启动 | 实机走查：①语速确为 0.85x；②`CharSheet` 从 `Column` 子项移到函数体后弹层视觉/层级无变化；③断点跳页无闪播错位 |
| **旧持久化数据兼容** | 未能实际注入旧 JSON 运行 | 静态推证：`load()`（`StudyRepository.kt:230-263`）已删除 `fscale`/`rate`/`remind`/`forgot` 读取分支，旧键被忽略、不抛错（整体包在 `runCatching` 内）→ 代码可推证；**实机迁移未验证** |
| **`.trellis/spec/` 规范符合性** | 该目录是**未填充的模板**（`frontend/index.md` 全部标 "To fill"，且是 JS 向的 hook/state-management），无 Kotlin/Compose 项目规范可对照 | 建议单独立项做 spec bootstrap；本项判为**不适用** |
| **词库内容质量** | 非本轮范围 | 已有 `pinyin_check.py` / `audit_wordbank.py` 覆盖 |

---

## 复核方法可复现说明

- 脚本：`research/review/check_v5_decisions.py`（只读）—— ①去注释/去字符串后括号配平 + `AppRoot` 函数体 depth=1 语句清单；②每文件 import 使用点检测（含扩展函数 `.name(` 与 `by` 委托运算符的豁免）；③`android-app/` 被删符号残留扫描。
  ```
  python .trellis/tasks/09-17-elderly-literacy-app/research/review/check_v5_decisions.py
  → FAIL 汇总: 0 项
  ```
- 本轮 delta 的隔离方法：`git archive HEAD android-app | tar -x -C /tmp/v4snap` 后套用 `research/review/_snapshot_v4.diff`（16:46 抓取的改动前快照），再与当前工作区逐文件 `diff -u`。
  - 说明：该 16:46 快照**早于**上一轮 `markKnown` 闸门修复（快照里的 `markKnown` 还是无闸门、无返回值的旧版），因此本 delta 同时包含了「上一轮修复」与「本轮三项决策」两部分。我按任务要求**只把上一轮修复当作回归项核对**（`#5b`，结论：完整保留、未被破坏），未对其重审。
- 词库计量：`WordBank.kt` 368 条 / 12 场景（bank 30、emergency 30、express 30、food 32、gov 30、hospital 32、market 30、medicine 30、phone 32、property 30、transit 30、weather 32），无重复 id，无 `scene="rec"` 词条 → 决策②下 `rec` 范围 = 368 条全量；`graduatedScenes()` 覆盖 12 个真实分区（与 `design.md:206`「全部 12 分区」一致）。
