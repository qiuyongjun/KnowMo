# 提交前总审报告（2026-09-20 17:26，小沃）

> 审查对象：HEAD `cc50f64`（R11）→ 当前工作区的全部未提交改动。
> 内容构成 = v6–v13 八轮功能 + 项目改名（认识么 / KnowMo，含包名迁移）+ 原型删除 + spec/任务文档同步。
> 定位：**提交前总审**——各轮（v6/v7/v8/v9/v11/v12/v13）均已有独立 trellis-check 记录在案，本轮不重审单轮逻辑，只核「整批改动作为提交候选」的完整性、一致性与残留。
> 方法：只读脚本 + git 状态取证；本机无 JDK / Android SDK，**未编译**（见文末验证边界）。

---

## 结论

**代码与数据层面：无 P0、无 P1。** 提交操作层面有 **1 项 P0**：整个新源码树未被 git 跟踪，直接 commit 会把全部源码从仓库里删掉。先 `git add` 再提交，其余可提。

---

## P0（提交操作级，必须先处理）

### P0-1 新源码树 `com/knowmo/app/**` 整体未跟踪，直接提交 = 源码从仓库消失

- `git status -uall` 共 **88 个脏路径**（18 M / 12 D / 58 ??，比此前口头的 77 多 11 个）。
- 未跟踪里包含 **12 个 kt 源文件**：`android-app/app/src/main/java/com/knowmo/app/{MainActivity,data/*,tts/*,ui/*,ui/theme/*}`。
- 同时旧树 `com/qyj/shibang/` 11 个文件显示 `D`（含 `Sheets.kt`）。
- **后果**：`git commit -am ...`（只提交已跟踪文件）会得到「旧源码全删、新源码不存在」的提交。这不是代码缺陷，是**提交操作**的雷。
- `.gitignore` 并未排除新路径（它们以 `??` 出现在 status 里），纯粹是还没 `git add`。
- **修法**：`git add -A`（或至少 `git add android-app/app/src/main/java/com/knowmo/`），add 后用 `git status` 复核新树 12 文件变 `A`。

### P0-2（同一条的知情项）新规范文件也未被跟踪

- `.trellis/spec/frontend/wordbank-guidelines.md`（词库内容契约，多处文档挂链引用）同样是 `??`。上面那条 `git add -A` 顺带覆盖，单独列出让它不被漏看。

---

## 静态验证全过项（证据清单）

| # | 检查 | 结果 | 证据 |
|---|---|---|---|
| 1 | 词库不变量 | ✅ **509 条，fails=0** | `check_wordbank_invariants.py` 实跑：分区内重词 0 / 孤儿 scene 0 / id 前缀一致 0 / 509 条逐字拼音配对通过；cross_dup=5（既有口径允许，tip 各不同） |
| 2 | 4 处计数同步点 | ✅ 全部一致于 v13/509 | WordBank.kt:4（v13·14 分区·509）；README.md:36（14 分区 509 = 13 场景 493 + 常用词 16）；StudyRepository.kt:543（n ≤ 509）；component-guidelines.md:96（13 个场景分区）+:113（n ≤ 509） |
| 3 | 括号/圆括号配平 | ✅ 12 个 kt 全配平 | `check_precmt_sweep.py`（剥注释/字符串后计 {}/()） |
| 4 | 未用 import | ✅ 无真实未用 | AppRoot/Common 报的 getValue/setValue 为 `by` 委托隐式使用（AppRoot 17 处、Common 2 处 `by remember`），属脚本误报 |
| 5 | 旧名残留 | ✅ 0 处 | `com.qyj.shibang` / `Shibang` / `Mando` 在 android-app 全树 + readme.md 0 命中 |
| 6 | gradle 身份 | ✅ 三处齐全 | namespace + applicationId = `com.knowmo.app`（build.gradle.kts:8,12）；rootProject.name = `KnowMo`（settings.gradle.kts:16）；strings.xml app_name = `认识么` |
| 7 | AppSettings 口径 | ✅ 仍绿 | `check_appsettings_load.py` 复跑：static_fails=0 case_fails=0 manageable=14 |
| 8 | pypinyin 差异 | ✅ 10 处全在既有约定内 | 5 处轻声无调号（fu/fu/tun/xi/shang）+ 3 处本调（yī/bù 系的 tiáo×2）+ 2 处词义辨读（gān 干拌）；比 v12 多的 1 处 = v13 新收 `馄饨`（轻声），无新增问题 |
| 9 | 旧计数残留 | ✅ 0 处 | 368/401/417/431/480、「12 个场景」在活代码+readme 全 0 命中 |

脚本落盘：`research/review/check_precmt_sweep.py`（可复跑）→ `_precmt_out.txt`；词库报告 `research/vocab/_wordbank_invariants.md`。

---

## P2（文档漂移，均为已知未决项，不阻塞提交，等 QYJ 裁）

1. **android-app/README.md 三处与 v6+ 实装矛盾**（v12 质检已登记）：
   - `:43` 仍列 v7 已删除的 `Sheets.kt`；
   - `:52` 频道清单未含「家电 / 常用词 / 收藏」；
   - `:56`「无设置页、无生词本」与 v6 实装的设置页冲突。
2. **超纲字 4 个**（烊/饨/馄/驿）：信息性。三者都是环境真实文字（打烊告示 / 馄饨招牌 / 驿站牌），符合「以文字形式出现在老人生活环境」的收录判据，建议不处理。
3. **6 个历史研究脚本硬编码路径指向 `D:\QYJ\MyProject\KnowMo`**：根目录改名尚未执行（IDE 占用），这些脚本此刻跑不了属预期；根目录改名完成后自动恢复。不是缺陷，是改名轮的既定安排。

---

## 验证边界（必读）

- 本机无 JDK / Android SDK，**未编译、未运行**。以上全部为脚本静态核查 + 数据全量校验；「可编译」只能由实机构建证明。
- 实机构建仍是强制评审门：implement.md v6 步骤 9 + v7 步骤 7 + v8 步骤 5 一并走查。
- applicationId 已从 com.qyj.shibang 变为 com.knowmo.app：真机上会**与旧应用共存**（属新应用），首次启动后旧数据不迁移——验收时别拿旧图标找新状态。

---

## 建议的提交动作（顺序）

1. `git add -A`（覆盖：新源码树 12 文件、新 spec、旧树删除、原型删除）→ `git status` 复核新树变 `A`。
2. 提交节奏由 QYJ 定；若想拆分，最自然的切线是「改名（rename + 删原型）」与「v6–v13 功能」两个提交——但工作区里两批改动已混在同一批文件上（如 WordBank.kt 既是 v13 又是改名产物），拆分需要 `git add -p` 逐块挑，成本不低，**建议一个提交完事**，提交信息里分节写明。
3. 提交前 IDE 里对 WordBank.kt 等 12 个 kt 做 reload 确认（v13 轮有缓冲区反向覆盖的前科）。
