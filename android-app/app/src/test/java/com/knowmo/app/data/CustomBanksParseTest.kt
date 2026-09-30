package com.knowmo.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * CustomBanks CSV 解析的 JVM 单测（2026-09-28 第 2 批，CI `testDebugUnitTest` 跑）：
 * 覆盖 [CustomBanks.parseCsv] 的 fail-closed 校验、表头识别、词条 id 派生
 * 与 [CustomBanks.suggestBankName] 清洗。
 *
 * 可测性说明：`parseCsv` 只读 banks 注册表、自身零写入，各用例相互独立、无需清理；
 * 拼音一律手填——android ICU Transliterator 在 JVM 单测环境取不到，
 * 「拼音留空自动注音」路径不在本套覆盖内（真机/导入文案已提示抽查多音字）。
 * 词库文件级校验（官方 5 库的格式门禁）由 `tools/check_wordbank_csv.py` 在 CI 另行把关。
 */
class CustomBanksParseTest {

    private val header = "词,拼音,用途\r\n"

    private fun csv(vararg rows: String) = header + rows.joinToString("\r\n") + "\r\n"

    /* ---------- 正常路径 ---------- */

    @Test
    fun parseCsv_normalRows_buildsTermsWithPinyinPairs() {
        val r = CustomBanks.parseCsv(
            csv("地铁站,dì tiě zhàn,看到这仨字就是坐地铁", "公交站,gōng jiāo zhàn,等公共汽车的牌子"),
            "测试库",
        )
        assertEquals("测试库", r.bank.name)
        assertEquals(0, r.skippedRepeats)
        assertEquals(2, r.bank.terms.size)
        val t = r.bank.terms[0]
        assertEquals("地铁站", t.text)
        assertEquals(r.bank.id, t.scene)
        assertEquals(3, t.chars.size)
        assertEquals("dì", t.chars[0].c)
        assertEquals("tiě", t.chars[1].p)
        assertEquals("看到这仨字就是坐地铁", t.tip)
        assertTrue("词条 id 须以库 id 为前缀", t.id.startsWith(r.bank.id + "-"))
    }

    @Test
    fun parseCsv_headerVariants_allRecognized() {
        for (h in listOf("词,拼音,用途", "词语,拼音,用途", "text,pinyin,usage", "TEXT,PINYIN,USAGE")) {
            val r = CustomBanks.parseCsv("$h\r\n地铁站,dì tiě zhàn,坐地铁\r\n", "测试库")
            assertEquals("表头「$h」应整行跳过", 1, r.bank.terms.size)
        }
    }

    @Test
    fun parseCsv_noHeader_alsoWorks() {
        val r = CustomBanks.parseCsv("地铁站,dì tiě zhàn,坐地铁\r\n", "测试库")
        assertEquals(1, r.bank.terms.size)
    }

    @Test
    fun parseCsv_quotedTipWithComma_rfc4180Parsed() {
        val r = CustomBanks.parseCsv("地铁,dì tiě,\"坐地铁、买票，都很常用\"\r\n", "测试库")
        assertEquals("坐地铁、买票，都很常用", r.bank.terms[0].tip)
    }

    /* ---------- 词条 id 派生（可复算契约） ---------- */

    @Test
    fun parseCsv_termId_fromWordAndPinyin_notFromTip() {
        val a = CustomBanks.parseCsv(csv("地铁站,dì tiě zhàn,坐地铁"), "测试库").bank.terms[0].id
        val b = CustomBanks.parseCsv(csv("地铁站,dì tiě zhàn,换一句用途说明"), "测试库").bank.terms[0].id
        assertEquals("改用途说明不变 id（进度保留）", a, b)
        val c = CustomBanks.parseCsv(csv("地铁站,dí tiě zhàn,坐地铁"), "测试库").bank.terms[0].id
        assertNotEquals("改拼音 = 新词", a, c)
    }

    @Test
    fun parseCsv_sameTitleSameBankId_differentTitleDifferentId() {
        val a = CustomBanks.parseCsv(csv("地铁站,dì tiě zhàn,坐地铁"), "我的库").bank.id
        val b = CustomBanks.parseCsv(csv("地铁站,dì tiě zhàn,坐地铁"), "我的库").bank.id
        val c = CustomBanks.parseCsv(csv("地铁站,dì tiě zhàn,坐地铁"), "我的词库").bank.id
        assertEquals("同名重导 = 同库 id（替换更新）", a, b)
        assertNotEquals("换名 = 新库 id（进度失联）", a, c)
    }

    /* ---------- fail-closed 校验（任一不合格整库拒绝） ---------- */

    @Test
    fun parseCsv_syllableCountMismatch_throws() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            CustomBanks.parseCsv(csv("地铁站,dìtiě zhàn,坐地铁"), "测试库")
        }
        assertTrue(e.message!!.contains("音节数"))
    }

    @Test
    fun parseCsv_missingTip_throws() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            CustomBanks.parseCsv("地铁站,dì tiě zhàn,\r\n", "测试库")
        }
        assertTrue(e.message!!.contains("用途"))
    }

    @Test
    fun parseCsv_wordTooLong_throws() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            CustomBanks.parseCsv(csv("火车站售票大厅门口,huo che zhan shou piao da ting men kou,x"), "测试库")
        }
        assertTrue(e.message!!.contains("1~8"))
    }

    @Test
    fun parseCsv_duplicateWordInBank_throws() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            CustomBanks.parseCsv(csv("地铁站,dì tiě zhàn,坐地铁", "地铁站,dì tiě zhàn,再来一次"), "测试库")
        }
        assertTrue(e.message!!.contains("重复"))
    }

    @Test
    fun parseCsv_emptySheet_throws() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            CustomBanks.parseCsv("", "测试库")
        }
        assertTrue(e.message!!.contains("空"))
    }

    @Test
    fun parseCsv_headerOnly_throws() {
        // 只有表头时 dataRows 非空（逃过空表检查），须在此兜住——否则空库导入成功、下次启动装载复校验失败后静默消失
        val e = assertThrows(IllegalArgumentException::class.java) {
            CustomBanks.parseCsv("词,拼音,用途\r\n", "测试库")
        }
        assertTrue(e.message!!.contains("空"))
    }

    @Test
    fun parseCsv_overMaxTerms_throws() {
        val rows = (1..(CustomBanks.MAX_TERMS + 3)).map { i ->
            val text = "词" + i.toString().padStart(4, '0')   // 恒 5 字
            "$text,a b c d e,用途说明第${i % 100}条"
        }
        val e = assertThrows(IllegalArgumentException::class.java) {
            CustomBanks.parseCsv(header + rows.joinToString("\r\n") + "\r\n", "测试库")
        }
        assertTrue(e.message!!.contains("超过单库上限"))
    }

    @Test
    fun parseCsv_blankTitle_throws() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            CustomBanks.parseCsv(csv("地铁站,dì tiě zhàn,坐地铁"), "   ")
        }
        assertTrue(e.message!!.contains("名字"))
    }

    /* ---------- suggestBankName：副本后缀清洗 + 截断 ---------- */

    @Test
    fun suggestBankName_stripsCopySuffixes() {
        assertEquals("常用字词", CustomBanks.suggestBankName("常用字词.csv"))
        assertEquals("常用字词", CustomBanks.suggestBankName("常用字词(1).csv"))
        assertEquals("常用字词", CustomBanks.suggestBankName("常用字词（1）.csv"))
        assertEquals("常用字词", CustomBanks.suggestBankName("常用字词 - 副本.csv"))
        assertEquals("常用字词", CustomBanks.suggestBankName("常用字词 - 副本(2).csv"))
    }

    @Test
    fun suggestBankName_truncatesToEightChars() {
        assertEquals(8, CustomBanks.suggestBankName("十个字以上的长文件名.csv").length)
        assertEquals("十个字以上的长文", CustomBanks.suggestBankName("十个字以上的长文件名.csv"))
    }

    @Test
    fun suggestBankName_keepsPlainNumberSuffix() {
        // 刻意不剥「名字 2」：无括号纯数字可能是有意命名的不同词库
        assertEquals("词库 2", CustomBanks.suggestBankName("词库 2.csv"))
    }
}
