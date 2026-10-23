package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.WebExtension

class WebExtensionProxyApi(pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiWebExtension(pigeonRegistrar) {
    override fun getTabDelegate(pigeon_instance: WebExtension): WebExtension.TabDelegate? {
        return pigeon_instance.tabDelegate
    }

    override fun setMessageDelegate(pigeon_instance: WebExtension, delegate: WebExtension.MessageDelegate?, nativeApp: String) {
        pigeon_instance.setMessageDelegate(delegate, nativeApp)
    }

    override fun setTabDelegate(pigeon_instance: WebExtension, delegate: WebExtension.TabDelegate?) {
        pigeon_instance.tabDelegate = delegate
    }
}