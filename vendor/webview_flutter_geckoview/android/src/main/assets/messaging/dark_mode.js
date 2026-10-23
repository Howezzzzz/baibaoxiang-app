// 百宝箱网页深色适配（扩展自带 content script，由 background.js 注册）
//
// 思路参考 Dark Reader：不做整页反相（反相会毁掉半透明遮罩与固定定位层），
// 而是逐元素重映射颜色——浅色底改同色系深色、深色文字改浅色，透明度保留。
//
// v5：
//  - document_start 注入，先铺底色（html/body 立刻变暗）→ 不再白闪
//  - 每元素只取一次计算样式；已处理元素用签名比对，避免重复开销
//  - 内联 SVG 的深色 fill/stroke 提亮（按钮图标在深底上不再「消失」）
//  - 小图标图片（≤64px、同源）按平均亮度判断，深色图标自动反相
//  - 大页面 BFS 遍历 + 增量重算（DOM 变动只算新增/变动节点）
(function () {
    var STYLE_ID = "__bbx_dark_style";
    var MARK = "data-bbx-dk";
    var INV = "data-bbx-inv";
    var IMGDONE = "data-bbx-img";
    var MAXELEMS = 25000;
    var BUDGET_MS = 2000;
    var MAX_RULES = 4000;
    var MAX_ICONS = 200;
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

    // light -> dark (keep hue/saturation/alpha, lower lightness)
    function dark(c) {
        var l = (1 - c.l) * 0.30 + 0.06;
        if (l < 0.06) l = 0.06;
        if (l > 0.30) l = 0.30;
        return { h: c.h, s: c.s * 0.9, l: l, a: c.a };
    }

    // dark -> light (body text / icon strokes)
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

    // rewrite light colors inside gradients too
    function remapGradient(v) {
        return v.replace(/rgba?\([^()]*\)/g, function (m) {
            var c = parse(m);
            if (!c || c.a < 0.02) return m;
            return isLight(c) ? cssOf(dark(c)) : m;
        });
    }

    function declFor(el, cs) {
        var decl = "";
        var isSvg = el.namespaceURI === SVG_NS;
        if (isSvg) {
            // 按钮里的图标常是内联 SVG 且填充写死深色 → 深底上会「消失」
            var f = parse(cs.fill), stk = parse(cs.stroke);
            if (f && f.a > 0.05 && isDark(f)) decl += "fill:" + cssOf(light(f)) + " !important;";
            if (stk && stk.a > 0.05 && isDark(stk)) decl += "stroke:" + cssOf(light(stk)) + " !important;";
            return decl;
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
        return decl;
    }

    // ---- state -----------------------------------------------------------
    var enabled = false, observer = null, timer = null, applying = false;
    var rules = {}, next = 0, pendingRoots = [];

    // doubled attribute keeps our rules above the page's own !important rules
    function markSel(n) {
        return "[" + MARK + '="' + n + '"][' + MARK + '="' + n + '"]';
    }

    function sig(cs) {
        return cs.backgroundColor + "|" + (cs.backgroundImage || "").length + "|" +
            cs.color + "|" + cs.borderTopWidth + "|" + cs.borderTopColor + "|" + cs.fill;
    }

    function bootOk() {
        return typeof document !== "undefined" && document.documentElement;
    }

    function walk(roots, cb) {
        var t0 = Date.now(), seen = 0;
        // BFS：浅层（撑满页面的大容器）先处理，避免被上限截断时漏掉主体
        for (var r = 0; r < roots.length; r++) {
            var queue = [roots[r]], qi = 0;
            while (qi < queue.length) {
                if (seen >= MAXELEMS || (Date.now() - t0) > BUDGET_MS) return false;
                var el = queue[qi++];
                if (!el || el.nodeType !== 1) continue;
                seen++;
                if (!cb(el)) return false;
                var ch = el.children;
                for (var i = 0; i < ch.length; i++) queue.push(ch[i]);
            }
        }
        return true;
    }

    function processEl(el) {
        var tag = el.tagName ? el.tagName.toUpperCase() : "";
        if (SKIP[tag]) return true;
        var cs = g.getComputedStyle(el);
        var marked = el.getAttribute(MARK) !== null;
        if (marked && el.getAttribute("data-bbx-sig") === sig(cs)) return true;
        if (cs.display === "none" || cs.visibility === "hidden") return true;
        var decl = declFor(el, cs);
        el.setAttribute("data-bbx-sig", sig(cs));
        if (!decl) {
            if (marked) el.removeAttribute(MARK);
            return true;
        }
        var n = String(next++);
        el.setAttribute(MARK, n);
        if (!rules[decl]) rules[decl] = [];
        rules[decl].push(n);
        return true;
    }

    // 小图标图片（深色图标在深底上看不见）→ 同源且够小就采样判断，深色则反相
    function iconPass() {
        var imgs;
        try { imgs = document.querySelectorAll("img"); } catch (e) { return; }
        for (var i = 0; i < imgs.length && i < MAX_ICONS; i++) {
            var im = imgs[i];
            if (im.getAttribute(INV) !== null || im.getAttribute(IMGDONE) !== null) continue;
            var w = im.naturalWidth, h = im.naturalHeight;
            if (!w || !h || w > 64 || h > 64) continue;
            var src = im.currentSrc || im.src || "";
            if (!src || (src.indexOf(location.origin) !== 0 && src.indexOf("data:") !== 0)) continue;
            im.setAttribute(IMGDONE, "1");
            try {
                var c = document.createElement("canvas");
                c.width = w; c.height = h;
                var cx = c.getContext("2d");
                cx.clearRect(0, 0, w, h);
                cx.drawImage(im, 0, 0);
                var d = cx.getImageData(0, 0, w, h).data;
                var lum = 0, n = 0;
                for (var j = 0; j < d.length; j += 4) {
                    if (d[j + 3] > 16) {
                        lum += (0.299 * d[j] + 0.587 * d[j + 1] + 0.114 * d[j + 2]) / 255;
                        n++;
                    }
                }
                if (n > 0 && (lum / n) < 0.45) im.setAttribute(INV, "1");
            } catch (e) { /* 画布被污染等，放弃该图 */ }
        }
    }

    function writeSheet() {
        var keys = [], k;
        for (k in rules) if (rules[k].length) keys.push(k);
        var out = [
            // 先铺底色：页面一出现就是暗的，不再白闪
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
    }

    function ruleCount() {
        var n = 0;
        for (var k in rules) n += rules[k].length;
        return n;
    }

    function apply(roots) {
        if (!bootOk()) return;
        applying = true;
        try {
            var t0 = Date.now();
            if (!roots || !roots.length) {
                rules = {};
                next = 0;
                var marked = document.querySelectorAll("[" + MARK + "]");
                for (var i = 0; i < marked.length; i++) marked[i].removeAttribute(MARK);
                roots = [document.documentElement];
            }
            walk(roots, processEl);
            writeSheet();
            iconPass();
            g.__bbxDarkStats = {
                marks: ruleCount(),
                nodes: document.querySelectorAll("*").length,
                rules: Object.keys(rules).length,
                ms: Date.now() - t0,
                at: Date.now()
            };
        } catch (e) {
            g.__bbxDarkErr = String(e);
        } finally {
            applying = false;
        }
    }

    function requestApply(roots) {
        if (!enabled) return;
        if (roots && roots.length) {
            for (var i = 0; i < roots.length; i++) pendingRoots.push(roots[i]);
        }
        if (timer) return;
        timer = g.setTimeout(function () {
            timer = null;
            var rs = pendingRoots;
            pendingRoots = [];
            if (ruleCount() > MAX_RULES) { apply(null); return; }
            apply(rs);
        }, 300);
    }

    function onMutations(records) {
        if (!enabled || applying) return;
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
        requestApply(roots);
    }

    function disable() {
        enabled = false;
        if (observer) { observer.disconnect(); observer = null; }
        if (timer) { g.clearTimeout(timer); timer = null; }
        pendingRoots = [];
        rules = {};
        next = 0;
        if (typeof document === "undefined") return;
        var st = document.getElementById(STYLE_ID);
        if (st && st.parentNode) st.parentNode.removeChild(st);
        var marked = document.querySelectorAll("[" + MARK + "]");
        for (var i = 0; i < marked.length; i++) {
            marked[i].removeAttribute(MARK);
            marked[i].removeAttribute("data-bbx-sig");
        }
        var inv = document.querySelectorAll("[" + INV + "]");
        for (var j = 0; j < inv.length; j++) inv[j].removeAttribute(INV);
    }

    function enable() {
        if (!bootOk()) return;
        enabled = true;
        // 立刻铺底色（哪怕后面的逐元素适配还没跑完，也不会白屏）
        writeSheet();
        apply(null);
        if (g.MutationObserver && document.documentElement) {
            if (!observer) {
                observer = new g.MutationObserver(onMutations);
                observer.observe(document.documentElement, {
                    childList: true, subtree: true,
                    attributes: true, attributeFilter: ["class", "style"]
                });
            }
        }
        // 图片加载完再判一次图标明暗
        if (!g.__bbxDarkLoadHooked) {
            g.__bbxDarkLoadHooked = true;
            g.addEventListener("load", function () { if (enabled) requestApply([document.documentElement]); });
        }
    }

    g.__bbxDark = { version: 5, enable: enable, disable: disable, apply: apply };

    // ---- host glue (only inside the web extension content script) --------
    function setEnabled(on) {
        try { on ? enable() : disable(); } catch (e) { g.__bbxDarkErr = String(e); }
    }

    if (typeof browser !== "undefined" && browser && browser.runtime &&
        browser.runtime.onMessage) {
        try {
            browser.runtime.onMessage.addListener(function (m) {
                if (m && typeof m.__bbxDark === "boolean") setEnabled(m.__bbxDark);
            });
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
