package com.knowmo.app.data

import androidx.compose.ui.graphics.Color

/** 单字 + 拼音 */
data class TermChar(val c: String, val p: String)

/**
 * 学习单元 = 生活词组（不是单字）。
 * kind/days 不在此存储：新学/复习/温故由 StudyRepository 调度器按
 * TermState（间隔天数 + 上次学习日期 + 遗忘史，v5 R11 首答定调度：当日首答「认识」即写本状态）在运行时计算。
 * 词条内容见 WordBank.kt。
 */
data class Term(
    val id: String,
    val scene: String,
    val icon: String,
    val text: String,
    val chars: List<TermChar>,
    val tip: String,
)

data class Scene(val id: String, val name: String, val icon: String, val color: Color)

/**
 * 场景分区定义。**当前 = 6 项**（推荐 + 常用词 + 4 个场景分区）。
 *
 * ## 分区退役史（删除的分区 id **永久退役**）
 * 词条仍可能带这些 id 前缀（id 是"首次归属"，永不改，见 WordBank.kt 文件头 id 契约），
 * 但**不能再有 `Scene` 项**。`AppSettings.load()` 靠 `MERGED_INTO` 表把「当初被用户打开的
 * 已退役分区」的可见性迁移到接收分区。
 *
 * - **v17**（可管理分区 13 → 10）：删 `bank`（并入 `gov`）、`medicine`（并入 `hospital`）、
 *   `emergency`（内容分流到 phone / hospital / property / gov）。
 * - **v18**（可管理分区 10 → 5）：整区移除 `market` / `gov` / `express` / `property` / `weather`。
 *   其中 `weather` 24 条全部归入 `daily`；`gov` 与 `express` 只留了 7 条例外（身份证 + 快递 6 条）
 *   归入 `daily`；**`market` / `property` 的词条全部删除**（含 v17 刚从 `emergency` 迁入
 *   `property` 的 4 条安全类词 —— QYJ 2026-09-21 明确选择一起删）。
 *   ⇒ 词库 550 → **414** 条。
 * - **v22**（可管理分区 5 → 4）：整区删除 `food` 88 条，**词条全删、不并入任何分区**——面馆是
 *   QYJ 妈妈的私有定制，不进开源仓库（已导出标准 JSON，私有设备经自定义词库导入恢复；
 *   库 id 复用 `food`，学习进度与显隐原样保留）。存储 JSON 残留的 `food` id 由
 *   `manageableIds()` 自然过滤，无需显隐迁移项。⇒ 词库 452 → **364** 条。
 */
val SCENES = listOf(
    // 推荐：聚合频道，不是分区——不参与「分区学完」判定（graduatedScenes 显式排除），
    // 也没有自己的词条（词来自「可见分区 + 常用词」，见 StudyRepository.scopeIds）。
    Scene("rec", "推荐", "⭐", Color(0xFFEDE7F6)),
    // 常用词（v9 设立；v17 升级为固定频道）：日常生活高频字词 + **跨场景通用词**，
    // **不是固定某个场景的内容**。在 SCENES 里（参与分区毕业判定），但**不进 AppSettings
    // 显隐管理**（order/hidden 均不含 daily ⇒ 不可开关、不可移除）。
    // 两重身份：① 推荐频道的基础内容源（StudyRepository 的 CHANNEL_COMMON 特例，恒入推荐范围）；
    // ② 频道栏的第三个固定频道（恒显示，渲染在 ui/Common.kt 的 ChannelBar，不经 visibleScenes）。
    // 规模沿革：v16 37 条 → v17 通用词回流 72 条 → **v18 收纳 weather 与 7 条例外词共 103 条**——
    // 它现在是全库最大的分区，也是「不设场景限制、什么都能放」的那个兜底分区。
    Scene("daily", "常用词", "🔤", Color(0xFFE0F7FA)),
    Scene("transit", "公交地铁", "🚌", Color(0xFFE3F2FD)),
    // v17：原「药品说明」分区并入本院区（用药是看病的下游）——名字保持「医院」不变，
    // 以维持 chip 宽度（适老化：4 字 chip 比 2 字显著占宽，见 Common.kt 的「学完」后缀注释）。
    Scene("hospital", "医院", "🏥", Color(0xFFFFEBEE)),
    Scene("phone", "手机微信", "📱", Color(0xFFE8EAF6)),
    Scene("appliance", "家电", "🔌", Color(0xFFF0F4C3)),
)

/**
 * v22：全部分区 = 内置 [SCENES] + 自定义词库分区（`CustomBanks`）。
 * AppSettings / StudyRepository / sceneColor / sceneName 一律读本函数——
 * 自定义分区由此获得与内置分区**完全同等**的待遇（显隐、排序、毕业判定、推荐聚合），
 * 调度与设置逻辑零改动（延续 v17/v18「分区集合来自 SCENES、本类无硬编码清单」的设计）。
 */
fun allScenes(): List<Scene> = SCENES + CustomBanks.scenes

fun sceneColor(id: String): Color = allScenes().firstOrNull { it.id == id }?.color ?: Color(0xFFF5F7FA)

/**
 * v6：收藏频道（`fav`）**不进 SCENES**——否则会被当成可调度场景参与分区毕业判定（design.md §11.1），
 * 频道播报名在此显式映射。
 */
fun sceneName(id: String): String =
    if (id == StudyRepository.CHANNEL_FAV) "收藏"
    else allScenes().firstOrNull { it.id == id }?.name ?: ""
