# Journal - qyj (Part 1)

> AI development session journal
> Started: 2026-09-17

---

## 2026-09-17 — v5 新增 R7：完成卡按队列完成度分流

**任务**：`.trellis/tasks/09-17-scheduling-v5`（提交 `56c897b`）

### 起点：一次交互设计探讨

问题：「推荐每日任务时用户可以随意上下滑动，但用户选择认识/不认识时该不该允许滑动？」

核查代码后得到决定结论的关键事实：**滑过未作答的卡不产生债务**。滑动不写 `TermState`，次日 `isDue` 仍为真，且 `dueTime` 比当天答过的词更旧 → 在 v5 的 `lapses 降序 → dueTime 升序` 双键排序里反而更靠前；新词卡被快速甩过连 `markSeen` 都不触发。跳过是**自愈**的。

因此结论是**不锁滑动**。三条理由：① 强制作答会把「跳过」转成「乱答」（v5 加重了「忘了」的代价，用户倾向点「认识」），即放大 Out of Scope 里的「认识自评虚高」；② Anki bury / SuperMemo postpone 说明跳过是一等控制权；③ 与「没关系，再学一遍」的产品语气一致。锁定的收益是零，代价是数据质量。

### 但发现了真问题：完成卡会说谎

`pages` = 队列卡 + `Page.Done`，`Done` 无条件在队尾 → 一路滑到底就播「今日任务完成」，哪怕中间多张复习卡一张没答；`DoneCard` 的 `known/forgot` 还是会话计数（`remember`，重启归零）。写这个结论时也把 `DoneCard.kt` 原有 KDoc「任务池为空或全部移除后才到达本卡」证伪了。

**修法不是封滑动，是修「完成」这句话的语义。** 用户批准作为 v5 补充项（规划工件已回退到 Phase 1 修订：prd R7 + 4 条验收项、design §5.1、implement 第 4 步 + 走查 8–11）。

### 实现要点（踩坑记录）

1. **派生值必须写成局部函数，不能写成 `val`** —— `LaunchedEffect(pagerState, pages)` 的 key 不含 `revealed`；页面集合未变而仅作答（满 3 连击移除、不追加新页）时 effect 不重启，`val` 被旧闭包捕获 → 播报过期张数。已沉淀到 `.trellis/spec/frontend/state-management.md`。
2. **方向词**：`VerticalPager` 里上滑 = index+1（下一个），未作答卡在完成卡**之前/上方**，回看要**往下滑**。`SwipeHint()` 文案固定「上滑看下一个 ↑」，`pending > 0` 分支必须不渲染它。
3. **播报去重键要随完成度变化**（`done:${pending}`）—— 否则先听到「还有 N 张没作答」、答完回本卡不再播「今日任务完成」。原型侧同样的坑以另一种形式存在（通用守卫用 `channel:undefined` 把完成卡回访全掐死），check 阶段发现并修掉。
4. `pending == 0` 分支经程序化逐字比对确认零回归（v4 文案原样搬入 `else`）。

### 其他

- 同批提交还带上了一轮遗留的未提交改动：作答按钮 emoji 😀/😕 → √/× 字形（随字号缩放、无彩色表情歧义）。
- 顺手修正 README / 注释漂移（阶梯 1→3→7→15→30、`DoneCard` 描述、交互契约补 v5）。
- **未验证项**：本机无 JDK / Android SDK / `gradlew`（仓库只有 wrapper properties），`gradlew assembleDebug` 跑不了，且 `cmd.exe` 被沙箱拒绝。编译交由 CI（`.github/workflows/android-build.yml`，ubuntu + JDK 17 + Gradle 8.7）在 push 后验证。任务因此保持 `in_progress`，未归档。
- 环境坑：本机 Bash 工具不可用（portable-git shim 报 `ls`/`dirname: command not found`），PowerShell 工具**不返回 stdout**，只能把输出重定向到文件再用 Read 读取；PowerShell 也不支持 bash 的 `<<'EOF'`，提交信息要走 `git commit -F <文件>`。

