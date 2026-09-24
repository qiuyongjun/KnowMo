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

/**
 * 自定义词库（v22→v23.1，QYJ 拍板）：**用户导入格式只有 CSV**——一个词库文件 = 一个分区词库，
 * 设置页「我的词库」导入后成为频道栏的一个**普通场景分区**，显隐、排序、分区毕业判定、推荐聚合
 * 全部复用内置分区的既有机制（见 [allScenes] / [STUDY_TERMS]）。
 * v23.1 起 JSON **不再作为导入格式**（QYJ：家属用 Excel 即可，JSON 多一层门槛）——JSON 退为
 * **App 内部存储格式**（[toJson] 写、[parse] 读，用户不可见）。
 *
 * ## 用户导入格式：CSV（用 Excel 填好「另存为 CSV」即可）
 * - **三列：词 / 拼音 / 用途**；首行表头可选（首格为「词 / 词语 / text」即识别为表头跳过）；
 * - **库名由用户导入时命名**（v24，QYJ 2026-09-23 拍板：不再取文件名）；文件名只用来生成
 *   命名对话框的**预填建议**（[suggestBankName]，会剥掉「(1) / - 副本」这类接收工具自动加的
 *   副本后缀，预填稳定）。库 id 由**完整库名**派生（[bankIdForName]，截断只影响显示名——
 *   两个前 8 字相同的不同库名不会撞出同一个库互相覆盖）；
 *   ⚠️ **重导更新要用同一个库名**，换名 = 新库 id = 旧进度失联（term id 挂在库 id 下）——
 *   对话框文案有此提示；官方库（[importOfficial]）无交互，库名 = 文件名（仓库维护，稳定）；
 * - 词条 id 自动生成且**可复算**：`库id + "-" + hash(词+拼音)`——改用途说明不变 id（进度保留），
 *   改词或改拼音 = 新词（本来就该重学），删除/插入行不影响其他词的 id；
 * - **拼音列可空** → [HAN_TO_PINYIN]（android ICU Han-Latin，**API 29+**；API 26~28 即
 *   Android 8/9 系统不带该组件，拼音列留空的行会要求手填，不吞错不误报）；填了以填的为准。
 *   ⚠️ 自动注音对**多音字**可能取错音（适老产品教错音是真伤害）——导入成功文案与文档都提示抽查；
 * - 编码自动探测：UTF-8 BOM → 严格 UTF-8 → GBK（兜住老版 Excel「另存 CSV」的默认编码）；
 * - 同名重导 = 同库 id = **替换更新**（家族自己迭代词库的方式）。
 *
 * ## fail-closed 校验（任一条不合格 → 整库拒绝、逐条报告、已装库不受影响）
 * - 库 id 由库名派生（`c` + 完整库名 hashCode 的 8 位十六进制，跨设备可复算）；派生形态天然
 *   撞不上内置分区 id，`forbiddenBankIds` 检查仅作防御；
 * - 库名 1~8 字（频道 chip 适老宽度）；单库 1~[MAX_TERMS] 条；自定义库总数 ≤ [MAX_BANKS]（频道栏可读性）；
 * - 词条 id 全库唯一；词文本 1~8 字、库内不重复；
 * - **跨库重词跳过而非整库拒绝**（v23.2）：家族多库间词有交集是常态（如面馆私有库与官方
 *   常用字词库），交集之外的词不该被连坐——重复词跳过并在导入结果中计数提示；
 * - 拼音空格逐字配对（自动注音产物同样过这道校验）；tip 1~30 字。
 * - 存储规范化 JSON（[toJson] 产物）在装载时走同一套 [parse] 校验兜底（防文件被手改坏）；
 *   JSON 装载路径的跨库重词**仍整库拒绝**（存储文件是校验后的产物，出现重词只可能是手改）。
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

/** 一次导入的结果：`isNew` = 该库 id 首次入库（调用方据此做「首次导入默认显示」）；
 *  `skippedRepeats` = 与已装库重复而被跳过的词数（导入文案提示用） */
data class ImportOutcome(val bank: CustomBank, val isNew: Boolean, val skippedRepeats: Int = 0)

/** 一键导入官方词库（assets）的结果：imported = 成功入库的库；failed = 失败文件及原因文案；
 *  skippedExisting = 因已装同 id 库而被跳过的文件数（skipInstalled 口径的文案提示用） */
data class OfficialImportResult(
    val imported: List<ImportOutcome>,
    val failed: List<String>,
    val skippedExisting: Int = 0,
)

/** parseCsv 的产物：入库后的词库 + 被跳过的跨库重词数 */
data class CsvParseResult(val bank: CustomBank, val skippedRepeats: Int)

object CustomBanks {

    private val banks = linkedMapOf<String, CustomBank>()
    private var dir: File? = null

    /** 自定义分区调色板（与内置分区底色同语言的浅色系；按库 id 稳定取色） */
    private val PALETTE = listOf(
        Color(0xFFE8F5E9), Color(0xFFFFF8E1), Color(0xFFE0F2F1),
        Color(0xFFF3E5F5), Color(0xFFE1F5FE),
    )

    /** 汉字→带调拼音（android ICU Han-Latin，**API 29+ 才公开**；minSdk 26 → Android 8/9 上
     *  取不到，拼音列留空的行会明确要求手填）。**多音字取常用音，可能错**——拼音列留空才启用。 */
    private val HAN_TO_PINYIN: Transliterator? by lazy {
        runCatching { Transliterator.getInstance("Han-Latin") }.getOrNull()
    }

    /** 自定义库数量上限：v23 起官方 5 库也走导入（官方 CSV + 家属自定义），频道栏可读性约束 */
    const val MAX_BANKS = 10

    /** 单库词条上限（官方常用字词库 226 条，留余量到 500；仍兜住 Excel 全表误粘贴的场景） */
    const val MAX_TERMS = 500

    private const val DIR_NAME = "custom_banks"

    /**
     * 文件名副本后缀（2026-09-23 修复 #9）：微信/浏览器重复接收同一文件会自动命名成
     * 「常用字词(1).csv」「常用字词 - 副本.csv」。库名改由用户命名后，这不再影响库 id——
     * 只用于 [suggestBankName] 清洗**预填建议**（重导同一文件时预填稳定，用户直接确认即替换）。
     * 覆盖：半/全角括号数字、- 副本、- 副本(2)。刻意不匹配「名字 2」（无括号纯数字）——
     * 那可能是有意命名的不同词库。
     */
    private val COPY_SUFFIX = Regex("""[（(]\d+[）)]\s*$|\s*[-－—~～]?\s*副本(?:\s*[（(]\d+[）)])?\s*$""")

    /** 已装自定义库（装载序） */
    val all: List<CustomBank> get() = banks.values.toList()

    /** 自定义分区集合（[allScenes] 拼装用） */
    val scenes: List<Scene> get() = banks.values.map { it.scene }

    /** 自定义词条集合（[STUDY_TERMS] getter 拼装用） */
    val terms: List<Term> get() = banks.values.flatMap { it.terms }

    /**
     * 词条 id → 词条 的全库索引（2026-09-24 修复 #1）。**在 [load] / [import] / [delete]
     * 变更点重建**，消费方（AppRoot 的 termById）直接读它——界面层不再自持词表缓存。
     * 为什么不能留在界面层 remember(bankTick)：长驻的翻页监听（LaunchedEffect(pagerState)）
     * 只在首次组合启动，闭包捕获的本地缓存跨 bankTick 不更新——导入词库后补浏览页会
     * 拿到旧词表（空库导入的当天一张浏览卡都补不出来）。数据层单点维护就没有失效时机问题。
     */
    @Volatile
    private var termsIndex: Map<String, Term> = emptyMap()

    /** 按词条 id 查词条（全库口径 = [STUDY_TERMS]；内置词恒空，索引即自定义库） */
    fun termById(id: String): Term? = termsIndex[id]

    private fun rebuildTermsIndex() {
        termsIndex = (BUILTIN_TERMS + banks.values.flatMap { it.terms }).associateBy { it.id }
    }

    fun find(id: String): CustomBank? = banks[id]

    /**
     * 导入命名对话框的预填建议（v24）：文件名去扩展名 + 剥副本后缀 + 截到显示名上限 8 字
     * （所见即所得，确认后不会再被截断）。**只是建议**——库名（= 库身份）由用户输入决定。
     * 文件名拿不出建议名时返回空串，对话框要求用户自己起一个。
     */
    fun suggestBankName(fileName: String): String =
        COPY_SUFFIX.replace(fileName.substringBeforeLast('.').trim(), "").trim().take(8)

    /** floorMod 防负下标：`abs(Int.MIN_VALUE)` 仍是负数，`%` 会产生负索引越界 */
    fun colorFor(id: String): Color = PALETTE[Math.floorMod(id.hashCode(), PALETTE.size)]

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
        rebuildTermsIndex()
    }

    /**
     * 导入一份 CSV 词库字节（v24 起 `bankTitle` = **用户命名的库名**，官方库路径传文件名）。
     * 写入 `filesDir/custom_banks/<id>.json`（存储规范化，见类 KDoc）→ 进注册表。
     * **字节入口**（编码探测在 [decode] 做——Excel 老版另存的 CSV 是 GBK，
     * 调用方若先按 UTF-8 解码就救不回来了）。先写文件成功再进注册表（写失败时内存态不得领先
     * 磁盘态）；任一步失败抛 IllegalArgumentException（消息可直接展示），已装库不受影响。
     * 返回 [ImportOutcome]：`isNew` = 首次导入（AppRoot 借此「首次导入默认显示」——显式导入
     * = 明确想学；重导替换则**不动**用户手动改过的显隐）。
     * 文件扩展名校验不在内（库名 ≠ 文件名）：SAF 选文件的 `.csv` 兜底由调用方做。
     */
    fun import(context: Context, bytes: ByteArray, bankTitle: String): ImportOutcome {
        val parsed = parseCsv(decode(bytes), bankTitle)
        val bank = parsed.bank
        val d = dir ?: File(context.applicationContext.filesDir, DIR_NAME).also { dir = it }
        d.mkdirs()
        File(d, bank.id + ".json").writeText(toJson(bank), Charsets.UTF_8)
        val isNew = !banks.containsKey(bank.id)
        banks[bank.id] = bank
        rebuildTermsIndex()
        return ImportOutcome(bank, isNew, parsed.skippedRepeats)
    }

    /**
     * 删除词库：移除文件与注册表条目。**刻意不删** TermState / 收藏（见类 KDoc「与学习状态的关系」）——
     * 重导同 id 的文件学习进度自动恢复。
     */
    fun delete(id: String) {
        banks.remove(id)
        dir?.let { d -> File(d, "$id.json").delete() }
        rebuildTermsIndex()
    }

    /**
     * 一键导入官方词库（2026-09-23 修复 #1：首次使用门槛）。官方 CSV 由 build.gradle.kts 把仓库
     * `wordbanks/` 打进 APK assets——CSV 仍是唯一数据源，内容不回到代码里。逐文件走与手动导入
     * 完全相同的 [parseCsv] 校验 + [import] 存储规范化；官方库无交互，**库名 = 文件名去扩展名**
     * （[suggestBankName]，仓库维护、跨版本稳定 → 重导同 id 替换更新，幂等可重复点）。
     *
     * @param skipInstalled true = **只装还没装的库**，已装的跳过不动（2026-09-24 修复 #3）。
     *   设置页文案教家属「想更新已装的库，导入时用同一个名字」——家属用同一名导过自己的 CSV 后，
     *   再点一键导入会把官方版悄悄覆盖上去；一键导入因此只补缺，更新走显式的「更新官方词库」
     *   （skipInstalled = false，UI 侧要求二次确认）。
     *   单个文件失败不连坐（记入 [OfficialImportResult.failed]），assets 整体读不到时两列表皆空
     *   （调用方按空结果提示，不抛异常阻塞空态引导页）。
     */
    fun importOfficial(context: Context, skipInstalled: Boolean = false): OfficialImportResult {
        val appContext = context.applicationContext
        val names = runCatching { appContext.assets.list("").orEmpty().filter { it.endsWith(".csv", ignoreCase = true) } }
            .getOrElse { return OfficialImportResult(emptyList(), emptyList()) }
        val imported = mutableListOf<ImportOutcome>()
        val failed = mutableListOf<String>()
        var skippedExisting = 0
        for (name in names.sorted()) {
            // 跳过判定用与 import 相同的 id 派生链（文件名 → 预填库名 → 库 id），
            // 保证「这里跳过的」与「真导入会覆盖的」是同一个库
            if (skipInstalled && banks.containsKey(bankIdForName(suggestBankName(name)))) {
                skippedExisting++
                continue
            }
            runCatching {
                val bytes = appContext.assets.open(name).use { it.readBytes() }
                import(appContext, bytes, suggestBankName(name))
            }.onSuccess { imported.add(it) }
                .onFailure { e -> failed.add("${name.removeSuffix(".csv")}：${e.message ?: "读取失败"}") }
        }
        return OfficialImportResult(imported, failed, skippedExisting)
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
        // 「一个词只留一份」（v17 契约）：与已装自定义库重复的词整库拒绝（存储文件是校验后的
        // 产物，出现重词只可能是手改；导入端 CSV 路径另行降级为跳过，见 parseCsv）
        val existingTexts = banks.values.filter { it.id != id }.flatMap { bank -> bank.terms.map { it.text } }.toSet()

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
     * 校验并解析一份 CSV 词库（v22.1；v24 起第二参数 = **库名**而非文件名）。格式约定见
     * 类 KDoc「用户导入格式」：三列（词 / 拼音 / 用途）、库 id 由库名派生、词条 id 由内容派生、
     * 拼音可空自动注音。跨库重词（v23.2）：**跳过并计数**，返回 [CsvParseResult.skippedRepeats]
     * 供导入文案提示。
     */
    fun parseCsv(text: String, bankTitle: String): CsvParseResult {
        val errs = mutableListOf<String>()

        // v24：库名即身份——用户输入什么就是什么（含 (1) 这类后缀也是他的自由），不清洗
        val base = bankTitle.trim()
        if (base.isEmpty()) errs.add("库名不能是空的，请起个名字")
        // 超 8 字自动截断（输入框已限长并预填截断后的建议名，这里兜底）
        val name = base.take(8)
        // id 按完整库名派生：截断只作用于显示名，前 8 字相同的不同库名不会互相覆盖
        val id = bankIdForName(base)
        if (id in forbiddenBankIds()) errs.add("库名「$name」派生的库 id 与内置分区冲突，请换个名字")
        // 不同库名撞出同 id（32 位 hashCode 碰撞）时继续导入会按「重导替换」覆盖掉另一个库，
        // fail-closed：要求换名。
        banks[id]?.let { existing ->
            if (existing.name != name) {
                errs.add("库名「$name」与已装词库「${existing.name}」派生出相同的库 id（碰撞），请换个名字")
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
        val existingTexts = banks.values.filter { it.id != id }.flatMap { bank -> bank.terms.map { it.text } }.toSet()
        var skippedRepeats = 0

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
            // 跨库重词跳过（v23.2，整库拒绝的旧口径见类 KDoc）：交集之外的词不该被连坐
            if (text2 in existingTexts) {
                skippedRepeats++
                return@forEachIndexed
            }
            if (tip.isEmpty()) errs.add("$label：缺「用途」列说明")
            val pinyin = if (pinyinRaw.isNotEmpty()) {
                pinyinRaw
            } else if (translit == null) {
                // Android 8/9（API 26~28）不带 ICU Transliterator：如实报因，别误导成字符问题
                errs.add("$label：此手机系统不支持自动注音，请在拼音列手填")
                return@forEachIndexed
            } else {
                val auto = translit.transliterate(text2).trim()
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

        // 行都在但全被跳过/无有效词 → 空库不可导（装载端要求 ≥1 条）
        if (parsed.isEmpty() && errs.isEmpty() && body.isNotEmpty()) {
            errs.add("${body.size} 条词全部与已装词库重复，没有需要导入的新词")
        }

        abortIfErrors(errs)
        return CsvParseResult(CustomBank(id = id, name = name, icon = "📚", terms = parsed), skippedRepeats)
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
     * CSV 库 id 派生：`"c" + 完整库名 hashCode 的 8 位十六进制`（**截断前**的库名——
     * 截断只作用于显示名，见 parseCsv）。`String.hashCode` 算法是 Java 语言规范定义的
     * （跨设备/跨版本稳定），故**可复算**——同名重导得到同 id（替换更新、进度保留）；
     * 换名 = 新 id = 视为新库（类 KDoc 已警示）。
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
