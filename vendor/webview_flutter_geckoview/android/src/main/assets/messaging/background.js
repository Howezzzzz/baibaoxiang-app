/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

const javascriptChannels = new Map();
const webExtensionPort = browser.runtime.connectNative("webview_flutter");

// 百宝箱：网页深色适配以扩展自带 content script 形式加载（不经受限端口传大段代码）。
// 宿主通过端口下发 {category:0, type:4, enabled:bool} 开关状态。
// 只在「开启深色」期间注册该 content script —— 日间模式完全零开销
// （否则每个页面、每个子框架都要解析执行一遍适配脚本）。
let darkEnabled = false;
let darkScript = null;

async function setDarkEnabled(on) {
    darkEnabled = !!on;
    if (darkEnabled && !darkScript) {
        try {
            darkScript = await browser.contentScripts.register({
                matches: ["<all_urls>"],
                js: [{ file: "dark_mode.js" }],
                runAt: "document_start",   // 尽早注入：先铺底色，避免白闪
                allFrames: true,           // 页面内容在 iframe 里也要覆盖
            });
            console.info("bbx dark content script registered");
        } catch (e) {
            console.error("bbx dark register failed", e);
            darkScript = null;
        }
    } else if (!darkEnabled && darkScript) {
        try {
            await darkScript.unregister();
        } catch (e) {
            console.error("bbx dark unregister failed", e);
        }
        darkScript = null;
    }
    // 已经打开的页面：开启时补注入一次适配脚本（这些页面是在关闭状态下加载的，
    // 当时没注册脚本），再广播状态；关闭时只广播（让已注入的页面自己还原）
    const tabs = await browser.tabs.query({});
    for (const tab of tabs) {
        try {
            if (darkEnabled) {
                await browser.tabs.executeScript(tab.id, { file: "dark_mode.js" });
            }
            await browser.tabs.sendMessage(tab.id, { __bbxDark: darkEnabled });
        } catch (e) {
            // 个别页面（about:blank 等）注入不了就跳过
        }
    }
}

webExtensionPort.onDisconnect.addListener(() => {
    console.info("webExtensionPort disconnected");
});

webExtensionPort.onMessage.addListener(async (message) => {
    if (typeof message !== "object" || Array.isArray(message) || message === null) {
        return;
    }
    const category = message.category;
    const type = message.type;
    if (typeof category !== "number" || typeof type !== "number") {
        return;
    }
    switch (category) {
        case 0: {
            const id = message.id;
            if (typeof id !== "number") {
                return;
            }
            const reply = {
                category: 1,
                type: type,
                id: id,
            };
            try {
                switch (type) {
                    case 0: {
                        const javascriptChannelName = message.name;
                        await addJavascriptChannel(javascriptChannelName);
                        break;
                    }
                    case 1: {
                        const javascriptChannelName = message.name;
                        await removeJavascriptChannel(javascriptChannelName);
                        break;
                    }
                    case 2: {
                        const tab = await getCurrentTab();
                        const javascript = message.javascript;
                        reply.result = await runJavascript(tab.id, javascript);
                        break;
                    }
                    case 3: {
                        const details = message.details;
                        reply.result = await browser.cookies.set(details);
                        break;
                    }
                    case 4: {
                        // 百宝箱：网页深色适配开关（按需注册/注销 content script + 广播）
                        await setDarkEnabled(!!message.enabled);
                        reply.result = true;
                        break;
                    }
                    default: {
                        throw new TypeError(`illegal type: ${type}`);
                    }
                }
            } catch (error) {
                reply.error = error.toString();
            } finally {
                webExtensionPort.postMessage(reply);
            }
            break;
        }
        case 1: {
            break;
        }
        case 2: {
            break;
        }
        default: {
            break;
        }
    }
});

browser.runtime.onMessage.addListener((message) => {
    if (typeof message === "object" && !Array.isArray(message) && message !== null &&
        message.__bbxQuery) {
        // content script 启动时询问当前开关状态
        return Promise.resolve({ __bbxDark: darkEnabled });
    }
    if (typeof message !== "object" || Array.isArray(message) || message === null) {
        return;
    }
    webExtensionPort.postMessage(message);
});

async function addJavascriptChannel(javascriptChannelName) {
    const javascriptChannel = await browser.contentScripts.register({
        matches: [
            "<all_urls>"
        ],
        js: [
            {
                code: `
window["${javascriptChannelName}"] = {
    postMessage: function(message) {
        browser.runtime.sendMessage({
            category: 2,
            type: 0,
            name: "${javascriptChannelName}",
            message: message
        });
    }
};
                `
            }
        ],
        runAt: "document_start"
    });
    javascriptChannels.set(javascriptChannelName, javascriptChannel);
}

async function removeJavascriptChannel(javascriptChannelName) {
    const javascriptChannel = javascriptChannels.get(javascriptChannelName);
    await javascriptChannel.unregister();
    javascriptChannels.delete(javascriptChannelName);
}

async function runJavascript(tabId, javascript) {
    const result = await browser.tabs.executeScript(
        tabId,
        {
            code: javascript,
            runAt: "document_start",
        }
    );
    return result[0] || null;
}

async function getCurrentTab() {
    const tabs = await browser.tabs.query({ active: true });
    return tabs[0] || null;
}
