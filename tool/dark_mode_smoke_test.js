// 网页深色适配（扩展 content script）回归测试，真 DOM（jsdom）。
//
// 运行：
//   npm i jsdom
//   node tool/dark_mode_smoke_test.js
//
// 覆盖：
//  A. 引擎本体：白底压暗、深色底不动、深色字变浅、白字不动、半透明遮罩保 alpha、
//     渐变改写、img/script/隐藏元素跳过、选择器权重双写、关闭后彻底清理。
//  B. 扩展接线：宿主未开启时不注入；收到 {__bbxDark:true} 注入；false 时清理；
//     启动时向 background 查询状态并据此生效。
const path = require('path');
const fs = require('fs');

let JSDOM;
try {
  ({ JSDOM } = require('jsdom'));
} catch (e) {
  console.error('缺少 jsdom，先执行：npm i jsdom');
  process.exit(2);
}

const ENGINE = path.join(
  __dirname, '..', 'vendor', 'webview_flutter_geckoview',
  'android', 'src', 'main', 'assets', 'messaging', 'dark_mode.js');

const html = `<!doctype html><html><head><style>
body { background: #ffffff; color: #222222; }
.card { background-color: #ffffff; color: #333333; border: 1px solid #dddddd; }
.card .btn { background-color: rgb(66, 133, 244); color: #ffffff; }
.overlay { background-color: rgba(255, 255, 255, 0.5); }
.hidden { display: none; background-color: #ffffff; }
.grad { background-image: linear-gradient(rgb(255, 255, 255), rgb(240, 240, 240)); }
.darkbtn { background-color: #0b3d91; color: #ffffff; }
</style></head><body>
<div class="card">卡片文字<span class="btn">按钮</span></div>
<div class="overlay">遮罩</div>
<div class="hidden">隐藏块</div>
<div class="grad">渐变块</div>
<div class="darkbtn">深色按钮</div>
<img class="logo" src="a.png">
<script>var x = 1;</script>
</body></html>`;

let fail = 0;
function t(name, got, want) {
  const ok = JSON.stringify(got) === JSON.stringify(want);
  if (!ok) fail++;
  console.log((ok ? 'PASS ' : 'FAIL ') + name + ' -> ' + JSON.stringify(got) +
    (ok ? '' : ' (want ' + JSON.stringify(want) + ')'));
}

// ---- A. 引擎本体 ---------------------------------------------------------
function engineSection() {
  const dom = new JSDOM(html, { pretendToBeVisual: true });
  global.window = dom.window;
  global.document = dom.window.document;

  new Function(fs.readFileSync(ENGINE, 'utf8'))();
  dom.window.__bbxDark.enable();

  const st = document.getElementById('__bbx_dark_style');
  t('A1 注入 style 元素', !!st, true);
  t('A2 白底卡片被标记', document.querySelector('.card').hasAttribute('data-bbx-dk'), true);
  t('A3 中等亮度蓝按钮底色被压暗(保色相)', /hsla\(217,/.test(st.textContent), true);
  t('A4 深色按钮底色不动', document.querySelector('.darkbtn').hasAttribute('data-bbx-dk'), false);
  t('A5 白字未被改写', /color:hsla\(0,0%,95%/.test(st.textContent), false);
  t('A6 隐藏元素未处理', document.querySelector('.hidden').hasAttribute('data-bbx-dk'), false);
  t('A7 img 被跳过', document.querySelector('img').hasAttribute('data-bbx-dk'), false);
  t('A8 script 被跳过', document.querySelector('script').hasAttribute('data-bbx-dk'), false);
  t('A9 半透明遮罩保留 alpha', /background-color:hsla\([^)]*,0\.5\)/.test(st.textContent), true);
  t('A10 渐变被改写', /background-image:linear-gradient\(hsla/.test(st.textContent), true);
  t('A11 生成了深色底', /background-color:hsla\(0,0%,6%,1\)/.test(st.textContent), true);
  t('A12 生成了浅色字', /color:hsla\(0,0%,80%,1\)/.test(st.textContent), true);
  t('A13 选择器权重双写', /\[data-bbx-dk="\d+"\]\[data-bbx-dk="\d+"\]/.test(st.textContent), true);

  const markedBefore = document.querySelectorAll('[data-bbx-dk]').length;
  dom.window.__bbxDark.disable();
  t('A14 关闭后移除 style', !document.getElementById('__bbx_dark_style'), true);
  t('A15 关闭后清空标记', document.querySelectorAll('[data-bbx-dk]').length, 0);
  console.log('   （关闭前标记元素数 ' + markedBefore + '）');
}

// ---- B. 扩展接线（browser stub）-----------------------------------------
function bootWithStub(initial) {
  const dom = new JSDOM(html, { pretendToBeVisual: true });
  const w = dom.window;
  const ctx = { listener: null, state: initial, queries: 0 };
  w.browser = {
    runtime: {
      onMessage: { addListener: (fn) => { ctx.listener = fn; } },
      sendMessage: (msg) => {
        if (msg && msg.__bbxQuery) {
          ctx.queries++;
          return Promise.resolve({ __bbxDark: ctx.state });
        }
        return Promise.resolve(null);
      },
    },
  };
  global.window = w;
  global.document = w.document;
  global.browser = w.browser; // 真实环境里 browser 是 content script 的全局对象
  new Function(fs.readFileSync(ENGINE, 'utf8'))();
  return { dom, w, ctx };
}

const wait = (ms) => new Promise((r) => setTimeout(r, ms));

async function glueSection() {
  // B1：初始关闭 -> 不应注入
  {
    const { w, ctx } = bootWithStub(false);
    await wait(20);
    t('B1 未开启时不注入', !!w.document.getElementById('__bbx_dark_style'), false);
    t('B2 启动时向 background 查询过状态', ctx.queries >= 1, true);
    t('B3 已注册消息监听', typeof ctx.listener === 'function', true);
  }
  // B4/B5：宿主下发开关
  {
    const { w, ctx } = bootWithStub(false);
    await wait(20);
    ctx.listener({ __bbxDark: true });
    t('B4 收到开启消息后注入', !!w.document.getElementById('__bbx_dark_style'), true);
    t('B5 开启后确有元素被标记',
      w.document.querySelectorAll('[data-bbx-dk]').length > 0, true);
    ctx.listener({ __bbxDark: false });
    t('B6 收到关闭消息后清理', !!w.document.getElementById('__bbx_dark_style'), false);
    t('B7 关闭后标记清空',
      w.document.querySelectorAll('[data-bbx-dk]').length, 0);
  }
  // B8：启动时 background 已为开启 -> 自动生效
  {
    const { w } = bootWithStub(true);
    await wait(20);
    t('B8 启动查询为开启时自动生效',
      !!w.document.getElementById('__bbx_dark_style'), true);
  }
}

async function incrementalSection() {
  const { w } = bootWithStub(true);
  await wait(40);
  t('C1 初始已注入', !!w.document.getElementById('__bbx_dark_style'), true);

  // 新增节点：MutationObserver 应把它也适配掉（防抖 300ms）
  const div = w.document.createElement('div');
  div.className = 'card';
  div.textContent = '后来加的内容';
  w.document.body.appendChild(div);
  await wait(500);
  t('C2 新增元素被适配', div.hasAttribute('data-bbx-dk'), true);

  // 已有元素改 class（背景变浅）→ 应重算
  const overlay = w.document.querySelector('.overlay');
  const before = overlay.getAttribute('data-bbx-dk');
  overlay.className = 'plain';
  await wait(500);
  t('C3 样式变化的元素被重算',
    overlay.getAttribute('data-bbx-sig') !== before, true);
}

(async () => {
  engineSection();
  await glueSection();
  await incrementalSection();
  console.log(fail ? ('失败 ' + fail + ' 项') : '全部通过');
  process.exit(fail ? 1 : 0);
})();
