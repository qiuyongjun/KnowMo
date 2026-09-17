# 组件与交互契约

> Mando 的 Compose UI 约定与 feed 交互契约。
> 语言：中文，与 `prd.md` / `design.md` / 代码注释保持一致。

---

## 组件结构

- 所有 Composable 放 `ui/`；业务状态由 `AppRoot` 持有并下传，子组件**不感知调度器与 repo**。
- **形态分流集中在调用点**，组件只渲染。例：`TermCard` 只收 `revealed` / `peeked` / `onSpeakTerm` / `onPeek` / `onAnswer`，「考试态未作答该播提示语还是重听」的判定在 `AppRoot` 的调用点完成。
- 参数扁平传递；不要在两个文件里各维护一份分流规则。
- 文案与 TTS 话术集中在 `AppRoot`（`cardSpeech`），组件只负责渲染。

## feed 交互契约（VerticalPager）

### 方向语义

`VerticalPager` 以 index 递增为「下一个」：**手指上滑 = index+1**；手指下滑 = index−1（回看）。

> **Warning**：凡是引导用户「回去看前面某张卡」的文案，方向词必须是**往下滑**。
> `SwipeHint()` 的文案固定为「上滑看下一个 ↑」，在需要往回滑的分支里**不得渲染**，否则方向相反会误导。
>
> v5 R7 起**不再需要往回看的引导文案**：前向消化保证「身前永远是没答完的卡」（见下），正常路径上没有任何分支该说「往下滑」。

### 滑动策略：未作答不锁定

**决策（v5 评审）**：考试态未作答时**不锁滑动** —— `VerticalPager` 不得设置 `userScrollEnabled = false`，也不得加任何形式的未作答拦截。

依据：

1. 跳过不写 `TermState`，次日 `isDue` 仍为真且 `dueTime` 更旧 → 在 `buildQueue` 的双键排序中反而更靠前，**自愈、无债务**；
2. 强制作答会把「跳过」转换成「乱答」（「忘了」在 v5 代价加重，用户倾向点「认识」），污染连击层与 lapses，即放大「认识」自评虚高；
3. Anki bury / SuperMemo postpone：跳过与延后是一等的用户控制权。

**配套（v5 R7）**：不锁滑动的代价是「滑过的卡落在身后」，所以必须同时实现**前向消化**（见下）。否则用户会被困在队尾、必须往回滑才能做完——跳过应当是「稍后」，不是「放弃」。

### 完成度语义：完成卡是**事件**，不是**位置**

**规则**：`Page.Done` 只在 `pendingTaskCount() == 0` 时**插入** feed（任务区末尾）。未做完时 feed 里根本没有收尾页，队尾就是最后一张待处理卡。

```kotlin
fun isPending(p: Page) = p is Page.TermPage && when (p.mode) {
    CardMode.NEW -> taught[p.seq] != true      // 新词卡按「是否已教读」
    CardMode.REVIEW -> revealed[p.seq] != true // 复习卡按「是否已作答」
    CardMode.FREE -> false                     // 自由刷不计入
}
```

完成卡文案是**单一形态**（不再按 pending 分流）：🎉 / 今日任务完成！/ 今天学完了 N 个词 · 认识了 x 次，忘了 y 次 / 继续上滑，随便看看 / `SwipeHint`。

### 前向消化（滑过即回收）与 frontier 不变量

**不变量**：设 frontier = 第一张待处理卡的下标，则 **frontier 之前全是已处理卡，frontier 起全是待处理卡**；用户永远站在 frontier 上，「往上滑」永远有下一张待处理卡可做。

- 向前停稳到 `i` 时，把 `[lastSettled, i)` 里仍待处理的卡按原相对顺序移到队尾（完成卡之前）；`lastSettled = i − 回收张数`。
- 往回滑、断点跳页（`restoreTo != null`）只移动锚点，**不回收**（身后只可能是已处理卡）。
- 回收**只改 `pages` 顺序**：不碰 `revealed` / `peeked` / `results`（状态按 `seq` 跟着卡走），不调 repo。
- 装载队列时对任务区做**稳定分区**（已处理在前、待处理在后），把上次会话「滑过但未回收」的卡归位；断点位置 = frontier。

> **Warning**：pager 必须传 `key`（`TermPage -> seq`、`Done -> 常量`）。回收会移动列表项，没有稳定 key 时 pager 按 index 定位，会把用户正在看的卡换成别的卡。

> **Warning**：`appendReviewCard` 的插入点是**任务区末尾**——`indexOfFirst { it is Page.Done }` 取不到时用 `pages.size`。写成 `coerceAtLeast(0)` 会在 Done 缺席（新模型下的常态）时把新卡插到**队首**。

### 播报去重键绑「页身份」

去重键 = `"$channel:seq:${p.seq}"`，完成卡 = `"$channel:done"`。

**不要**把下标或会变动的派生值放进键：回收会改变同一张卡的序号，含序号的键会被当成新卡而重复播报。

## 适老化硬约束

- 反馈按钮高度 92dp；图标 40–44sp；词字随词长 42–64sp；点击目标不小于 48dp。
- TTS 语速 0.85x。播报在**滑动停稳后**延迟约 200ms 触发，滑动进行中一律 `tts.stop()`（快速连滑不闪播）。
- 关键语义不用 emoji 表达：作答按钮用 √ / × 字形（随字号缩放、无彩色表情歧义）。
- 不做设置页、生词本、进度条。

## Common Mistakes

| 症状 | 根因 | 修法 |
|---|---|---|
| 引导文案方向相反 | 把「上滑 = 下一个」误用于「回看」 | 回看用「往下滑」，且该分支不渲染 `SwipeHint()` |
| 有卡没答却看到「任务完成」 | 完成卡被当成队尾的固定页 | `pendingTaskCount() == 0` 时才把 `Page.Done` 插入 feed |
| 滑过未答卡后必须往回滑才能做完 | 只做了「不锁滑动」，没做前向消化 | 向前停稳时把 `[lastSettled, i)` 的待处理卡回收队尾 |
| 回收后正在看的卡被换成别的卡 | pager 没传 `key` | 补 `key`（`TermPage -> seq`、`Done -> 常量`） |
| 回收 / 追加后该次播报消失 | 播报 effect 的 key 含 `pages`（body 会改它） | key 只留 `pagerState`；`pages` 在 collect 体内读 |
| 卡被回收后再滑到时重复播报 | 去重键含下标 | 键绑 `seq`；完成卡用常量 `done` |
| 新追加的考核卡跑到队首 | `appendReviewCard` 用了 `coerceAtLeast(0)` | 取不到 Done 时用 `pages.size` |
| 复习卡点一下就把答案念出来 | 点卡回调没按形态分流 | 调用点按 `mode` + `revealed` + `peeked` 路由到提示语 |
