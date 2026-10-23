package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.GeckoSession

class GeckoSessionNavigationDelegateLoadRequestProxyApi(pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiGeckoSessionNavigationDelegateLoadRequest(pigeonRegistrar) {
    override fun hasUserGesture(pigeon_instance: GeckoSession.NavigationDelegate.LoadRequest): Boolean {
        return pigeon_instance.hasUserGesture
    }

    override fun isDirectNavigation(pigeon_instance: GeckoSession.NavigationDelegate.LoadRequest): Boolean {
        return pigeon_instance.isDirectNavigation
    }

    override fun isRedirect(pigeon_instance: GeckoSession.NavigationDelegate.LoadRequest): Boolean {
        return pigeon_instance.isRedirect
    }

    override fun target(pigeon_instance: GeckoSession.NavigationDelegate.LoadRequest): Long {
        return pigeon_instance.target.toLong()
    }

    override fun triggerUri(pigeon_instance: GeckoSession.NavigationDelegate.LoadRequest): String? {
        return pigeon_instance.triggerUri
    }

    override fun uri(pigeon_instance: GeckoSession.NavigationDelegate.LoadRequest): String {
        return pigeon_instance.uri
    }
}