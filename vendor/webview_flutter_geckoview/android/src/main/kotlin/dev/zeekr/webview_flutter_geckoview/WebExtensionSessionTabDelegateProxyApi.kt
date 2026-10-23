package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension

class WebExtensionSessionTabDelegateProxyApi(pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiWebExtensionSessionTabDelegate(pigeonRegistrar) {
    override fun pigeon_defaultConstructor(): WebExtension.SessionTabDelegate {
        return object : WebExtension.SessionTabDelegate {
            override fun onCloseTab(
                extension: WebExtension?, session: GeckoSession
            ): GeckoResult<AllowOrDeny> {
                val result = GeckoResult<AllowOrDeny>()
                this@WebExtensionSessionTabDelegateProxyApi.onCloseTab(this, extension, session) {
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

            override fun onUpdateTab(
                extension: WebExtension,
                session: GeckoSession,
                details: WebExtension.UpdateTabDetails
            ): GeckoResult<AllowOrDeny> {
                val result = GeckoResult<AllowOrDeny>()
                this@WebExtensionSessionTabDelegateProxyApi.onUpdateTab(
                    this, extension, session, details
                ) {
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