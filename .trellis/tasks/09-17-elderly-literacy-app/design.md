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
