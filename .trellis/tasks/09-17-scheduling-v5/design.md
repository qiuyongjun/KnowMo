# design.md — 学习复习调度优化 v5

> 延续 09-17-elderly-literacy-app/design.md §9（v4 连击 + 间隔双层模型）的迭代记录文体。
> v5 只动调度参数与排序、毕业判定、复习卡交互契约；双层状态机结构（连击层/间隔层/每日队列/自由刷）不变。

## 0. 问题定位（为什么改）

| # | 问题 | 根因 | 影响 |
|---|---|---|---|
| P0-1 | 到期债务必然堆积 | 370 词 × 15 天封顶 → 稳态每日到期 ≈25 > 配额 10 | 长期用户逾期队列只增不减，「忘了」率上升 → days 打回 1 → 债更多，恶性循环 |
| P0-2 | 「忘了」词排序反向 | markForgot 写 {days=1, lastSeen=今天} → 次日 dueTime 全池最新 → dueTime 升序排池尾 | 超额日 take(quota) 首先挤掉昨天刚忘的词——最需要复现的词反而见不到 |
| P1-1 | 复习卡点按泄题 | TermCard 主区域 clickable → onSpeakTerm 对所有形态生效 | 考试态未作答时一个 tap 听到「词+提示」，考回忆契约失效，连击虚高 |
| P1-2 | 完成卡与队列完成度不一致 | `pages` = 队列卡 + Done，Done 无条件位于队尾；滑动全程开放 | 中间多张复习卡未作答也能滑到底并播「今日任务完成」；DoneCard 注释所写「全部移除后才到达本卡」的前提在当前实现下不成立 |

## 1. 决策

| 决策点 | 选择 | 理由 |
|---|---|---|
| 封顶间隔 | 1→3→7→15→**30** | 370/30 ≈ 12 词/天，配额 10 附近收支接近平衡；45 天对高龄学习者记忆维持偏激进 |
| 毕业门槛 | 固定 **15**（与 INTERVALS 解耦） | 🎓 毕业体验不变；15 之后升 30 只影响到期复核节奏 |
| 毕业判定 | `days >= GRADUATED_DAYS`（原为 `==`） | ⚠️ 若维持 `==`，毕业词升到 30 后 🎓 反而回退——本变更是 R4 的硬前提 |
| lapse 权重 | TermState 增 `lapses`，排序 `lapses 降序 → dueTime 升序` | 简单插队首方案会打乱「先清旧债」的次要目标，双键排序两者兼顾 |
| lapses 衰减 | 升级时 `lapses / 2` | 遗忘史随成功升级半衰，避免一次忘了永久高优先 |
| 清债模式 | due ≥ 20 → pool = due.take(15)、不补新词 | 先清债再学新；阈值与扩容量保守，避免老人单日负担过重 |
| peek 求助 | 展开内容但**不写任何状态**，按钮保留 | 老人需要帮助通道；peek 后作答仍走正常状态机，自评权留给用户 |
| 滑动策略 | 考试态未作答**不锁滑动**，全程开放 | ① 跳过不写 `TermState`，次日 `isDue` 仍为真且 `dueTime` 更旧 → 在 v5 双键排序中反而更靠前，**自愈无债务**；② 强制作答会把「跳过」转成「乱答」（「忘了」在 v5 代价加重，用户倾向点「认识」），污染连击层与 lapses——即往 Out of Scope 的「认识虚高」方向加码；③ Anki bury / SuperMemo postpone 说明跳过是一等控制权 |
| 完成卡分流 | 以未作答复习卡数驱动文案与播报，不做闸门 | 滑动保持自由，那「完成」的判定就必须从「滑到底」改成「队列答完」；用提示而非阻塞，避免把完成卡变成变相锁定 |

## 2. 数据模型变更

```kotlin
// TermState：间隔层 + 遗忘史
data class TermState(val days: Int, val lastSeen: String, val lapses: Int = 0)
```

事件表（增量，其余同 v4 §9.2）：

| 事件 | 间隔层 |
|---|---|
| 「忘了」（任何地方） | days=1、lastSeen=今天、**lapses+1** |
| 满 3 连击升级（lastSeen≠今天） | days=nextInterval、lastSeen=今天、**lapses/2** |
| 满 3 连击但闸门挡住 / 无状态首次完成 | lapses 不变（首次完成写 {1, 今天, 0}） |

## 3. 排序与配额（buildQueue）

```kotlin
val due = scope.filter { isDue(...) }
    .sortedWith(
        compareByDescending<Int> { termStates[it]?.lapses ?: 0 }
            .thenBy { termStates[it]?.let { s -> dueTime(s) } ?: Long.MAX_VALUE }
    )
val pool = when {
    channel == "rec" && due.size >= DEBT_THRESHOLD -> due.take(DEBT_QUOTA)   // 清债模式
    channel == "rec" -> (due + news).take(DAILY_POOL_QUOTA)                   // 现状
    else -> due + news                                                        // 场景频道不变
}
```

常量：`INTERVALS = [1, 3, 7, 15, 30]`；`GRADUATED_DAYS = 15`（const，不再跟随 INTERVALS.last()）；`DEBT_THRESHOLD = 20`；`DEBT_QUOTA = 15`。

> ⚠️ 有意打破 09-17-elderly-literacy-app/design.md §8「毕业阈值自动跟随阶梯」不变量：阶梯与毕业解耦后，改阶梯不再自动改毕业门槛。GRADUATED_DAYS 必须显式评审。

## 4. 持久化与迁移

- terms JSON 增写 `"lapses"`；load 侧 `optInt("lapses", 0)`——旧数据自动迁移，无需迁移代码。
- 其余结构（day / queues / answered / position）不动；读写对称兜底逻辑不动。

## 5. 交互契约修订（TermCard/AppRoot）

| 卡形态 | 点卡片主体 | 「想看答案」按钮 |
|---|---|---|
| 新学（NEW） | 重听「词+提示」（不变） | 无 |
| 考试态未作答（REVIEW/FREE 且未 reveal） | TTS「再想一想，想起来了吗？」——**不念词、不念提示** | 有：展开拼音+提示（peek 态），按钮保留 |
| peek 后未作答 | 重听「词+提示」（已展开，泄题无意义） | 已展开，消失 |
| 已作答 | 重听（不变） | 无 |

- peek 态 = AppRoot 内 `peeked: mutableStateMapOf<seq, Boolean>`（会话态、不持久化、不写 repo）；重启后回未展开——与 revealed（作答）区别在于不 markAnswered。
- 底部提示文案：考试态未作答时「👆 想不起来？点下面的按钮」；其余维持「👆 点一下再听 · 点单字看讲解」。
- 自动播报 cardSpeech 不变（peek 不触发重播）。
- TTS 泄题修复的实现点：TermCard 主区域 clickable 的回调按形态分流——考试态未作答回调 onPeek-hint（播提示语）而非 onSpeakTerm。
- **滑动策略（R7）**：全程开放，不因未作答而锁定（决策理由见 §1）。跳过不写任何状态，被滑过的卡仍在 `pages` 中，往回滑即可作答；因为它无债务风险，所以不需用阻塞来兜底——需要兜底的是下面「完成」的语义。

### 5.1 完成卡分流（R7）

- 未作答数口径：`pages` 中 `mode == CardMode.REVIEW && revealed[seq] != true` 的卡数（NEW 卡无作答按钮、FREE 卡属自由刷，均不计入）。
- 实现为一个**局部函数**而非 `val`：函数体内每次读取 `pages` / `revealed` 的委托 getter，才能拿到当前值。若写成 `val`，`LaunchedEffect(pagerState, pages)` 的闭包会捕获旧值——页面集合未变而仅作答（满 3 连击移除时不追加卡）的情况下 effect 不重启，滑到完成卡会播报过期张数。
- `pending == 0`：文案与播报**完全维持 v4 现状**（DoneCard：🎉 / 今日任务完成！/ 认识了 x 个，忘了 y 个 / 继续上滑，随便看看；播报：太棒了，今日任务完成！…）。
- `pending > 0`：主文案「还有 N 张没作答」，副文案「今天答了 M 张」（M = 已作答复习卡数），引导「往下滑，回去把它们答完」；播报「还有 N 张没作答。往下滑，回去把它们答完吧。」——此分支不再出现「今日任务完成」与 🎉。
- ⚠️ 方向词：`VerticalPager` 中未作答卡位于完成卡**之前**（上方），回看需手指**下滑**（上滑是 index+1）。因此文案用「往下滑」而非「上滑」，且此分支**不显示 `SwipeHint`**（其文案固定为「上滑看下一个 ↑」，方向相反会误导）。
- 「今天答了 M 张」为队列维度计数，不依赖会话计数；`session.known/forgot` 仅在 `pending == 0` 分支沿用（重启归零为已知瑕疵，本期不改语义）。

## 6. 回滚

- 全部改动集中在 StudyRepository.kt / TermCard.kt / AppRoot.kt / DoneCard.kt 四个文件 + 原型 HTML；按改动分层可分别 revert（R7 只落在 AppRoot.kt / DoneCard.kt / 原型，与调度层无耦合）。
- 持久化新增字段为缺省读，回滚版本读到多余 key 无害。

## 7. 验证

- 编译：`gradlew assembleDebug`。
- 手动场景走查（见 implement.md 清单）：升级播报 30 天、lapse 优先排序、清债配额、防泄题、peek 不写状态、旧 JSON 兼容、完成卡分流、未作答时滑动仍可用。
