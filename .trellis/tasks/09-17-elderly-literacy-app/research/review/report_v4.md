# v4 代码检查报告（工作区未提交改动）

> 对象：`git diff HEAD`，13 个已修改文件（3 文档 + 9 Kotlin + 1 HTML）
> 基线：HEAD = `ad32d06`（词库 368 词）
> 方法：逐文件 diff 走查 + 全符号导入/签名人工核对 + Python 复刻调度器模拟（`sched_sim_v4.py` / `sched_sim_v4_daily.py`）+ WCAG 对比度推算
> **限制**：本机确认无 JDK/SDK（`which java javac kotlinc` 全无；`/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot` 只有 `lib/`，无 `bin/java.exe`），**未编译、未实测**。所有运行时结论均为「推算」。

## 审查基线快照（重要）

审查期间工作区**仍在被并发修改**（`design.md` 16:45:46、`prd.md` 16:46:01、`implement.md` 16:46:15、`design.md` 16:46:30、新增 `review-2026-09-17.md` 16:53:30）。

- 本报告结论基于 **16:46:20 冻结的 diff 快照**（`.trellis/tasks/09-17-elderly-literacy-app/research/review/_snapshot_v4.diff`，md5 `7298d5f3…`）。
- 快照之后仅 `design.md` §8.6 表格新增一行（「可逆性」）、并出现一份主会话自写的 `review-2026-09-17.md`；**9 个 Kotlin 文件自 16:45:46 起未再变动**，故代码类结论仍然有效。
- Kotlin 文件 mtime：MainActivity 14:57、TermCard 16:02、TTSSpeaker/Theme 16:18、DoneCard 16:39、WordBank 16:40、StudyRepository 16:42:37、Common 16:42:44、AppRoot 16:45:46。
- 工作区已存在另一份同级报告 `review-2026-09-17.md`（P1×8/P2×8）。本报告独立成稿，重合处我标注了**评级分歧**及理由。

---

## 1. 结论摘要

### 1.1 分级问题表

| 编号 | 级别 | 问题 | 证据 | 状态 |
|---|---|---|---|---|
| P0-C | **P0 候选（未验证）** | `isDue(state, today: String = today())` — 参数名与成员函数 `today()` 同名，默认值表达式可能被参数遮蔽而编译失败 | `StudyRepository.kt:87` + `:82` | 需编译确认 |
| P1-1 | P1 | **首日队列 368 张**（无新词配额）；完成卡在第 369 页、自由刷首日不可达 → prd「先不限配额」在 368 词下失效 | `StudyRepository.kt:173`；推算 `sched_sim_v4.py` S1 | report_v3 #3 未修（已登记） |
| P1-2 | P1 | **空队列日 / 永久空转**：理想用户 60 天仅 4 天有内容（第 1/2/5/12 天）；第 13 天 12 分区全毕业 → 推荐频道此后每天队列为空，直接落在完成卡并播「今天学完了，认识了 0 个」 | 推算 `sched_sim_v4_daily.py`；`StudyRepository.kt:141-147`、`AppRoot.kt:116-122` | 新发现（毕业机制引入） |
| P1-3 | P1 | **毕业后 15 天维持循环在推荐频道断链**：毕业分区被排除出推荐队列（含其到期词），自由刷卡又无反馈按钮（不推进 `days`）→ 用户不进该场景频道就永远不再复习该分区 | `StudyRepository.kt:141-147`、`:153-155`、`AppRoot.kt:246`、`TermCard.kt:149` | 新发现（与 design §8.6「长期维持记忆」意图矛盾） |
| P1-4 | P1 | **原型未跟随 v3**：仍 36 词/7 场景、仍用静态 `REC_QUEUE`、仍有「第 x / y 张」进度条、**复习卡朗读念出词本身（泄题，直接违反 v3 验收 1）**、仍有生词本/设置弹层 | `index.html:254-297`、`:301/:346`、`:401-402/:412-413`、`:485`、`:184-185/:230` | report_v3 #8 未修（已登记）+ 新交互漂移 |
| P1-5 | P1 | **对比度硬性约束 5 处 < 7:1**：拼音 5.75、复习 badge 5.11、结果文案 5.11、完成卡按钮 5.75、忘了按钮 6.57；`design.md` §2 声称「#1565C0 白字 8.6:1」实测 5.75:1（文档数据错误） | `TermCard.kt:120/171`、`Common.kt:88`、`DoneCard.kt:49`、`design.md:19`；推算见 §4.1 | 新发现 |
| P1-6 | P1 | **字号/触控落值低于硬性约束**：7 条 5 字词组主字 42sp（<48sp）；频道 chip 热区推算 ≈53dp（<64dp）；字卡内单字（5 字词）≈54dp；「更大字体」开关无任何入口（`setFontScale` 零调用）→ prd AC 落空 | `TermCard.kt:93-97`、`Common.kt:55`、`TermCard.kt:110`、`AppRoot.kt:61-62` | 新发现（原型侧同源：`index.html:53/41/90`） |
| P2-1 | P2 | `lastSeen` 缺失/损坏时该词**静默消失**（既不算到期也不算未学），仅偶现于自由刷 | `StudyRepository.kt:281` + `:87-90` + `:167`/`:173` | 新发现（同级报告评 P1） |
| P2-2 | P2 | 每次翻页/作答都 `persist()` 全量 JSON（推算 ~25KB 主线程序列化）；断点恢复首帧多写一次 `position=0`（末次写入收敛为断点值，低风险，非「断点必归零」） | `AppRoot.kt:155-161`、`StudyRepository.kt:222-228/238-266` | 新发现（与同级报告 P1-7 结论不同，见 §4.3） |
| P2-3 | P2 | TTS：`pendingQueue` 跨线程 `ArrayDeque`（CME 风险）；`stop()` 不清 `pendingQueue` → **冷启动窗口**仍可能连播（违反 v3 验收 1 的窄窗口）；`ready` 先置位再补播期间新 `speak` 会 `QUEUE_FLUSH` 打断补播 | `TTSSpeaker.kt:24/33-37/42-50/52-55` | 新发现（部分与同级 P1-2 重合，我评 P2） |
| P2-4 | P2 | 完成卡战果 `session` 纯内存，重启归零（完成卡念「认识了 0 个」）；`known` 无持久化而 `forgot` 有，不对称 | `AppRoot.kt:41/65/107/169/174` | report_v3 #6 未修（已登记） |
| P2-5 | P2 | 死代码：`addForgot`/`forgotIds`/`forgotCount`/`setFontScale`/`setSpeechRate`/`setRemindTime`/`remindTime` 零调用；`persist()` 仍写 `forgot/fscale/rate/remind`；`:30` 注释仍写「生词本、设置项」 | `StudyRepository.kt:55/58/60/65/70/75/238-266` | report_v3 #7 未修（已登记） |
| P2-6 | P2 | 重启后 `revealed` 恢复、`results`（「N 天后再见」文案）不恢复 → 半恢复态 | `AppRoot.kt:66/67/120` | 新发现 |
| P2-7 | P2 | 「忘了」的队尾重现卡因 `revealed` 按 termId 记录，恒为展开态、无按钮 → 只是复读，不是「当天再考一次」 | `AppRoot.kt:165/180/246`、`TermCard.kt:149` | 与同级 P1-1 同一事实，我评 P2（prd 原文是「再见一次」，需求歧义） |
| P2-8 | P2 | 清债顺序：剩余到期词被排在**全部新词之后**，与 `:160` 注释「保证清债优先」自相矛盾 | `StudyRepository.kt:160/179-194` | 新发现（同级 P2-2 已提及） |
| P2-9 | P2 | 欢迎语必被首卡播报打断（`QUEUE_FLUSH`），仅「未 ready」时才受 pending 保护 | `AppRoot.kt:86` vs `:217`、`TTSSpeaker.kt:49` | 新发现 |
| P2-10 | P2 | 辅助文案低于 22sp 档：点听提示 18sp、SwipeHint 20sp、TermBadge 19sp（原型 `.relisten` 19px） | `TermCard.kt:141`、`Common.kt:106/94` | 新发现 |
| P2-11 | P2 | README 口径漂移：「7 场景 × 6 词组」「复习状态仅内存」「含设置/生词本/进度条」「正文 ≥18sp」「主字 42–64sp」 | `android-app/README.md:35/36/42/43/52/53/57/61/62` | 新发现（顺带解释了 42sp 的来源） |
| — | 无影响 | `load()` 的 `modes` 兜底 `MODE_REVIEW`：本版本首次引入该字段，不存在缺字段旧数据 | `StudyRepository.kt:296` | 同级 P2-1 评级偏高 |

**P0 已确认项：0**（上一轮 `DoneCard.kt` 缺 `Box` 导入已彻底修复）。**P1：6 项**，**P2：11 项**。

### 1.2 一句话结论

v3 主干（每日队列调度、断点续刷、隔天重建、忘了尾部重现、不泄题朗读、200ms 停稳去抖、进度条下线、动态 badge、分区毕业）在 Android 侧**代码级已落地且互相自洽**（推算验证通过）；主要风险集中在三处：**(a) 无配额的 368 张首日队列 + 毕业后的永久空队列**（产品语义，会直接决定实机第一印象）、**(b) 原型未同步 v3**（已不具备「冻结基准」资格）、**(c) 6 处适老化硬性指标实际落值不达标**。另有 1 项编译候选待 Android Studio 确认。

---

## 2. 逐项核对 report_v3 的 8 项

| # | 原问题 | 判定 | 证据（当前行号） |
|---|---|---|---|
| 1 | **P0** `DoneCard.kt` 缺 `import ...layout.Box` → 编译失败 | ✅ **已修（彻底）** | `DoneCard.kt:5` 已有导入；`:44` `Box(` 使用；同批删除的其它导入（`Arrangement`/`size`/`AppSurface`/`GreenBg`/`GreenKnown`/`OrangeBg`）复核确实未被使用；`:53` 用全限定 `androidx.compose.ui.graphics.Color.White`，不引入新依赖 |
| 2 | **P1** `design.md` 两个 `## 9` + `### 8.4` 挂错节，`§8.x` 引用失效 | ✅ **已修（与建议改法不同但自洽）** | 现为 `## 8. v3…`:84 + `8.1`:88/`8.2`:102/`8.3`:115/`8.4`:127/`8.5`:132/`8.6`:139，`## 9. 取舍与风险`:172（唯一）；文内引用 `§8.6`:108、`§8.3`:136 **自洽**；`AppRoot.kt:51/57/98/195` 与 `implement.md:4/14/15` 均引用 §8.x → 全仓 `grep §8` 无失效引用（报告曾建议改为 §9，作者改为把 v3 节降到 §8，结果一致，无需再动） |
| 3 | **P1** 368 词首日队列 368 张，prd「先不限配额」失效 | ❌ **未修（已登记为已知未决）** | `StudyRepository.kt:173` `val news = scope.filter { termStates[id] == null }.shuffled()` 无上限；推算首日 rec = **368 张**、`market` = 30 张（`sched_sim_v4.py` S1）；`implement.md:37` 已写「推荐频道首日队列 = 全部未学新词（368 张）…待 QYJ 决策」。**新增后果**：完成卡位于第 369 页 → 首日无法到达完成卡，`AppRoot.kt:155-161` 的自由刷预追加也因此不可达 |
| 4 | **P1** 15 天封顶无毕业 → 稳态 ~38 张/天永不停 | 🟡 **部分修复（按设计决策关闭，但暴露新问题）** | 新增分区毕业：判定 `:124-132`、推荐范围排除 `:141-147`、自由刷池不排除 `:153-155`；`design.md:139-170` §8.6 + `prd.md:71-75` 第 7 条把「不做词级/每日毕业、15 天封顶是有意的长期维持」定为决策；`implement.md:21` 勾选。**但**：旧问题被重新定性（不再是缺陷），同时产生 **P1-2 永久空队列** 与 **P1-3 维持循环断链** 两个新缺口（推算见 §4.2） |
| 5 | **P2** 跨频道共享 `TermState` 语义未写明 | ✅ **已修（文档）** | `prd.md:70`「**跨频道共享同一份学习状态**（同一词在推荐和场景频道共享 TermState），但各自维护独立队列」；实现侧行为与之相符（`:141-155` 用同一 `termStates`，`:199-206` 按频道各存 `DailyQueue`） |
| 6 | **P2** 完成卡战果只统计「本次会话」 | ❌ **未修（已登记）** | `AppRoot.kt:41/65` `SessionStats` 仍为 `remember` 内存态，`:107` 播报与 `:253-254` 卡片均取 `session`；重启后归零。`implement.md:39` 已登记 |
| 7 | **P2** `addForgot`/`forgotIds`/`forgotCount`/`forgot` 死代码 | ❌ **未修（已登记）** | `:55`/`:58`/`:60` 仍存在且零调用（我按符号逐个 grep 确认）；`:113` `markForgot` 仍写 `forgot`；`:245-246` `persist()` 仍写 `"forgot"`；`:30` 类注释仍称「生词本」。`implement.md:39` 已登记。**死代码范围比原报告更大**：`setFontScale:65`、`setSpeechRate:70`、`setRemindTime:75`、`remindTime:48` 同样零调用（见 P2-5） |
| 8 | **P2** 网页原型词库未同步（36 vs 368） | ❌ **未修（已登记）** | 原型仍 `TERMS=36`（`index.html:254-297`）、`SCENES=7`（`:244-252`），缺 `phone/medicine/express/property/emergency/weather` 六分区；与安卓 id 交集 36/36、安卓多出 332 词。`implement.md:38` 已登记。**且漂移不止词库**：见 P1-4（进度条/静态队列/泄题朗读/生词本/设置） |

**修复是否彻底 / 是否引入新问题**：
- #1 修复彻底，未引入新问题（`Box` 仍在使用，无「未用导入」告警）。
- #2 修复彻底（重编号方向与建议相反，但全仓引用一致，属等价修复）。
- #4 的修复**引入了两个新缺口**（P1-2/P1-3），属「以设计决策收口 + 副作用未评估」。
- #3/#6/#7/#8 未修但已写入 `implement.md`「已知未决」——登记属实，唯 #8 除词库外还遗留交互级漂移未登记。

---

## 3. 验收标准 / 步骤清单对照

### 3.1 prd「v3 验收标准」6 条（`prd.md:77-84`）

| 验收标准 | 判定 | 证据 |
|---|---|---|
| 1. 复习卡不念读音；新词卡停稳自动念；快速连滑无闪播 | 🟡 **部分（未实测）** | 不念词 ✅ `AppRoot.kt:99-105`（复习未答只念「这个词，还记得它念什么吗？」）；停稳去抖 ✅ `:197-219`（`isScrollInProgress` 为真即 `tts.stop()`；停稳后 `delay(200)`；`:205-207` 双保险）；**但** ① `TTSSpeaker.kt:52-55` `stop()` 不清 `pendingQueue` → 冷启动未 ready 窗口内仍会连播（P2-3）；② 原型侧**不满足**：`index.html:485` `speak(t.text+"。还记得它念什么吗…")` **念出了词本身**（泄题） |
| 2. 界面无「第 x/y 张」进度条 | 🟡 **安卓 ✅ / 原型 ❌** | 安卓：`ProgressRow`/进度条组件全仓 0 命中（仅 `AppRoot.kt:56`、`TermCard.kt:46` 注释提及）；原型：`:401-402`、`:412-413` 仍在渲染「第 ${idx+1} / ${total} 张」 |
| 3. 隔天重新调度；当天重启从断点继续；忘了的词当天尾部重现 | ✅ **安卓满足（推算）** | S2：同日重启 `ensureQueue` 返回同一队列且 `position=20` 保留（`:199-206`、`:222-228`）；S3：隔天 `date` 不符 → 重建 368 张（NEW 348 / REVIEW 20）、`position` 归 0；S4：`appendQueue` 首次 `true`（队列 368→369，尾部即该词）、二次 `false`（`:213-219`）。原型侧无日期概念，仍用静态 `REC_QUEUE`（`:301/346`），**不满足** |
| 4. 完成卡后继续滑出现自由刷卡，badge 显示「温故」 | 🟡 **代码满足，体验不可达** | `AppRoot.kt:140-152`（池空重洗、顺序追加）、`:155-161`（`idx >= doneIdx-1` 预追加 2 页）、`Common.kt:78/89`（`CardMode.FREE` → 「📖 温故」）。**但**首日队列 368 → 完成卡在第 369 页（推算），用户实际到不了；原型无自由刷功能 |
| 5. 反馈逻辑保留，间隔升级持久化，重启不丢 | ✅ **满足** | `AppRoot.kt:166-171`（认识 → 文案 + 播报 + `markKnown`）；`StudyRepository.kt:93-97`（1→3→7→15）；持久化 `persist():238-266` / `load():268-307` 字段一一对应（`days`/`lastSeen` ↔ `optInt/optString`）。边界例外见 P2-1 |
| 6. 分区毕业：🎓 徽章 / 次日不进推荐队列 / 场景频道仍可复习 / 自由刷池仍含该区词 | ✅ **满足（附带 P1-2/P1-3 副作用）** | 判定 `:124-132`；徽章 `Common.kt:38/48/58/63` + `AppRoot.kt:70/131/233`；排除推荐 `:141-147`；场景频道不受影响、自由刷池不排除 `:153-155`；推算 S6：market 30 词全 `days=15` → `rec` 队列 338 张新词中该区到期词 **0** 个，`market` 场景队列 30 张复习，自由刷池含 30 个 |

### 3.2 prd「Acceptance Criteria」（v2 时代清单，`prd.md:94-102`）

| AC | 判定 | 证据 |
|---|---|---|
| 抖音式 feed + scroll-snap 无半张卡 | ✅ | 安卓 `VerticalPager`（`AppRoot.kt:235-241`，天然 snap）；原型 `scroll-snap-type:y mandatory` + `scroll-snap-stop:always`（`index.html:63/67`） |
| 顶部 ≥6 频道可切换 | ✅ | 安卓 13 频道（`StudyData.kt:25-39`）；原型 7 频道（`index.html:244-252`） |
| 词组单元 + 逐字拼音 + 用途 + 自动朗读 + 点按重听 | ✅ | `TermCard.kt:98-137`、`:72` `.clickable { onSpeakTerm() }`；`:86` 场景图标 |
| 单字点按开字卡弹层 | ✅ | `TermCard.kt:109` → `AppRoot.kt:249` → `:261-263` `CharSheet`（`Sheets.kt:40-91`） |
| 新学/复习混排 + 复习流程 + 反馈即时更新间隔 | ✅ | `StudyRepository.kt:179-189` 交错；`TermCard.kt:149-163` 双按钮 → `:117-137` 展开 |
| 队列末尾完成卡 | ✅ | `AppRoot.kt:122` 追加 `Page.Done`；`DoneCard.kt:27-62` |
| 无手写/无拍照；**设置页含字体/语速/提醒/记忆曲线说明** | ❌ | 安卓设置入口已删（`AppRoot.kt:61-62`「不再提供设置入口」），`Sheets.kt` 仅剩 `CharSheet`；`setFontScale/setSpeechRate/setRemindTime` 零调用。原型**仍满足**（`index.html:199-228`）→ 两端相反 |
| 适老化硬性约束逐条满足 | ❌ | 见 §4.1（对比度 5 处、字号 1 处、触控 3 处、弹层层级） |
| 原型冻结为工程基准 | ❌ | 原型仍 v2（`index.html:6/227` 自述「原型 v2」），与 v3 工程实现不同步 |

### 3.3 implement.md「v3 步骤清单」10 步（`implement.md:11-22`）

> 注意：清单在本轮审查期间被改写（原为 7 步全 `[ ]`，现为 10 步、1–9 已勾 `[x]`）。下面按**代码实际状态**独立核实，不采信勾选。

| 步骤 | 代码实际状态 | 证据 |
|---|---|---|
| 1 删 `Term.kind`/`days`/`REC_QUEUE` | ✅ 安卓侧已完成（`StudyData.kt` 未在本轮改动，属上一提交）；原型侧未做 | `StudyData.kt:8-21` 无 kind/days；全仓 `REC_QUEUE` 在 android-app 命中 0；原型 `:256-296/301` 仍在 |
| 2 TermState/DailyQueue + 到期 + buildQueue + 自由刷池 | ✅ 已实现（带 P1-1/P2-1/P2-8 缺陷） | `:14-27`、`:87-90`、`:162-196`、`:233-234` |
| 3 AppRoot 接调度器 + 朗读契约 | ✅ 已实现（带 P2-3/P2-6 缺陷） | `:112-127`、`:197-219`、`:99-109` |
| 4 TermCard 去 index/total + 动态 badge | ✅ | `TermCard.kt:49-57` 无 index/total；`Common.kt:78-100` 三态 badge |
| 5 DoneCard/Common 去 ProgressRow | ✅ | 全仓 `ProgressRow` 0 命中；`DoneCard.kt:59` 用 `SwipeHint()` |
| 6 静态检查：无残留 `REC_QUEUE`/`kind`；JSON 读写对称 | ✅ 残留=0；读写对称（逐字段核对一致），边界缺口见 P2-1 | `persist():238-266` ↔ `load():268-307`：`fscale/rate/remind/forgot/terms{days,lastSeen}/queues{date,queue,modes,position}` 全对应 |
| 7 审查修复（DoneCard `Box` 导入） | ✅ | `DoneCard.kt:5` |
| 8 词库校验（2 处拼音修正） | ✅ | diff：`express-16` `má→mǎ`、`weather-10` `chen→chén`；我另核对 368 条「拼音音节数 == 字数」全部通过（`WordBank.kt:13` 的 `require` 不会在启动即抛错） |
| 9 分区毕业机制 | ✅ 已实现（副作用 P1-2/P1-3） | `:124-132/141-147/153-155`、`Common.kt:38-68`、`AppRoot.kt:70/131/233` |
| 10 QYJ 实机构建 + 实机验证 | ❌ 未做（我也确认本机无 JDK/SDK） | 无 `java.exe`/`kotlinc`；`android-app/` 无 `build/` 产物 |

---

## 4. 详细发现

### 4.1 适老化硬性约束落值（逐条，指数推算）

对比度为按 WCAG 2.x 相对亮度公式推算（`sched_sim_v4.py` §3）：

| 用途 | 前景/背景 | 推算对比度 | 约束（≥7:1） |
|---|---|---|---|
| 词组主字 | `#1A1A1A` / 白卡 | 17.40:1 | ✅ |
| 用途说明 | `#424244` / 白卡 | 10.03:1 | ✅ |
| 辅助说明 | `#424244` / `#F5F7FA` | 9.34:1 | ✅ |
| **逐字拼音** | `#1565C0` / 白卡（`TermCard.kt:120`） | **5.75:1** | ❌ |
| 新学 badge | `#0D47A1` / `#E3F2FD`（`Common.kt:87`） | 7.56:1 | ✅ |
| **复习 badge** | `#BF360C` / `#FFF3E0`（`Common.kt:88`） | **5.11:1** | ❌ |
| 温故 badge | `#1B5E20` / `#E8F5E9`（`Common.kt:89`） | 7.00:1 | 临界 ✅ |
| 反馈按钮「认识」 | 白字 / `#1B5E20`（`TermCard.kt:158/205`） | 7.87:1 | ✅ |
| **反馈按钮「忘了」** | 白字 / `#B71C1C`（`TermCard.kt:153`） | **6.57:1** | ❌ |
| **完成卡按钮** | 白字 / `#1565C0`（`DoneCard.kt:49`） | **5.75:1** | ❌ |
| **结果文案（忘了）** | `#BF360C` / `#FFF3E0`（`TermCard.kt:171-176`） | **5.11:1** | ❌ |
| 频道 chip 激活/毕业 | `#0D47A1`/`#E3F2FD`、`#1B5E20`/`#F5F7FA`（`Common.kt:62-64`） | 7.56 / 7.33:1 | ✅ |

- `design.md:19` 声称 `--color-primary #1565C0`「白字对比 8.6:1」——**实测 5.75:1**，token 表数据错误（会误导后续按此选色）。
- 触控：反馈按钮 92dp ✅（`TermCard.kt:197`，prd 要求 ≥88dp）；完成卡按钮 76dp ✅（`DoneCard.kt:47`）；**频道 chip ≈53dp** ❌（`Common.kt:55` `padding(vertical=14.dp)` + `21sp` 文本 ⇒ ≈24.6+28≈53dp，是全 app 唯一需反复点按的控件）；**5 字词组单字热区 ≈54dp** ❌（`TermCard.kt:93-97` `else -> 42.sp` + `:110` `padding(vertical=2.dp)`）；`Sheets.kt:78` 相关词 chip ≈52dp（弹层内，可接受度较高）。
- 字号：拼音 26sp ✅（`TermCard.kt:120`）、用途说明 22sp ✅（`:129`）；**5 字词组 42sp ❌（<48sp）**，命中 7 条：`market-5 三块五一斤`、`transit-3 从前门上车`、`transit-22 老弱病残孕`、`bank-2 请输入密码`、`gov-24 老年人优先`、`gov-29 身份证复印`、`food-3 加一双筷子`；辅助文案 18sp/19sp/20sp（`TermCard.kt:141`、`Common.kt:94/106`）低于 22sp 说明档（P2-10）。「更大字体」开关**无入口** ❌（`AppRoot.kt:61-62` 自认；`repo.setFontScale` 零调用）——即当前 `fontScale` 恒为 1f，`AppRoot.kt:222-224` 的放大链路永远不生效。
- 导航层级：安卓现为 **1 屏 + 1 弹层**（`AppRoot.kt:261-263` 仅 `CharSheet`；设置/生词本已删），不满足「1 屏 + 2 弹层」；原型为 **1 屏 + 3 弹层**（`index.html:196/199/231`），两端都偏离。
- snap 吸附：两端满足 ✅（`AppRoot.kt:235`；`index.html:63/67`）。
- 原型侧同源偏差：`.chip{min-height:56px}`（`:53`）、`.iconbtn{height:60px}`（`:41`）、`.sheet-close{60×60}`（`:137`）均 < 64px；`.len5/.len6 → 44px`（`:90`）< 48px。

### 4.2 调度器四条路径走查（**推算**，非实测）

复刻实现见 `research/review/sched_sim_v4.py`（算法逐行照抄 `StudyRepository.kt`），日期取 2026-09-17。

**A. 到期计算 `isDue`（`:87-90`）** ✅ 正确
- `lastSeen + days*DAY_MS <= today`；`markSeen`（`:104-108`）写 `days=1, lastSeen=今天` ⇒ 次日到期、当天不到期（S4 推算：`当天到期=False / 次日到期=True`）✅ 与 prd「明天再来」一致。
- `runCatching{...}.getOrDefault(false)` 使异常静默为「不到期」——正确性上偏保守，但配合 `news` 的 `termStates[id] == null` 过滤会**静默丢词**（P2-1）。
- 跨天用 `SimpleDateFormat("yyyy-MM-dd", Locale.US)` 解析后再做毫秒运算：**无 DST 陷阱**（中国无夏令时），且 `days` 为整数天 ⇒ 安全。

**B. 当天断点恢复（`ensureQueue` `:199-206` + `saveQueuePosition` `:222-228` + `AppRoot.kt:125/134-138`）** ✅ 推算通过
- 同日重启：`existing.date == today()` ⇒ 返回同一 `DailyQueue`（队列 368 与首次完全一致），`position` 保留（S2）。
- `position` clamp 到 `0..queue.size`；`AppRoot.kt:137` 再 clamp 到 `pages.lastIndex`（完成卡索引 = `queue.size`）⇒ 断点落在完成卡上也安全。
- 断点恢复的「不重回考试态」：`AppRoot.kt:120` `if (mode == REVIEW && repo.termState(id)?.lastSeen == q.date) revealed[id] = true` ✅ 正确（当天已作答的复习卡直接展开）。**缺口**：`results` 文案不恢复（P2-6）。
- **推算的时序细节**（与同级报告 P1-7 结论不同）：`LaunchedEffect(pagerState)`（`:155-161`）首帧确实会以 `currentPage=0` 落库一次，但 `:134-138` 的 `pagerState.scrollToPage()` 是**瞬时跳页（非 `animateScrollToPage`）**，跳页后 `snapshotFlow` 会再发射一次并写入正确断点 ⇒ **末次写入收敛为断点值**；只在「首帧窗口内进程被杀」时可丢断点。真正确定的问题是**写放大**（每次翻页/作答全量 `persist()`，见 P2-2）。

**C. 隔天重调度（`ensureQueue`）** ✅ 推算通过 / ⚠️ 语义缺口
- S3：`date` 不符 → 重建 ⇒ 348 新词 + 20 到期复习、`position=0`（不重头，因为队列本身变了）✅ 符合 v3 验收 3。
- ⚠️ 但**队列规模不收敛**：因无配额，只要还有未学词，队列就恒 ≈368（S1/S3）；理想用户 60 天里仅 4 天有内容（第 1/2/5/12 天），第 13 天 12 分区全部毕业 → 推荐频道**永久空队列**（P1-2）。
- ⚠️ 跨午夜保持前台时 `ensureQueue` 不会被重新调用（只在 `LaunchedEffect(channel)` 内），当日队列会沿用（`:112`）；`appendQueue` 因 `date != today()` 静默返回 false（`:215`）⇒ 跨天场景下「忘了尾部重现」失效（推算，未实测）。

**D. 忘了的词当天尾部重现（`markForgot` `:111-115` + `appendQueue` `:213-219` + `AppRoot.kt:178-182`）** ✅ 推算通过
- S4：首次 `appendQueue = true`（队列 368→369，尾元素即该词，该词合计出现 2 次）；再次调用 `false`（`count { it == id } != 1`）⇒ 去重按「只出现一次」判定有效，含 `replay()` 重置 `revealed` 后重复作答的场景 ✅ 幂等。
- UI 侧插入位置 `indexOfFirst { it is Page.Done }`（`:180`）与 repo 的「追加到队列尾部（完成卡之前）」语义一致 ✅。
- ⚠️ 重现卡恒为展开态（P2-7）：`revealed` 以 termId 为 key，`AppRoot.kt:246` `p.mode != REVIEW || revealed[id]==true` ⇒ 该卡无「认识/忘了」按钮、直接显示拼音/说明 + 「没关系，明天再来一遍」。

**E. 负荷推算（用于 P1-1/P1-2 决策量化）** —— 全部为**推算**

| 策略 | 首日 | 峰值 | 前 30 天均值 | 有内容天数/60 | 备注 |
|---|---|---|---|---|---|
| 现状（无配额）· 理想全对 | 368 | 368 | — | **4/60**（1,2,5,12） | 第 13 天起毕业 → 永久空队列 |
| 现状（无配额）· 答对率 80% | 368 | 368 | ≈70 | 60/60 | 第 2 天 368、第 5 天 292、第 27 天 184；复习债显著堆高 |
| 配额 10/天（仅示意对照） | 10 | 40 | 27.3 | — | 30 天后首次空队列日 = 第 48 天 |
| 配额 20/天（仅示意对照） | 20 | 60 | 39.2 | — | 第 32 天空队列 |

> 结论（供决策）：「无配额」在 368 词下既让**首日不可完成**（P1-1），也让**毕业后期永久空转**（P1-2）；两个方向都由「队列范围 = 全部未学 + 全部到期、且毕业即整块移出」这一条规则决定。建议在 prd 层面同时定：①推荐频道每日新词配额（8–10）；②毕业分区的到期复习是否仍进推荐队列（或改为「毕业分区只出复习、不出新词」）。

### 4.3 编译级静态检查（逐符号人工核对，**未编译**）

**导入完整性 / 符号引用**（逐文件核对，均一致）：
- `AppRoot.kt`：`Column/fillMaxSize/fillMaxWidth/statusBarsPadding/navigationBarsPadding/background`、`VerticalPager/rememberPagerState`、`Composable/CompositionLocalProvider/LaunchedEffect/getValue/setValue/mutableFloatStateOf/mutableStateMapOf/mutableStateOf/remember/rememberCoroutineScope/snapshotFlow`、`LocalDensity/Density/Modifier`、`delay/launch`、`STUDY_TERMS/StudyRepository/Term/TermChar/sceneName`、`TTSSpeaker/AppSurface` —— 全部存在且被使用 ✅（`androidx.compose.ui.graphics.Color.White` 用全限定名，`:229`/`DoneCard.kt:53`，无需导入）。
- `Common.kt` / `TermCard.kt` / `DoneCard.kt` / `Sheets.kt` / `Theme.kt` / `TTSSpeaker.kt` / `MainActivity.kt` / `StudyRepository.kt` / `WordBank.kt` / `StudyData.kt`：本次新增/删除的符号（`CardMode`、`TermBadge`、`SwipeHint`、`ChannelBar(graduated=)`、`TermBadge` 三态、`TermState/DailyQueue/MODE_*/GRADUATED_DAYS/nextInterval`、`isSceneGraduated/graduatedScenes/freePoolIds`）**均有定义且被调用**；被删符号（`ProgressRow`/`RedBg`/`BadgeDot`/`ProgressTrack`/`Term.kind`/`Term.days`/`REC_QUEUE`）全仓 0 引用 ✅。
- 函数签名匹配：`TermCard(term,mode,revealed,resultText,onSpeakTerm,onCharClick,onAnswer)`（`TermCard.kt:49-57`）、`DoneCard(known,forgot,onReplay)`（`DoneCard.kt:27`）、`ChannelBar(current,graduated,onSelect)`（`Common.kt:38`）、`CharSheet(ch,terms,onSpeak,onDismiss)`（`Sheets.kt:40`）、`TermBadge(mode)`（`Common.kt:85`）与调用处逐参数类型一致 ✅。
- `SharedPreferences` 键名一致：`getSharedPreferences("study_state")`（`:36`）+ `putString("state", …)`（`:264`）/ `getString("state", null)`（`:269`）✅ 对称。
- JVM/包结构：`namespace`/`applicationId` `com.qyj.shibang`（`app/build.gradle.kts:8/12`）与目录 `java/com/qyj/shibang/**` 及各文件 `package` 声明**一致**（同级报告 P2-5 的「目录与包声明不一致」我核对后**不成立**；`shibang` 与产品名「慢慢懂」不符属历史命名，无编译影响）。
- 平台一致性：`compileSdk 34 / minSdk 26`（`:9/:13`）、Compose BOM `2024.09.02`（`:37`）、`activity-compose 1.9.2`（`:41`）、Kotlin 2.0.x 插件（`:4` `kotlin.plugin.compose`）与所用 API 匹配 ✅：`VerticalPager`（foundation 1.7，稳定 API）、`rememberPagerState(pageCount=lambda)`、`PagerState.scrollToPage`（suspend）、`enableEdgeToEdge(statusBarStyle=SystemBarStyle.light(...))`（activity 1.9）、`HorizontalDivider`/`ModalBottomSheet`（material3 1.3）、`mutableFloatStateOf`（compose runtime 1.6+）、`data object`（Kotlin 1.9+）。
- `AndroidManifest.xml:21-24`：`android:exported="true"` + LAUNCHER intent-filter（targetSdk 34 必需）✅；TTS engine `<queries>` 存在 ✅。

**唯一编译风险点（P0 候选，无法本地证实）**：

```kotlin
// StudyRepository.kt:82
fun today(): String = fmt.format(Date())
// StudyRepository.kt:87
fun isDue(state: TermState, today: String = today()): Boolean = runCatching { ... }
```

参数 `today: String` 与成员函数 `today()` 同名，默认值表达式 `today()` 处于「内层参数遮蔽外层成员」的位置。Kotlin 对**同名属性与函数**的解析是**按调用形式区分**的（属性与函数可同名并存），若解析器在参数作用域就停止匹配，则此处会报 `Expression 'today' of type 'String' cannot be invoked as a function`（编译阻断）；若按「非可调用变量不阻断外层函数解析」处理则编译通过。**两种行为我无法在本机验证（无 JDK），故列为 P0 候选而非确认项。**
**一行零风险改法**：`fun isDue(state: TermState, today: String = this.today())` 或把参数改名为 `onDate`。建议顺手改掉（成本极低，且能消除 Android Studio 的遮蔽告警）。

### 4.4 两端漂移（原型 vs android-app）

| 维度 | android-app | prototype/index.html | 判定 |
|---|---|---|---|
| 词库 | 368 词 / 13 频道（12 场景 + rec） | 36 词 / 7 频道 | ❌ 漂移（交集 36/36，安卓多 332；缺 6 分区） |
| 队列 | 每日调度器（`date+channel` 持久化） | 静态 `REC_QUEUE`（`:301/346`）+ `kind/days` 静态字段（`:256-296`） | ❌ 漂移（v3 核心未同步） |
| 进度条 | 已删除 | 仍在（`:401-402/412-413`） | ❌ 违反 v3 验收 2 |
| 复习卡朗读 | 「这个词，还记得它念什么吗？」（不念词） | `t.text + 还记得它念什么吗`（**念词 = 泄题**，`:485`） | ❌ 违反 v3 验收 1 |
| 完成卡 | `DoneCard` + 自由刷（温故无限流） | `doneCardHTML` 后回到队首（`replayFeed:425`），无自由刷 | ❌ 缺 v3 功能 |
| 生词本 / 设置 | 均已删除（`Sheets.kt` 仅 `CharSheet`） | 仍在（`:184-185/199-228/230-234`） | ❌ 反向漂移（原型更旧更全） |
| 朗读语速/字体档 | 有字段无入口（`fontScale=1f` 恒定） | 有设置 UI（`setFontScale(1.15)` 等） | ⚠️ 两端行为不同 |
| 语音实现 | Android TTS（`QUEUE_FLUSH`/pending 队列） | Web Speech（`speechSynthesis.cancel()` 后 `speak`，语义等价于 FLUSH） | ✅ 契约基本一致 |
| 自动朗读触发 | 停稳 + 200ms debounce（`:197-219`） | `IntersectionObserver threshold 0.55`（`:500`），**无 debounce** | ⚠️ 行为不同（v3 的「快速连滑不闪播」未在原型实现） |

**根因**：两套硬编码数据结构 + 手工同步（`implement.md:38` 已登记「待转单一数据源 + 脚本生成两端」）。本次迭代只改了原型的两处语音文案 + 阈值 + 自动播放解锁（`index.html` diff），**未跟随 v3 任何结构变更**。

### 4.5 其它代码级发现（细节证据）

- **P2-1 `lastSeen` 损坏静默丢词**：`load():281` `s.optString("lastSeen")` 缺键返回 `""` → `isDue` 中 `fmt.parse("")` 抛异常被 `runCatching` 吞（`:87-90`）→ 该词既不在 `due`（`:167` 过滤 false）也不在 `news`（`:173` 要求 `termStates[id] == null`）⇒ 永不进每日队列，只偶现于自由刷池。建议：`load()` 时校验 `lastSeen` 可解析，否则丢弃该条（视为未学）。（同级报告评 P1；我评 P2，因为 `persist()` 总是同时写 `days`+`lastSeen`，只有外部损坏/半写才可达——但它确实使 `implement.md` 步骤 6「读写对称」在**异常路径**上不成立。）
- **P2-2 写放大**：`persist()`（`:238-266`）每次翻页（`AppRoot.kt:157`）、每次 `markSeen/markKnown/markForgot` 都把**全部** `termStates`（最多 368 条）+ **全部频道队列**（rec 队列最多 369 项 + modes）重新序列化。推算 JSON ≈ 18KB(terms) + 5KB(queue) + 其余 ≈ **25KB/次**，主线程构造字符串。适老化低端机上有卡顿风险（**未实测**）。建议：翻页只写 `position`（单独 key 或轻量 JSON），或改为 `commit/apply` 节流。
- **P2-3 TTS 并发/时序**：`pendingQueue`（`:24`）为 `ArrayDeque`，主线程 `speak()`（`:44-45`）与 TTS 回调线程 `forEach/clear`（`:33-37`）无同步 ⇒ 理论 CME；`stop()`（`:52-55`）不清队列 ⇒ 冷启动未 ready 时「滑动打断」失效（最多 4 条补播）；`ready=true` 先于补播，补播期间新 `speak` 走 `QUEUE_FLUSH`（`:49`）会**打断正在补播的欢迎语**（与 `:9-10` 注释的设计意图相反）。建议：`stop()` 内 `pendingQueue.clear()`、补播前 `pendingQueue.toList().also{ pendingQueue.clear() }`、或加 generation 计数。
- **P2-9 欢迎语被打断**（同一根因的 UI 表现）：`AppRoot.kt:86` 欢迎语与 `:217` 首卡播报都走 `QUEUE_FLUSH`，首卡在 200ms 后即播 ⇒ 欢迎语必被截断；`:214-217` 的 `pendingAnnounce` 只保护「频道切换语 + 首卡」的合并，不保护欢迎语。
- **P2-6 半恢复态**：重启后 `revealed`（`:120`）会恢复成「已展开」，但 `results`（`:67`，反馈文案）丢失 ⇒ 用户看到已展开的复习卡却没有「👍记得牢！N 天后再见」，与 v3「反馈后间隔标注即时更新」的持久化语义不完全一致。
- **P2-8 清债口径**：`:160` 注释「剩余复习词排在新词之后，保证清债优先」自相矛盾；`:190-194` 把剩余到期词排在**全部新词之后**。若到期数 > 新词数（如 80% 答对率下的第 27 天：184 复习 / 0 新词），全部复习仍会出现，只是顺序靠后；若两者都多（第 5 天 292 张），「清债」被稀释。与 `prd.md:62`「到期复习词优先（先清债）」口径不一致，需求需拍板。
- **无影响的防御分支**：`:296` `while (modes.size < queue.size) modes.add(MODE_REVIEW)` — 本版本首次引入 `modes`，不存在缺字段旧数据，实际不触发（同级 P2-1 评级偏高）。
- **P2-5 死代码范围**（我独立 grep 的调用点计数，排除定义文件）：
  `addForgot 0 / forgotIds 0 / forgotCount 0 / setFontScale 0 / setRemindTime 0 / remindTime 0 / setSpeechRate 0`（`TTSSpeaker.setSpeechRate` 是另一类型的方法）；持久化的 `forgot/fscale/rate/remind` 四个字段因而成为只写不读；`:30/:100/:110` 注释仍称「生词本」。
- **P2-11 README 漂移**（`android-app/README.md`）：`:35` 「7 场景频道 × 6 生活词组」（实为 12 场景/368 词）、`:36/:53/:61` 「生词本/设置持久化」「复习状态仅内存」（已变为 TermState 持久化、生词本死代码）、`:42/:43` 「Sheets 含设置/生词本」「Common 含进度条」（已删）、`:57` 「主字 42–64sp、正文 ≥18sp」（**与 prd 硬性 ≥48sp/≥22sp 冲突**——这正是 `TermCard.kt:96` 42sp 的口径来源）、`:62` 「提醒未接 AlarmManager」。文档与 prd 冲突应统一。

---

## 5. 未验证项（不得视为通过）

1. **编译结果**：本机无 JDK/SDK（`java`/`javac`/`kotlinc` 均不存在；Adoptium 安装只有 `lib/`），**任何「编译通过」结论都未验证**。其中 **P0 候选 `StudyRepository.kt:87` 必须由 Android Studio 编译判定**。上一轮 P0（`Box` 导入）我仅能确认「导入已存在且被使用」。
2. **真机时序**：停稳 200ms debounce 与「快速连滑不闪播」的手感、`isScrollInProgress` 在 `scrollToPage` 瞬时跳页时的取值、冷启动 TTS 未 ready 窗口的实际长度与是否真的连播 —— 均为**推算**，需实机。
3. **TTS 实际音质/打断效果**：中文引擎是否可用、`QUEUE_FLUSH` 截断欢迎语的实际听感、`pendingQueue` 并发是否真触发 —— 未验证。
4. **性能**：每次翻页 ~25KB 主线程 JSON 序列化是否造成可感知卡顿 —— 未实测（无设备）。
5. **断点恢复的末次写入收敛**：我推算「首帧 `position=0` 会被随后的跳页写入覆盖」，未在真机验证「首帧窗口内杀进程是否丢断点」（见 §4.2 B）。
6. **对比度**：为 WCAG 公式推算值，未用取色器/真机截图复核；OLED/亮度自适应下的实际观感未验证。
7. **原型**：只在静态层面核对了 HTML/JS/CSS 与两端差异，**未在浏览器实际运行**（自动播放解锁 `:642+`、语音降级、snap 吸附手感未验证）。
8. **拼音准确性**：本轮只核对「音节数 == 字数」（368/368 通过）与作者自述的 2 处修正（`express-16`/`weather-10`）；声调/轻声的逐条正确性属 `research/review/report.md` + `pinyin_check.md` 范围，**本轮未重做**。
9. **README/manifest/资源**：只做了与本轮 diff 相关的对照（manifest 未改动且无明显缺陷）；README 之外的项目级文档未全量核对。
10. **`research/review/_snapshot_v4.diff` 与两个 `sched_sim_v4*.py`**：为本次审查新增的分析产物（非源码），如需清理可删除。

---

## 6. 建议处理顺序（仅建议，未修改任何源码）

1. **P0 候选**：`StudyRepository.kt:87` 改 `this.today()`（一行），随后进 Android Studio 首次编译，把编译错误一次性暴露（本机无 JDK 是当前最大盲区）。
2. **P1-1 + P1-2 一起定需求**（同一根规则）：推荐频道加每日新词配额（8–10）；明确毕业分区的到期复习是否保留在推荐队列；空队列日的 `DoneCard` 文案/首屏（是否直接进自由刷）。
3. **P1-4**：要么同步原型到 v3（含删进度条、去掉泄题朗读、换调度器），要么在 prd 写明「原型冻结于 v2，v3 只维护 android-app」——现在的「原型 = 工程基准」条款（`prd.md:102`）与事实冲突。
4. **P1-5/P1-6**：一屏级的硬指标修正（`else -> 48.sp` 或允许换行；chip `heightIn(min=64.dp)`；拼音改 `BlueDark #0D47A1`(7.56:1) 或加粗放大；「忘了」按钮/完成卡按钮改深色底或加深色描边；恢复极简设置档或删掉该 AC 并落字 prd；修正 `design.md:19` 的 8.6:1 数据）。
5. **P2-3**：`stop()` 清 pendingQueue + 补播防打断（真机会被老人第一时间听到）。
6. P2-4/P2-5/P2-11：完成卡战果改从 `TermState` 统计、删死代码与只写字段、README 与 prd 口径对齐（≥48sp/≥22sp）。
