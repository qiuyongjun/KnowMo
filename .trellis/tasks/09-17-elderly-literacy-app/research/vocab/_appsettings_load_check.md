# AppSettings `load()` 口径校验（v12）

> 自动生成，脚本 `check_appsettings_load.py`（可复跑）。源：`AppSettings.kt` + `StudyData.kt`
> 本机无 JDK / Android SDK，**未编译**；本文件与实机走查共同构成验证链。

## A. 静态断言（代码里真有这条规则）

| # | 断言 | 结果 |
|---|---|---|
| 1 | load() 记住了 order 数组的存在性 | ✅ |
| 2 | 新增分区归入 hidden 的判定（且带 order 存在性守卫） | ✅ |
| 3 | 未知 id 过滤仍在（known = stored ∩ manageableIds） | ✅ |
| 4 | order 仍是「已知项 + SCENES 顺序补尾」 | ✅ |
| 5 | persist() 仍写完整 order | ✅ |
| 6 | 判定位于 hidden.clear() 之后（上移即静默失效） | ✅ |

可管理分区 14 个（SCENES 15 项，去掉 `rec`；`fav` 不在 SCENES）：`daily`, `market`, `transit`, `hospital`, `bank`, `gov`, `food`, `phone`, `medicine`, `express`, `property`, `appliance`, `emergency`, `weather`

## B. 语义模拟（新旧口径对照）

「旧口径」= 改动前：`hidden` 只按存储数组重填，新分区因此**可见**。
「新口径」= 本轮：不在存储 `order` 里的分区额外归入 `hidden`。

| 场景 | 期望可见 | 旧口径可见 | 新口径可见 | 差异 | 判定 |
|---|---|---|---|---|---|
| S1 新装机：无 settings 键 | （空） | （空） | （空） | 无 | ✅ |
| S2 升级自 v6–v8（存储里既无 daily 也无 appliance） | （空） | daily、appliance | （空） | **新增隐藏：daily、appliance** | ✅ |
| S3 升级自 v9–v11（有 daily、无 appliance；用户开了 market） | market | market、appliance | market | **新增隐藏：appliance** | ✅ |
| S4 已装机且用户显式开启了 appliance | appliance | appliance | appliance | 无 | ✅ |
| S5 用户把 14 个分区全开 | daily、market、transit、hospital、bank、gov、food、phone、medicine、express、property、appliance、emergency、weather | daily、market、transit、hospital、bank、gov、food、phone、medicine、express、property、appliance、emergency、weather | daily、market、transit、hospital、bank、gov、food、phone、medicine、express、property、appliance、emergency、weather | 无 | ✅ |
| S6 order 键缺失但 hidden 存在（畸形 JSON，防御分支） | daily、transit、hospital、bank、gov、food、phone、medicine、express、property、appliance、emergency、weather | daily、transit、hospital、bank、gov、food、phone、medicine、express、property、appliance、emergency、weather | daily、transit、hospital、bank、gov、food、phone、medicine、express、property、appliance、emergency、weather | 无 | ✅ |
| S7 存储里混入未知 id（noodle 已撤销 / bogus 从没存在） | market | market、daily、transit、hospital、bank、gov、food、phone、medicine、express、property、appliance、emergency、weather | market | **新增隐藏：daily、transit、hospital、bank、gov、food、phone、medicine、express、property、appliance、emergency、weather** | ✅ |
| S8 order 类型不对（字符串而非数组） | daily、market、transit、hospital、bank、gov、food、phone、medicine、express、property、appliance、emergency、weather | daily、market、transit、hospital、bank、gov、food、phone、medicine、express、property、appliance、emergency、weather | daily、market、transit、hospital、bank、gov、food、phone、medicine、express、property、appliance、emergency、weather | 无 | ✅ |

### 每个场景要证明什么

- **S1 新装机：无 settings 键** —— 频道栏只有推荐 + 收藏
- **S2 升级自 v6–v8（存储里既无 daily 也无 appliance）** —— daily 与 appliance 都该隐藏 —— daily 这条是新口径顺带修的旧缺陷
- **S3 升级自 v9–v11（有 daily、无 appliance；用户开了 market）** —— 用户的 market 选择要保住，appliance 不得自己冒出来
- **S4 已装机且用户显式开启了 appliance** —— 用户显式选择优先，新口径不得把它打回隐藏
- **S5 用户把 14 个分区全开** —— 没人被误隐藏
- **S6 order 键缺失但 hidden 存在（畸形 JSON，防御分支）** —— order 缺失 → 不做推断，保持原行为（新旧口径必须完全一致）
- **S7 存储里混入未知 id（noodle 已撤销 / bogus 从没存在）** —— 未知 id 一律忽略，不崩、不影响判定
- **S8 order 类型不对（字符串而非数组）** —— Kotlin optJSONArray 对非数组返回 null → 同 S6 走防御分支；此时 hidden 保持存储值（空）⇒ 全部分区可见。**这是改动前就有的既有行为，本轮未改**；且 persist() 必写数组，实际不可达

## C. 结论

**A 段 6 条静态断言全过；B 段 8 个场景全部符合期望**。

- S2/S3/S7 的「差异」列非空 ⇒ 本改动**确实起了作用**（旧口径会把新分区放进频道栏）；
- S4/S5 新旧一致 ⇒ 用户显式选择不被覆盖；
- S6/S8 新旧一致 ⇒ `order` 缺失时的防御分支没被越界推断。

### 关于 `if (storedOrder != null)` 这个守卫

它是**纯防御**，生产路径不可达 —— `persist()` 必定写 `order` 且为数组，
而全仓只有 `persist()` 一处写 `settings` 键（`StudyRepository` 写的是另一个 prefs 文件
`study_state`）。所以「`order` 为 null 但 `hidden` 有值」的数据只能来自 adb 手改 / 备份还原 /
外部写坏。保留它是有意的：宁可对畸形数据退化成旧行为，也不要拿用户已有的显隐选择去做推断。

### 本脚本的已知局限

B 段的 `expect` 是人工常量，对**模型**是硬断言；对 **Kotlin 真身**的桥接只有 A 段正则。
若有人同时改模型与 `expect`，脚本仍会绿 —— 这是「规格 vs 实现」双向检查的固有局限，
唯一的缓解是 A 段正则钉在 Kotlin 真身上。**实机验证不可省。**

剩余风险来自**未编译**：Kotlin 侧的语法/类型正确性只能由实机或 CI 构建确认。
