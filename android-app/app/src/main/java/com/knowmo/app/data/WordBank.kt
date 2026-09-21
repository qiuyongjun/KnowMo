package com.knowmo.app.data

/**
 * 词库 v18：6 个有词的分区 414 词条（5 个场景分区 311 条 + 常用词 103 条）。
 * term() 紧凑构造：拼音按空格逐字配对，字数不符启动即抛错（fail-fast，便于挑错）。
 *
 * ## id 契约（⚠️ v17 修订，动手改词库前必读）
 * - `id` 一经发布**永不删除、永不复用**——学习状态（`TermState` / 当日 `counts` / `favorites` /
 *   当日队列）全部按 id 挂靠，改 id 等于丢掉这个词的全部进度与收藏。
 * - v17 起 **`id` 的前缀只表示「首次归属」，不再是当前归属**。跨分区搬迁**只改 `term()` 的
 *   `scene` 参数、不改 id**（2026-09-21 QYJ 拍板方案 B）。故库内存在 106 条「id 前缀 ≠ scene」
 *   的词条 —— **这是预期状态，不是错误**。按 id 前缀过滤或统计一律是 bug，必须按 `term.scene`。
 * - 校验脚本第 7 项据此改为「前缀 ∈ 当前分区 ∪ 已退役分区」，并另报搬迁条数供审计。
 *
 * ## 分区沿革（历史口径；当前规则以本文档 v17 段与各分区子注释为准）
 * v9（prd 第 4 条）：新增「常用词」分区（scene = "daily"）——日常生活高频字词，
 * **不是固定某个场景的内容**。其词**恒入推荐范围**（StudyRepository.CHANNEL_COMMON 特例）——
 * 软件默认只有推荐 + 收藏两个频道时，推荐才有内容。
 * v10（口水话清理）：删除 52 条纯口语词条——判据为「老人生活环境里该词是否会以文字形式出现」
 * （招牌/价签/菜单/告示/屏幕/包装）；口语词删，有文字环境的词留。
 * v11（面馆补充，2026-09-20）：`food` 分区补面馆相关词（调料罐 / 菜单加料 / 价目表规格 /
 * 告示证照 / 后厨物件）。判据同 v10——只收「会以文字形式出现」的词；口说类整句（「不要香菜」这类）
 * 不单收，因为对应的名词（香菜）已在库中，重复无益。
 * v12（家电分区，2026-09-20）：新增「家电」分区（scene = "appliance"，63 条）——电器名称 / 面板按键 /
 * 遥控器与电视 / 说明书与铭牌 / 安全警示 / 电源与插座 / 各机型专有词（冰箱·洗衣机·空调·微波炉·
 * 电饭锅·热水器与灶具）。收录判据同 v10——只收「会以文字形式出现在电器面板 / 铭牌 / 贴纸 / 屏幕 /
 * 说明书上」的词。该分区**默认隐藏**（QYJ 2026-09-20 拍板**不做**推荐范围特例），且**新装与升级
 * 同等成立**——同批改了 `AppSettings.load()`，让「存储 JSON 的 order 里没有的分区」也默认隐藏
 * （design.md §15）；`StudyRepository` 逻辑零改动。
 * v13（三分区查漏补缺，2026-09-20）：transit +8（含导航/无人售票/扫码乘车）、
 * food +10（含筷子/早餐/火锅）、appliance +11（含烘干/模式/风速/电池），
 * 共 29 条。判据同前——只收老人生活环境里会以文字形式出现的词（QYJ 拍板 P1+P2 全收）。
 * v15（round5 五分区梳理，2026-09-20）：daily/transit/food/phone/appliance 收推荐档共 45 条；
 * 同轮 daily 删 5 条启蒙单字（人/大/小/好/钱，环境里不独立出现），daily 定位改为
 * 「公共标识字 + 跨场景通用词」（QYJ 拍板；可选 12 条与字覆盖补词 14 条未收）。
 * v16（日用洗护补充，2026-09-21，QYJ 点名）：daily +18 条居家日用品——洗护 8（洗发露/沐浴露/
 * 香皂/牙膏/牙刷/毛巾/洗手液/护发素）、清洁 5（洗洁精/洗衣粉/洗衣液/消毒液/杀虫剂）、
 * 纸品与日用 5（卫生纸/抽纸/湿巾/垃圾袋/保鲜袋）。收录判据同 v10——只收「包装 / 瓶身 / 标签上
 * 会以文字印出来」的词。落在 daily 而非新分区：daily 恒入推荐范围（CHANNEL_COMMON 特例），
 * 新增词默认即学，无需进设置页开启；代价是 daily 定位由「公共标识字」外延到「居家消耗品包装词」。
 * ⚠️ 本批触发了一次**跨分区去重**（同日 QYJ 拍板「一个词只留一份」）：收洗洁精时发现它已在 food-99
 * （面馆后厨语境），QYJ 决定连既有的「身份证」双份一并清掉 —— **洗洁精只留 daily-46（删 food-99）**、
 * **身份证只留 gov-2（删 bank-16；「办事」是它的主场，bank 那句提示只覆盖办业务一途）**。
 * 留下的 id 一律不动（学习状态按 id 挂靠），删掉的 id 成空号、永不复用。
 * ⚠️ 容量知情项：本批净 +16 条（+18 新增 −2 去重），词库 549 → 565，超载倍数由 1.8~3.7 升到 1.9~3.8
 * （见 StudyRepository.DAILY_POOL_QUOTA 的容量观测记录）；QYJ 2026-09-20「先不动配额」的结论不变，
 * 待实机 f30 观测清债征兆。
 *
 * ## v17（分区合并 + 通用词回流，2026-09-21 QYJ 拍板；可管理分区 13 → 10）
 * 起因：QYJ 指出「分区太多」且「很多词其实是日常生活的，不该放到特定分区」。诊断后确认是**一个病因**——
 * 13 个可管理分区里 9 个的条数挤在 24~30 的窄带，为了让每个分区"像样"而把通用词硬塞进去
 * （卫生间/电梯 在医院、钥匙 在物业、盐糖醋 在面馆〔时名「吃饭」〕、抹布纸巾筷子 同在面馆）；而这些场景分区**默认
 * 全部隐藏**，通用词因此被锁在用户不开启就永远学不到的地方。
 *
 * ① **通用词回流 `daily`（35 条）**——判据与 v16 同源，不另立新分区（daily 恒入推荐范围，且 v17 起
 *    成为默认频道，无需开启即可学到）：
 *    - 包装 / 瓶身 / 标签词 20：`food-66..77` 家庭调料 12 + `food-98/100/102/103/104`
 *      （抹布 / 保鲜膜 / 牙签 / 纸巾 / 筷子）+ `market-26/27`（生产日期 / 保质期）+ `market-19` 塑料袋；
 *    - 通用公共标识 / 设施 8：`transit-4` 出口、`market-21` 入口、`transit-33` 小心地滑、
 *      `food-64` 请勿吸烟、`hospital-31` 电梯、`hospital-32` 卫生间、`gov-22` 楼梯、`property-19` 钥匙；
 *    - 通用按键 7：`appliance-10` 启动、`-11` 暂停、`-76` 开始、`-75` 待机、`-22` 返回、`-23` 确认、
 *      `-77` 取消 —— 先例是 `daily-32` 开 / `daily-33` 关，其提示本就写着「电器面板上所标的」档位。
 * ② **分区合并（可管理分区 13 → 10）**：
 *    - **解散 `emergency`**（17 条全有明确归属，该分区是为"功能列表完整"凑出来的）：电话类 5 条
 *      → `phone`、医疗类 6 条 → `hospital`、消防与电梯类 4 条 → `property`、`派出所` 1 条 → `gov`；
 *    - **`bank` → `gov`**（26 条）：同为「取号 → 等叫号 → 到窗口 → 签字 → 拿回执」，词汇重叠 6 组；
 *    - **`medicine` → `hospital`**（29 条）：用药是看病的下游（医院已有 `饭后吃药` / `处方` 等）。
 *    `SCENES` 删除这三项；`AppSettings` 增显隐迁移表（`MERGED_INTO`，防用户原有显隐选择静默丢失）。
 * ③ **重复概念同批清理（删 15 条）**：校验脚本第 8 项报的 9 组同文本跨分区重复（余额 / 取消 / 外卖 /
 *    生产日期 / 确认 / 菜单 / 说明书 / 转账 / 预约）全部清掉，另加阅读全文发现的 6 条跨词同义
 *    （饭后吃药↔饭后服用、紧急出口↔安全出口、药房↔药店、签名↔请签字、防诈骗三连取一）。删除的
 *    id 成空号、永不复用。清完跨分区重词为 **0**。
 *    ⚠️ **刻意未清**：「扫码 X」六连（`market-2` 扫码付款 / `transit-32` 扫码乘车 / `food-124` 扫码点餐 /
 *    `express-16` 扫码出库 / `phone-33` 扫一扫 / `phone-11` 付款码）—— 同一模板，但**是六块不同的真实
 *    标牌**，不属「重复概念」，未动。
 * ④ **`daily` 升为默认频道**：v9 起 daily 不进显隐管理（不可开关、频道栏不出现）；v17 起它在频道栏
 *    **恒显示、不可移除**（与推荐 / 收藏同列固定频道），仍不进 `AppSettings` 的 order/hidden。
 *    至此 daily 既是推荐的内容源，也是可主动浏览的分区。
 *
 * ⚠️ 容量知情项：本批净 −15 条（565 → 550），超载倍数由 1.9~3.8 降到 **1.8~3.7**
 * （见 `StudyRepository.DAILY_POOL_QUOTA` 的容量观测记录）；QYJ「先不动配额、待实机 f30 观测」的
 * 结论不变。⚠️ **`daily` 由 37 涨到 72 条**，推荐队列里通用词的比重显著上升 —— 方向是有意的
 * （先学最能迁移的字），但这是一次实质的课程结构调整，不是纯整理。
 *
 * ## v18（分区大幅收敛，2026-09-21 QYJ 拍板；可管理分区 10 → 5）
 * QYJ 逐区指令：`天气日历`「全部归为常用词」、`物业水电` / `快递驿站` / `买菜` / `办事` 「全部不要」。
 * 执行前我提了三处「上一轮刚发生的耦合」，QYJ 确认：安全类 4 条**一起删**、**身份证保留**、
 * **快递核心 6 条保留**。
 *
 * - **整区转 `daily`（24 条）**：`weather-*` 全部 —— 其中时间词 9 条（今天/明天/后天/星期一/周末/
 *   上午/中午/下午/晚上/傍晚）本就是 v17 T2 判据下的「跨场景通用词」，天气词 10 条也出现在手机屏幕与
 *   公告牌上。至此 **`weather` 分区退役**。
 * - **整区删除（136 条）**：
 *   - `market` 25 条全部删（含白菜/土豆/西红柿/鸡蛋/豆腐/猪肉等生鲜名与价签词）；
 *   - `gov` 56 条删 55，**唯留 `gov-2` 身份证** 归入 daily（该条是 v16 跨分区去重的唯一存活副本，
 *     另一份 `bank-16` 早已删除）；
 *   - `express` 30 条删 24，**留 6 条**归入 daily：快递 / 包裹 / 取件码 / 驿站 / 收件人 / 退货；
 *   - `property` 32 条**全部删**，**含 v17 刚从解散的 `emergency` 迁入的 4 条安全类词**
 *     （灭火器 / 安全出口 / 电梯困人 / 电梯警铃）—— 这一点 QYJ 在本批执行前被单独问过，明确选择一起删。
 * - 规模：550 → **414 条**（删 136、迁 daily 31）；`daily` 72 → **103 条**，
 *   成为全库最大的分区；`SCENES` 由 12 项降到 **7 项**（推荐 + 常用词 + 5 个场景分区）。
 * - ⚠️ **`daily` 的定位在 v17~v18 两轮里实质上变成了「兜底分区」**：不设场景限制、什么都能放，
 *   且因为它是固定频道，搬进来的词**无需用户开启即可学到**。后续若还要收词，优先考虑放这里
 *   而不是新建场景分区。
 * - **同日后续：`food` 由「吃饭」改名为「面馆」**（QYJ：这个区就是专门针对面馆做的）。
 *   只动 `Scene.name` 一个字段 —— **scene id 仍为 `food`**，`id` 前缀、学习状态、词条内容全不动。
 *   核实过：93 条里 `food-33`~`food-65` 是 2026-09-20 并入的原独立「面馆」分区，v11 又专项
 *   补了 30 条面馆词（调料罐 / 加料栏 / 价目表规格 / 墙面证照 / 后厨物件），以面馆为主场成立。
 *   ⚠️ 另约 10 条是**通用餐馆 / 食杂店**语境而非面馆专有（早餐 / 火锅 / 油条 / 豆浆 / 包子 /
 *   馒头 / 快餐 / 炒饭 / 矿泉水 / 饮料 / 排队等位）。**QYJ 2026-09-21 决定原样保留** —— 面馆确实
 *   也卖早餐、也有冰柜饮料，这些词出现在面馆的招牌 / 菜单 / 冰柜上并不违和。
 *   ⇒ **这是有意为之，不是漏改**：后续别把它们当"混进面馆的外来词"清掉。
 */

private val ICON = SCENES.associate { it.id to it.icon }

private fun term(id: String, scene: String, text: String, pinyin: String, tip: String): Term {
    val py = pinyin.split(" ")
    require(text.length == py.size) { "词条 $id 拼音与字数不符: $text / $pinyin" }
    return Term(
        id, scene, ICON.getValue(scene), text,
        List(text.length) { i -> TermChar(text[i].toString(), py[i]) },
        tip,
    )
}

/* ==================== 常用词（v9 设立；v17 升为默认频道，恒显示不可移除） ==================== */

private val DAILY_COMMON = listOf(
    term("daily-4", "daily", "上", "shàng", "位置的上面"),
    term("daily-5", "daily", "下", "xià", "位置的下面"),
    term("daily-6", "daily", "水", "shuǐ", "喝的、洗的都靠它"),
    term("daily-7", "daily", "火", "huǒ", "做饭取暖要用它，小心烫"),
    term("daily-8", "daily", "门", "mén", "进出走的门"),
    term("daily-20", "daily", "洗手", "xǐ shǒu", "用水把两只手洗干净"),
    term("daily-21", "daily", "衣服", "yī fu", "穿在身上的"),
    term("daily-22", "daily", "裤子", "kù zi", "穿在两条腿上的"),
    term("daily-23", "daily", "鞋子", "xié zi", "穿在脚上的"),
    term("daily-26", "daily", "电话", "diàn huà", "打给别人的通话工具"),
    term("daily-27", "daily", "手机", "shǒu jī", "随身带的小电话"),
    // ---- 以下 8 条为 round5 五分区梳理（v15，2026-09-20 QYJ 拍板收推荐档）----
    // 定位改变：daily 由「启蒙单字」改为「公共标识字 + 跨场景通用词」（人/大/小/好/钱 同轮删除）
    term("daily-28", "daily", "推", "tuī", "门上的字，往外推才开"),
    term("daily-29", "daily", "拉", "lā", "门上的字，往里拉才开"),
    term("daily-30", "daily", "男", "nán", "男厕所门上的字"),
    term("daily-31", "daily", "女", "nǚ", "女厕所门上的字"),
    term("daily-32", "daily", "开", "kāi", "面板上标的这一档，按了通电"),
    term("daily-33", "daily", "关", "guān", "面板上标的这一档，按了断电"),
    term("daily-36", "daily", "免费", "miǎn fèi", "不要钱的，停车场、量血压都写着"),
    term("daily-37", "daily", "收费", "shōu fèi", "要交钱的，公厕、停车场门口挂着"),
    // ---- 以下 18 条为日用洗护补充（v16，2026-09-21 QYJ 点名收录）----
    // 判据同 v10：只收「包装 / 瓶身 / 标签 / 说明书上会以文字印出来」的词。
    // 这 18 条进 daily = 恒入推荐范围（CHANNEL_COMMON 特例），默认就能学到，无需去设置页开启。
    // 洗护（8）
    term("daily-38", "daily", "洗发露", "xǐ fà lù", "洗头发的，瓶身上印着这三个字"),
    term("daily-39", "daily", "沐浴露", "mù yù lù", "洗澡用的，瓶子上印着"),
    term("daily-40", "daily", "香皂", "xiāng zào", "洗手洗脸的肥皂，纸包装上印着"),
    term("daily-41", "daily", "牙膏", "yá gāo", "刷牙用的，管子上印着"),
    term("daily-42", "daily", "牙刷", "yá shuā", "刷牙用的，包装上印着"),
    term("daily-43", "daily", "毛巾", "máo jīn", "洗脸擦身子用的，布上的标签印着"),
    term("daily-44", "daily", "洗手液", "xǐ shǒu yè", "按一下出水洗手，瓶子上印着"),
    term("daily-45", "daily", "护发素", "hù fà sù", "洗完头抹的，瓶子上印着"),
    // 清洁（5）
    term("daily-46", "daily", "洗洁精", "xǐ jié jīng", "洗碗用的，瓶子上印着"),
    term("daily-47", "daily", "洗衣粉", "xǐ yī fěn", "洗衣服的白粉，袋子上面印着"),
    term("daily-48", "daily", "洗衣液", "xǐ yī yè", "洗衣服的液体，瓶子上面印着"),
    term("daily-49", "daily", "消毒液", "xiāo dú yè", "擦地擦桌子用的，不能和洁厕灵兑一起"),
    term("daily-50", "daily", "杀虫剂", "shā chóng jì", "灭蚊子蟑螂的，罐子上印着"),
    // 纸品与日用（5）
    term("daily-51", "daily", "卫生纸", "wèi shēng zhǐ", "上厕所用的纸，包装上印着"),
    term("daily-52", "daily", "抽纸", "chōu zhǐ", "一抽一张的纸巾，盒子、袋子上面印着"),
    term("daily-53", "daily", "湿巾", "shī jīn", "带水的小纸巾，小包装上印着"),
    term("daily-54", "daily", "垃圾袋", "lā jī dài", "套在垃圾桶上的袋子，包装上印着"),
    term("daily-55", "daily", "保鲜袋", "bǎo xiān dài", "装菜装吃的透明袋子，盒子上印着"),
    // ---- v17（2026-09-21 分区合并）：以下 35 条由 appliance、food、gov、hospital、market、property、transit 迁入 ----
    // ⚠️ id 保留原分区前缀（= 首次归属），`scene` 字段才是当前归属 —— 见文件头 id 契约。
    term("appliance-10", "daily", "启动", "qǐ dòng", "按一下，机器开始干活"),
    term("appliance-11", "daily", "暂停", "zàn tíng", "先停一下，还能接着用"),
    term("appliance-22", "daily", "返回", "fǎn huí", "退回上一个画面"),
    term("appliance-23", "daily", "确认", "què rèn", "按这个键，表示就这么办"),
    term("appliance-75", "daily", "待机", "dài jī", "机器歇着没干活，按一下就能用"),
    term("appliance-76", "daily", "开始", "kāi shǐ", "按这个键，机器就动起来"),
    term("appliance-77", "daily", "取消", "qǔ xiāo", "按错了就按它，重新来"),
    term("food-64", "daily", "请勿吸烟", "qǐng wù xī yān", "墙上贴的字，店里不准抽烟"),
    term("food-66", "daily", "盐", "yán", "盐罐上的字，白白的细颗粒"),
    term("food-67", "daily", "糖", "táng", "糖罐上的字，甜的"),
    term("food-68", "daily", "醋", "cù", "醋瓶上的字，酸的那瓶"),
    term("food-69", "daily", "味精", "wèi jīng", "烧菜提味用的白粉，袋子上印着"),
    term("food-70", "daily", "鸡精", "jī jīng", "烧菜提味用的黄粉，袋子上印着"),
    term("food-71", "daily", "花椒", "huā jiāo", "麻嘴的小圆粒，川菜都放"),
    term("food-72", "daily", "胡椒", "hú jiāo", "胡椒磨的粉，撒汤里提味"),
    term("food-73", "daily", "酱油", "jiàng yóu", "黑黑的咸汁，也叫生抽"),
    term("food-74", "daily", "香油", "xiāng yóu", "芝麻磨的油，点面时滴几滴"),
    term("food-75", "daily", "料酒", "liào jiǔ", "炒肉去腥用的，瓶子上印着"),
    term("food-76", "daily", "淀粉", "diàn fěn", "勾芡、码肉用的白粉"),
    term("food-77", "daily", "豆瓣酱", "dòu bàn jiàng", "郫县出的辣酱，烧菜时放一勺"),
    term("food-98", "daily", "抹布", "mā bù", "擦桌子用的布"),
    term("food-100", "daily", "保鲜膜", "bǎo xiān mó", "透明的一层膜，盖碗上包东西"),
    term("food-102", "daily", "牙签", "yá qiān", "剔牙用的小棍"),
    term("food-103", "daily", "纸巾", "zhǐ jīn", "擦嘴的纸，桌上摆着"),
    term("food-104", "daily", "筷子", "kuài zi", "吃饭用的两根小棍，餐具柜和包装上印着"),
    term("gov-22", "daily", "楼梯", "lóu tī", "指示牌上写着，一层层走上去"),
    term("hospital-31", "daily", "电梯", "diàn tī", "上楼下楼坐这个"),
    term("hospital-32", "daily", "卫生间", "wèi shēng jiān", "门上写着这三个字，就是厕所"),
    term("market-19", "daily", "塑料袋", "sù liào dài", "买东西给的白色袋子"),
    term("market-21", "daily", "入口", "rù kǒu", "从这里进店"),
    term("market-26", "daily", "生产日期", "shēng chǎn rì qī", "包装上印着，东西是哪天做的"),
    term("market-27", "daily", "保质期", "bǎo zhì qī", "包装上印着，过了这日子就别吃了"),
    term("property-19", "daily", "钥匙", "yào shi", "开家门的"),
    term("transit-4", "daily", "出口", "chū kǒu", "从这扇门出去"),
    term("transit-33", "daily", "小心地滑", "xiǎo xīn dì huá", "商场、楼道常立的黄牌子，走路慢点"),
    // ---- v18（2026-09-21 分区收敛）：以下 31 条由 express、gov、weather 迁入 ----
    // ⚠️ 「天气日历」整区、以及 express 与 gov 的少数例外条目；id 保留原前缀，见文件头 id 契约。
    term("express-1", "daily", "快递", "kuài dì", "网上买的东西送来了"),
    term("express-2", "daily", "包裹", "bāo guǒ", "寄来的一大包东西"),
    term("express-3", "daily", "取件码", "qǔ jiàn mǎ", "手机短信里那串数字"),
    term("express-4", "daily", "驿站", "yì zhàn", "小区门口代收快递的店"),
    term("express-7", "daily", "收件人", "shōu jiàn rén", "上面写着你的名字"),
    term("express-12", "daily", "退货", "tuì huò", "不想要了，寄回去"),
    term("gov-2", "daily", "身份证", "shēn fèn zhèng", "最重要的证件，出门要带"),
    term("weather-1", "daily", "今天", "jīn tiān", "就是现在这一天"),
    term("weather-2", "daily", "明天", "míng tiān", "一觉醒来那天"),
    term("weather-3", "daily", "后天", "hòu tiān", "明天的明天"),
    term("weather-4", "daily", "星期一", "xīng qī yī", "一周的头一天"),
    term("weather-5", "daily", "周末", "zhōu mò", "星期六、星期天，歇着"),
    term("weather-6", "daily", "上午", "shàng wǔ", "早上到中午之前"),
    term("weather-7", "daily", "中午", "zhōng wǔ", "十二点前后"),
    term("weather-8", "daily", "下午", "xià wǔ", "中午到天黑之前"),
    term("weather-9", "daily", "晚上", "wǎn shang", "天黑以后"),
    term("weather-11", "daily", "傍晚", "bàng wǎn", "太阳快落山"),
    term("weather-12", "daily", "晴天", "qíng tiān", "大太阳，好天气"),
    term("weather-13", "daily", "阴天", "yīn tiān", "没太阳，也不下雨"),
    term("weather-14", "daily", "下雨", "xià yǔ", "出门记得带伞"),
    term("weather-15", "daily", "下雪", "xià xuě", "路滑，慢慢走"),
    term("weather-18", "daily", "降温", "jiàng wēn", "要变冷了，添衣服"),
    term("weather-19", "daily", "高温预警", "gāo wēn yù jǐng", "太热了，少出门"),
    term("weather-20", "daily", "零下", "líng xià", "温度到了零度以下，水会结冰"),
    term("weather-21", "daily", "路滑", "lù huá", "路面结冰打滑，要小步慢走"),
    term("weather-22", "daily", "台风", "tái fēng", "大风大雨，别出门"),
    term("weather-23", "daily", "大雾", "dà wù", "看不清，开车慢点"),
    term("weather-29", "daily", "生日", "shēng rì", "出生纪念日"),
    term("weather-30", "daily", "农历", "nóng lì", "老日历上的日子，标着初一、十五"),
    term("weather-31", "daily", "日历", "rì lì", "墙上挂的，看日子"),
    term("weather-32", "daily", "提醒", "tí xǐng", "手机到点会响"),
)

/* ==================== 公交地铁 ==================== */

private val TRANSIT = listOf(
    term("transit-1", "transit", "地铁站", "dì tiě zhàn", "看到这仨字，就是坐地铁的地方"),
    term("transit-2", "transit", "公交站", "gōng jiāo zhàn", "等公共汽车的牌子"),
    term("transit-3", "transit", "从前门上车", "cóng qián mén shàng chē", "车门上贴着，上车都从前门上"),
    term("transit-5", "transit", "换乘", "huàn chéng", "在这里换另一趟车"),
    term("transit-6", "transit", "下一站", "xià yī zhàn", "广播说这三个字后，就是站名了"),
    term("transit-7", "transit", "首班车", "shǒu bān chē", "早上的第一班车"),
    term("transit-8", "transit", "末班车", "mò bān chē", "晚上最后一班，错过就没了"),
    term("transit-9", "transit", "票价", "piào jià", "坐一趟车多少钱"),
    term("transit-10", "transit", "投币", "tóu bì", "把硬币、纸币投进箱子"),
    term("transit-11", "transit", "刷卡", "shuā kǎ", "上车时卡贴一下机器，嘀一声"),
    term("transit-12", "transit", "老年卡", "lǎo nián kǎ", "老年人坐车免费的卡"),
    term("transit-13", "transit", "让座", "ràng zuò", "把座位让给需要的人"),
    term("transit-14", "transit", "终点站", "zhōng diǎn zhàn", "车开的最后一站"),
    term("transit-15", "transit", "方向", "fāng xiàng", "坐车前先看清去哪边"),
    term("transit-16", "transit", "进站口", "jìn zhàn kǒu", "坐地铁从这里进"),
    term("transit-17", "transit", "安检", "ān jiǎn", "包放机器上过一下检查"),
    term("transit-18", "transit", "禁止携带", "jìn zhǐ xié dài", "这些东西不许带上车"),
    term("transit-19", "transit", "先下后上", "xiān xià hòu shàng", "让下车的人先下来"),
    term("transit-20", "transit", "小心缝隙", "xiǎo xīn fèng xì", "上车注意脚下的空当"),
    term("transit-21", "transit", "站台", "zhàn tái", "站着等车的地方"),
    term("transit-22", "transit", "老弱病残孕", "lǎo ruò bìng cán yùn", "这排座位要让给他们"),
    term("transit-23", "transit", "高峰期", "gāo fēng qī", "上下班人最多的钟点"),
    term("transit-26", "transit", "天桥", "tiān qiáo", "过马路的天上桥"),
    term("transit-27", "transit", "路口", "lù kǒu", "马路交叉的地方，过街要看灯"),
    term("transit-28", "transit", "红灯停", "hóng dēng tíng", "红灯亮了先站住"),
    term("transit-29", "transit", "斑马线", "bān mǎ xiàn", "白条条的过街道"),
    term("transit-30", "transit", "导航", "dǎo háng", "手机上指路的软件，屏幕上就有这两个字"),
    term("transit-31", "transit", "无人售票", "wú rén shòu piào", "车上贴着，自己投币刷卡，没人收钱"),
    term("transit-32", "transit", "扫码乘车", "sǎo mǎ chéng chē", "车门口的牌子，用手机扫码就能坐车"),
    term("transit-34", "transit", "屏蔽门", "píng bì mén", "站台上隔在人和轨道中间的玻璃门"),
    term("transit-35", "transit", "无障碍电梯", "wú zhàng ài diàn tī", "推轮椅、拉行李能坐的电梯，有牌子"),
    term("transit-36", "transit", "一卡通", "yī kǎ tōng", "公交地铁都能刷的卡，充值点有牌子"),
    term("transit-37", "transit", "补票", "bǔ piào", "没买到票或坐过站，车上要办的手续"),
    // ---- v15 round5 收推荐档：现余 7 条（v17 删「紧急出口」1 条 —— 与 emergency-24 安全出口 同义）----
    term("transit-38", "transit", "上行", "shàng xíng", "扶梯旁边写着，往上走的那边"),
    term("transit-39", "transit", "下行", "xià xíng", "扶梯旁边写着，往下走的那边"),
    term("transit-40", "transit", "扶梯", "fú tī", "会自己动的楼梯，商场地铁里都有"),
    term("transit-41", "transit", "充值", "chōng zhí", "公交卡钱不够，来这里加钱"),
    term("transit-42", "transit", "时刻表", "shí kè biǎo", "站牌上写着头班末班几点，照着等车"),
    term("transit-43", "transit", "爱心专座", "ài xīn zhuān zuò", "车厢里那排黄座位，专门让给老人"),
    term("transit-44", "transit", "勿越黄线", "wù yuè huáng xiàn", "站台上黄线外的字，等车别踩过去"),
)

/* ==================== 医院（v17：原「药品说明」分区并入；含用药指令词） ==================== */

private val HOSPITAL = listOf(
    term("hospital-1", "hospital", "挂号处", "guà hào chù", "看病第一步，先来这里登记"),
    term("hospital-2", "hospital", "内科三楼", "nèi kē sān lóu", "看病要找对科室和楼层"),
    term("hospital-3", "hospital", "叫号", "jiào hào", "屏幕上出现您的号，就轮到您了"),
    term("hospital-4", "hospital", "取药窗口", "qǔ yào chuāng kǒu", "拿到药方后，来这里拿药"),
    term("hospital-7", "hospital", "门诊", "mén zhěn", "普通看病的地方"),
    term("hospital-8", "hospital", "急诊", "jí zhěn", "病得厉害，赶紧来这里"),
    term("hospital-9", "hospital", "住院", "zhù yuàn", "住在医院里治病"),
    term("hospital-10", "hospital", "出院", "chū yuàn", "病好了，可以回家"),
    term("hospital-11", "hospital", "病历本", "bìng lì běn", "记你看病史的小本"),
    term("hospital-12", "hospital", "处方", "chǔ fāng", "医生开的买药单子"),
    term("hospital-13", "hospital", "输液", "shū yè", "俗称打吊瓶"),
    term("hospital-14", "hospital", "量血压", "liáng xuè yā", "袖带绑胳膊上量一量"),
    term("hospital-15", "hospital", "测血糖", "cè xuè táng", "指尖扎一下测一测"),
    term("hospital-16", "hospital", "抽血", "chōu xuè", "化验前先抽一管血"),
    term("hospital-17", "hospital", "化验", "huà yàn", "血和尿送去检查"),
    term("hospital-18", "hospital", "拍片", "pāi piàn", "站着不动照个相"),
    term("hospital-19", "hospital", "彩超", "cǎi chāo", "肚子上抹油、滑滑的检查"),
    term("hospital-20", "hospital", "心电图", "xīn diàn tú", "检查心脏的曲线图"),
    term("hospital-21", "hospital", "医保卡", "yī bǎo kǎ", "看病能报销的卡"),
    term("hospital-22", "hospital", "报销", "bào xiāo", "花的钱能退一部分"),
    term("hospital-23", "hospital", "过敏", "guò mǐn", "有些药不能碰，要提前告诉医生"),
    term("hospital-25", "hospital", "感冒", "gǎn mào", "流鼻涕、打喷嚏"),
    term("hospital-26", "hospital", "头晕", "tóu yūn", "脑袋发昏站不稳"),
    term("hospital-27", "hospital", "复查", "fù chá", "过几天再来看一次"),
    term("hospital-28", "hospital", "候诊区", "hòu zhěn qū", "在这里坐着等叫号"),
    term("hospital-29", "hospital", "缴费处", "jiǎo fèi chù", "看病交钱的地方"),
    term("hospital-30", "hospital", "一楼大厅", "yī lóu dà tīng", "进门就是，挂着指示牌的大厅"),
    // ---- v17（2026-09-21 分区合并）：以下 35 条由 emergency、medicine 迁入 ----
    // ⚠️ id 保留原分区前缀（= 首次归属），`scene` 字段才是当前归属 —— 见文件头 id 契约。
    term("emergency-10", "hospital", "烫伤", "tàng shāng", "被热水、热油烫到了"),
    term("emergency-14", "hospital", "救护车", "jiù hù chē", "白车、红杠杠的车"),
    term("emergency-21", "hospital", "社区医院", "shè qū yī yuàn", "小区旁边的小医院"),
    term("emergency-22", "hospital", "药店", "yào diàn", "门口挂十字灯的店"),
    term("emergency-28", "hospital", "高血压", "gāo xuè yā", "血压高，天天按时吃药"),
    term("emergency-29", "hospital", "糖尿病", "táng niào bìng", "少吃甜，常测血糖"),
    term("medicine-1", "hospital", "药盒", "yào hé", "装药的盒子"),
    term("medicine-2", "hospital", "说明书", "shuō míng shū", "药盒里那张纸，吃前看一眼"),
    term("medicine-3", "hospital", "口服", "kǒu fú", "倒进嘴里咽下去"),
    term("medicine-4", "hospital", "外用", "wài yòng", "抹在皮肤上的，不能吃"),
    term("medicine-5", "hospital", "一日三次", "yī rì sān cì", "早中晚各吃一回"),
    term("medicine-6", "hospital", "一次一片", "yī cì yī piàn", "每回吃一片，别多吃"),
    term("medicine-7", "hospital", "饭后服用", "fàn hòu fú yòng", "吃完饭再吃药"),
    term("medicine-8", "hospital", "饭前服用", "fàn qián fú yòng", "吃饭前空腹吃"),
    term("medicine-9", "hospital", "睡前服用", "shuì qián fú yòng", "晚上睡觉前吃"),
    term("medicine-10", "hospital", "温水送服", "wēn shuǐ sòng fú", "用温开水咽下去"),
    term("medicine-11", "hospital", "遵医嘱", "zūn yī zhǔ", "医生说怎么吃，就怎么吃"),
    term("medicine-12", "hospital", "处方药", "chǔ fāng yào", "要医生开单才能买"),
    term("medicine-13", "hospital", "有效期", "yǒu xiào qī", "过了这个日子，不能再吃"),
    term("medicine-14", "hospital", "密封保存", "mì fēng bǎo cún", "盖子拧紧，别漏气"),
    term("medicine-15", "hospital", "阴凉处", "yīn liáng chù", "别晒太阳的地方存放"),
    term("medicine-16", "hospital", "不良反应", "bù liáng fǎn yìng", "吃了不舒服的表现"),
    term("medicine-17", "hospital", "禁忌", "jìn jì", "说明书上写着，哪些情况不能吃"),
    term("medicine-18", "hospital", "胶囊", "jiāo náng", "软软长条，装着药粉"),
    term("medicine-19", "hospital", "药片", "yào piàn", "圆圆的小药片，说明书上写着"),
    term("medicine-20", "hospital", "冲剂", "chōng jì", "倒进热水搅化再喝"),
    term("medicine-21", "hospital", "膏药", "gāo yào", "贴在疼的地方"),
    term("medicine-22", "hospital", "体温计", "tǐ wēn jì", "夹在胳膊底下量热度"),
    term("medicine-23", "hospital", "量体温", "liáng tǐ wēn", "夹五分钟，看看烧不烧"),
    term("medicine-24", "hospital", "退烧药", "tuì shāo yào", "烧得厉害才吃"),
    term("medicine-25", "hospital", "降压药", "jiàng yā yào", "血压高的人天天吃"),
    term("medicine-26", "hospital", "降糖药", "jiàng táng yào", "血糖高的人天天吃"),
    term("medicine-27", "hospital", "漏服", "lòu fú", "忘了吃一顿，别补双份"),
    term("medicine-28", "hospital", "过量", "guò liàng", "吃多了，会出危险"),
    term("medicine-29", "hospital", "摇匀", "yáo yún", "药水喝前晃一晃"),
)

/* ==================== 面馆（v18 由「吃饭」改名；scene id 仍为 food） ==================== */

private val FOOD = listOf(
    term("food-1", "food", "牛肉面", "niú ròu miàn", "最常见的一碗面"),
    term("food-2", "food", "微辣", "wēi là", "只有一点点辣"),
    term("food-4", "food", "买单", "mǎi dān", "吃完饭结账，就说这两个字"),
    term("food-5", "food", "小票", "xiǎo piào", "结账后的小纸条，留着对账"),
    term("food-6", "food", "菜单", "cài dān", "写着所有菜和价格的本子"),
    term("food-8", "food", "服务员", "fú wù yuán", "餐馆里端菜倒水的人"),
    term("food-10", "food", "米饭", "mǐ fàn", "一碗一碗的白饭"),
    term("food-11", "food", "饺子", "jiǎo zi", "皮包馅，过年常吃"),
    term("food-12", "food", "包子", "bāo zi", "早上蒸的一笼一笼"),
    term("food-13", "food", "馒头", "mán tou", "白白软软的蒸面"),
    term("food-14", "food", "稀饭", "xī fàn", "米熬的稀粥"),
    term("food-15", "food", "例汤", "lì tāng", "一碗一碗盛好的汤"),
    term("food-16", "food", "不辣", "bù là", "一点辣椒都不要"),
    term("food-18", "food", "清淡", "qīng dàn", "不油不咸的口味"),
    term("food-19", "food", "荤菜", "hūn cài", "有肉的菜"),
    term("food-20", "food", "素菜", "sù cài", "全是蔬菜的菜"),
    term("food-21", "food", "打包", "dǎ bāo", "吃不完，装起来带走"),
    term("food-22", "food", "堂食", "táng shí", "在店里坐着吃"),
    term("food-23", "food", "结账", "jié zhàng", "吃完去算总账"),
    term("food-24", "food", "发票", "fā piào", "报销要用的正式票据"),
    term("food-26", "food", "大碗", "dà wǎn", "分量大的那种碗，饭量大的点这个"),
    term("food-27", "food", "免费茶水", "miǎn fèi chá shuǐ", "不要钱的茶，自己倒"),
    term("food-28", "food", "加饭", "jiā fàn", "饭不够，再添一碗"),
    term("food-29", "food", "排队等位", "pái duì děng wèi", "人多，先拿号坐着等"),
    term("food-30", "food", "干净", "gān jìng", "桌子碗筷没油污"),
    // ---- 以下 33 条原为独立「面馆」分区（2026-09-20 同日并入本分区）----
    term("food-33", "food", "炸酱面", "zhá jiàng miàn", "肉末炒酱拌的面，有的店写成杂酱面"),
    term("food-34", "food", "米线", "mǐ xiàn", "大米做的细条，泡在汤里吃，不是面条"),
    term("food-35", "food", "抄手", "chāo shǒu", "成都人的叫法，皮包肉馅，就是馄饨"),
    term("food-36", "food", "鸡杂面", "jī zá miàn", "浇炒鸡杂的面，成都面馆常见的浇头"),
    term("food-37", "food", "排骨面", "pái gǔ miàn", "面上盖一块炖得软和的排骨"),
    term("food-38", "food", "肥肠面", "féi cháng miàn", "浇红烧肥肠的面，成都人好这一口"),
    term("food-39", "food", "担担面", "dàn dàn miàn", "干拌的麻辣面，不放汤，成都名小吃"),
    term("food-40", "food", "甜水面", "tián shuǐ miàn", "粗粗的筷子面，甜中带辣，成都特色"),
    term("food-41", "food", "酸辣粉", "suān là fěn", "红薯粉做的，又酸又辣，不算面条"),
    term("food-42", "food", "红油抄手", "hóng yóu chāo shǒu", "泡在红辣椒油里的抄手，很辣"),
    term("food-43", "food", "清汤抄手", "qīng tāng chāo shǒu", "不放辣椒的抄手，汤是清的"),
    term("food-44", "food", "卤蛋", "lǔ dàn", "酱油卤过的鸡蛋，点面时常添一个"),
    term("food-45", "food", "一两", "yī liǎng", "分量最小的一份，吃得少的点这个"),
    term("food-46", "food", "二两", "èr liǎng", "最常见的分量，一碗刚刚好"),
    term("food-47", "food", "三两", "sān liǎng", "分量最大的一份，干重活的点这个"),
    term("food-48", "food", "中辣", "zhōng là", "辣得适中，成都人的家常口味"),
    term("food-49", "food", "特辣", "tè là", "辣椒放得足，能吃辣的才点"),
    term("food-51", "food", "免青", "miǎn qīng", "面里不放青菜，青就是青菜"),
    term("food-52", "food", "加青", "jiā qīng", "青菜多抓一把，碗里绿油油的"),
    term("food-53", "food", "干拌", "gān bàn", "不要汤，调料直接拌在面里"),
    term("food-54", "food", "宽汤", "kuān tāng", "汤多舀一点，连汤带面一起喝"),
    term("food-56", "food", "点单", "diǎn dān", "客人说要吃啥，记在单子上"),
    term("food-57", "food", "出餐", "chū cān", "面煮好了，端出去给客人"),
    term("food-58", "food", "打包盒", "dǎ bāo hé", "带走装面、装抄手的白盒子"),
    term("food-60", "food", "消毒柜", "xiāo dú guì", "洗好的碗筷放里头消毒，烫手别碰"),
    term("food-61", "food", "健康证", "jiàn kāng zhèng", "在馆子上班要办的证，一年查一回"),
    term("food-62", "food", "留样", "liú yàng", "每样菜留一小盒放冰箱，备着检查"),
    term("food-63", "food", "打烊", "dǎ yàng", "就是关店收工，牌子一挂就不接客"),
    term("food-65", "food", "生熟分开", "shēng shú fēn kāi", "切生肉的刀和板，不能碰熟食"),
    // ---- v11 面馆补充词（2026-09-20，判据=该词是否会以文字形式出现）：现余 30 条 ----
    // ⚠️ v17 已迁出 17 条到 daily：原「调料罐 / 包装」整段 12 条（盐糖醋味精鸡精花椒胡椒
    //    酱油香油料酒淀粉豆瓣酱）+ 后厨物件 5 条（抹布 / 保鲜膜 / 牙签 / 纸巾 / 筷子）——
    //    判据同源（出现在瓶身 / 包装 / 标签上，且是家里厨房就会遇到的词，不是面馆专有）。
    // 菜单/加料栏
    term("food-78", "food", "香菜", "xiāng cài", "又叫芫荽，一小撮绿叶子"),
    term("food-79", "food", "葱花", "cōng huā", "切碎的葱，撒在面上"),
    term("food-80", "food", "泡菜", "pào cài", "免费的小碟腌萝卜，自己拿"),
    term("food-81", "food", "酸菜", "suān cài", "腌过的青菜，酸酸的"),
    term("food-82", "food", "煎蛋", "jiān dàn", "油锅里煎的鸡蛋，跟卤蛋不一样"),
    term("food-83", "food", "冰粉", "bīng fěn", "成都的甜凉粉，夏天吃"),
    term("food-84", "food", "凉糕", "liáng gāo", "凉凉的米糕，淋红糖水"),
    // 价目表规格
    term("food-85", "food", "小碗", "xiǎo wǎn", "分量小的那种碗，饭量小的点这个"),
    term("food-86", "food", "半份", "bàn fèn", "只要一半的量"),
    term("food-87", "food", "加面", "jiā miàn", "要加钱的一项，价目表上单列"),
    term("food-88", "food", "清汤", "qīng tāng", "不放辣椒的汤，汤是清的"),
    term("food-89", "food", "红汤", "hóng tāng", "放了辣椒油、红红的那种汤"),
    term("food-90", "food", "原汤", "yuán tāng", "煮面的本汤，不兑水"),
    // 墙面告示与证照
    term("food-91", "food", "招牌", "zhāo pái", "店门口挂的大牌子，也指最出名那道菜"),
    term("food-92", "food", "价目表", "jià mù biǎo", "墙上或柜台上写的价钱单"),
    term("food-93", "food", "营业中", "yíng yè zhōng", "灯牌亮着这几个字，就是还在卖"),
    term("food-94", "food", "自助调料", "zì zhù tiáo liào", "调料台，自己舀，不要钱"),
    term("food-95", "food", "明厨亮灶", "míng chú liàng zào", "厨房敞开、贴着这种牌子"),
    term("food-96", "food", "卫生许可证", "wèi shēng xǔ kě zhèng", "墙上挂的证，上头有店名"),
    // 后厨与桌前物件
    term("food-97", "food", "围裙", "wéi qún", "系在腰前的布，防油污"),
    term("food-101", "food", "一次性筷子", "yī cì xìng kuài zi", "用一回就扔的筷子，打包时给"),
    term("food-105", "food", "早餐", "zǎo cān", "早上吃的那一顿，招牌上写着"),
    term("food-106", "food", "火锅", "huǒ guō", "围着热锅涮着吃的，店门口大字写着"),
    term("food-107", "food", "油条", "yóu tiáo", "早上炸的长条面食，早餐摊招牌上写"),
    term("food-108", "food", "豆浆", "dòu jiāng", "黄豆磨的白色饮品，早餐摊上常见"),
    term("food-109", "food", "炒饭", "chǎo fàn", "锅里炒的饭，菜单上常见"),
    term("food-110", "food", "矿泉水", "kuàng quán shuǐ", "瓶装的水，小店冰柜里卖"),
    term("food-111", "food", "馄饨", "hún tun", "北方叫法，就是抄手，皮包馅带汤吃"),
    term("food-112", "food", "米粉", "mǐ fěn", "大米做的粉条，泡汤里吃，跟米线一路"),
    term("food-113", "food", "饮料", "yǐn liào", "瓶装的甜水，冰柜上写着这两个字"),
    // ---- v15 round5 收推荐档：现余 9 条（v17 删「外卖」1 条 —— 与 express-29 重复）----
    // 菜单/招牌分栏
    term("food-114", "food", "凉菜", "liáng cài", "菜单上分栏，不热的那几样"),
    term("food-115", "food", "热菜", "rè cài", "菜单上分栏，现炒现做的"),
    term("food-116", "food", "主食", "zhǔ shí", "菜单上分栏，饭和面都归这里"),
    term("food-117", "food", "小吃", "xiǎo chī", "店招牌上常见，粉面抄手这类"),
    term("food-118", "food", "快餐", "kuài cān", "招牌上写着，坐下就吃不用等"),
    term("food-119", "food", "家常菜", "jiā cháng cài", "招牌上写着，平常家里吃的那几样"),
    // 菜名前两字
    term("food-121", "food", "红烧", "hóng shāo", "菜名前两个字，酱油烧的，比如红烧肉"),
    term("food-122", "food", "清蒸", "qīng zhēng", "菜名前两个字，蒸出来的，不辣"),
    // 点餐与取餐
    term("food-124", "food", "扫码点餐", "sǎo mǎ diǎn cān", "桌上贴的，用手机扫一下自己点"),
)

/* ==================== 手机微信 ==================== */

private val PHONE = listOf(
    term("phone-1", "phone", "微信", "wēi xìn", "最常用的聊天软件"),
    term("phone-2", "phone", "语音通话", "yǔ yīn tōng huà", "按住说话，像对讲机"),
    term("phone-3", "phone", "视频通话", "shì pín tōng huà", "能看见人脸的通话"),
    term("phone-4", "phone", "接听", "jiē tīng", "电话响了，按绿色键"),
    term("phone-5", "phone", "挂断", "guà duàn", "按红色键，就挂了"),
    term("phone-6", "phone", "免提", "miǎn tí", "声音外放，不用贴耳朵"),
    term("phone-7", "phone", "消息", "xiāo xi", "别人发来的话"),
    term("phone-8", "phone", "发照片", "fā zhào piàn", "把相册里的图发给儿女"),
    term("phone-9", "phone", "发红包", "fā hóng bāo", "过年过节发个吉利钱"),
    term("phone-10", "phone", "收款码", "shōu kuǎn mǎ", "你的码，别人扫了给你钱"),
    term("phone-11", "phone", "付款码", "fù kuǎn mǎ", "结账时给店家扫的码"),
    term("phone-12", "phone", "朋友圈", "péng yǒu quān", "大家晒生活的地方"),
    term("phone-13", "phone", "点赞", "diǎn zàn", "按个小红心，表示喜欢"),
    term("phone-14", "phone", "无线网络", "wú xiàn wǎng luò", "连上它，上网不花钱"),
    term("phone-15", "phone", "流量", "liú liàng", "不连网络时上网用的量"),
    term("phone-16", "phone", "充电", "chōng diàn", "没电了插上线，慢慢充满"),
    term("phone-17", "phone", "充电器", "chōng diàn qì", "插头带线那个"),
    term("phone-18", "phone", "电量不足", "diàn liàng bù zú", "手机快没电了"),
    term("phone-19", "phone", "通讯录", "tōng xùn lù", "存电话号码的地方"),
    term("phone-20", "phone", "拨号", "bō hào", "打电话前按的数字键"),
    term("phone-21", "phone", "未接来电", "wèi jiē lái diàn", "没接上的电话，红点提醒"),
    term("phone-22", "phone", "锁屏", "suǒ píng", "屏幕黑了，就是锁上了"),
    term("phone-23", "phone", "音量调大", "yīn liàng tiáo dà", "听不清，就按音量加号"),
    term("phone-24", "phone", "字体调大", "zì tǐ tiáo dà", "字太小，去设置里调大"),
    term("phone-25", "phone", "保存图片", "bǎo cún tú piàn", "长按照片，就能存下来"),
    term("phone-26", "phone", "转发", "zhuǎn fā", "把消息发给另一个人"),
    term("phone-27", "phone", "语音输入", "yǔ yīn shū rù", "说话变文字，不用打字"),
    term("phone-28", "phone", "相册", "xiàng cè", "手机里存照片的地方"),
    term("phone-29", "phone", "手电筒", "shǒu diàn tǒng", "晚上照亮的那个灯"),
    term("phone-30", "phone", "关机", "guān jī", "长按电源键，手机休息"),
    term("phone-31", "phone", "重启", "chóng qǐ", "关了再开，卡了就试试"),
    term("phone-32", "phone", "骗子短信", "piàn zi duǎn xìn", "让你转账的，都是骗子"),
    // ---- v15 round5 收推荐档：现余 6 条（v17 删「转账」「余额」「垃圾短信」各 1 条，均与他处重复）----
    term("phone-33", "phone", "扫一扫", "sǎo yī sǎo", "微信里那个方框，对着码扫"),
    term("phone-35", "phone", "验证码", "yàn zhèng mǎ", "短信里那串数字，谁要都别给"),
    term("phone-36", "phone", "微信支付", "wēi xìn zhī fù", "结账时选这个，从微信里扣钱"),
    term("phone-37", "phone", "零钱", "líng qián", "微信里的钱袋子，收的红包在这"),
    term("phone-39", "phone", "发送", "fā sòng", "打完字按它，消息才发得出去"),
    term("phone-41", "phone", "设置", "shè zhì", "手机里调东西的地方，齿轮图标"),
    // ---- v17（2026-09-21 分区合并）：以下 5 条由 emergency 迁入 ----
    // ⚠️ id 保留原分区前缀（= 首次归属），`scene` 字段才是当前归属 —— 见文件头 id 契约。
    term("emergency-1", "phone", "紧急电话", "jǐn jí diàn huà", "有急事，就打这些号"),
    term("emergency-2", "phone", "报警电话", "bào jǐng diàn huà", "遇到坏人，打110"),
    term("emergency-3", "phone", "急救电话", "jí jiù diàn huà", "人不行了，打120"),
    term("emergency-4", "phone", "火警电话", "huǒ jǐng diàn huà", "着火了，打119"),
    term("emergency-17", "phone", "家人电话", "jiā rén diàn huà", "儿女电话，存手机里"),
)

/* ==================== 家电 ==================== */

private val APPLIANCE = listOf(
    // 电器名称
    term("appliance-1", "appliance", "冰箱", "bīng xiāng", "冷藏冷冻两用的大柜子"),
    term("appliance-2", "appliance", "洗衣机", "xǐ yī jī", "洗衣服的机器"),
    term("appliance-3", "appliance", "空调", "kōng tiáo", "夏天制冷、冬天制热"),
    term("appliance-4", "appliance", "电视机", "diàn shì jī", "看频道、看新闻的"),
    term("appliance-5", "appliance", "电饭锅", "diàn fàn guō", "煮饭的那口锅"),
    term("appliance-6", "appliance", "热水器", "rè shuǐ qì", "洗澡烧热水的"),
    term("appliance-68", "appliance", "电风扇", "diàn fēng shàn", "夏天吹风的机器，摇头送凉风"),
    term("appliance-73", "appliance", "电水壶", "diàn shuǐ hú", "烧开水的小壶，底座一按就烧"),
    term("appliance-74", "appliance", "微波炉", "wēi bō lú", "转盘热饭的机器，机身上写着名字"),
    // 面板按键
    term("appliance-7", "appliance", "电源", "diàn yuán", "机器上管通电的那个键"),
    term("appliance-8", "appliance", "开关", "kāi guān", "一按开、一按关"),
    term("appliance-9", "appliance", "指示灯", "zhǐ shì dēng", "亮着，说明机器在通电"),
    term("appliance-12", "appliance", "定时", "dìng shí", "定好几点开始、几点停"),
    term("appliance-14", "appliance", "自动", "zì dòng", "机器自己拿主意，不用管"),
    term("appliance-15", "appliance", "手动", "shǒu dòng", "得自己一下一下按"),
    term("appliance-64", "appliance", "烘干", "hōng gān", "把湿衣服烘干的档，洗衣机上有"),
    term("appliance-65", "appliance", "模式", "mó shì", "面板上的键，选机器怎么干活"),
    term("appliance-66", "appliance", "风速", "fēng sù", "风的大小，分高中低几档"),
    term("appliance-69", "appliance", "节能", "jié néng", "省电的那一档，机身上贴着这种标"),
    term("appliance-70", "appliance", "童锁", "tóng suǒ", "按住它锁住按键，小孩乱按也不起作用"),
    term("appliance-71", "appliance", "照明", "zhào míng", "油烟机、微波炉上的灯，按一下就亮"),
    // 遥控器与电视
    term("appliance-16", "appliance", "遥控器", "yáo kòng qì", "手里那个长条的按键板"),
    term("appliance-17", "appliance", "频道", "pín dào", "台号，按加号换下一个台"),
    term("appliance-18", "appliance", "音量", "yīn liàng", "声音的大小"),
    term("appliance-19", "appliance", "静音", "jìng yīn", "一点声音都没有"),
    term("appliance-20", "appliance", "信号源", "xìn hào yuán", "电视上换着看，机顶盒还是影碟机"),
    term("appliance-67", "appliance", "电池", "diàn chí", "遥控器后盖里装的，没电了换新的"),
    // 说明书与铭牌
    term("appliance-25", "appliance", "保修卡", "bǎo xiū kǎ", "坏了免费修，要凭这张卡"),
    term("appliance-26", "appliance", "合格证", "hé gé zhèng", "说明书里的一张纸，证明东西是好的"),
    term("appliance-27", "appliance", "型号", "xíng hào", "一长串字母数字，报修要报它"),
    term("appliance-28", "appliance", "功率", "gōng lǜ", "费不费电，看这个数"),
    term("appliance-29", "appliance", "能效标识", "néng xiào biāo shí", "绿黄红条的贴纸，绿的最省电"),
    // 安全警示
    term("appliance-30", "appliance", "当心触电", "dāng xīn chù diàn", "别用手碰这里，会电到人"),
    term("appliance-31", "appliance", "禁止覆盖", "jìn zhǐ fù gài", "上面别盖布、别捂东西"),
    term("appliance-32", "appliance", "请勿用水冲洗", "qǐng wù yòng shuǐ chōng xǐ", "不能拿水浇着洗"),
    term("appliance-33", "appliance", "小心烫手", "xiǎo xīn tàng shǒu", "这里热，别直接上手摸"),
    term("appliance-34", "appliance", "接地线", "jiē dì xiàn", "通到地里的那根线，防漏电"),
    term("appliance-35", "appliance", "漏电保护", "lòu diàn bǎo hù", "漏了电，它会自己断掉"),
    // 电源与插座
    term("appliance-36", "appliance", "插头", "chā tóu", "插进插座的那一头"),
    term("appliance-37", "appliance", "插座", "chā zuò", "墙上带电的孔"),
    term("appliance-38", "appliance", "电源线", "diàn yuán xiàn", "连着插头的那根电线"),
    // 冰箱
    term("appliance-39", "appliance", "冷藏室", "lěng cáng shì", "冰箱上面不结冰的那层"),
    term("appliance-40", "appliance", "冷冻室", "lěng dòng shì", "冻肉、冻冰棍的那层"),
    term("appliance-41", "appliance", "除霜", "chú shuāng", "把里面的结冰清掉"),
    term("appliance-42", "appliance", "温度调节", "wēn dù tiáo jié", "转一下，调冷调热"),
    term("appliance-72", "appliance", "保鲜", "bǎo xiān", "冰箱上面那格，菜放这里不容易坏"),
    // 洗衣机
    term("appliance-43", "appliance", "洗涤", "xǐ dí", "洗的那一段"),
    term("appliance-44", "appliance", "漂洗", "piǎo xǐ", "用清水把泡沫冲干净"),
    term("appliance-45", "appliance", "脱水", "tuō shuǐ", "转起来把水甩干"),
    term("appliance-46", "appliance", "水位", "shuǐ wèi", "洗衣机放多少水，分高中低三档"),
    term("appliance-47", "appliance", "快洗", "kuài xǐ", "十几分钟就洗完的那一档"),
    // 空调
    term("appliance-48", "appliance", "制冷", "zhì lěng", "出冷风，夏天用"),
    term("appliance-49", "appliance", "制热", "zhì rè", "出热风，冬天用"),
    term("appliance-50", "appliance", "除湿", "chú shī", "抽走潮气，屋里不黏"),
    term("appliance-51", "appliance", "送风", "sòng fēng", "只吹风，不冷也不热"),
    // 微波炉与烤箱
    term("appliance-52", "appliance", "微波", "wēi bō", "转起来热饭的那一档"),
    term("appliance-53", "appliance", "烧烤", "shāo kǎo", "上面烤，表面烤得焦香"),
    term("appliance-54", "appliance", "解冻", "jiě dòng", "把冻硬的肉化开"),
    term("appliance-55", "appliance", "火力", "huǒ lì", "大小火，热得快慢不一样"),
    // 电饭锅
    term("appliance-56", "appliance", "煮饭", "zhǔ fàn", "按了它就自己把饭煮熟"),
    term("appliance-57", "appliance", "煮粥", "zhǔ zhōu", "熬稀饭那一档，费时间"),
    term("appliance-58", "appliance", "保温", "bǎo wēn", "饭熟了不凉，一直温着"),
    // 热水器与灶具
    term("appliance-59", "appliance", "加热", "jiā rè", "把水烧热"),
    term("appliance-60", "appliance", "水温", "shuǐ wēn", "热水器出来的水有多热"),
    term("appliance-61", "appliance", "油烟机", "yóu yān jī", "灶台上方抽烟的机器"),
    term("appliance-62", "appliance", "燃气灶", "rán qì zào", "点火的灶，用完关阀门"),
    term("appliance-63", "appliance", "电磁炉", "diàn cí lú", "平板上烧锅的那种灶"),
    // ---- v15 round5 收推荐档：现余 6 条（v17 迁出「待机/开始/取消」3 条到 daily，删「生产日期」1 条）----
    // 通用面板按键
    term("appliance-78", "appliance", "清洁", "qīng jié", "洗衣机、油烟机上的档位，专门洗机器"),
    term("appliance-85", "appliance", "温度", "wēn dù", "冷热看这个数，按加号调高"),
    // 洗衣机 / 冰箱 / 空调专有
    term("appliance-79", "appliance", "速冻", "sù dòng", "冰箱上那个键，让东西冻得快"),
    term("appliance-80", "appliance", "标准洗", "biāo zhǔn xǐ", "洗衣机最常用的那档，平常衣服都用它"),
    term("appliance-83", "appliance", "睡眠", "shuì mián", "空调上那个键，风小了、不吵人"),
    // 铭牌与说明书
    term("appliance-88", "appliance", "客服电话", "kè fú diàn huà", "坏了就打这个号，说明书上印着"),
)

/**
 * 全库词条（**按 `SCENES` 的固有顺序拼装**）。
 * ⚠️ v17 起：各列表的成员**不再等同于其 `scene`**（搬迁不改 id、只改 `scene`，见文件头 id 契约）——
 *   这里的拼接顺序只影响「无意义时的遍历序」（新词池会洗牌、池型频道会加权重排），
 *   **归属一律以每条自己的 `scene` 为准**。
 */
val STUDY_TERMS: List<Term> =
    DAILY_COMMON + TRANSIT + HOSPITAL + FOOD + PHONE + APPLIANCE
