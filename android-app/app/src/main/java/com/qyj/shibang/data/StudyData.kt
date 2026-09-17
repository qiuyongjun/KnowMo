package com.qyj.shibang.data

import androidx.compose.ui.graphics.Color

/** 单字 + 拼音 */
data class TermChar(val c: String, val p: String)

/** 学习单元 = 生活词组（不是单字） */
data class Term(
    val id: String,
    val scene: String,
    val icon: String,
    val text: String,
    val chars: List<TermChar>,
    val tip: String,
    val kind: Kind,
    val days: Int = 1,
) {
    enum class Kind { NEW, REVIEW }
}

data class Scene(val id: String, val name: String, val icon: String, val color: Color)

val SCENES = listOf(
    Scene("rec", "推荐", "⭐", Color(0xFFEDE7F6)),
    Scene("market", "买菜", "🛒", Color(0xFFE8F5E9)),
    Scene("transit", "公交地铁", "🚌", Color(0xFFE3F2FD)),
    Scene("hospital", "医院", "🏥", Color(0xFFFFEBEE)),
    Scene("bank", "银行", "🏦", Color(0xFFFFF8E1)),
    Scene("gov", "办事", "🏛️", Color(0xFFF3E5F5)),
    Scene("food", "吃饭", "🍜", Color(0xFFFFF3E0)),
)

val STUDY_TERMS = listOf(
    // 买菜
    Term("market-1", "market", "🛒", "今日特价",
        listOf(TermChar("今", "jīn"), TermChar("日", "rì"), TermChar("特", "tè"), TermChar("价", "jià")),
        "超市里便宜的菜，会挂这四个字的牌子", Term.Kind.REVIEW, 7),
    Term("market-2", "market", "🛒", "扫码付款",
        listOf(TermChar("扫", "sǎo"), TermChar("码", "mǎ"), TermChar("付", "fù"), TermChar("款", "kuǎn")),
        "用手机对准方块图案，就能付钱", Term.Kind.NEW),
    Term("market-3", "market", "🛒", "收银台",
        listOf(TermChar("收", "shōu"), TermChar("银", "yín"), TermChar("台", "tái")),
        "排队交钱的地方", Term.Kind.NEW),
    Term("market-4", "market", "🛒", "请排队",
        listOf(TermChar("请", "qǐng"), TermChar("排", "pái"), TermChar("队", "duì")),
        "看到这三个字，就等前面的人办完", Term.Kind.NEW),
    Term("market-5", "market", "🛒", "三块五一斤",
        listOf(TermChar("三", "sān"), TermChar("块", "kuài"), TermChar("五", "wǔ"), TermChar("一", "yī"), TermChar("斤", "jīn")),
        "菜价牌上写着多少钱一斤", Term.Kind.NEW),
    Term("market-6", "market", "🛒", "打折",
        listOf(TermChar("打", "dǎ"), TermChar("折", "zhé")),
        "便宜了，可以买", Term.Kind.NEW),
    // 公交地铁
    Term("transit-1", "transit", "🚇", "地铁站",
        listOf(TermChar("地", "dì"), TermChar("铁", "tiě"), TermChar("站", "zhàn")),
        "看到这仨字，就是坐地铁的地方", Term.Kind.REVIEW, 1),
    Term("transit-2", "transit", "🚌", "公交站",
        listOf(TermChar("公", "gōng"), TermChar("交", "jiāo"), TermChar("站", "zhàn")),
        "等公共汽车的牌子", Term.Kind.NEW),
    Term("transit-3", "transit", "🚌", "从前门上车",
        listOf(TermChar("从", "cóng"), TermChar("前", "qián"), TermChar("门", "mén"), TermChar("上", "shàng"), TermChar("车", "chē")),
        "坐公交车，要从前门上车", Term.Kind.NEW),
    Term("transit-4", "transit", "🚇", "出口",
        listOf(TermChar("出", "chū"), TermChar("口", "kǒu")),
        "从这扇门出去", Term.Kind.NEW),
    Term("transit-5", "transit", "🚇", "换乘",
        listOf(TermChar("换", "huàn"), TermChar("乘", "chéng")),
        "在这里换另一趟车", Term.Kind.NEW),
    Term("transit-6", "transit", "🚌", "下一站",
        listOf(TermChar("下", "xià"), TermChar("一", "yī"), TermChar("站", "zhàn")),
        "广播说这三个字后，就是站名了", Term.Kind.NEW),
    // 医院
    Term("hospital-1", "hospital", "🏥", "挂号处",
        listOf(TermChar("挂", "guà"), TermChar("号", "hào"), TermChar("处", "chù")),
        "看病第一步，先来这里登记", Term.Kind.REVIEW, 3),
    Term("hospital-2", "hospital", "🏥", "内科三楼",
        listOf(TermChar("内", "nèi"), TermChar("科", "kē"), TermChar("三", "sān"), TermChar("楼", "lóu")),
        "看病要找对科室和楼层", Term.Kind.NEW),
    Term("hospital-3", "hospital", "🏥", "叫号",
        listOf(TermChar("叫", "jiào"), TermChar("号", "hào")),
        "屏幕上出现您的号，就轮到您了", Term.Kind.NEW),
    Term("hospital-4", "hospital", "🏥", "取药窗口",
        listOf(TermChar("取", "qǔ"), TermChar("药", "yào"), TermChar("窗", "chuāng"), TermChar("口", "kǒu")),
        "拿到药方后，来这里拿药", Term.Kind.NEW),
    Term("hospital-5", "hospital", "🏥", "饭后吃药",
        listOf(TermChar("饭", "fàn"), TermChar("后", "hòu"), TermChar("吃", "chī"), TermChar("药", "yào")),
        "吃完饭半小时再吃药", Term.Kind.NEW),
    Term("hospital-6", "hospital", "🏥", "打针",
        listOf(TermChar("打", "dǎ"), TermChar("针", "zhēn")),
        "护士扎一针，很快就好", Term.Kind.NEW),
    // 银行
    Term("bank-1", "bank", "🏧", "取款机",
        listOf(TermChar("取", "qǔ"), TermChar("款", "kuǎn"), TermChar("机", "jī")),
        "从这台机器里取钱", Term.Kind.NEW),
    Term("bank-2", "bank", "🏦", "请输入密码",
        listOf(TermChar("请", "qǐng"), TermChar("输", "shū"), TermChar("入", "rù"), TermChar("密", "mì"), TermChar("码", "mǎ")),
        "按您自己设的六个数字", Term.Kind.NEW),
    Term("bank-3", "bank", "🏦", "排队取号",
        listOf(TermChar("排", "pái"), TermChar("队", "duì"), TermChar("取", "qǔ"), TermChar("号", "hào")),
        "先拿一张号，等着叫号", Term.Kind.NEW),
    Term("bank-4", "bank", "🏦", "三号窗口",
        listOf(TermChar("三", "sān"), TermChar("号", "hào"), TermChar("窗", "chuāng"), TermChar("口", "kǒu")),
        "柜台按号码办业务", Term.Kind.REVIEW, 7),
    Term("bank-5", "bank", "🏦", "回执单",
        listOf(TermChar("回", "huí"), TermChar("执", "zhí"), TermChar("单", "dān")),
        "办完事给的纸条，要收好", Term.Kind.NEW),
    Term("bank-6", "bank", "🏦", "存款",
        listOf(TermChar("存", "cún"), TermChar("款", "kuǎn")),
        "把钱交给银行保管", Term.Kind.NEW),
    // 办事
    Term("gov-1", "gov", "🏛️", "办事大厅",
        listOf(TermChar("办", "bàn"), TermChar("事", "shì"), TermChar("大", "dà"), TermChar("厅", "tīng")),
        "办证件、盖章都在这里", Term.Kind.NEW),
    Term("gov-2", "gov", "🪪", "身份证",
        listOf(TermChar("身", "shēn"), TermChar("份", "fèn"), TermChar("证", "zhèng")),
        "最重要的证件，出门要带", Term.Kind.NEW),
    Term("gov-3", "gov", "🏛️", "请签字",
        listOf(TermChar("请", "qǐng"), TermChar("签", "qiān"), TermChar("字", "zì")),
        "在这里写您的名字", Term.Kind.REVIEW, 3),
    Term("gov-4", "gov", "🏛️", "盖公章",
        listOf(TermChar("盖", "gài"), TermChar("公", "gōng"), TermChar("章", "zhāng")),
        "工作人员盖章的地方", Term.Kind.NEW),
    Term("gov-5", "gov", "🖨️", "复印件",
        listOf(TermChar("复", "fù"), TermChar("印", "yìn"), TermChar("件", "jiàn")),
        "证件的复印件，办事常常要", Term.Kind.NEW),
    Term("gov-6", "gov", "📝", "填表",
        listOf(TermChar("填", "tián"), TermChar("表", "biǎo")),
        "按格子把信息写进去", Term.Kind.NEW),
    // 吃饭
    Term("food-1", "food", "🍜", "牛肉面",
        listOf(TermChar("牛", "niú"), TermChar("肉", "ròu"), TermChar("面", "miàn")),
        "最常见的一碗面", Term.Kind.NEW),
    Term("food-2", "food", "🌶️", "微辣",
        listOf(TermChar("微", "wēi"), TermChar("辣", "là")),
        "只有一点点辣", Term.Kind.NEW),
    Term("food-3", "food", "🥢", "加一双筷子",
        listOf(TermChar("加", "jiā"), TermChar("一", "yī"), TermChar("双", "shuāng"), TermChar("筷", "kuài"), TermChar("子", "zi")),
        "请服务员多拿一双筷子", Term.Kind.NEW),
    Term("food-4", "food", "🧾", "买单",
        listOf(TermChar("买", "mǎi"), TermChar("单", "dān")),
        "吃完饭结账，就说这两个字", Term.Kind.REVIEW, 1),
    Term("food-5", "food", "🧾", "小票",
        listOf(TermChar("小", "xiǎo"), TermChar("票", "piào")),
        "结账后的小纸条，留着对账", Term.Kind.NEW),
    Term("food-6", "food", "📖", "菜单",
        listOf(TermChar("菜", "cài"), TermChar("单", "dān")),
        "写着所有菜和价格的本子", Term.Kind.NEW),
)

/** 推荐频道：复习与新学交错（记忆曲线混排演示） */
val REC_QUEUE = listOf("transit-1", "transit-2", "hospital-1", "hospital-2", "market-1", "market-2")

fun sceneColor(id: String): Color = SCENES.firstOrNull { it.id == id }?.color ?: Color(0xFFF5F7FA)
fun sceneName(id: String): String = SCENES.firstOrNull { it.id == id }?.name ?: ""
