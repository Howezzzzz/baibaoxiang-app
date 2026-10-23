package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

class WebExtensionControllerProxyApi(pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiWebExtensionController(pigeonRegistrar) {
    override fun ensureBuiltIn(pigeon_instance: WebExtensionController, uri: String, id: String?, callback: (Result<WebExtension?>) -> Unit) {
        pigeon_instance.ensureBuiltIn(uri, id).accept(
            { ResultCompat.success(it, callback) },
            { ResultCompat.failure(it, callback) }
        )
    }

    override fun list(pigeon_instance: WebExtensionController, callback: (Result<List<WebExtension>?>) -> Unit) {
        pigeon_instance.list().accept(
            { ResultCompat.success(it, callback) },
            { ResultCompat.failure(it,callback) }
        )
    }

    override fun setTabActive(pigeon_instance: WebExtensionController, session: GeckoSession, active: Boolean) {
        pigeon_instance.setTabActive(session, active)
    }
}