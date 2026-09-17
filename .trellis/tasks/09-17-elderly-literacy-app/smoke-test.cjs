/* 原型 v2（抖音式 feed）DOM 冒烟测试 */
const path = require("path");
const { JSDOM } = require("C:/Users/85109/.workbuddy/binaries/node/workspace/node_modules/jsdom");
const fs = require("fs");

const html = fs.readFileSync(path.join(__dirname, "..", "..", "..", "prototype", "index.html"), "utf8");

const spoken = [];
const dom = new JSDOM(html, {
  runScripts: "dangerously",
  url: "http://localhost/prototype/",
  pretendToBeVisual: true,
  beforeParse(window) {
    window.speechSynthesis = { cancel() {}, speak() {}, getVoices() { return []; } };
    window.SpeechSynthesisUtterance = function (t) { this.text = t; spoken.push(t); };
    /* IO 桩：不自动触发，测试手动调 onCardVisible */
    window.IntersectionObserver = class {
      constructor(cb) { this.cb = cb; }
      observe() {} disconnect() {}
    };
  }
});

const w = dom.window, d = w.document;
const $ = s => d.querySelector(s);
const $$ = s => [...d.querySelectorAll(s)];
const ev = code => w.eval(code);
const activeSheet = () => ["sheet-char", "scr-settings", "sheet-wordbook"].filter(id => $("#" + id).classList.contains("open"));
const lastSpoken = () => spoken[spoken.length - 1] || "";

const results = [];
function check(name, cond) { results.push((cond ? "PASS" : "FAIL") + "  " + name); if (!cond) process.exitCode = 1; }

(async () => {
  /* ---- 启动（推荐频道） ---- */
  check("启动：7 个频道 chip", $$("#channelbar .chip").length === 7);
  check("启动：推荐频道激活", $("#channelbar .chip").classList.contains("active"));
  check("启动：feed 7 张卡（6词条+完成卡）", $$("#feed .fcard").length === 7);
  check("启动：第 1 张是复习卡（地铁站）", $("#feed .fcard:nth-child(1)").dataset.term === "transit-1");
  check("启动：队列交错（第2张为新学公交站）", $("#feed .fcard:nth-child(2)").dataset.term === "transit-2");
  check("启动：复习卡初始隐藏拼音", $("#py-transit-1").classList.contains("hidden"));
  check("启动：复习卡有认识/忘了按钮", $$("#fb-transit-1 .fb-btn").length === 2);

  /* ---- 自动朗读 ---- */
  w.onCardVisible($("#feed .fcard:nth-child(1)"));
  check("自动朗读：复习卡播报（含提示语）", lastSpoken().includes("还记得它念什么吗"));
  w.onCardVisible($("#feed .fcard:nth-child(1)"));
  check("自动朗读：同卡不重复播", spoken.filter(s => s.includes("还记得它念什么吗")).length === 1);
  w.onCardVisible($("#feed .fcard:nth-child(2)"));
  check("自动朗读：新学卡播报词+用途", lastSpoken().includes("公交站") && lastSpoken().includes("等公共汽车"));

  /* ---- 词条点读 ---- */
  w.speakTerm("market-1");
  check("点读：今日特价+用途", lastSpoken().includes("今日特价") && lastSpoken().includes("牌子"));

  /* ---- 复习流：认识 ---- */
  w.reviewAnswer("transit-1", true);
  await new Promise(r => setTimeout(r, 260));   /* 等待展开后的延迟朗读 */
  check("认识：拼音展开", !$("#py-transit-1").classList.contains("hidden"));
  check("认识：反馈文案（间隔升级 1→3 天）", ($$(".fb-result").some(el => el.textContent.includes("3 天后再见"))));
  check("认识：朗读完整内容", lastSpoken().includes("坐地铁的地方"));
  check("认识：按钮防重复（data-done）", $("#fb-transit-1") === null);
  check("认识：known=1", ev("state.session.known") === 1);

  /* ---- 复习流：忘了 ---- */
  w.reviewAnswer("hospital-1", false);
  check("忘了：known 不变、forgot=1", ev("state.session.known") === 1 && ev("state.session.forgot") === 1);
  check("忘了：进入生词本", ev("state.forgot['hospital-1']") === 1);
  check("忘了：生词本红点显示", $("#wb-badge").classList.contains("show") && $("#wb-badge").textContent === "1");
  check("忘了：反馈文案（明天再来）", $$(".fb-result").some(el => el.textContent.includes("明天再来")));

  /* ---- 完成卡 ---- */
  w.updateDoneStats();
  check("完成卡统计：认识了 1 个，忘了 1 个", $("#done-stats").textContent.includes("认识了 1 个") && $("#done-stats").textContent.includes("忘了 1 个"));
  w.onCardVisible($("#feed .fcard[data-done]"));
  check("完成卡：语音表扬", lastSpoken().includes("学完了"));
  w.replayFeed();
  check("重看一遍：feed 重建", $$("#feed .fcard").length === 7);
  check("重看一遍：统计清零", ev("state.session.known") === 0 && ev("state.session.forgot") === 0);

  /* ---- 频道切换 ---- */
  w.switchChannel("hospital");
  check("切换：医院频道激活", $$("#channelbar .chip")[3].classList.contains("active"));
  check("切换：feed 替换为医院 6 词条+完成卡", $$("#feed .fcard").length === 7);
  check("切换：播报频道名", lastSpoken().includes("医院"));
  check("切换：第 1 张为挂号处", $("#feed .fcard:nth-child(1)").dataset.term === "hospital-1");
  w.switchChannel("hospital");
  check("切换：重复点同频道不重置", lastSpoken().includes("医院") && ev("state.spokenSet") !== undefined);

  /* ---- 字卡弹层 ---- */
  w.openCharSheet("地");
  check("字卡：弹层打开", activeSheet().includes("sheet-char"));
  check("字卡：拼音 dì", $("#sheet-char .cs-py").textContent === "dì");
  check("字卡：相关词条含地铁站", $$("#sheet-char .wchip").some(c => c.textContent === "地铁站"));
  w.closeSheets();
  await new Promise(r => setTimeout(r, 320));
  check("字卡：关闭", activeSheet().length === 0);

  /* ---- 生词本 ---- */
  w.openWordbook();
  check("生词本：列表 1 项", $$("#wb-list .wb-item").length === 1);
  check("生词本：显示忘记次数", $("#wb-list .wb-item .n").textContent.includes("1 次"));
  w.closeSheets();

  /* ---- 设置 ---- */
  w.openSheet("scr-settings");
  await new Promise(r => setTimeout(r, 120));   /* 等待 rAF 添加 open class */
  check("设置：弹层打开", activeSheet().includes("scr-settings"));
  w.setFontScale(1.15, $$("#seg-font .seg-btn")[1]);
  check("设置：字体放大生效", ev("state.fscale") === 1.15 && d.documentElement.style.getPropertyValue("--fscale") === "1.15");
  w.setRate(0.7, $$("#seg-rate .seg-btn")[0]);
  check("设置：语速=慢", ev("state.rate") === 0.7);
  w.setRemind("晚上 7 点", $$("#seg-remind .seg-btn")[2]);
  check("设置：提醒=晚上7点", ev("state.remind") === "晚上 7 点");
  check("设置：持久化", JSON.parse(w.localStorage.getItem("lcs2-settings")).rate === 0.7);
  w.closeSheets();

  /* ---- 汇总 ---- */
  const pass = results.filter(r => r.startsWith("PASS")).length;
  console.log(results.join("\n"));
  console.log(`\n===== ${pass}/${results.length} PASS =====`);
  w.close();
})().catch(e => { console.error("SCRIPT ERROR:", e.message); process.exitCode = 1; });
