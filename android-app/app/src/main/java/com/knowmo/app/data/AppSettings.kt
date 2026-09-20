package com.knowmo.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * v6 设置仓库（design.md §11.1）：独立于 StudyRepository 的 SharedPreferences（`app_settings`）+ 单 JSON：
 * `{"order": [sceneId...], "hidden": [sceneId...], "quota": 10, "quotaNew": 5}`。
 * - order = 场景分区显示顺序（**rec / fav / daily 不进**；存储中出现未知 id 忽略；存储后新增的分区按 SCENES 固有顺序补尾）；
 * - hidden = 隐藏分区 id 集合（**缺省 = 全部分区**——频道栏默认只剩推荐 + 收藏两个固定频道，
 *   prd v9 第 4 条）。**v12 口径（QYJ 2026-09-20，design.md §15）：新装与升级行为一致** ——
 *   存储 JSON 的 `order` 里没有的分区（= 存储后新增的分区）一律按隐藏处理，故任何分区都要进设置页
 *   手动开启才会出现在频道栏；「缺省只对新装机生效」的旧口径作废。
 * - 常用词分区（daily）**不进显隐管理**：不可开关、设置页无此行、频道栏永不出现（v9 第 4 条
 *   口径细化，QYJ 2026-09-20 拍板——原「默认隐藏（设置里可打开）」作废）；它是推荐的基础
 *   内容源，其词由 StudyRepository 特例（CHANNEL_COMMON）恒入推荐范围。
 * - quota = 每日学习词数量（3/5/10/15/20，缺省 10）。**只被 StudyRepository.buildQueue 在重建队列时读**——
 *   当日队列冻结不变、次日生效（design.md §11.1 的注入语义由 MainActivity 组装 quotaProvider 完成）；
 * - quotaNew = 每天学几个新词（1/3/5/10，缺省 5，v6 R14 新词配额独立：新词速率恒定、不被到期复习
 *   挤占；新词上限同时受 quota 总量约束）。同样只在 buildQueue 重建队列时读（次日生效）。
 *   v9：visibleScenes() 同样被 MainActivity 注入 StudyRepository.recScenesProvider（推荐范围过滤，
 *   prd v9 第 3 条）——只在重建队列 / 重洗池时读。
 * 显隐/顺序改动**即时生效**：UI 回调写库后重读 visibleScenes / orderedScenes 触发重组（AppRoot）。
 */
class AppSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private var order: List<String> = defaultOrder()
    // 缺省**全部分区隐藏**——频道栏默认只有推荐 + 收藏两个固定频道（prd v9 第 4 条）。
    // 常用词分区（daily）不进显隐管理（manageableIds 已排除）——设置页无此行、频道栏永不出现，
    // 其词由 StudyRepository 特例恒入推荐范围。
    // v12（QYJ 2026-09-20）：**升级路径与缺省口径一致** —— 有存储 JSON 的用户，凡不在该 JSON 的 `order`
    // 里的分区（= 存储之后才新增的分区）同样按隐藏处理，见 load()。故「默认隐藏」对新装与升级同等成立，
    // 不再有「只对新装机生效」的限定。
    private val hidden = linkedSetOf<String>().apply { addAll(manageableIds()) }
    private var quotaValue: Int = DEFAULT_QUOTA
    private var quotaNewValue: Int = DEFAULT_NEW_QUOTA   // v6 R14 每日新词配额

    init {
        load()
    }

    /**
     * 可管理分区 = SCENES 去掉 rec 与 daily（fav 不在 SCENES，天然不在；两处排除都写上以防将来有人把 fav 加进 SCENES）。
     * daily（常用词）是推荐的基础内容源特例——不参与显隐管理（设置页无此行、频道栏永不出现、不可开关），
     * 其词由 StudyRepository.scopeIds 的 CHANNEL_COMMON 特例恒入推荐范围。
     */
    private fun manageableIds(): List<String> = SCENES
        .filter { it.id != StudyRepository.CHANNEL_DAILY && it.id != StudyRepository.CHANNEL_FAV && it.id != StudyRepository.CHANNEL_COMMON }
        .map { it.id }

    private fun defaultOrder(): List<String> = manageableIds()

    /** 全部分区（按当前 order，含隐藏的）——设置页行序 */
    fun orderedScenes(): List<Scene> = order.mapNotNull { id -> SCENES.firstOrNull { it.id == id } }

    /** 可见分区（按 order 过滤 hidden）——频道栏渲染序；rec/fav 由 ChannelBar 固定渲染，不经过这里 */
    fun visibleScenes(): List<Scene> = orderedScenes().filter { it.id !in hidden }

    fun hiddenIds(): Set<String> = hidden.toSet()

    fun quota(): Int = quotaValue

    fun quotaNew(): Int = quotaNewValue

    /** 每天学几个新词（v6 R14）；非档位取值回退缺省（与 setQuota 同口径） */
    fun setQuotaNew(n: Int) {
        quotaNewValue = if (n in NEW_QUOTA_OPTIONS) n else DEFAULT_NEW_QUOTA
        persist()
    }

    /** 设置分区可见性；rec/fav/daily 与未知 id 一律忽略（daily 不可显隐——推荐内容源特例；防御，正常入口来自设置页已排除） */
    fun setSceneVisible(id: String, visible: Boolean) {
        if (id == StudyRepository.CHANNEL_DAILY || id == StudyRepository.CHANNEL_FAV || id == StudyRepository.CHANNEL_COMMON) return
        if (id !in manageableIds()) return
        if (visible) hidden.remove(id) else hidden.add(id)
        persist()
    }

    /** 上移（delta = -1）/ 下移（delta = +1），边界裁剪；rec/fav 不在 order 内，天然不可移动 */
    fun moveScene(id: String, delta: Int) {
        if (order.isEmpty()) return   // 防空区间：coerceIn(0, -1) 会抛异常
        val i = order.indexOf(id)
        if (i < 0) return
        val j = (i + delta).coerceIn(0, order.lastIndex)
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

    /* ---------- 持久化（读写对称；旧数据无 "settings" 键 → 全部缺省值，无需迁移） ---------- */

    private fun load() {
        val raw = prefs.getString("settings", null) ?: return
        runCatching {
            val obj = JSONObject(raw)
            // v12：留住这个引用——它的「存在与否」用于区分「本版本写的 JSON」与「更早的 JSON」，
            // 见下方「存储后新增的分区默认隐藏」的判定条件。
            val storedOrder = obj.optJSONArray("order")
            val stored = mutableListOf<String>()
            storedOrder?.let { a ->
                for (i in 0 until a.length()) stored.add(a.optString(i))
            }
            // 未知 id 忽略；存储后新增的分区按 SCENES 固有顺序补到末尾（兼容口径，design.md §11.1）。
            // daily 不在 manageableIds → 旧版本存储里的 "daily" 在此（及下方 hidden 过滤）自然丢弃，无需迁移
            val known = stored.filter { it in manageableIds() }
            order = known + manageableIds().filter { it !in known }
            hidden.clear()
            obj.optJSONArray("hidden")?.let { a ->
                for (i in 0 until a.length()) {
                    val id = a.optString(i)
                    if (id in manageableIds()) hidden.add(id)
                }
            }
            // v12（QYJ 2026-09-20，design.md §15）：**存储后新增的分区默认隐藏**。
            // persist() 写的是完整 order，所以「id 不在存储的 order 里」等价于「写入这份 JSON 时该分区
            // 还不存在」，其显隐选择无从继承——取隐藏，使升级路径与新装机缺省（全部分区隐藏）口径一致：
            // 任何分区都要进设置页手动开启才会出现在频道栏，不会因为升级自己冒出来。
            // 仅在 order 数组确实存在时判定：缺失说明这不是本版本写的 JSON，不做推断以免覆盖用户已有选择。
            if (storedOrder != null) hidden.addAll(manageableIds().filter { it !in known })
            val q = obj.optInt("quota", DEFAULT_QUOTA)
            quotaValue = if (q in QUOTA_OPTIONS) q else DEFAULT_QUOTA
            val qn = obj.optInt("quotaNew", DEFAULT_NEW_QUOTA)   // v6 R14：旧数据无键缺省 5
            quotaNewValue = if (qn in NEW_QUOTA_OPTIONS) qn else DEFAULT_NEW_QUOTA
        }
    }

    private fun persist() {
        runCatching {
            // ⚠️ 前提：`order` 必须是**完整**的 `manageableIds()` 全集（见 defaultOrder / load 的补尾）。
            // load() 的「存储后新增的分区默认隐藏」判定（v12，design.md §15）正是靠
            // 「id ∉ 存储 order ⇒ 写入时该分区还不存在」这条推理 —— 改这里前先读 §15.1。
            val obj = JSONObject()
                .put("order", JSONArray(order))
                .put("hidden", JSONArray(hidden.toList()))
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
