// 网页深色适配引擎的回归测试（真 DOM，jsdom）。
//
// 运行：
//   npm i jsdom
//   node tool/dark_engine_smoke_test.js
//
// 覆盖：白底被压暗、深色底不动、深色文字变浅、白字不动、半透明遮罩保留 alpha、
//      渐变改写、img/script/隐藏元素跳过、选择器权重双写、关闭后彻底清理。
const path = require('path');
const fs = require('fs');

let JSDOM;
try {
  ({ JSDOM } = require('jsdom'));
} catch (e) {
  console.error('缺少 jsdom，先执行：npm i jsdom');
  process.exit(2);
}

const ENGINE = path.join(__dirname, '..', 'assets', 'dark_engine.js');

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

const dom = new JSDOM(html, { pretendToBeVisual: true });
global.window = dom.window;
global.document = dom.window.document;

new Function(fs.readFileSync(ENGINE, 'utf8'))();
dom.window.__bbxDark.enable();

const st = document.getElementById('__bbx_dark_style');
let fail = 0;
function t(name, got, want) {
  const ok = JSON.stringify(got) === JSON.stringify(want);
  if (!ok) fail++;
  console.log((ok ? 'PASS ' : 'FAIL ') + name + ' -> ' + JSON.stringify(got) +
    (ok ? '' : ' (want ' + JSON.stringify(want) + ')'));
}

t('注入 style 元素', !!st, true);
t('白底卡片被标记', document.querySelector('.card').hasAttribute('data-bbx-dk'), true);
t('中等亮度蓝按钮底色被压暗(保色相)', /hsla\(217,/.test(st.textContent), true);
t('深色按钮底色不动', document.querySelector('.darkbtn').hasAttribute('data-bbx-dk'), false);
t('白字未被改写', /color:hsla\(0,0%,95%/.test(st.textContent), false);
t('隐藏元素未处理', document.querySelector('.hidden').hasAttribute('data-bbx-dk'), false);
t('img 被跳过', document.querySelector('img').hasAttribute('data-bbx-dk'), false);
t('script 被跳过', document.querySelector('script').hasAttribute('data-bbx-dk'), false);
t('半透明遮罩保留 alpha', /background-color:hsla\([^)]*,0\.5\)/.test(st.textContent), true);
t('渐变被改写', /background-image:linear-gradient\(hsla/.test(st.textContent), true);
t('生成了深色底', /background-color:hsla\(0,0%,6%,1\)/.test(st.textContent), true);
t('生成了浅色字', /color:hsla\(0,0%,80%,1\)/.test(st.textContent), true);
t('选择器权重双写', /\[data-bbx-dk="\d+"\]\[data-bbx-dk="\d+"\]/.test(st.textContent), true);

const markedBefore = document.querySelectorAll('[data-bbx-dk]').length;
dom.window.__bbxDark.disable();
t('关闭后移除 style', !document.getElementById('__bbx_dark_style'), true);
t('关闭后清空标记', document.querySelectorAll('[data-bbx-dk]').length, 0);

console.log('（关闭前标记元素数 ' + markedBefore + '）');
console.log(fail ? ('失败 ' + fail + ' 项') : '全部通过');
process.exit(fail ? 1 : 0);
