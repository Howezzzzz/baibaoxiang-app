package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.WebExtension

class WebExtensionUpdateTabDetailsProxyApi(pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiWebExtensionUpdateTabDetails(pigeonRegistrar) {
    override fun active(pigeon_instance: WebExtension.UpdateTabDetails): Boolean? {
        return pigeon_instance.active
    }

    override fun autoDiscardable(pigeon_instance: WebExtension.UpdateTabDetails): Boolean? {
        return pigeon_instance.autoDiscardable
    }

    override fun highlighted(pigeon_instance: WebExtension.UpdateTabDetails): Boolean? {
        return pigeon_instance.highlighted
    }

    override fun muted(pigeon_instance: WebExtension.UpdateTabDetails): Boolean? {
        return pigeon_instance.muted
    }

    override fun pinned(pigeon_instance: WebExtension.UpdateTabDetails): Boolean? {
        return pigeon_instance.pinned
    }

    override fun url(pigeon_instance: WebExtension.UpdateTabDetails): String? {
        return pigeon_instance.url
    }
}