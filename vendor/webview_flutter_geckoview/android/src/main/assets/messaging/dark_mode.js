// 百宝箱网页深色适配（扩展自带 content script，由 background.js 注册）
//
// 思路参考 Dark Reader：不做整页反相（反相会毁掉半透明遮罩与固定定位层），
// 而是逐元素重映射颜色——浅色底改同色系深色、深色文字改浅色，透明度保留。
// 由宿主通过扩展端口下发开关状态（消息 {__bbxDark:true/false}）。
//
// v4：BFS 遍历（先浅层大容器，大页面不会被元素上限截断主体）+ 增量处理
//     （DOM 变动只重算新增/变动节点，弱机型上不再每次全量重扫）。
(function () {
    var STYLE_ID = "__bbx_dark_style";
    var MARK = "data-bbx-dk";
    var MAXELEMS = 25000;
    var BUDGET_MS = 2000;
    var MAX_RULES = 4000;
    var SKIP = {
        SCRIPT: 1, STYLE: 1, LINK: 1, META: 1, HEAD: 1, TITLE: 1, NOSCRIPT: 1,
        TEMPLATE: 1, BR: 1, HR: 1, IMG: 1, VIDEO: 1, CANVAS: 1, IFRAME: 1,
        PICTURE: 1, SOURCE: 1, TRACK: 1, OBJECT: 1, EMBED: 1, MAP: 1, AREA: 1,
        BASE: 1, AUDIO: 1, SLOT: 1
    };
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

    // dark -> light (body text)
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

    function rootCheckBoot() {
        return typeof document !== "undefined" && document.documentElement;
    }

    function walk(roots, cb) {
        var t0 = Date.now(), seen = 0;
        // BFS：浅层（通常是撑满页面的大容器）先处理，避免被上限截断时漏掉主体
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
        var id = el.getAttribute(MARK);
        if (id !== null) {
            // 已处理过：只有自身样式变化才重算（增量场景下省掉绝大部分开销）
            var cs0 = g.getComputedStyle(el);
            if (el.getAttribute("data-bbx-sig") === sig(cs0)) return true;
        }
        var cs = g.getComputedStyle(el);
        if (cs.display === "none" || cs.visibility === "hidden") return true;
        var decl = declFor(el, cs);
        el.setAttribute("data-bbx-sig", sig(cs));
        if (!decl) {
            if (id !== null) el.removeAttribute(MARK);
            return true;
        }
        var n = String(next++);
        el.setAttribute(MARK, n);
        if (!rules[decl]) rules[decl] = [];
        rules[decl].push(n);
        return true;
    }

    function sig(cs) {
        return cs.backgroundColor + "|" + (cs.backgroundImage || "").length + "|" +
            cs.color + "|" + cs.borderTopWidth + "|" + cs.borderTopColor;
    }

    function writeSheet() {
        var keys = [], k;
        for (k in rules) if (rules[k].length) keys.push(k);
        var out = [
            "html,body{background-color:#14161a !important}",
            "html{color-scheme:dark !important}",
            "::placeholder{color:#8b939e !important}"
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
        if (!rootCheckBoot()) return;
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

    function schedule(records) {
        if (!enabled || applying) return;
        if (records && records.length) {
            for (var i = 0; i < records.length; i++) {
                var r = records[i];
                if (r.type === "attributes") {
                    if (r.target && r.target.nodeType === 1) pendingRoots.push(r.target);
                } else {
                    for (var j = 0; j < r.addedNodes.length; j++) {
                        var n = r.addedNodes[j];
                        if (n && n.nodeType === 1 && n.id !== STYLE_ID) pendingRoots.push(n);
                    }
                }
            }
        }
        if (timer) return;
        timer = g.setTimeout(function () {
            timer = null;
            var roots = pendingRoots;
            pendingRoots = [];
            if (ruleCount() > MAX_RULES) { apply(null); return; }
            apply(roots);
        }, 300);
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
    }

    function enable() {
        if (!rootCheckBoot()) return;
        enabled = true;
        apply(null);
        if (g.MutationObserver && document.documentElement) {
            if (!observer) {
                observer = new g.MutationObserver(schedule);
                observer.observe(document.documentElement, {
                    childList: true, subtree: true,
                    attributes: true, attributeFilter: ["class", "style"]
                });
            }
        }
    }

    g.__bbxDark = { version: 4, enable: enable, disable: disable, apply: apply };

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
