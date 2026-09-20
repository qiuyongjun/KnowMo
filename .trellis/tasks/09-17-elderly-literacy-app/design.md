# 技术设计 — 老年人识字辅助应用（原型阶段 v2 · 抖音式 feed）

> v2 变更：交互范式从"底部 Tab + 分页导航"改为"抖音式垂直 feed"；学习单元从单字改为生活词组；移除手写/拍照；复习内嵌 feed。v1 的多屏导航方案废弃。

## 1. 原型形态

> **原型已冻结于 v2（2026-09-17 决策 C）。** 本节及 §2–§6 描述的是 **v2 原型**的设计，
> 与 v3 工程实现（§7–§8）已有差异：词库规模（36 → 368）、队列调度（静态 `REC_QUEUE` → 每日调度器）、
> "第 x/y 张"进度条（有 → 已删）、设置页/生词本（有 → 已删）、复习卡朗读（念词 → 不念词）。
> 这些差异**已知且有意保留**，原型不再同步、不再作为工程基准。
> **工程实现的当前状态以 §7 / §8 为准。**

单文件 HTML/CSS/JS 交互原型（`prototype/index.html`），手机框（412×915）居中，浏览器直接运行，无外部依赖。

**语音**：Web Speech API（`speechSynthesis`，zh-CN，默认语速 0.85）。工程阶段替换为 Android `TextToSpeech`，交互契约不变。

## 2. 设计系统（Design Tokens）

| Token | 值 | 适老化依据 |
|---|---|---|
| `--font-term` | 56–64px（更大字体档 72px+） | 词条主字 |
| `--font-pinyin` | 26px | 逐字拼音 |
| `--font-body` | 22px | 用途说明等正文 |
| `--font-button` | 24px 700 | 反馈按钮 |
| `--color-primary` | #1565C0 | 深蓝，白字对比 8.6:1 |
| `--color-accent` | #E65100 | 深橙，"忘了"以外的主动作 |
| `--color-known` | #1B5E20 / `--color-forgot` #B71C1C | 复习反馈双按钮（绿/深红） |
| `--color-bg` | #FFFFFF / `--surface` #F5F7FA | 浅底深字 |
| 触控目标 | ≥64px；反馈按钮 ≥88px | 防误触 |
| 翻页 | scroll-snap y mandatory | 每滑一次正好一张，无半张卡 |

## 3. 信息架构（v2 原型：1 屏 + 2 弹层；v3 工程版为 **1 屏 + 1 弹层**，见 §8.7）

```
┌─────────────────────────────┐
│ 频道Tab（横滑）：推荐 买菜 公交 医院 银行 政务 餐厅 │ ← 常驻顶部，点按切换+播报
├─────────────────────────────┤
│                             │
│   Feed（垂直滑动，snap 吸附）  │ ← 每屏一张学习卡
│   · 新学卡：图标+词组+逐字拼音  │    进入视口自动朗读
│     +用途说明，点任意处重听     │
│   · 复习卡：🔁角标（距上次N天） │    先出词组不出释义
│     → 认识/忘了 大按钮         │    → 展开完整内容
│   · 字卡弹层：点单字看详情      │    拼音/组词/例句
│   · 完成卡：🎉 表扬+重看一遍    │
│                             │
│                  ⚙️ 设置(右上) │ ← 字体/语速/提醒/记忆曲线说明
└─────────────────────────────┘
```

## 4. Feed 队列与记忆曲线（**v2 原型演示逻辑**；工程实现见 §8）

- **推荐频道**队列 = 到期复习项 + 新学项交替穿插（演示：复习3 + 新学3，F-N-F-N-F-N 交错）+ 结束卡。
- **场景频道**队列 = 该场景词条（含 1–2 个标"复习"），同样以结束卡收尾。
- 词条携带 `ReviewState{box, lastSeen}`：认识 → box+1（间隔 1→3→7→15 天）；忘了 → box 归 1（明天再来）。原型中反馈后角标即时更新为"3 天后再见"/"明天再来"，让评审直观看到调度效果。
- ~~"忘了"的词条自动进入**生词本**；feed 顶部"我的生词本"入口（红色数字角标）打开简单列表，点读。~~ → **仅 v2 冻结原型保留此入口**；工程版已删除生词本（§8.7），"忘了"改由每日队列承担：当天队列尾部重现一次 + `days` 打回 1（次日再考）。

## 5. 数据模型（演示数据，对应工程实体）

```js
TermChar { c, p }                       // 单字 + 拼音
Term     { id, text, chars: TermChar[], tip, sceneId, icon, kind: 'new'|'review', interval } // 学习单元=词组
Scene    { id, name, icon, color }
ReviewState { termId, box, dueInDays }  // 工程阶段入库；原型内存演示
```

演示数据：6 场景 × 6 词条（组合如"地铁站""今日特价""挂号处""输入密码""请签字""加一双筷子"），每条 2–5 字 + 逐字拼音 + 一句生活用途。

## 6. 关键交互契约

1. **自动朗读**：卡片进入视口（IntersectionObserver ≥60%）→ 朗读词条 + 拼音首字提示；同一张卡只自动播一次，重复进入不重复播。
2. **点按重听**：卡片任意空白处点按 → 重听。单字区点按 → 字卡弹层（弹层内自动发音）。
3. **复习卡流程**：初始态出词组+两按钮 → 点"认识/忘了" → 展开完整内容（拼音/用途/朗读）+ 间隔反馈文案 → 上滑继续；展开后按钮消失防误触。
4. **频道切换**：点频道 chip → feed 整体替换、回到该频道第一张 + 播报频道名。
5. **完成卡**：语音表扬 + "从头再看一遍"大按钮（重置队列）。

## 7. 工程映射（v2 已实施 → `android-app/`）

> ⚠️ **本节描述的是 v2 交付时的状态，不是当前状态。** v3 重构了数据层与调度（§8），
> 2026-09-17 决策③又删除了设置页与生词本（§8.7）。下方带删除线的项**均已不存在**，
> 当前状态以 §8 为准。

**已实现（2026-09-17，Kotlin + Compose，无第三方依赖）：**

- Feed：`VerticalPager`（foundation pager，天然 snap 吸附）；~~页变化 `LaunchedEffect(pagerState.currentPage, pages)` 触发朗读~~ → v3 改为 `snapshotFlow { currentPage to isScrollInProgress }` + 200ms 停稳去抖（§8.3）；`spokenKeys` 去重保留。
- TTS：`tts/TTSSpeaker.kt` 封装 `TextToSpeech`（zh-CN，语速 0.85，异步初始化 pending 补播欢迎语）。
- 数据：~~`data/StudyData.kt` 36 词条（7 频道），`REC_QUEUE` 交错混排~~ → 现为 **368 词条 / 12 场景 + rec**，`REC_QUEUE` 已删除并改为每日队列调度（§8.2）；~~`data/StudyRepository.kt` 生词本 + 设置持久化~~ → 现仅 `TermState` + `DailyQueue` 持久化（生词本与设置项已按 §8.7 删除），`nextInterval`（1→3→7→15 天）保留。
- UI：`ui/` — AppRoot（编排）、TermCard（新学/复习双态）、DoneCard、~~Sheets（字卡/设置/生词本 ModalBottomSheet）~~ → 现仅 `CharSheet`（字卡）、~~Common（顶栏/频道 chips/进度）~~ → 现为频道 chips + 三态 badge + `SwipeHint`、theme（设计 token）。
- ~~字体放大档：`CompositionLocalProvider(LocalDensity provides Density(density, fontScale * 1.15))` 全局乘系统缩放。~~ → **已删除**（决策③取消设置页，字号跟随系统缩放，见 §8.7）。
- 构建：AGP 8.5.2 / Kotlin 2.0.20 / Compose BOM 2024.09.02，minSdk 26 / target 34。本机（Windows）无 JDK/SDK，构建走 Android Studio；见 `android-app/README.md`。

**待下一迭代：** Room + WorkManager 调度入库、AlarmManager 提醒、拍照识字（CameraX + ML Kit）。

## 8. v3：每日队列调度与滑动即播（2026-09-17 收敛，工程迭代）

> ⚠️ **2026-09-17 v4（§9）推翻本节决策①（无配额→每日 10 词）**；§8.1 的间隔阶梯、到期计算与
> 每日升一级闸门在 v4 第三轮的**间隔层**中原样恢复，§8.6 毕业判定（days==15）恢复（达成路径改多轮连击）。
> §8 保留作决策过程记录，当前实现以 §9 为准。

> 决策记录：①复习卡不念词（保住考回忆）②完成卡+自由刷 ③新词先不限配额。
>
> **2026-09-17 复审修订**：④推荐频道不设配额定为正式决策（关闭"368 词首日 368 张"待决项）；⑤分区毕业改为**纯进度标记**，不再排除出推荐队列（§8.6）；⑥删除设置页与生词本相关代码（§8.7）。

### 8.1 数据模型变更

```kotlin
// StudyData.kt：删除 Term.kind 静态字段与 REC_QUEUE；days 静态初始值作废
// 新增（StudyRepository 持久化，SharedPreferences JSON）：
TermState  { days: Int, lastSeen: String }   // lastSeen = yyyy-MM-dd；到期日 = lastSeen + days
DailyQueue { date: String, channel: String, queue: List<String>, modes: List<String>, position: Int }
//   modes 与 queue 平行等长，记录每张卡生成时的形态（NEW/REVIEW）；
//   自由刷页不落库（UI 运行时追加），所以 modes 只覆盖队列本体。
```

- 动态 kind 计算规则：`lastSeen == null` → 新学；`lastSeen != null && 到期日 <= 今天` → 复习；自由刷池抽取 → 温故。
- `nextInterval`（1→3→7→15）保留在 StudyRepository。
- **每日最多升一级**：`markKnown` 若发现 `lastSeen == 今天` 则不再升级，并返回升级后的 `TermState`
  （UI 播报真实天数，不再自行预算）。理由：`replay()`（完成卡→从头再看一遍）会清空 `revealed`，
  同一批复习卡当天可被反复作答；没有闸门就能把 `days` 在同一天刷到封顶，
  使 §8.6 的分区毕业绕过 1/3/7/15 间隔 —— 那不是「连续三次认识」，是「一天点三下」。

### 8.2 队列生成（调度器，在 StudyRepository）

```
当日首次打开（或切换频道后当日首次）：
  due  = 到期日 <= 今天 的已学词（按到期日升序，先清债）
  news = 该范围未学词，随机洗牌
         范围：推荐 = 全部场景（**含已毕业分区**，§8.6 修订） / 场景频道 = 该场景
         ↑ 推荐与自由刷池的范围因此完全一致，scopeIds 与 freePoolScopeIds 合并为一个函数
  交错：约每 2 张新词插 1 张到期复习；无到期复习则纯新词流
  忘了的词（今日队列内反馈"忘了"）：追加到当前队列尾部一次（days=1）
自由刷（完成卡之后继续滑）：
  池 = 该范围已学词（**推荐频道含已毕业分区的词**），洗牌循环抽取，抽完重洗（无限流）
```

### 8.3 朗读契约（AppRoot）

| 卡型 | 滑到停稳后 | 动作后 |
|---|---|---|
| 新词卡 | "词 + 提示语" | 点卡片重听 |
| 复习卡（未答） | "这个词，还记得它念什么吗？"（**不念读音**） | 认识/忘了 → 念"词+提示"+反馈文案 |
| 自由刷卡 | "词 + 提示语"（全展开，无考试按钮） | 点卡片重听 |
| 完成卡 | 战果播报 + "下面随意看看，温故知新" | — |

- 停稳判定：`pagerState.isScrollInProgress` 为 true 时不播；停稳后 delay ~200ms 再播（快速连滑中间卡不闪播）。用 `snapshotFlow { currentPage to isScrollInProgress }` 实现替代现有 LaunchedEffect(currentPage)。
- 欢迎语简化（不再报队列信息）。

### 8.4 持久化时机

- 队列生成时写 DailyQueue（date+channel+queue+modes+position=0）；翻页/反馈时更新 position 与 TermState；隔天（date 不符）作废重建。
- 断点恢复：当天重启 → 恢复 queue+position+TermState，当前卡重播一遍（老人隔一会儿回来重听当前卡更自然）。

### 8.5 涉及文件

- `StudyData.kt`：删 kind/days 静态字段、REC_QUEUE。
- `StudyRepository.kt`：+TermState/DailyQueue 持久化、调度器、到期计算、分区毕业判定。
- `AppRoot.kt`：调度接入、朗读契约 §8.3、去 index/total 传参、毕业徽章状态。
- `TermCard.kt`/`DoneCard.kt`/`Common.kt`：去 ProgressRow；+温故 badge；DoneCard 后接自由刷；ChannelBar +毕业 🎓。
- **删除项（§8.7）**：`StudyRepository` 的生词本与设置字段/方法、`AppRoot` 的 `fontScale` 放大链路。

### 8.7 删除设置页与生词本（2026-09-17 决策）

> 决策：**第一版不做设置页、不做生词本。** 语速固定 0.85x，字号跟随系统缩放。

**理由**：v2 极简化时两个入口的 UI 已被移除，只剩仓库层空转（死代码），
保留会造成"文档写着有、代码里没有"的长期漂移；且生词本的功能已被每日队列覆盖
（「忘了」→ 当天尾部重现 + 次日再考），设置项则因只有一个合理取值而不需要入口。

**删除清单**：

| 位置 | 删除内容 | 理由 |
|---|---|---|
| `StudyRepository` | `forgot` map、`forgotIds`、`forgotCount`、`addForgot` | 零调用；「忘了」已由 `markForgot` + `appendQueue` 承担 |
| `StudyRepository` | `fontScale`、`setFontScale` | 零调用；字号改由系统提供 |
| `StudyRepository` | `speechRate`、`setSpeechRate` | 无入口；语速作为常量（0.85f）交给 `TTSSpeaker` 默认值 |
| `StudyRepository` | `remindTime`、`setRemindTime` | 提醒功能未实现，字段无消费方 |
| `persist()`/`load()` | `forgot` / `fscale` / `rate` / `remind` 四个 JSON 字段 | 随上游删除；旧持久化数据里的残留键读不回即可，无需迁移 |
| `AppRoot` | `fontScale` 的 `remember` 与 `CompositionLocalProvider(Density(...))` 包裹 | `fontScale` 恒为 1f 时该 provider 与 `LocalDensity.current` 等价，删除是行为等价的简化 |
| `AppRoot` | 「忘了」反馈文案里的"已加入生词本" | 生词本已不存在，文案不得承诺不存在的功能 |

**保留**：`markForgot`（把 `days` 打回 1）与 `appendQueue`（当天尾部重现）—— 这是"错词当天再见"的实现，与生词本无关。

### 8.6 分区毕业（2026-09-17 决策；同日复审改为纯进度标记）

> 决策记录：**没有「每日毕业」机制**（词一旦学就永久按 1/3/7/15 循环，不进退出池）；
> 只做**分区级毕业**，且毕业**只作进度展示、不影响队列范围**。

**判定**：某分区**每个词都连续 3 次「认识」** → 该分区毕业。

实现上不新增计数器：连续 3 次「认识」恰好把 `days` 推到阶梯封顶值（1→3→7→15），
任何一次「忘了」都会把 `days` 打回 1。因此：

```
isSceneGraduated(scene) = 该场景所有词 termStates[id]?.days == GRADUATED_DAYS   // = INTERVALS.last() = 15
```

- `GRADUATED_DAYS = INTERVALS.last()`：改间隔阶梯时毕业阈值自动跟随，不会两处不一致。
- 空分区不参与毕业（`rec` 自身、以及定义但无词条的分区）。
- 词尚未学过（无 TermState）→ 该分区必然未毕业。
- 配合 §8.1 的「每日最多升一级」，`days` 到封顶需要**至少 3 个不同日期**的连续认识，
  所以分区的实际最短毕业周期 = 3 天 + 分区内所有词都走完各自阶梯（受队列长度限制，实际远长于此）。

**效果（2026-09-17 修订：改为纯进度标记）**：

| 项 | 行为 |
|---|---|
| 频道栏 | 该分区加 **🎓** 标记、文字转绿，仍可点击进入 |
| 推荐频道队列 | **不受影响** —— 毕业分区的词仍正常排入、仍可复习（不再是"推荐只推没毕业的分区"） |
| 场景频道队列 | 不受影响，仍按同一调度器排到期复习 + 未学新词 |
| 自由刷池 | 不受影响（本来就含全部词） |
| 生效时机 | 徽章即时更新（不再区分当日/次日，因为它已不影响队列生成） |
| 可逆性 | 毕业后若某个词被答成「忘了」，它的 `days` 打回 1 → 该分区**立即退出毕业状态**（徽章消失） |

**为什么不做「词级毕业」**：老人需要长期复现维持记忆，退出池会让已学词彻底消失；
分区毕业既给了「学完一块」的进度感，又不切断记忆维持。

**为什么从"排除出推荐队列"改为"纯标记"**（2026-09-17 复审）：原方案下毕业分区整体移出推荐 `scope`，
叠加"无新词配额"后产生两个副作用 ——

1. **全部 12 分区毕业时，推荐频道当日队列为空**：首屏直接落在完成卡，语音播报"认识了 0 个"，用户第一眼看到的是"没内容"。
2. **毕业词脱离复习循环**：它们只能靠场景频道复习（自由刷不推进 `days`），若用户不进场景频道就再也不复习 —— 与本节开头"老人需要长期复现维持记忆"的立论直接矛盾。

改为纯标记后，队列范围恒为"全部词"，`scopeIds` 与 `freePoolScopeIds` 合并成一个函数，
上述两个副作用与"推荐排除毕业分区"这条特例规则一起消失。**代价**：推荐频道的队列长度不再随进度变轻
（但既然已决定不设配额、由用户自己决定何时停，这个"代价"不成立）。

**风险**：本机（Windows）无 JDK/SDK，无法本地编译验证；需 QYJ 在 Android Studio 构建确认。

## 9. v4：连击 + 间隔双层模型（2026-09-17 第三轮修订定稿）

> **迭代记录**：第二轮曾拍板**纯计数方案**（废除间隔阶梯，days/GRADUATED_DAYS/到期判定全删，
> 「移除」成为唯一进度语义），并已实现一轮。第三轮修订（同日）推翻纯计数，定为**连击 + 间隔双层**：
> 连击只管**当日移出队列**，跨天仍按 1→3→7→15 间隔调度；恢复**推荐每日 10 词**配额（到期复核优先）；
> 分区完成恢复 **days==15** 判定。**第二轮代码需按本节返工**。
> 与 v3 的关系：推翻 v3 决策①（无配额 → 每日 10 词）；§8.1 间隔阶梯与每日升一级闸门**在间隔层原样恢复**；
> §8.6 毕业判定恢复，达成路径改为多轮连击。

### 9.0 双层模型总览

| 层 | 状态 | 生命周期 | 作用 |
|---|---|---|---|
| **连击层**（当日） | 每词当日认识计数 0–3（跨频道共享） | 当日有效，隔天随 DayState 整体作废 | 满 3 → 移出当日队列；任意「忘了」→ 清零 |
| **间隔层**（全局） | `TermState {days, lastSeen}` | 永久持久化 | 满 3 移除时升一级（1→3→7→15，每日最多升一级）；「忘了」→ days=1 |

- **未满 3 的作答不写 TermState** —— 词保持原有到期状态，次日自然回池继续（遗留词机制，无单独列表）。
- 自由刷/场景自主复习作答走**同一状态机**（连击照算、忘了降级），受每日升一级闸门约束。

### 9.1 数据模型变更

```kotlin
// TermState：恢复间隔语义（第二轮的 knowCount 移入连击层）
TermState { days: Int, lastSeen: String }
//   lastSeen = yyyy-MM-dd；"" = 教读路径不产生状态，本字段恒非空；"" 仅作加载兜底（视为立即到期）
// 持久化 JSON：{"terms": {id: {"days": N, "lastSeen": "yyyy-MM-dd"}}}
// 迁移：第二轮的 "count" 字段弃读；days 缺省 1（视为已学、近期到期），原型阶段可接受

// 连击层（当日，随日期整体作废）：
DayState { date: String, counts: Map<String, Int>, seen: Set<String> }
//   counts：每词当日认识计数（0–3，跨频道共享，自由刷作答同样写入）
//   seen：当日已教读词集合（markSeen 幂等去重——防重启/回滑后对同一 NEW 卡重复追加考核卡）

// DailyQueue 增加 answered 平行数组：
DailyQueue { date, channel, queue, modes, answered: List<Boolean>, position }
//   answered[i] = 该卡实例是否已作答（断点恢复按**卡实例**恢复展开态，不再按词恢复）
```

- **为什么恢复按卡实例记 answered**：v4 同一词会出现多张卡（学 1 次 + 考核若干次），v3 的
  "lastSeen==今天 → 已作答" 是**词级**判定——第二轮沿用它有两个缺陷：
  ①教读后未答完的词无 TermState，重启后已答的卡重回考试状态；
  ②同词的多张考核卡会被一并展开，剩余连击作答不了（重启后连击卡死）。
  answered 与 queue/modes 平行追加（appendQueue 时 +false），恢复时逐卡还原。

- **恢复**：`INTERVALS = [1, 3, 7, 15]`、`GRADUATED_DAYS = INTERVALS.last()`（=15）、
  `nextInterval(days)`（1→3→7→15，15 封顶）、`isDue`（`lastSeen + days ≤ 今天`，空 lastSeen 视为到期）、
  `DAY_MS`。
- `markSeen(id): Boolean`：当日首次教读返回 true 并写入 seen（不写 TermState）；已 seen 返回 false。

### 9.2 状态机（每词，任何频道/自由刷口径一致）

| 事件 | 连击层 | 间隔层（TermState） |
|---|---|---|
| 新词卡停稳教读（markSeen 返回 true） | — | **不写**；AppRoot 据返回值追加该词复习卡到队尾（开始当日连击考核） |
| 「认识」count<2 | +1 → 追加队尾（REVIEW） | **不写** |
| 「认识」count==2（→3） | **移出当日队列**（不追加） | 无 TermState → 写 `{1, 今天}`（首次完成）；有状态且 `lastSeen ≠ 今天` → `days=nextInterval(days)`、`lastSeen=今天`；`lastSeen == 今天`（当日已升级或已忘了）→ **不升级**（每日最多升一级闸门） |
| 「忘了」（任何地方） | 清零 → 追加队尾（当天再见） | 写 `{1, 今天}`（只重置间隔，不回退为未学词） |

- 间隔阶梯：无状态 → 1 → 3 → 7 → 15（封顶）；days==15 后每 15 天到期回池复核（长期维持）。
- 同日「忘了」后再连击满 3：闸门挡住升级（lastSeen==今天），days 保持 1 → 次日到期回池重新连击，
  与 prd「次日到期回到任务池，重新连击三次才能移除」一致。
- **反馈文案**：沿用按剩余次数口径（count=1 →「记得牢！再认对 2 次就学会」；2 →「再认对 1 次」；
  3 →「这个词学会啦！」，且**发生了升级**时追加播报「N 天后再来复习」；闸门挡住则不播天数）；
  忘了 →「没关系，再学一遍」。markKnown 返回 `(count, upgraded, daysAfter)` 供 UI 播报。

### 9.3 任务池与调度器（buildQueue 重写）

```
推荐频道：due  = 范围内到期词（isDue），按到期日升序（先清债）
          news = 未学词洗牌
          pool = (due + news).take(DAILY_POOL_QUOTA)        // 复核优先 + 新词补足，共 10 个
场景频道：pool = due + news（不另设配额）
queue = pool 原序（到期在前、每词一次），NEW/REVIEW 按有无 TermState 标注，answered 全 false
pool 为空 → 当日队列只有完成卡，直接自由刷（prd：一个也没有当天直接自由刷）
```

- 「当日冻结，重启不换词」仍由 ensureQueue 持久化保证。
- 未满 3 的到期词不写 TermState → 保持到期 → 次日自动回池（遗留词机制）。
- 教读后未答完连击的新词：无 TermState → 次日仍为未学新词（重新教读，连击当日清零重来）。
- appendQueue：作答/教读后词**未移除**（当日 count<3）→ 追加队尾（REVIEW、answered=false）；
  已移除不追加。

### 9.4 自由刷与场景自主复习

- 与第二轮方案相同：FREE 卡复习式交互、可作答、连击/降级照算（§9.2 状态机）；自由刷卡**不追加**
  任务队列（插入点在完成卡之前会引发 pager 错位；当天再见由自由刷池循环覆盖，次日回池由
  days=1 + buildQueue 保证）。
- 自由刷池 = 该范围全部已学词（有 TermState，含 days==15 的）。「教读未完成连击」的词无 TermState，
  不入自由刷池。

### 9.5 分区完成（恢复 days 封顶判定）

```
isSceneGraduated(scene) = 该场景所有词 termStates[id]?.days == GRADUATED_DAYS   // = 15
```

- 达成 = 多轮**不同日**的满 3 连击（1→3→7→15）；任何一处「忘了」days 打回 1 → 即时退出完成状态（可逆）。
- 毕业分区词仍正常排入推荐队列（v3「纯标记」决策保留）；徽章即时更新；空分区与 `rec` 不参与。

### 9.6 涉及文件

- `StudyRepository.kt`：TermState 恢复 `{days, lastSeen}`；新增 DayState（counts/seen）持久化与隔天作废；
  DailyQueue +answered；恢复 INTERVALS/GRADUATED_DAYS/nextInterval/isDue/DAY_MS；
  markKnown/markForgot 改双层逻辑；markSeen 改幂等 Boolean；buildQueue 改「due 优先 + 10 词配额」；
  isSceneGraduated 改 days==15；新增 markAnswered(channel, index)。
- `AppRoot.kt`：TermPage 携带队列索引；answer() 走双层状态机 + answered 落库 + 文案（剩余次数、
  升级播报天数）；NEW 停稳改 markSeen 返回值控制追加；revealed 断点恢复改按 answered[i]；
  毕业徽章注释同步。
- `TermCard.kt` / `DoneCard.kt` / `Common.kt`：预期无行为变化，仅注释语义核对。

## 10. 取舍与风险

- **滑动 vs 点击翻页**：选择滑动（已被短视频市场教育的习惯，单指大面积操作）；snap + 大卡片降低误操作代价。风险：部分高龄用户首次需引导——完成卡/空态有语音引导文案。
- **词组学习 vs 单字学习**：词组贴近真实识别场景（看牌子认整体），但泛化能力弱于单字。字卡弹层（点单字看组词例句）作为补充路径保留。
- **语音依赖**：浏览器无 zh voice 时降级为视觉反馈；Android TTS 内置中文，工程阶段风险消除。

## 11. v6 设计追加（2026-09-20：隐藏设置入口 + 收藏分区）

> 需求与验收见 prd.md「v6 交互修订」。改动仅限 `android-app/`。

### 11.1 数据层

- **新文件 `data/AppSettings.kt`**：独立于 StudyRepository 的设置仓库。
  - 自有 SharedPreferences（`app_settings`）+ 单个 JSON key：`{"order": [sceneId...], "hidden": [sceneId...], "quota": 10}`。
  - 默认值：`order` = `SCENES` 固有顺序（排除 rec）、`hidden` = 空、`quota` = 10。
  - 对外暴露：`visibleScenes(): List<Scene>`（按 order 过滤 hidden，返回 Scene 列表）、`setSceneVisible(id, visible)`、`moveScene(id, delta)`（-1 上移 / +1 下移，边界裁剪）、`quota(): Int`、`setQuota(n)`。
  - rec 与 fav 不进 order/hidden 集合；order 中出现未知 id 忽略（兼容口径）。
- **`StudyRepository.kt` 增量**：
  - `CHANNEL_FAV = "fav"` 常量（与 `CHANNEL_DAILY` 并列）。
  - 收藏持久化：主 `state` JSON 顶层新增 `"favorites": [termId...]`（**有序数组**，LinkedHashSet 保存）；旧 JSON 无此键 → 缺省空集，无需迁移。
  - API：`favorites(): List<String>`、`isFavorite(id): Boolean`、`toggleFavorite(id)`（返回收藏后的新状态；写后 persist）。
  - `scopeIds("fav")` = 收藏词 id 列表（有序，不洗牌——洗牌交给池型抽取）；`poolIds("fav")` = `weightedShuffle(收藏 ids)`（与场景分区同一加权逻辑）。
  - `graduatedScenes()` / `isSceneGraduated` 天然不含 fav（fav 不在 `SCENES`），保持不动。
  - 配额注入：`StudyRepository` 构造函数新增 `quotaProvider: () -> Int`（MainActivity 组装：`{ appSettings.quota() }`）；`buildQueue` 的 `DAILY_POOL_QUOTA` 改读 `quotaProvider()`。常量 10 保留为缺省值。**当日队列冻结语义不变**：配额只在重建队列时被读。
- **`StudyData.kt`**：`sceneName("fav")` 需返回「收藏」——fav 不进 `SCENES`（否则会被当成可调度场景），在 `sceneName` 加显式映射（`CHANNEL_FAV -> "收藏"` 或字面量）。

### 11.2 UI 层

- **`Common.kt` `ChannelBar`**：签名改为接收 `scenes: List<Scene>`（设置过滤排序后的可见场景分区）、`onOpenSettings: () -> Unit`。
  - 渲染顺序：推荐（`CHANNEL_DAILY`）→ 收藏（`CHANNEL_FAV`，图标 ⭐ 名「收藏」）→ 可见场景分区。前两个固定渲染。
  - **连点检测**：推荐 tab 内部 `remember` 计数 + 上次点击时间；点击间隔 > 2s 重置；计满 5 次回调 `onOpenSettings` 并清零。每次点击照常 `onSelect(rec)`。
  - 收藏 tab 无 🎓 逻辑（不参与毕业）。
- **`TermCard.kt`**：新增参数 `isFavorite: Boolean`、`onToggleFavorite: () -> Unit`。
  - 卡片主体外层包 `Box`：右缘垂直居中放星按钮（抖音式右侧动作栏）；`Modifier.clickable` 自消费 + 不冒泡到卡片 `onSpeakTerm`。
  - 视觉：藏 = ★ 金色（#F9A825 系），未藏 = ☆ 灰；字号 ≥ 40sp、触控 ≥ 64dp。
  - 所有卡型（NEW/REVIEW/FREE）都渲染星按钮；复习卡考试态也不隐藏（收藏与考试互不干扰）。
- **新文件 `ui/SettingsScreen.kt`**：全屏 overlay（`AppRoot` 内 `var showSettings` 控制条件渲染，覆盖整个 Column）。
  - 白底大字列表：①「每日学习词数量」单选行组（3/5/10/15/20，大按钮，选中高亮，附「明天生效」小字）；②每个场景分区一行：名称 + 显示开关（大按钮「显示/隐藏」）+ 「↑」「↓」上下移按钮；③底部「完成」大按钮关闭。
  - 触控 ≥ 64dp、字号 ≥ 22sp，配色沿用主题常量。
- **`AppRoot.kt`**：
  - `channel` 装载分支不变——fav ≠ `CHANNEL_DAILY` 自然落入**池型分支**（`appendPoolPages`），无需新增类型。
  - `poolIds("fav")` 空池（无收藏）→ `appendPoolPages` 拿不到卡：此时 `pages` 为空会白屏，需插入**引导页**：`Page.Guide`（新 sealed 分支，大字引导文案，停稳播报同文案）。仅 fav 空池可达；场景分区池必非空不受影响。
  - 星按钮回调 → `repo.toggleFavorite(p.t.id)` → 本地状态刷新（用 `mutableStateMapOf`/state 持有收藏集合快照供 TermCard 重组；频道内取消收藏**不重排**已出卡，只影响后续 `poolIds` 重洗）。
  - 隐藏入口：`showSettings` state + `ChannelBar(onOpenSettings = { showSettings = true })`；打开时 `tts.speak("已打开设置")`。
  - 设置即时生效：`AppSettings` 变化后重建传给 ChannelBar 的 `scenes` 列表；若 `channel` 被隐藏（不在可见分区且 ≠ rec/fav）→ `channel = CHANNEL_DAILY`。
  - SettingsScreen 保存配额只写 `appSettings.setQuota`，**不**触碰当日队列。

### 11.3 抽卡算法统一（2026-09-20 追加拍板）

- `poolIds` 三分支（rec 温故流 / 分区 / 收藏）**抽卡算法统一为 `weightedShuffle`**；温故流不再 `shuffled()` 等概率洗牌。
- **池的构成差异保留**：rec = 仅已学词（`filter { termStates[it] != null }`，D4 新词入口唯一化）；分区/收藏 = 全部词。统一的是算法，不是池的范围。
- 每日任务队列调度（due 优先 + interleave）不变——队列型不参与抽卡统一。
- `poolWeight` / `weightedShuffle` / `SCENE_NEW_WEIGHT` 均无签名变化，仅消费面扩大到 rec 分支。

### 11.4 兼容与风险

- 旧数据：state JSON 无 `favorites`/settings 键 → 缺省空集/默认值，无需迁移。
- 收藏分区零写入契约不被破坏：收藏状态存 favorites（独立于 TermState/DayState），浏览卡仍无作答入口。
- 连点 5 次切频道副作用：第 1 次点击切到推荐，后 4 次重复选中推荐 = 无操作；可接受。
- 本机无 JDK/SDK：静态自检（导入、读写对称、括号配平）+ QYJ 实机验证同 v5 流程。

## 12. v7 设计（2026-09-20：直接考试 + 总结确认 + 布局修正 + 单字直读）

> 需求与验收见 prd.md「v7 交互修订」。改动仅限 `android-app/`（包名 `com.knowmo.app`，QYJ 已重构，勿动）。

### 12.1 数据层（StudyRepository.kt）

- **删除**：`markSeen` / `wasSeenToday` / `appendQueue`；`DayState.seen` 字段（persist 不再写 "seen" 键；load 遇旧 JSON 的 "seen" 键直接忽略——读写不对称仅此一处，向前兼容）。
- **DailyQueue 增 `confirmed: Boolean`**：JSON `"confirmed"` 键，缺省 false（旧数据兼容）；新队列构建恒 false。
- **新增 `markConfirmed()`**：`queues[CHANNEL_DAILY] = q.copy(confirmed = true)` + persist（幂等，已 true 直接返回）。
- `buildQueue` / `ensureQueue` / `markAnswered` / `saveQueuePosition` / 间隔层状态机 / f30 全部不动。

### 12.2 UI 层

- **AppRoot.kt**：
  - 新会话态 `browseMode: Boolean`（rec 频道专用：false = 考试阶段锁滑；true = 总结确认后的自由浏览）。
  - **装载分支**（LaunchedEffect(channel)，rec）：`q.confirmed == true` → `browseMode = true`、`pages = emptyList()` + `appendPoolPages(POOL_BATCH)`、`restoreTo = 0`；否则考试模式（现有断点/frontier 装载，去掉 taught/markSeen 逻辑）。
  - **删除**：`taught` map、`appendReviewCard`、停稳播报里的 markSeen 分支、`charFor` 状态与 CharSheet 调用、`blockingIndex` 及拦截分支（v5 R7 §5.2 整段——锁滑结构性取代，死代码不留）。
  - `isPending` 收窄：`TermPage && mode != FREE && revealed[seq] != true`（不再有教读待处理）。
  - **锁滑**：`VerticalPager(userScrollEnabled = channel != CHANNEL_DAILY || browseMode)`——考试阶段 false，浏览/分区/收藏恒 true。程序 `animateScrollToPage` 不受影响。
  - **onConfirmDone**（DoneCard 确认回调）：`repo.markConfirmed()` → `browseMode = true` → `pages = emptyList()` + `appendPoolPages(POOL_BATCH)` → `restoreTo = 0`、`spokenKeys.clear()`、`pendingAnnounce = "任务完成，随便看看吧。"`。⚠️ browseMode 后 `syncDonePage` 必须短路（浏览页永不挂完成卡）。
  - **cardSpeech 分流**：考试态未作答——NEW 首见词播「学新词啦！认识它吗？想一想，再按下面的按钮」（不再说「还记得」）；REVIEW 维持原防泄题话术。
  - **单字点击**：`onSpeakWord = { tts.speak(p.t.text) }`（只读词，不读提示——与点卡片重听「词+提示」区分）。
  - 顶部大 KDoc 契约注释同步 v7（删拦截/教读条目，新增锁滑/确认条目）。
- **TermCard.kt**：
  - `isExam = mode != CardMode.FREE`（首见词也是考试卡；badge 仍区分 新学/复习）。
  - `onCharClick: (TermChar) -> Unit` 改名 **`onSpeakWord: () -> Unit`**，单字点击调用。
  - **布局修正（多字词）**：词组 Row → `FlowRow`（ExperimentalLayoutApi）允许换行；字号档 ≤3:64sp / 4:54sp / 5:44sp / ≥6:36sp；拼音行同 FlowRow；**词区与拼音区 `padding(end ≈ 56.dp)` 为右缘星按钮预留空间**（防遮挡）；星按钮位置/触控不变。
- **DoneCard.kt**：新参 `onConfirm: () -> Unit`，底部加大号「确认」按钮（高 ≥ 88dp、居中、大字），点击回调。
- **删除 `ui/Sheets.kt`**（仅含 CharSheet，无其他消费方）。

### 12.3 兼容与风险

- 旧 JSON：`seen` 键忽略、`confirmed` 缺省 false——无需迁移。
- 锁滑后 TTS 不可用场景：作答后仍按 AUTO_ADVANCE_MIN_MS 兜底前进（既有机制）。
- 考试中途切频道再回：confirmed=false 走断点恢复（未答卡保持考试态）；confirmed=true 直接浏览模式。
- 首见即考对高龄用户偏苛刻是 QYJ 明确决策（按自己情况选认识/忘了），不做折中。

## 13. v8 设计（2026-09-20：连击回归 + 最小间隔插入 + 全显拼音 + 封顶分级）

> 需求与验收见 prd.md「v8 交互修订」。改动仅限 `android-app/`（包名 `com.knowmo.app` 勿动；`prototype/` 已冻结、README.md 禁改）。
> 核心：**恢复 v4 连击语义**（当日 3 次认识才移出），但插入方式从队尾追加改为**中段最小间隔插入**；**废除防泄题契约**（v3 以来）——所有任务卡全显示拼音+提示；**封顶分级** 30/60。

### 13.1 数据层（StudyRepository.kt）

- **DayState.counts 恢复连击计数语义（0–3）**：markKnown `counts[id] = min(3, (counts[id] ?: 0) + 1)`；markForgot `counts[id] = 0`。JSON 结构不变——旧数据里 v7 写的 1/0（已作答标记）按计数自然沿用、隔天随 date 作废，无需迁移。新增 `comboCount(id): Int`。
- **markKnown 返回类型 Pair → Triple(count, upgraded, daysAfter)**（AppRoot 需要 count 决定是否插卡）；markForgot 返回 count（作答后恒 0）。
- **新增 `repeatCard(id: String, afterIndex: Int): Int`**：
  - 前置条件：调用方已确认 `comboCount(id) < 3`（repo 不重复判，保持 API 薄）。
  - `insertAt = min(afterIndex + 1 + MIN_GAP, queue.size)`；queue/modes/answered 三平行数组同步插入（mode=`MODE_REVIEW`、answered=false）+ persist；返回 insertAt。
  - afterIndex 之后不足 MIN_GAP 张时钳到队尾（尽力而为，不拒绝插入——同词当日重现比严格间距更重要）。
  - **同词间隔不变量由构造保证**：所有插入点 = 源卡下标 + MIN_GAP + 1，按归纳法任意两张同词卡之间 ≥ MIN_GAP 张其他卡。`MIN_GAP = 2`（companion 常量，QYJ 口径「避免连续/紧邻」的最小满足，可调）。
  - 插入点恒在当前卡之后 → position/frontier/已有卡下标不受影响；pages 对齐见 13.2。
- **f30 去重**：markKnown/markForgot 内 `recordF30` 调用加守卫——`id in currentDay().counts`（今日已作答过）→ 跳过。恢复连击后同词一日可答 3 次，去重后 f30 口径与 v7「无重复观测噪声」等价（只记每词每日首次作答）。
- **nextInterval 签名改 `(prevDays: Int, ease: Double, lapses: Int)`**：cap = `if (ease >= 2.5 && lapses == 0) INTERVAL_CAP_MATURE else INTERVAL_CAP_NORMAL`；常量 `INTERVAL_CAP_NORMAL = 30`、`INTERVAL_CAP_MATURE = 60`（45–60 区间 QYJ 授权内取 60：稳态减负最大化；f30 实测不达标可下调为改常量）。markKnown 调用处传 `cur.lapses`（无状态首写不经此函数，不受影响）。
- buildQueue（当日首排）/ ensureQueue / markAnswered / saveQueuePosition / markConfirmed / confirmed / 锁滑数据支撑 / graduatedScenes 全部不动。
- KDoc 全面校准：v7 的「每词恰好一张考试卡」「counts=已作答标记」「一日内至多 1 次作答（f30）」「作答不追加任何卡」等口径按 v8 改写，防文档漂移。

### 13.2 UI 层

- **AppRoot.kt**：
  - `answer()` 连击分流：作答后取返回 count——`count < 3 && p.qIndex >= 0` → `val insertAt = repo.repeatCard(t.id, p.qIndex)` → `pages = pages.toMutableList().apply { add(insertAt, Page.TermPage(t, CardMode.REVIEW, nextSeq(), insertAt)) }`。**pages 下标 == queue 下标恒成立**（考试阶段任务卡区与 queue 一一对应、每次同步插入），所以 insertAt 直接用作 pages 插入下标；插入点在当前卡之后，自动前进落点 `cur + 1`、frontier、`saveQueuePosition` 全不受影响。`count == 3` → 不插卡（该词移出）。
  - 播报文案按连击分级（恢复 v4 剩余次数口径）：count=1 →「记得牢！再认对 2 次就学会」、2 →「再认对 1 次就学会」、3 →「这个词学会啦！」+（upgraded 时）「N 天后再来复习」；忘了 →「没关系，再学一遍」（连击清零，后续重复卡自然重现）。
  - **cardSpeech 简化**：所有 TermPage 一律 `termSpeech`（词+提示）——FREE、未作答、已作答同文案；删 `EXAM_TAP_HINT_SPEECH`、`NEW_EXAM_SPEECH`、onSpeakTerm/onSpeakWord 的 `examUnanswered` 防泄题分流（点卡 = 词+提示、点单字 = 读词，无条件）。
  - **删 peeked / onPeek**（「想看答案」随隐藏态一起失去意义）；revealed 语义收窄为「已作答」（控制结果文案显示与按钮隐藏），不再控制拼音显隐。
  - 顶部 KDoc 契约注释同步 v8（删防泄题条目，加连击/打散插入条目）。
- **TermCard.kt**：删考试隐藏逻辑与 peek 按钮——**所有卡恒全展开**（词+拼音+提示）；√/× 作答按钮仅 `mode != FREE` 渲染（浏览卡不变）；badge 新学/复习保留。
- 锁滑（考试阶段 userScrollEnabled=false）、总结确认（markConfirmed → browseMode → 温故流）、温故流/分区/收藏装载与零写入：全部不动。

### 13.3 兼容与风险

- 旧 JSON 无需迁移：counts 沿用（当日数据隔天作废）、repeatCard 只在运行时改队列并整条持久化、封顶改动对存量 days 无追溯（下次升级时才按新 cap 截断）。
- 队列增长有界：每词 ≤ 3 张（初始 1 + 重复 ≤ 2），当日队列 ≤ 池词数 × 3；三平行数组同步插入，读写对称由 repeatCard 单点维护。
- 断点恢复：队列持久化含重复卡，answered 逐卡还原——重启后连击进度（counts）与卡实例作答态都正确。
- f30 去重后，同词当日连击不重复计数；days==60 的成熟词不再触发 f30（观测点仍为 30）——预期内的口径变化，调参时注意。
- 本机无 JDK/SDK：静态自检（Grep 残留、读写对称、括号配平）+ QYJ GitHub Actions 构建验证（v4 步骤 7 同流程）。

## 14. v9 设计（2026-09-20：任务卡静默自评 + 完成卡上滑转浏览 + 推荐范围收窄 + 常用词默认分区）

> 需求与验收见 prd.md「v9 交互修订」。改动仅限 `android-app/`（包名 `com.knowmo.app` 勿动；`prototype/` 已冻结、README.md 禁改）。

### 14.1 数据层

- **StudyRepository 构造函数第 4 参** `recScenesProvider: () -> Set<String>`（缺省 = 全部 SCENES id）；MainActivity 注入 `{ settings.visibleScenes().map { it.id }.toSet() }`。与配额同语义：只在 buildQueue / poolIds 读取，当日队列冻结不变。
- **scopeIds(CHANNEL_DAILY)** = `STUDY_TERMS.filter { it.scene == CHANNEL_COMMON || it.scene in recScenesProvider() }`——隐藏分区排除（prd 第 3 条）+ 常用词特例恒入（prd 第 4 条 QYJ 拍板：不进显隐管理、只作推荐内容源，否则默认状态下推荐频道为空）。每日任务与温故流共用同一 scope，两个入口都收窄。
- **新常量 `CHANNEL_COMMON = "daily"`**；confirmed 语义微调（到达完成卡即置 true，不再等点击）——复用 `markConfirmed()`，无 schema 变化。
- **WordBank**：`DAILY_COMMON` 30 条（common-1..30，scene=daily；单字高频 10 + 双字常用 20），STUDY_TERMS 并入，总量 431（id 无重复）。
- **StudyData**：SCENES 在 rec 之后插入 `Scene("daily", "常用词", "🔤", 0xFFE0F7FA)`——在 SCENES 里（参与分区毕业），但**不进 AppSettings 显隐管理**（order/hidden 均不含 daily；不可开关、频道栏不出现），其词恒入推荐范围（2026-09-20 口径细化，QYJ 拍板——原「设置页可显隐」表述作废）。
- **AppSettings**：`hidden` 缺省 = 全部 manageableIds（v9 默认频道只剩推荐+收藏）。有存储 JSON 的用户以存储为准（load 覆盖）。⚠️ 该行原写「缺省只对新装机生效」，**v12 已作废**（见 §15：升级路径同样按隐藏处理）。

### 14.2 UI 层

- **AppRoot.kt**：
  - `cardSpeech`：任务卡（非 FREE）返回**空串** → 停稳静默（prd 第 1 条「显示词卡不朗读」）；点卡片/单字听读回调不变（v8 无条件念）。停稳播报块改为「`full` 非空白才 speak + 记 spokenKeys」——**频道切换播报（announce）不受任务卡静默影响**。完成卡话术改「上滑进入推荐模式，随便看看吧」。
  - `onConfirmDone` **删除** → `enterBrowse(doneIdx)`（suspend，currentPage collector 内调用）：`markConfirmed()` + `browseMode = true` + `pages.filter { Done || qIndex < 0 }`（任务卡移除、**完成卡保留在首位**）+ `scrollToPage(currentPage - doneIdx)`（下标左移校正）+ 丢弃未完成的自动前进。
  - currentPage collector：`doneIdx >= 0 && idx >= doneIdx` → `enterBrowse(doneIdx)`（在 appendPoolPages 之后调用，浏览页已就位）。
  - confirmed 装载分支：`pages = listOf(Page.Done)` + `appendPoolPages` + `restoreTo = 0`（当日重启落在完成卡，回滑看战果、上滑进浏览）。
  - `userScrollEnabled = channel != rec || browseMode || (doneIdx >= 0 && currentPage >= doneIdx)`——正式解锁走 browseMode，末项兜底覆盖 collector 触发前的极短窗口。
  - `insertRepeatCard` **修 v8 缺陷**：插入后对 `qIndex >= insertAt` 的任务卡 qIndex **同步 +1**（否则 markAnswered 按旧下标标记错卡）；插入点 == pages.size 时尾插兜底。
  - DoneCard 调用去 `onConfirm`、增 `inBrowse = browseMode`。
- **DoneCard.kt**：去确认按钮，改「上滑进入推荐模式」+ SwipeHint；`inBrowse = true` 时文案「随便看看吧」。
- **TermCard.kt / Common.kt / SettingsScreen.kt**：无改动（v8 已全显、无 peek；新分区自动出现在设置页 —— ⚠️ **但不会自动出现在频道栏**，见 §15：「新分区自动出现在设置页与频道栏」的旧口径 **v12 已作废**）。

### 14.3 兼容与风险

- confirmed 旧数据缺省 false 兼容；无持久化 schema 变化。
- 转浏览瞬间的页跳变：pages 过滤 + scrollToPage 同一 collect 内完成；settle effect 的 200ms 去抖与「迟到事件」守卫（currentPage != idx → return）吸收中间 coerced 事件。
- enterBrowse 时浏览池必非空：作答即写间隔层 → 转浏览前已答词必有 TermState → 池 ⊇ 已答词（且常用词 30 条恒在 scope）。
- 任务卡静默后，频道切换播报仍可达（announce 拼接在空白文案前）。
- 本机无 JDK/SDK：静态自检（括号配平 / 残留符号扫描 / 词库拼音与场景校验，`.workbuddy/v9_check.py`）通过；实机构建验证走 GitHub Actions / Android Studio。

## 15. v12 设置口径（2026-09-20：新增分区对新装与升级一律默认隐藏）

> 需求与验收见 prd.md「v12 设置口径」。**唯一改动文件**：`android-app/.../data/AppSettings.kt`。
> v12 的「家电」分区词库内容轮（63 条）见 implement.md「v12 步骤清单」。

### 15.1 数据层（AppSettings.kt）

- **契约**：`hidden` 的缺省语义从「只在无存储 JSON（新装机）时生效」改为「**对存储 JSON 里不存在的分区同样生效**」。两条路径等价，故「默认隐藏」不再有「仅新装机」的限定。
- **判定依据 = 存储的 `order` 数组**。`persist()` 写入的是**完整** order（`manageableIds()` 全集），所以：
  - `id ∈ 存储 order` → 写入该 JSON 时分区已存在 → 按存储的 `hidden` 数组决定显隐（**用户显式选择优先**）；
  - `id ∉ 存储 order` → 写入该 JSON 时分区尚不存在 → 显隐选择无从继承 → **取隐藏**。
- **仅在 `order` 数组确实存在时判定**：`order` 键缺失说明这不是本版本写的 JSON（本版本 `persist()` 必写 order），此时不做推断、保持原行为，避免把用户的显隐选择覆盖掉。
- 无持久化 schema 变化：`order` 仍只存 id 列表，`hidden` 仍只存隐藏 id；**不需要迁移**。

### 15.2 兼容与风险

- **顺带修掉一个同源旧缺陷**：v9 引入 `daily` 时，v6–v8 时代写入过设置的机器升级后会看到 `daily` 自己冒进频道栏（同一机制）。本口径把 `daily` 一并收进「默认隐藏」，与 v9 原意一致。~~副作用（可接受）：若某台机器在 v9–v11 期间**显式开启过** `daily`，其 `order` 里含 `daily` → 仍按用户选择保持可见，不被新口径打回隐藏。**用户显式选择永远优先**，这是本口径的唯一让步。~~ → **已被 2026-09-20 口径细化作废**：daily 不再进显隐管理（`manageableIds()` 排除），旧存储 order/hidden 里的 `daily` 在 load 过滤时自然丢弃——曾显式开启过常用词的机器升级后频道栏不再出现该频道，其词仍在推荐范围内（CHANNEL_COMMON 特例），无需迁移。
- 新增分区因此永远「安静」：升级不会改变频道栏构成 —— 频道栏的每一次变化都来自用户操作。
- 本机无 JDK/SDK：以 Python 移植 `load()` 语义做场景模拟（`research/vocab/check_appsettings_load.py`）作为唯一自动化防线；实机验证走 GitHub Actions / Android Studio。

## 16. v14 设计（2026-09-20：设置页学习统计）

**受众拍板**：家属/年轻人（QYJ：设置都是给年轻人看的）。指标全套：总览 + 今日战果 + 分区进度 + 连续天数 + f30 观测。

### 16.1 数据层（StudyRepository）

- **streak 持久化**（唯一数据模型改动）：独立 SharedPreferences key（与 f30 观测同模式，不进主 state JSON——清观测/独立演化的口径一致）：
  - `streak_count: Int`、`last_study_date: String(yyyy-MM-dd)`；
  - `touchStreak()`：`today() == last_study_date` → 不动；`last_study_date == 昨天` → +1；否则 → 1。写后 apply。
  - 调用点：`markKnown` / `markForgot` 开头（currentDay() 之后、persist() 附近）——**旁路写入**，不触碰 termStates/day/queues，v8 调度语义零改动。「昨天」判定用 `fmt.parse(today()) - DAY_MS` 格式化比对，与 isDue 同款 runCatching 防御。
  - 只读 API `studyStreak(): Int`；旧数据缺省 0，不回填。
- **只读统计 API**（全部零写入）：
  - `learnedCount() = termStates.size`（全库口径，含隐藏分区词——家属视角的总账）；
  - `graduatedCount()` = `termStates.values.count { it.days >= GRADUATED_DAYS }`；
  - `sceneProgress(sceneId): Pair<Int, Int>` =（该区已学数, 该区总词条数），消费 `SCENES` + `STUDY_TERMS`，含 `daily`；
  - 复用现有：`todayKnown()` / `todayForgot()` / `favorites().size` / `f30Total()` / `f30Fail()`。

### 16.2 UI 层（SettingsScreen / AppRoot）

- 设置页**标题下新增第一节「📊 学习统计」**（位于①每日词量之前）——设置页本就 verticalScroll，13+1 行分区进度可接受；不做二级页（README 硬指标「无多级菜单」）。
- 版式（沿用设置页既有 token，正文 ≥22sp、说明小字 18sp）：
  - 汇总两行：`已学 X / N 词（毕业 Y）· 收藏 Z`；`今日认识 A · 忘了 B · 连续学习 D 天`；
  - 分区进度列表：每行 `${icon} ${name}` + 右侧 `x/y` + 细进度条（高 8dp、BlueBg/BlueDark）；
  - f30 观测小字：`30天词观测：作答 X · 忘了 Y（Z%）`——Z=0 时省略括号。
- 装配：`SettingsScreen` 增加统计参数（与 quota/hiddenIds 同模式），MainActivity/AppRoot 从 repo 一次性取快照传入。设置页打开期间无作答发生，静态快照够用，不需要 Flow/状态订阅。
- streak 更新在 repo 作答路径内完成，UI 层零改动。

### 16.3 兼容与风险

- 旧安装升级：streak key 缺省 → 显示 0，无崩溃、无迁移代码。
- 风险：v14 后任何统计断言先跑 `research/vocab/check_wordbank_invariants.py` 取词库真值（工作备忘条款）；分区进度总数用 `STUDY_TERMS.filter` 动态算，不硬编码 509。
- 回滚点：revert SettingsScreen 统计节 + repo 统计 API 即可，streak key 残留无害。
