# 状态管理

> 本项目（Mando / 适老识字 app，Kotlin + Jetpack Compose）的状态约定。
> 语言：中文，与 `prd.md` / `design.md` / 代码注释保持一致（模板原文要求英文，如需统一切换请一次性改全部 spec）。

---

## 状态分层

| 层 | 载体 | 生命周期 | 例子 |
|---|---|---|---|
| 持久层 | `StudyRepository`（SharedPreferences + 单 key JSON） | 跨会话 | `TermState`（days / lastSeen / lapses）、`DayState`（连击层 + 当日战果计数）、`DailyQueue` |
| 会话态 | `AppRoot` 内 `remember { mutableStateMapOf(...) }` | 进程存活期，重启归零 | `revealed`、`peeked`、`results`、`taught` |
| 页面态 | `mutableStateOf<List<Page>>` | 频道切换时重建 | `pages`、`freePool` |

**判定规则**：跨重启必须一致的落持久层；能靠 `seq` / `qIndex` 重建的放会话态。**表达「今天做到哪儿了」的数字必须落持久层**——完成是跨会话可达的事件（推荐频道 10 张配额约 30 张卡），会话计数会让战果在重启后显示 0。

> **Warning**：`revealed` 的键是 `seq`（本次**出现**序号），不是词 id。v4 起同一词当天会出现多张卡（学 1 次 + 连击考核若干次），按词记录会把后续追加的考核卡一并展开，重启后连击卡死。`DailyQueue.answered` 同样按**卡实例**逐卡记录，理由相同。

## 派生状态

**规则：派生值一律由 state 现场推导，不另存副本。**

即：能写成 `pages.count { ... }` 的，就不要新增 `var xxx by remember`。副本必然带来同步遗漏。

```kotlin
/** 待处理：新词卡未被教读，或复习卡未作答；FREE 卡恒不计入 */
fun isPending(p: Page): Boolean = p is Page.TermPage && when (p.mode) {
    CardMode.NEW -> taught[p.seq] != true
    CardMode.REVIEW -> revealed[p.seq] != true
    CardMode.FREE -> false
}
```

由 `pages` / `taught` / `revealed` 推导，作答与教读后随重组自动更新。凡是「还剩多少没做」「完成没有」这类判断，一律走这个口径，不要另立一套。

## Gotcha：effect 与播报的三条规矩（v5 R7 三次踩坑）

### (1) 在闭包里读派生值，必须写成局部函数

**症状**：播报的内容比屏幕文案旧一拍（读到的张数 / 完成度是上一轮的）。

**原因**：effect 的 key 不含 `revealed` / `taught`。派生值若写成 `val`，会被某一次 composition 的闭包捕获，而「页面集合未变、仅发生作答」时 effect 不重启，闭包里的值就停在旧值。

```kotlin
// ❌ 错：闭包捕获旧值
val pending = pages.count(::isPending)
LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
        .collect { (idx, scrolling) -> /* ... 用的是这次 composition 的 pending */ }
}

// ✅ 对：每次调用读当前值
fun pendingTaskCount(): Int = pages.count(::isPending)
```

**代价**：Kotlin 局部函数不能前向引用，必须声明在首个使用点之前（本项目里放在 `spokenKey` / `cardSpeech` 之上）。

**替代方案**（择一，不要混用）：把 `revealed` / `taught` 也加进 effect 的 key（effect 会频繁重启，重播判定需另做）；或用 `derivedStateOf`（读取须发生在 composition 内，闭包外仍要函数包装）。

### (2) key 里不能放 effect 体内会改写的 state

**症状**：卡片被教读 / 追加 / 插入完成卡了，但那次播报没发生——声音莫名其妙丢了。

**原因**：`LaunchedEffect(pagerState, pages)` 以 `pages` 为 key，而 effect 体内（教读追加考核卡、`syncDonePage()` 插入完成卡）会改 `pages` → effect 执行到一半被自己重启并取消。

**判定规则**：**effect 体内写过的 state，不要作为自己的 key**。key 只留真正的外部触发源，需要读的 state 在 `collect` 体内读。

### (3) 同一次交互里的两处播报语义，必须合并成一句

**症状**：前向拦截的越界解释语只播出了前几个字。

**原因**：`TTSSpeaker.speak` 是 `QUEUE_FLUSH`。退回动画结束后，目标页会再触发一次停稳并播 `cardSpeech`，把解释语冲掉——把 `speak` 挪到动画之后也不行，200ms 的停稳去抖不构成足够间隔。

**修法**：把解释语挂到 `blockHint` state，在落点停稳时与 `cardSpeech` **拼成一句** utterance 播出；落点卡此前已播报过时只补解释、不重复念词。**推论**：换频道语 + 卡片播报同理（现有实现即 `announce + text` 拼接）。

**附注**：`snapshotFlow` 是 conflated 的——collector 挂在 `animateScrollToPage` 期间的状态变化不会被逐个 emit，只在 collector 空出来时重读一次，因此退回动画自身的 `scrolling == true` 通常**不会**触发 `tts.stop()` 掐断解释语。但这条时序未经源码核实（本机无 Compose 源码），**不要依赖它**——合并成一句才是稳的。

## 播报去重键按「页身份」

`spokenKeys` 的键必须绑在**页实例**上（`"$channel:seq:${p.seq}"`），完成卡用常量 `"$channel:done"`。

**不要**把下标放进键：追加（考核卡 / 完成卡 / 自由刷页）会让同一张卡在不同时刻对应不同下标，绑 `seq` 才能让「页身份」与「列表位置」解耦。同理也别让键依赖会反复变动的派生值。完成卡用常量键是安全的——`pending == 0` 之后不可能再产生待处理卡，它一旦出现就不会消失。

## 完成度不由「进度」表达

不展示进度条、不展示「第 x / y 张」，也不靠「滑到底」。完成是**事件**：队列里没有待处理卡时，完成卡才被插入 feed。`session` / 战果计数只能用于**展示成绩**，不得参与「做完了没有」的判定。

## 战果计数（当日持久）

`DayState.knownAnswers` / `forgotAnswers` 是**当日持久计数**（隔天随 `DayState` 整体作废），`markKnown` / `markForgot` 各自 +1；UI 侧的 `SessionStats` 只是它的**重组镜像**，装载队列时用 `repo.todayKnown()` / `repo.todayForgot()` 初始化。

`SessionStats` 只做**展示**（完成卡战果、播报），**不得**参与「做完了没有」的判定——那走 `pendingTaskCount()`。

## Common Mistakes

| 症状 | 根因 | 修法 |
|---|---|---|
| 播报内容比屏幕文案旧一拍 | 派生值写成 `val`，被 `LaunchedEffect` 闭包捕获 | 改写为局部函数，见 Gotcha (1) |
| 追加 / 插入完成卡后该次播报丢失 | effect 的 key 含它自己会改写的 `pages` | key 只留外部触发源，见 Gotcha (2) |
| 同一词的多张考核卡一起展开 | 用词 id 而非 `seq` 记录展开态 | 按页出现（`seq`）记录；队列用 `answered[i]` |
| 一张卡没答也能看到「任务完成」 | 用「滑到底」当完成判定 | 完成卡改为条件出现，判定走 `pendingTaskCount()` |
| 卡在列表里换了位置后重复播报 | 去重键含下标 | 键绑页身份 `seq`；完成卡用常量 |
| 越界解释语只播出前几个字 | 解释语当场 `speak`，被落点卡的播报 FLUSH | 挂 `blockHint`，与 `cardSpeech` 拼成一句（Gotcha (3)） |
| 重启后战果变成 0 | 用会话计数表达当日成绩 | 计数落 `DayState`，装载时从 repo 初始化 |
