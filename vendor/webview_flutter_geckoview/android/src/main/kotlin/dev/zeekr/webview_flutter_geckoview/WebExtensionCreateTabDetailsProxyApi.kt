package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.WebExtension

class WebExtensionCreateTabDetailsProxyApi(pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiWebExtensionCreateTabDetails(pigeonRegistrar) {
    override fun active(pigeon_instance: WebExtension.CreateTabDetails): Boolean? {
        return pigeon_instance.active
    }

    override fun cookieStoreId(pigeon_instance: WebExtension.CreateTabDetails): String? {
        return pigeon_instance.cookieStoreId
    }

    override fun discarded(pigeon_instance: WebExtension.CreateTabDetails): Boolean? {
        return pigeon_instance.discarded
    }

    override fun index(pigeon_instance: WebExtension.CreateTabDetails): Long? {
        return pigeon_instance.index?.toLong()
    }

    override fun openInReaderMode(pigeon_instance: WebExtension.CreateTabDetails): Boolean? {
        return pigeon_instance.openInReaderMode
    }

    override fun pinned(pigeon_instance: WebExtension.CreateTabDetails): Boolean? {
        return pigeon_instance.pinned
    }

    override fun url(pigeon_instance: WebExtension.CreateTabDetails): String? {
        return pigeon_instance.url
    }
}