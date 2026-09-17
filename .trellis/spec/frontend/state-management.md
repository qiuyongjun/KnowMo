# 状态管理

> 本项目（Mando / 适老识字 app，Kotlin + Jetpack Compose）的状态约定。
> 语言：中文，与 `prd.md` / `design.md` / 代码注释保持一致（模板原文要求英文，如需统一切换请一次性改全部 spec）。

---

## 状态分层

| 层 | 载体 | 生命周期 | 例子 |
|---|---|---|---|
| 持久层 | `StudyRepository`（SharedPreferences + 单 key JSON） | 跨会话 | `TermState`（days / lastSeen / lapses）、`DayState`（连击层）、`DailyQueue` |
| 会话态 | `AppRoot` 内 `remember { mutableStateMapOf(...) }` | 进程存活期，重启归零 | `revealed`、`peeked`、`results` |
| 页面态 | `mutableStateOf<List<Page>>` | 频道切换时重建 | `pages`、`freePool` |

**判定规则**：跨重启必须一致的落持久层；能靠 `seq` / `qIndex` 重建的放会话态。

> **Warning**：`revealed` 的键是 `seq`（本次**出现**序号），不是词 id。v4 起同一词当天会出现多张卡（学 1 次 + 连击考核若干次），按词记录会把后续追加的考核卡一并展开，重启后连击卡死。`DailyQueue.answered` 同样按**卡实例**逐卡记录，理由相同。

## 派生状态

**规则：派生值一律由 state 现场推导，不另存副本。**

即：能写成 `pages.count { ... }` 的，就不要新增 `var xxx by remember`。副本必然带来同步遗漏。

```kotlin
fun pendingTaskCount(): Int =
    pages.count { it is Page.TermPage && it.mode == CardMode.REVIEW && revealed[it.seq] != true }
```

由 `pages` 与 `revealed` 推导，作答后随重组自动更新。

## Gotcha：在 `LaunchedEffect` 闭包里读派生值，必须写成局部函数

**症状**：滑到完成卡时，播报的张数是过期的（屏幕上的卡片文案却是新的）。

**原因**：`LaunchedEffect(pagerState, pages)` 的 key 不含 `revealed`。派生值若写成 `val`，会被这一次 composition 的闭包捕获：

```kotlin
// ❌ 错：闭包捕获旧值
val pending = pages.count { it is Page.TermPage && it.mode == CardMode.REVIEW && revealed[it.seq] != true }
LaunchedEffect(pagerState, pages) {
    snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
        .collect { (idx, scrolling) -> /* ... */ tts.speak(if (pending > 0) "还有${pending}张没作答" else DONE) }
}
```

当「页面集合未变而仅发生作答」时（满 3 连击的卡不再追加新页），effect 不重启，闭包里的 `pending` 停留在旧值 → 播报过期。这正是 v5 R7 踩到的坑。

**修法**：写成局部函数。函数体每次调用都走 `pages` / `revealed` 的委托 getter，读到当前值：

```kotlin
// ✅ 对：每次调用读当前值
fun pendingTaskCount(): Int =
    pages.count { it is Page.TermPage && it.mode == CardMode.REVIEW && revealed[it.seq] != true }
```

**代价**：Kotlin 局部函数不能前向引用，必须声明在首个使用点之前（本项目里要放在 `spokenKey` / `cardSpeech` 之上）。

**替代方案**（择一，不要混用）：把 `revealed` 也加进 effect 的 key（effect 会频繁重启，重播判定需另做）；或用 `derivedStateOf`（读取须发生在 composition 内，闭包外仍要函数包装）。

## 会话计数 vs 队列口径

`SessionStats(known/forgot)` 是**会话计数**（`remember`，重启归零），含义是「本次打开 app 答了多少」。

凡是表达**队列完成度**的地方（如完成卡分流）**不得**使用它，必须走队列/页面口径 —— 见 `pendingTaskCount()` / `answeredTaskCount()`。

## Common Mistakes

| 症状 | 根因 | 修法 |
|---|---|---|
| 播报内容比屏幕文案旧一拍 | 派生值写成 `val`，被 `LaunchedEffect` 闭包捕获 | 改写为局部函数，见上文 Gotcha |
| 同一词的多张考核卡一起展开 | 用词 id 而非 `seq` 记录展开态 | 按页出现（`seq`）记录；队列用 `answered[i]` |
| 完成卡说「任务完成」但还有卡没答 | 用「滑到底」当完成判定 | 完成度由数据推导，见 `component-guidelines.md` |
| 重启后完成卡计数变成 0 | 用会话计数表达队列完成度 | 改用队列/页面口径 |
