package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.GeckoSession

class GeckoSessionPermissionDelegateProxyApi(pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiGeckoSessionPermissionDelegate(pigeonRegistrar) {
    override fun pigeon_defaultConstructor(): GeckoSession.PermissionDelegate {
        return object : GeckoSession.PermissionDelegate {
            override fun onAndroidPermissionsRequest(
                session: GeckoSession,
                permissions: Array<out String>?,
                callback: GeckoSession.PermissionDelegate.Callback
            ) {
                super.onAndroidPermissionsRequest(session, permissions, callback)
                if (permissions == null) return
                this@GeckoSessionPermissionDelegateProxyApi.onAndroidPermissionsRequest(
                    this, session, permissions.toList(), callback
                ) {}
            }
        }
    }
}