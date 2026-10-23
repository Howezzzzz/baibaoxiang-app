package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.PanZoomController
import org.mozilla.geckoview.ScreenLength

class PanZoomControllerProxyApi(pigeonRegistrar: ProxyApiRegistrar) : PigeonApiPanZoomController(pigeonRegistrar) {
    override fun getScrollFactor(pigeon_instance: PanZoomController): Double {
        return pigeon_instance.scrollFactor.toDouble()
    }

    override fun scrollBy(
        pigeon_instance: PanZoomController,
        width: ScreenLength,
        height: ScreenLength,
        behavior: Long?
    ) {
        if (behavior == null) {
            pigeon_instance.scrollBy(width, height)
        } else {
            pigeon_instance.scrollBy(width, height, behavior.toInt())
        }
    }

    override fun scrollTo(
        pigeon_instance: PanZoomController,
        width: ScreenLength,
        height: ScreenLength,
        behavior: Long?
    ) {
        if (behavior == null) {
            pigeon_instance.scrollTo(width, height)
        } else {
            pigeon_instance.scrollTo(width, height, behavior.toInt())
        }
    }

    override fun scrollToBottom(pigeon_instance: PanZoomController) {
        pigeon_instance.scrollToBottom()
    }

    override fun scrollToTop(pigeon_instance: PanZoomController) {
        pigeon_instance.scrollToTop()
    }

    override fun setIsLongpressEnabled(
        pigeon_instance: PanZoomController,
        isLongpressEnabled: Boolean
    ) {
        pigeon_instance.setIsLongpressEnabled(isLongpressEnabled)
    }

    override fun setScrollFactor(pigeon_instance: PanZoomController, factor: Double) {
        pigeon_instance.scrollFactor = factor.toFloat()
    }
}