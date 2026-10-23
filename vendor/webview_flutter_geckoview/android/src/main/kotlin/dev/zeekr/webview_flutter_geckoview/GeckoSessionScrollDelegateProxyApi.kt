package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.GeckoSession

class GeckoSessionScrollDelegateProxyApi(pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiGeckoSessionScrollDelegate(pigeonRegistrar) {
    override fun pigeon_defaultConstructor(): GeckoSession.ScrollDelegate {
        return object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
                super.onScrollChanged(session, scrollX, scrollY)
                this@GeckoSessionScrollDelegateProxyApi.onScrollChanged(
                    this, session, scrollX.toLong(), scrollY.toLong()
                ) {}
            }
        }
    }
}