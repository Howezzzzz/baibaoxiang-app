package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.GeckoSession

class GeckoSessionProgressDelegateProxyApi(pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiGeckoSessionProgressDelegate(pigeonRegistrar) {
    override fun pigeon_defaultConstructor(): GeckoSession.ProgressDelegate {
        return object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                super.onPageStart(session, url)
                this@GeckoSessionProgressDelegateProxyApi.onPageStart(this, session, url) {}
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                super.onPageStop(session, success)
                this@GeckoSessionProgressDelegateProxyApi.onPageStop(this, session, success) {}
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                super.onProgressChange(session, progress)
                this@GeckoSessionProgressDelegateProxyApi.onProgressChange(this, session, progress.toLong()) {}
            }
        }
    }
}