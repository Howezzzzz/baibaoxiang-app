package dev.zeekr.webview_flutter_geckoview

import android.view.View
import io.flutter.plugin.platform.PlatformView
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.PanZoomController

class GeckoViewProxyApi(override val pigeonRegistrar: ProxyApiRegistrar) :
    PigeonApiGeckoView(pigeonRegistrar) {
    override fun pigeon_defaultConstructor(): GeckoView {
        return object : GeckoView(pigeonRegistrar.context), PlatformView {
            override fun onScrollChanged(left: Int, top: Int, oldLeft: Int, oldTop: Int) {
                super.onScrollChanged(left, top, oldLeft, oldTop)
                this@GeckoViewProxyApi.onScrollChanged(
                    this,
                    left.toLong(),
                    top.toLong(),
                    oldLeft.toLong(),
                    oldTop.toLong()
                ) {}
            }

            override fun getView(): View = this

            override fun dispose() {}
        }
    }

    override fun panZoomController(pigeon_instance: GeckoView): PanZoomController {
        return pigeon_instance.panZoomController
    }

    override fun getSession(pigeon_instance: GeckoView): GeckoSession? {
        return pigeon_instance.session
    }

    override fun releaseSession(pigeon_instance: GeckoView): GeckoSession? {
        return pigeon_instance.releaseSession()
    }

    override fun setSession(pigeon_instance: GeckoView, session: GeckoSession) {
        pigeon_instance.setSession(session)
    }

    override fun setViewBackend(pigeon_instance: GeckoView, backend: Long) {
        pigeon_instance.setViewBackend(backend.toInt())
    }
}