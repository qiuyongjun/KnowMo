# 拼音逐条判定

> 数据约定（已从词库自身证实）：`豆腐 → dòu fu`、`便宜 → pián yi`、`太贵了 → tài guì le`
> —— **无调号即表示轻声**。因此凡是无声调却不该读轻声的字，就是漏标调号。

## 1. 与 pypinyin 的逐字差异（共 11 处）

| id | 词条 | 字 | GLM | pypinyin | 谁是错的 | 依据 |
|---|---|---|---|---|---|---|
| market-11 | 豆腐 | 腐 | fu | |  |
| transit-30 | 司机师傅 | 傅 | fu | |  |
| phone-7 | 消息 | 息 | xi | |  |
| phone-8 | 发照片 | 片 | piàn | |  |
| phone-23 | 音量调大 | 调 | tiáo | |  |
| phone-24 | 字体调大 | 调 | tiáo | |  |
| express-16 | 扫码出库 | 码 | má | |  |
| emergency-9 | 流血了 | 血 | xuè | |  |
| emergency-15 | 不要乱动 | 不 | bù | |  |
| weather-9 | 晚上 | 上 | shang | |  |
| weather-10 | 早晨 | 晨 | chen | |  |

## 2. 全库「无声调」拼音清单（约定应为轻声，逐条确认）

| id | 词条 | 字 | 标音 | 是否真为轻声 |
|---|---|---|---|---|
| market-11 | 豆腐 | 腐 | fu | |
| market-16 | 便宜 | 宜 | yi | |
| market-17 | 太贵了 | 了 | le | |
| transit-24 | 到站了 | 了 | le | |
| transit-30 | 司机师傅 | 傅 | fu | |
| food-3 | 加一双筷子 | 子 | zi | |
| food-11 | 饺子 | 子 | zi | |
| food-12 | 包子 | 子 | zi | |
| food-13 | 馒头 | 头 | tou | |
| food-25 | 勺子 | 子 | zi | |
| phone-7 | 消息 | 息 | xi | |
| phone-32 | 骗子短信 | 子 | zi | |
| property-14 | 灯泡坏了 | 了 | le | |
| property-19 | 钥匙 | 匙 | shi | |
| emergency-6 | 着火了 | 了 | le | |
| emergency-7 | 摔倒了 | 了 | le | |
| emergency-9 | 流血了 | 了 | le | |
| weather-9 | 晚上 | 上 | shang | |
| weather-10 | 早晨 | 晨 | chen | |

> 共 19 处。除「早晨·晨」外，其余均需人工过一眼。

## 3. 待修清单

| id | 词条 | 字 | 现值 | 改为 |
|---|---|---|---|---|
| express-16 | 扫码出库 | 码 | má | **mǎ** |
| weather-10 | 早晨 | 晨 | chen | **chén** |
