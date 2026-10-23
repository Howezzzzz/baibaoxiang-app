package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.GeckoSession

class GeckoSessionPermissionDelegateContentPermissionProxyApi(pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiGeckoSessionPermissionDelegateContentPermission(pigeonRegistrar) {
    override fun contextId(pigeon_instance: GeckoSession.PermissionDelegate.ContentPermission): String? {
        return pigeon_instance.contextId
    }

    override fun permission(pigeon_instance: GeckoSession.PermissionDelegate.ContentPermission): Long {
        return pigeon_instance.permission.toLong()
    }

    override fun privateMode(pigeon_instance: GeckoSession.PermissionDelegate.ContentPermission): Boolean {
        return pigeon_instance.privateMode
    }

    override fun thirdPartyOrigin(pigeon_instance: GeckoSession.PermissionDelegate.ContentPermission): String? {
        return pigeon_instance.thirdPartyOrigin
    }

    override fun uri(pigeon_instance: GeckoSession.PermissionDelegate.ContentPermission): String {
        return pigeon_instance.uri
    }

    override fun value(pigeon_instance: GeckoSession.PermissionDelegate.ContentPermission): Long {
        return pigeon_instance.value.toLong()
    }
}