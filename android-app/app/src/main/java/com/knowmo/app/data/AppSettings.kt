package com.knowmo.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * v6 设置仓库（design.md §11.1）：独立于 StudyRepository 的 SharedPreferences（`app_settings`）+ 单 JSON：
 * `{"order": [sceneId...], "quota": 10, "quotaNew": 5}`。
 * - order = 场景分区（词库）显示顺序（**rec / fav / daily 不进**；存储中出现未知 id 忽略；
 *   存储后新增的分区按固定顺序补尾）。
 * - **v29（QYJ 2026-09-24）：分区级显隐废除**——不再有 hidden 集合，`visibleScenes()` =
 *   `orderedScenes()`（全部已导入词库）；**显隐由词库的导入/删除直接控制**（导入即显示、
 *   删除即隐藏）。旧存储 JSON 里的 `hidden` 键读入即忽略。原 v12「存储后新增分区默认隐藏」、
 *   v17 MERGED_INTO 显隐迁移随 hidden 一并作废（分区集合自 v23 起已全部来自词库导入）。
 * - **v17/v18/v23 分区退役**：内置分区已全部退役（v23 起分区集合全部来自词库导入）。
 *   存储 JSON 里残留的旧分区 id 会被 `manageableIds()` 过滤掉，无需任何迁移。
 * - quota = 每日学习词数量（3/5/10/15/20，缺省 10）。**只被 StudyRepository.buildQueue 在重建队列时读**——
 *   当日队列冻结不变、次日生效（design.md §11.1 的注入语义由 MainActivity 组装 quotaProvider 完成）；
 * - quotaNew = 每天学几个新词（1/3/5/10，缺省 5，v6 R14 新词配额独立；v28 起语义是**新词上限**
 *   而非固定速率——复习优先占预算，积压时新词自动降速到保底 1 个；新词上限同时受 quota 总量约束）。
 *   同样只在 buildQueue 重建队列时读（次日生效）。
 *   v9：visibleScenes() 同样被 MainActivity 注入 StudyRepository.recScenesProvider（推荐范围过滤，
 *   prd v9 第 3 条）——只在重建队列 / 重洗池时读。
 * 顺序改动**即时生效**：UI 回调写库后重读 orderedScenes 触发重组（AppRoot）。
 */
class AppSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private var order: List<String> = defaultOrder()
    private var quotaValue: Int = DEFAULT_QUOTA
    private var quotaNewValue: Int = DEFAULT_NEW_QUOTA   // v6 R14 每日新词配额

    init {
        load()
    }

    /**
     * 可管理分区 = **全部分区**（v22 起 [allScenes]：自定义词库分区）去掉 rec 与 fav
     * （fav 不在 SCENES，天然不在；两处排除都写上以防将来有人把 fav 加进 SCENES）。
     * v29：管理范围只剩**顺序**（显隐已废）。v9 的 daily（CHANNEL_COMMON）排除随
     * 2026-09-28 恒空特例删除——分区 id 派生自带「c」前缀，本就撞不上退役 id。
     */
    private fun manageableIds(): List<String> = allScenes()
        .filter { it.id != StudyRepository.CHANNEL_DAILY && it.id != StudyRepository.CHANNEL_FAV }
        .map { it.id }

    private fun defaultOrder(): List<String> = manageableIds()

    /**
     * 全部分区（按当前 order，含隐藏的）——设置页行序。
     * v22：**运行期导入**的自定义分区不在存储 order 里 → 按 [allScenes] 固有顺序补尾
     * （与 [load] 的「存储后新增分区补尾」同一口径；导入发生在 AppSettings 初始化之后，
     * load 的补尾覆盖不到它们）。order 里残留的已删自定义 id 在此自然过滤（firstOrNull 为 null）。
     */
    fun orderedScenes(): List<Scene> {
        val mapped = order.mapNotNull { id -> allScenes().firstOrNull { it.id == id } }
        val missing = allScenes().filter { it.id !in order && it.id in manageableIds() }
        return mapped + missing
    }

    /**
     * 可见分区（v29 起 = [orderedScenes]，全部已导入词库）——频道栏渲染序；
     * rec/fav 由 ChannelBar 固定渲染，不经过这里。显隐不再有独立开关：
     * 导入词库 = 显示分区，删除词库 = 隐藏分区。
     */
    fun visibleScenes(): List<Scene> = orderedScenes()

    fun quota(): Int = quotaValue

    fun quotaNew(): Int = quotaNewValue

    /** 每天学几个新词（v6 R14）；非档位取值回退缺省（与 setQuota 同口径） */
    fun setQuotaNew(n: Int) {
        quotaNewValue = if (n in NEW_QUOTA_OPTIONS) n else DEFAULT_NEW_QUOTA
        persist()
    }

    /**
     * v16 拖动重排落位：把 id 移到 order 的 `targetIndex` 处（越界裁剪）。
     * 设置页「我的词库」列表按 order 渲染（v29 起单段、无显隐分组），一次拖动可能跨多项；
     * 落点由调用方按「目标词库在 order 里的下标」给出（`orderedScenes.indexOf(targetScene)`）。
     * 裁剪上界用 `order.lastIndex`：`removeAt(i)` 后长度恰为 lastIndex，
     * `MutableList.add(lastIndex, …)` 是合法插入点（等价于追加到末尾）。
     *
     * 2026-09-28 实机修复：order 与显示列表可能失同步——order 缺某个已导入词库的 id 时，
     * 该词库经 [orderedScenes] 的 missing 补尾仍会**显示**在末尾，但旧实现
     * `order.indexOf(id) < 0` 直接**静默返回**：拖它永远无效、无任何提示
     * （实机症状：拖末尾的「手机微信」到「常用字词」前不生效，诊断行确认 move 已发、列表未变）。
     * 落位前先按 [orderedScenes] 的显示 id 序重对齐 order——与 [load] 的「known + 补尾」
     * 同一语义（多余的退役 id 一并清除），把「order 必须是显示全集」的不变量在变更点自愈。
     */
    fun moveSceneTo(id: String, targetIndex: Int) {
        val displayed = orderedScenes().map { it.id }
        if (displayed.isEmpty()) return   // 防空区间：coerceIn(0, -1) 会抛异常
        if (order != displayed) order = displayed
        val i = order.indexOf(id)
        if (i < 0) return
        val j = targetIndex.coerceIn(0, order.lastIndex)
        if (i == j) return
        order = order.toMutableList().apply {
            removeAt(i)
            add(j, id)
        }
        persist()
    }

    /** 每日学习词数量；非档位取值回退缺省（org.json 手工持久化，不做强类型校验，见 spec/frontend/type-safety.md） */
    fun setQuota(n: Int) {
        quotaValue = if (n in QUOTA_OPTIONS) n else DEFAULT_QUOTA
        persist()
    }

    /**
     * v22：导入/删除自定义词库后由设置页回调重扫存储（等效重新执行 [load]）——
     * 让持久化的 order 对新导入的分区**立即生效**（补尾、过滤已删 id）。
     * 所有 setter 均即时 persist，存储是权威，重放 load 无失真。
     */
    fun rescanScenes() = load()

    /* ---------- 持久化（读写对称；旧数据无 "settings" 键 → 全部缺省值，无需迁移） ---------- */

    private fun load() {
        val raw = prefs.getString("settings", null) ?: return
        runCatching {
            val obj = JSONObject(raw)
            // 未知 id 忽略；存储后新增的分区按固定顺序补到末尾（兼容口径，design.md §11.1）。
            // v29：存储 JSON 里的 `hidden` 键读入即忽略——分区显隐已废除（可见 = 已导入）。
            val stored = mutableListOf<String>()
            obj.optJSONArray("order")?.let { a ->
                for (i in 0 until a.length()) stored.add(a.optString(i))
            }
            val known = stored.filter { it in manageableIds() }
            order = known + manageableIds().filter { it !in known }
            val q = obj.optInt("quota", DEFAULT_QUOTA)
            quotaValue = if (q in QUOTA_OPTIONS) q else DEFAULT_QUOTA
            val qn = obj.optInt("quotaNew", DEFAULT_NEW_QUOTA)   // v6 R14：旧数据无键缺省 5
            quotaNewValue = if (qn in NEW_QUOTA_OPTIONS) qn else DEFAULT_NEW_QUOTA
        }
    }

    private fun persist() {
        runCatching {
            // ⚠️ 前提：`order` 必须是**完整**的 `manageableIds()` 全集（见 defaultOrder / load 的补尾）——
            // 补尾与未知 id 过滤都依赖这条不变量。
            val obj = JSONObject()
                .put("order", JSONArray(order))
                .put("quota", quotaValue)
                .put("quotaNew", quotaNewValue)   // v6 R14 每日新词配额
            prefs.edit().putString("settings", obj.toString()).apply()
        }
    }

    companion object {
        /** 每日学习词数量的可选档位（prd v6 第 2 条：3/5/10/15/20） */
        val QUOTA_OPTIONS = listOf(3, 5, 10, 15, 20)

        /** 每日学习词数量缺省值（与 StudyRepository.DAILY_POOL_QUOTA 一致） */
        const val DEFAULT_QUOTA = 10

        /** v6 R14 每天学几个新词的可选档位（1/3/5/10——对高龄用户合理的新词节奏） */
        val NEW_QUOTA_OPTIONS = listOf(1, 3, 5, 10)

        /** v6 R14 每天学几个新词缺省值（与 StudyRepository.DAILY_NEW_QUOTA 一致） */
        const val DEFAULT_NEW_QUOTA = 5
    }
}
