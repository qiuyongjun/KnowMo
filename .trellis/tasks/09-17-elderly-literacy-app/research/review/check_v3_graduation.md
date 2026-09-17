# v3 第二轮独立复核 — 分区毕业机制（2026-09-17）

> 对象：工作区未提交改动（`git diff HEAD`，13 文件）。本轮重点 = ①`DoneCard` 补 `Box` 导入 ②2 处拼音 ③分区毕业机制 ④design.md 编号回 §8
> 方式：逐符号导入/签名核对 + 状态机穷举 + 队列调度路径走查（本机无 JDK/SDK，**未编译**）
> 约束遵守：**未改动任何被 git 跟踪的文件**；新增产物仅在本目录（`check_v3_*.py` / `*.out.txt` / `_head_*.kt|md` / `_tmp_diff.txt` / 本报告）

---

## 结论（约 380 字）

本轮 4 项改动**未发现 P0**（Box 导入、2 处拼音已确认修好；`ChannelBar` 只有 AppRoot 一个调用点、实参与形参同为 `Set<String>`；`GreenKnown`/`GreenBg` 已在 Common.kt 导入；全仓残留符号 0 命中）。

毕业机制本体**实现正确**：`days == GRADUATED_DAYS` 与「连续 3 次认识」的等价关系经状态机穷举成立（状态只能是 1/3/7/15，1→0 次、3→1 次、7→2 次、15→≥3 次，**无反例**）；空分区、未学词（无 TermState）、`rec` 自身三条边界都判 false；`GRADUATED_DAYS = INTERVALS.last()` 初始化顺序安全；当日队列复用使毕业只在次日生效、中途不会突变；毕业区词仍经场景频道与自由刷池可达，全部毕业后自由刷页在第 0 页即预追加，**不存在空转死角**。

**唯一的 P1 是语义强度**：`days` 记录的是"作答次数"而非"复习轮次"，而 `replay()`（完成卡"从头再看一遍"）会清空 `revealed`，使复习卡重新出现"认识"按钮 —— 同一个词一天内可被连续升 3 级，**一个分区在第 2 天刷 3 遍就能集体毕业**，与"1/3/7/15 天间隔"的意图不符（另见 P1-2：忘了词的当日重现卡因 `revealed` 恒真而不可再答，反向上锁死了同日补答）。

文档编号已连续（1–9，8.1–8.6），10 处 §引用全部指向存在节；prd §7 + 验收末条、design §8.6 效果表 6 条与代码逐条一致。

---

## 问题清单

### P0 — 会编译失败

**无。** 已核（证据见「已核无问题」1）。唯一编译风险提示：`AppRoot.kt:156`/`:198` 的 `snapshotFlow { … }.collect { … }` 未 import `kotlinx.coroutines.flow.collect` —— 依赖 kotlinx-coroutines ≥1.6 的 SAM 转换（`Flow.collect(FlowCollector)`，`FlowCollector` 是 `fun interface`）成立，判定可编译。**若 CI 报 `Unresolved reference: collect`**，改 `.collect(FlowCollector { … })`（该扩展函数在新版本已 `DeprecationLevel.HIDDEN`，**不要**再加 import）。

### P1-1 — `days=15` 可在同一天达成，"连续 3 次认识"≠"跨 3 个复习周期"

- **位置**：`AppRoot.kt:186-193`（`replay()` 清 `revealed`/`results`）、`AppRoot.kt:163-184`（`answer()`→`markKnown`）、`StudyRepository.kt:93-97`（`markKnown` 不看 `lastSeen`）
- **现象**：完成卡点"🔄 从头再看一遍"→ 全部复习卡恢复"认识/忘了"按钮 → 再点"认识"→ `days` 再升一级。到达 `days=15` 的最短路径实测为：第 1 天新词（`markSeen`→1，新词卡无按钮不可作答），**第 2 天刷 3 遍（1→3→7→15）**。按 `buildQueue` 语义，一个场景的词当天会同时就绪，故"30–32 词的分区在第 2 天集体毕业"是可达的。
- **依据**：`replay()` 只清内存态；`markKnown` 未做"每词每日只升一级"闸门；`revealed` 只在 `replay()` 里被清空（正常作答路径下按 termId 上锁，故同日跨频道重复作答反而不成立）。
- **影响**：验收标准"每个词都连续 3 次认识后毕业"的**时间语义被绕过**；也波及 prd 第 3 条"1→3→7→15 天升级"。
- **建议修法**：`markKnown` 加闸门 —— `val cur = termStates[id] ?: TermState(1, today()); if (cur.lastSeen == today()) return`（同日二次作答只更新反馈文案、不升级）；`AppRoot.kt:167` 的 `newDays` 改为 `markKnown` 之后从 `repo.termState(t.id)?.days` 读取，保证文案与实际一致。**需 QYJ 拍板**（会改变"忘了→当天补答"的语义，见 P1-2）。

### P1-2 — 忘了词的当日重现卡不可再答（上轮遗留 P1-1，本轮未改，独立复核确认仍成立）

- **位置**：`AppRoot.kt:165`（`revealed[t.id] = true`）、`:180`（重现卡复用同一 termId）、`:246`（`revealed = p.mode != CardMode.REVIEW || revealed[p.t.id] == true`）、`TermCard.kt:149`（`if (isReview && !revealed)` 才渲染按钮）
- **现象**：重现卡恒为展开态、无"认识/忘了"按钮 → 设计里"错词当天再见（可再考一次）"退化为纯复读。
- **依据**：`revealed` 以 termId 为键，答完即 true；重现卡 id 相同。
- **交叉影响**：这正是 P1-1 的另一面 —— 正常路径被上锁、replay 路径不设锁，两者都对不上"间隔复习"的意图。
- **建议修法**：`revealed` 改按队列位次（`"$channel:$index"`）或在重现页上带独立标记，仅共享 `results` 文案。

### P2-1 — 毕业后进入该分区场景频道可能只有 0–2 张卡

- **位置**：`StudyRepository.kt:141-155/162-196`（场景频道 `news` 为空 + `due` 只含到期词）；`AppRoot.kt:122`（`pages = … + Page.Done`）
- **现象**：毕业分区全词都在 15 天间隔上，日均仅 30/15 ≈ 2 词到期 → 常出现 `pages = [完成卡]`，文案"认识了 0 个，忘了 0 个"。
- **依据**：与 design §8.6"场景频道仍按同一调度器"一致，属设计结果而非实现错误；自由刷兜底可用。
- **建议**：场景频道队列为空时用自由刷池补 2–3 张「温故」页；或完成卡文案区分"今天没有到期复习"。

### P2-2 — 可逆性的"回潮量级"未在文档中标注

- **位置**：`StudyRepository.kt:141-147`（`scopeIds` 排除）+ `112`（`markForgot`→days=1）
- **现象**：毕业区某词被答"忘了"→ 分区退出毕业、次日回推荐队列时，该区**全部**词因 `lastSeen` 陈旧而同时到期（一次涌入 30–32 张）——与"回到推荐队列"一致，但反差大。
- **建议**：design §8.6 补一句量级说明，或 un-graduation 只回补该区 1/3 词。

### P2-3 — 性能：已核可接受，可选优化

- **位置**：`StudyRepository.kt:131-132`（`graduatedScenes`）← `:143` / `AppRoot.kt:131`
- **数据**：12 分区 × 368 词 = 约 4.4k 次 `scene` 字符串比较，每次作答调 1 次（`scopeIds` 侧每天每频道 1 次）。返回的 `Set` 走结构相等，值不变不触发重组。**无需优化**；若想更干净可在 companion 里 `STUDY_TERMS.groupBy { it.scene }`。

### P2-4 — 文档：`design.md` §4/§5 仍停在 36 词原型时代

- **位置**：`design.md:45-61`（"复习3+新学3""6 场景 × 6 词条"、`ReviewState{box, dueInDays}`）vs 工程 `TermState{days,lastSeen}` + 368 词
- **现象**：§8.1 已声明 `kind/days/REC_QUEUE` 作废，但 §4/§5 无指引，易被当作现行契约。
- **建议**：§4/§5 抬头各加一行"仅原型演示逻辑，工程实现见 §7/§8"。另 `implement.md:21`（步骤 9）只写了 `scopeIds` 排除，未提 `freePoolScopeIds` 不受影响（design §8.2/§8.6 已写）。

### P2-5 — 仅记录（非缺陷）

`isSceneGraduated` 的"空分区早退"分支在当前词库下不可达（12 个非 `rec` 分区各有 30–32 词）；`graduatedScenes()` 对 `rec` 有显式过滤 + `rec` 无词条双保险。防御性写法正确，保留即可。

---

## 已核 — 无问题（逐条）

**1. 编译可行性（逐符号）**
- `DoneCard.kt:5` 有 `import androidx.compose.foundation.layout.Box`，`:44` 使用 `Box(` ✓（P0 修复确认）；同批删除的 `Arrangement/size/AppSurface/GreenBg/GreenKnown/OrangeBg` 均无引用 ✓
- `Common.kt` 导入含 `Box`(:7)、`Surface`(:13)、`GreenBg`(:26)、`GreenKnown`(:27)，全部被使用 ✓；`Surface(color=, shape=)`、`Text(名称, fontSize=, fontWeight=, color=)`、三元组解构 `val (bg, fg, label)` 类型均对 ✓
- `ChannelBar` 全仓**唯一**调用点 `AppRoot.kt:233`，命名实参 `current/graduated/onSelect` 与 `Common.kt:38` 形参一一对应；`graduated` 由 `mutableStateOf(emptySet<String>())` 解构为 `Set<String>`，与 `graduatedScenes(): Set<String>` 赋值兼容 ✓
- 残留符号扫描（android-app 全量）：`ProgressRow`/`REC_QUEUE`/`Term.Kind`/`.kind`/`onReady`/`ProgressTrack`/`RedBg`/`BadgeDot`/`index`-`total` 参数 **0 命中** ✓
- 项目内 import 全部可解析（4 个 `com.qyj.shibang.*` 跨包文件 + 同名声明均在各自作用域内，无重定义）；同包跨文件符号 `SCENES/STUDY_TERMS/TermState/CardMode/SwipeHint/sceneName` 均可解析 ✓
- `data object Done` 在 **when 表达式**中的分支写法与 HEAD 完全一致（HEAD 已由 CI 构建过），exhaustiveness 成立 ✓
- `q.copy(queue=, modes=)`、`DailyQueue(date,channel,queue,modes,0)` 位置参数顺序、`ArrayList<Page> + List<Page>`、`indexOfFirst { }.coerceAtLeast(0)`、`TermCard/DoneCard/CharSheet` 命名实参与形参一一对应 —— 类型与重载均核对通过 ✓

**2. 毕业逻辑与边界**
- 状态机穷举（`check_v3_static.py` B 节）：可达 `days` 仅 {1,3,7,15}；`days==15 ⟺ 连续认识次数 ≥3` **无反例**，且 1→0 次、3→1 次、7→2 次严格成立；从 null 直接 `markKnown`→3（记 1 次认识），仍需 3 次认识才到 15（该路径 UI 不可达，UI 上 REVIEW 卡必有 TermState）✓
- 空分区 → false；全词 `days=15` → true；**一个词缺 TermState 或 days≠15 → false**（未学词会阻断毕业，符合"每个词都…"）✓
- `termStates[it]?.days == GRADUATED_DAYS`：`Int? == Int` 编译合法，`null == 15` 为 false ✓
- `GRADUATED_DAYS: Int = INTERVALS.last()` 声明在 `INTERVALS` 之后（companion 初始化顺序安全）；`nextInterval(15)` 命中 `coerceAtMost(lastIndex)` 不越界；`nextInterval(非法值)` 走 `INTERVALS[1]` 不崩 ✓
- `graduatedScenes()` **不会**把 `rec` 判进去（显式 `it != "rec"` + `rec` 无词条）✓

**3. 毕业 × 队列持久化交互**
- `ensureQueue` 当日复用（`:200`）→ 毕业只在**次日**生效、徽章即时（design §8.6 已写明）✓；切频道 `LaunchedEffect(channel)` 只重载 `pages`，`LaunchedEffect(channel, session)` 只刷新徽章 → **不会当日中途重建队列** ✓
- 全部毕业后 `scopeIds("rec")` 为空 → `pages=[完成卡]`，但 `freePoolScopeIds` 保留全部已学词，且 `doneIdx-1 = -1` 使第 0 页即预追加自由刷页 → 推荐频道不会空转、无死角 ✓
- `lastSeen` 陈旧 → 只会让 `isDue` 恒为 true（`scopeIds` 已排除该区），`isDue`/断点还原均为幂等只读，**不产生状态不一致** ✓（量级副作用见 P2-2）
- 空场景频道不崩：`filterNotNull` + `Page.Done` 兜底 ✓

**4. 文档与代码一致性**
- `design.md` 编号 **1–9 连续、无重号**，### 8.1–8.6 连续 ✓（HEAD 的 `## 8. 取舍与风险` 已顺移为 `## 9`，无冲突）
- 全仓 10 处 `§x` 引用（AppRoot 4 处 + design 2 处 + implement 4 处）**全部指向存在的节** ✓
- prd v3 第 7 条（没有词级毕业 / 判定 / 效果①②③ / 生效时机）与验收末条，design §8.6 效果表 6 行，**逐条与代码对应**（`scopeIds` 排除、🎓+绿色、场景不受影响、自由刷不受影响、次日生效、可逆）✓
- `implement.md` 步骤 7/8/9 勾选内容与实际代码改动一致 ✓

**5. 其它（顺带复核）**
- 拼音：`express-16 扫码出库 = sǎo mǎ chū kù`、`weather-10 早晨 = zǎo chén` ✓；全库 368 条 `text.length == pinyin.split(" ").size` **全通过**，id 唯一、scene 合法、每分区 30–32 词、id 前缀与 scene 一致 ✓
- `.trellis/spec/**` 目前是空模板（无项目级规范条目）→ 规范符合性 **N/A**，本条无可查项
- `prototype/index.html` 的本次改动（朗读反馈文案、自动播放解锁、阈值 0.55）属上一轮，与本轮 4 项无关，未纳入本次核查结论

---

## 复核产物（均在本目录，未跟踪）

| 文件 | 用途 |
|---|---|
| `check_v3_static.py` / `.out.txt` | 词库结构 + Leitner 状态机穷举（毕业等价性）+ 边界 + 性能 + 残留符号 |
| `check_v3_symbols.py` / `.out.txt` | 逐文件大写标识符解析（找"漏网导入"，如 `Box`） |
| `check_v3_imports.py` / `.out.txt` | 项目内 import 解析 + 同文件同名声明扫描 |
| `check_v3_docs.py` / `.out.txt` | design.md 编号连续性 + §引用可解析 + 文档/代码逐条比对 |
| `_head_approot.kt` / `_head_design.md` / `_tmp_diff.txt` | HEAD 基线快照（用于比对 when 表达式写法、编号顺移） |

**仍未闭环**：真机编译与手感（TTS 打断、200ms debounce、chip 热区实测）只能由 QYJ 在 Android Studio 上执行（`implement.md` 步骤 10 未勾选）。
