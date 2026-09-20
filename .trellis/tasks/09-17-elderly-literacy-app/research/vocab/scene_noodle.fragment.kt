// ===================================================================
// 面馆词条 · 创作原稿（2026-09-20）
//
// ⚠️ 这是**留档**，不是待接入的补丁。**不要直接粘贴进源码**。
//    原方案是新增独立分区 `noodle`（34 条），该方案已**撤销**；
//    现行落位 = 33 条并入「吃饭」(`food`)，id 为 `food-33` … `food-65`
//    （「微辣」因与既有 `food-2` 重复而剔除）。沿革见 scene_noodle.md §6。
//    现役不变量检查脚本 = check_wordbank_invariants.py。
//
// 本文件的价值：记录 34 条原稿的**文字/拼音/tip 原文**，是并入内容的比对基线。
// 词条 id 约定：一经发布永不复用（学习状态 TermState 按 id 挂靠）。
// ===================================================================

/* ==================== 面馆 ==================== */

private val NOODLE = listOf(
    term("noodle-1", "noodle", "炸酱面", "zhá jiàng miàn", "肉末炒酱拌的面，有的店写成杂酱面"),
    term("noodle-2", "noodle", "米线", "mǐ xiàn", "大米做的细条，泡在汤里吃，不是面条"),
    term("noodle-3", "noodle", "抄手", "chāo shǒu", "成都人的叫法，皮包肉馅，就是馄饨"),
    term("noodle-4", "noodle", "鸡杂面", "jī zá miàn", "浇炒鸡杂的面，成都面馆常见的浇头"),
    term("noodle-5", "noodle", "排骨面", "pái gǔ miàn", "面上盖一块炖得软和的排骨"),
    term("noodle-6", "noodle", "肥肠面", "féi cháng miàn", "浇红烧肥肠的面，成都人好这一口"),
    term("noodle-7", "noodle", "担担面", "dàn dàn miàn", "干拌的麻辣面，不放汤，成都名小吃"),
    term("noodle-8", "noodle", "甜水面", "tián shuǐ miàn", "粗粗的筷子面，甜中带辣，成都特色"),
    term("noodle-9", "noodle", "酸辣粉", "suān là fěn", "红薯粉做的，又酸又辣，不算面条"),
    term("noodle-10", "noodle", "红油抄手", "hóng yóu chāo shǒu", "泡在红辣椒油里的抄手，很辣"),
    term("noodle-11", "noodle", "清汤抄手", "qīng tāng chāo shǒu", "不放辣椒，汤是清的，老人娃娃爱吃"),
    term("noodle-12", "noodle", "卤蛋", "lǔ dàn", "酱油卤过的鸡蛋，点面时常添一个"),
    term("noodle-13", "noodle", "一两", "yī liǎng", "分量最小的一份，吃得少的点这个"),
    term("noodle-14", "noodle", "二两", "èr liǎng", "最常见的分量，一碗刚刚好"),
    term("noodle-15", "noodle", "三两", "sān liǎng", "分量最大的一份，干重活的点这个"),
    term("noodle-16", "noodle", "微辣", "wēi là", "只放一点点辣椒，尝得出、不冲人"),
    term("noodle-17", "noodle", "中辣", "zhōng là", "辣得适中，成都人的家常口味"),
    term("noodle-18", "noodle", "特辣", "tè là", "辣椒放得足，能吃辣的才点"),
    term("noodle-19", "noodle", "不要辣", "bù yào là", "一点辣椒都不放，直接说一声就行"),
    term("noodle-20", "noodle", "免青", "miǎn qīng", "面里不放青菜。青，就指碗里的青菜"),
    term("noodle-21", "noodle", "加青", "jiā qīng", "青菜多抓一把，碗里绿油油的"),
    term("noodle-22", "noodle", "干拌", "gān bàn", "不要汤，调料直接拌在面里"),
    term("noodle-23", "noodle", "宽汤", "kuān tāng", "汤多舀一点，连汤带面一起喝"),
    term("noodle-24", "noodle", "加个蛋", "jiā gè dàn", "客人想再添一个卤蛋"),
    term("noodle-25", "noodle", "点单", "diǎn dān", "客人说要吃啥，记在单子上"),
    term("noodle-26", "noodle", "出餐", "chū cān", "面煮好了，端出去给客人"),
    term("noodle-27", "noodle", "打包盒", "dǎ bāo hé", "带走装面、装抄手的白盒子"),
    term("noodle-28", "noodle", "后厨", "hòu chú", "煮面炒臊子的地方，客人不进去"),
    term("noodle-29", "noodle", "消毒柜", "xiāo dú guì", "洗好的碗筷放里头消毒，烫手别碰"),
    term("noodle-30", "noodle", "健康证", "jiàn kāng zhèng", "在馆子上班要办的证，一年查一回"),
    term("noodle-31", "noodle", "留样", "liú yàng", "每样菜留一小盒放冰箱，备着检查"),
    term("noodle-32", "noodle", "打烊", "dǎ yàng", "就是关店收工，牌子一挂就不接客"),
    term("noodle-33", "noodle", "请勿吸烟", "qǐng wù xī yān", "墙上贴的字，店里不准抽烟"),
    term("noodle-34", "noodle", "生熟分开", "shēng shú fēn kāi", "切生肉的刀和板，不能碰熟食"),
)

// -------------------------------------------------------------------
// 【已作废的接入指引 —— 仅作历史留档，不要执行】
//
// 原方案（独立分区，已撤销）是：
//   ① StudyData.kt 插 Scene("noodle", "面馆", "🥢", Color(0xFFF9FBE7))
//   ② WordBank.kt 插入上面的 NOODLE 列表
//   ③ WordBank.kt 的 STUDY_TERMS 改为 … + FOOD + NOODLE + …
//   ④ README.md 计数同步为「13 个场景 402 条」+ 频道清单补「面馆」
//
// 按此执行会把词库**回退**成 13 场景 / 402 条（而且会重新引入
// 「微辣」在 `food` 内的重复）。**现行落位见 scene_noodle.md §6。**
//
// 上面 34 条 term(...) 中，`noodle-16`（微辣）未被采用；
// 其余 33 条已按 `food-33` … `food-65` 的编号并入 `food` 列表末尾。
// -------------------------------------------------------------------
