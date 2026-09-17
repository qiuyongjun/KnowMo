# design.md — 学习复习调度优化 v5

> 延续 09-17-elderly-literacy-app/design.md §9（v4 连击 + 间隔双层模型）的迭代记录文体。
> v5 只动调度参数与排序、毕业判定、复习卡交互契约；双层状态机结构（连击层/间隔层/每日队列/自由刷）不变。

## 0. 问题定位（为什么改）

| # | 问题 | 根因 | 影响 |
|---|---|---|---|
| P0-1 | 到期债务必然堆积 | 370 词 × 15 天封顶 → 稳态每日到期 ≈25 > 配额 10 | 长期用户逾期队列只增不减，「忘了」率上升 → days 打回 1 → 债更多，恶性循环 |
| P0-2 | 「忘了」词排序反向 | markForgot 写 {days=1, lastSeen=今天} → 次日 dueTime 全池最新 → dueTime 升序排池尾 | 超额日 take(quota) 首先挤掉昨天刚忘的词——最需要复现的词反而见不到 |
| P1-1 | 复习卡点按泄题 | TermCard 主区域 clickable → onSpeakTerm 对所有形态生效 | 考试态未作答时一个 tap 听到「词+提示」，考回忆契约失效，连击虚高 |

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

## 6. 回滚

- 全部改动集中在 StudyRepository.kt / TermCard.kt / AppRoot.kt 三个文件 + 原型 HTML；单 commit 可整体 revert。
- 持久化新增字段为缺省读，回滚版本读到多余 key 无害。

## 7. 验证

- 编译：`gradlew assembleDebug`。
- 手动场景走查（见 implement.md 清单）：升级播报 30 天、lapse 优先排序、清债配额、防泄题、peek 不写状态、旧 JSON 兼容。
