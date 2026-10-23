package dev.zeekr.webview_flutter_geckoview

import org.json.JSONObject
import org.mozilla.geckoview.GeckoSession

class GeckoSessionContentDelegateProxyApi(pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiGeckoSessionContentDelegate(pigeonRegistrar) {
    override fun pigeon_defaultConstructor(): GeckoSession.ContentDelegate {
        return object : GeckoSession.ContentDelegate {
            override fun onCloseRequest(session: GeckoSession) {
                super.onCloseRequest(session)
                this@GeckoSessionContentDelegateProxyApi.onCloseRequest(this, session) {}
            }

            override fun onCookieBannerDetected(session: GeckoSession) {
                super.onCookieBannerDetected(session)
                this@GeckoSessionContentDelegateProxyApi.onCookieBannerDetected(this, session) {}
            }

            override fun onCookieBannerHandled(session: GeckoSession) {
                super.onCookieBannerHandled(session)
                this@GeckoSessionContentDelegateProxyApi.onCookieBannerHandled(this, session) {}
            }

            override fun onCrash(session: GeckoSession) {
                super.onCrash(session)
                this@GeckoSessionContentDelegateProxyApi.onCrash(this, session) {}
            }

            override fun onFirstComposite(session: GeckoSession) {
                super.onFirstComposite(session)
                this@GeckoSessionContentDelegateProxyApi.onFirstComposite(this, session) {}
            }

            override fun onFirstContentfulPaint(session: GeckoSession) {
                super.onFirstContentfulPaint(session)
                this@GeckoSessionContentDelegateProxyApi.onFirstContentfulPaint(this, session) {}
            }

            override fun onFocusRequest(session: GeckoSession) {
                super.onFocusRequest(session)
                this@GeckoSessionContentDelegateProxyApi.onFocusRequest(this, session) {}
            }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                super.onFullScreen(session, fullScreen)
                this@GeckoSessionContentDelegateProxyApi.onFullScreen(this, session, fullScreen) {}
            }

            override fun onHideDynamicToolbar(session: GeckoSession) {
                super.onHideDynamicToolbar(session)
                this@GeckoSessionContentDelegateProxyApi.onHideDynamicToolbar(this, session) {}
            }

            override fun onKill(session: GeckoSession) {
                super.onKill(session)
                this@GeckoSessionContentDelegateProxyApi.onKill(this, session) {}
            }

            override fun onMetaViewportFitChange(session: GeckoSession, viewportFit: String) {
                super.onMetaViewportFitChange(session, viewportFit)
                this@GeckoSessionContentDelegateProxyApi.onMetaViewportFitChange(
                    this, session, viewportFit
                ) {}
            }

            override fun onPaintStatusReset(session: GeckoSession) {
                super.onPaintStatusReset(session)
                this@GeckoSessionContentDelegateProxyApi.onPaintStatusReset(this, session) {}
            }

            override fun onPreviewImage(session: GeckoSession, previewImageUrl: String) {
                super.onPreviewImage(session, previewImageUrl)
                this@GeckoSessionContentDelegateProxyApi.onPreviewImage(
                    this, session, previewImageUrl
                ) {}
            }

            override fun onShowDynamicToolbar(session: GeckoSession) {
                super.onShowDynamicToolbar(session)
                this@GeckoSessionContentDelegateProxyApi.onShowDynamicToolbar(this, session) {}
            }

            override fun onTitleChange(session: GeckoSession, title: String?) {
                super.onTitleChange(session, title)
                this@GeckoSessionContentDelegateProxyApi.onTitleChange(this, session, title) {}
            }

            override fun onWebAppManifest(session: GeckoSession, manifest: JSONObject) {
                super.onWebAppManifest(session, manifest)
                this@GeckoSessionContentDelegateProxyApi.onWebAppManifest(
                    this, session, manifest.toString()
                ) {}
            }
        }
    }
}