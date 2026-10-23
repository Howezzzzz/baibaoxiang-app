package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebRequestError

class GeckoSessionNavigationDelegateProxyApi(pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiGeckoSessionNavigationDelegate(pigeonRegistrar) {
    override fun pigeon_defaultConstructor(): GeckoSession.NavigationDelegate {
        return object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                super.onCanGoBack(session, canGoBack)
                this@GeckoSessionNavigationDelegateProxyApi.onCanGoBack(this, session, canGoBack) {}
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                super.onCanGoForward(session, canGoForward)
                this@GeckoSessionNavigationDelegateProxyApi.onCanGoForward(this, session, canGoForward) {}
            }

            override fun onLoadError(
                session: GeckoSession,
                uri: String?,
                error: WebRequestError
            ): GeckoResult<String?>? {
                return super.onLoadError(session, uri, error)
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny> {
                val result = GeckoResult<AllowOrDeny>()
                this@GeckoSessionNavigationDelegateProxyApi.onLoadRequest(this, session, request) {
                    it.onSuccess { allowOrDeny ->
                        result.complete(
                            when (allowOrDeny) {
                                dev.zeekr.webview_flutter_geckoview.AllowOrDeny.ALLOW -> AllowOrDeny.ALLOW
                                dev.zeekr.webview_flutter_geckoview.AllowOrDeny.DENY -> AllowOrDeny.DENY
                            }
                        )
                    }
                        .onFailure { error -> result.completeExceptionally(error) }
                }
                return result
            }

            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                super.onLocationChange(session, url, perms, hasUserGesture)
                this@GeckoSessionNavigationDelegateProxyApi.onLocationChange(
                    this,
                    session,
                    url,
                    perms,
                    hasUserGesture
                ) {}
            }

            override fun onSubframeLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny> {
                val result = GeckoResult<AllowOrDeny>()
                this@GeckoSessionNavigationDelegateProxyApi.onLoadRequest(this, session, request) {
                    it.onSuccess { allowOrDeny ->
                        result.complete(
                            when (allowOrDeny) {
                                dev.zeekr.webview_flutter_geckoview.AllowOrDeny.ALLOW -> AllowOrDeny.ALLOW
                                dev.zeekr.webview_flutter_geckoview.AllowOrDeny.DENY -> AllowOrDeny.DENY
                            }
                        )
                    }
                        .onFailure { error -> result.completeExceptionally(error) }
                }
                return result
            }
        }
    }
}