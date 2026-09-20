// ⚠️ 原稿留档，勿直接粘贴 —— 粘贴前必须：
//   ① 确认 WordBank.kt 未被他人改动（重跑 check_wordbank_invariants.py 取真值）
//   ② 确认 id 起点仍是各分区当前最大 +1
//   ③ 落地后同步 4 处计数点（wordbank-guidelines.md §9）

// ---- 常用词（daily）新增 10 条，建议接在 DAILY 列表末尾 ----
    term("daily-28", "daily", "推", "tuī", "门上的字，往外推才开"),
    term("daily-29", "daily", "拉", "lā", "门上的字，往里拉才开"),
    term("daily-30", "daily", "男", "nán", "男厕所门上的字"),
    term("daily-31", "daily", "女", "nǚ", "女厕所门上的字"),
    term("daily-32", "daily", "开", "kāi", "老式插座、电器面板上标的「开」那一档"),
    term("daily-33", "daily", "关", "guān", "老式插座、电器面板上标的「关」那一档"),
    term("daily-34", "daily", "左", "zuǒ", "门上、旋钮上标的方向，跟「右」相反"),  // 可选
    term("daily-35", "daily", "右", "yòu", "门上、旋钮上标的方向，跟「左」相反"),  // 可选
    term("daily-36", "daily", "免费", "miǎn fèi", "不要钱的，停车、量血压都见过"),
    term("daily-37", "daily", "收费", "shōu fèi", "要交钱的，公厕、停车场门口挂着"),

// ---- 公交地铁（transit）新增 10 条，建议接在 TRANSIT 列表末尾 ----
    term("transit-38", "transit", "上行", "shàng xíng", "扶梯旁边写着，往上走的那边"),
    term("transit-39", "transit", "下行", "xià xíng", "扶梯旁边写着，往下走的那边"),
    term("transit-40", "transit", "扶梯", "fú tī", "会自己动的楼梯，商场地铁里都有"),
    term("transit-41", "transit", "充值", "chōng zhí", "公交卡钱不够，来这里加钱"),
    term("transit-42", "transit", "时刻表", "shí kè biǎo", "站牌上写着首班末班几点，照着它等车"),
    term("transit-43", "transit", "爱心专座", "ài xīn zhuān zuò", "车厢里那排黄座位，专门让给老人"),
    term("transit-44", "transit", "勿越黄线", "wù yuè huáng xiàn", "站台上黄线外的字，等车别踩过去"),
    term("transit-45", "transit", "自动售票", "zì dòng shòu piào", "机器上写着，自己投钱拿票"),  // 可选
    term("transit-46", "transit", "单程票", "dān chéng piào", "只坐一趟的票，机器上能买"),  // 可选
    term("transit-47", "transit", "紧急出口", "jǐn jí chū kǒu", "出事时从这儿出去，绿牌子"),

// ---- 吃饭（food）新增 12 条，建议接在 FOOD 列表末尾 ----
    term("food-114", "food", "凉菜", "liáng cài", "菜单上分栏，不热的那几样"),
    term("food-115", "food", "热菜", "rè cài", "菜单上分栏，现炒现做的"),
    term("food-116", "food", "主食", "zhǔ shí", "菜单上分栏，饭和面都归这里"),
    term("food-117", "food", "小吃", "xiǎo chī", "店招牌上常见，粉面抄手这类"),
    term("food-118", "food", "快餐", "kuài cān", "招牌上写着，坐下就吃不用等"),
    term("food-119", "food", "家常菜", "jiā cháng cài", "招牌上写着，平常家里吃的那几样"),
    term("food-120", "food", "川菜", "chuān cài", "招牌上写着，四川口味的馆子"),  // 可选
    term("food-121", "food", "红烧", "hóng shāo", "菜名前两个字，酱油烧的，比如红烧肉"),
    term("food-122", "food", "清蒸", "qīng zhēng", "菜名前两个字，蒸出来的，不辣"),
    term("food-123", "food", "凉拌", "liáng bàn", "菜名前两个字，拌好就上，不加热"),  // 可选
    term("food-124", "food", "扫码点餐", "sǎo mǎ diǎn cān", "桌上贴的，用手机扫一下自己点"),
    term("food-125", "food", "外卖", "wài mài", "门口贴着，让骑手取餐的地方"),

// ---- 手机微信（phone）新增 11 条，建议接在 PHONE 列表末尾 ----
    term("phone-33", "phone", "扫一扫", "sǎo yī sǎo", "微信里那个方框，对着码扫"),
    term("phone-34", "phone", "转账", "zhuǎn zhàng", "把钱转给别人，认准了再按"),
    term("phone-35", "phone", "验证码", "yàn zhèng mǎ", "短信里那串数字，谁要都别给"),
    term("phone-36", "phone", "微信支付", "wēi xìn zhī fù", "结账时选这个，从微信里扣钱"),
    term("phone-37", "phone", "零钱", "líng qián", "微信里的钱袋子，收的红包在这"),
    term("phone-38", "phone", "余额", "yú é", "还剩多少钱，数字在这儿写着"),
    term("phone-39", "phone", "发送", "fā sòng", "打完字按它，消息才发得出去"),
    term("phone-40", "phone", "截图", "jié tú", "把屏幕照下来，存成一张图"),  // 可选
    term("phone-41", "phone", "设置", "shè zhì", "手机里调东西的地方，齿轮图标"),
    term("phone-42", "phone", "拒接", "jù jiē", "不想接，按红色键挂掉"),  // 可选
    term("phone-43", "phone", "垃圾短信", "lā jī duǎn xìn", "广告和骗子发来的，直接删"),

// ---- 家电（appliance）新增 14 条，建议接在 APPLIANCE 列表末尾 ----
    term("appliance-75", "appliance", "待机", "dài jī", "机器歇着没干活，按一下就能用"),
    term("appliance-76", "appliance", "开始", "kāi shǐ", "按这个键，机器就动起来"),
    term("appliance-77", "appliance", "取消", "qǔ xiāo", "按错了就按它，重新来"),
    term("appliance-78", "appliance", "清洁", "qīng jié", "洗衣机、油烟机上的档位，专门洗机器"),
    term("appliance-79", "appliance", "速冻", "sù dòng", "冰箱上那个键，让东西冻得快"),
    term("appliance-80", "appliance", "标准洗", "biāo zhǔn xǐ", "洗衣机最常用的那档，平常衣服都用它"),
    term("appliance-81", "appliance", "强力", "qiáng lì", "洗得狠的那一档，脏衣服用"),  // 可选
    term("appliance-82", "appliance", "轻柔", "qīng róu", "洗得轻的那一档，毛衣用"),  // 可选
    term("appliance-83", "appliance", "睡眠", "shuì mián", "空调上那个键，风小了、不吵人"),
    term("appliance-84", "appliance", "摆风", "bǎi fēng", "空调叶片来回摆，风不冲着人吹"),  // 可选
    term("appliance-85", "appliance", "温度", "wēn dù", "冷热看这个数，按加号调高"),
    term("appliance-86", "appliance", "额定电压", "é dìng diàn yā", "铭牌上那行，220 伏就是它"),  // 可选
    term("appliance-87", "appliance", "生产日期", "shēng chǎn rì qī", "铭牌上写着，哪天造的"),
    term("appliance-88", "appliance", "客服电话", "kè fú diàn huà", "坏了好打这个号，说明书上印着"),

