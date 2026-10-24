// 深色适配引擎微基准（合成大页面，模拟 SPA 列表密度），用于对比优化前后的
// 「计算样式调用次数」与「整轮耗时」。注意 jsdom 的 getComputedStyle 比真实浏览器
// 慢很多，绝对值无意义，看的是同环境下的相对改善。
const path = require('path');
const fs = require('fs');
let JSDOM;
try { ({ JSDOM } = require('jsdom')); } catch (e) { console.error('先 npm i jsdom'); process.exit(2); }

function buildHtml(rows) {
  let cells = '';
  for (let i = 0; i < rows; i++) {
    cells += `<div class="row"><span class="c1">标题${i}</span><span class="c2">副标题</span>` +
      `<span class="c3">2026-10-24</span><span class="c4">状态</span><span class="c5">操作</span>` +
      `<div class="wrap"><span class="c6">标签</span><em class="c7">备注</em></div></div>`;
  }
  return `<!doctype html><html><head><style>
body{background:#ffffff;color:#333333}
.card{background:#ffffff;color:#333333;border:1px solid #dddddd}
.row{background-color:#ffffff;color:#444444;border-bottom:1px solid #eeeeee}
.row .c2{color:#888888}
.wrap{background-color:rgba(255,255,255,0.5);color:#555555}
.btn{background-color:rgb(66,133,244);color:#ffffff}
</style></head><body><div class="card">卡片</div>${cells}<button class="btn">按钮</button></body></html>`;
}

function run(label, enginePath, rows) {
  const dom = new JSDOM(buildHtml(rows), { pretendToBeVisual: true });
  const w = dom.window;
  global.window = w;
  global.document = w.document;
  // 统计计算样式调用次数
  let calls = 0;
  const real = w.getComputedStyle.bind(w);
  w.getComputedStyle = function () { calls++; return real.apply(null, arguments); };
  new Function(fs.readFileSync(enginePath, 'utf8'))();
  const nodes = w.document.querySelectorAll('*').length;
  // 阶段一：enable()（v9 内含快速通道；v8 只有底色）
  const t0 = Date.now();
  w.__bbxDark.enable();
  const fastMs = Date.now() - t0;
  const fastCalls = calls;
  const fastMarked = w.document.querySelectorAll('[data-bbx-dk]').length;
  // 阶段二：兜底同步全量
  const t1 = Date.now();
  w.__bbxDark.applyNow();
  const fullMs = Date.now() - t1;
  const marked = w.document.querySelectorAll('[data-bbx-dk]').length;
  console.log(`${label}: 节点 ${nodes}`);
  console.log(`   首屏变黑(enable): ${fastMs}ms，调用 ${fastCalls}，标记 ${fastMarked}`);
  console.log(`   兜底全量: ${fullMs}ms，累计调用 ${calls}，标记 ${marked}`);
  return { calls, ms: fastMs, nodes, marked, fastMs, fullMs };
}

const rows = Number(process.argv[3] || 1500);
const a = run('优化前(v8)', process.argv[2] === 'both' ? '/tmp/engine_v8_baseline.js' : process.argv[2], rows);
const b = run('优化后(v9)', path.join(__dirname, '..', 'vendor', 'webview_flutter_geckoview',
  'android', 'src', 'main', 'assets', 'messaging', 'dark_mode.js'), rows);
console.log(`=> 首屏变黑耗时: ${a.fastMs}ms → ${b.fastMs}ms（下降 ${(100 * (1 - b.fastMs / Math.max(a.fastMs, 1))).toFixed(1)}%）`);
console.log(`=> 首屏阶段样式计算调用: ${a.calls && 0}—见上行明细；全量调用 ${a.calls} → ${b.calls}`);
