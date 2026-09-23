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
 * 场景分区定义。**当前 = 1 项**（仅推荐聚合频道——v23 起分区全部由自定义词库导入产生）。
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
 * - **v23**（词库全面外置）：`daily`（常用词）与 4 个场景分区**全部退役**——App 裸装零词库，
 *   原 364 条导出为仓库 `wordbanks/*.csv`（官方词库包，拼音全填），用户经设置页导入后以
 *   自定义分区存在（id 为 CSV 派生 id，与内置历史 id 不互通——内置词进度清零，QYJ 拍板接受）。
 *   固定频道只剩推荐 / 收藏；`CHANNEL_COMMON` 推荐特例保留但恒为空集（无害）。
 *   ⇒ 内置词库 364 → **0** 条，`SCENES` 只剩 `rec`。
 */
val SCENES = listOf(
    // 推荐：聚合频道，不是分区——不参与「分区学完」判定（graduatedScenes 显式排除），
    // 也没有自己的词条（词来自「可见分区」，见 StudyRepository.scopeIds）。
    // v23：唯一内置项——场景分区全部由自定义词库导入产生（零词库时 App 显示导入引导）。
    Scene("rec", "推荐", "⭐", Color(0xFFEDE7F6)),
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
