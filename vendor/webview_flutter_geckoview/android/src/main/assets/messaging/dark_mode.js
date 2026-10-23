// 百宝箱网页深色适配（扩展自带 content script，由 background.js 注册）
//
// 思路参考 Dark Reader：不做整页反相（反相会毁掉半透明遮罩与固定定位层），
// 而是逐元素重映射颜色——浅色底改同色系深色、深色文字改浅色，透明度保留。
// 由宿主通过扩展端口下发开关状态（消息 {__bbxDark:true/false}）。
(function () {
    var STYLE_ID = "__bbx_dark_style";
    var MARK = "data-bbx-dk";
    var MAXELEMS = 6000;
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

    // ---- main ------------------------------------------------------------
    var enabled = false, observer = null, timer = null, applying = false;

    // doubled attribute keeps our rules above the page's own !important rules
    function markSel(n) {
        return "[" + MARK + '="' + n + '"][' + MARK + '="' + n + '"]';
    }

    function apply() {
        if (typeof document === "undefined" || !document.documentElement) return;
        applying = true;
        try {
            var i, el, cs, decl, list = [];
            var groups = {}, order = [], k = 0;

            var marked = document.querySelectorAll("[" + MARK + "]");
            for (i = 0; i < marked.length; i++) marked[i].removeAttribute(MARK);

            list.push(document.documentElement);
            if (document.body) list.push(document.body);
            var all = (document.body || document.documentElement).querySelectorAll("*");
            for (i = 0; i < all.length && i < MAXELEMS; i++) list.push(all[i]);

            for (i = 0; i < list.length; i++) {
                el = list[i];
                if (i > 1) {
                    if (SKIP[el.tagName.toUpperCase()]) continue;
                    cs = g.getComputedStyle(el);
                    if (cs.display === "none" || cs.visibility === "hidden") continue;
                } else {
                    cs = g.getComputedStyle(el);
                }
                decl = "";

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

                if (!decl) continue;
                el.setAttribute(MARK, String(k));
                if (!groups[decl]) { groups[decl] = []; order.push(decl); }
                groups[decl].push(k);
                k++;
            }

            var rules = [
                "html,body{background-color:#14161a !important}",
                "html{color-scheme:dark !important}",
                "::placeholder{color:#8b939e !important}"
            ];
            for (i = 0; i < order.length; i++) {
                decl = order[i];
                var sel = [], idx = groups[decl], j;
                for (j = 0; j < idx.length; j++) sel.push(markSel(idx[j]));
                rules.push(sel.join(",") + "{" + decl + "}");
            }
            var text = rules.join("\n");

            var st = document.getElementById(STYLE_ID);
            if (!st) {
                st = document.createElement("style");
                st.id = STYLE_ID;
                (document.head || document.documentElement).appendChild(st);
            }
            if (st.textContent !== text) st.textContent = text;
            g.__bbxDarkStats = { marks: k, nodes: list.length, at: Date.now() };
        } catch (e) {
            g.__bbxDarkErr = String(e);
        } finally {
            applying = false;
        }
    }

    function schedule() {
        if (!enabled || applying || timer) return;
        timer = g.setTimeout(function () { timer = null; apply(); }, 350);
    }

    function disable() {
        enabled = false;
        if (observer) { observer.disconnect(); observer = null; }
        if (timer) { g.clearTimeout(timer); timer = null; }
        if (typeof document === "undefined") return;
        var st = document.getElementById(STYLE_ID);
        if (st && st.parentNode) st.parentNode.removeChild(st);
        var marked = document.querySelectorAll("[" + MARK + "]");
        for (var i = 0; i < marked.length; i++) marked[i].removeAttribute(MARK);
    }

    function enable() {
        if (typeof document === "undefined") return;
        enabled = true;
        apply();
        if (!observer && g.MutationObserver && document.documentElement) {
            observer = new g.MutationObserver(schedule);
            observer.observe(document.documentElement, {
                childList: true, subtree: true,
                attributes: true, attributeFilter: ["class", "style"]
            });
        }
    }

    g.__bbxDark = { version: 3, enable: enable, disable: disable, apply: apply };

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
