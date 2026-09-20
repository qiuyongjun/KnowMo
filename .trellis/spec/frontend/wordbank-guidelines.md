# 词库内容契约（WordBank Guidelines）

> `android-app/.../data/WordBank.kt` 里每一条 `term(...)` 都是 App 的**学习单元**，
> 直接决定老人看到什么字。这份文档是**增删词条前必读**的执行契约。
> 建立于 2026-09-20（v10「口水话清理」+ v11 面馆补充两轮实践之后）。

---

## 1. Scope / Trigger

**必须读本文件的情形**：

- 往任何分区增删词条
- 新增 / 删除场景分区
- 「清理」类批量改动（v10 删了 52 条纯口语词条）

**适用文件**：`android-app/app/src/main/java/com/knowmo/app/data/WordBank.kt`（词条）、
同目录 `StudyData.kt`（分区定义 `SCENES`）。

---

## 2. Data Contract

```kotlin
private fun term(id: String, scene: String, text: String, pinyin: String, tip: String): Term {
    val py = pinyin.split(" ")
    require(text.length == py.size) { "词条 $id 拼音与字数不符: $text / $pinyin" }
    return Term(id, scene, ICON.getValue(scene), text,
        List(text.length) { i -> TermChar(text[i].toString(), py[i]) }, tip)
}
```

| 字段 | 约束 | 违反后果 |
|---|---|---|
| `id` | `<scene>-<序号>`，**id 前缀必须等于 scene**；**一经发布永不复用** | 前缀不符 → 命名混乱；复用 id → 顶掉已学记录（`TermState` 按 id 挂靠） |
| `scene` | 必须在 `SCENES` 中存在 | `ICON.getValue(scene)` 在**类初始化**抛 `NoSuchElementException`，**App 一启动就崩** |
| `text` | 学习单元本体；`text.length` 用 UTF-16 code unit 数（即汉字个数） | 与 `pinyin` 音节数不符 → 启动即 `require` 失败 |
| `pinyin` | 空格逐字配对，**字数必须等于 `text.length`**；**无调号 = 轻声** | 同上 |
| `tip` | 朗读给老人听的用途说明，口语、一句话说清「在哪见、什么意思」 | — |

**拼音标音口径**（教学用，不是语流变调）：

- 写**本调**：`不要辣 bù yào là`、`不要乱动 bù yào luàn dòng`、`一次性筷子 yī cì xìng kuài zi`
  （pypinyin 会给变调 `bú`/`yí`，**不采用**）
- 词义辨读**查词典**，不信 pypinyin 默认音：`担担面 dàn dàn miàn`（挑担叫卖，非 dān）、
  `干拌 gān bàn`（干＝乾）、`抹布 mā bù`（非 mǒ）
- 真读轻声才不写调号：`豆腐 dòu fu`、`晚上 wǎn shang`、`筷子 kuài zi`

---

## 3. 准入判据（核心契约）

> **这个词，老人生活环境里是否会以文字形式出现**
> （招牌 / 价签 / 菜单 / 告示 / 屏幕 / 包装 / **电器面板 / 铭牌 / 贴纸 / 说明书**）。

| 判定 | 处置 | 例 |
|---|---|---|
| **会以文字出现** | **收** | `营业中`、`价目表`、`卫生许可证`、`出餐`、`打包盒`、`味精`、`围裙`、`一次性筷子`；电器侧 `当心触电`、`能效标识`、`冷藏室` |
| **只在口头交流出现** | **不收** | `不要香菜`、`少放辣椒`、`多放醋`、`清淡点`、`少面`、`加汤` |

> 「文字出现的载体」是一张开放清单：v11 面馆那轮补了「调料罐 / 价目表 / 墙面证照 / 后厨物件」，
> v12 家电那轮补了「电器面板 / 铭牌 / 贴纸 / 屏幕 / 说明书」。**新载体出现时往上面那句括注里加，别另立判据。**

**为什么**：App 的产出是「认出看到的字」。只用来**说**的词，她不看这些字也能干活，收进来只是灌水。

**例外口径（外卖备注）**：外卖备注确实以文字印在小票上，但备注内容都是围绕食物 / 调料的说法，
而**名词已在库中**（收了 `香菜` 就不必再收 `不要香菜`）→ **名词收，整句不收**。

**边界案例**（同一条轴上的两侧，别搞反）：

| 词 | 判 | 理由 |
|---|---|---|
| `加面` | **收** | 价目表上单列加收，是字 |
| `加汤` / `少面` | 不收 | 纯口说 |
| `抹布` | **收** | 依据最弱的一类——布本身不带字，仅超市货架 / 包装可见；**下次清理别当口说词删掉** |
| `后厨` | 不收（v10 已删） | 客人进不去，牌子也少见 |

---

## 4. Validation & Error Matrix

| 违规 | 触发时机 | 表现 | 拦截手段 |
|---|---|---|---|
| `text` 字数 ≠ `pinyin` 音节数 | **类初始化 / App 启动** | `IllegalArgumentException: 词条 xxx 拼音与字数不符` | `check_wordbank_invariants.py` 第 2 项 |
| 词条的 `scene` 不在 `SCENES` | **类初始化 / App 启动** | `NoSuchElementException` | 同脚本第 4 项（**头号坑**） |
| 同一分区内两条同文本词条 | 运行期（学习单元重复） | 同一个词被当成两个单元各学一遍 | 同脚本第 6 项 |
| 跨分区同文本词条 | 无（**允许**） | 同一个词在两个场景各学一遍，可接受 | 同脚本第 8 项（信息性） |
| `SCENES` 里有分区但无词条 | 运行期（频道空池） | 点进去空白 | 同脚本第 5 项 |
| id 重号 | 运行期 | 学习状态互相顶掉 | 同脚本第 3 项 |

---

## 5. Good / Base / Bad Cases

- **Good**：`term("food-93", "food", "营业中", "yíng yè zhōng", "灯牌亮着这几个字，就是还在卖")`
  —— 前缀==scene、配对、带调号、tip 口语且点明「在哪见」。
- **Base**：`term("food-66", "food", "盐", "yán", "盐罐上的字，白白的细颗粒")`
  —— **单字词条是允许的**（`daily` 分区还有 `人/大/小/上/下/水/火/门/钱/好`），
  取自罐子上印的字，配对 1/1。
- **Bad**：`term("food-104", "food", "不要香菜", "bù yào xiāng cài", "碗里别放香菜")`
  —— 判据不符（口说整句、且 `food-78 香菜` 已在库）；`term("noodle-1", "noodle", ...)`
  —— `noodle` 不在 `SCENES`，**启动即崩**。

---

## 6. Tests Required

改完词库**必须**复跑：

```bash
cd .trellis/tasks/09-17-elderly-literacy-app/research/vocab
"C:/Users/85109/.workbuddy/binaries/python/envs/default/Scripts/python.exe" check_wordbank_invariants.py
```

断言点（期望全绿）：

| # | 断言 | 期望 |
|---|---|---|
| 1 | 总词条数 == Σ各分区 | 一致 |
| 2 | `text.length` == 拼音音节数 | 全部通过 |
| 3 | 全库 id 唯一 | 无重复 |
| 4 | 每条 `scene` ∈ `SCENES` | **孤儿 = 0**（否则启动崩溃） |
| 5 | 每个非 `rec` 分区非空 | 无空分区 |
| 6 | 分区内文本不重复 | 无重复 |
| 7 | id 前缀 == scene | 全部一致 |

本机**无 JDK / Android SDK，不能编译**，以上是唯一的自动化防线。

---

## 7. Wrong vs Correct

#### Wrong —— 新增分区只加词、忘了加 Scene

```kotlin
// WordBank.kt
private val NOODLE = listOf(term("noodle-1", "noodle", "炸酱面", "zhá jiàng miàn", "..."))
// 但 SCENES 里没有 Scene("noodle", ...)
```

**后果**：`ICON.getValue("noodle")` 在类初始化抛 `NoSuchElementException`，**App 一打开就崩**，
而且编译能过、IDE 不报错——只有启动才炸。

#### Correct —— 四处同步

```kotlin
// ① StudyData.kt 的 SCENES
Scene("noodle", "面馆", "🥢", Color(0xFFF9FBE7)),

// ② WordBank.kt 加列表
private val NOODLE = listOf(term("noodle-1", "noodle", "炸酱面", "zhá jiàng miàn", "..."))

// ③ WordBank.kt 的 STUDY_TERMS 并入
val STUDY_TERMS: List<Term> =
    MARKET + TRANSIT + HOSPITAL + BANK + GOV + FOOD + NOODLE +
        PHONE + MEDICINE + EXPRESS + PROPERTY + EMERGENCY + WEATHER

// ④（**知情项，不是改动项**）AppSettings.kt —— **新分区默认隐藏**（v12 口径，design.md §15）：
//     不在存储 JSON 的 `order` 里的分区会被自动归入 hidden。**无需写任何代码**，新分区自动生效；
//     列在这里是为了让「新分区默认不出现在频道栏」成为**预期行为而不是 bug** ——
//     想让它出现，得进设置页（推荐 tab 连点 5 次）手动开启。
```

---

## 8. 附：id 空号是正常现象，不要回填

删除词条会留下**永不复用的空号**（id 一经发布永不复用）。当前快照：

| 分区 | 空号 |
|---|---|
| `food` | `3, 7, 9, 17, 25, 31, 32, 50, 55, 59` |
| `daily` | `1, 2, 3, 9, 10`（v15 删启蒙单字：人/大/小/钱/好） |

> 这里只列了 `food` 一个例子——2026-09-20 v10「口水话清理」在 8 个分区共删了 52 条，**各分区都有空号**。
> 别去枚举空号，按「该分区当前最大 id + 1」决定新号即可。

**不要为了让编号连续而回填这些 id**——真机上若有用户学过被删的词，回填会让新词继承旧的学习状态。
新增一律从**该分区当前最大 id + 1** 开始。

---

## 9. 计数口径同步点

词条总数 / 分区数变化时，这些地方要一起改（漏一处就是文档漂移）：

1. `WordBank.kt` 顶部 KDoc 首行（`词库 vNN：NN 场景 NNN 词条（场景 + 常用词）`）
2. `android-app/README.md` 目录结构里 `WordBank.kt` / `StudyData.kt` 两行
3. `StudyRepository.kt` 加权抽卡注释里的 `n ≤ NNN`
4. `.trellis/spec/frontend/component-guidelines.md` 池型契约里的**场景分区数**与 `n ≤ NNN`

> 第 4 条是 v12（家电分区，2026-09-20）那轮**漏改后补列**的——原清单只有 3 条，
> 所以 component-guidelines.md 的两处数字漂了一个版本（12 → 13 个场景分区、368 → 480）才被质检抓到。
> 加它就是为了让下次不用靠人眼补。

**两个数别混**：

- **场景分区数** = `SCENES` 去掉 `rec` 与 `daily` 的个数（v12 起 = **13**）；
- **词条数** = 全库 `term(...)` 条数（v15 起 = **549**；其中场景词 530 + 常用词 19）。

