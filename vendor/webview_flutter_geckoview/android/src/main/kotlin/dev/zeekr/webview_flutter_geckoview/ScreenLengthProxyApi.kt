package dev.zeekr.webview_flutter_geckoview

import org.mozilla.geckoview.ScreenLength

class ScreenLengthProxyApi(pigeonRegistrar: ProxyApiRegistrar) : PigeonApiScreenLength(pigeonRegistrar) {
    override fun bottom(): ScreenLength {
        return ScreenLength.bottom()
    }

    override fun fromPixels(value: Double): ScreenLength {
        return ScreenLength.fromPixels(value)
    }

    override fun fromVisualViewportHeight(value: Double): ScreenLength {
        return ScreenLength.fromVisualViewportHeight(value)
    }

    override fun fromVisualViewportWidth(value: Double): ScreenLength {
        return ScreenLength.fromVisualViewportWidth(value)
    }

    override fun top(): ScreenLength {
        return ScreenLength.top()
    }

    override fun zero(): ScreenLength {
        return ScreenLength.zero()
    }

    override fun getType(pigeon_instance: ScreenLength): Long {
        return pigeon_instance.type.toLong()
    }

    override fun getValue(pigeon_instance: ScreenLength): Double {
        return pigeon_instance.value
    }
}