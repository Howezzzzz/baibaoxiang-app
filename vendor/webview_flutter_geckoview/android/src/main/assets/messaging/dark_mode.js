// 百宝箱网页深色适配（扩展自带 content script，由 background.js 注册）
//
// 思路参考 Dark Reader：不做整页反相（反相会毁掉半透明遮罩与固定定位层），
// 而是逐元素重映射颜色——浅色底改同色系深色、深色文字改浅色，透明度保留。
//
// v6 性能优化（针对「很卡」）：
//  1) 读写分离：先用只读阶段批量收集声明，再统一写属性。原实现边读
//     getComputedStyle 边写 setAttribute，每次写都会让下一次读触发整棵样式
//     重算（layout thrashing），这是卡顿主因。
//  2) 空闲分片：BFS 队列按 ≤12ms 一片处理，用 requestIdleCallback 让出主线程，
//     不再长时间阻塞交互；不再需要「2 秒预算」这种粗暴截断。
//  3) display:none 子树整棵跳过（SPA 里隐藏模板树往往占了大半 DOM）。
//  4) 统计不再 querySelectorAll('*') 全量遍历，直接复用遍历计数。
//  5) 图标采样限频（每 2 秒最多一批，每批 ≤40 张）。
//  6) 启动时先只写底色（立刻变暗），元素适配交给空闲分片，避免和首屏抢主线程。
(function () {
    var STYLE_ID = "__bbx_dark_style";
    var MARK = "data-bbx-dk";
    var SIG = "data-bbx-sig";
    var INV = "data-bbx-inv";
    var IMGDONE = "data-bbx-img";
    var MAXELEMS = 15000;
    var MAX_RULES = 4000;
    var SLICE_MS = 25;
    var ICON_EVERY_MS = 2000;
    var ICON_PER_BATCH = 40;
    var SVG_NS = "http://www.w3.org/2000/svg";
    var SKIP = {
        SCRIPT: 1, STYLE: 1, LINK: 1, META: 1, HEAD: 1, TITLE: 1, NOSCRIPT: 1,
        TEMPLATE: 1, BR: 1, HR: 1, IMG: 1, VIDEO: 1, CANVAS: 1, IFRAME: 1,
        PICTURE: 1, SOURCE: 1, TRACK: 1, OBJECT: 1, EMBED: 1, MAP: 1, AREA: 1,
        BASE: 1, AUDIO: 1, SLOT: 1
    };
    var baseBg = "#14161a";
    var g = typeof window !== "undefined" ? window : globalThis;

    // ---- color helpers ---------------------------------------------------
    function parse(c) {
        var m = /^rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+)(?:[,\s/]+([\d.]+%?))?\s*\)$/.exec(c || "");
        if (!m) return null;
        var r = +m[1] / 255, gg = +m[2] / 255, b = +m[3] / 255;
        var a = m[4] == null ? 1 : (m[4].indexOf("%") >= 0 ? parseFloat(m[4]) / 100 : +m[4]);
        if (!isFinite(r) || !isFinite(gg) || !isFinite(b) || !isFinite(a)) return null;
        var max = Math.max(r, gg, b), min = Math.min(r, gg, b), d = max - min;
        var l = (max + min) / 2, h = 0, s = 0;
        if (d > 0) {
            s = d / (1 - Math.abs(2 * l - 1));
            if (max === r) h = 60 * (((gg - b) / d) % 6);
            else if (max === gg) h = 60 * ((b - r) / d + 2);
            else h = 60 * ((r - gg) / d + 4);
            if (h < 0) h += 360;
            if (s > 1) s = 1;
        }
        return { h: h, s: s, l: l, a: a };
    }

    function cssOf(c) {
        return "hsla(" + Math.round(c.h) + "," + Math.round(c.s * 100) + "%," +
            (Math.round(c.l * 1000) / 10) + "%," + (Math.round(c.a * 1000) / 1000) + ")";
    }

    function dark(c) {
        var l = (1 - c.l) * 0.30 + 0.06;
        if (l < 0.06) l = 0.06;
        if (l > 0.30) l = 0.30;
        return { h: c.h, s: c.s * 0.9, l: l, a: c.a };
    }

    function light(c) {
        var l = 1 - c.l;
        if (l < 0.68) l = 0.68;
        if (l > 0.95) l = 0.95;
        return { h: c.h, s: c.s * 0.85, l: l, a: c.a };
    }

    function isLight(c) { return c.l > 0.5; }
    function isDark(c) { return c.l < 0.55; }

    function hasText(el) {
        var n = el.childNodes;
        for (var i = 0; i < n.length; i++) {
            if (n[i].nodeType === 3 && /\S/.test(n[i].nodeValue || "")) return true;
        }
        return false;
    }

    function remapGradient(v) {
        return v.replace(/rgba?\([^()]*\)/g, function (m) {
            var c = parse(m);
            if (!c || c.a < 0.02) return m;
            return isLight(c) ? cssOf(dark(c)) : m;
        });
    }

    function sigOf(cs) {
        return cs.backgroundColor + "|" + (cs.backgroundImage || "").length + "|" +
            cs.color + "|" + cs.borderTopWidth + "|" + cs.borderTopColor + "|" + cs.fill;
    }

    // 只读：返回 {skip:true}（连子孙一起跳过）或 {decl, sig}
    function readDecl(el) {
        var tag = el.tagName ? el.tagName.toUpperCase() : "";
        if (SKIP[tag]) return null;
        var cs = g.getComputedStyle(el);
        if (cs.display === "none") return { skip: true };
        var marked = el.getAttribute(MARK) !== null;
        var sig = sigOf(cs);
        if (marked && el.getAttribute(SIG) === sig) return null; // 无变化
        if (cs.visibility === "hidden") return null;
        var decl = "";
        if (el.namespaceURI === SVG_NS) {
            var f = parse(cs.fill), stk = parse(cs.stroke);
            if (f && f.a > 0.05 && isDark(f)) decl += "fill:" + cssOf(light(f)) + " !important;";
            if (stk && stk.a > 0.05 && isDark(stk)) decl += "stroke:" + cssOf(light(stk)) + " !important;";
            return { decl: decl, sig: sig };
        }
        var bg = parse(cs.backgroundColor);
        if (bg && bg.a > 0.05 && isLight(bg)) {
            decl += "background-color:" + cssOf(dark(bg)) + " !important;";
        }
        var bi = cs.backgroundImage;
        if (bi && bi.indexOf("gradient(") >= 0) {
            var rb = remapGradient(bi);
            if (rb !== bi) decl += "background-image:" + rb + " !important;";
        }
        if (hasText(el)) {
            var fg = parse(cs.color);
            if (fg && fg.a > 0.05 && isDark(fg)) {
                decl += "color:" + cssOf(light(fg)) + " !important;";
            }
        }
        var bw = parseFloat(cs.borderTopWidth) || 0;
        if (bw > 0) {
            var bc = parse(cs.borderTopColor);
            if (bc && bc.a > 0.05 && isLight(bc)) {
                decl += "border-color:" + cssOf(dark(bc)) + " !important;";
            }
        }
        return { decl: decl, sig: sig };
    }

    // ---- state -----------------------------------------------------------
    var enabled = false, observer = null, sliceTimer = null, sheetDirty = false;
    var queue = [], cursor = 0, visited = 0, marksTotal = 0;
    var rules = {}, next = 0;
    var curRules = 0, lastIconAt = 0, chunkTimer = null, sliceCount = 0, lastFullAt = 0;
    var pendingWrites = [];
    var lastStats = null;

    function bootOk() {
        return typeof document !== "undefined" && document.documentElement;
    }

    function markSel(n) {
        return "[" + MARK + '="' + n + '"][' + MARK + '="' + n + '"]';
    }

    function writeSheet() {
        var keys = [], k;
        for (k in rules) if (rules[k].length) keys.push(k);
        var out = [
            "html,body{background-color:" + baseBg + " !important}",
            "html{color-scheme:dark !important}",
            "::placeholder{color:#8b939e !important}",
            "[" + INV + '="1"]{filter:invert(1) hue-rotate(180deg) !important}'
        ];
        for (var i = 0; i < keys.length; i++) {
            var ids = rules[keys[i]], sel = [];
            for (var j = 0; j < ids.length; j++) sel.push(markSel(ids[j]));
            out.push(sel.join(",") + "{" + keys[i] + "}");
        }
        var text = out.join("\n");
        var st = document.getElementById(STYLE_ID);
        if (!st) {
            st = document.createElement("style");
            st.id = STYLE_ID;
            (document.head || document.documentElement).appendChild(st);
        }
        if (st.textContent !== text) st.textContent = text;
        sheetDirty = false;
        curRules = 0;
        for (k in rules) curRules += rules[k].length;
    }

    function resetMarks() {
        var marked = document.querySelectorAll("[" + MARK + "]");
        for (var i = 0; i < marked.length; i++) marked[i].removeAttribute(MARK);
        rules = {};
        next = 0;
    }

    function enqueue(roots) {
        for (var i = 0; i < roots.length; i++) {
            var r = roots[i];
            if (r && r.nodeType === 1) queue.push(r);
        }
    }

    // 分片调度：用定时器保证吞吐。早期版本用 requestIdleCallback，空闲回调来得太慢，
    // 等于每 400ms 才干 12ms 的活 → 大页面要几分钟才变黑，已弃用。
    function scheduleSlice() {
        if (chunkTimer || !enabled) return;
        chunkTimer = g.setTimeout(function () { chunkTimer = null; slice(); }, 0);
    }

    // 一片：只读收集 → 统一写；每片 ≤ SLICE_MS，既不断阻塞主线程、又保证吞吐
    function slice() {
        if (!enabled) return;
        var t0 = Date.now();
        pendingWrites.length = 0;
        while (cursor < queue.length && visited < MAXELEMS) {
            if (Date.now() - t0 > SLICE_MS) break;
            var el = queue[cursor++];
            if (!el || el.nodeType !== 1) continue;
            visited++;
            var r = readDecl(el);
            if (r === null) continue;
            if (r.skip) continue;                 // display:none：整棵子树不入队
            if (r.decl) pendingWrites.push([el, r.decl, r.sig]);
            else pendingWrites.push([el, "", r.sig]);
            var ch = el.children, i;
            for (i = 0; i < ch.length; i++) queue.push(ch[i]);   // BFS：子节点入队尾
        }
        // 写阶段（与读阶段分离，避免样式反复重算）
        for (var w = 0; w < pendingWrites.length; w++) {
            var pw = pendingWrites[w], e2 = pw[0], decl = pw[1];
            e2.setAttribute(SIG, pw[2]);
            if (!decl) {
                if (e2.getAttribute(MARK) !== null) e2.removeAttribute(MARK);
                continue;
            }
            if (curRules > MAX_RULES) continue;
            var n = String(next++);
            e2.setAttribute(MARK, n);
            if (!rules[decl]) rules[decl] = [];
            rules[decl].push(n);
            rules[decl].length;
            sheetDirty = true;
            curRules++;
        }
        if (sheetDirty) writeSheet();
        if (cursor < queue.length && visited < MAXELEMS) { scheduleSlice(); return; }
        queue = [];
        cursor = 0;
        visited = 0;
        marksTotal = curRules;
        lastFullAt = Date.now();
        maybeIconPass();
        sliceCount++;
        lastStats = {
            marks: curRules, rules: Object.keys(rules).length,
            ms: Date.now() - t0, slices: sliceCount, at: Date.now()
        };
        g.__bbxDarkStats = lastStats;
    }

    // 图标采样：限频限量，避免每次变动都跑 canvas
    function maybeIconPass(force) {
        if (typeof document === "undefined") return;
        var now = Date.now();
        if (!force && now - lastIconAt < ICON_EVERY_MS) return;
        lastIconAt = now;
        var imgs;
        try { imgs = document.querySelectorAll("img"); } catch (e) { return; }
        var done = 0;
        for (var i = 0; i < imgs.length && done < ICON_PER_BATCH; i++) {
            var im = imgs[i];
            if (im.getAttribute(INV) !== null || im.getAttribute(IMGDONE) !== null) continue;
            var w = im.naturalWidth, h = im.naturalHeight;
            if (!w || !h || w > 64 || h > 64) continue;
            var src = im.currentSrc || im.src || "";
            if (!src || (src.indexOf(location.origin) !== 0 && src.indexOf("data:") !== 0)) continue;
            im.setAttribute(IMGDONE, "1");
            done++;
            try {
                var c = document.createElement("canvas");
                c.width = w; c.height = h;
                var cx = c.getContext("2d");
                cx.clearRect(0, 0, w, h);
                cx.drawImage(im, 0, 0);
                var d = cx.getImageData(0, 0, w, h).data;
                var lum = 0, cnt = 0;
                for (var j = 0; j < d.length; j += 4) {
                    if (d[j + 3] > 16) {
                        lum += (0.299 * d[j] + 0.587 * d[j + 1] + 0.114 * d[j + 2]) / 255;
                        cnt++;
                    }
                }
                if (cnt > 0 && (lum / cnt) < 0.45) im.setAttribute(INV, "1");
            } catch (e) { /* 跨域/污染，跳过 */ }
        }
    }

    function requestApply(roots) {
        if (!enabled) return;
        if (roots && roots.length) enqueue(roots);
        scheduleSlice();
    }

    function fullPass() {
        if (!bootOk()) return;
        resetMarks();
        queue = [document.documentElement];
        cursor = 0;
        visited = 0;
        sheetDirty = true;
        writeSheet();
        scheduleSlice();
    }

    // 同步整轮（测试与诊断用）
    function applyNow() {
        if (!bootOk()) return;
        enabled = true;
        resetMarks();
        queue = [document.documentElement];
        cursor = 0;
        visited = 0;
        var guard = 0;
        while (cursor < queue.length && guard++ < MAXELEMS) {
            var el = queue[cursor++];
            if (!el || el.nodeType !== 1) continue;
            var r = readDecl(el);
            if (r === null) continue;
            if (r.skip) continue;
            if (r.decl) {
                var n = String(next++);
                el.setAttribute(MARK, n);
                if (!rules[r.decl]) rules[r.decl] = [];
                rules[r.decl].push(n);
            } else if (el.getAttribute(MARK) !== null) {
                el.removeAttribute(MARK);
            }
            el.setAttribute(SIG, r.sig);
            var ch = el.children, i;
            for (i = 0; i < ch.length; i++) queue.push(ch[i]);
        }
        queue = [];
        cursor = 0;
        writeSheet();
        maybeIconPass(true);
        lastStats = { marks: curRules, rules: Object.keys(rules).length, at: Date.now() };
        g.__bbxDarkStats = lastStats;
    }

    function onMutations(records) {
        if (!enabled) return;
        var roots = [];
        for (var i = 0; i < records.length; i++) {
            var r = records[i];
            if (r.type === "attributes") {
                if (r.target && r.target.nodeType === 1) roots.push(r.target);
            } else {
                for (var j = 0; j < r.addedNodes.length; j++) {
                    var n = r.addedNodes[j];
                    if (n && n.nodeType === 1 && n.id !== STYLE_ID) roots.push(n);
                }
            }
        }
        if (roots.length) requestApply(roots);
    }

    function disable() {
        enabled = false;
        if (observer) { observer.disconnect(); observer = null; }
        if (sliceTimer) { g.clearTimeout(sliceTimer); sliceTimer = null; }
        queue = []; cursor = 0; visited = 0; pendingWrites.length = 0;
        rules = {}; next = 0; curRules = 0; marksTotal = 0;
        if (typeof document === "undefined") return;
        var st = document.getElementById(STYLE_ID);
        if (st && st.parentNode) st.parentNode.removeChild(st);
        var marked = document.querySelectorAll("[" + MARK + "]");
        for (var i = 0; i < marked.length; i++) {
            marked[i].removeAttribute(MARK);
            marked[i].removeAttribute(SIG);
        }
        var inv = document.querySelectorAll("[" + INV + "]");
        for (var j = 0; j < inv.length; j++) inv[j].removeAttribute(INV);
    }

    function enable() {
        if (!bootOk()) return;
        var wasOn = enabled;
        enabled = true;
        // 先只铺底色：立刻变暗，不白闪、不和首屏渲染抢主线程
        writeSheet();
        if (!observer && g.MutationObserver && document.documentElement) {
            observer = new g.MutationObserver(onMutations);
            observer.observe(document.documentElement, {
                childList: true, subtree: true,
                attributes: true, attributeFilter: ["class", "style"]
            });
        }
        if (!g.__bbxDarkLoadHooked) {
            g.__bbxDarkLoadHooked = true;
            g.addEventListener("load", function () {
                if (enabled) { maybeIconPass(true); fullPass(); }
            });
        }
        // 元素适配分片进行；刚跑完不到 2 秒不重启整轮（页面常会反复切前后台）
        if (!wasOn || (cursor >= queue.length && Date.now() - lastFullAt > 2000)) {
            queue = [document.documentElement];
            cursor = 0;
            visited = 0;
            scheduleSlice();
        }
    }

    g.__bbxDark = {
        version: 7,
        enable: enable, disable: disable,
        apply: applyNow, applyNow: applyNow
    };

    // ---- host glue (only inside the web extension content script) --------
    function setEnabled(on) {
        try { on ? enable() : disable(); } catch (e) { g.__bbxDarkErr = String(e); }
    }

    if (typeof browser !== "undefined" && browser && browser.runtime &&
        browser.runtime.onMessage) {
        try {
            if (!g.__bbxDarkHooked) {
                g.__bbxDarkHooked = true;
                browser.runtime.onMessage.addListener(function (m) {
                    if (m && typeof m.__bbxDark === "boolean") setEnabled(m.__bbxDark);
                });
            }
            var ask = function () {
                try {
                    browser.runtime.sendMessage({ __bbxQuery: true }).then(function (resp) {
                        if (resp && typeof resp.__bbxDark === "boolean") setEnabled(resp.__bbxDark);
                    }).catch(function () {});
                } catch (e) { g.__bbxDarkErr = String(e); }
            };
            ask();
            g.addEventListener("pageshow", ask);
            document.addEventListener("visibilitychange", function () {
                if (!document.hidden) ask();
            });
        } catch (e) { g.__bbxDarkErr = String(e); }
    }
})();
