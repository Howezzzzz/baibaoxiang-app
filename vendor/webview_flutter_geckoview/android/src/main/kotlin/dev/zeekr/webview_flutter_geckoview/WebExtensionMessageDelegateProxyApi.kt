package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.WebExtension

class WebExtensionMessageDelegateProxyApi(pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiWebExtensionMessageDelegate(pigeonRegistrar) {
    override fun pigeon_defaultConstructor(): WebExtension.MessageDelegate {
        return object : WebExtension.MessageDelegate {
            override fun onConnect(port: WebExtension.Port) {
                super.onConnect(port)
                this@WebExtensionMessageDelegateProxyApi.onConnect(this, port) {}
            }
        }
    }
}