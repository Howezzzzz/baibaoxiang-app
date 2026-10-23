package dev.zeekr.webview_flutter_geckoview

import org.json.JSONObject
import org.mozilla.geckoview.WebExtension

class WebExtensionPortDelegateProxyApi(pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiWebExtensionPortDelegate(pigeonRegistrar) {
    override fun pigeon_defaultConstructor(): WebExtension.PortDelegate {
        return object : WebExtension.PortDelegate {
            override fun onDisconnect(port: WebExtension.Port) {
                super.onDisconnect(port)
                this@WebExtensionPortDelegateProxyApi.onDisconnect(this, port) {}
            }

            override fun onPortMessage(message: Any, port: WebExtension.Port) {
                super.onPortMessage(message, port)
                this@WebExtensionPortDelegateProxyApi.onPortMessage(this, message.toString(), port) {}
            }
        }
    }
}