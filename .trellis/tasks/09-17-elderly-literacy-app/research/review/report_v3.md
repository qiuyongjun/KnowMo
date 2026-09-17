# v3 迭代（未提交改动）代码审查报告

> 对象：工作区未提交改动（`git diff HEAD`），共 11 个文件（3 份文档 + 7 个 Kotlin + 1 个 HTML）
> 基线：HEAD = ad32d06（词库 368 词）
> 方法：逐文件 diff 走查 + 符号/导入静态核对 + 调度器算法复刻模拟
> 限制：**本机无 JDK/SDK，无法编译**，编译结论为静态推断（已逐符号核对）

---

## 0. 变更范围

| 类别 | 文件 |
|---|---|
| 文档 | `prd.md`（+v3 交互修订 6 条 + 5 条验收）、`design.md`（+§9 v3 设计）、`implement.md`（改为 v3 步骤清单） |
| 数据层 | `StudyRepository.kt`（+TermState/DailyQueue/调度器/自由刷池）、`StudyData.kt` 已在上个提交改完 |
| UI 层 | `AppRoot.kt`（调度接入 + 滑动即播）、`Common.kt`、`TermCard.kt`、`DoneCard.kt`、`theme/Theme.kt` |
| 系统层 | `MainActivity.kt`（edge-to-edge）、`tts/TTSSpeaker.kt`（pending 队列 + stop） |
| 原型 | `prototype/index.html`（朗读反馈文案 + 自动播放解锁 + 阈值 0.55） |

---

## 1. 🔴 P0：编译失败 —— `DoneCard.kt` 缺 `Box` 导入

`DoneCard.kt` 第 43 行使用 `Box(...)`，但本次改动把 `import androidx.compose.foundation.layout.Box` 删掉了，
且文件内没有通配导入：

```
DoneCard.kt:43        Box(                        ← 使用
DoneCard.kt 导入列表   （无 androidx.compose.foundation.layout.Box）
```

**结论：`unresolved reference: Box`，编译直接失败。** 这不是警告，是错误。

已核对同批删除的其它导入均**未被使用**（不构成问题）：

| 文件 | 删除的导入 | 是否仍被使用 |
|---|---|---|
| `DoneCard.kt` | `Arrangement` / `size` / `AppSurface` / `GreenBg` / `GreenKnown` / `OrangeBg` | 否（仅 `Box` 例外） |
| `Common.kt` | `Spacer` / `fillMaxHeight` / `height` / `width` / `Alignment` / `BluePrimary` / `ProgressTrack` / `Term` | 否 |
| `Theme.kt` | `RedBg` / `BadgeDot` / `ProgressTrack`（符号本体删除） | 否（全仓无引用） |

**修法**：`DoneCard.kt` 补回 `import androidx.compose.foundation.layout.Box`。

---

## 2. 🟠 P1：文档编号冲突（design.md）

同一文件出现**两个 `## 9`**，且出现 `### 8.4` 挂在第 9 节内部：

```
## 9. v3：每日队列调度与滑动即播
### 9.1 数据模型变更
### 9.2 队列生成
### 9.3 朗读契约
### 8.4 持久化时机      ← 应为 9.4
### 9.5 涉及文件
## 9. 取舍与风险        ← 与上面重号，应为 10
```

`implement.md` 与 `AppRoot.kt` 注释里引用的「design.md §8.2 / §8.3」也指向旧编号，现在实际是 §9.2 / §9.3。
**修法**：v3 节改为 `## 9`，`8.4`→`9.4`，原「取舍与风险」改为 `## 10`；同步修 `implement.md` 与代码注释里的 §8.x 引用。

---

## 3. 🟠 P1：配额 —— prd v3 的「先不限配额」在 368 词下已失效

`buildQueue` 把**全部未学新词**一次性放进队列。36 词时代这没问题，368 词时代：

| 频道 | 词条数 | 首日队列 |
|---|---|---|
| 推荐（rec） | 368 | **368 张** |
| 单个场景频道 | 30–32 | 30–32 张 |

抖音式 feed 没有进度条、没有配额，用户无法知道「还剩多少」，也无法在中途获得「今天完了」的正反馈。
模拟（`quota_sim.md`，算法照抄 `buildQueue`/`markKnown`/`markForgot`）：

| 每日新词配额 | 学完全部 368 词 | 前 7 天卡片 | 第 8–30 天日均 | 稳态日均 | 峰值 |
|---|---|---|---|---|---|
| 5 | 74 天 | 82 | 24 | 38 | 61 |
| 8 | 46 天 | 133 | 40 | 39 | 66 |
| **10（建议）** | **37 天** | 169 | 49 | 38 | 76 |
| 20 | 19 天 | 334 | 71 | 37 | 109 |
| 368（当前） | 1 天 | **1250** | 46 | 38 | **368** |

**建议**：推荐频道加每日新词配额（8–10 词），场景频道不必（单场景一天可完成）。

---

## 4. 🟠 P1：15 天是封顶不是毕业 —— 稳态负荷永不归零

`nextInterval(15) = 15`（`INTERVALS[(i+1).coerceAtMost(lastIndex)]`）。词学完之后**每 15 天必然回到队列，永不停止**。

| 已学词数 | 每天固定复习量 |
|---|---|
| 36 | 2.4 张/天 |
| 200 | 13.3 张/天 |
| 368 | **24.5 张/天** |

模拟稳态 ~38 张/天（含新词），且**与配额大小无关** —— 配额只改变多久走完全部词，不改变稳态高度。
**正确率敏感性**：认识率 60% → 稳态 65 张/天；70% → 49；80% → 38；90% → 30。老人成功率偏低时复习债会显著堆高。

含义：老人的「今天学完了」永远在 ~25 张之后才到，**没有真正的终点**。
**需明确**：这是有意设计（长期记忆维持）还是疏漏。若是疏漏，应加毕业机制（连对 N 次后间隔拉到 30/60 天，或移出当日队列只留自由刷）。

---

## 5. 🟡 P2：跨频道重复计数

`ensureQueue` 按频道各存一条 `DailyQueue`；`scopeIds("rec")` = 全部 368 词，`scopeIds(scene)` = 该场景 30 词。
→ **同一个到期词会同时出现在推荐队列和它所属的场景队列里**，两个频道各算一次，且两边都推进同一个 `TermState`。

举例：老人在推荐频道答对「挂号处」（days 1→3），切到「医院」频道当天可能再见到它 —— 此时它的 `lastSeen` 已是今天，`isDue` 为 false，
所以**不会重复进队列**（这点实现是对的）。但若在推荐频道**还没答**就切频道，它仍会出现在医院频道 —— 这是合理的，可接受。

真正的问题是：两频道共享 `TermState`，用户在 A 频道答完后在 B 频道看到同一词的**不同卡型**（A 是复习、B 因同一天重建可能仍是复习，但已 revealed）。
**建议**：prd 里写明「跨频道共享学习状态，队列独立」这一语义，避免后续被当成 bug 反复改。

---

## 6. 🟡 P2：完成卡战果只统计「本次会话」

`session`（known/forgot）是 `remember` 的内存态，重启后归零；而 `TermState` 是持久化的。
→ 断点恢复后走到完成卡，会显示「认识了 0 个」，与用户实际当天成果不符。
**建议**：完成卡战果改为从 `TermState` 按 `lastSeen == today` 统计，或明确接受「会话制」并写进文档。

---

## 7. 🟡 P2：生词本已成死代码

`2aafd58`（界面极简化）已移除生词本入口，但仓库层仍保留：

| 符号 | 状态 |
|---|---|
| `StudyRepository.addForgot(id)` | **无任何调用**（`AppRoot` 已改用 `markForgot`） |
| `forgotIds` / `forgotCount(id)` | **无任何调用** |
| `forgot` map + `persist()` 里的 `"forgot"` 字段 | 仅被 `markForgot` 写入，无人读 |

「忘了的词」这一功能现在由 `appendQueue`（当天尾部重现）+ `TermState.days=1`（明天到期）承担。
**建议**：要么删掉 `addForgot`/`forgotIds`/`forgotCount` 与持久化里的 `forgot`，要么明确保留为「生词本」未来能力的预留并加注释。

---

## 8. ✅ 静态核对通过项

以下均已逐符号核对，未发现残留引用或签名不匹配：

- **无残留引用**：`REC_QUEUE`、`Term.Kind`、`term.kind`、`term.days`、`ProgressRow`、`RedBg`、`BadgeDot`、`ProgressTrack` — 全仓 0 命中。
- **签名匹配**：`TermCard(term, mode, revealed, resultText, onSpeakTerm, onCharClick, onAnswer)`、`DoneCard(known, forgot, onReplay)`、`TermBadge(mode)`、`CharSheet(ch, terms, onSpeak, onDismiss)` — 调用处与定义处一致。
- **新增符号齐备**：`CardMode{NEW,REVIEW,FREE}`、`SwipeHint()`、`TermState`、`DailyQueue`、`MODE_NEW/MODE_REVIEW`、`DAY_MS`、`isDue/markKnown/markSeen/markForgot/ensureQueue/appendQueue/saveQueuePosition/freePoolIds` — 全部有定义且被调用。
- **导入完整**：`snapshotFlow`、`delay`、`statusBarsPadding`、`navigationBarsPadding`、`JSONArray` — 均已补入。`TermCard.kt`/`Common.kt`/`AppRoot.kt`/`StudyRepository.kt` 的导入与用法一致。
- **持久化读写对称**：`persist()` 写 `terms`/`queues`，`load()` 读回并带「`modes` 与 `queue` 等长」兜底（旧数据缺省按 REVIEW）—— 这条兜底写得好。
- **断点恢复不回考试态**：`if (mode == REVIEW && repo.termState(id)?.lastSeen == q.date) revealed[id] = true` —— 逻辑正确。
- **`markSeen` 的时机设计正确**：新词卡没有反馈按钮，「学过」的时机 = 首次停稳自动播报；缺这一步词永远没有 `TermState`，复习债与自由刷池都无从产生。
- **TTS pending 队列修复到位**：由「只存最后一条」改为 `ArrayDeque` 按序补播 + 去重 + 上限 4 条；`QUEUE_FLUSH` 用于打断，`QUEUE_ADD` 用于补播 —— 语义分得清。

---

## 9. ⚠️ 两端同步：网页原型仍是旧的 36 词

| 项 | 安卓 `WordBank.kt` | 网页 `prototype/index.html` |
|---|---|---|
| 词条数 | **368** | **36**（旧词库） |
| 含有的新词条 | — | 抽样命中 49 / 368 |

新增的 12 个场景里，`phone`/`medicine`/`express`/`property`/`emergency`/`weather` 六类词条网页端**完全没有**。
网页端本次只改了朗读反馈文案与自动播放解锁，**词库没同步**。

这正是上一轮方案文档里指出的问题：**两份硬编码手工同步必然漂移**。现在漂移已经发生。
**建议**：按 `wordbank_plan.md` 的路线，改成 TSV/单一数据源 + 脚本生成两端。

---

## 10. 结论与整改清单

| 级别 | 问题 | 修法 |
|---|---|---|
| **P0** | `DoneCard.kt` 缺 `Box` 导入 → 编译失败 | 补 `import androidx.compose.foundation.layout.Box` |
| **P1** | `design.md` 两个 `## 9` + `### 8.4` 挂错节 | 重编号；同步 §8.x 引用 |
| **P1** | 368 词首日队列 = 368 张，prd「不限配额」前提失效 | 决策：加每日新词配额（建议 8–10） |
| **P1** | 15 天封顶无毕业 → 稳态 ~38 张/天永不停 | 决策：是否加毕业机制 |
| **P2** | 跨频道共享 `TermState` 语义未写明 | prd 补一句说明 |
| **P2** | 完成卡战果只算本次会话 | 改从 `TermState` 统计或写进文档 |
| **P2** | `addForgot`/`forgotIds`/`forgotCount`/`forgot` 死代码 | 删除或标注预留 |
| **P2** | 网页原型词库未同步（36 vs 368） | 转单一数据源 + 脚本生成 |

> 除 P0 外，其余均为设计/一致性问题，**不影响编译**（P0 除外）。
> 本报告不含真机实测结论 —— 调度时序、TTS 打断、滑动即播的真实手感需 QYJ 实机验证。
