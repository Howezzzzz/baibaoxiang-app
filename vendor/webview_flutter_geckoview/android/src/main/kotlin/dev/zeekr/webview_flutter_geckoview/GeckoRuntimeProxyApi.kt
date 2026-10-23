package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtensionController

class GeckoRuntimeProxyApi(override val pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiGeckoRuntime(pigeonRegistrar) {
    override fun instance(): GeckoRuntime {
        return pigeonRegistrar.runtime
    }

    override fun settings(pigeon_instance: GeckoRuntime): GeckoRuntimeSettings {
        return pigeon_instance.settings
    }

    override fun storageController(pigeon_instance: GeckoRuntime): StorageController {
        return pigeon_instance.storageController
    }

    override fun webExtensionController(pigeon_instance: GeckoRuntime): WebExtensionController {
        return pigeon_instance.webExtensionController
    }
}