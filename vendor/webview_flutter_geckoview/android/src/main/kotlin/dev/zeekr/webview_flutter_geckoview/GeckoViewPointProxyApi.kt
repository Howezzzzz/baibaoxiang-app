package dev.zeekr.webview_flutter_geckoview

class GeckoViewPointProxyApi(pigeonRegistrar: ProxyApiRegistrar) : PigeonApiGeckoViewPoint(pigeonRegistrar) {
    override fun x(pigeon_instance: GeckoViewPoint): Long {
        return pigeon_instance.x.toLong()
    }

    override fun y(pigeon_instance: GeckoViewPoint): Long {
        return pigeon_instance.y.toLong()
    }
}