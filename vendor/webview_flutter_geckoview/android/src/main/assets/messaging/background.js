/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

const javascriptChannels = new Map();
const webExtensionPort = browser.runtime.connectNative("webview_flutter");

// 百宝箱：网页深色适配以扩展自带 content script 形式加载（不经受限端口传大段代码）。
// 宿主只需通过端口下发 {category:0, type:4, enabled:bool} 开关状态。
let darkEnabled = false;
let darkScript = null;
(async () => {
    try {
        darkScript = await browser.contentScripts.register({
            matches: ["<all_urls>"],
            js: [{ file: "dark_mode.js" }],
            runAt: "document_end",
        });
        console.info("bbx dark_mode content script registered");
    } catch (e) {
        console.error("bbx dark_mode register failed", e);
    }
})();

webExtensionPort.onDisconnect.addListener(() => {
    console.info("webExtensionPort disconnected");
});

webExtensionPort.onMessage.addListener(async (message) => {
    console.info(`webExtensionPort received: ${JSON.stringify(message)}`);
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
                        // 百宝箱：网页深色适配开关（广播给所有页面里的 content script）
                        darkEnabled = !!message.enabled;
                        const tabs = await browser.tabs.query({});
                        for (const tab of tabs) {
                            try {
                                await browser.tabs.sendMessage(tab.id, { __bbxDark: darkEnabled });
                            } catch (e) {
                                // 页面里没挂上 content script 就跳过
                            }
                        }
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
