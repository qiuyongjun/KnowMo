# AppSettings `load()` 口径校验（v12 + 2026-09-20 daily 不可显隐细化）

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
| 7 | manageableIds 排除 CHANNEL_COMMON（daily 不进显隐管理） | ✅ |
| 8 | setSceneVisible 防御守卫含 CHANNEL_COMMON | ✅ |

可管理分区 13 个（SCENES 15 项，去掉 `rec` 与 `daily`；`fav` 不在 SCENES）：`market`, `transit`, `hospital`, `bank`, `gov`, `food`, `phone`, `medicine`, `express`, `property`, `appliance`, `emergency`, `weather`

`daily`（常用词）不在可管理集合 —— 不可开关、设置页无此行、频道栏永不出现，其词恒入推荐（CHANNEL_COMMON 特例）。

## B. 语义模拟（新旧口径对照）

「旧口径」= 改动前：`hidden` 只按存储数组重填，新分区因此**可见**。
「新口径」= 本轮：不在存储 `order` 里的分区额外归入 `hidden`；且 `daily` 被移出可管理集合，旧存储里的 `daily` 在 known/hidden 双重过滤时自然丢弃。

| 场景 | 期望可见 | 旧口径可见 | 新口径可见 | 差异 | 判定 |
|---|---|---|---|---|---|
| S1 新装机：无 settings 键 | （空） | （空） | （空） | 无 | ✅ |
| S2 升级自 v6–v8（存储里既无 daily 也无 appliance） | （空） | appliance | （空） | **新增隐藏：appliance** | ✅ |
| S3 升级自 v9–v13（用户开了 market；hidden 含已不管理的 daily） | market | market、appliance | market | **新增隐藏：appliance** | ✅ |
| S4 已装机且用户显式开启了 appliance | appliance | appliance | appliance | 无 | ✅ |
| S5 用户把可管理分区全开 | market、transit、hospital、bank、gov、food、phone、medicine、express、property、appliance、emergency、weather | market、transit、hospital、bank、gov、food、phone、medicine、express、property、appliance、emergency、weather | market、transit、hospital、bank、gov、food、phone、medicine、express、property、appliance、emergency、weather | 无 | ✅ |
| S6 order 键缺失但 hidden 存在（畸形 JSON，防御分支） | transit、hospital、bank、gov、food、phone、medicine、express、property、appliance、emergency、weather | transit、hospital、bank、gov、food、phone、medicine、express、property、appliance、emergency、weather | transit、hospital、bank、gov、food、phone、medicine、express、property、appliance、emergency、weather | 无 | ✅ |
| S7 存储里混入未知 id（noodle 已撤销 / bogus 从没存在） | market | market、transit、hospital、bank、gov、food、phone、medicine、express、property、appliance、emergency、weather | market | **新增隐藏：transit、hospital、bank、gov、food、phone、medicine、express、property、appliance、emergency、weather** | ✅ |
| S8 order 类型不对（字符串而非数组） | market、transit、hospital、bank、gov、food、phone、medicine、express、property、appliance、emergency、weather | market、transit、hospital、bank、gov、food、phone、medicine、express、property、appliance、emergency、weather | market、transit、hospital、bank、gov、food、phone、medicine、express、property、appliance、emergency、weather | 无 | ✅ |
| S9 老用户曾显式开启 daily（order 与 hidden 都含它，当时可见） | market | market、appliance | market | **新增隐藏：appliance** | ✅ |

### 每个场景要证明什么

- **S1 新装机：无 settings 键** —— 频道栏只有推荐 + 收藏
- **S2 升级自 v6–v8（存储里既无 daily 也无 appliance）** —— appliance 该隐藏（存储后新增）；daily 已被移出可管理集合，任何情况下都不会出现
- **S3 升级自 v9–v13（用户开了 market；hidden 含已不管理的 daily）** —— 用户的 market 选择要保住；存储里的 daily 被 manageableIds 过滤自然丢弃（不可显隐口径），频道栏不出现常用词
- **S4 已装机且用户显式开启了 appliance** —— 用户显式选择优先，新口径不得把它打回隐藏
- **S5 用户把可管理分区全开** —— 没人被误隐藏；daily 不在集合内，与全开无关
- **S6 order 键缺失但 hidden 存在（畸形 JSON，防御分支）** —— order 缺失 → 不做推断，保持原行为（新旧口径必须完全一致）
- **S7 存储里混入未知 id（noodle 已撤销 / bogus 从没存在）** —— 未知 id 一律忽略，不崩、不影响判定
- **S8 order 类型不对（字符串而非数组）** —— Kotlin optJSONArray 对非数组返回 null → 同 S6 走防御分支；此时 hidden 保持存储值（空）⇒ 全部分区可见。**这是改动前就有的既有行为，本轮未改**；且 persist() 必写数组，实际不可达
- **S9 老用户曾显式开启 daily（order 与 hidden 都含它，当时可见）** —— 2026-09-20 口径细化的关键升级路径：daily 不再被视作可管理 id——旧存储里它可见与否都无所谓，known/hidden 双重过滤自然丢弃，无需迁移。频道栏不再出现常用词，其词仍在推荐范围（CHANNEL_COMMON 特例）

## C. 结论

**A 段 8 条静态断言全过；B 段 9 个场景全部符合期望**。

- S2/S3/S7/S9 的「差异」列非空 ⇒ 本改动**确实起了作用**（旧口径会把新分区放进频道栏；S9 是 daily 丢弃路径）；
- S4/S5 新旧一致 ⇒ 用户显式选择不被覆盖；
- S6/S8 新旧一致 ⇒ `order` 缺失时的防御分支没被越界推断；
- S9：老用户曾显式开启过 `daily` → 升级后频道栏不再出现常用词频道（其词仍在推荐范围内），无需迁移代码。

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
