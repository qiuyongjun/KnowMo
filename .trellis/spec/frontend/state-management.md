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

## Gotcha：effect 与播报的三条规矩（v5 R7 / R8）

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

**症状**：卡片被教读 / 追加 / 插入完成卡了但那次播报没发生（声音莫名其妙丢了）；或者**翻页动画刚开始就被取消**（v5 R8 自动前进实际踩到的形态，表现为「作答后不翻页」或停在两页之间）。

**原因**：`LaunchedEffect(pagerState, pages)` 以 `pages` 为 key，而 effect 体内（教读追加考核卡、`syncDonePage()` 插入完成卡）会改 `pages` → effect 执行到一半被自己重启并取消。

**判定规则**：**effect 体内写过的 state，不要作为自己的 key**。key 只留真正的外部触发源，需要读的 state 在 `collect` 体内读。

⚠️ **推论：即使 key 就是「这次任务的身份」，复位也必须排在挂起动画之后。** R8 的自动前进 effect 以 `advanceAfterSpeechSeq` 为 key，最初把复位写在 `animateScrollToPage` **之前** → 复位使 key 变化 → effect 被自己重启 → `animateScrollToPage` 在跨帧挂起中被取消，翻页胎死腹中。

对照：`LaunchedEffect(pages, restoreTo)` 里也是「先置 key 再滚」，却一直没问题——因为它用的是**非动画**的 `scrollToPage`，同一次 dispatch 内就跑完了，取消来临时工作已完成；`animateScrollToPage` 必然 `withFrameNanos` 挂起，躲不过。

**修法**：复位放进 `finally`（用户抢方向打断动画时也能归位），且**比较后再清**：

```kotlin
} finally {
    if (advanceAfterSpeechSeq == seq) advanceAfterSpeechSeq = -1
}
```

否则用户答下一张时（key 已换成新 seq）会被这次收尾一并抹掉，下一次自动前进就丢了。注意 `delay` / `awaitQuiet()` **不要**包进同一个 `try`——同理。

### (3) 播报完成信号：靠 `speaking` 标志，且 `onStart` 必须置位

**症状**：作答播报被掐断——卡片在「词 + 提示 + 反馈」念完前就自动翻走。

**原因**：`TTSSpeaker.speak` 是 `QUEUE_FLUSH`；抢在播报结束前翻页，落点卡的 `cardSpeech` 会把整句冲掉。而判断「念完没有」用的是 `speaking` 标志——它有两处**必须**置位，少一处就会出现「`awaitQuiet()` 立刻返回」：

1. **`speak()` 里必须同步置 `true`**（不能只靠 listener 的 `onStart`）：否则「`speak()` 之后立刻 `awaitQuiet()`」会读到还没置位的 `false`；
2. **listener 的 `onStart` 也必须置 `true`**：`QUEUE_FLUSH` 冲掉上一句时会先给**上一句**发 `onStop`（"flushed from the queue" 也走 `onStop`），那一瞬间标志被清成 `false`；若新那句的 `onStart` 不置回，它**整段播放期间** `speaking` 都是 false。

清除点：`onDone` / `onError`（两个重载）/ `onStop` / `stop()` / `shutdown()`。`!ready` 的 pending 分支**不置位**（只入队，没有实际播报）。

**修法**：`awaitQuiet()` = `withTimeoutOrNull(20_000) { while (speaking) delay(100) }`（上限兜住 TTS 引擎异常不回调）。

**未核实（写在代码注释里了）**：flush 的 `onStop` 与下一句 `onStart` 的到达顺序，依据是同一条回调 binder 上的顺序消息。若真机实测顺序相反（表现仍是播报被掐断），升级为强版本——每次 `speak` 生成唯一 utteranceId，listener 按**身份过滤**（`if (id != currentId) return`）。

**通用结论（仍然成立）**：同一次交互里两处需要播报的语义——换频道语 + 卡片播报——一律拼成一句 `speak`（现有实现即 `announce + text`），分开 `speak` 会互相 FLUSH。

## 播报去重键按「页身份」

`spokenKeys` 的键必须绑在**页实例**上（`"$channel:seq:${p.seq}"`），完成卡用常量 `"$channel:done"`。

**不要**把下标放进键：追加（考核卡 / 完成卡 / 池型页）会让同一张卡在不同时刻对应不同下标，绑 `seq` 才能让「页身份」与「列表位置」解耦。同理也别让键依赖会反复变动的派生值。完成卡用常量键是安全的——`pending == 0` 之后不可能再产生待处理卡，它一旦出现就不会消失。⚠️ v5 R10：池型页（温故流 / 分区）抽完会重洗，**同一个词可能出现两次**，故必须按**页身份**（自增页序号）而不是词 id 入键，否则第二次出现会被静默去重。

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
| 作答后翻页动画刚起步就被取消 | 复位写在了 `animateScrollToPage` 之前（key 被自己改写） | 复位放 `finally` 且比较后再清，见 Gotcha (2) |
| 作答播报被掐断、卡片提前翻走 | `speaking` 没在 `onStart` 置位，`QUEUE_FLUSH` 的 `onStop` 把它清成 false | `speak()` 与 `onStart` 都置位，见 Gotcha (3) |
| 重启后战果变成 0 | 用会话计数表达当日成绩 | 计数落 `DayState`，装载时从 repo 初始化 |
