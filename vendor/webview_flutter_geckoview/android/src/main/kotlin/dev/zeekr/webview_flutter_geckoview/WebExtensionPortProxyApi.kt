package dev.zeekr.webview_flutter_geckoview

import org.json.JSONObject
import org.mozilla.geckoview.WebExtension

class WebExtensionPortProxyApi(override val pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiWebExtensionPort(pigeonRegistrar) {
    override fun name(pigeon_instance: WebExtension.Port): String {
        return pigeon_instance.name
    }

    override fun getAsync(callback: (Result<WebExtension.Port?>) -> Unit) {
        pigeonRegistrar.webExtensionPort.accept(
            { ResultCompat.success(it, callback) },
            { ResultCompat.failure(it, callback) })
    }

    override fun disconnect(pigeon_instance: WebExtension.Port) {
        pigeon_instance.disconnect()
    }

    override fun postMessage(pigeon_instance: WebExtension.Port, message: String) {
        pigeon_instance.postMessage(JSONObject(message))
    }

    override fun setDelegate(pigeon_instance: WebExtension.Port, delegate: WebExtension.PortDelegate?) {
        pigeon_instance.setDelegate(delegate)
    }
}