package com.knowmo.app.data

import android.content.Context
import android.icu.text.Transliterator
import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale
import kotlin.math.abs

/**
 * 自定义词库（v22→v23.1，QYJ 拍板）：**用户导入格式只有 CSV**——一个词库文件 = 一个分区词库，
 * 设置页「我的词库」导入后成为频道栏的一个**普通场景分区**，显隐、排序、分区毕业判定、推荐聚合
 * 全部复用内置分区的既有机制（见 [allScenes] / [STUDY_TERMS]）。
 * v23.1 起 JSON **不再作为导入格式**（QYJ：家属用 Excel 即可，JSON 多一层门槛）——JSON 退为
 * **App 内部存储格式**（[toJson] 写、[parse] 读，用户不可见）。
 *
 * ## 用户导入格式：CSV（用 Excel 填好「另存为 CSV」即可）
 * - **三列：词 / 拼音 / 用途**；首行表头可选（首格为「词 / 词语 / text」即识别为表头跳过）；
 * - **文件名即库名**（去扩展名，超 8 字自动截断）；库 id 由库名派生（[bankIdForName]）——
 *   ⚠️ **导入后别改文件名**，改名 = 新库 id = 旧进度失联（term id 挂在库 id 下）；
 * - 词条 id 自动生成且**可复算**：`库id + "-" + hash(词+拼音)`——改用途说明不变 id（进度保留），
 *   改词或改拼音 = 新词（本来就该重学），删除/插入行不影响其他词的 id；
 * - **拼音列可空** → [HAN_TO_PINYIN]（android ICU Han-Latin，API 24+）自动注音；填了以填的为准。
 *   ⚠️ 自动注音对**多音字**可能取错音（适老产品教错音是真伤害）——导入成功文案与文档都提示抽查；
 * - 编码自动探测：UTF-8 BOM → 严格 UTF-8 → GBK（兜住老版 Excel「另存 CSV」的默认编码）；
 * - 同名文件重导 = 同库 id = **替换更新**（家族自己迭代词库的方式）。
 *
 * ## fail-closed 校验（任一条不合格 → 整库拒绝、逐条报告、已装库不受影响）
 * - 库 id 由文件名派生（`c` + 库名 hashCode 的 8 位十六进制，跨设备可复算）；派生形态天然
 *   撞不上内置分区 id，`forbiddenBankIds` 检查仅作防御；
 * - 库名 1~8 字（频道 chip 适老宽度）；单库 1~[MAX_TERMS] 条；自定义库总数 ≤ [MAX_BANKS]（频道栏可读性）；
 * - 词条 id 全库唯一；词文本 1~8 字、库内不重复、**不与其他库已装词重复**
 *   （「一个词只留一份」契约，v17 口径；v23 起内置词库为空，此项只在自定义库之间生效）；
 * - 拼音空格逐字配对（自动注音产物同样过这道校验）；tip 1~30 字。
 * - 存储规范化 JSON（[toJson] 产物）在装载时走同一套 [parse] 校验兜底（防文件被手改坏）。
 *
 * ## 与学习状态的关系（对设计提案的有意偏离，先读再改）
 * **删除词库只删文件与注册表条目，TermState / 收藏一律保留**——与「id 永不复用」契约同源：
 * 残留鬼 id 已被统计口径容忍（`studyStats` 按 [STUDY_TERMS] 剔除），重导同 id 的文件
 * 学习进度自动恢复。不做「删库连带删记录」级联，少一套破坏性写入路径。
 *
 * ## 存储规范化
 * CSV 导入后**统一转成 JSON** 存 `filesDir/custom_banks/<id>.json`（[toJson]）——磁盘上只有
 * 一种权威格式，装载路径（[load]）不需要感知来源格式。
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

/** 一次导入的结果：`isNew` = 该库 id 首次入库（调用方据此做「首次导入默认显示」） */
data class ImportOutcome(val bank: CustomBank, val isNew: Boolean)

object CustomBanks {

    private val banks = linkedMapOf<String, CustomBank>()
    private var dir: File? = null

    /** 自定义分区调色板（与内置分区底色同语言的浅色系；按库 id 稳定取色） */
    private val PALETTE = listOf(
        Color(0xFFE8F5E9), Color(0xFFFFF8E1), Color(0xFFE0F2F1),
        Color(0xFFF3E5F5), Color(0xFFE1F5FE),
    )

    /** 汉字→带调拼音（android ICU，API 24+；minSdk 26 覆盖）。**多音字取常用音，可能错**——拼音列留空才启用。 */
    private val HAN_TO_PINYIN: Transliterator? by lazy {
        runCatching { Transliterator.getInstance("Han-Latin") }.getOrNull()
    }

    /** 自定义库数量上限：v23 起官方 5 库也走导入（官方 CSV + 家属自定义），频道栏可读性约束 */
    const val MAX_BANKS = 10

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
     * 从设置页 SAF 导入：**只收 CSV**（v23.1 起；非 .csv 扩展名直接拒绝并提示格式）→
     * 写入 `filesDir/custom_banks/<id>.json`（存储规范化，见类 KDoc）→ 进注册表。
     * **字节入口**（编码探测在 [decode] 做——Excel 老版另存的 CSV 是 GBK，
     * 调用方若先按 UTF-8 解码就救不回来了）。先写文件成功再进注册表（写失败时内存态不得领先
     * 磁盘态）；任一步失败抛 IllegalArgumentException（消息可直接展示），已装库不受影响。
     * 返回 [ImportOutcome]：`isNew` = 首次导入（AppRoot 借此「首次导入默认显示」——显式导入
     * = 明确想学；重导替换则**不动**用户手动改过的显隐）。
     */
    fun import(context: Context, bytes: ByteArray, fileName: String): ImportOutcome {
        if (!fileName.endsWith(".csv", ignoreCase = true)) {
            throw IllegalArgumentException("仅支持 CSV 词库文件：用 Excel 填三列（词 / 拼音 / 用途），另存为 CSV 即可")
        }
        val bank = parseCsv(decode(bytes), fileName)
        val d = dir ?: File(context.applicationContext.filesDir, DIR_NAME).also { dir = it }
        d.mkdirs()
        File(d, bank.id + ".json").writeText(toJson(bank), Charsets.UTF_8)
        val isNew = !banks.containsKey(bank.id)
        banks[bank.id] = bank
        return ImportOutcome(bank, isNew)
    }

    /**
     * 删除词库：移除文件与注册表条目。**刻意不删** TermState / 收藏（见类 KDoc「与学习状态的关系」）——
     * 重导同 id 的文件学习进度自动恢复。
     */
    fun delete(id: String) {
        banks.remove(id)
        dir?.let { d -> File(d, "$id.json").delete() }
    }

    /**
     * 内部装载器：解析 [load] 发现的规范化 JSON（[toJson] 产物，App 自己写自己读）。
     * **不作为用户导入格式**（v23.1 起用户导入只收 CSV）；校验与 CSV 同一套，兜住被手改坏的
     * 存储文件。任何问题抛 IllegalArgumentException，消息为逐条问题清单。
     */
    private fun parse(text: String): CustomBank {
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
            // 库 id 本身非法时不再按它派生正则（防正则注入），词条级 id 检查跳过（库级错误已致命）
            val termIdRegex = if (Regex("^[a-z][a-z0-9_]{1,15}$").matches(id)) Regex("^$id-[A-Za-z0-9_]{1,16}$") else null
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
                if (termIdRegex == null || termIdRegex.matches(tid)) {
                    checkAndBuildTerm(errs, seenIds, seenTexts, existingTexts, parsed, id, icon, label, tid, text2, pinyin, tip)
                } else {
                    errs.add("$label 词条 id「$tid」不合法：须为「$id-」开头的 1~16 位字母/数字/下划线")
                }
            }
        }

        abortIfErrors(errs)
        return CustomBank(
            id = id,
            name = name,
            icon = icon,
            terms = parsed,
        )
    }

    /**
     * 校验并解析一份 CSV 词库（v22.1）。格式约定见类 KDoc「文件格式二」：
     * 三列（词 / 拼音 / 用途）、文件名即库名、词条 id 由内容派生、拼音可空自动注音。
     */
    fun parseCsv(text: String, fileName: String): CustomBank {
        val errs = mutableListOf<String>()

        val base = fileName.substringBeforeLast('.').trim()
        if (base.isEmpty()) errs.add("文件名是空的：文件名会用作库名，请起个名字")
        // 超 8 字自动截断（文件名是随手起的，截断比报错友好；类 KDoc 已注明）
        val name = base.take(8)
        val id = bankIdForName(name)
        if (id in forbiddenBankIds()) errs.add("库名「$name」派生的库 id 与内置分区冲突，请改个文件名")
        // 2026-09-23 复审修复（#4）：库 id 派生自 32 位 hashCode，存在碰撞（如「Aa」与「BB」同值）
        // ——不同文件名撞出同 id 时，继续导入会按「重导替换」覆盖掉另一个库，且跨库重词检查
        // 对同 id 库不生效、防线不触发。fail-closed：要求改文件名。
        banks[id]?.let { existing ->
            if (existing.name != name) {
                errs.add("文件名「$name」与已装词库「${existing.name}」派生出相同的库 id（碰撞），请改个文件名")
            }
        }
        if (!banks.containsKey(id) && banks.size >= MAX_BANKS) {
            errs.add("自定义词库最多 $MAX_BANKS 个（已装 ${banks.size} 个），请先删除不用的再导入")
        }

        val dataRows = parseCsvRows(text).filter { row -> row.any { it.isNotBlank() } }
        if (dataRows.isEmpty()) {
            errs.add("表格是空的：至少要有一行词")
        } else if (dataRows.size > MAX_TERMS + 1) {
            // +1 给表头留量：表头不计入条数；正文真超限由下方截掉表头后精确报
            errs.add("共 ${dataRows.size} 行，超过单库上限 $MAX_TERMS")
        }

        // 表头识别：首格为「词 / 词语 / text」即整行视为表头跳过
        val body = if (dataRows.isNotEmpty()) {
            val h0 = dataRows[0].getOrNull(0)?.trim()
            if (h0 == "词" || h0 == "词语" || h0.equals("text", ignoreCase = true)) dataRows.drop(1) else dataRows
        } else dataRows
        if (body.size > MAX_TERMS) errs.add("词条 ${body.size} 条超过单库上限 $MAX_TERMS")

        val parsed = mutableListOf<Term>()
        val seenIds = mutableSetOf<String>()
        val seenTexts = mutableSetOf<String>()
        val existingTexts = BUILTIN_TERMS.map { it.text }.toHashSet() +
            banks.values.filter { it.id != id }.flatMap { bank -> bank.terms.map { it.text } }.toSet()

        val translit = HAN_TO_PINYIN
        body.forEachIndexed { idx, row ->
            val text2 = row.getOrNull(0)?.trim().orEmpty()
            val pinyinRaw = row.getOrNull(1)?.trim().orEmpty()
            val tip = row.getOrNull(2)?.trim().orEmpty()
            val label = "第 ${idx + 1} 行「$text2」"
            if (text2.isEmpty()) {
                errs.add("$label：词一列为空")
                return@forEachIndexed
            }
            if (tip.isEmpty()) errs.add("$label：缺「用途」列说明")
            val pinyin = if (pinyinRaw.isNotEmpty()) {
                pinyinRaw
            } else {
                val auto = translit?.transliterate(text2)?.trim().orEmpty()
                if (auto.isEmpty() || auto.split(" ").size != text2.length) {
                    errs.add("$label：自动注音失败（可能含非汉字字符），请手填拼音列")
                    return@forEachIndexed
                }
                auto
            }
            // 词条 id 由内容派生（可复算）：改用途说明不变 id；改词/改拼音 = 新词；增删行不影响他人
            val tid = id + "-" + String.format(Locale.US, "%08x", (text2 + "\u0001" + pinyin).hashCode())
            checkAndBuildTerm(errs, seenIds, seenTexts, existingTexts, parsed, id, "📚", label, tid, text2, pinyin, tip)
        }

        abortIfErrors(errs)
        return CustomBank(id = id, name = name, icon = "📚", terms = parsed)
    }

    /** 逐条问题清单 → IllegalArgumentException（前 6 条全展示，剩余计数） */
    private fun abortIfErrors(errs: MutableList<String>) {
        if (errs.isNotEmpty()) {
            val shown = errs.take(6).joinToString("\n") { "· $it" }
            val more = if (errs.size > 6) "\n·……另有 ${errs.size - 6} 个问题" else ""
            throw IllegalArgumentException("词库未通过检查，未做任何改动：\n$shown$more")
        }
    }

    /**
     * JSON / CSV 共用的词条级校验 + 构造（fail-closed：问题只累积进 [errs]，最终由 [abortIfErrors]
     * 整库拒绝）。id 格式检查在调用方（JSON 的 `<库id>-` 前缀规则 / CSV 的内容派生 id 恒合法）。
     */
    private fun checkAndBuildTerm(
        errs: MutableList<String>,
        seenIds: MutableSet<String>,
        seenTexts: MutableSet<String>,
        existingTexts: Set<String>,
        out: MutableList<Term>,
        sceneId: String,
        icon: String,
        label: String,
        tid: String,
        text2: String,
        pinyin: String,
        tip: String,
    ) {
        if (!seenIds.add(tid)) errs.add("$label 词条 id「$tid」在库内重复")
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
            out.add(
                Term(
                    id = tid, scene = sceneId, icon = icon, text = text2,
                    chars = List(text2.length) { k -> TermChar(text2[k].toString(), syllables[k]) },
                    tip = tip,
                ),
            )
        }
    }

    /**
     * 编码探测：UTF-8 BOM → 严格 UTF-8（解码失败不吞错）→ GBK。
     * GBK 兜底是给老版 Excel「另存为 CSV」的默认编码；带 BOM 时剥掉前 3 字节。
     */
    private fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: Exception) {
            String(bytes, Charset.forName("GBK"))
        }
    }

    /**
     * RFC 4180 CSV 状态机（Excel「另存为 CSV」的方言）：双引号包裹的字段可含逗号/换行，
     * `""` 转义为字面引号；\r\n 与 \n 都按行结束处理。tip 里带逗号是家常便饭，
     * 逐字符状态机是唯一不出歧义的解析法。
     */
    private fun parseCsvRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val field = StringBuilder()
        val row = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        fun endField() {
            row.add(field.toString())
            field.setLength(0)
        }
        fun endRow() {
            endField()
            rows.add(row.toList())
            row.clear()
        }
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
                c == '"' && field.isEmpty() -> inQuotes = true
                c == ',' -> endField()
                c == '\r' -> { if (i + 1 < text.length && text[i + 1] == '\n') i++; endRow() }
                c == '\n' -> endRow()
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    /**
     * CSV 库 id 派生：`"c" + 库名 hashCode 的 8 位十六进制`。
     * `String.hashCode` 算法是 Java 语言规范定义的（跨设备/跨版本稳定），故**可复算**——
     * 同名文件重导得到同 id（替换更新、进度保留）；改名 = 新 id = 视为新库（类 KDoc 已警示）。
     */
    private fun bankIdForName(name: String): String =
        "c" + String.format(Locale.US, "%08x", name.hashCode())

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
