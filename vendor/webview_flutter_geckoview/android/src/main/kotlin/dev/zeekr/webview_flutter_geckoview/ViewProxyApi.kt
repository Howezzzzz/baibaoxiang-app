package dev.zeekr.webview_flutter_geckoview

import android.view.View

class ViewProxyApi(override val pigeonRegistrar: ProxyApiRegistrar) : PigeonApiView(pigeonRegistrar) {
    override fun setBackgroundColor(pigeon_instance: View, color: Long) {
        pigeon_instance.setBackgroundColor(color.toInt())
    }

    override fun scrollTo(pigeon_instance: View, x: Long, y: Long) {
        pigeon_instance.scrollTo(x.toInt(), y.toInt())
    }

    override fun scrollBy(pigeon_instance: View, x: Long, y: Long) {
        pigeon_instance.scrollBy(x.toInt(), y.toInt())
    }

    override fun getScrollPosition(pigeon_instance: View): GeckoViewPoint {
        return GeckoViewPoint(pigeon_instance.scrollX, pigeon_instance.scrollY)
    }

    override fun setVerticalScrollBarEnabled(pigeon_instance: View, enabled: Boolean) {
        pigeon_instance.isVerticalScrollBarEnabled = enabled
    }

    override fun setHorizontalScrollBarEnabled(pigeon_instance: View, enabled: Boolean) {
        pigeon_instance.isHorizontalScrollBarEnabled = enabled
    }

    override fun setOverScrollMode(pigeon_instance: View, mode: OverScrollMode) {
        when (mode) {
            OverScrollMode.ALWAYS -> pigeon_instance.overScrollMode = View.OVER_SCROLL_ALWAYS
            OverScrollMode.IF_CONTENT_SCROLLS -> pigeon_instance.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            OverScrollMode.NEVER -> pigeon_instance.overScrollMode = View.OVER_SCROLL_NEVER
            OverScrollMode.UNKNOWN -> throw pigeonRegistrar.createUnknownEnumException(mode)
        }
    }
}