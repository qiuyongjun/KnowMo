package com.knowmo.app.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

/**
 * 自定义词库（v22，QYJ 2026-09-22 拍板「纯 B：仅 SAF 导入」）：
 * 一个 JSON 文件 = 一个分区词库，设置页「我的词库」导入后成为频道栏的一个**普通场景分区**——
 * 显隐、排序、分区毕业判定、推荐聚合全部复用内置分区的既有机制（见 [allScenes] / [STUDY_TERMS]）。
 *
 * ## 文件格式（词条与内置 `Term` 同构）
 * ```json
 * {"id": "noodle", "name": "面馆", "icon": "🍜",
 *  "terms": [{"id": "noodle-1", "text": "牛肉面", "pinyin": "niú ròu miàn", "tip": "最常见的一碗面"}]}
 * ```
 * `icon` 可选（缺省「📚」，1~2 字符）；其余字段全部必填。
 *
 * ## fail-closed 校验（[parse] 任一条不合格 → 整库拒绝，逐条报告问题；已装库不受影响）
 * - 库 id：`^[a-z][a-z0-9_]{1,15}$`；**不得与内置在用分区 / 频道 id 冲突**（rec/fav/daily +
 *   [SCENES] 全部在用 id）。⚠️ **已退役分区 id 允许复用**——这是有意的迁移通道：
 *   v22 面馆导出文件就用 `food`（老设备上 `food-*` 的学习状态与显隐选择原样保留，导入后
 *   进度、收藏、频道开关自动恢复）。代价：将来若内置重新启用同名分区，会与已装自定义库冲突
 *   （导入时拒绝，需先删旧库）；
 * - 与**已装自定义库同 id 重导 = 替换更新**（家族自己迭代词库的方式；term id 稳定则进度保留）；
 * - 库名 1~8 字（频道 chip 适老宽度）；单库 1~200 条；自定义库总数 ≤ [MAX_BANKS]（频道栏可读性）；
 * - 词条 id 必须 `<库id>-` 前缀、全库唯一；词文本 1~8 字、库内不重复、**不与内置词库及
 *   其他自定义库重复**（「一个词只留一份」契约，v17 口径）；
 * - 拼音空格逐字配对（与内置 `term()` 的启动 require 同一条件）；tip 1~30 字。
 *
 * ## 与学习状态的关系（对设计提案的有意偏离，先读再改）
 * **删除词库只删文件与注册表条目，TermState / 收藏一律保留**——与「id 永不复用」契约同源：
 * 残留鬼 id 已被统计口径容忍（`studyStats` 按 [STUDY_TERMS] 剔除），重导同 id 的 JSON
 * 学习进度自动恢复。不做「删库连带删记录」级联，少一套破坏性写入路径。
 *
 * ## 装载时序（改 MainActivity 前必读）
 * [load] 在 `MainActivity.onCreate` **先于** `setContent` 执行——AppSettings / StudyRepository
 * 虽是 lazy，但都在组合期间首次访问，届时注册表已就绪，`allScenes()` 才能包含自定义分区。
 */
data class CustomBank(
    val id: String,
    val name: String,
    val icon: String,
    val terms: List<Term>,
) {
    /** 自定义分区的 Scene（颜色按库 id 从调色板稳定取色，跨会话不变） */
    val scene: Scene get() = Scene(id, name, icon, CustomBanks.colorFor(id))
}

object CustomBanks {

    private val banks = linkedMapOf<String, CustomBank>()
    private var dir: File? = null

    /** 自定义分区调色板（与内置分区底色同语言的浅色系；按库 id 稳定取色） */
    private val PALETTE = listOf(
        Color(0xFFE8F5E9), Color(0xFFFFF8E1), Color(0xFFE0F2F1),
        Color(0xFFF3E5F5), Color(0xFFE1F5FE),
    )

    /** 自定义库数量上限：频道栏与设置页列表的可读性约束（适老化，Tab 不能无限加） */
    const val MAX_BANKS = 5

    /** 单库词条上限 */
    const val MAX_TERMS = 200

    private const val DIR_NAME = "custom_banks"

    /** 已装自定义库（装载序） */
    val all: List<CustomBank> get() = banks.values.toList()

    /** 自定义分区集合（[allScenes] 拼装用） */
    val scenes: List<Scene> get() = banks.values.map { it.scene }

    /** 自定义词条集合（[STUDY_TERMS] getter 拼装用） */
    val terms: List<Term> get() = banks.values.flatMap { it.terms }

    fun find(id: String): CustomBank? = banks[id]

    fun colorFor(id: String): Color = PALETTE[abs(id.hashCode()) % PALETTE.size]

    /** App 启动装载（MainActivity.onCreate 调用）。单个文件损坏只跳过该库，不崩启动（fail-closed 到「少一个库」而非闪退）。 */
    fun load(context: Context) {
        val d = File(context.applicationContext.filesDir, DIR_NAME)
        dir = d
        banks.clear()
        if (!d.isDirectory) return
        d.listFiles { f -> f.isFile && f.extension == "json" }
            ?.sortedBy { it.name }
            ?.forEach { f ->
                runCatching { parse(f.readText()) }
                    .onSuccess { banks[it.id] = it }
            }
    }

    /**
     * 从设置页 SAF 导入：[parse] 校验 → 写入 `filesDir/custom_banks/<id>.json` → 进注册表。
     * 先写文件成功再进注册表（写失败时内存态不得领先磁盘态）；任一步失败抛
     * IllegalArgumentException（消息可直接展示给导入者），已装库不受影响。
     */
    fun import(context: Context, text: String): CustomBank {
        val bank = parse(text)
        val d = dir ?: File(context.applicationContext.filesDir, DIR_NAME).also { dir = it }
        d.mkdirs()
        File(d, bank.id + ".json").writeText(toJson(bank), Charsets.UTF_8)
        banks[bank.id] = bank
        return bank
    }

    /**
     * 删除词库：移除文件与注册表条目。**刻意不删** TermState / 收藏（见类 KDoc「与学习状态的关系」）——
     * 重导同 id 的 JSON 学习进度自动恢复。
     */
    fun delete(id: String) {
        banks.remove(id)
        dir?.let { d -> File(d, "$id.json").delete() }
    }

    /**
     * 校验并解析一份自定义词库 JSON。任何问题抛 IllegalArgumentException，消息为**逐条问题清单**
     * （中文、可直接展示；导入者按提示改文件重导）。
     */
    fun parse(text: String): CustomBank {
        val obj = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("不是有效的 JSON 文件（${e.message}）")
        }

        val errs = mutableListOf<String>()

        val id = obj.optString("id").trim()
        if (!Regex("^[a-z][a-z0-9_]{1,15}$").matches(id)) {
            errs.add("库 id「$id」不合法：小写字母开头，2~16 位小写字母/数字/下划线")
        } else if (id in forbiddenBankIds()) {
            errs.add("库 id「$id」与内置分区或固定频道冲突，请换一个（已退役分区的 id 允许复用）")
        }

        val name = obj.optString("name").trim()
        if (name.isEmpty() || name.length > 8) errs.add("库名「$name」须为 1~8 个字（频道按钮宽度有限）")

        val icon = obj.optString("icon").trim().ifEmpty { "📚" }
        if (icon.length > 2) errs.add("图标「$icon」最长 2 个字符（可省略，缺省「📚」）")

        if (!banks.containsKey(id) && banks.size >= MAX_BANKS) {
            errs.add("自定义词库最多 $MAX_BANKS 个（已装 ${banks.size} 个），请先删除不用的再导入")
        }

        val arr = obj.optJSONArray("terms")
        if (arr == null || arr.length() == 0) {
            errs.add("terms 缺失或为空：至少要有 1 条词")
        } else if (arr.length() > MAX_TERMS) {
            errs.add("词条 ${arr.length()} 条超过单库上限 $MAX_TERMS")
        }

        val parsed = mutableListOf<Term>()
        val seenIds = mutableSetOf<String>()
        val seenTexts = mutableSetOf<String>()
        // 「一个词只留一份」（v17 契约）：与内置词库或其他自定义库重复的词整库拒绝
        val existingTexts = BUILTIN_TERMS.map { it.text }.toHashSet() +
            banks.values.filter { it.id != id }.flatMap { bank -> bank.terms.map { it.text } }.toSet()

        if (arr != null) {
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i)
                if (t == null) {
                    errs.add("terms[$i] 不是对象")
                    continue
                }
                val tid = t.optString("id").trim()
                val text2 = t.optString("text").trim()
                val pinyin = t.optString("pinyin").trim()
                val tip = t.optString("tip").trim()
                val label = "terms[$i]「$text2」"

                if (termIdRegex == null || !termIdRegex.matches(tid)) {
                    errs.add("$label 词条 id「$tid」不合法：须为「$id-」开头的 1~16 位字母/数字/下划线")
                } else if (!seenIds.add(tid)) {
                    errs.add("$label 词条 id「$tid」在库内重复")
                }
                if (text2.isEmpty() || text2.length > 8) errs.add("$label 词文本须为 1~8 个字")
                else if (!seenTexts.add(text2)) errs.add("$label 与本库其他词条重复")
                else if (text2 in existingTexts) errs.add("$label 与词库已有词重复——一个词只学一份")

                val syllables = pinyin.split(" ")
                if (pinyin.isEmpty() || syllables.any { it.isEmpty() }) {
                    errs.add("$label 拼音为空或含连续空格")
                } else if (syllables.size != text2.length) {
                    errs.add("$label 拼音音节数（${syllables.size}）与字数（${text2.length}）不符")
                }
                if (tip.isEmpty() || tip.length > 30) errs.add("$label 用途说明须为 1~30 个字")

                // 仅在配对成立时构造（配对不符已报错，最终必抛；此处守卫防 syllables 越界抢在报错前崩掉）
                if (tid.isNotEmpty() && text2.isNotEmpty() && tip.isNotEmpty() &&
                    syllables.size == text2.length && syllables.all { it.isNotEmpty() }
                ) {
                    parsed.add(
                        Term(
                            id = tid, scene = id, icon = icon, text = text2,
                            chars = List(text2.length) { k -> TermChar(text2[k].toString(), syllables[k]) },
                            tip = tip,
                        ),
                    )
                }
            }
        }

        if (errs.isNotEmpty()) {
            val shown = errs.take(6).joinToString("\n") { "· $it" }
            val more = if (errs.size > 6) "\n·……另有 ${errs.size - 6} 个问题" else ""
            throw IllegalArgumentException("词库未通过检查，未做任何改动：\n$shown$more")
        }
        return CustomBank(
            id = id,
            name = name,
            icon = icon,
            terms = parsed,
        )
    }

    /** 注册表 → 持久化 JSON（与 [parse] 读写对称；字段顺序稳定，便于人工比对） */
    private fun toJson(bank: CustomBank): String {
        val obj = JSONObject()
            .put("id", bank.id)
            .put("name", bank.name)
            .put("icon", bank.icon)
        val arr = org.json.JSONArray()
        bank.terms.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("text", t.text)
                    .put("pinyin", t.chars.joinToString(" ") { it.p })
                    .put("tip", t.tip),
            )
        }
        obj.put("terms", arr)
        return obj.toString()
    }

    /**
     * 自定义库 id 的禁区 = 固定频道（rec/fav/daily）+ 内置在用分区。
     * **刻意不含已退役分区 id**（迁移通道，见类 KDoc）；[SCENES] 是运行时求值，
     * 不把自定义库自己的 id 算进去（同 id 重导 = 替换更新）。
     */
    private fun forbiddenBankIds(): Set<String> =
        setOf(
            StudyRepository.CHANNEL_DAILY,
            StudyRepository.CHANNEL_FAV,
            StudyRepository.CHANNEL_COMMON,
        ) + SCENES.map { it.id }.toSet()
}
