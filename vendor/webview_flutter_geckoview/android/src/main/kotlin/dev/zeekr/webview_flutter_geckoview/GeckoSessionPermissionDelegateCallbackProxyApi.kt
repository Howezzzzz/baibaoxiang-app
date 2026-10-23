package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.GeckoSession

class GeckoSessionPermissionDelegateCallbackProxyApi(pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiGeckoSessionPermissionDelegateCallback(pigeonRegistrar) {
    override fun grant(pigeon_instance: GeckoSession.PermissionDelegate.Callback) {
        pigeon_instance.grant()
    }

    override fun reject(pigeon_instance: GeckoSession.PermissionDelegate.Callback) {
        pigeon_instance.reject()
    }
}